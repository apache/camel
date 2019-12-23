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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;
import org.slf4j.MDC;

public class MDCTest extends ContextTestSupport {

    @Test
    public void testMDC() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("direct:a", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
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

                from("direct:a").routeId("route-a").step("step-a").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        assertEquals("route-a", MDC.get("camel.routeId"));
                        assertEquals(exchange.getExchangeId(), MDC.get("camel.exchangeId"));
                        assertEquals(exchange.getIn().getMessageId(), MDC.get("camel.messageId"));
                        assertEquals("step-a", MDC.get("camel.stepId"));

                        MDC.put("custom.id", "1");
                    }
                }).to("log:foo").to("direct:b").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        assertEquals("route-a", MDC.get("camel.routeId"));
                        assertEquals(exchange.getExchangeId(), MDC.get("camel.exchangeId"));
                        assertEquals(exchange.getIn().getMessageId(), MDC.get("camel.messageId"));
                        assertEquals("step-a", MDC.get("camel.stepId"));
                    }
                });

                from("direct:b").routeId("route-b").step("step-b").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        assertEquals("route-b", MDC.get("camel.routeId"));
                        assertEquals(exchange.getExchangeId(), MDC.get("camel.exchangeId"));
                        assertEquals(exchange.getIn().getMessageId(), MDC.get("camel.messageId"));
                        assertEquals("step-b", MDC.get("camel.stepId"));
                        assertEquals("1", MDC.get("custom.id"));
                    }
                }).to("log:bar").to("mock:result");
            }
        };
    }
}
