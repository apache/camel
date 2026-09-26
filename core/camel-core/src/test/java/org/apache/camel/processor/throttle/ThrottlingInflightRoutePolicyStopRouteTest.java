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
package org.apache.camel.processor.throttle;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.throttling.ThrottlingInflightRoutePolicy;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A graceful stop of a route suspends the consumer and waits for the inflight exchanges. When such an exchange
 * completes, the {@link ThrottlingInflightRoutePolicy} must not resume the consumer, which would take new messages
 * while the stop waits for the inflight exchanges.
 */
class ThrottlingInflightRoutePolicyStopRouteTest extends ContextTestSupport {

    private final CountDownLatch firstStarted = new CountDownLatch(1);
    private final CountDownLatch releaseFirst = new CountDownLatch(1);
    private final CountDownLatch releaseOthers = new CountDownLatch(1);
    private final AtomicInteger counter = new AtomicInteger();
    private final AtomicBoolean stopping = new AtomicBoolean();
    private final AtomicInteger startedDuringStop = new AtomicInteger();

    @Test
    void testGracefulStopRoute() throws Exception {
        try {
            assertTrue(firstStarted.await(10, TimeUnit.SECONDS), "The first exchange should be started");

            CompletableFuture<Void> stop = CompletableFuture.runAsync(() -> {
                try {
                    context.getRouteController().stopRoute("foo", 5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            // the graceful stop suspends the consumer and then waits for the inflight exchange
            ServiceSupport consumer = (ServiceSupport) context.getRoute("foo").getConsumer();
            await().atMost(10, TimeUnit.SECONDS).until(consumer::isSuspended);
            stopping.set(true);
            releaseFirst.countDown();

            stop.get(20, TimeUnit.SECONDS);

            assertEquals(0, startedDuringStop.get(), "No exchange should be started while the route is being stopped");
            assertFalse(context.getShutdownStrategy().isTimeoutOccurred(), "The route should be stopped gracefully");
            assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("foo"));
        } finally {
            releaseFirst.countDown();
            releaseOthers.countDown();
        }
    }

    private void onExchange(Exchange exchange) throws Exception {
        if (counter.incrementAndGet() == 1) {
            firstStarted.countDown();
            releaseFirst.await(20, TimeUnit.SECONDS);
        } else if (stopping.get()) {
            // an exchange that the consumer took while the route is being stopped keeps the stop waiting
            startedDuringStop.incrementAndGet();
            releaseOthers.await(20, TimeUnit.SECONDS);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                ThrottlingInflightRoutePolicy policy = new ThrottlingInflightRoutePolicy();
                policy.setMaxInflightExchanges(10);

                from("timer:foo?period=10").routeId("foo").routePolicy(policy)
                        .process(e -> onExchange(e));
            }
        };
    }
}
