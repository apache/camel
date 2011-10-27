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
package org.apache.camel.builder;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;

/**
 * Test how to add routes at runtime using a RouteBuilder
 *
 * @version 
 */
public class AddRoutesAtRuntimeTest extends ContextTestSupport {

    public void testAddRoutesAtRuntime() throws Exception {
        getMockEndpoint("mock:start").expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();
        assertEquals(1, context.getRoutes().size());

        getMockEndpoint("mock:foo").expectedMessageCount(1);
        context.addRoutes(new MyDynamcRouteBuilder(context, "direct:foo", "mock:foo"));
        template.sendBody("direct:foo", "Bye Camel");
        assertMockEndpointsSatisfied();
        assertEquals(2, context.getRoutes().size());

        getMockEndpoint("mock:bar").expectedMessageCount(1);
        context.addRoutes(new MyDynamcRouteBuilder(context, "direct:bar", "mock:bar"));
        template.sendBody("direct:bar", "Hi Camel");
        assertMockEndpointsSatisfied();
        assertEquals(3, context.getRoutes().size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // here is an existing route
                from("direct:start").to("mock:start");
            }
        };
    }

    /**
     * This route builder is a skeleton to add new routes at runtime
     */
    private static final class MyDynamcRouteBuilder extends RouteBuilder {
        private final String from;
        private final String to;

        private MyDynamcRouteBuilder(CamelContext context, String from, String to) {
            super(context);
            this.from = from;
            this.to = to;
        }

        @Override
        public void configure() throws Exception {
            from(from).to(to);
        }
    }
}
