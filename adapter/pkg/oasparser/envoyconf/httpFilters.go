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
 *
 */

// Package envoyconf generates the envoyconfiguration for listeners, virtual hosts,
// routes, clusters, and endpoints.
package envoyconf

import (
	"time"

	corev3 "github.com/envoyproxy/go-control-plane/envoy/config/core/v3"
	ext_authv3 "github.com/envoyproxy/go-control-plane/envoy/extensions/filters/http/ext_authz/v3"
	routerv3 "github.com/envoyproxy/go-control-plane/envoy/extensions/filters/http/router/v3"
	hcmv3 "github.com/envoyproxy/go-control-plane/envoy/extensions/filters/network/http_connection_manager/v3"
	"github.com/envoyproxy/go-control-plane/pkg/wellknown"

	"github.com/golang/protobuf/ptypes"
	logger "github.com/wso2/micro-gw/loggers"
)

// getHTTPFilters generates httpFilter configuration
func getHTTPFilters() []*hcmv3.HttpFilter {
	extAauth := getExtAuthzHTTPFilter()
	router := getRouterHTTPFilter()

	httpFilters := []*hcmv3.HttpFilter{
		extAauth,
		router,
	}
	return httpFilters
}

// getRouterHTTPFilter gets router http filter.
func getRouterHTTPFilter() *hcmv3.HttpFilter {

	routeFilterConf := routerv3.Router{
		DynamicStats:             nil,
		StartChildSpan:           false,
		UpstreamLog:              nil,
		SuppressEnvoyHeaders:     false,
		StrictCheckHeaders:       nil,
		RespectExpectedRqTimeout: false,
	}

	routeFilterTypedConf, err := ptypes.MarshalAny(&routeFilterConf)
	if err != nil {
		logger.LoggerOasparser.Error("Error marshaling route filter configs. ", err)
	}
	filter := hcmv3.HttpFilter{
		Name:       wellknown.Router,
		ConfigType: &hcmv3.HttpFilter_TypedConfig{TypedConfig: routeFilterTypedConf},
	}
	return &filter
}

// UpgradeFilters that are applied in websocket upgrade mode
func getUpgradeFilters() []*hcmv3.HttpFilter {
	extAauth := getExtAuthzHTTPFilter()
	router := getRouterHTTPFilter()
	// TODO : (LahiruUdayanga) Configure the custom C++ filter.
	httpFilters := []*hcmv3.HttpFilter{
		extAauth,
		router,
	}
	return httpFilters
}

// getExtAuthzHTTPFilter gets ExtAauthz http filter.
func getExtAuthzHTTPFilter() *hcmv3.HttpFilter {
	extAuthzConfig := &ext_authv3.ExtAuthz{
		WithRequestBody: &ext_authv3.BufferSettings{
			MaxRequestBytes:     1024,
			AllowPartialMessage: false,
		},
		// This would clear the route cache only if there is a header added/removed or changed
		// within ext-authz filter. Without this configuration, the API cannot have production
		// and sandbox endpoints both at once as the cluster is set based on the header added
		// from the ext-authz filter.
		ClearRouteCache:     true,
		TransportApiVersion: corev3.ApiVersion_V3,
		Services: &ext_authv3.ExtAuthz_GrpcService{
			GrpcService: &corev3.GrpcService{
				TargetSpecifier: &corev3.GrpcService_EnvoyGrpc_{
					EnvoyGrpc: &corev3.GrpcService_EnvoyGrpc{
						ClusterName: extAuthzClusterName,
					},
				},
				Timeout: ptypes.DurationProto(20 * time.Second),
			},
		},
	}
	ext, err2 := ptypes.MarshalAny(extAuthzConfig)
	if err2 != nil {
		logger.LoggerOasparser.Error(err2)
	}
	extAuthzFilter := hcmv3.HttpFilter{
		Name: extAuthzFilterName,
		ConfigType: &hcmv3.HttpFilter_TypedConfig{
			TypedConfig: ext,
		},
	}
	return &extAuthzFilter
}
