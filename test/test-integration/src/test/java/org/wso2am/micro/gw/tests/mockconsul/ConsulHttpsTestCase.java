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

public class ConsulHttpsTestCase extends BaseTestCase {
    public static final String mockConsulHttpsServerURL = "https://localhost:8501";

    @BeforeClass(description = "initialise a mock consul server")
    void start() throws Exception {
        File targetClassesDir = new File(ConsulTestCase.class.getProtectionDomain().getCodeSource().
                getLocation().getPath());
        String configPath = targetClassesDir.toString() + File.separator + "conf" + File.separator +
                "consul" + File.separator + "https" + File.separator + "config.toml";
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

    @AfterClass(description = "stop the mock consul server")
    void stop() {
        super.stopMGW();
    }

    @Test(description = "Connect to consul server via HTTPS")
    public void consulLoadConfigToRouterTest() throws IOException, InterruptedException {
        String testCaseName = "1";
        //load the test case data to the consul mock server
        String requestUrl = mockConsulHttpsServerURL + ConsulTestCase.statusContext + testCaseName;
        System.out.println(requestUrl);
        HttpResponse tcResp = HttpsClientRequest.doGet(requestUrl);
        Assert.assertTrue(tcResp.getData().contains(testCaseName), "test case not loaded");
        ConsulTestCase.checkFirstTestCase();
    }


}
