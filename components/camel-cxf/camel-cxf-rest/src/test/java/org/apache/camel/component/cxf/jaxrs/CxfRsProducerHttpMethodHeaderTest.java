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
package org.apache.camel.component.cxf.jaxrs;

import jakarta.ws.rs.core.Response;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.common.CXFTestSupport;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class CxfRsProducerHttpMethodHeaderTest extends CamelTestSupport {

    @Test
    public void testHttpMethodHeader() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        // should not leak internal cxf headers
        getMockEndpoint("mock:result").message(0).header("org.apache.cxf.request.uri").isNull();
        getMockEndpoint("mock:result").message(0).header("org.apache.cxf.request.method").isNull();

        Exchange exchange = context.createProducerTemplate().send(
                "cxfrs://http://localhost:" + CXFTestSupport.getPort7() + "/CxfRsProducerHttpMethodHeaderTest",
                new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        exchange.setPattern(ExchangePattern.InOut);
                        Message inMessage = exchange.getIn();
                        inMessage.setHeader(Exchange.HTTP_METHOD, "GET");
                        inMessage.setHeader(Exchange.HTTP_PATH, "/CxfRsProducerHttpMethodHeaderTest/");
                        inMessage.setHeader(Exchange.HTTP_QUERY, "q=1");
                        inMessage.setHeader(Exchange.CONTENT_TYPE, "application/text");
                        inMessage.setBody("Hello World");
                    }

                });

        // get the response message
        Response response = (Response) exchange.getMessage().getBody();

        // check the response code on the Response object as set by the "HttpProcess"
        assertEquals(204, response.getStatus());

        Exchange e1 = getMockEndpoint("mock:result").getReceivedExchanges().get(0);
        // should not contain CXF headers
        assertFalse(() -> e1.getMessage().getHeaders().keySet().stream().anyMatch(k -> k.startsWith("org.apache.cxf")),
                "Should not contain CXF headers");
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                fromF("undertow://http://localhost:%s/CxfRsProducerHttpMethodHeaderTest/?matchOnUriPrefix=true",
                        CXFTestSupport.getPort7())
                        .to("mock:result");
            }
        };
    }
}
