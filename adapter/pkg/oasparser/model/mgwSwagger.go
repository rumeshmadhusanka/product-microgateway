/*
 *  Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
 */

package model

import (
	logger "github.com/wso2/micro-gw/loggers"
	"github.com/wso2/micro-gw/pkg/svcdiscovery"
)

// MgwSwagger represents the object structure holding the information related to the
// openAPI object. The values are populated from the extensions/properties mentioned at
// the root level of the openAPI definition. The pathItem level information is represented
// by the resources array which contains the MgwResource entries.
type MgwSwagger struct {
	id               string
	swaggerVersion   string
	description      string
	title            string
	version          string
	vendorExtensible map[string]interface{}
	productionUrls   []Endpoint
	sandboxUrls      []Endpoint
	resources        []Resource
	xWso2Basepath    string
}

// Endpoint represents the structure of an endpoint.
type Endpoint struct {
	// Host name
	Host string
	// BasePath (which would be added as prefix to the path mentioned in openapi definition)
	// In openAPI v2, it is determined from the basePath property
	// In openAPi v3, it is determined from the server object's suffix
	Basepath string
	// https, http, ws, wss
	// In openAPI v2, it is fetched from the schemes entry
	// In openAPI v3, it is extracted from the server property under servers object
	// only https and http are supported at the moment.
	URLType string
	// Port of the endpoint.
	// If the port is not specified, 80 is assigned if URLType is http
	// 443 is assigned if URLType is https
	Port uint32
	//ServiceDiscoveryQuery consul query for service discovery
	ServiceDiscoveryString string
}

// GetSwaggerVersion returns the openapi version
func (swagger *MgwSwagger) GetSwaggerVersion() string {
	return swagger.swaggerVersion
}

// GetVersion returns the API version
func (swagger *MgwSwagger) GetVersion() string {
	return swagger.version
}

// GetTitle returns the API Title
func (swagger *MgwSwagger) GetTitle() string {
	return swagger.title
}

// GetXWso2Basepath returns the basepath set via the vendor extension.
func (swagger *MgwSwagger) GetXWso2Basepath() string {
	return swagger.xWso2Basepath
}

// GetVendorExtensible returns the map of vendor extensions which are defined
// at openAPI's root level.
func (swagger *MgwSwagger) GetVendorExtensible() map[string]interface{} {
	return swagger.vendorExtensible
}

// GetProdEndpoints returns the array of production endpoints.
func (swagger *MgwSwagger) GetProdEndpoints() []Endpoint {
	return swagger.productionUrls
}

// GetSandEndpoints returns the array of sandbox endpoints.
func (swagger *MgwSwagger) GetSandEndpoints() []Endpoint {
	return swagger.sandboxUrls
}

// GetResources returns the array of resources (openAPI path level info)
func (swagger *MgwSwagger) GetResources() []Resource {
	return swagger.resources
}

// SetXWso2Extenstions set the MgwSwagger object with the properties
// extracted from vendor extensions.
// xWso2Basepath, xWso2ProductionEndpoints, and xWso2SandboxEndpoints are assigned
// based on the vendor extensions.
//
// Resource level properties (xwso2ProductionEndpoints and xWso2SandboxEndpoints are
// also populated at the same time).
func (swagger *MgwSwagger) SetXWso2Extenstions() {
	swagger.setXWso2Basepath()
	swagger.setXWso2PrdoductionEndpoint()
	swagger.setXWso2SandboxEndpoint()
}

func (swagger *MgwSwagger) setXWso2PrdoductionEndpoint() {
	xWso2APIEndpoints := getXWso2Endpoints(swagger.vendorExtensible, productionEndpoints)
	if xWso2APIEndpoints != nil && len(xWso2APIEndpoints) > 0 {
		swagger.productionUrls = xWso2APIEndpoints
	}

	//resources
	for i, resource := range swagger.resources {
		xwso2ResourceEndpoints := getXWso2Endpoints(resource.vendorExtensible, productionEndpoints)
		if xwso2ResourceEndpoints != nil {
			swagger.resources[i].productionUrls = xwso2ResourceEndpoints
		}
	}
}

func (swagger *MgwSwagger) setXWso2SandboxEndpoint() {
	xWso2APIEndpoints := getXWso2Endpoints(swagger.vendorExtensible, sandboxEndpoints)
	if xWso2APIEndpoints != nil && len(xWso2APIEndpoints) > 0 {
		swagger.sandboxUrls = xWso2APIEndpoints
	}

	// resources
	for i, resource := range swagger.resources {
		xwso2ResourceEndpoints := getXWso2Endpoints(resource.vendorExtensible, sandboxEndpoints)
		if xwso2ResourceEndpoints != nil {
			swagger.resources[i].sandboxUrls = xwso2ResourceEndpoints
		}
	}
}

// getXWso2Endpoints extracts and generate the Endpoint Objects from the vendor extension map.
func getXWso2Endpoints(vendorExtensible map[string]interface{}, endpointType string) []Endpoint {
	var endpoints []Endpoint

	// TODO: (VirajSalaka) x-wso2-production-endpoint 's type does not represent http/https, instead it indicates loadbalance and failover
	if y, found := vendorExtensible[endpointType]; found {
		if val, ok := y.(map[string]interface{}); ok {
			urlsProperty, ok := val[urls]
			if !ok {
				// TODO: (VirajSalaka) Throw an error and catch from an upper layer where the API name is visible.
				logger.LoggerOasparser.Error("urls property is not provided with the x-wso2-production-endpoints/" +
					"x-wso2-sandbox-endpoints extension.")
			} else {
				castedUrlsInterface := urlsProperty.([]interface{})
				for _, v := range castedUrlsInterface {
					if svcdiscovery.IsDiscoveryServiceEndpoint(v.(string), svcdiscovery.ConsulBegin) {
						logger.LoggerOasparser.Debug("consul query syntax found: ", v.(string))
						queryString, defHost, err := svcdiscovery.ParseConsulSyntax(v.(string))
						if err != nil {
							logger.LoggerOasparser.Error("consul syntax parse error")
							continue
						}
						endpoint := getHostandBasepathandPort(defHost)
						endpointType, endpointTypeFound := val[typeConst]
						endpoint.ServiceDiscoveryString = queryString

						if endpointTypeFound {
							endpoint.URLType = endpointType.(string)
						}
						endpoints = append(endpoints, endpoint)

					} else {
						endpoint := getHostandBasepathandPort(v.(string))
						endpointType, endpointTypeFound := val[typeConst]

						if endpointTypeFound {
							endpoint.URLType = endpointType.(string)
						}
						endpoints = append(endpoints, endpoint)
					}

				}
				return endpoints
			}
		} else {
			logger.LoggerOasparser.Fatal("x-wso2-production/sandbox-endpoints is not having a correct map structure")
		}
	}
	return nil
}

// getXWso2Basepath extracts the value of xWso2BasePath extension.
// if the property is not available, an empty string is returned.
func getXWso2Basepath(vendorExtensible map[string]interface{}) string {
	xWso2basepath := ""
	if y, found := vendorExtensible[xWso2BasePath]; found {
		if val, ok := y.(string); ok {
			xWso2basepath = val
		}
	}
	return xWso2basepath
}

func (swagger *MgwSwagger) setXWso2Basepath() {
	swagger.xWso2Basepath = getXWso2Basepath(swagger.vendorExtensible)
}
