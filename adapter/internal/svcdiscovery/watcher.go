/*
 *  Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package svcdiscovery

import (
	"crypto/tls"
	"crypto/x509"
	corev3 "github.com/envoyproxy/go-control-plane/envoy/config/core/v3"
	tlsv3 "github.com/envoyproxy/go-control-plane/envoy/extensions/transport_sockets/tls/v3"
	"io/ioutil"
	"net/url"
	"strings"
	"sync"
	"time"

	"github.com/wso2/micro-gw/config"
	logger "github.com/wso2/micro-gw/loggers"
)

var (
	//IsServiceDiscoveryEnabled whether Consul service discovery should be enabled
	IsServiceDiscoveryEnabled bool
	onceConfigLoad            sync.Once
	conf                      *config.Config
	pollInterval              time.Duration
	errConfLoad               error
	//MeshEnabled whether Consul service mesh is enabled or not
	MeshEnabled    bool
	mgwServiceName string
	//MeshCACerts a CA cert of Consul Mesh
	MeshCACerts []string
	//MeshServiceCert public cert of Router
	MeshServiceCert string
	//MeshServiceKey private key of Router
	MeshServiceKey string
	//MeshUpdateSignal send a signal to notify that a cert update should be propagated to envoy
	MeshUpdateSignal chan bool
	//ssl certs
	caCert   []byte
	cert     []byte
	key      []byte
	aclToken string

	//ConsulClientInstance instance for consul client
	ConsulClientInstance ConsulClient
	//ClusterConsulKeyMap Cluster Name -> consul syntax key
	ClusterConsulKeyMap map[string]string
	//ClusterConsulResultMap Cluster Name -> Upstream
	//saves the last result with respected to a cluster
	ClusterConsulResultMap map[string][]Upstream
	//ClusterConsulDoneChanMap Cluster Name -> doneChan for respective go routine
	//when the cluster is removed we can stop the respective go routine to stop polling and release resources
	ClusterConsulDoneChanMap map[string]chan bool
)

func init() {
	ClusterConsulKeyMap = make(map[string]string)
	ClusterConsulResultMap = make(map[string][]Upstream)
	ClusterConsulDoneChanMap = make(map[string]chan bool)
	//Read config
	conf, errConfLoad = config.ReadConfigs()
	IsServiceDiscoveryEnabled = conf.Adapter.Consul.Enable
	aclToken = strings.TrimSpace(aclToken)
	mgwServiceName = conf.Adapter.Consul.MgwServiceName
	MeshEnabled = conf.Adapter.Consul.ServiceMeshEnabled
	MeshUpdateSignal = make(chan bool)
	MeshCACerts = make([]string, 1)
}

//read the certs and access token required for tls into respective global variables
func readCerts() error {
	// TODO: (VirajSalaka) Replace with common CA cert pool
	caFileContent, readErr := ioutil.ReadFile(conf.Adapter.Consul.CaCertFile)
	if readErr != nil {
		return readErr
	}

	certFileContent, readErr := ioutil.ReadFile(conf.Adapter.Consul.CertFile)
	if readErr != nil {
		return readErr
	}

	keyFileContent, readErr := ioutil.ReadFile(conf.Adapter.Consul.KeyFile)
	if readErr != nil {
		return readErr
	}

	caCert = caFileContent
	cert = certFileContent
	key = keyFileContent

	return nil
}

//InitConsul loads certs and initialize a ConsulClient
//lazy loading
func InitConsul() {
	onceConfigLoad.Do(func() {
		conf, errConfLoad = config.ReadConfigs()
		if errConfLoad != nil {
			logger.LoggerSvcDiscovery.Error("Consul Config loading error ", errConfLoad)
			return
		}
		pollInterval = time.Duration(conf.Adapter.Consul.PollInterval) * time.Second
		urlStructure, errURLParse := url.Parse(conf.Adapter.Consul.URL)
		if errURLParse != nil {
			errConfLoad = errURLParse
			logger.LoggerSvcDiscovery.Error("Invalid URL to Consul Client ", errURLParse)
			return
		}
		if urlStructure.Scheme == "https" { //communicate to consul through https
			errCertRead := readCerts()
			if errCertRead != nil {
				errConfLoad = errCertRead
				logger.LoggerSvcDiscovery.Error("Consul Certs read error ", errCertRead)
				return
			}
			pool := x509.NewCertPool()
			pool.AppendCertsFromPEM(caCert)
			clientCert, errKeyPairLoad := tls.X509KeyPair(cert, key)
			if errKeyPairLoad != nil {
				errConfLoad = errKeyPairLoad
				logger.LoggerSvcDiscovery.Error("Key pair error", errKeyPairLoad)
				return
			}
			tlsConfig := newTLSConfig(pool, []tls.Certificate{clientCert}, false)
			transport := newHTTPSTransport(&tlsConfig)
			client := newHTTPClient(&transport, pollInterval)
			longPollClient := newHTTPClient(&transport, time.Duration(longPollInterval*2)*time.Second)
			ConsulClientInstance = NewConsulClient(client, longPollClient, urlStructure.Scheme, urlStructure.Host, aclToken)
			ConsulClientInstance.LongPollRootCert(MeshUpdateSignal)
			ConsulClientInstance.LongPollServiceCertAndKey(MeshUpdateSignal)
		} else {
			//communicate to consul through http
			transport := newHTTPTransport()
			client := newHTTPClient(&transport, pollInterval)
			longPollClient := newHTTPClient(&transport, time.Duration(longPollInterval*2)*time.Second)
			ConsulClientInstance = NewConsulClient(client, longPollClient, urlStructure.Scheme, urlStructure.Host, aclToken)
		}

	})
}

func generateTLSCertWithStr(privateKey string, publicKey string) *tlsv3.TlsCertificate {
	var tlsCert tlsv3.TlsCertificate
	tlsCert = tlsv3.TlsCertificate{
		PrivateKey: &corev3.DataSource{
			Specifier: &corev3.DataSource_InlineString{
				InlineString: privateKey,
			},
		},
		CertificateChain: &corev3.DataSource{
			Specifier: &corev3.DataSource_InlineString{
				InlineString: publicKey,
			},
		},
	}
	return &tlsCert
}

//CreateUpstreamTLSContext create a new TLS context using CA, private key and public key
func CreateUpstreamTLSContext(upstreamCACerts []string, privateKey, publicKey string) *tlsv3.UpstreamTlsContext {
	tlsCert := generateTLSCertWithStr(privateKey, publicKey)
	// Convert the cipher string to a string array
	ciphersArray := strings.Split(conf.Envoy.Upstream.TLS.Ciphers, ",")
	for i := range ciphersArray {
		ciphersArray[i] = strings.TrimSpace(ciphersArray[i])
	}

	upstreamTLSContext := &tlsv3.UpstreamTlsContext{
		CommonTlsContext: &tlsv3.CommonTlsContext{
			TlsParams: &tlsv3.TlsParameters{
				TlsMinimumProtocolVersion: createTLSProtocolVersion(conf.Envoy.Upstream.TLS.MinVersion),
				TlsMaximumProtocolVersion: createTLSProtocolVersion(conf.Envoy.Upstream.TLS.MaxVersion),
				CipherSuites:              ciphersArray,
			},
			TlsCertificates: []*tlsv3.TlsCertificate{tlsCert},
		},
	}

	// Sni should be assigned when there is a hostname
	//if net.ParseIP(address.GetSocketAddress().GetAddress()) == nil {
	//	upstreamTLSContext.Sni = address.GetSocketAddress().GetAddress()
	//}

	if !conf.Envoy.Upstream.TLS.DisableSSLVerification {
		var trustedCASrc *corev3.DataSource

		logger.LoggerSvcDiscovery.Println("upstream ca cert: ", upstreamCACerts)
		//if upstreamCACert != "" {

		for _, upstreamCACert := range upstreamCACerts {
			trustedCASrc = &corev3.DataSource{
				Specifier: &corev3.DataSource_InlineString{
					InlineString: upstreamCACert,
				},
			}
		}
		//} else {
		//	//todo remove log
		//	logger.LoggerXds.Println("CA from file")
		//	trustedCASrc = &corev3.DataSource{
		//		Specifier: &corev3.DataSource_Filename{
		//			Filename: conf.Envoy.Upstream.TLS.CACrtPath,
		//		},
		//	}
		//}

		upstreamTLSContext.CommonTlsContext.ValidationContextType = &tlsv3.CommonTlsContext_ValidationContext{
			ValidationContext: &tlsv3.CertificateValidationContext{
				TrustedCa: trustedCASrc,
			},
		}
	} else {
		//todo remove else
		logger.LoggerXds.Println("SSL verification disabled")
	}

	if conf.Envoy.Upstream.TLS.VerifyHostName && !conf.Envoy.Upstream.TLS.DisableSSLVerification {
		//addressString := address.GetSocketAddress().GetAddress()
		//addressString := "consul"
		//subjectAltNames := []*envoy_type_matcherv3.StringMatcher{
		//	{
		//		MatchPattern: &envoy_type_matcherv3.StringMatcher_Suffix{
		//			Suffix: addressString,
		//		},
		//	},
		//}
		//upstreamTLSContext.CommonTlsContext.GetValidationContext().MatchSubjectAltNames = subjectAltNames
	} else {
		//todo remove else
		logger.LoggerSvcDiscovery.Println("Verify host name disabled")
	}
	return upstreamTLSContext
}

func createTLSProtocolVersion(tlsVersion string) tlsv3.TlsParameters_TlsProtocol {
	switch tlsVersion {
	case "TLS1_0":
		return tlsv3.TlsParameters_TLSv1_0
	case "TLS1_1":
		return tlsv3.TlsParameters_TLSv1_1
	case "TLS1_2":
		return tlsv3.TlsParameters_TLSv1_2
	case "TLS1_3":
		return tlsv3.TlsParameters_TLSv1_3
	default:
		return tlsv3.TlsParameters_TLS_AUTO
	}
}