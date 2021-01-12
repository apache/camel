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
package org.apache.camel.processor.interceptor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Advice with using to string matching
 */
public class AdviceWithTasksToStringPatternTest extends ContextTestSupport {

    @Test
    public void testUnknownId() throws Exception {
        try {
            AdviceWith.adviceWith(context.getRouteDefinitions().get(0), context, new AdviceWithRouteBuilder() {
                @Override
                public void configure() throws Exception {
                    weaveByToString("xxx").replace().to("mock:xxx");
                }
            });
            fail("Should hve thrown exception");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith("There are no outputs which matches: xxx in the route"), e.getMessage());
        }
    }

    @Test
    public void testReplace() throws Exception {
        // START SNIPPET: e1
        AdviceWith.adviceWith(context.getRouteDefinitions().get(0), context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                // weave nodes in the route which has foo anywhere in their to
                // string representation
                // and replace them with the following route path
                weaveByToString(".*foo.*").replace().multicast().to("mock:a").to("mock:b");
            }
        });
        // END SNIPPET: e1

        getMockEndpoint("mock:foo").expectedMessageCount(0);
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:b").expectedMessageCount(1);
        getMockEndpoint("mock:bar").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRemove() throws Exception {
        AdviceWith.adviceWith(context.getRouteDefinitions().get(0), context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveByToString(".*bar.*").remove();
            }
        });

        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        assertNull(context.hasEndpoint("mock:bar"), "Should have removed mock:bar endpoint");
    }

    @Test
    public void testBefore() throws Exception {
        AdviceWith.adviceWith(context.getRouteDefinitions().get(0), context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveByToString(".*bar.*").before().to("mock:a").transform(constant("Bye World"));
            }
        });

        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:a").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:bar").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testAfter() throws Exception {
        AdviceWith.adviceWith(context.getRouteDefinitions().get(0), context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveByToString(".*bar.*").after().to("mock:a").transform(constant("Bye World"));
            }
        });

        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:a").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:bar").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("mock:foo").to("mock:bar").to("mock:result");
            }
        };
    }
}
