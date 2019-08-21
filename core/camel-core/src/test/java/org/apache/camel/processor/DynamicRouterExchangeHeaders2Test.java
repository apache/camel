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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.apache.camel.Headers;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class DynamicRouterExchangeHeaders2Test extends ContextTestSupport {

    private static List<String> bodies = new ArrayList<>();
    private static List<String> previouses = new ArrayList<>();

    @Test
    public void testDynamicRouter() throws Exception {
        getMockEndpoint("mock:a").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:a").expectedHeaderReceived("invoked", 1);
        getMockEndpoint("mock:b").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:b").expectedHeaderReceived("invoked", 2);
        getMockEndpoint("mock:c").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:c").expectedHeaderReceived("invoked", 2);
        getMockEndpoint("mock:foo").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:foo").expectedHeaderReceived("invoked", 3);
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:result").expectedHeaderReceived("invoked", 4);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        assertEquals(5, bodies.size());
        assertEquals("Hello World", bodies.get(0));
        assertEquals("Hello World", bodies.get(1));
        assertEquals("Hello World", bodies.get(2));
        assertEquals("Bye World", bodies.get(3));
        assertEquals("Bye World", bodies.get(4));

        assertEquals(4, previouses.size());
        assertEquals("mock://a", previouses.get(0));
        assertEquals("mock://c", previouses.get(1));
        assertEquals("direct://foo", previouses.get(2));
        assertEquals("mock://result", previouses.get(3));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    // use a bean as the dynamic router
                    .dynamicRouter(method(DynamicRouterExchangeHeaders2Test.class, "slip"));

                from("direct:foo").transform(constant("Bye World")).to("mock:foo");
            }
        };
    }

    // START SNIPPET: e2
    /**
     * Use this method to compute dynamic where we should route next.
     *
     * @param body the message body
     * @param headers the message headers where we can store state between
     *            invocations
     * @param previous the previous slip
     * @return endpoints to go, or <tt>null</tt> to indicate the end
     */
    public String slip(String body, @Headers Map<String, Object> headers, @Header(Exchange.SLIP_ENDPOINT) String previous) {
        bodies.add(body);
        if (previous != null) {
            previouses.add(previous);
        }

        // get the state from the message headers and keep track how many times
        // we have been invoked
        int invoked = 0;
        Object current = headers.get("invoked");
        if (current != null) {
            invoked = Integer.valueOf(current.toString());
        }
        invoked++;
        // and store the state back on the headers
        headers.put("invoked", invoked);

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
    // END SNIPPET: e2

}
