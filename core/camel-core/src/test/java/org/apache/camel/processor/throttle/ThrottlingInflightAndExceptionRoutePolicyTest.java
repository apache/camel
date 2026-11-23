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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.throttling.ThrottlingExceptionRoutePolicy;
import org.apache.camel.throttling.ThrottlingInflightRoutePolicy;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A route with both {@link ThrottlingInflightRoutePolicy} and {@link ThrottlingExceptionRoutePolicy}, in both orders:
 * each policy resumes the consumer only when the other one no longer keeps it suspended.
 * <p>
 * Four exchanges are in flight at the same time (four concurrent consumers, at most one inflight exchange), and the
 * test decides the order in which they complete. The half open check of the circuit is run by the test, not by a timer.
 */
class ThrottlingInflightAndExceptionRoutePolicyTest extends ContextTestSupport {

    private final Map<String, CountDownLatch> gates = new ConcurrentHashMap<>();
    private final AtomicInteger entered = new AtomicInteger();
    private final Map<String, CountingInflightPolicy> inflightPolicies = new ConcurrentHashMap<>();
    private final Map<String, ManualHalfOpenPolicy> breakers = new ConcurrentHashMap<>();

    @ParameterizedTest
    @ValueSource(strings = { "inflightFirst", "exceptionFirst" })
    void inflightPolicyDoesNotResumeTheConsumerWhileTheCircuitIsOpen(String routeId) throws Exception {
        Route route = context.getRoute(routeId);
        ServiceSupport consumer = (ServiceSupport) route.getConsumer();
        ManualHalfOpenPolicy breaker = breakers.get(routeId);

        startFourExchanges(routeId);

        // A fails with 3 still in flight (more than 1): the inflight policy suspends the consumer
        complete(routeId, "A", 1);
        assertTrue(consumer.isSuspended());
        assertEquals("closed", breaker.getStateAsString());

        // B fails, the second failure: the circuit opens (the consumer is suspended already)
        complete(routeId, "B", 2);
        assertEquals("opened", breaker.getStateAsString());
        assertTrue(consumer.isSuspended());

        // C succeeds with 1 in flight: the inflight policy would resume the consumer, but the circuit is open
        complete(routeId, "C", 3);
        assertEquals("opened", breaker.getStateAsString());
        assertTrue(consumer.isSuspended(), "the consumer must stay suspended while the circuit is open");

        complete(routeId, "D", 4);
        assertTrue(consumer.isSuspended());

        // the half open check resumes the consumer, as no other policy keeps it suspended
        breaker.halfOpen(route);
        assertEquals("half opened", breaker.getStateAsString());
        assertTrue(consumer.isStarted());

        // the next exchange succeeds and closes the circuit
        gate("E").countDown();
        template.sendBody("seda:" + routeId, "E");
        awaitDone(routeId, 5);
        assertEquals("closed", breaker.getStateAsString());
        assertTrue(consumer.isStarted());
    }

    @ParameterizedTest
    @ValueSource(strings = { "inflightFirst", "exceptionFirst" })
    void halfOpenCheckDoesNotResumeTheConsumerTheInflightPolicySuspended(String routeId) throws Exception {
        Route route = context.getRoute(routeId);
        ServiceSupport consumer = (ServiceSupport) route.getConsumer();
        ManualHalfOpenPolicy breaker = breakers.get(routeId);

        startFourExchanges(routeId);

        complete(routeId, "A", 1);
        complete(routeId, "B", 2);
        assertEquals("opened", breaker.getStateAsString());
        assertTrue(consumer.isSuspended());

        // half open with 2 still in flight (more than 1): the inflight policy still keeps the consumer suspended
        breaker.halfOpen(route);
        assertEquals("half opened", breaker.getStateAsString());
        assertTrue(consumer.isSuspended(), "the consumer must stay suspended while the inflight policy keeps it suspended");

        // C succeeds with 1 in flight: the inflight policy resumes the consumer, and the success closes the circuit
        complete(routeId, "C", 3);
        assertTrue(consumer.isStarted());
        assertEquals("closed", breaker.getStateAsString());

        complete(routeId, "D", 4);
        assertTrue(consumer.isStarted());
    }

    @ParameterizedTest
    @ValueSource(strings = { "inflightFirst", "exceptionFirst" })
    void closingTheOpenCircuitDoesNotResumeTheConsumerTheInflightPolicySuspended(String routeId) throws Exception {
        Route route = context.getRoute(routeId);
        ServiceSupport consumer = (ServiceSupport) route.getConsumer();
        ManualHalfOpenPolicy breaker = breakers.get(routeId);

        startFourExchanges(routeId);

        complete(routeId, "A", 1);
        complete(routeId, "B", 2);
        assertEquals("opened", breaker.getStateAsString());

        // closing the open circuit (as with a halfOpenHandler that is ready) with 2 still in flight: the inflight
        // policy still keeps the consumer suspended
        breaker.close(route);
        assertEquals("closed", breaker.getStateAsString());
        assertTrue(consumer.isSuspended(), "the consumer must stay suspended while the inflight policy keeps it suspended");

        // C succeeds with 1 in flight: the inflight policy resumes the consumer
        complete(routeId, "C", 3);
        assertTrue(consumer.isStarted());

        complete(routeId, "D", 4);
        assertTrue(consumer.isStarted());
    }

    @ParameterizedTest
    @ValueSource(strings = { "inflightFirst", "exceptionFirst" })
    void circuitStaysOpenWhenTheResumeOfTheHalfOpenCheckFails(String routeId) throws Exception {
        Route route = context.getRoute(routeId);
        ServiceSupport consumer = (ServiceSupport) route.getConsumer();
        ManualHalfOpenPolicy breaker = breakers.get(routeId);

        startFourExchanges(routeId);
        complete(routeId, "A", 1);
        complete(routeId, "B", 2);
        complete(routeId, "C", 3);
        complete(routeId, "D", 4);
        assertEquals("opened", breaker.getStateAsString());

        // the policy lets go of the consumer before it resumes it: if the resume fails, the circuit stays open
        breaker.failResume = true;
        breaker.halfOpen(route);
        assertEquals("opened", breaker.getStateAsString());
        assertTrue(consumer.isSuspended());

        breaker.failResume = false;
        breaker.halfOpen(route);
        assertEquals("half opened", breaker.getStateAsString());
        assertTrue(consumer.isStarted());
    }

    private void startFourExchanges(String routeId) {
        for (String body : new String[] { "A", "B", "C", "D" }) {
            gate(body);
            template.sendBody("seda:" + routeId, body);
        }
        await().atMost(10, TimeUnit.SECONDS).until(() -> entered.get() == 4);
    }

    // lets the exchange complete, and waits until both policies are done with it
    private void complete(String routeId, String body, int done) {
        gate(body).countDown();
        awaitDone(routeId, done);
    }

    private void awaitDone(String routeId, int done) {
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> inflightPolicies.get(routeId).done.get() == done && breakers.get(routeId).done.get() == done);
    }

    private CountDownLatch gate(String body) {
        return gates.computeIfAbsent(body, b -> new CountDownLatch(1));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                CountingInflightPolicy inflight1 = new CountingInflightPolicy();
                ManualHalfOpenPolicy breaker1 = new ManualHalfOpenPolicy();
                inflightPolicies.put("inflightFirst", inflight1);
                breakers.put("inflightFirst", breaker1);
                from("seda:inflightFirst?concurrentConsumers=4").routeId("inflightFirst")
                        .routePolicy(inflight1, breaker1)
                        .process(e -> route(e));

                CountingInflightPolicy inflight2 = new CountingInflightPolicy();
                ManualHalfOpenPolicy breaker2 = new ManualHalfOpenPolicy();
                inflightPolicies.put("exceptionFirst", inflight2);
                breakers.put("exceptionFirst", breaker2);
                from("seda:exceptionFirst?concurrentConsumers=4").routeId("exceptionFirst")
                        .routePolicy(breaker2, inflight2)
                        .process(e -> route(e));
            }
        };
    }

    private void route(Exchange exchange) throws Exception {
        String body = exchange.getMessage().getBody(String.class);
        entered.incrementAndGet();
        assertTrue(gate(body).await(20, TimeUnit.SECONDS));
        // the downstream fails, except for C and E
        if (!"C".equals(body) && !"E".equals(body)) {
            throw new IllegalStateException("Downstream failure: " + body);
        }
    }

    private static class CountingInflightPolicy extends ThrottlingInflightRoutePolicy {
        private final AtomicInteger done = new AtomicInteger();

        CountingInflightPolicy() {
            setMaxInflightExchanges(1);
            setResumePercentOfMax(70);
        }

        @Override
        public void onExchangeDone(Route route, Exchange exchange) {
            super.onExchangeDone(route, exchange);
            done.incrementAndGet();
        }
    }

    // opens the circuit on the second failure; the half open check runs only when the test calls halfOpen
    private static class ManualHalfOpenPolicy extends ThrottlingExceptionRoutePolicy {
        private final AtomicInteger done = new AtomicInteger();
        private volatile boolean failResume;

        ManualHalfOpenPolicy() {
            super(2, 60000, 60000, null);
        }

        @Override
        public void onExchangeDone(Route route, Exchange exchange) {
            super.onExchangeDone(route, exchange);
            done.incrementAndGet();
        }

        @Override
        protected void addHalfOpenTimer(Route route, long delay) {
            // no timer: the test runs the half open check
        }

        @Override
        public boolean resumeOrStartConsumer(Consumer consumer) throws Exception {
            if (failResume) {
                throw new IllegalStateException("Cannot resume the consumer");
            }
            return super.resumeOrStartConsumer(consumer);
        }

        void halfOpen(Route route) {
            halfOpenCircuit(route);
        }

        void close(Route route) {
            closeCircuit(route);
        }
    }
}
