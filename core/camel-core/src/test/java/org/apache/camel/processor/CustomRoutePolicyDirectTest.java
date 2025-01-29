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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.RoutePolicySupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CustomRoutePolicyDirectTest extends ContextTestSupport {

    private final MyCustomRoutePolicy policy = new MyCustomRoutePolicy();

    private static class MyCustomRoutePolicy extends RoutePolicySupport {

        private volatile int inflight1;
        private volatile int inflight2;

        @Override
        public void onExchangeBegin(Route route, Exchange exchange) {
            if ("foo".equals(route.getId())) {
                inflight1++;
            } else {
                inflight2++;
            }
        }

        @Override
        public void onExchangeDone(Route route, Exchange exchange) {
            if ("foo".equals(route.getId())) {
                inflight1--;
            } else {
                inflight2--;
            }
        }

        public int getInflight1() {
            return inflight1;
        }

        public int getInflight2() {
            return inflight2;
        }
    }

    @Test
    public void testCustomPolicy() throws Exception {
        assertEquals(0, policy.getInflight1());
        assertEquals(0, policy.getInflight2());

        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBody("direct:foo", "Hello World");
        assertMockEndpointsSatisfied();

        assertEquals(0, policy.getInflight1());
        assertEquals(0, policy.getInflight2());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:foo").routeId("foo").routePolicy(policy)
                        .process(e -> {
                            Assertions.assertEquals(1, policy.getInflight1());
                            Assertions.assertEquals(0, policy.getInflight2());
                        })
                        .to("direct:bar")
                        .process(e -> {
                            Assertions.assertEquals(1, policy.getInflight1());
                            Assertions.assertEquals(0, policy.getInflight2());
                        })
                        .to("mock:result");

                from("direct:bar").routeId("bar").routePolicy(policy)
                        .process(e -> {
                            Assertions.assertEquals(1, policy.getInflight1());
                            Assertions.assertEquals(1, policy.getInflight2());
                        })
                        .to("mock:b");

            }
        };
    }
}
