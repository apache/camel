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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;

/**
 * Advice with using to string matching
 */
public class AdviceWithTasksToStringPatternTest extends ContextTestSupport {

    public void testUnknownId() throws Exception {
        try {
            context.getRouteDefinitions().get(0).adviceWith(context, new AdviceWithRouteBuilder() {
                @Override
                public void configure() throws Exception {
                    weaveByToString("xxx").replace().to("mock:xxx");
                }
            });
            fail("Should hve thrown exception");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().startsWith("There are no outputs which matches: xxx in the route"));
        }
    }

    public void testReplace() throws Exception {
        // START SNIPPET: e1
        context.getRouteDefinitions().get(0).adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                // weave nodes in the route which has foo anywhere in their to string representation
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

    public void testRemove() throws Exception {
        context.getRouteDefinitions().get(0).adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveByToString(".*bar.*").remove();
            }
        });

        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        assertTrue("Should have removed mock:bar endpoint", context.hasEndpoint("mock:bar") == null);
    }

    public void testBefore() throws Exception {
        context.getRouteDefinitions().get(0).adviceWith(context, new AdviceWithRouteBuilder() {
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

    public void testAfter() throws Exception {
        context.getRouteDefinitions().get(0).adviceWith(context, new AdviceWithRouteBuilder() {
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
                from("direct:start")
                    .to("mock:foo")
                    .to("mock:bar")
                    .to("mock:result");
            }
        };
    }
}