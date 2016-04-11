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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * @version 
 */
public class MDCOnCompletionTest extends ContextTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(MDCOnCompletionTest.class);

    public void testMDC() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("direct:a", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public void testMDCTwoMessages() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World", "Bye World");

        template.sendBody("direct:a", "Hello World");
        template.sendBody("direct:a", "Bye World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // enable MDC
                context.setUseMDCLogging(true);

                from("direct:a").routeId("route-a")
                        .onCompletion()
                            .process(new Processor() {
                                public void process(Exchange exchange) throws Exception {
                                    assertEquals("route-a", MDC.get("camel.routeId"));
                                    assertEquals(exchange.getExchangeId(), MDC.get("camel.exchangeId"));
                                    assertEquals(exchange.getIn().getMessageId(), MDC.get("camel.messageId"));

                                    assertEquals("1", MDC.get("custom.id"));

                                    LOG.info("From onCompletion after route-a");
                                }
                            })
                        .end()
                        .to("log:foo")
                        .to("direct:b");

                from("direct:b").routeId("route-b")
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                assertEquals("route-b", MDC.get("camel.routeId"));
                                assertEquals(exchange.getExchangeId(), MDC.get("camel.exchangeId"));
                                assertEquals(exchange.getIn().getMessageId(), MDC.get("camel.messageId"));

                                MDC.put("custom.id", "1");
                                LOG.info("From processor in route-b");
                            }
                        })
                        .to("log:bar")
                        .to("mock:result");
            }
        };
    }
}
