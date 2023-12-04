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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CxfProducerProtocalHeaderTest extends CamelTestSupport {
    private static int port = AvailablePortFinder.getNextAvailable();
    private static final String RESPONSE = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                                           + "<soap:Body><ns1:echoResponse xmlns:ns1=\"http://jaxws.cxf.component.camel.apache.org/\">"
                                           + "<return xmlns=\"http://jaxws.cxf.component.camel.apache.org/\">echo Hello World!</return>"
                                           + "</ns1:echoResponse></soap:Body></soap:Envelope>";

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("undertow:http://localhost:" + port + "/CxfProducerProtocalHeaderTest/user")
                        .process(new Processor() {

                            public void process(Exchange exchange) throws Exception {
                                assertNull(exchange.getIn().getHeader("CamelCxfTest"), "We should not get this header");
                                assertNull(exchange.getIn().getHeader("Transfer-Encoding"), "We should not get this header");
                                //check the headers
                                exchange.getMessage().setHeader("Content-Type", "text/xml");
                                exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
                                //send the response back
                                exchange.getMessage().setBody(RESPONSE);
                            }
                        });
            }
        };
    }

    private Exchange sendSimpleMessage(String endpointUri) {
        Exchange exchange = template.send(endpointUri, new Processor() {
            public void process(final Exchange exchange) {
                final List<String> params = new ArrayList<>();
                params.add("Hello World!");
                exchange.getIn().setBody(params);
                exchange.getIn().setHeader(CxfConstants.OPERATION_NAME, "echo");
                // Test the CxfHeaderFilterStrategy
                exchange.getIn().setHeader("CamelCxfTest", "\"test\"");
                exchange.getIn().setHeader("SOAPAction", "\"test\"");
                exchange.getIn().setHeader("Transfer-Encoding", "chunked");
            }
        });
        return exchange;

    }

    @Test
    public void testSendMessage() {
        Exchange exchange = sendSimpleMessage("cxf://http://localhost:" + port
                                              + "/CxfProducerProtocalHeaderTest/user"
                                              + "?serviceClass=org.apache.camel.component.cxf.jaxws.HelloService");
        org.apache.camel.Message out = exchange.getMessage();
        String result = out.getBody(String.class);
        assertEquals("echo " + "Hello World!", result, "reply body on Camel");
    }

}
