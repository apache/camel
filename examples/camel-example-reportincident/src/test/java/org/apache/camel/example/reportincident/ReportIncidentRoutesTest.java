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

import junit.framework.TestCase;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.junit.Test;
import org.jvnet.mock_javamail.Mailbox;

import static org.junit.Assert.assertEquals;

/**
 * Unit test of our routes
 */
public class ReportIncidentRoutesTest {

    // should be the same address as we have in our route
    private static final String URL = "http://localhost:9080/camel-example-reportincident/webservices/incident";

    protected CamelContext camel;

    protected void startCamel() throws Exception {
        camel = new DefaultCamelContext();
        ReportIncidentRoutes routes = new ReportIncidentRoutes();
        routes.setUsingServletTransport(false);
        camel.addRoutes(routes);
        camel.start();
    }
    
    protected void stopCamel() throws Exception {
        camel.stop();
    }

    protected static ReportIncidentEndpoint createCXFClient() {
        // we use CXF to create a client for us as its easier than JAXWS and works
        JaxWsProxyFactoryBean factory = new JaxWsProxyFactoryBean();
        factory.setServiceClass(ReportIncidentEndpoint.class);
        factory.setAddress(URL);
        return (ReportIncidentEndpoint) factory.create();
    }

    @Test
    public void testRendportIncident() throws Exception {
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
        ReportIncidentEndpoint client = createCXFClient();
        OutputReportIncident out = client.reportIncident(input);

        // assert we got a OK back
        assertEquals("0", out.getCode());

        // let some time pass to allow Camel to pickup the file and send it as an email
        Thread.sleep(3000);

        // assert mail box
        assertEquals("Should have got 1 mail", 1, inbox.size());
    }
}
