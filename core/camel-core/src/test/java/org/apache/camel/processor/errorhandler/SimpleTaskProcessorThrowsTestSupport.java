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
package org.apache.camel.processor.errorhandler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.sanitizer.Violation;
import org.apache.camel.sanitizer.junit.ExpectSanitizerViolation;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.ExchangeCompletedEvent;
import org.apache.camel.spi.CamelEvent.ExchangeFailedEvent;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.EventNotifierSupport;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Shared by {@link SimpleTaskProcessorThrowsTest} and {@link SimpleTaskProcessorThrowsPooledTest}: an
 * {@link AsyncProcessor} that throws from {@code process(exchange, callback)}, instead of setting the exception on the
 * exchange, in a route whose error handler has no redelivery, no exception policies and no dead letter channel (so it
 * runs the step with its simple task). The tests here depend on whether the simple tasks are pooled, so they run with
 * and without pooling. Each send is bounded, so a hang fails the test.
 */
abstract class SimpleTaskProcessorThrowsTestSupport extends ContextTestSupport {

    protected final AtomicInteger calls = new AtomicInteger();

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    protected void start(Processor processor) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").process(processor).to("mock:result");
            }
        });
        context.start();
    }

    protected Exchange send() throws Exception {
        return send(false);
    }

    protected Exchange send(boolean transacted) throws Exception {
        return asyncSend("direct:start", transacted).get(10, TimeUnit.SECONDS);
    }

    protected CompletableFuture<Exchange> asyncSend(String uri, boolean transacted) {
        // bounded by the caller: on a hang the future does not complete and get times out
        return template.asyncSend(uri, e -> {
            e.getMessage().setBody("Hello");
            e.getExchangeExtension().setTransacted(transacted);
        });
    }

    /**
     * Sends on this thread, which runs the reactive work of the exchange until none is left: when it returns, the
     * takeover of a thrown failure has run too.
     */
    protected Exchange sendOnThisThread() {
        return template.send("direct:start", e -> e.getMessage().setBody("Hello"));
    }

    /**
     * Counts the exchanges done: a second continuation of an exchange would complete its unit of work again.
     */
    protected AtomicInteger countDoneExchanges() {
        AtomicInteger done = new AtomicInteger();
        context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            @Override
            public void notify(CamelEvent event) {
                done.incrementAndGet();
            }

            @Override
            public boolean isEnabled(CamelEvent event) {
                return event instanceof ExchangeCompletedEvent || event instanceof ExchangeFailedEvent;
            }
        });
        return done;
    }

    protected void assertFailedWithTheThrownException(Exchange out) {
        assertInstanceOf(IllegalStateException.class, out.getException());
        assertEquals("thrown", out.getException().getMessage());
        assertEquals(1, calls.get(), "calls");
        assertEquals(0, getMockEndpoint("mock:result").getReceivedCounter(), "mock:result");
    }

    @Test
    @ExpectSanitizerViolation({ Violation.THREW_BEFORE_CALLBACK, Violation.THREW_AFTER_CALLBACK })
    @ExpectSanitizerViolation({ Violation.CALLBACK_AFTER_THROW, Violation.THREW_AFTER_CALLBACK })
    void callbackCalledAfterTheProcessorThrewDoesNotContinueTheNextExchange() throws Exception {
        // as above, but the late callback comes while the next exchange waits for the callback of its own call of the
        // processor: it must not continue that exchange (with pooled tasks, the next exchange may get a task again)
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch releaseSecond = new CountDownLatch(1);
        AtomicReference<Thread> first = new AtomicReference<>();
        AtomicReference<Thread> second = new AtomicReference<>();
        AtomicReference<Throwable> lateFailure = new AtomicReference<>();
        start(new AsyncProcessorSupport() {
            @Override
            public boolean process(Exchange exchange, AsyncCallback callback) {
                int call = calls.incrementAndGet();
                CountDownLatch release = call == 1 ? releaseFirst : releaseSecond;
                Thread thread = new Thread(() -> {
                    try {
                        if (release.await(10, TimeUnit.SECONDS)) {
                            callback.done(false);
                        }
                    } catch (Throwable t) {
                        lateFailure.set(t);
                    }
                }, "callback-" + call);
                (call == 1 ? first : second).set(thread);
                thread.start();
                if (call == 1) {
                    throw new IllegalStateException("thrown");
                }
                return false;
            }
        });

        assertFailedWithTheThrownException(send());

        CompletableFuture<Exchange> next = asyncSend("direct:start", false);
        await().atMost(10, TimeUnit.SECONDS).until(() -> second.get() != null);

        // the callback of the first call, while the second call waits for its own
        releaseFirst.countDown();
        first.get().join(TimeUnit.SECONDS.toMillis(10));
        assertFalse(first.get().isAlive(), "the late callback thread ended");
        assertFalse(next.isDone(), "the late callback of the first exchange continued the next exchange");
        assertEquals(0, getMockEndpoint("mock:result").getReceivedCounter(), "mock:result");

        releaseSecond.countDown();
        Exchange out = next.get(10, TimeUnit.SECONDS);
        assertNull(out.getException());
        assertEquals(1, getMockEndpoint("mock:result").getReceivedCounter(), "mock:result");
        second.get().join(TimeUnit.SECONDS.toMillis(10));
        assertNull(lateFailure.get(), "a callback failed");
        assertEquals(2, calls.get(), "calls");
    }

    @Test
    @ExpectSanitizerViolation(Violation.COMPLETED_TWICE)
    void callbackCalledTwiceContinuesTheExchangeOnce() throws Exception {
        // a processor that calls its callback twice (also a broken processor): the second call is ignored, also when the
        // first one released the task (pooled tasks are reset when they are released)
        AtomicReference<Throwable> secondCall = new AtomicReference<>();
        start(new AsyncProcessorSupport() {
            @Override
            public boolean process(Exchange exchange, AsyncCallback callback) {
                calls.incrementAndGet();
                callback.done(true);
                try {
                    callback.done(true);
                } catch (Throwable t) {
                    secondCall.set(t);
                }
                return true;
            }
        });
        AtomicInteger done = countDoneExchanges();

        Exchange out = sendOnThisThread();
        assertNull(out.getException());
        assertNull(secondCall.get(), "the second call of the callback failed");
        assertEquals(1, done.get(), "the exchange was done once");
        assertEquals(1, getMockEndpoint("mock:result").getReceivedCounter(), "mock:result");
    }
}
