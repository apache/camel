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
package org.apache.camel.component.reactive.streams;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreams;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A graceful shutdown of several routes suspends the consumer first, and must route the items it has queued while the
 * downstream direct consumer is still running: the routes are shutdown in reverse startup order, so the direct consumer
 * is stopped before the reactive-streams one.
 */
class ConsumerGracefulShutdownTest extends BaseReactiveTest {

    private final CountDownLatch gateReached = new CountDownLatch(1);
    private final CountDownLatch gate = new CountDownLatch(1);
    private final CountDownLatch blockSecond = new CountDownLatch(1);
    private final List<Integer> received = new CopyOnWriteArrayList<>();
    private volatile boolean blockSecondItem;

    @AfterEach
    void openGates() {
        gate.countDown();
        blockSecond.countDown();
    }

    @Test
    void testContextStopRoutesTheQueuedItemsToTheDirectRoute() throws Exception {
        startWithQueuedItems();

        stopContextWhileTheFirstItemWaits();

        assertEquals(IntStream.rangeClosed(1, 10).boxed().toList(), received,
                "The items queued in the consumer must reach the direct route before it is stopped");
    }

    @Test
    void testShutdownTimeoutAppliesToTheQueuedItems() throws Exception {
        // the second item blocks in the direct route: the shutdown strategy waits for the queued items up to its
        // timeout, then forces the routes to stop
        context.getShutdownStrategy().setTimeout(2);
        context.getExecutorServiceManager().setShutdownAwaitTermination(1000);
        blockSecondItem = true;
        startWithQueuedItems();

        stopContextWhileTheFirstItemWaits();

        assertTrue(context.getShutdownStrategy().hasTimeoutOccurred(),
                "The shutdown strategy must wait for the queued items until its timeout");
        assertTrue(received.contains(2), "The queued items must be routed during the graceful shutdown");
    }

    private void startWithQueuedItems() {
        Flux.range(1, 1000).subscribe(CamelReactiveStreams.get(context).streamSubscriber("in", Integer.class));

        // the publisher sent the 10 requested numbers: the first one waits in the direct route, the others are queued
        assertTrue(awaitGate(), "The first item must reach the direct route");
        ReactiveStreamsCamelSubscriber subscriber
                = (ReactiveStreamsCamelSubscriber) CamelReactiveStreams.get(context).streamSubscriber("in");
        await().atMost(10, TimeUnit.SECONDS).until(() -> subscriber.getInflightCount() == 10);
    }

    private boolean awaitGate() {
        try {
            return gateReached.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void stopContextWhileTheFirstItemWaits() throws Exception {
        CompletableFuture<Void> stop = CompletableFuture.runAsync(context::stop);
        ServiceSupport consumer = (ServiceSupport) context.getRoute("rs").getConsumer();
        // the graceful shutdown suspends the consumer first
        await().atMost(10, TimeUnit.SECONDS).until(consumer::isSuspended);
        gate.countDown();
        stop.get(30, TimeUnit.SECONDS);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // defined (and started) before the direct route, so it is shutdown after it
                from("reactive-streams:in?maxInflightExchanges=10").routeId("rs")
                        .to("direct:sub");

                from("direct:sub").routeId("sub")
                        .process(e -> {
                            int item = e.getMessage().getBody(Integer.class);
                            if (item == 1) {
                                gateReached.countDown();
                                gate.await();
                            } else if (item == 2 && blockSecondItem) {
                                received.add(item);
                                blockSecond.await();
                                return;
                            }
                            received.add(item);
                        });
            }
        };
    }
}
