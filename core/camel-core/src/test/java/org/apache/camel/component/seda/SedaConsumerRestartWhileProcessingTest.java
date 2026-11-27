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
package org.apache.camel.component.seda;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.service.ServiceHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The same SedaConsumer is stopped and started again while its threads process exchanges, as a route policy does with
 * ServiceHelper.stopService and startService (RoutePolicySupport.stopConsumer and startConsumer, used by the leader
 * election policies). The stop interrupts the threads and does not wait for them; the processor here does not react to
 * the interrupt (like a blocking call that cannot be interrupted).
 */
class SedaConsumerRestartWhileProcessingTest extends ContextTestSupport {

    private final AtomicInteger active = new AtomicInteger();
    private final AtomicInteger maxActive = new AtomicInteger();
    private final AtomicInteger processed = new AtomicInteger();
    // the release latch of each body that holds the thread, and the thread that processes it
    private final Map<String, CountDownLatch> releases = new ConcurrentHashMap<>();
    private final Map<String, Thread> threads = new ConcurrentHashMap<>();
    private final Map<String, CountDownLatch> busy = new ConcurrentHashMap<>();

    @AfterEach
    void releaseAll() {
        releases.values().forEach(CountDownLatch::countDown);
    }

    private void hold(String body, boolean keepInterrupt) {
        threads.put(body, Thread.currentThread());
        busy.computeIfAbsent(body, b -> new CountDownLatch(1)).countDown();
        CountDownLatch release = releases.computeIfAbsent(body, b -> new CountDownLatch(1));
        boolean interrupted = false;
        while (true) {
            try {
                release.await(30, TimeUnit.SECONDS);
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted && keepInterrupt) {
            Thread.currentThread().interrupt();
        }
    }

    private void sendAndWaitBusy(String... bodies) throws Exception {
        for (String body : bodies) {
            busy.computeIfAbsent(body, b -> new CountDownLatch(1));
            template.sendBody("seda:foo", body);
        }
        for (String body : bodies) {
            assertTrue(busy.get(body).await(5, TimeUnit.SECONDS), body + " is not being processed");
        }
    }

    private void release(String body) {
        releases.computeIfAbsent(body, b -> new CountDownLatch(1)).countDown();
    }

    private SedaConsumer restartConsumer() {
        SedaConsumer consumer = (SedaConsumer) context.getRoute("seda").getConsumer();
        ServiceHelper.stopService(consumer);
        ServiceHelper.startService(consumer);
        return consumer;
    }

    private Thread prepareShutdownInThread(SedaConsumer consumer) {
        Thread shutdown = new Thread(() -> consumer.prepareShutdown(false, false), "prepare-shutdown");
        shutdown.start();
        return shutdown;
    }

    @Test
    void testRestartWhileBusyKeepsConcurrentConsumers() throws Exception {
        sendAndWaitBusy("a1", "a2");
        restartConsumer();
        release("a1");
        release("a2");
        await().atMost(5, TimeUnit.SECONDS).until(() -> active.get() == 0);

        maxActive.set(0);
        sendAndWaitBusy("b1", "b2");
        template.sendBody("seda:foo", "b3");
        // concurrentConsumers=2: the third exchange must wait for a free thread
        await().during(500, TimeUnit.MILLISECONDS).atMost(2, TimeUnit.SECONDS).until(() -> active.get() == 2);
        assertEquals(2, maxActive.get());
        release("b1");
        release("b2");
        release("b3");
        await().atMost(5, TimeUnit.SECONDS).until(() -> processed.get() == 5);
        // the threads of the first start ended after their exchange
        await().atMost(5, TimeUnit.SECONDS).until(() -> !threads.get("a1").isAlive() && !threads.get("a2").isAlive());
    }

    @Test
    void testPrepareShutdownAfterRestartWaitsForTheExchangeBeingProcessed() throws Exception {
        sendAndWaitBusy("a1", "a2");
        SedaConsumer consumer = restartConsumer();
        release("a1");
        release("a2");
        await().atMost(5, TimeUnit.SECONDS).until(() -> active.get() == 0);

        sendAndWaitBusy("b1");
        Thread shutdown = prepareShutdownInThread(consumer);
        await().during(500, TimeUnit.MILLISECONDS).atMost(2, TimeUnit.SECONDS)
                .until(() -> shutdown.isAlive() && active.get() == 1);
        release("b1");
        shutdown.join(5000);
        assertFalse(shutdown.isAlive(), "prepareShutdown did not return once the exchange was processed");
        assertEquals(0, active.get());
    }

    @Test
    void testPrepareShutdownWaitsForAThreadOfTheEarlierStart() throws Exception {
        sendAndWaitBusy("a1", "a2");
        SedaConsumer consumer = restartConsumer();
        // one thread of the first start ends, the other still processes the exchange it polled before the stop
        release("a1");
        await().atMost(5, TimeUnit.SECONDS).until(() -> active.get() == 1);

        Thread shutdown = prepareShutdownInThread(consumer);
        await().during(500, TimeUnit.MILLISECONDS).atMost(2, TimeUnit.SECONDS)
                .until(() -> shutdown.isAlive() && active.get() == 1);
        release("a2");
        shutdown.join(5000);
        assertFalse(shutdown.isAlive(), "prepareShutdown did not return once the exchange was processed");
        assertEquals(0, active.get());
    }

    @Test
    void testThreadInterruptedByTheStopEndsAfterTheRestart() throws Exception {
        sendAndWaitBusy("keep");
        SedaConsumer consumer = restartConsumer();
        // the processor returns with the interrupt flag of the stop still set
        release("keep");
        Thread old = threads.get("keep");
        await().atMost(5, TimeUnit.SECONDS).until(() -> !old.isAlive());

        for (int i = 0; i < 10; i++) {
            template.sendBody("seda:foo", "fast");
        }
        await().atMost(5, TimeUnit.SECONDS).until(() -> processed.get() == 11);

        // nothing pending or in flight: a graceful shutdown does not wait for its timeout (10 seconds)
        Thread shutdown = prepareShutdownInThread(consumer);
        shutdown.join(5000);
        assertFalse(shutdown.isAlive(), "prepareShutdown waits for a thread that does not end");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.getShutdownStrategy().setTimeout(10);
                from("seda:foo?concurrentConsumers=2&pollTimeout=100").routeId("seda")
                        .process(e -> {
                            String body = e.getMessage().getBody(String.class);
                            int a = active.incrementAndGet();
                            maxActive.accumulateAndGet(a, Math::max);
                            try {
                                if (!"fast".equals(body)) {
                                    hold(body, "keep".equals(body));
                                }
                            } finally {
                                active.decrementAndGet();
                                processed.incrementAndGet();
                            }
                        });
            }
        };
    }
}
