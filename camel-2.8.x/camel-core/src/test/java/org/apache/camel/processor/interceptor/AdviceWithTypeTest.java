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
import org.apache.camel.model.LogDefinition;
import org.apache.camel.model.SplitDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.model.TransformDefinition;

/**
 * Advice with tests
 */
public class AdviceWithTypeTest extends ContextTestSupport {

    public void testUnknownType() throws Exception {
        try {
            context.getRouteDefinitions().get(0).adviceWith(context, new AdviceWithRouteBuilder() {
                @Override
                public void configure() throws Exception {
                    weaveByType(SplitDefinition.class).replace().to("mock:xxx");
                }
            });
            fail("Should hve thrown exception");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().startsWith("There are no outputs which matches: SplitDefinition in the route"));
        }
    }

    public void testReplace() throws Exception {
        // START SNIPPET: e1
        context.getRouteDefinitions().get(0).adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                // weave by type in the route
                // and replace it with the following route path
                weaveByType(LogDefinition.class).replace().multicast().to("mock:a").to("mock:b");
            }
        });
        // END SNIPPET: e1

        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:b").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "World");

        assertMockEndpointsSatisfied();
    }

    public void testRemove() throws Exception {
        // START SNIPPET: e2
        context.getRouteDefinitions().get(0).adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                // weave the type in the route and remove it
                weaveByType(TransformDefinition.class).remove();
            }
        });
        // END SNIPPET: e2

        getMockEndpoint("mock:result").expectedBodiesReceived("World");

        template.sendBody("direct:start", "World");

        assertMockEndpointsSatisfied();
    }

    public void testBefore() throws Exception {
        // START SNIPPET: e3
        context.getRouteDefinitions().get(0).adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                // weave the type in the route and remove it
                // and insert the following route path before the adviced node
                weaveByType(ToDefinition.class).before().transform(constant("Bye World"));
            }
        });
        // END SNIPPET: e3

        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");

        template.sendBody("direct:start", "World");

        assertMockEndpointsSatisfied();
    }

    public void testAfter() throws Exception {
        // START SNIPPET: e4
        context.getRouteDefinitions().get(0).adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                // weave the type in the route and remove it
                // and insert the following route path after the adviced node
                weaveByType(ToDefinition.class).after().transform(constant("Bye World"));
            }
        });
        // END SNIPPET: e4

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        Object out = template.requestBody("direct:start", "World");
        assertEquals("Bye World", out);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e5
                from("direct:start")
                    .transform(simple("Hello ${body}"))
                    .log("Got ${body}")
                    .to("mock:result");
                // END SNIPPET: e5
            }
        };
    }
}