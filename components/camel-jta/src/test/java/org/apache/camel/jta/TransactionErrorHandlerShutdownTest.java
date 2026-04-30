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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultUnitOfWork;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test that verifies in-flight transacted exchanges are rolled back when a forced shutdown occurs on the JTA
 * TransactionErrorHandler.
 */
public class TransactionErrorHandlerShutdownTest {

    private CamelContext camelContext;
    private final CountDownLatch processingStarted = new CountDownLatch(1);
    private final CountDownLatch releaseLatch = new CountDownLatch(1);
    private final AtomicBoolean rollbackTriggered = new AtomicBoolean(false);
    private TransactionErrorHandler transactionErrorHandler;

    @BeforeEach
    public void setUp() throws Exception {
        camelContext = new DefaultCamelContext();

        // Create a test JtaTransactionPolicy that tracks whether rollback was triggered
        JtaTransactionPolicy testPolicy = new JtaTransactionPolicy() {
            @Override
            public void run(Runnable runnable) throws Throwable {
                try {
                    runnable.run();
                } catch (Throwable t) {
                    rollbackTriggered.set(true);
                    throw t;
                }
            }
        };

        // Create the TransactionErrorHandler with a processor that blocks
        Processor blockingProcessor = new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                processingStarted.countDown();
                releaseLatch.await(30, TimeUnit.SECONDS);
            }
        };

        transactionErrorHandler = new TransactionErrorHandler(
                camelContext, blockingProcessor, testPolicy, LoggingLevel.WARN);

        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:test").routeId("testRoute")
                        .process(blockingProcessor);
            }
        });

        camelContext.start();
        transactionErrorHandler.start();
    }

    @AfterEach
    public void tearDown() throws Exception {
        releaseLatch.countDown(); // ensure we don't hang
        if (transactionErrorHandler != null) {
            transactionErrorHandler.stop();
        }
        if (camelContext != null) {
            camelContext.stop();
        }
    }

    @Test
    public void testForcedShutdownMarksExchangeForRollback() throws Exception {
        AtomicReference<Exchange> exchangeRef = new AtomicReference<>();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            // process an exchange through the TransactionErrorHandler in a separate thread
            executor.submit(() -> {
                Exchange exchange = camelContext.getEndpoint("direct:test").createExchange();
                exchange.getIn().setBody("test");
                // set up UnitOfWork so transacted tracking works
                DefaultUnitOfWork uow = new DefaultUnitOfWork(exchange);
                exchange.getExchangeExtension().setUnitOfWork(uow);
                exchangeRef.set(exchange);
                try {
                    transactionErrorHandler.process(exchange);
                } catch (Exception e) {
                    // expected - rollback may throw
                }
                return null;
            });

            // wait for the exchange to enter the blocking processor
            assertTrue(processingStarted.await(10, TimeUnit.SECONDS), "Exchange should have started processing");

            // simulate forced shutdown
            transactionErrorHandler.prepareShutdown(false, true);

            // release the blocking processor
            releaseLatch.countDown();

            // wait for processing to complete
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "Processing should have completed");

            // verify the transaction was rolled back
            // before the fix: rollbackTriggered = false (exchange completes normally, transaction commits)
            // after the fix: rollbackTriggered = true (exchange marked rollbackOnly, transaction rolls back)
            assertTrue(rollbackTriggered.get(),
                    "Transaction should have been rolled back due to forced shutdown");
        } finally {
            releaseLatch.countDown();
            executor.shutdown();
        }
    }
}
