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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.ServletRequest;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.common.CXFTestSupport;
import org.apache.camel.component.cxf.common.DataFormat;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.cxf.BusFactory;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientFactoryBean;
import org.apache.cxf.frontend.ClientProxyFactoryBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CxfConsumerResponseTest extends CamelTestSupport {

    private static final String ECHO_OPERATION = "echo";
    private static final String ECHO_BOOLEAN_OPERATION = "echoBoolean";
    private static final String PING_OPERATION = "ping";
    private static final String TEST_MESSAGE = "Hello World!";
    private static int pingCounter;

    protected final String simpleEndpointAddress = "http://localhost:"
                                                   + CXFTestSupport.getPort1() + "/" + getClass().getSimpleName() + "/test";

    protected final String simpleEndpointURI = "cxf://" + simpleEndpointAddress
                                               + "?serviceClass=org.apache.camel.component.cxf.jaxws.HelloService"
                                               + "&publishedEndpointUrl=http://www.simple.com/services/test"
                                               + "&exchangePattern=InOnly";

    // START SNIPPET: example
    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(simpleEndpointURI)
                        .choice().when(header(CxfConstants.OPERATION_NAME).isEqualTo(ECHO_OPERATION)).process(new Processor() {
                            public void process(final Exchange exchange) {
                                assertEquals(DataFormat.POJO,
                                        exchange.getProperty(CxfConstants.DATA_FORMAT_PROPERTY, DataFormat.class));
                                Message in = exchange.getIn();
                                // check the remote IP from the cxfMessage
                                org.apache.cxf.message.Message cxfMessage
                                        = in.getHeader(CxfConstants.CAMEL_CXF_MESSAGE, org.apache.cxf.message.Message.class);
                                assertNotNull(cxfMessage, "Should get the cxfMessage instance from message header");
                                ServletRequest request = (ServletRequest) cxfMessage.get("HTTP.REQUEST");
                                assertNotNull(request, "Should get the ServletRequest");
                                assertNotNull("Should get the RemoteAddress" + request.getRemoteAddr());
                                // Get the parameter list
                                List<?> parameter = in.getBody(List.class);
                                // Get the operation name
                                String operation = (String) in.getHeader(CxfConstants.OPERATION_NAME);
                                Object result = operation + " " + (String) parameter.get(0);
                                // Put the result back
                                exchange.getIn().setBody(result);
                                // set up the response context which force start document
                                Map<String, Object> map = new HashMap<>();
                                map.put("org.apache.cxf.stax.force-start-document", Boolean.TRUE);
                                exchange.getIn().setHeader(Client.RESPONSE_CONTEXT, map);
                            }
                        })
                        .when(header(CxfConstants.OPERATION_NAME).isEqualTo(ECHO_BOOLEAN_OPERATION)).process(new Processor() {
                            public void process(final Exchange exchange) {
                                Message in = exchange.getIn();
                                // Get the parameter list
                                List<?> parameter = in.getBody(List.class);
                                // Put the result back
                                exchange.getMessage().setBody(parameter.get(0));
                            }
                        })
                        .when(header(CxfConstants.OPERATION_NAME).isEqualTo(PING_OPERATION)).process(new Processor() {
                            public void process(final Exchange exchange) {
                                pingCounter++;
                            }

                        });

            }
        };
    }
    // END SNIPPET: example

    @Test
    public void testInvokingServiceFromCXFClient() throws Exception {
        ClientProxyFactoryBean proxyFactory = new ClientProxyFactoryBean();
        ClientFactoryBean clientBean = proxyFactory.getClientFactoryBean();
        clientBean.setAddress(simpleEndpointAddress);
        clientBean.setServiceClass(HelloService.class);
        clientBean.setBus(BusFactory.getDefaultBus());

        HelloService client = (HelloService) proxyFactory.create();
        assertNotNull(client);

        String result = client.echo(TEST_MESSAGE);
        assertEquals(result, "echo " + TEST_MESSAGE, "We should get the echo string result from router");

        Boolean bool = client.echoBoolean(Boolean.TRUE);
        assertNotNull(bool, "The result should not be null");
        assertEquals("true", bool.toString(), "We should get the echo boolean result from router");

        int beforeCallingPing = pingCounter;
        client.ping();
        int afterCallingPing = pingCounter;
        assertEquals(1, afterCallingPing - beforeCallingPing, "The ping operation doesn't be called");
    }

}
