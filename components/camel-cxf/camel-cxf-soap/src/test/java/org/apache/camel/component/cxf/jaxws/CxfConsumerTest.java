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
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CxfConsumerTest extends CamelTestSupport {
    protected static final String SIMPLE_ENDPOINT_ADDRESS = "http://localhost:"
                                                            + CXFTestSupport.getPort1() + "/CxfConsumerTest/test";
    protected static final String SIMPLE_ENDPOINT_URI = "cxf://" + SIMPLE_ENDPOINT_ADDRESS
                                                        + "?serviceClass=org.apache.camel.component.cxf.jaxws.HelloService"
                                                        + "&publishedEndpointUrl=http://www.simple.com/services/test";

    private static final String ECHO_REQUEST = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                                               + "<soap:Body><ns1:echo xmlns:ns1=\"http://jaxws.cxf.component.camel.apache.org/\">"
                                               + "<arg0 xmlns=\"http://jaxws.cxf.component.camel.apache.org/\">Hello World!</arg0></ns1:echo></soap:Body></soap:Envelope>";

    private static final String ECHO_OPERATION = "echo";
    private static final String ECHO_BOOLEAN_OPERATION = "echoBoolean";
    private static final String TEST_MESSAGE = "Hello World!";

    // START SNIPPET: example
    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(SIMPLE_ENDPOINT_URI).choice().when(header(CxfConstants.OPERATION_NAME).isEqualTo(ECHO_OPERATION))
                        .process(new Processor() {
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
                                assertNotNull(request.getRemoteAddr(), "Should get the RemoteAddress");
                                // Could verify the HttpRequest
                                String contentType = in.getHeader(Exchange.CONTENT_TYPE, String.class);
                                assertNotNull(contentType, "Should get the contentType.");

                                // Get the parameter list
                                List<?> parameter = in.getBody(List.class);
                                // Get the operation name
                                String operation = (String) in.getHeader(CxfConstants.OPERATION_NAME);
                                Object result = operation + " " + (String) parameter.get(0);
                                // Put the result back
                                exchange.getMessage().setBody(result);
                                // set up the response context which force start document
                                Map<String, Object> map = new HashMap<>();
                                map.put("org.apache.cxf.stax.force-start-document", Boolean.TRUE);
                                exchange.getMessage().setHeader(Client.RESPONSE_CONTEXT, map);
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
                        });
            }
        };
    }
    // END SNIPPET: example

    @Test
    public void testInvokingServiceFromCXFClient() throws Exception {
        ClientProxyFactoryBean proxyFactory = new ClientProxyFactoryBean();
        ClientFactoryBean clientBean = proxyFactory.getClientFactoryBean();
        clientBean.setAddress(SIMPLE_ENDPOINT_ADDRESS);
        clientBean.setServiceClass(HelloService.class);
        clientBean.setBus(BusFactory.newInstance().createBus());

        HelloService client = (HelloService) proxyFactory.create();

        String result = client.echo(TEST_MESSAGE);
        assertEquals(result, "echo " + TEST_MESSAGE, "We should get the echo string result from router");

        Boolean bool = client.echoBoolean(Boolean.TRUE);
        assertNotNull(bool, "The result should not be null");
        assertEquals("true", bool.toString(), "We should get the echo boolean result from router");
    }

    @Test
    public void testXmlDeclaration() throws Exception {
        String response = template.requestBodyAndHeader(SIMPLE_ENDPOINT_ADDRESS, ECHO_REQUEST, Exchange.CONTENT_TYPE,
                "text/xml; charset=UTF-8", String.class);
        assertTrue(response.startsWith("<?xml version='1.0' encoding="), "Can't find the xml declaration.");
    }

    @Test
    public void testPublishEndpointUrl() throws Exception {
        String response = template.requestBody(SIMPLE_ENDPOINT_ADDRESS + "?wsdl", null, String.class);
        assertTrue(response.indexOf("http://www.simple.com/services/test") > 0, "Can't find the right service location.");
    }

}
