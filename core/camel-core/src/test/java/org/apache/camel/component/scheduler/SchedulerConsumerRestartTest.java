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
package org.apache.camel.component.scheduler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.ScheduledPollConsumer;
import org.apache.camel.support.service.ServiceHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A scheduler consumer that is stopped and started again (as JMX or a route policy does) must take the thread pool of
 * the component again, and a consumer must only release the pool it took.
 */
public class SchedulerConsumerRestartTest extends ContextTestSupport {

    private final Map<String, CountDownLatch> polls = new ConcurrentHashMap<>();
    private final ScheduledExecutorService ownPool = Executors.newSingleThreadScheduledExecutor();

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry registry = super.createCamelRegistry();
        registry.bind("ownPool", ownPool);
        return registry;
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        ownPool.shutdownNow();
    }

    @Test
    public void testStoppedAndStartedConsumerPollsAgain() throws Exception {
        awaitPoll("solo");

        Consumer consumer = context.getRoute("solo").getConsumer();
        ServiceHelper.stopService(consumer);
        ServiceHelper.startService(consumer);

        assertFalse(executorOf("solo").isShutdown(),
                "the restarted consumer must not use the thread pool that was shut down when it stopped");
        awaitPoll("solo");
    }

    @Test
    public void testRestartedConsumerDoesNotShutDownThePoolOfAnotherRoute() throws Exception {
        awaitPoll("a");
        awaitPoll("b");

        Consumer consumer = context.getRoute("a").getConsumer();
        ServiceHelper.stopService(consumer);
        ServiceHelper.startService(consumer);
        ServiceHelper.stopService(consumer);

        assertFalse(executorOf("b").isShutdown(), "route b still uses the thread pool of scheduler foo");
        awaitPoll("b");
    }

    @Test
    public void testConsumerWithItsOwnPoolDoesNotReleaseThePoolOfTheComponent() throws Exception {
        awaitPoll("d");

        // route c runs on its own thread pool, so it never took a reference to the pool of scheduler bar
        context.getRouteController().stopRoute("c");

        assertFalse(executorOf("d").isShutdown(), "route d still uses the thread pool of scheduler bar");
        awaitPoll("d");
    }

    private void awaitPoll(String routeId) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        polls.put(routeId, latch);
        assertTrue(latch.await(10, TimeUnit.SECONDS), "route " + routeId + " must poll");
    }

    private ScheduledExecutorService executorOf(String routeId) {
        return ((ScheduledPollConsumer) context.getRoute(routeId).getConsumer()).getScheduledExecutorService();
    }

    private void poll(String routeId) {
        CountDownLatch latch = polls.get(routeId);
        if (latch != null) {
            latch.countDown();
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("scheduler://solo?initialDelay=0&delay=10").routeId("solo").process(e -> poll("solo"));

                from("scheduler://foo?initialDelay=0&delay=10").routeId("a").process(e -> poll("a"));
                from("scheduler://foo?initialDelay=0&delay=11").routeId("b").process(e -> poll("b"));

                from("scheduler://bar?initialDelay=0&delay=10").routeId("d").process(e -> poll("d"));
                from("scheduler://bar?initialDelay=0&delay=12&scheduledExecutorService=#ownPool").routeId("c")
                        .process(e -> poll("c"));
            }
        };
    }
}
