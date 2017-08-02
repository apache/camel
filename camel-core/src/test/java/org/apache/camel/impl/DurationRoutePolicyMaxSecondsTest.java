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
package org.apache.camel.impl;

import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

import static org.awaitility.Awaitility.await;

public class DurationRoutePolicyMaxSecondsTest extends ContextTestSupport {

    public void testDurationRoutePolicy() throws Exception {
        assertTrue(context.getRouteStatus("foo").isStarted());
        assertFalse(context.getRouteStatus("foo").isStopped());

        // the policy should stop the route after 2 seconds which is approx 20-30 messages
        getMockEndpoint("mock:foo").expectedMinimumMessageCount(10);
        assertMockEndpointsSatisfied();

        // need a little time to stop async
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertFalse(context.getRouteStatus("foo").isStarted());
            assertTrue(context.getRouteStatus("foo").isStopped());
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                DurationRoutePolicy policy = new DurationRoutePolicy();
                policy.setMaxSeconds(2);

                from("timer:foo?period=100").routeId("foo").routePolicy(policy)
                    .to("mock:foo");
            }
        };
    }
}
