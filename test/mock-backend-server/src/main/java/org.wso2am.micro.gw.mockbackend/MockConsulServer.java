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

import com.sun.net.httpserver.*;
import org.apache.commons.text.StringSubstitutor;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ConsulServer starts an HTTP server to mock the behaviour of a Consul Client/Server.
 */
public class MockConsulServer extends Thread {
    private static final Logger logger = Logger.getLogger(MockConsulServer.class.getName());
    private final int port;
    private final String scheme; //http, https
    private List<Upstream> upstreams = new ArrayList<>(); //list of service nodes
    private HttpServer httpServer;


    /**
     * Instantiates a new Mock consul server.
     *
     * @param port   the port
     * @param scheme the scheme
     */
    public MockConsulServer(int port, String scheme) {
        this.port = port;
        this.scheme = scheme;
    }

//todo remove this main method
    public static void main(String[] args) {
        MockConsulServer consulServerHttp = new MockConsulServer(Constants.MOCK_CONSUL_SERVER_HTTP_PORT, "http");
        consulServerHttp.start();
        MockConsulServer consulServerHttps = new MockConsulServer(Constants.MOCK_CONSUL_SERVER_HTTPS_PORT,
                "https");
        consulServerHttps.start();
        ConsulServerState.loadStates();
    }

    /**
     * Add node.
     *
     * @param upstream the node
     */
    public void addUpstream(Upstream upstream) {
        upstreams.add(upstream);
    }

    /**
     * Reset server.
     */
    public void resetServer() {
        upstreams = new ArrayList<>();
    }

    /**
     * Stop server.
     */
    public void stopServer() {
        httpServer.stop(0);
    }

    /**
     * Filters the available nodes by service name, datacenter and health check status.
     *
     * @param datacenter          name of the datacenter
     * @param serviceName         name of the Service
     * @param healthChecksPassing status of health check
     * @return List of Nodes matching the criteria
     */
    public List<Upstream> get(String datacenter, String serviceName, boolean healthChecksPassing) {
        List<Upstream> upstreams = new ArrayList<>();
        for (Upstream upstream : get(datacenter, serviceName)) {
            if (healthChecksPassing == upstream.getHealthCheck().isPassing()) {
                upstreams.add(upstream);
            }
        }
        return upstreams;
    }

    /**
     * Filters the available nodes by service name and datacenter.
     *
     * @param datacenter  name of the datacenter
     * @param serviceName name of the Service
     * @return List of Nodes matching the criteria
     * @see #get(String) #get(String)
     * @see #get(String, String, boolean) #get(String, String, boolean)
     */
    public List<Upstream> get(String datacenter, String serviceName) {
        List<Upstream> ret = new ArrayList<>();
        for (Upstream upstream : get(serviceName)) {
            if (upstream.getDatacenter().getName().equals(datacenter)) {
                ret.add(upstream);
            }
        }
        return ret;
    }

    /**
     * Filters the available nodes by service name.
     *
     * @param serviceName name of the Service
     * @return List of Nodes matching the criteria
     * @see #get(String, String) #get(String, String)
     * @see #get(String, String, boolean) #get(String, String, boolean)
     */
    public List<Upstream> get(String serviceName) {
        List<Upstream> ret = new ArrayList<>();
        for (Upstream upstream : upstreams) {
            if (upstream.getService().getName().equals(serviceName)) {
                ret.add(upstream);
            }
        }
        return ret;
    }

    private void sendHTTPResponse(HttpExchange exchange, byte[] response) throws IOException {
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    /**
     * Splits the URI into path param and query params.
     * Splits only one path param.
     * Splits all the query params.
     *
     * @param base base of the URI
     * @param uri  complete URI(without the domain and port)
     * @return Map that contains 1 path param and all the query params
     */
    private Map<String, String> paramsAndQueryToMap(String base, String uri) {
        Map<String, String> result = new HashMap<>();
        String[] rest = uri.split(base);
        if (rest.length != 2) {
            return result;
        }
        String nameAndQuery = rest[1];
        String[] sp = nameAndQuery.split("\\?");
        String pathParam = sp[0];
        result.put("PATH_PARAM", pathParam);
        if (sp.length != 2) {
            return result;
        }
        String query = sp[1];
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) {
                result.put(entry[0], entry[1]);
            } else {
                result.put(entry[0], "");
            }
        }
        return result;
    }

    /**
     * Creates a JSON String for a given Node.
     *
     * @param upstream Node which the JSON for response is created.
     * @return String JSON for the response.
     */
    private String buildJsonForNode(Upstream upstream) {
        System.out.println(upstream);
        String jsonTemplate = "{\n" +
                "        \"Node\": {\n" +
                "            \"ID\": \"${nodeId}\",\n" +
                "            \"Node\": \"${nodeName}\",\n" +
                "            \"Address\": \"${consulNodeAddress}\",\n" +
                "            \"Datacenter\": \"${datacenter}\",\n" +
                "            \"TaggedAddresses\": {\n" +
                "                \"lan\": \"${consulNodeAddress}\",\n" +
                "                \"lan_ipv4\": \"${consulNodeAddress}\",\n" +
                "                \"wan\": \"${consulNodeAddress}\",\n" +
                "                \"wan_ipv4\": \"${consulNodeAddress}\"\n" +
                "            },\n" +
                "            \"Meta\": {\n" +
                "                \"consul-network-segment\": \"\"\n" +
                "            },\n" +
                "            \"CreateIndex\": ${createIndex},\n" +
                "            \"ModifyIndex\": ${createIndex}\n" +
                "        },\n" +
                "        \"Service\": {\n" +
                "            \"ID\": \"${serviceId}\",\n" +
                "            \"Service\": \"${serviceName}\",\n" +
                "            \"Tags\": [\n" +
                "                \"${tag}\"\n" +
                "            ],\n" +
                "            \"Address\": \"${address}\",\n" +
                "            \"Meta\": null,\n" +
                "            \"Port\": ${port},\n" +
                "            \"Weights\": {\n" +
                "                \"Passing\": 1,\n" +
                "                \"Warning\": 1\n" +
                "            },\n" +
                "            \"EnableTagOverride\": false,\n" +
                "            \"Proxy\": {\n" +
                "                \"MeshGateway\": {},\n" +
                "                \"Expose\": {}\n" +
                "            },\n" +
                "            \"Connect\": {},\n" +
                "            \"CreateIndex\": 14,\n" +
                "            \"ModifyIndex\": 14\n" +
                "        },\n" +
                "        \"Checks\": [\n" +
                "            {\n" +
                "                \"Node\": \"${nodeName}\",\n" +
                "                \"CheckID\": \"api 3000\",\n" +
                "                \"Name\": \"health check on 3000\",\n" +
                "                \"Status\": \"${healthStatus}\",\n" +
                "                \"Notes\": \"\",\n" +
                "                \"Output\": \"Get \\\"http://localhost:3000\\\": dial tcp 127.0.0.1:3000: connect:" +
                " connection refused\",\n" +
                "                \"ServiceID\": \"3000l\",\n" +
                "                \"ServiceName\": \"web\",\n" +
                "                \"ServiceTags\": [\n" +
                "                    \"golang\"\n" +
                "                ],\n" +
                "                \"Type\": \"http\",\n" +
                "                \"Definition\": {},\n" +
                "                \"CreateIndex\": 14,\n" +
                "                \"ModifyIndex\": 101\n" +
                "            }\n" +
                "        ]\n" +
                "    },";
        Map<String, String> substitutes = new HashMap<>();
        substitutes.put("nodeId", upstream.getConsulNode().getNodeId());
        substitutes.put("nodeName", upstream.getConsulNode().getNodeName());
        substitutes.put("datacenter", upstream.getDatacenter().getName());
        substitutes.put("consulNodeAddress", upstream.getConsulNode().getAddress());
        substitutes.put("address", upstream.getAddress());
        substitutes.put("createIndex", "50");
        substitutes.put("serviceId", upstream.getId());
        substitutes.put("serviceName", upstream.getService().getName());
        substitutes.put("port", Integer.toString(upstream.getPort()));
        substitutes.put("tag", upstream.getTags()[0]);
        substitutes.put("healthStatus", upstream.getHealthCheck().getStatus());
        StringSubstitutor substitutor = new StringSubstitutor(substitutes);
        return substitutor.replace(jsonTemplate);
    }

    /**
     * starts the mock HTTP server.
     * /v1/health/service/ is where the adapter queries.
     * /tc/ is where the integration tests send HTTP requests to change the state of the mock consul server.
     */
    @Override
    public void run() {
        try {
            String host = "0.0.0.0";
            if (scheme.equals("https")) { //https
                this.httpServer = HttpsServer.create(new InetSocketAddress(host, port), 0);
                //todo https
                String tlsVersion = "TLS";
                char[] password = "wso2carbon".toCharArray();
                SSLContext sslContext = SSLContext.getInstance(tlsVersion);
                // initialise the keystore
                KeyStore keyStore = KeyStore.getInstance("JKS");
                InputStream keyStoreIS = Thread.currentThread().getContextClassLoader()
                        .getResourceAsStream("wso2carbon.jks");
                keyStore.load(keyStoreIS, password);
                // setup the key manager factory
                KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
                kmf.init(keyStore, password);
                // setup the trust manager factory
                TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
                KeyStore trustStore = KeyStore.getInstance("JKS");
                InputStream trustStoreIS = Thread.currentThread().getContextClassLoader()
                        .getResourceAsStream("client-truststore.jks");
                trustStore.load(trustStoreIS, password);
                tmf.init(trustStore);
                // setup the HTTPS context and parameters
                sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

                ((HttpsServer)httpServer).setHttpsConfigurator(new HttpsConfigurator(sslContext) {
                    public void configure(HttpsParameters params) {
                        try {
                            // initialise the SSL context
                            SSLContext sslContext = SSLContext.getDefault();
                            SSLEngine engine = sslContext.createSSLEngine();
                            params.setNeedClientAuth(true);
                            params.setCipherSuites(engine.getEnabledCipherSuites());
                            params.setProtocols(engine.getEnabledProtocols());
                            // get the default parameters
                            SSLParameters defaultSSLParameters = sslContext
                                    .getDefaultSSLParameters();
                            params.setSSLParameters(defaultSSLParameters);
                        } catch (Exception ex) {
                            logger.severe("Failed to create HTTPS port");
                        }
                    }
                });
                httpServer.setExecutor(Executors.newCachedThreadPool());//multi threaded
            } else { //http
                this.httpServer = HttpServer.create(new InetSocketAddress(host, port), 0);
            }
            createContextsForHttpServer(httpServer);

            httpServer.start();
            logger.log(Level.INFO, "Consul mock server started: " + this + scheme + "://" + host +
                    ":" + this.port);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error starting the consul mock server: ", e);
        }
    }

    private void createContextsForHttpServer(HttpServer httpServer){
        String consulContext = "/v1/health/service/";
        httpServer.createContext(consulContext, exchange -> {
            System.out.println(exchange.getRequestURI());
            Map<String, String> map = paramsAndQueryToMap(consulContext, exchange.getRequestURI().toString());
            String serviceName = map.get("PATH_PARAM");
            //dc=local-dc&passing=1
            List<Upstream> resultUpstreams = get(serviceName);
            if (map.containsKey("dc")) {
                String dc = map.get("dc");
                resultUpstreams = get(dc, serviceName);
                if (map.containsKey("passing")) {
                    resultUpstreams = get(dc, serviceName, true);
                }
            }
            byte[] response = buildAllResultsToJsonArray(resultUpstreams).getBytes();
            sendHTTPResponse(exchange, response);
            exchange.getResponseBody().write(response);
            exchange.close();

        });
        String stateLoadPath = "/status/";
        httpServer.createContext(stateLoadPath, exchange -> {
            System.out.println(exchange.getRequestURI());
            Map<String, String> map = paramsAndQueryToMap(stateLoadPath, exchange.getRequestURI().toString());
            String testCase = map.get("PATH_PARAM");
            //call methods to change consul server state
            resetServer(); //reset the state before loading a new state
            if (ConsulServerState.states.containsKey(testCase)) {
                ConsulServerState.states.get(testCase).loadState(this);
                sendHTTPResponse(exchange, testCase.getBytes());
            } else {
                logger.log(Level.SEVERE, "Test case not found: " + testCase);
                sendHTTPResponse(exchange, ("Test case not found: " + testCase).getBytes());
            }
        });
    }

    /**
     * Gives the output JSON array to the given set of Nodes.
     *
     * @param upstreamArrayList List of Nodes
     * @return String JSON array
     */
    private String buildAllResultsToJsonArray(List<Upstream> upstreamArrayList) {
        StringBuilder sb = new StringBuilder();
        for (Upstream upstream : upstreamArrayList) {
            sb.append(this.buildJsonForNode(upstream));
        }
        sb.setLength(sb.length() - 1); // remove the trailing ","
        sb.insert(0, "[");
        sb.append("]");
        return sb.toString();
    }
}
