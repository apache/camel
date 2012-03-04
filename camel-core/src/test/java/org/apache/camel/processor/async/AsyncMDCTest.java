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
package org.apache.camel.processor.async;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

import org.slf4j.MDC;

import static org.apache.camel.impl.MDCUnitOfWork.MDC_BREADCRUMB_ID;
import static org.apache.camel.impl.MDCUnitOfWork.MDC_CAMEL_CONTEXT_ID;
import static org.apache.camel.impl.MDCUnitOfWork.MDC_EXCHANGE_ID;
import static org.apache.camel.impl.MDCUnitOfWork.MDC_ROUTE_ID;

/**
 * @version 
 */
public class AsyncMDCTest extends ContextTestSupport {

    public void testMDC() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye Camel");

        template.sendBody("direct:a", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public void testThreeMessagesMDC() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye Camel", "Bye Camel", "Bye Camel");

        log.info("#1 message");
        template.sendBody("direct:a", "Hello World");

        log.info("#2 message");
        template.sendBody("direct:a", "Hello Camel");

        log.info("#3 message");
        template.sendBody("direct:a", "Hi Camel");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // enable MDC
                context.setUseMDCLogging(true);

                context.addComponent("async", new MyAsyncComponent());

                from("direct:a").routeId("route-a")
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                assertEquals("route-a", MDC.get(MDC_ROUTE_ID));
                                assertEquals(exchange.getExchangeId(), MDC.get(MDC_EXCHANGE_ID));
                                assertEquals(exchange.getContext().getName(), MDC.get(MDC_CAMEL_CONTEXT_ID));
                                assertEquals(exchange.getIn().getHeader(Exchange.BREADCRUMB_ID), MDC.get(MDC_BREADCRUMB_ID));
                            }
                        })
                        .to("log:before")
                        .to("async:bye:camel")
                        .to("log:after")
                        .to("direct:b");

                from("direct:b").routeId("route-b")
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                assertEquals("route-b", MDC.get(MDC_ROUTE_ID));
                                assertEquals(exchange.getExchangeId(), MDC.get(MDC_EXCHANGE_ID));
                            }
                        })
                        .to("log:bar").to("mock:result");
            }
        };
    }

}