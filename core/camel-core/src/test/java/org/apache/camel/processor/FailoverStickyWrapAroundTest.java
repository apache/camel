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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Sticky failover (without round robin) must also try the endpoints before the last known good endpoint, once each.
 */
public class FailoverStickyWrapAroundTest extends ContextTestSupport {

    private final Set<String> down = ConcurrentHashMap.newKeySet();

    @Test
    public void testFailoverStickyContinuesFromFirstEndpoint() throws Exception {
        // a and b are down, so c becomes the last known good endpoint
        down.add("a");
        down.add("b");
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:b").expectedMessageCount(1);
        getMockEndpoint("mock:c").expectedBodiesReceived("Hello World");
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();

        // now c is down and a and b are up: sticky starts from c, and must then continue from a
        resetMocks();
        down.clear();
        down.add("c");
        getMockEndpoint("mock:a").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:b").expectedMessageCount(0);
        getMockEndpoint("mock:c").expectedBodiesReceived("Bye World");
        template.sendBody("direct:start", "Bye World");
        assertMockEndpointsSatisfied();

        // and a is now the last known good endpoint
        resetMocks();
        getMockEndpoint("mock:a").expectedBodiesReceived("Hi World");
        getMockEndpoint("mock:b").expectedMessageCount(0);
        getMockEndpoint("mock:c").expectedMessageCount(0);
        template.sendBody("direct:start", "Hi World");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testFailoverStickyTriesEachEndpointOnceWhenAllDown() throws Exception {
        down.add("a");
        down.add("b");
        getMockEndpoint("mock:c").expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();

        resetMocks();
        down.add("c");
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:b").expectedMessageCount(1);
        getMockEndpoint("mock:c").expectedMessageCount(1);
        assertThrows(CamelExecutionException.class, () -> template.sendBody("direct:start", "Bye World"));
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .loadBalance().failover(-1, false, false, true)
                        .to("direct:a", "direct:b", "direct:c");

                from("direct:a").to("mock:a").process(e -> failIfDown("a"));
                from("direct:b").to("mock:b").process(e -> failIfDown("b"));
                from("direct:c").to("mock:c").process(e -> failIfDown("c"));
            }
        };
    }

    private void failIfDown(String name) {
        if (down.contains(name)) {
            throw new IllegalArgumentException(name + " is down");
        }
    }
}
