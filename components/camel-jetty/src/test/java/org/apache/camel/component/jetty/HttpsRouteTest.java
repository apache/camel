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
package org.apache.camel.component.jetty;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;


public class HttpsRouteTest extends CamelTestSupport {
    private static final String NULL_VALUE_MARKER = CamelTestSupport.class.getCanonicalName();
    protected String expectedBody = "<hello>world!</hello>";
    protected String pwd = "changeit";
    protected Properties originalValues = new Properties();
    
    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();        
        // ensure jsse clients can validate the self signed dummy localhost cert, 
        // use the server keystore as the trust store for these tests
        URL trustStoreUrl = this.getClass().getClassLoader().getResource("jsse/localhost.ks");
        setSystemProp("javax.net.ssl.trustStore", trustStoreUrl.toURI().getPath());
    }

    @Override
    @After
    public void tearDown() throws Exception {
        restoreSystemProperties();
        super.tearDown();
    }

    protected void setSystemProp(String key, String value) {
        String originalValue = System.setProperty(key, value);
        originalValues.put(key, originalValue != null ? originalValue : NULL_VALUE_MARKER);
    }

    protected void restoreSystemProperties() {
        for (Object key : originalValues.keySet()) {
            Object value = (String) originalValues.get(key);  
            if (NULL_VALUE_MARKER.equals(value)) {
                System.getProperties().remove(key);    
            } else {
                System.setProperty((String)key, (String)value);
            }
        }
    }

    @Test
    public void testEndpoint() throws Exception {
        MockEndpoint mockEndpointA = resolveMandatoryEndpoint("mock:a", MockEndpoint.class);
        mockEndpointA.expectedBodiesReceived(expectedBody);
        MockEndpoint mockEndpointB = resolveMandatoryEndpoint("mock:b", MockEndpoint.class);
        mockEndpointB.expectedBodiesReceived(expectedBody);

        invokeHttpEndpoint();

        mockEndpointA.assertIsSatisfied();
        mockEndpointB.assertIsSatisfied();
        List<Exchange> list = mockEndpointA.getReceivedExchanges();
        Exchange exchange = list.get(0);
        assertNotNull("exchange", exchange);

        Message in = exchange.getIn();
        assertNotNull("in", in);

        Map<String, Object> headers = in.getHeaders();

        log.info("Headers: " + headers);

        assertTrue("Should be more than one header but was: " + headers, headers.size() > 0);
    }
    
    @Test
    public void testEndpointWithoutHttps() {
        MockEndpoint mockEndpoint = resolveMandatoryEndpoint("mock:a", MockEndpoint.class);    
        try {
            template.sendBodyAndHeader("jetty:http://localhost:9080/test", expectedBody, "Content-Type", "application/xml");
            fail("expect exception on access to https endpoint via http");
        } catch (RuntimeCamelException expected) {
        }
        assertTrue("mock endpoint was not called", mockEndpoint.getExchanges().isEmpty());
    }

    @Test
    public void testHelloEndpoint() throws Exception {

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        InputStream is = new URL("https://localhost:9080/hello").openStream();
        int c;
        while ((c = is.read()) >= 0) {
            os.write(c);
        }

        String data = new String(os.toByteArray());
        assertEquals("<b>Hello World</b>", data);
        
    }
    
    @Test
    public void testHelloEndpointWithoutHttps() throws Exception {
        try {
            new URL("http://localhost:9080/hello").openStream();
            fail("expected SocketException on use ot http");
        } catch (SocketException expected) {
        }
        
        
    }
    
    protected void invokeHttpEndpoint() throws IOException {
        template.sendBodyAndHeader("jetty:https://localhost:9080/test", expectedBody, "Content-Type", "application/xml");
        template.sendBodyAndHeader("jetty:https://localhost:9090/test", expectedBody, "Content-Type", "application/xml");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws URISyntaxException {
                
                JettyHttpComponent componentJetty = (JettyHttpComponent) context.getComponent("jetty");
                componentJetty.setSslPassword(pwd);
                componentJetty.setSslKeyPassword(pwd);
                URL keyStoreUrl = this.getClass().getClassLoader().getResource("jsse/localhost.ks");
                componentJetty.setKeystore(keyStoreUrl.toURI().getPath());
                
                from("jetty:https://localhost:9080/test").to("mock:a");

                Processor proc = new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        exchange.getOut().setBody("<b>Hello World</b>");
                    }
                };
                from("jetty:https://localhost:9080/hello").process(proc);
                
                from("jetty:https://localhost:9090/test").to("mock:b");
            }
        };
    }
}

