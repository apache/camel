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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Delayer while shutting down so its interrupted and will also stop.
 */
public class DelayerWhileShutdownTest extends ContextTestSupport {

    private final CountDownLatch shortRouteStarted = new CountDownLatch(1);

    @Test
    public void testSendingMessageGetsDelayed() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Short delay");

        template.sendBody("seda:a", "Long delay");
        template.sendBody("seda:b", "Short delay");

        // Wait until the short-delay route has started processing its message
        assertTrue(shortRouteStarted.await(5, TimeUnit.SECONDS),
                "Short-delay route should have started processing within 5 seconds");

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertMockEndpointsSatisfied());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // Long delay must be well above the assertion timeout (5s) to ensure
                // the context shutdown always interrupts it before it completes
                from("seda:a").delay(30000).to("mock:result");
                from("seda:b").process(e -> shortRouteStarted.countDown()).delay(1).to("mock:result");
            }
        };
    }
}
