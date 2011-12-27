/**
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

import org.apache.camel.CamelContext;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.junit.BeforeClass;
import org.junit.Test;
import org.jvnet.mock_javamail.Mailbox;

import static org.junit.Assert.assertEquals;

/**
 * Unit test of our routes
 */
public class ReportIncidentRoutesTest {

    // should be the same address as we have in our route
    private static final String URL = "http://localhost:{{port}}/camel-example-reportincident/webservices/incident";

    protected CamelContext camel;

    @BeforeClass
    public static void setupFreePort() throws Exception {
        // find a free port number from 9100 onwards, and write that in the custom.properties file
        // which we will use for the unit tests, to avoid port number in use problems
        int port = AvailablePortFinder.getNextAvailable(9100);
        String s = "port=" + port;
        File custom = new File("target/custom.properties");
        FileOutputStream fos = new FileOutputStream(custom);
        fos.write(s.getBytes());
        fos.close();
    }

    protected void startCamel() throws Exception {
        camel = new DefaultCamelContext();

        // add properties component
        camel.addComponent("properties", new PropertiesComponent("classpath:incident.properties,file:target/custom.properties"));

        ReportIncidentRoutes routes = new ReportIncidentRoutes();
        routes.setUsingServletTransport(false);
        camel.addRoutes(routes);
        camel.start();
    }
    
    protected void stopCamel() throws Exception {
        camel.stop();
    }

    protected static ReportIncidentEndpoint createCXFClient(String url) {
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
        // assert mailbox is empty before starting
        Mailbox inbox = Mailbox.get("incident@mycompany.com");
        inbox.clear();
        assertEquals("Should not have mails", 0, inbox.size());

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
        String url = camel.resolvePropertyPlaceholders(URL);
        ReportIncidentEndpoint client = createCXFClient(url);
        OutputReportIncident out = client.reportIncident(input);

        // assert we got a OK back
        assertEquals("0", out.getCode());

        // let some time pass to allow Camel to pickup the file and send it as an email
        Thread.sleep(3000);

        // assert mail box
        assertEquals("Should have got 1 mail", 1, inbox.size());
    }
}
