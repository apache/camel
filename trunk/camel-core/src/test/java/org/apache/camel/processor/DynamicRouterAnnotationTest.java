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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class DynamicRouterAnnotationTest extends ContextTestSupport {

    private static int invoked;
    private static List<String> bodies = new ArrayList<String>();

    public void testDynamicRouterAnnotation() throws Exception {
        getMockEndpoint("mock:a").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:b").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:c").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:foo").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        assertEquals(5, invoked);
        assertEquals(5, bodies.size());
        assertEquals("Hello World", bodies.get(0));
        assertEquals("Hello World", bodies.get(1));
        assertEquals("Hello World", bodies.get(2));
        assertEquals("Bye World", bodies.get(3));
        assertEquals("Bye World", bodies.get(4));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                from("direct:start")
                    .bean(MyBean.class, "dynamicRouter");

                from("direct:foo").transform(constant("Bye World")).to("mock:foo");
                // END SNIPPET: e1
            }
        };
    }

    // START SNIPPET: e2
    public static class MyBean {

        @org.apache.camel.DynamicRouter
        public String dynamicRouter(String body) {
            bodies.add(body);
            invoked++;

            if (invoked == 1) {
                return "mock:a";
            } else if (invoked == 2) {
                return "mock:b,mock:c";
            } else if (invoked == 3) {
                return "direct:foo";
            } else if (invoked == 4) {
                return "mock:result";
            }

            // no more so return null
            return null;
        }
    }
    // END SNIPPET: e2

}
