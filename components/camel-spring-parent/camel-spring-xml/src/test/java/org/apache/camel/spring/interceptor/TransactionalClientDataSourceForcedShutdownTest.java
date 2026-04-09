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
package org.apache.camel.spring.interceptor;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.Service;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spring.SpringRouteBuilder;
import org.apache.camel.spring.spi.SpringTransactionPolicy;
import org.apache.camel.spring.spi.TransactionErrorHandler;
import org.apache.camel.support.service.ServiceHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test that verifies in-flight transacted exchanges are rolled back when a forced shutdown occurs.
 */
public class TransactionalClientDataSourceForcedShutdownTest extends TransactionClientDataSourceSupport {

    private final CountDownLatch firstInsertDone = new CountDownLatch(1);
    private final CountDownLatch releaseLatch = new CountDownLatch(1);

    @Test
    public void testForcedShutdownRollsBackInFlightTransaction() throws Exception {
        // verify initial state: 1 book from init.sql
        int initialCount = jdbc.queryForObject("select count(*) from books", Integer.class);
        assertEquals(1, initialCount, "Initial number of books");

        // send message asynchronously since the route will block
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> {
                template.sendBody("direct:forceShutdown", "Hello World");
                return null;
            });

            // wait for the first insert to complete
            firstInsertDone.await(10, TimeUnit.SECONDS);

            // find the TransactionErrorHandler in the route's services and call prepareShutdown
            Route route = context.getRoute("forceShutdownRoute");
            TransactionErrorHandler teh = findTransactionErrorHandler(route);
            if (teh != null) {
                teh.prepareShutdown(false, true);
            }

            // release the blocking processor so the exchange can complete
            releaseLatch.countDown();

            // wait for the exchange to finish processing
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS),
                    "Exchange processing should have completed");

            // verify that the transaction was rolled back
            // before the fix: count = 3 (original + 2 inserts committed)
            // after the fix: count = 1 (both inserts rolled back)
            int count = jdbc.queryForObject("select count(*) from books", Integer.class);
            assertEquals(1, count, "Number of books after forced shutdown - transaction should have been rolled back");
        } finally {
            releaseLatch.countDown(); // ensure we don't hang if test fails
            executor.shutdownNow();
        }
    }

    private TransactionErrorHandler findTransactionErrorHandler(Route route) {
        Processor processor = route.getProcessor();
        if (processor instanceof Service service) {
            Set<Service> children = ServiceHelper.getChildServices(service, true);
            for (Service child : children) {
                if (child instanceof TransactionErrorHandler teh) {
                    return teh;
                }
            }
        }
        return null;
    }

    @Override
    @SuppressWarnings("deprecation")
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new SpringRouteBuilder() {
            public void configure() throws Exception {
                SpringTransactionPolicy required = lookup("PROPAGATION_REQUIRED", SpringTransactionPolicy.class);
                errorHandler(transactionErrorHandler(required));

                from("direct:forceShutdown").routeId("forceShutdownRoute")
                        .policy(required)
                        .setBody(constant("Tiger in Action")).bean("bookService")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                // signal that the first insert is done
                                firstInsertDone.countDown();
                                // block until released
                                releaseLatch.await(30, TimeUnit.SECONDS);
                            }
                        })
                        .setBody(constant("Elephant in Action")).bean("bookService");
            }
        };
    }
}
