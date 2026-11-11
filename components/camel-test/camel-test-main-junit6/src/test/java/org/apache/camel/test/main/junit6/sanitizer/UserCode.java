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
package org.apache.camel.test.main.junit6.sanitizer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;

/**
 * A processor and a strategy of a user that break the contract of AsyncProcessor.
 */
final class UserCode {

    private UserCode() {
    }

    /**
     * Completes from two threads.
     */
    static final class CompletesTwice implements AsyncProcessor {

        final CountDownLatch done = new CountDownLatch(2);

        @Override
        public boolean process(Exchange exchange, AsyncCallback callback) {
            Thread first = new Thread(() -> {
                callback.done(false);
                done.countDown();
            }, "user-first");
            first.start();
            new Thread(() -> {
                try {
                    first.join();
                    callback.done(false);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }, "user-second").start();
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
     * Throws from process.
     */
    static final class Throws implements AsyncProcessor {

        @Override
        public boolean process(Exchange exchange, AsyncCallback callback) {
            throw new IllegalStateException("Forced by Throws");
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
     * An aggregation strategy that fails with an assertion, which the aggregator does not catch.
     */
    static final class AssertingStrategy implements AggregationStrategy {

        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (oldExchange != null) {
                throw new AssertionError("Forced by AssertingStrategy");
            }
            return newExchange;
        }
    }
}
