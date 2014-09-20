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
package org.apache.camel.component.xmlrpc;

import java.util.HashMap;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.xmlrpc.XmlRpcException;
import org.junit.BeforeClass;
import org.junit.Test;

public class XmlRpcComponentTest extends CamelTestSupport {
    private static volatile int port;
    
    private static final String RESPONSE = 
          "<methodResponse><params>"
          + "<param><value><string>GreetMe!</string></value></param>" 
          + "</params></methodResponse>";

    private static final String FAULT_RESPONSE = 
        "<methodResponse><fault><value>"
        + "<struct><member><name>faultCode</name><value><int>4</int></value></member>"
        + "<member><name>faultString</name><value><string>Too many parameters.</string></value></member>"
        + "</struct></value></fault></methodResponse>";
    
    @BeforeClass
    public static void initPort() throws Exception {
        // start from somewhere in the 22xxx range
        port = AvailablePortFinder.getNextAvailable(22000);
    }

    @Test
    public void testXmlRpcResponseMessage() throws Exception {
        invokeService("direct:async");
        invokeService("direct:sync");
    }
    
    @Test
    public void testXmlRpcFaultMessage() throws Exception {
        invokeServiceFaultResponse("xmlrpc:http://localhost:" + port + "/xmlrpc/fault");
        invokeServiceFaultResponse("xmlrpc:http://localhost:" + port + "/xmlrpc/fault?synchronous=true");
    }
    
    @Test
    public void verifyHeadersPreservedSync() throws Exception {
        verifyHeadersPreserved("direct:sync");
    }

    @Test
    public void verifyHeadersPreservedAsync() throws Exception {
        verifyHeadersPreserved("direct:async");
    }

    private void verifyHeadersPreserved(String uri) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.reset();
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived("UserHeader", "test-header-value");
        template.requestBodyAndHeaders(uri, new Object[] {"me"},
                new HashMap<String, Object>() {

                    private static final long serialVersionUID = 1L;

                    {
                        put(XmlRpcConstants.METHOD_NAME, "hello");
                        put("UserHeader", "test-header-value");
                    }
                });

        assertMockEndpointsSatisfied();
    }
    
    private void invokeService(String uri) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.reset();
        mock.expectedBodiesReceived("GreetMe!");
        
        template.requestBodyAndHeader(uri, new Object[]{"me"}, XmlRpcConstants.METHOD_NAME, "hello");
        
        assertMockEndpointsSatisfied();
    }
    
    private void invokeServiceFaultResponse(String uri) throws Exception {
        try {
            template.requestBodyAndHeader(uri, new Object[]{"me"}, XmlRpcConstants.METHOD_NAME, "hello");
            fail("Expects the exception here");
        } catch (Exception ex) {
            assertTrue("Get a wrong exception.", ex instanceof CamelExecutionException);
            assertTrue("Get a worng exception cause.", ex.getCause() instanceof XmlRpcException);
            XmlRpcException xmlrpcException = (XmlRpcException)ex.getCause();
            assertEquals("Get a worng exception message.", "Too many parameters.", xmlrpcException.getMessage());
        }
        
    }
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:async")
                    .to("xmlrpc:http://localhost:" + port + "/xmlrpc/test")
                    .to("mock:result");
                from("direct:sync")
                    .to("xmlrpc:http://localhost:" + port + "/xmlrpc/test?synchronous=true")
                    .to("mock:result");
                // setup a mock test server for testing
                from("jetty:http://localhost:" + port + "/xmlrpc/test")
                    .convertBodyTo(String.class)
                    // here print out the message that we get 
                    .to("log:org.apache.camel.component.xmlrpc")
                    .transform().constant(RESPONSE);
                // setup a mock test server for falt message
                from("jetty:http://localhost:" + port + "/xmlrpc/fault")
                    .convertBodyTo(String.class)
                    // here print out the message that we get 
                    .to("log:org.apache.camel.component.xmlrpc")
                    .transform().constant(FAULT_RESPONSE);
                    
            }
        };
    }
}
