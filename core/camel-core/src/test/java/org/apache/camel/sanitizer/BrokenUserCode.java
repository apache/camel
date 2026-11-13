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
package org.apache.camel.sanitizer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.SynchronizationAdapter;

/**
 * Processors and strategies of a user that break the contract of AsyncProcessor, for the tests of the sanitizer.
 */
final class BrokenUserCode {

    private BrokenUserCode() {
    }

    /**
     * Completes from a timeout thread, and again from a reply thread that comes late, as the PriceLookup of the design.
     * A plain AsyncProcessor (not a Service), so Camel puts a delegate around it.
     */
    static final class CompletesTwice implements AsyncProcessor {

        final CountDownLatch secondDone = new CountDownLatch(1);

        @Override
        public boolean process(Exchange exchange, AsyncCallback callback) {
            CountDownLatch timedOut = new CountDownLatch(1);
            new Thread(() -> {
                callback.done(false);
                timedOut.countDown();
            }, "user-timeout").start();
            new Thread(() -> {
                try {
                    if (timedOut.await(10, TimeUnit.SECONDS)) {
                        callback.done(false);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    secondDone.countDown();
                }
            }, "user-reply").start();
            return false;
        }

        @Override
        public void process(Exchange exchange) {
            throw new UnsupportedOperationException("async only");
        }

        @Override
        public CompletableFuture<Exchange> processAsync(Exchange exchange) {
            throw new UnsupportedOperationException("async only");
        }
    }

    /**
     * Calls its callback in process, and keeps it: {@link #completeAgain()} calls it a second time, whenever a test
     * wants.
     */
    static final class CompletesAgainLater extends AsyncProcessorSupport {

        private volatile AsyncCallback kept;

        @Override
        public boolean process(Exchange exchange, AsyncCallback callback) {
            kept = callback;
            callback.done(true);
            return true;
        }

        void completeAgain() {
            try {
                kept.done(false);
            } catch (RuntimeException e) {
                // the exchange is continued again in a context that may be stopped; only the report matters here
            }
        }
    }

    /**
     * Returns false and never calls the callback: the exchange waits for it forever (still pending).
     */
    static final class NeverCallsBack extends AsyncProcessorSupport {

        @Override
        public boolean process(Exchange exchange, AsyncCallback callback) {
            return false;
        }
    }

    /**
     * Throws from process instead of setting the exception and calling the callback.
     */
    static final class ThrowsInProcess extends AsyncProcessorSupport {

        @Override
        public boolean process(Exchange exchange, AsyncCallback callback) {
            throw new IllegalStateException("Forced by ThrowsInProcess");
        }
    }

    /**
     * Calls the callback, then throws.
     */
    static final class ThrowsAfterCallback extends AsyncProcessorSupport {

        @Override
        public boolean process(Exchange exchange, AsyncCallback callback) {
            callback.done(true);
            throw new IllegalStateException("Forced by ThrowsAfterCallback");
        }
    }

    /**
     * Hands the callback to a thread that calls it when released, then throws: the callback comes after the throw.
     */
    static final class ThrowsThenCallsBack extends AsyncProcessorSupport {

        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch calledBack = new CountDownLatch(1);

        @Override
        public boolean process(Exchange exchange, AsyncCallback callback) {
            new Thread(() -> {
                try {
                    if (release.await(10, TimeUnit.SECONDS)) {
                        callback.done(false);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    calledBack.countDown();
                }
            }, "user-late-callback").start();
            throw new IllegalStateException("Forced by ThrowsThenCallsBack");
        }
    }

    /**
     * The endpoint of a custom component whose producer calls its callback twice.
     */
    static final class TwiceEndpoint extends DefaultEndpoint {

        TwiceEndpoint(String uri, CamelContext camelContext) {
            super(uri, null);
            setCamelContext(camelContext);
        }

        @Override
        public Producer createProducer() {
            return new DefaultAsyncProducer(this) {
                @Override
                public boolean process(Exchange exchange, AsyncCallback callback) {
                    callback.done(true);
                    callback.done(true);
                    return true;
                }
            };
        }

        @Override
        public Consumer createConsumer(Processor processor) {
            throw new UnsupportedOperationException("producer only");
        }
    }

    /**
     * A plain Processor (not an AsyncProcessor) that throws an Error, as an assertion of a test in a processor does.
     * Camel's adapter around it sets the Error on the exchange and calls the callback, so it breaks no rule.
     */
    static final class ThrowsError implements Processor {

        @Override
        public void process(Exchange exchange) {
            throw new StrategyBug("Forced by ThrowsError");
        }
    }

    /**
     * An error a strategy or a processor throws, which is not an Exception.
     */
    static final class StrategyBug extends Error {
        private static final long serialVersionUID = 1L;

        StrategyBug(String message) {
            super(message);
        }
    }

    /**
     * An aggregation strategy that throws an Error (as an assertion in a strategy does), which the EIPs do not catch.
     */
    static final class ErrorStrategy implements AggregationStrategy {

        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (oldExchange != null) {
                throw new StrategyBug("Forced by ErrorStrategy");
            }
            return newExchange;
        }
    }

    /**
     * An aggregation strategy that throws a RuntimeException, which the EIPs catch and set on the exchange.
     */
    static final class RuntimeExceptionStrategy implements AggregationStrategy {

        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (oldExchange != null) {
                throw new IllegalArgumentException("Forced by RuntimeExceptionStrategy");
            }
            return newExchange;
        }
    }

    /**
     * The endpoint of a custom component whose consumer commits its message with an on completion, and creates and
     * dones the unit of work of its exchanges itself: it calls done after the processing and again in finally, so the
     * commit runs twice.
     */
    static final class DoneTwiceEndpoint extends DefaultEndpoint {

        volatile DoneTwiceConsumer consumer;

        DoneTwiceEndpoint(String uri, CamelContext camelContext) {
            super(uri, null);
            setCamelContext(camelContext);
        }

        @Override
        public Producer createProducer() {
            throw new UnsupportedOperationException("consumer only");
        }

        @Override
        public Consumer createConsumer(Processor processor) {
            consumer = new DoneTwiceConsumer(this, processor);
            return consumer;
        }
    }

    static final class DoneTwiceConsumer extends DefaultConsumer {

        // how often the commit of a message ran
        final AtomicInteger commits = new AtomicInteger();

        DoneTwiceConsumer(DoneTwiceEndpoint endpoint, Processor processor) {
            super(endpoint, processor);
        }

        /**
         * Receives a message from the external system, routes it, and commits it with an on completion.
         */
        void receive(String body) throws Exception {
            Exchange exchange = createExchange(false);
            exchange.getMessage().setBody(body);
            exchange.getExchangeExtension().addOnCompletion(new MessageCommit());
            UnitOfWork uow = createUoW(exchange);
            try {
                getProcessor().process(exchange);
                uow.done(exchange);
            } finally {
                // the bug: the unit of work is done again when the exchange is released
                uow.done(exchange);
                releaseExchange(exchange, false);
            }
        }

        final class MessageCommit extends SynchronizationAdapter {

            @Override
            public void onDone(Exchange exchange) {
                commits.incrementAndGet();
            }
        }
    }
}
