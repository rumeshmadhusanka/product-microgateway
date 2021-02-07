/*
 * Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2am.micro.gw.mockbackend;

import java.util.HashMap;
import java.util.Map;

interface ConsulState {
    void loadState(MockConsulServer server);
}

//classes to hold data required for Consul
class ConsulAgent {
    private final String nodeId;
    private final String nodeName;
    private final String address;

    public ConsulAgent(String nodeId, String nodeName, String address) {
        this.nodeId = nodeId;
        this.nodeName = nodeName;
        this.address = address;
    }

    public String getAddress() {
        return address;
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getNodeName() {
        return nodeName;
    }
}

class Upstream {
    private final String id;
    private final Datacenter datacenter;
    private final Service service;
    private final String address;
    private final int port;
    private final String[] tags;
    private final HealthCheck healthCheck;
    private final ConsulAgent consulAgent;

    public Upstream(String id, Datacenter datacenter, Service service, String address, int port, String[] tags, HealthCheck healthCheck, ConsulAgent consulAgent) {
        this.id = id;
        this.datacenter = datacenter;
        this.service = service;
        this.address = address;
        this.port = port;
        this.tags = tags;
        this.healthCheck = healthCheck;
        this.consulAgent = consulAgent;
    }

    public String getId() {
        return id;
    }

    public ConsulAgent getConsulNode() {
        return consulAgent;
    }

    public HealthCheck getHealthCheck() {
        return healthCheck;
    }

    public Datacenter getDatacenter() {
        return datacenter;
    }

    public Service getService() {
        return service;
    }

    public String getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public String[] getTags() {
        return tags;
    }
}

class Service {

    private final String name;
    private final String nameSpace;

    public Service(String name, String nameSpace) {
        this.name = name;
        this.nameSpace = nameSpace;

    }

    public Service(String name) {
        this.name = name;
        this.nameSpace = "";
    }

    public String getName() {
        return name;
    }
}

class HealthCheck {
    private String status;

    public HealthCheck() {
        this.status = "passing";
    }

    public String getStatus() {
        return status;
    }

    public boolean isPassing() {
        return status.equals("passing");
    }

    public void SetCritical() {
        this.status = "critical";
    }
}

class Datacenter {
    private final String name;

    public Datacenter(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}

/**
 * ConsulTestCases contains the statuses required for testing
 */
class ConsulServerState {
    public static Map<String, ConsulState> states = new HashMap<>();

    public static void loadStates() {
        Datacenter awsDatacenter = new Datacenter("aws-east");
        Datacenter gcpDatacenter = new Datacenter("gcp-west");
        ConsulAgent consulAgent = new ConsulAgent("d674a962-2e8e-d765-aa91-6d4d66fecacb", "mgw-local",
                "127.0.0.1");
        Service petService = new Service("pet", "");
        Service pizzaService = new Service("pizza", "");
        String[] productionTag = {"production"};
        String[] devTag = {"dev"};
        HealthCheck passingHealthCheck = new HealthCheck();
        HealthCheck failingHealthCheck = new HealthCheck();
        failingHealthCheck.SetCritical();
        String mockBackendHost = "mockBackend";
        int mockBackendUpstreamProductionPort = Constants.MOCK_BACKEND_SERVER_PORT;
        int mockBackendUpstreamSandboxPort = Constants.MOCK_SANDBOX_SERVER_PORT;


        //consul([gcp-west,aws-east].pet.[production],http://localhost:5001)
        //consul(pizza,https://localhost:5001)
        states.put("1", consulServer -> {
            Upstream u1 = new Upstream("abc", gcpDatacenter, petService, mockBackendHost,
                    mockBackendUpstreamProductionPort, productionTag, passingHealthCheck, consulAgent);
            consulServer.addUpstream(u1);
            Upstream u2 = new Upstream("pqr", awsDatacenter, petService, mockBackendHost,
                    mockBackendUpstreamProductionPort, productionTag, passingHealthCheck, consulAgent);
            consulServer.addUpstream(u2);
            Upstream u3 = new Upstream("lmn", awsDatacenter, pizzaService, mockBackendHost,
                    mockBackendUpstreamSandboxPort, productionTag, passingHealthCheck, consulAgent);
            consulServer.addUpstream(u3);
            Upstream u4 = new Upstream("hij", awsDatacenter, pizzaService, mockBackendHost,
                    mockBackendUpstreamSandboxPort, productionTag, passingHealthCheck, consulAgent);
            consulServer.addUpstream(u4);

            //not included
            Upstream u5 = new Upstream("xyz", gcpDatacenter, petService, "127.0.0.0",
                    6000, productionTag, failingHealthCheck, consulAgent);
            consulServer.addUpstream(u5);
            Upstream u6 = new Upstream("qwe", gcpDatacenter, petService, "127.0.0.0",
                    6001, devTag, passingHealthCheck, consulAgent);
            consulServer.addUpstream(u6);
        });

        states.put("2", consulServer -> {
            Upstream u1 = new Upstream("abc", gcpDatacenter, petService, mockBackendHost,
                    mockBackendUpstreamProductionPort, productionTag, failingHealthCheck, consulAgent);
            consulServer.addUpstream(u1);
            Upstream u2 = new Upstream("pqr", awsDatacenter, petService, mockBackendHost,
                    mockBackendUpstreamProductionPort, productionTag, passingHealthCheck, consulAgent);
            consulServer.addUpstream(u2);
            Upstream u3 = new Upstream("lmn", awsDatacenter, pizzaService, mockBackendHost,
                    mockBackendUpstreamSandboxPort, productionTag, passingHealthCheck, consulAgent);
            consulServer.addUpstream(u3);
            Upstream u4 = new Upstream("hij", awsDatacenter, pizzaService, mockBackendHost,
                    mockBackendUpstreamSandboxPort, productionTag, failingHealthCheck, consulAgent);
            consulServer.addUpstream(u4);

            //not included
            Upstream u5 = new Upstream("xyz", gcpDatacenter, petService, "127.0.0.0",
                    6000, productionTag, failingHealthCheck, consulAgent);
            consulServer.addUpstream(u5);
            Upstream u6 = new Upstream("qwe", gcpDatacenter, petService, "127.0.0.0",
                    6001, devTag, passingHealthCheck, consulAgent);
            consulServer.addUpstream(u6);
        });

        states.put("3", MockConsulServer::stopServer);
    }
}