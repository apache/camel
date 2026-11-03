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

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.support.DefaultThreadPoolFactory;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.throttling.ThrottlingExceptionRoutePolicy;
import org.apache.camel.util.TimeSource;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@link ThrottlingExceptionRoutePolicy} opens the circuit when failureThreshold failures occur within the
 * failureWindow, and an exchange done in the half open state decides: a success closes the circuit, also when the
 * failures that opened it are still within the failure window.
 */
class ThrottlingExceptionRoutePolicyFailureWindowTest extends ContextTestSupport {

    // long enough for two sends in a row to be within it also on a slow machine
    private static final long FAILURE_WINDOW = 1000;

    // the half open check comes before the failures that opened the circuit leave the window (as with the defaults: a
    // window of 60 seconds and a half open check after 30 seconds)
    private final LatchPolicy halfOpenPolicy = new LatchPolicy(1, 60000, 100);
    private final ThrottlingExceptionRoutePolicy windowPolicy
            = new ThrottlingExceptionRoutePolicy(2, FAILURE_WINDOW, 60000, null);
    private final LatchPolicy racePolicy = new LatchPolicy(1, 60000, 100);

    private final CountDownLatch slowEntered = new CountDownLatch(1);
    private final CountDownLatch slowRelease = new CountDownLatch(1);

    // the scheduler of the half open checks of the policy of the race route (the only policy whose circuit opens there)
    private volatile ScheduledExecutorService raceScheduler;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getExecutorServiceManager().setThreadPoolFactory(new DefaultThreadPoolFactory() {
            @Override
            public ScheduledExecutorService newScheduledThreadPool(ThreadPoolProfile profile, ThreadFactory threadFactory) {
                ScheduledExecutorService answer = super.newScheduledThreadPool(profile, threadFactory);
                if ("ThrottlingExceptionRoutePolicy".equals(profile.getId())) {
                    raceScheduler = answer;
                }
                return answer;
            }
        });
        return context;
    }

    @Test
    void testSuccessInHalfOpenStateClosesTheCircuit() throws Exception {
        ServiceSupport consumer = (ServiceSupport) context.getRoute("halfOpen").getConsumer();

        assertThrows(CamelExecutionException.class, () -> template.sendBody("direct:halfOpen", "fail"));
        // the half open check of the timer waits until it may go, so the circuit is still open
        assertEquals("opened", halfOpenPolicy.getStateAsString());
        assertTrue(consumer.isSuspended());

        // the half open timer resumes the consumer
        halfOpenPolicy.timerMayGo.countDown();
        assertTrue(halfOpenPolicy.halfOpened.await(10, TimeUnit.SECONDS));
        assertEquals("half opened", halfOpenPolicy.getStateAsString());
        // no further half open check while the test runs
        halfOpenPolicy.setHalfOpenAfter(60000);

        template.sendBody("direct:halfOpen", "ok");
        assertEquals("closed", halfOpenPolicy.getStateAsString(), "A success in the half open state should close the circuit");
        assertFalse(consumer.isSuspended());
    }

    @Test
    void testFailuresOutsideTheWindowDoNotOpenTheCircuit() {
        ServiceSupport consumer = (ServiceSupport) context.getRoute("window").getConsumer();

        assertThrows(CamelExecutionException.class, () -> template.sendBody("direct:window", "fail"));
        long first = windowPolicy.getLastFailure();
        // the first failure leaves the window (a condition on the clock, there is no event to wait for)
        await().atMost(10, TimeUnit.SECONDS).until(() -> TimeSource.currentTimeMillis() > first + FAILURE_WINDOW);

        // only this failure is within the window
        assertThrows(CamelExecutionException.class, () -> template.sendBody("direct:window", "fail"));
        assertEquals("closed", windowPolicy.getStateAsString(),
                "One failure within the window should not open the circuit with a threshold of 2");
        assertFalse(consumer.isSuspended());

        // and a second failure within the window opens it
        assertThrows(CamelExecutionException.class, () -> template.sendBody("direct:window", "fail"));
        assertEquals("opened", windowPolicy.getStateAsString());
        assertTrue(consumer.isSuspended());
    }

    /**
     * An exchange that was in flight when the circuit opened is done successfully when the half open check of the timer
     * is due, and half opens the circuit itself; the timer's check, which runs at the same time, must then leave the
     * half open circuit to the next exchange, and not open it again because of the failure that opened it.
     */
    @Test
    void testHalfOpenTimerDoesNotReopenACircuitHalfOpenedByAnExchange() throws Exception {
        ServiceSupport consumer = (ServiceSupport) context.getRoute("race").getConsumer();

        Future<Object> slow = template.asyncRequestBody("direct:race", "slow");
        assertTrue(slowEntered.await(10, TimeUnit.SECONDS));
        assertThrows(CamelExecutionException.class, () -> template.sendBody("direct:race", "fail"));
        assertEquals("opened", racePolicy.getStateAsString());
        assertTrue(consumer.isSuspended());

        // the timer's half open check has started (and waits); the straggler is done with a success now
        assertTrue(racePolicy.timerChecking.await(10, TimeUnit.SECONDS));
        slowRelease.countDown();
        assertEquals("slow", slow.get(10, TimeUnit.SECONDS));
        assertEquals("half opened", racePolicy.getStateAsString());
        assertFalse(consumer.isSuspended());

        // the timer's check goes on and ends: the scheduler has one thread, so a task submitted now runs after it
        racePolicy.timerMayGo.countDown();
        raceScheduler.submit(() -> {
        }).get(10, TimeUnit.SECONDS);
        assertEquals("half opened", racePolicy.getStateAsString(),
                "The half open timer should not open the circuit again after an exchange half opened it");
        assertFalse(consumer.isSuspended());

        // the next exchange decides
        template.sendBody("direct:race", "ok");
        assertEquals("closed", racePolicy.getStateAsString());
        assertFalse(consumer.isSuspended());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:halfOpen").routeId("halfOpen").routePolicy(halfOpenPolicy)
                        .filter(body().isEqualTo("fail")).throwException(new IllegalArgumentException("Forced"));

                from("direct:window").routeId("window").routePolicy(windowPolicy)
                        .throwException(new IllegalArgumentException("Forced"));

                from("direct:race").routeId("race").routePolicy(racePolicy)
                        .process(exchange -> {
                            if ("slow".equals(exchange.getMessage().getBody())) {
                                slowEntered.countDown();
                                assertTrue(slowRelease.await(10, TimeUnit.SECONDS));
                            }
                        })
                        .filter(body().isEqualTo("fail")).throwException(new IllegalArgumentException("Forced"));
            }
        };
    }

    /**
     * The policy, with latches: halfOpened when the circuit has been half opened, and a gate that stops the first half
     * open check (on the thread of the policy's scheduler) before it reads the state, until timerMayGo.
     */
    private static final class LatchPolicy extends ThrottlingExceptionRoutePolicy {
        private final CountDownLatch halfOpened = new CountDownLatch(1);
        private final CountDownLatch timerChecking = new CountDownLatch(1);
        private final CountDownLatch timerMayGo = new CountDownLatch(1);
        private volatile Thread timerThread;

        LatchPolicy(int threshold, long failureWindow, long halfOpenAfter) {
            super(threshold, failureWindow, halfOpenAfter, List.of());
        }

        @Override
        protected boolean isThresholdExceeded() {
            // the half open check of the timer calls this first, before it reads the state
            if (timerThread == null && Thread.currentThread().getName().endsWith(" - ThrottlingExceptionRoutePolicy")) {
                timerThread = Thread.currentThread();
                timerChecking.countDown();
                try {
                    timerMayGo.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return super.isThresholdExceeded();
        }

        @Override
        protected void halfOpenCircuit(Route route) {
            super.halfOpenCircuit(route);
            halfOpened.countDown();
        }
    }
}
