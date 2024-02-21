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
package org.apache.camel.component.cxf.jaxws;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.common.CXFTestSupport;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.cxf.BusFactory;
import org.apache.cxf.frontend.ClientFactoryBean;
import org.apache.cxf.frontend.ClientProxyFactoryBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CxfConsumerMessageTest extends CamelTestSupport {
    private static final String TEST_MESSAGE = "Hello World!";

    private static final String ECHO_METHOD = "ns1:echo xmlns:ns1=\"http://jaxws.cxf.component.camel.apache.org/\"";

    private static final String ECHO_RESPONSE = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                                                + "<soap:Body><ns1:echoResponse xmlns:ns1=\"http://jaxws.cxf.component.camel.apache.org/\">"
                                                + "<return xmlns=\"http://jaxws.cxf.component.camel.apache.org/\">echo Hello World!</return>"
                                                + "</ns1:echoResponse></soap:Body></soap:Envelope>";
    private static final String ECHO_BOOLEAN_RESPONSE
            = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
              + "<soap:Body><ns1:echoBooleanResponse xmlns:ns1=\"http://jaxws.cxf.component.camel.apache.org/\">"
              + "<return xmlns=\"http://jaxws.cxf.component.camel.apache.org/\">true</return>"
              + "</ns1:echoBooleanResponse></soap:Body></soap:Envelope>";

    protected final String simpleEndpointAddress = "http://localhost:"
                                                   + CXFTestSupport.getPort1() + "/" + getClass().getSimpleName() + "/test";
    protected final String simpleEndpointURI = "cxf://" + simpleEndpointAddress
                                               + "?serviceClass=org.apache.camel.component.cxf.jaxws.HelloService";

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(simpleEndpointURI + "&dataFormat=RAW").process(new Processor() {
                    public void process(final Exchange exchange) {
                        Message in = exchange.getIn();
                        // check the content-length header is filtered
                        Object value = in.getHeader("Content-Length");
                        assertNull(value, "The Content-Length header should be removed");
                        // Get the request message
                        String request = in.getBody(String.class);
                        // Send the response message back
                        if (request.indexOf(ECHO_METHOD) > 0) {
                            exchange.getMessage().setBody(ECHO_RESPONSE);
                        } else { // echoBoolean call
                            exchange.getMessage().setBody(ECHO_BOOLEAN_RESPONSE);
                        }

                    }
                });
            }
        };
    }

    @Test
    public void testInvokingServiceFromClient() throws Exception {
        ClientProxyFactoryBean proxyFactory = new ClientProxyFactoryBean();
        ClientFactoryBean clientBean = proxyFactory.getClientFactoryBean();
        clientBean.setAddress(simpleEndpointAddress);
        clientBean.setServiceClass(HelloService.class);
        clientBean.setBus(BusFactory.getDefaultBus());

        HelloService client = (HelloService) proxyFactory.create();

        String result = client.echo(TEST_MESSAGE);
        assertEquals(result, "echo " + TEST_MESSAGE, "We should get the echo string result from router");

        Boolean bool = client.echoBoolean(Boolean.TRUE);
        assertNotNull(bool, "The result should not be null");
        assertEquals("true", bool.toString(), "We should get the echo boolean result from router");

    }

}
