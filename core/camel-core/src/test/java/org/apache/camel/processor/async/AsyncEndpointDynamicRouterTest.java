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
package org.apache.camel.processor.async;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class AsyncEndpointDynamicRouterTest extends ContextTestSupport {

    private static int invoked;
    private static List<String> bodies = new ArrayList<>();

    @Test
    public void testAsyncEndpoint() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");

        String reply = template.requestBody("direct:start", "Hello Camel", String.class);
        assertEquals("Bye World", reply);

        assertMockEndpointsSatisfied();

        assertEquals(4, invoked);
        assertEquals(4, bodies.size());
        assertEquals("Hello Camel", bodies.get(0));
        assertEquals("Bye Camel", bodies.get(1));
        assertEquals("Bye World", bodies.get(2));
        assertEquals("Bye World", bodies.get(3));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.addComponent("async", new MyAsyncComponent());

                from("direct:start").dynamicRouter(method(AsyncEndpointDynamicRouterTest.class, "slip"));

                from("direct:foo").transform(constant("Bye World"));
            }
        };
    }

    /**
     * Use this method to compute dynamic where we should route next.
     *
     * @param body the message body
     * @return endpoints to go, or <tt>null</tt> to indicate the end
     */
    public String slip(String body) {
        bodies.add(body);
        invoked++;

        if (invoked == 1) {
            return "async:bye:camel";
        } else if (invoked == 2) {
            return "direct:foo";
        } else if (invoked == 3) {
            return "mock:result";
        }

        // no more so return null
        return null;
    }

}
