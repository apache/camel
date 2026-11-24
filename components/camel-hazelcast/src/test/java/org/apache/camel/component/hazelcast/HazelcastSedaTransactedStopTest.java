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
package org.apache.camel.component.hazelcast;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.hazelcast.collection.IQueue;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.ShutdownRoute;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stopping the route of a transacted Hazelcast SEDA consumer while an exchange is being routed. The stop shuts the
 * consumer threads down, which interrupts a thread that still waits for its exchange: the transaction must not be
 * rolled back while the exchange goes on and completes, or its message is consumed again.
 */
class HazelcastSedaTransactedStopTest extends CamelTestSupport {

    private final CountDownLatch release = new CountDownLatch(1);
    private final AtomicInteger started = new AtomicInteger();
    private final AtomicInteger completed = new AtomicInteger();
    private final AtomicLong selfStopMillis = new AtomicLong(-1);
    private final ExecutorService stopper = Executors.newSingleThreadExecutor();
    private HazelcastInstance member;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        Config config = new Config();
        config.setClusterName("seda-transacted-stop-" + UUID.randomUUID());
        config.getMetricsConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getAutoDetectionConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
        member = Hazelcast.newHazelcastInstance(config);
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("hazelcastInstance", member);
        // the time a graceful shutdown of a thread pool waits before it interrupts the threads (10 s by default, which
        // is less than the 45 s that the shutdown strategy waits for the exchanges in flight), scaled down for the test
        context.getExecutorServiceManager().setShutdownAwaitTermination(1000);
        return context;
    }

    @AfterEach
    void shutdownMember() {
        release.countDown();
        // stop the routes before the member, so the consumers do not poll a member that is shut down
        context.stop();
        stopper.shutdownNow();
        if (member != null) {
            member.shutdown();
        }
    }

    @Test
    void exchangeThatContinuesOnAnotherThreadIsCommittedWhenTheRouteStops() throws Exception {
        stopWhileAnExchangeIsRouted("async");
    }

    @Test
    void synchronousExchangeIsCommittedWhenTheRouteStops() throws Exception {
        stopWhileAnExchangeIsRouted("sync");
    }

    @Test
    void exchangeThatOutlastsTheShutdownTimeoutIsRolledBack() throws Exception {
        // control (as before the fix): the stop waits at most the shutdown timeout, and then rolls back the exchange
        context.getShutdownStrategy().setTimeout(2);
        IQueue<Object> queue = member.getQueue("async");
        queue.offer("a");
        context.getRouteController().startRoute("async");
        await().atMost(10, TimeUnit.SECONDS).until(() -> started.get() == 1);

        long start = System.nanoTime();
        context.getRouteController().stopRoute("async");
        long stopMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertTrue(stopMillis < 8000, "Stopping the route took " + stopMillis + " ms");
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(1, queue.size(),
                "The exchange that was still routed when the shutdown timed out was not rolled back"));
        assertEquals(0, completed.get());
    }

    @Test
    void forcedShutdownDoesNotWaitForTheExchangeAgain() throws Exception {
        // the route is deferred, so the shutdown strategy waits for its exchange before it stops the consumer: when it
        // times out, it stops the consumer at once (forced), and the consumer must not wait another shutdown timeout
        context.getShutdownStrategy().setTimeout(4);
        IQueue<Object> queue = member.getQueue("deferred");
        queue.offer("a");
        context.getRouteController().startRoute("deferred");
        await().atMost(10, TimeUnit.SECONDS).until(() -> started.get() == 1);

        long start = System.nanoTime();
        context.getRouteController().stopRoute("deferred");
        long stopMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        // 4 s of the shutdown strategy and 1 s of shutdownAwaitTermination; another 4 s when the consumer waits again
        assertTrue(stopMillis < 7000, "Stopping the route took " + stopMillis + " ms");
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(1, queue.size(),
                "The exchange that was still routed when the shutdown timed out was not rolled back"));
        assertEquals(0, completed.get());
    }

    @Test
    void consumerStoppedByARoutePolicyOfItsOwnExchangeDoesNotWaitForIt() throws Exception {
        // a route policy stops the consumer when its exchange completes, on the consumer thread of that exchange,
        // which cannot end its exchange while the stop waits for it
        context.getShutdownStrategy().setTimeout(6);
        member.getQueue("selfstop").offer("a");
        context.getRouteController().startRoute("selfstop");

        await().atMost(20, TimeUnit.SECONDS).until(() -> selfStopMillis.get() >= 0);
        assertTrue(selfStopMillis.get() < 4000, "Stopping the consumer took " + selfStopMillis.get() + " ms");
    }

    private void stopWhileAnExchangeIsRouted(String routeId) throws Exception {
        IQueue<Object> queue = member.getQueue(routeId);
        queue.offer("a");
        context.getRouteController().startRoute(routeId);
        await().atMost(10, TimeUnit.SECONDS).until(() -> started.get() == 1);

        Future<?> stop = stopper.submit(() -> {
            context.getRouteController().stopRoute(routeId);
            return null;
        });

        // longer than the shutdownAwaitTermination of 1 s, after which the consumer thread was interrupted: its
        // transaction was rolled back (the message back in the queue) while the exchange went on
        await().during(3, TimeUnit.SECONDS).atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertEquals(0, queue.size(), "The transaction was rolled back while the exchange was routed");
            assertFalse(stop.isDone(), "The stop did not wait for the exchange");
        });

        release.countDown();
        stop.get(20, TimeUnit.SECONDS);
        assertEquals(1, completed.get());
        assertEquals(0, queue.size(), "The completed exchange was rolled back");

        // the next start does not consume the message again
        context.getRouteController().startRoute(routeId);
        await().during(2, TimeUnit.SECONDS).atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(1, started.get(), "The message was consumed again"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("hazelcast-seda:async?transacted=true&hazelcastInstance=#hazelcastInstance").routeId("async")
                        .autoStartup(false)
                        .threads(1)
                        .process(exchange -> routeStep());

                from("hazelcast-seda:sync?transacted=true&hazelcastInstance=#hazelcastInstance").routeId("sync")
                        .autoStartup(false)
                        .process(exchange -> routeStep());

                from("hazelcast-seda:deferred?transacted=true&hazelcastInstance=#hazelcastInstance").routeId("deferred")
                        .autoStartup(false)
                        .shutdownRoute(ShutdownRoute.Defer)
                        .process(exchange -> routeStep());

                from("hazelcast-seda:selfstop?transacted=true&hazelcastInstance=#hazelcastInstance").routeId("selfstop")
                        .autoStartup(false)
                        .routePolicy(new RoutePolicySupport() {
                            @Override
                            public void onExchangeDone(Route route, Exchange exchange) {
                                if (selfStopMillis.get() < 0) {
                                    long start = System.nanoTime();
                                    try {
                                        stopConsumer(route.getConsumer());
                                    } catch (Exception e) {
                                        throw new IllegalStateException(e);
                                    } finally {
                                        selfStopMillis.set(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
                                    }
                                }
                            }
                        })
                        .log("${body}");
            }
        };
    }

    private void routeStep() throws InterruptedException {
        started.incrementAndGet();
        if (!release.await(30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("The test did not release the exchange");
        }
        completed.incrementAndGet();
    }
}
