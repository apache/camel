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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.interceptor.LoggingOutInterceptor;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.message.Message;
import org.apache.cxf.ws.security.wss4j.WSS4JOutInterceptor;
import org.junit.BeforeClass;
import org.junit.Test;
import org.jvnet.mock_javamail.Mailbox;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Unit test of our routes
 */
public class ReportIncidentRoutesTest extends CamelSpringTestSupport {

    // should be the same address as we have in our route
    private static final String URL = "http://localhost:{{port}}/camel-example-reportincident/webservices/incident";

    protected CamelContext camel;
    
    @BeforeClass
    public static void setupFreePort() throws Exception {
        // find a free port number, and write that in the custom.properties file
        // which we will use for the unit tests, to avoid port number in use problems
        int port = AvailablePortFinder.getNextAvailable();
        String s = "port=" + port;
        File custom = new File("target/custom.properties");
        FileOutputStream fos = new FileOutputStream(custom);
        fos.write(s.getBytes());
        fos.close();
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("/META-INF/spring/camel-context.xml");
    }

    protected static ReportIncidentEndpoint createCXFClient(String url) {
        List<Interceptor<? extends Message>> outInterceptors = new ArrayList<Interceptor<? extends Message>>();

        // Define WSS4j properties for flow outgoing
        Map<String, Object> outProps = new HashMap<String, Object>();
        outProps.put("action", "UsernameToken Timestamp");

        outProps.put("passwordType", "PasswordDigest");
        outProps.put("user", "charles");
        outProps.put("passwordCallbackClass", "org.apache.camel.example.reportincident.UTPasswordCallback");

        WSS4JOutInterceptor wss4j = new WSS4JOutInterceptor(outProps);

        // Add LoggingOutInterceptor
        LoggingOutInterceptor loggingOutInterceptor = new LoggingOutInterceptor();

        outInterceptors.add(wss4j);
        outInterceptors.add(loggingOutInterceptor);

        // we use CXF to create a client for us as its easier than JAXWS and works
        JaxWsProxyFactoryBean factory = new JaxWsProxyFactoryBean();
        factory.setOutInterceptors(outInterceptors);
        factory.setServiceClass(ReportIncidentEndpoint.class);
        factory.setAddress(url);
        return (ReportIncidentEndpoint) factory.create();
    }

    @Test
    public void testRendportIncident() throws Exception {
        // assert mailbox is empty before starting
        Mailbox inbox = Mailbox.get("incident@localhost");
        inbox.clear();
        assertEquals("Should not have mails", 0, inbox.size());

        // create input parameter
        InputReportIncident input = new InputReportIncident();
        input.setIncidentId("222");
        input.setIncidentDate("2010-07-14");
        input.setGivenName("Charles");
        input.setFamilyName("Moulliard");
        input.setSummary("Bla");
        input.setDetails("Bla bla");
        input.setEmail("cmoulliard@apache.org");
        input.setPhone("0011 22 33 44");

        // create the webservice client and send the request
        String url = context.resolvePropertyPlaceholders(URL);
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
