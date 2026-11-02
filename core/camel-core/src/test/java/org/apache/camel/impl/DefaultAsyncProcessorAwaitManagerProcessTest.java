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
package org.apache.camel.impl;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.engine.DefaultAsyncProcessorAwaitManager;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The callback can fire before the processor returns, which is the common case, or at any later time from another
 * thread. In all cases process must return, and only after the callback fired.
 */
public class DefaultAsyncProcessorAwaitManagerProcessTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final DefaultAsyncProcessorAwaitManager manager = new DefaultAsyncProcessorAwaitManager();
    private final ExecutorService otherThread = Executors.newSingleThreadExecutor();
    private CamelContext context;

    @BeforeEach
    public void startContext() {
        context = new DefaultCamelContext();
        context.start();
    }

    @AfterEach
    public void stopContext() {
        otherThread.shutdownNow();
        context.stop();
    }

    @Test
    public void testCallbackFiredBeforeProcessorReturnsDoesNotBlock() {
        assertTimeoutPreemptively(TIMEOUT, () -> process((exchange, callback) -> callback.done(true)));

        assertEquals(0, manager.size());
    }

    @Test
    public void testCallbackFiredTwiceDoesNotFail() {
        assertTimeoutPreemptively(TIMEOUT, () -> process((exchange, callback) -> {
            callback.done(true);
            callback.done(true);
        }));

        assertEquals(0, manager.size());
    }

    @Test
    public void testBlocksUntilCallbackFiredFromAnotherThread() {
        AtomicBoolean fired = new AtomicBoolean();

        assertTimeoutPreemptively(TIMEOUT, () -> process((exchange, callback) -> otherThread.execute(() -> {
            // the waiting thread is listed as blocked, which proves process did not return early
            await().atMost(10, TimeUnit.SECONDS).until(() -> manager.size() == 1);
            fired.set(true);
            callback.done(false);
        })));

        assertTrue(fired.get(), "process must not return before the callback fired");
        assertEquals(0, manager.size());
    }

    @Test
    public void testCallbackRacingWithProcessorReturnIsNotLost() {
        // a callback lost in the moment the latch is created would block process forever
        assertTimeoutPreemptively(TIMEOUT, () -> {
            for (int i = 0; i < 20_000; i++) {
                CountDownLatch returning = new CountDownLatch(1);
                process((exchange, callback) -> {
                    otherThread.execute(() -> {
                        try {
                            returning.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        callback.done(false);
                    });
                    returning.countDown();
                });
            }
        });
    }

    private void process(BiConsumer<Exchange, AsyncCallback> body) {
        manager.process(new AsyncProcessorSupport() {
            @Override
            public boolean process(Exchange exchange, AsyncCallback callback) {
                body.accept(exchange, callback);
                return false;
            }
        }, new DefaultExchange(context));
    }
}
