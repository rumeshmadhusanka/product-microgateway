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

package org.wso2am.micro.gw.tests.mockconsul;

import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.http.HttpStatus;
import io.netty.handler.codec.http.HttpHeaderNames;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2am.micro.gw.tests.common.BaseTestCase;
import org.wso2am.micro.gw.tests.util.*;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ConsulTestCase extends BaseTestCase {
    public static int pollInterval = 5; //seconds
    public static final String mockConsulServerURL = "http://localhost:8500"; //exposed to the host server
    public static final String routerConfigDumpURL = "http://localhost:9000/config_dump"; //exposed to the host server
    public static final String statusContext = "/status/"; //loading consul status to mockConsul server
    public static final String upstreamDefaultHost = "5001";
    public static final String mockBackendServerPort = "";

    @BeforeClass(description = "initialise the mgw pack")
    void start() throws Exception {
        File targetClassesDir = new File(ConsulTestCase.class.getProtectionDomain().getCodeSource().
                getLocation().getPath());
        String configPath = targetClassesDir.toString() + File.separator + "conf" + File.separator +
                "consul" + File.separator + "http" + File.separator + "config.toml";
        super.startMGW(configPath);
        //mockConsulApis.yaml file should put to the resources/apis/openApis folder
        String apiZipfile = ApiProjectGenerator.createApictlProjZip("apis/openApis/mockConsulApis.yaml");
        // Set header
        Map<String, String> headers = new HashMap<String, String>();
        headers.put(HttpHeaderNames.AUTHORIZATION.toString(), "Basic YWRtaW46YWRtaW4=");
        HttpsPostMultipart multipart = new HttpsPostMultipart(BaseTestCase.getImportAPIServiceURLHttps(
                TestConstant.ADAPTER_IMPORT_API_RESOURCE), headers);
        multipart.addFilePart("file", new File(apiZipfile));
        HttpResponse response = multipart.getResponse();
        Assert.assertNotNull(response);
        Assert.assertEquals(response.getResponseCode(), HttpStatus.SC_OK, "Response code mismatched");
    }

    @AfterClass(description = "stop the mgw pack")
    void stop() {
        super.stopMGW();
    }

    //Used in HTTPS test case too
    protected static void checkFirstTestCase() throws InterruptedException, IOException {
        //wait till the adapter picks up the change and update the router
        TimeUnit.SECONDS.sleep(pollInterval * 2L + 2);
        //get router's config
        HttpResponse response = HttpClientRequest.doGet(routerConfigDumpURL);
        String assertStr = "This data should be loaded according to syntax parse";
        Assert.assertTrue(response.getData().contains("3000"), assertStr);
        Assert.assertTrue(response.getData().contains("8080"), assertStr);
        Assert.assertTrue(response.getData().contains("4000"), assertStr);
        Assert.assertFalse(response.getData().contains("5001"), "Default host should be removed");
        Assert.assertFalse(response.getData().contains("6000"), "Only selected tags should be loaded to config");
        Assert.assertFalse(response.getData().contains("7000"), "Health check critical nodes should be removed");
        Assert.assertFalse(response.getData().contains("5000"), "Only nodes corresponding to selected service "
                + "should be loaded");
    }

    @Test(description = "Remove default host")
    public void removeDefaultHost() throws IOException, InterruptedException {
        String consulServerState = "1";
        //load the state to the mock consul server
        HttpResponse loadStateResponse = HttpClientRequest.doGet(mockConsulServerURL + statusContext
                + consulServerState);
        //response contains the consulServerState
        Assert.assertTrue(loadStateResponse.getData().contains(consulServerState), "Mock Consul server state failed to load");
        //wait till the adapter picks up the change and update the router
        TimeUnit.SECONDS.sleep(pollInterval * 2L + 2);
        HttpResponse response = HttpClientRequest.doGet(routerConfigDumpURL);
        Assert.assertFalse(response.getData().contains(upstreamDefaultHost), "Default host has not removed");
    }

    @Test(description = "Load upstreams into router config")
    public void loadConsulUpstreams() throws IOException, InterruptedException {
        String consulServerState = "1";
        //load the test case data to the consul mock server
        HttpResponse loadStateResponse = HttpClientRequest.doGet(mockConsulServerURL + statusContext
                + consulServerState);
        //response contains the consulServerState
        Assert.assertTrue(loadStateResponse.getData().contains(consulServerState), "Mock Consul server state failed to load");
        //wait till the adapter picks up the change and update the router
        TimeUnit.SECONDS.sleep(pollInterval * 2L + 2);
        HttpResponse response = HttpClientRequest.doGet(routerConfigDumpURL);
        //sandbox resource level
        Assert.assertTrue(response.getData().contains("3000"), "");
        //production API level

    }


    @Test(description = "Change in consul config/Health check fail reflects in router")
    public void consulReflectChange() throws IOException, InterruptedException {
        String consulServerState = "2";
        //load the first test case state
        HttpClientRequest.doGet(mockConsulServerURL + statusContext + "1");
        //wait till the adapter picks up the change and update the router
        TimeUnit.SECONDS.sleep(pollInterval + 2);
        //load the current test case data to the consul mock server
        HttpClientRequest.doGet(mockConsulServerURL + statusContext + consulServerState);
        //wait till the adapter picks up the change and update the router
        TimeUnit.SECONDS.sleep(pollInterval + 2);
        //get router's config
        HttpResponse response = HttpClientRequest.doGet(routerConfigDumpURL);
        String assertStr = "This data should be loaded according to syntax parse";
        Assert.assertTrue(response.getData().contains("3000"), assertStr);
        Assert.assertTrue(response.getData().contains("8080"), assertStr);
        Assert.assertTrue(response.getData().contains("4500"), assertStr);
        Assert.assertTrue(response.getData().contains("7000"), assertStr);
        Assert.assertFalse(response.getData().contains("5001"), "Default host should be removed");
        Assert.assertFalse(response.getData().contains("6000"), "Only selected tags should be loaded to config");
        Assert.assertFalse(response.getData().contains("4000"), "Health check critical nodes should be removed");
        Assert.assertFalse(response.getData().contains("5000"), "Only nodes corresponding to selected service " +
                "should be loaded");
    }

    @Test(description = "Consul server becomes unreachable, router state should not be changed")
    public void consulServerDown() throws IOException, InterruptedException {
        String consulServerState = "3";
        //load the first state
        HttpClientRequest.doGet(mockConsulServerURL + statusContext + "1");
        //wait till the adapter picks up the change and update the router
        TimeUnit.SECONDS.sleep(pollInterval + 2);

        HttpClientRequest.doGet(mockConsulServerURL + statusContext + consulServerState);
        //get router's config
        HttpResponse response = HttpClientRequest.doGet(routerConfigDumpURL);
        TimeUnit.SECONDS.sleep(pollInterval * 2L + 2);
        //router config should be equal to the first state
        String assertStr = "This data should be loaded according to syntax parse";
        Assert.assertTrue(response.getData().contains("3000"), assertStr);
        Assert.assertTrue(response.getData().contains("8080"), assertStr);
        Assert.assertTrue(response.getData().contains("4000"), assertStr);
        Assert.assertFalse(response.getData().contains("5001"), "Default host should be removed");
        Assert.assertFalse(response.getData().contains("6000"), "Only selected tags should be loaded to config");
        Assert.assertFalse(response.getData().contains("7000"), "Health check critical nodes should be removed");
        Assert.assertFalse(response.getData().contains("5000"), "Only nodes corresponding to selected service " +
                "should be loaded");
    }
}
