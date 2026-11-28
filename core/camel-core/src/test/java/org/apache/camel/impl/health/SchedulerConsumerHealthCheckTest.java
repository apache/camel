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
package org.apache.camel.impl.health;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.scheduler.SchedulerConsumer;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;

public class SchedulerConsumerHealthCheckTest {

    private static final String TEST_ROUTE_ID = "Test-Route";
    // the exchange that throws an Error, after successful ones
    private static final int ERROR_ON_POLL = 3;

    private CamelContext context;

    @AfterEach
    public void stopContext() {
        if (context != null) {
            context.stop();
        }
    }

    @Test
    public void testShouldBeUP() throws Exception {
        context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("scheduler:foo?initialDelay=5000").id(TEST_ROUTE_ID).log("Message");
            }
        });
        context.start();

        Assertions.assertEquals(HealthCheck.State.UP, healthCheck().getState());
    }

    @Test
    public void testErrorThrownByRouteStopsPollingAndShouldBeDown() throws Exception {
        AtomicInteger polls = startRouteThatThrowsOnPoll("scheduler:foo?initialDelay=0&delay=10");

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> Assertions.assertEquals(HealthCheck.State.DOWN, healthCheck().getState()));
        HealthCheck.Result result = healthCheck();
        Assertions.assertInstanceOf(OutOfMemoryError.class, result.getError().orElse(null));

        // the consumer does not poll again
        await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(1))
                .until(() -> polls.get() == ERROR_ON_POLL);
        Assertions.assertEquals(HealthCheck.State.DOWN, healthCheck().getState());
    }

    @Test
    public void testErrorThrownByRouteWithConcurrentConsumersStopsAllTasksAndShouldBeDown() throws Exception {
        AtomicInteger polls = startRouteThatThrowsOnPoll(
                "scheduler:foo?initialDelay=0&delay=10&poolSize=2&scheduler.concurrentConsumers=2");

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> Assertions.assertEquals(HealthCheck.State.DOWN, healthCheck().getState()));

        // the other task stops polling too (at most, a poll it had already started completes)
        int pollsWhenDown = polls.get();
        await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(10))
                .until(() -> polls.get() <= pollsWhenDown + 1);
        Assertions.assertEquals(HealthCheck.State.DOWN, healthCheck().getState());
    }

    @Test
    public void testRouteRestartAfterErrorPollsAgainAndShouldBeUp() throws Exception {
        AtomicInteger polls = startRouteThatThrowsOnPoll("scheduler:foo?initialDelay=0&delay=10");

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> Assertions.assertEquals(HealthCheck.State.DOWN, healthCheck().getState()));

        context.getRouteController().stopRoute(TEST_ROUTE_ID);
        context.getRouteController().startRoute(TEST_ROUTE_ID);

        await().atMost(Duration.ofSeconds(5)).until(() -> polls.get() > ERROR_ON_POLL);
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> Assertions.assertEquals(HealthCheck.State.UP, healthCheck().getState()));
    }

    /**
     * Starts a route whose exchange number ERROR_ON_POLL throws an OutOfMemoryError. Camel's error handling does not
     * handle a VirtualMachineError, so it reaches the consumer.
     */
    private AtomicInteger startRouteThatThrowsOnPoll(String uri) throws Exception {
        AtomicInteger polls = new AtomicInteger();
        context = new DefaultCamelContext();
        // do not wait long for inflight exchanges on shutdown
        context.getShutdownStrategy().setTimeout(1);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(uri).id(TEST_ROUTE_ID).process(exchange -> {
                    if (polls.incrementAndGet() == ERROR_ON_POLL) {
                        throw new OutOfMemoryError("Simulated error in the route");
                    }
                });
            }
        });
        context.start();
        return polls;
    }

    private HealthCheck.Result healthCheck() {
        Route route = context.getRoute(TEST_ROUTE_ID);
        SchedulerConsumer consumer = (SchedulerConsumer) route.getConsumer();
        return consumer.getHealthCheck().call();
    }

}
