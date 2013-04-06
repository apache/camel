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
package org.apache.camel.processor.interceptor;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class AdviceWithTest extends ContextTestSupport {

    public void testNoAdvised() throws Exception {
        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    // START SNIPPET: e1
    public void testAdvised() throws Exception {
        // advice the first route using the inlined route builder
        context.getRouteDefinitions().get(0).adviceWith(context, new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // intercept sending to mock:foo and do something else
                interceptSendToEndpoint("mock:foo")
                        .skipSendToOriginalEndpoint()
                        .to("log:foo")
                        .to("mock:advised");
            }
        });

        getMockEndpoint("mock:foo").expectedMessageCount(0);
        getMockEndpoint("mock:advised").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }
    // END SNIPPET: e1

    public void testAdvisedNoNewRoutesAllowed() throws Exception {
        try {
            context.getRouteDefinitions().get(0).adviceWith(context, new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("direct:bar").to("mock:bar");

                    interceptSendToEndpoint("mock:foo")
                            .skipSendToOriginalEndpoint()
                            .to("log:foo")
                            .to("mock:advised");
                }
            });
            fail("Should have thrown exception");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void testAdvisedThrowException() throws Exception {
        context.getRouteDefinitions().get(0).adviceWith(context, new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("mock:foo")
                    .to("mock:advised")
                    .throwException(new IllegalArgumentException("Damn"));
            }
        });

        getMockEndpoint("mock:foo").expectedMessageCount(0);
        getMockEndpoint("mock:advised").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        try {
            template.sendBody("direct:start", "Hello World");
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("Damn", e.getCause().getMessage());
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("mock:foo").to("mock:result");
            }
        };
    }
}
