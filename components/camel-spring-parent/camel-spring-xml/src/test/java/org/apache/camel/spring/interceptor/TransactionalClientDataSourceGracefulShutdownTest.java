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
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.Service;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.ShutdownPrepared;
import org.apache.camel.spring.spi.TransactionErrorHandler;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A graceful shutdown lets in-flight exchanges complete: a transacted exchange that finishes within the shutdown
 * timeout must be committed. Only a forced shutdown (timeout expired) marks in-flight transacted exchanges for
 * rollback, see {@link TransactionalClientDataSourceForcedShutdownTest}.
 * <p>
 * DefaultShutdownStrategy calls {@code prepareShutdown(false, false)} on the route services before it waits for the
 * in-flight exchanges, and {@code prepareShutdown(false, true)} only once the timeout expired.
 */
class TransactionalClientDataSourceGracefulShutdownTest extends TransactionClientDataSourceSupport {

    private final CountDownLatch inFlight = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);

    @Test
    void gracefulPrepareShutdownCommitsInFlightTransaction() throws Exception {
        assertEquals(1, countBooks(), "Initial number of books");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Exchange> sent = executor.submit(() -> template.send("direct:graceful", e -> e.getIn().setBody("x")));
            assertTrue(inFlight.await(10, TimeUnit.SECONDS), "exchange should be in flight");

            TransactionErrorHandler teh = findTransactionErrorHandler(context.getRoute("gracefulRoute"));
            assertNotNull(teh, "transacted route should use the Spring TransactionErrorHandler");

            // what DefaultShutdownStrategy does before it waits for the in-flight exchanges
            teh.prepareShutdown(false, false);
            release.countDown();
            Exchange done = sent.get(10, TimeUnit.SECONDS);

            assertNull(done.getException());
            assertFalse(done.isRollbackOnly(), "graceful shutdown must not mark the exchange rollback only");
            assertEquals(2, countBooks(), "graceful shutdown must let the transaction commit");
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void inFlightTransactionCompletingDuringContextStopIsCommitted() throws Exception {
        // the in-flight exchange completes well within the graceful shutdown timeout
        context.getShutdownStrategy().setTimeout(30);
        assertEquals(1, countBooks(), "Initial number of books");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Exchange> sent = executor.submit(() -> template.send("direct:graceful", e -> e.getIn().setBody("x")));
            assertTrue(inFlight.await(10, TimeUnit.SECONDS), "exchange should be in flight");

            // the shutdown strategy releases the exchange from its graceful prepareShutdown call (see
            // ReleaseOnPrepareShutdown), then waits for it to complete
            context.stop();
            Exchange done = sent.get(10, TimeUnit.SECONDS);

            assertFalse(context.getShutdownStrategy().isTimeoutOccurred(), "shutdown should not have timed out");
            assertNull(done.getException());
            assertFalse(done.isRollbackOnly(),
                    "an exchange that completed during a graceful shutdown must not be marked rollback only");
            assertEquals(2, countBooks(), "an exchange that completed during a graceful shutdown must be committed");
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private int countBooks() {
        return jdbc.queryForObject("select count(*) from books", Integer.class);
    }

    private static TransactionErrorHandler findTransactionErrorHandler(Route route) {
        if (route.getProcessor() instanceof Service service) {
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
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:graceful").routeId("gracefulRoute")
                        .transacted()
                        .setBody(constant("Tiger in Action")).bean("bookService")
                        .process(exchange -> {
                            inFlight.countDown();
                            assertTrue(release.await(10, TimeUnit.SECONDS), "test did not release the exchange");
                        })
                        .process(new ReleaseOnPrepareShutdown(release));
            }
        };
    }

    /**
     * Releases the in-flight exchange when the shutdown strategy prepares the route for a graceful shutdown. It is
     * inside the transacted block, so the strategy prepares the TransactionErrorHandler before this service.
     */
    private static final class ReleaseOnPrepareShutdown extends ServiceSupport implements Processor, ShutdownPrepared {

        private final CountDownLatch release;

        private ReleaseOnPrepareShutdown(CountDownLatch release) {
            this.release = release;
        }

        @Override
        public void prepareShutdown(boolean suspendOnly, boolean forced) {
            if (!suspendOnly && !forced) {
                release.countDown();
            }
        }

        @Override
        public void process(Exchange exchange) {
            // noop
        }
    }
}
