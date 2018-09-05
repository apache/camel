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
package org.apache.camel.spring;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Test;
import org.slf4j.MDC;

public class SpringMDCTransactedTest extends ContextTestSupport {

    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry result = super.createRegistry();
        result.bind("NOOP-TX", new NoopPlatformTransactionManager());
        return result;
    }

    @Test
    public void testMDCTransacted() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("direct:a", "Hello World");

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
                    .transacted()
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            assertEquals("route-a", MDC.get("camel.routeId"));
                            assertEquals(exchange.getExchangeId(), MDC.get("camel.exchangeId"));
                            assertEquals(exchange.getIn().getMessageId(), MDC.get("camel.messageId"));
                        }
                    })
                    .to("log:foo-before")
                    .to("direct:b")
                    .to("log:foo-after")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            assertEquals("route-a", MDC.get("camel.routeId"));
                            assertEquals(exchange.getExchangeId(), MDC.get("camel.exchangeId"));
                            assertEquals(exchange.getIn().getMessageId(), MDC.get("camel.messageId"));
                        }
                    });

                from("direct:b").routeId("route-b")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            assertEquals("route-b", MDC.get("camel.routeId"));
                            assertEquals(exchange.getExchangeId(), MDC.get("camel.exchangeId"));
                            assertEquals(exchange.getIn().getMessageId(), MDC.get("camel.messageId"));
                        }
                    })
                    .to("log:bar")
                    .to("mock:result");
            }
        };
    }


}
