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
package org.apache.camel.jta;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultUnitOfWork;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A graceful shutdown lets in-flight exchanges complete: an exchange that finishes within the shutdown timeout must be
 * committed. Only a forced shutdown (timeout expired) must mark in-flight transacted exchanges for rollback.
 * <p>
 * DefaultShutdownStrategy calls {@code prepareShutdown(false, false)} on the route services before it waits for the
 * in-flight exchanges, and {@code prepareShutdown(false, true)} only once the timeout expired.
 */
class TransactionErrorHandlerGracefulShutdownTest {

    private CamelContext camelContext;
    private final AtomicInteger commits = new AtomicInteger();
    private final AtomicInteger rollbacks = new AtomicInteger();
    private final CountDownLatch processingStarted = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);

    private final JtaTransactionPolicy policy = new JtaTransactionPolicy() {
        @Override
        public void run(Runnable runnable) throws Throwable {
            try {
                runnable.run();
            } catch (Throwable t) {
                rollbacks.incrementAndGet();
                throw t;
            }
            commits.incrementAndGet();
        }
    };

    private final Processor blocking = exchange -> {
        processingStarted.countDown();
        assertTrue(release.await(20, TimeUnit.SECONDS), "test did not release the exchange");
    };

    @BeforeEach
    void setUp() {
        camelContext = new DefaultCamelContext();
        camelContext.getRegistry().bind("PROPAGATION_REQUIRED", policy);
        // the in-flight exchange completes well within the graceful shutdown timeout
        camelContext.getShutdownStrategy().setTimeout(20);
        camelContext.getShutdownStrategy().setTimeUnit(TimeUnit.SECONDS);
    }

    @AfterEach
    void tearDown() {
        release.countDown();
        if (camelContext != null) {
            camelContext.stop();
        }
    }

    @Test
    void inflightExchangeCompletingDuringGracefulShutdownIsCommitted() throws Exception {
        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("transactedRoute")
                        .transacted()
                        .process(blocking);
            }
        });
        camelContext.start();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Exchange> sent = executor.submit(
                    () -> camelContext.createProducerTemplate().send("direct:start", e -> e.getIn().setBody("order")));
            assertTrue(processingStarted.await(10, TimeUnit.SECONDS), "exchange should be in flight");

            TransactionErrorHandler teh = findTransactionErrorHandler(camelContext.getRoute("transactedRoute").navigate());
            assertNotNull(teh, "transacted route should use the JTA TransactionErrorHandler");

            // graceful shutdown: the strategy notifies the error handler, then waits for the in-flight exchange
            Future<?> stopped = executor.submit(() -> camelContext.stop());
            await().atMost(10, TimeUnit.SECONDS).until(() -> teh.preparingShutdown);

            // the exchange finishes inside the grace period (no timeout, no forced shutdown)
            release.countDown();
            Exchange done = sent.get(10, TimeUnit.SECONDS);
            stopped.get(10, TimeUnit.SECONDS);

            assertFalse(done.isRollbackOnly(),
                    "an exchange that completed during a graceful shutdown must not be marked rollback only");
            assertEquals(0, rollbacks.get(),
                    "an exchange that completed during a graceful shutdown must not be rolled back");
            assertEquals(1, commits.get(), "an exchange that completed during a graceful shutdown must be committed");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void gracefulPrepareShutdownDoesNotRollBack() throws Exception {
        TransactionErrorHandler teh = new TransactionErrorHandler(camelContext, blocking, policy, LoggingLevel.WARN);
        camelContext.start();
        teh.start();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Exchange exchange = camelContext.getEndpoint("direct:test").createExchange();
            exchange.getExchangeExtension().setUnitOfWork(new DefaultUnitOfWork(exchange));
            Future<?> processed = executor.submit(() -> {
                teh.process(exchange);
                return null;
            });
            assertTrue(processingStarted.await(10, TimeUnit.SECONDS), "exchange should be in flight");

            // what DefaultShutdownStrategy does before it waits for in-flight exchanges
            teh.prepareShutdown(false, false);
            release.countDown();
            processed.get(10, TimeUnit.SECONDS);

            assertFalse(exchange.isRollbackOnly(), "graceful shutdown must not mark the exchange rollback only");
            assertEquals(1, commits.get(), "graceful shutdown must let the transaction commit");
            assertEquals(0, rollbacks.get(), "graceful shutdown must not roll back the transaction");
        } finally {
            executor.shutdownNow();
            teh.stop();
        }
    }

    private static TransactionErrorHandler findTransactionErrorHandler(Navigate<Processor> nav) {
        if (nav == null || !nav.hasNext()) {
            return null;
        }
        List<Processor> next = nav.next();
        if (next == null) {
            return null;
        }
        for (Processor p : next) {
            if (p instanceof TransactionErrorHandler teh) {
                return teh;
            }
            if (p instanceof Navigate<?>) {
                @SuppressWarnings("unchecked")
                TransactionErrorHandler found = findTransactionErrorHandler((Navigate<Processor>) p);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
