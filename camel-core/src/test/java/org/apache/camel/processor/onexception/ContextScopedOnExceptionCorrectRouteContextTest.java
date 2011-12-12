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
package org.apache.camel.processor.onexception;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;

/**
 *
 */
public class ContextScopedOnExceptionCorrectRouteContextTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testContextScopedOnExceptionLogRouteBarFail() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class)
                    .log("Error due ${exception.message}")
                    .process(new Processor() {
                        @Override
                        public void process(Exchange exchange) throws Exception {
                            String routeId = exchange.getUnitOfWork().getRouteContext().getRoute().getId();
                            assertEquals("bar", routeId);
                        }
                    });

                from("direct:start").routeId("foo")
                    .to("mock:foo")
                    .to("direct:bar")
                    .to("mock:result");

                from("direct:bar").routeId("bar")
                    .to("mock:bar")
                    .throwException(new IllegalArgumentException("Forced bar error"));
            }
        });
        context.start();

        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:bar").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(0);
        
        try {
            template.sendBody("direct:start", "Hello World");
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            IllegalArgumentException cause = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("Forced bar error", cause.getMessage());
        }

        assertMockEndpointsSatisfied();
    }

    public void testContextScopedOnExceptionLogRouteFooFail() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class)
                    .log("Error due ${exception.message}")
                    .process(new Processor() {
                        @Override
                        public void process(Exchange exchange) throws Exception {
                            String routeId = exchange.getUnitOfWork().getRouteContext().getRoute().getId();
                            assertEquals("foo", routeId);
                        }
                    });

                from("direct:start").routeId("foo")
                    .to("mock:foo")
                    .throwException(new IllegalArgumentException("Forced foo error"))
                    .to("direct:bar")
                    .to("mock:result");

                from("direct:bar").routeId("bar")
                    .to("mock:bar");

                from("direct:killer").routeId("killer")
                    .to("mock:killer");
            }
        });
        context.start();

        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:bar").expectedMessageCount(0);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        try {
            template.sendBody("direct:start", "Hello World");
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            IllegalArgumentException cause = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("Forced foo error", cause.getMessage());
        }

        assertMockEndpointsSatisfied();
    }

}
