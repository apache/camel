/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.example.reportincident;

import java.io.File;
import java.io.FileOutputStream;

import org.apache.camel.spring.Main;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.util.FileUtil;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Unit test of our routes
 */
public class ReportIncidentRoutesTest {

    // should be the same address as we have in our route
    private static String url;

    protected Main main;

    @BeforeClass
    public static void setupFreePort() throws Exception {
        // find a free port number, and write that in the custom.properties file
        // which we will use for the unit tests, to avoid port number in use problems
        int port = AvailablePortFinder.getNextAvailable();
        String s = "proxy.port=" + port;
        int port2 = AvailablePortFinder.getNextAvailable();
        String s2 = "real.port=" + port2;

        File custom = new File("target/custom.properties");
        FileOutputStream fos = new FileOutputStream(custom);
        fos.write(s.getBytes());
        fos.write("\n".getBytes());
        fos.write(s2.getBytes());
        fos.close();

        url = "http://localhost:" + port + "/camel-example-cxf-proxy/webservices/incident";
    }

    @AfterClass
    public static void cleanup() {
        File custom = new File("target/custom.properties");
        FileUtil.deleteFile(custom);
    }

    protected void startCamel() throws Exception {
        if (!"true".equalsIgnoreCase(System.getProperty("skipStartingCamelContext"))) {
            main = new Main();
            main.setApplicationContextUri("META-INF/spring/camel-config.xml");
            main.start();
        } else {
            System.out.println("Skipping starting CamelContext as system property skipStartingCamelContext is set to be true.");
        }
    }
    
    protected void stopCamel() throws Exception {
        if (main != null) {
            main.stop();
        }
    }

    protected static ReportIncidentEndpoint createCXFClient() {
        // we use CXF to create a client for us as its easier than JAXWS and works
        JaxWsProxyFactoryBean factory = new JaxWsProxyFactoryBean();
        factory.setServiceClass(ReportIncidentEndpoint.class);
        factory.setAddress(url);
        return (ReportIncidentEndpoint) factory.create();
    }

    @Test
    public void testReportIncident() throws Exception {
        // start camel
        startCamel();

        runTest();

        // stop camel
        stopCamel();
    }
    
    protected void runTest() throws Exception {
       
        // create input parameter
        InputReportIncident input = new InputReportIncident();
        input.setIncidentId("123");
        input.setIncidentDate("2008-08-18");
        input.setGivenName("Claus");
        input.setFamilyName("Ibsen");
        input.setSummary("Bla");
        input.setDetails("Bla bla");
        input.setEmail("davsclaus@apache.org");
        input.setPhone("0045 2962 7576");

        // create the webservice client and send the request
        
        ReportIncidentEndpoint client = createCXFClient();
        OutputReportIncident out = client.reportIncident(input);

        // assert we got a OK back
        Assert.assertEquals("OK;456", out.getCode());
    }
}
