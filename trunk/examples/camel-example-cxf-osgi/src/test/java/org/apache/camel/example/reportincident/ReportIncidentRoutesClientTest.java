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

import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Unit test of our routes
 */
public class ReportIncidentRoutesClientTest extends CamelSpringTestSupport {

    // should be the same address as we have in our route
    private static final String URL = "http://localhost:%s/cxf/camel-example-cxf-osgi/webservices/incident";
    private static final int PORT = AvailablePortFinder.getNextAvailable();
    
    @BeforeClass
    public static void setUpBeforeClass() {
        System.setProperty("port", String.valueOf(PORT));
    }
    
    @AfterClass
    public static void tearDownBeforeClass() {
        System.clearProperty("port");
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
        ReportIncidentEndpoint client = createCXFClient(String.format(URL, PORT));
        OutputReportIncident out = client.reportIncident(input);

        // assert we got a OK back
        assertEquals("OK", out.getCode());
        
        // test the input with other users
        input.setGivenName("Guest");
        out = client.reportIncident(input);
        
        // assert we got a Accept back
        assertEquals("Accepted", out.getCode());
    }
    
    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(new String[] {"camel-context.xml"});
    }
}