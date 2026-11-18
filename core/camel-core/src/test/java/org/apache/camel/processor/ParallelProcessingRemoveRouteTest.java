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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.RouteStoppingException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.ThreadPoolProfileBuilder;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A parallel Multicast, Split or Recipient List with its own thread pool, whose route is stopped after the shutdown
 * timeout and then removed while a part is still queued in that pool, must complete (fail) the exchange, not leave it
 * waiting for the part that the shut down pool never runs.
 */
class ParallelProcessingRemoveRouteTest extends ContextTestSupport {

    private final CountDownLatch latch = new CountDownLatch(1);
    private final CountDownLatch blocked = new CountDownLatch(1);

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        // one thread in the thread pool of the EIP, so the second part waits in its queue
        context.getExecutorServiceManager()
                .registerThreadPoolProfile(new ThreadPoolProfileBuilder("one").poolSize(1).maxPoolSize(1).build());
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:block").routeId("block").process(e -> {
                    blocked.countDown();
                    latch.await(20, TimeUnit.SECONDS);
                });
            }
        });
    }

    @Test
    void testMulticastRemoveRoute() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("parallel")
                        .multicast().parallelProcessing().executorService("one")
                        .to("direct:block", "mock:b");
            }
        });
        context.start();
        assertCompletedOnRemove();
    }

    @Test
    void testSplitRemoveRoute() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("parallel")
                        .split(body()).parallelProcessing().executorService("one")
                        .toD("${body}");
            }
        });
        context.start();
        assertCompletedOnRemove();
    }

    @Test
    void testRecipientListRemoveRoute() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("parallel")
                        .recipientList(body()).parallelProcessing().executorService("one");
            }
        });
        context.start();
        assertCompletedOnRemove();
    }

    @Test
    void testRecipientListRemoveRouteCompletesBeforeTheStrategyIsShutDown() throws Exception {
        RecordingStrategy strategy = new RecordingStrategy();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("parallel")
                        .recipientList(body()).parallelProcessing().executorService("one").aggregationStrategy(strategy);
            }
        });
        context.start();
        assertCompletedOnRemove();

        assertTrue(strategy.completed, "the failed exchange did not go through onCompletion of the strategy");
        assertFalse(strategy.shutDownAtCompletion, "onCompletion ran on a strategy that was shut down already");
    }

    @Test
    void testMulticastReachedAfterTheRouteIsRemoved() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                // the exchange waits before the Multicast, which then gets it after its thread pool is shut down. No
                // error handler: the one of the route would already fail the exchange, as its route is stopping
                errorHandler(noErrorHandler());
                from("direct:start").routeId("parallel")
                        .to("direct:block")
                        .multicast().parallelProcessing().executorService("one")
                        .to("mock:a", "mock:b");
            }
        });
        context.start();

        CompletableFuture<Exchange> future = template.asyncSend("direct:start", e -> e.getMessage().setBody("A"));
        assertTrue(blocked.await(5, TimeUnit.SECONDS));
        try {
            context.getRouteController().stopRoute("parallel", 100, TimeUnit.MILLISECONDS);
            context.removeRoute("parallel");
        } finally {
            latch.countDown();
        }

        // the thread pool drops the task without an exception (CallerRuns policy), so the Multicast fails the exchange
        Exchange out = future.get(10, TimeUnit.SECONDS);
        assertInstanceOf(RouteStoppingException.class, out.getException());
        await().atMost(5, TimeUnit.SECONDS).until(() -> context.getInflightRepository().size() == 0);
    }

    private void assertCompletedOnRemove() throws Exception {
        CompletableFuture<Exchange> future = template.asyncSend("direct:start",
                e -> e.getMessage().setBody(List.of("direct:block", "mock:b")));
        assertTrue(blocked.await(5, TimeUnit.SECONDS));

        try {
            // the shutdown timeout runs out, the route is stopped by force, then removed
            context.getRouteController().stopRoute("parallel", 100, TimeUnit.MILLISECONDS);
            context.removeRoute("parallel");

            Exchange out = future.get(10, TimeUnit.SECONDS);
            // the shutdown of the EIP fails the exchange. The first part, which the shutdown of the thread pool
            // interrupts, fails as well, but it cannot complete the exchange, as the second part never runs
            assertInstanceOf(RouteStoppingException.class, out.getException());
        } finally {
            latch.countDown();
        }
        await().atMost(5, TimeUnit.SECONDS).until(() -> context.getInflightRepository().size() == 0);
    }

    /**
     * Records whether it was shut down when the exchange completed.
     */
    private static final class RecordingStrategy extends ServiceSupport implements AggregationStrategy {
        private volatile boolean completed;
        private volatile boolean shutDownAtCompletion;

        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            return newExchange != null ? newExchange : oldExchange;
        }

        @Override
        public void onCompletion(Exchange exchange, Exchange inputExchange) {
            shutDownAtCompletion = isShutdown();
            completed = true;
        }
    }
}
