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

package model

import (
	"fmt"
	"os"
	"testing"

	"github.com/getkin/kin-openapi/openapi3"
	"github.com/stretchr/testify/assert"
)

func TestSetInfoOpenAPI(t *testing.T) {
	type setInfoTestItem struct {
		input   openapi3.Swagger
		result  MgwSwagger
		message string
	}
	dataItems := []setInfoTestItem{
		{
			openapi3.Swagger{
				OpenAPI: "openApi",
				Info: &openapi3.Info{
					Title:       "petsore",
					Description: "Swagger definition",
					Version:     "1.0",
				},
			},

			MgwSwagger{
				apiType:     "HTTP",
				description: "Swagger definition",
				title:       "petsore",
				version:     "1.0",
			},
			"usual case",
		},
		{
			openapi3.Swagger{
				OpenAPI: "openApi",
				Info:    nil,
			},
			MgwSwagger{
				id:          "",
				apiType:     "HTTP",
				description: "",
				title:       "",
				version:     "",
			},
			"when info section is null",
		},
	}
	for _, item := range dataItems {
		var mgwSwagger MgwSwagger
		mgwSwagger.SetInfoOpenAPI(item.input)
		assert.Equal(t, item.result, mgwSwagger, item.message)
	}
}

func TestSetResourcesOpenAPI(t *testing.T) {
	type setResourcesTestItem struct {
		input   openapi3.Swagger
		result  []Resource
		message string
	}
	dataItems := []setResourcesTestItem{
		{
			openapi3.Swagger{
				Paths: nil,
			},
			nil,
			"when paths are nil",
		},
		{
			openapi3.Swagger{
				Paths: openapi3.Paths{
					"/pet/{petId}": &openapi3.PathItem{
						Get: &openapi3.Operation{
							OperationID: "petfindbyid",
						},
						Summary:     "pet find by id",
						Description: "this retrieve data from id",
					},
				},
			},
			[]Resource{
				{
					path:        "/pet/{petId}",
					methods:     []string{"GET"},
					description: "this retrieve data from id",
					iD:          "petfindbyid",
					summary:     "pet find by id",
				},
			},
			"usual case",
		},
	}
	for _, item := range dataItems {
		resultResources := setResourcesOpenAPI(item.input)
		if item.result != nil {
			assert.Equal(t, item.result[0].path, resultResources[0].GetPath(), item.message)
			assert.Equal(t, item.result[0].methods, resultResources[0].GetMethod(), item.message)
			assert.Equal(t, item.result[0].description, resultResources[0].description, item.message)
			assert.Equal(t, item.result[0].summary, resultResources[0].summary, item.message)
		} else {
			assert.Equal(t, item.result, resultResources, item.message)
		}
	}
}

func TestGetHostandBasepathandPort(t *testing.T) {
	type setResourcesTestItem struct {
		input   string
		result  Endpoint
		message string
	}
	fmt.Println(os.Getwd())
	dataItems := []setResourcesTestItem{
		{
			input: "https://petstore.io:8000/api/v2",
			result: Endpoint{
				Host:     "petstore.io",
				Basepath: "/api/v2",
				Port:     8000,
				URLType:  "https",
			},
			message: "all the details are provided in the endpoint",
		},
		{
			input: "https://petstore.io:8000/api/v2",
			result: Endpoint{
				Host:     "petstore.io",
				Basepath: "/api/v2",
				Port:     8000,
				URLType:  "https",
			},
			message: "when port is not provided", //here should find a way to readi configs in tests
		},
		{
			input: "petstore.io:8000/api/v2",
			result: Endpoint{
				Host:     "petstore.io",
				Basepath: "/api/v2",
				Port:     8000,
				URLType:  "http",
			},
			message: "when protocol is not provided",
		},
	}
	for _, item := range dataItems {
		resultResources := getHostandBasepathandPort(item.input)
		assert.Equal(t, item.result, resultResources, item.message)
	}
}
