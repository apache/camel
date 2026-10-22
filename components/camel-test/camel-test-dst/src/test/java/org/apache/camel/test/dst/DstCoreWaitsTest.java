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
package org.apache.camel.test.dst;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.processor.errorhandler.RedeliveryPolicy;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.task.ForegroundTask;
import org.apache.camel.support.task.Tasks;
import org.apache.camel.support.task.budget.Budgets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The waits of Camel core that sleep on the calling thread run in virtual time without any rewriting: core sleeps and
 * reads the clock through {@code TimeSource}, and the run installs its virtual clock there. Covered: the synchronous
 * redelivery (RedeliveryPolicy.sleep and the sleep loop of RedeliveryErrorHandler, which measures with StopWatch),
 * ForegroundTask with an iteration and a time budget, the synchronous delayer, and the wait loop of
 * DefaultShutdownStrategy.
 */
@DstInstrument("org.apache.camel.test.dst.sample")
class DstCoreWaitsTest {

    private static final long REAL_LIMIT = 5_000_000_000L;

    @Test
    void redeliveryPolicySleepIsVirtual() {
        Dst dst = new Dst(1);
        try {
            long[] woke = new long[1];
            dst.executor("ex", 1).execute(() -> {
                try {
                    new RedeliveryPolicy().sleep(2_500);
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
                woke[0] = dst.now();
            });
            long start = System.nanoTime();
            dst.runUntilQuiet();
            assertEquals(2_500, woke[0]);
            assertTrue(System.nanoTime() - start < REAL_LIMIT);
        } finally {
            dst.close();
        }
    }

    @Test
    void synchronousRedeliveryWaitsInVirtualTime() throws Exception {
        Dst dst = new Dst(1);
        DefaultCamelContext context = DstCamel.newContext(dst);
        try {
            List<Long> attempts = new ArrayList<>();
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    errorHandler(defaultErrorHandler().maximumRedeliveries(2).redeliveryDelay(3_000));
                    from("direct:in").routeId("r").process(e -> {
                        attempts.add(dst.now());
                        if (attempts.size() < 3) {
                            throw new IllegalStateException("attempt " + attempts.size() + " fails");
                        }
                    });
                }
            });
            context.start();
            Exchange[] done = new Exchange[1];
            dst.executor("client", 1).execute(() -> send(context, "r", done));
            long start = System.nanoTime();
            dst.runUntilQuiet();
            assertEquals(List.of(0L, 3_000L, 6_000L), attempts);
            assertNull(done[0].getException());
            assertTraced(dst, "sleep at RedeliveryErrorHandler$");
            assertTrue(System.nanoTime() - start < REAL_LIMIT);
        } finally {
            dst.dropPending();
            context.stop();
            dst.close();
        }
    }

    @Test
    void foregroundTaskWaitsInVirtualTime() {
        Dst dst = new Dst(1);
        try {
            boolean[] ready = new boolean[1];
            boolean[] completed = new boolean[1];
            long[] finished = new long[1];
            DstExecutor ex = dst.executor("waiter", 1);
            dst.executor("other", 1).schedule("ready", 4_500, () -> ready[0] = true);
            ex.execute(() -> {
                ForegroundTask task = Tasks.foregroundTask().withName("wait for ready")
                        .withBudget(Budgets.iterationTimeBudget().withInterval(Duration.ofSeconds(1))
                                .withMaxDuration(Duration.ofSeconds(30)).withMaxIterations(100).build())
                        .build();
                completed[0] = task.run(null, () -> ready[0]);
                finished[0] = dst.now();
            });
            long start = System.nanoTime();
            dst.runUntilQuiet();
            assertTrue(completed[0]);
            assertEquals(5_000, finished[0]);
            assertTraced(dst, "sleep at ForegroundTask.run");
            assertTrue(System.nanoTime() - start < REAL_LIMIT);
        } finally {
            dst.close();
        }
    }

    @Test
    void gracefulShutdownWaitsForTheInflightExchangeInVirtualTime() throws Exception {
        Dst dst = new Dst(1);
        DefaultCamelContext context = DstCamel.newContext(dst);
        try {
            DstExecutor downstream = dst.executor("downstream", 1);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    // an asynchronous downstream call that answers after 3 s: the exchange is in flight meanwhile
                    from("direct:in").routeId("r").process(DstCamel.asyncStep(dst, downstream, 3_000, 3_000, e -> {
                    }));
                }
            });
            context.start();
            Exchange[] done = new Exchange[1];
            dst.executor("client", 1).execute(() -> send(context, "r", done));
            dst.runFor(500);
            assertEquals(1, context.getInflightRepository().size("r"));
            long start = System.nanoTime();
            // the shutdown task waits for the in-flight exchange with Thread.sleep: virtual now
            context.getRouteController().stopRoute("r");
            assertTrue(done[0] != null, "the in-flight exchange completed before the route stopped");
            assertTrue(dst.now() >= 3_000, "stopped at " + dst.now());
            assertFalse(context.getShutdownStrategy().isTimeoutOccurred());
            assertTraced(dst, "sleep at DefaultShutdownStrategy$ShutdownTask.run");
            assertTrue(System.nanoTime() - start < REAL_LIMIT);
        } finally {
            dst.dropPending();
            context.stop();
            dst.close();
        }
    }

    @Test
    void foregroundTaskGivesUpWhenItsTimeBudgetIsSpentInVirtualTime() {
        Dst dst = new Dst(1);
        try {
            boolean[] completed = { true };
            long[] finished = new long[1];
            ForegroundTask[] task = new ForegroundTask[1];
            dst.executor("waiter", 1).execute(() -> {
                // the time budget (TimeBoundedBudget) ends the task, long before the iteration budget would
                task[0] = Tasks.foregroundTask().withName("never ready")
                        .withBudget(Budgets.iterationTimeBudget().withInterval(Duration.ofSeconds(1))
                                .withMaxDuration(Duration.ofSeconds(30)).withMaxIterations(1_000).build())
                        .build();
                completed[0] = task[0].run(null, () -> false);
                finished[0] = dst.now();
            });
            long start = System.nanoTime();
            dst.runUntilQuiet();
            assertFalse(completed[0]);
            assertEquals(30_000, finished[0]);
            assertEquals(Duration.ofSeconds(30), task[0].elapsed());
            assertEquals(30, task[0].iteration());
            assertTrue(System.nanoTime() - start < REAL_LIMIT);
        } finally {
            dst.close();
        }
    }

    @Test
    void synchronousDelayWaitsInVirtualTime() throws Exception {
        Dst dst = new Dst(1);
        DefaultCamelContext context = DstCamel.newContext(dst);
        try {
            long[] reached = { -1 };
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:in").routeId("r").delay(4_000).syncDelayed().process(e -> reached[0] = dst.now());
                }
            });
            context.start();
            Exchange[] done = new Exchange[1];
            dst.executor("client", 1).schedule("send", 1_000, () -> send(context, "r", done));
            long start = System.nanoTime();
            dst.runUntilQuiet();
            assertEquals(5_000, reached[0]);
            assertNull(done[0].getException());
            assertTraced(dst, "sleep at DelayProcessorSupport.sleep");
            assertTrue(System.nanoTime() - start < REAL_LIMIT);
        } finally {
            dst.dropPending();
            context.stop();
            dst.close();
        }
    }

    private static void assertTraced(Dst dst, String text) {
        assertTrue(dst.traceLines().stream().anyMatch(l -> l.contains(text)), "no trace line contains: " + text);
    }

    /** Sends an exchange to the route's consumer without waiting for it (no blocking wait on the loop thread). */
    private static void send(DefaultCamelContext context, String routeId, Exchange[] done) {
        DefaultConsumer consumer = (DefaultConsumer) context.getRoute(routeId).getConsumer();
        Exchange exchange = consumer.createExchange(false);
        exchange.getMessage().setBody("hello");
        consumer.getAsyncProcessor().process(exchange, doneSync -> done[0] = exchange);
    }
}
