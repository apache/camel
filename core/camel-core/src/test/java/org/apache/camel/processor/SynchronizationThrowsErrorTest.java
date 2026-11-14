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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.spi.SynchronizationRouteAware;
import org.apache.camel.support.SynchronizationAdapter;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * An Error that is not a VirtualMachineError, thrown by an on completion (a {@link Synchronization}) of an exchange, is
 * logged and ignored as an exception is: the other on completions still run (such as the commit of a consumer), and the
 * unit of work ends (the exchange does not stay inflight).
 */
class SynchronizationThrowsErrorTest extends ContextTestSupport {

    private final List<String> calls = new CopyOnWriteArrayList<>();

    @Test
    void testErrorOfOnCompleteRunsTheOtherOnCompletions() throws Exception {
        Exchange out = send("direct:start", () -> new AssertionError("Forced onComplete"));

        assertNull(out.getException());
        assertDone(List.of("failing:onComplete", "counting:onComplete"));
    }

    @Test
    void testErrorOfOnFailureRunsTheOtherOnCompletions() throws Exception {
        Exchange out = send("direct:fail", () -> new AssertionError("Forced onFailure"));

        assertInstanceOf(IllegalArgumentException.class, out.getException());
        assertDone(List.of("failing:onFailure", "counting:onFailure"));
    }

    @Test
    void testExceptionOfOnCompleteRunsTheOtherOnCompletions() throws Exception {
        // control: an exception was already logged and ignored
        Exchange out = send("direct:start", () -> new IllegalStateException("Forced onComplete"));

        assertNull(out.getException());
        assertDone(List.of("failing:onComplete", "counting:onComplete"));
    }

    @Test
    void testErrorOfRouteAwareCallbacksRunsTheOtherOnCompletions() throws Exception {
        Exchange out = template.asyncSend("direct:routeAware", e -> e.getMessage().setBody("Hello"))
                .get(10, TimeUnit.SECONDS);

        assertNull(out.getException());
        // the route aware callbacks of both on completions (the failing one first), when the exchange enters and leaves
        // route b and leaves the route that added them, and then their on completions
        assertDone(List.of("failing:onBeforeRoute:b", "counting:onBeforeRoute:b",
                "failing:onAfterRoute:b", "counting:onAfterRoute:b",
                "failing:onAfterRoute:routeAware", "counting:onAfterRoute:routeAware",
                "failing:onComplete", "counting:onComplete"));
    }

    private Exchange send(String uri, Supplier<Throwable> failure) throws Exception {
        // bounded, as a guard: before, the Error reached the caller
        return template.asyncSend(uri, e -> {
            e.getMessage().setBody("Hello");
            e.setProperty("failure", failure);
        }).get(10, TimeUnit.SECONDS);
    }

    private void assertDone(List<String> expected) {
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(expected, calls));
        assertEquals(0, context.getInflightRepository().size(), "The exchange must not stay inflight");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // the on completions run last added first: the failing one runs before the counting one
                from("direct:start").routeId("start")
                        .process(e -> addOnCompletions(e, false))
                        .to("mock:result");

                from("direct:fail").routeId("fail")
                        .process(e -> addOnCompletions(e, false))
                        .throwException(new IllegalArgumentException("Forced route"));

                from("direct:routeAware").routeId("routeAware")
                        .process(e -> {
                            e.setProperty("failure", (Supplier<Throwable>) () -> new AssertionError("Forced"));
                            addOnCompletions(e, true);
                        })
                        .to("direct:b");

                from("direct:b").routeId("b")
                        .to("mock:result");
            }
        };
    }

    private void addOnCompletions(Exchange exchange, boolean routeAware) {
        @SuppressWarnings("unchecked")
        Supplier<Throwable> failure = exchange.getProperty("failure", Supplier.class);
        exchange.getExchangeExtension().addOnCompletion(new Recording("counting", routeAware, null));
        exchange.getExchangeExtension().addOnCompletion(new Recording("failing", routeAware, failure));
    }

    private final class Recording extends SynchronizationAdapter implements SynchronizationRouteAware {
        private final String name;
        private final boolean routeAware;
        private final Supplier<Throwable> failure;

        Recording(String name, boolean routeAware, Supplier<Throwable> failure) {
            this.name = name;
            this.routeAware = routeAware;
            this.failure = failure;
        }

        @Override
        public void onComplete(Exchange exchange) {
            call("onComplete");
        }

        @Override
        public void onFailure(Exchange exchange) {
            call("onFailure");
        }

        @Override
        public void onBeforeRoute(Route route, Exchange exchange) {
            call("onBeforeRoute:" + route.getRouteId());
        }

        @Override
        public void onAfterRoute(Route route, Exchange exchange) {
            call("onAfterRoute:" + route.getRouteId());
        }

        @Override
        public SynchronizationRouteAware getRouteSynchronization() {
            return routeAware ? this : null;
        }

        private void call(String what) {
            calls.add(name + ":" + what);
            if (failure != null) {
                Throwable t = failure.get();
                if (t instanceof Error error) {
                    throw error;
                }
                throw (RuntimeException) t;
            }
        }
    }
}
