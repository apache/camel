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

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A failover load balancer without any processors should complete the exchange like the other load balancers do,
 * instead of never calling the callback.
 */
public class FailOverLoadBalanceNoProcessorsTest extends ContextTestSupport {

    @ParameterizedTest
    @ValueSource(strings = { "direct:default", "direct:roundRobin", "direct:sticky", "direct:roundRobinSticky" })
    public void testNoProcessors(String uri) throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        // use async send so the test fails instead of hanging if the exchange is never completed
        Future<String> reply = template.asyncRequestBody(uri, "Hello World", String.class);
        assertEquals("Hello World", reply.get(5, TimeUnit.SECONDS));

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:default").loadBalance().failover().end().to("mock:result");
                from("direct:roundRobin").loadBalance().failover(-1, false, true).end().to("mock:result");
                from("direct:sticky").loadBalance().failover(-1, false, false, true).end().to("mock:result");
                from("direct:roundRobinSticky").loadBalance().failover(-1, false, true, true).end().to("mock:result");
            }
        };
    }

}
