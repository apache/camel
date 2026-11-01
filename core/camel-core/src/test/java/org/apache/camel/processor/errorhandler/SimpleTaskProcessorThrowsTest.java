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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.AsyncProducer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An {@link AsyncProcessor} that throws from {@code process(exchange, callback)}, instead of setting the exception on
 * the exchange, in a route whose error handler has no redelivery, no exception policies and no dead letter channel (so
 * it runs the step with its simple task): the exchange must complete with the thrown exception instead of hanging. Each
 * send is bounded, so a hang fails the test.
 */
class SimpleTaskProcessorThrowsTest extends ContextTestSupport {

    private final AtomicInteger calls = new AtomicInteger();

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    /**
     * Throws on the first call, and succeeds on the later calls.
     */
    private final class ThrowsFirst extends AsyncProcessorSupport {
        @Override
        public boolean process(Exchange exchange, AsyncCallback callback) {
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("thrown");
            }
            callback.done(true);
            return true;
        }
    }

    private void start(Processor processor) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").process(processor).to("mock:result");
            }
        });
        context.start();
    }

    private Exchange send() throws Exception {
        return send(false);
    }

    private Exchange send(boolean transacted) throws Exception {
        return asyncSend("direct:start", transacted).get(10, TimeUnit.SECONDS);
    }

    private CompletableFuture<Exchange> asyncSend(String uri, boolean transacted) {
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
    private Exchange sendOnThisThread() {
        return template.send("direct:start", e -> e.getMessage().setBody("Hello"));
    }

    /**
     * Counts the exchanges done: a second continuation of an exchange would complete its unit of work again.
     */
    private AtomicInteger countDoneExchanges() {
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

    private void assertFailedWithTheThrownException(Exchange out) {
        assertInstanceOf(IllegalStateException.class, out.getException());
        assertEquals("thrown", out.getException().getMessage());
        assertEquals(1, calls.get(), "calls");
        assertEquals(0, getMockEndpoint("mock:result").getReceivedCounter(), "mock:result");
    }

    @Test
    void defaultErrorHandler() throws Exception {
        start(new ThrowsFirst());
        assertFailedWithTheThrownException(send());
    }

    @Test
    void afterAnAsynchronousStep() throws Exception {
        // the step runs on the thread of the asynchronous delay, not on the caller's
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").delay(10).asyncDelayed().process(new ThrowsFirst()).to("mock:result");
            }
        });
        context.start();
        assertFailedWithTheThrownException(send());
    }

    @Test
    void transacted() throws Exception {
        // a transacted exchange runs on the reactive executor's queue
        start(new ThrowsFirst());
        assertFailedWithTheThrownException(send(true));
    }

    @Test
    void calledRouteFailsLikeAPlainProcessorThatThrows() throws Exception {
        // the called routes have no exception policy (simple task), their caller has one: the AsyncProcessor that
        // throws must fail the called route, and so its caller, exactly as a plain Processor that throws does
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:async").process(new ThrowsFirst()).to("mock:result");
                from("direct:plain").process(exchange -> {
                    throw new IllegalStateException("thrown");
                }).to("mock:result");
            }
        });
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                onException(IllegalStateException.class).handled(true).to("mock:handled");
                from("direct:callAsync").to("direct:async").to("mock:after");
                from("direct:callPlain").to("direct:plain").to("mock:after");
            }
        });
        context.start();

        Exchange plain = asyncSend("direct:callPlain", false).get(10, TimeUnit.SECONDS);
        int handledPlain = getMockEndpoint("mock:handled").getReceivedCounter();
        Exchange async = asyncSend("direct:callAsync", false).get(10, TimeUnit.SECONDS);
        int handledAsync = getMockEndpoint("mock:handled").getReceivedCounter() - handledPlain;

        assertEquals(String.valueOf(plain.getException()), String.valueOf(async.getException()), "exception");
        assertEquals(handledPlain, handledAsync, "mock:handled");
        // the error handler of the called route gives the failure back as exhausted, so the caller does not handle it
        assertInstanceOf(IllegalStateException.class, async.getException());
        assertEquals(0, handledAsync, "mock:handled");
        assertEquals(0, getMockEndpoint("mock:after").getReceivedCounter(), "mock:after");
        assertEquals(0, getMockEndpoint("mock:result").getReceivedCounter(), "mock:result");
        assertEquals(1, calls.get(), "calls");
    }

    @Test
    void nextExchangeIsProcessed() throws Exception {
        start(new ThrowsFirst());
        assertFailedWithTheThrownException(send());

        Exchange out = send();
        assertNull(out.getException());
        assertEquals(1, getMockEndpoint("mock:result").getReceivedCounter(), "mock:result");
        assertEquals(2, calls.get(), "calls");
    }

    @Test
    void controlPlainProcessorThrows() throws Exception {
        // control, as before: the converter of a plain Processor to an AsyncProcessor catches the exception and sets it
        // on the exchange
        start(exchange -> {
            calls.incrementAndGet();
            throw new IllegalStateException("thrown");
        });
        assertFailedWithTheThrownException(send());
    }

    @Test
    void controlCallbackCalledBeforeTheProcessorThrows() throws Exception {
        // control, as before: the processor calls the callback and then throws. The callback continued the exchange, so
        // the exception is left to the reactive executor (which logs it), and the exchange completes once
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);
        start(new AsyncProcessorSupport() {
            @Override
            public boolean process(Exchange exchange, AsyncCallback callback) {
                calls.incrementAndGet();
                callback.done(true);
                throw new IllegalStateException("after the callback");
            }
        });
        AtomicInteger done = countDoneExchanges();

        Exchange out = sendOnThisThread();
        assertNull(out.getException());
        result.assertIsSatisfied();
        assertEquals(1, result.getReceivedCounter(), "mock:result");
        assertEquals(1, done.get(), "the exchange was done once");
        assertEquals(1, calls.get(), "calls");
    }

    @Test
    void controlCallbackQueuedBeforeTheProcessorThrows() throws Exception {
        // control, as before: the processor queues the call of the callback on the reactive executor and then throws.
        // The error handler takes over the thrown failure through the reactive executor, after the work already queued
        // there, so the queued callback comes first and continues the exchange
        start(new AsyncProcessorSupport() {
            @Override
            public boolean process(Exchange exchange, AsyncCallback callback) {
                calls.incrementAndGet();
                exchange.getContext().getCamelContextExtension().getReactiveExecutor().schedule(() -> {
                    exchange.getMessage().setBody("queued");
                    callback.done(false);
                });
                throw new IllegalStateException("after the callback was queued");
            }
        });
        AtomicInteger done = countDoneExchanges();

        Exchange out = sendOnThisThread();
        assertNull(out.getException());
        assertEquals("queued", out.getMessage().getBody());
        assertEquals(1, getMockEndpoint("mock:result").getReceivedCounter(), "mock:result");
        assertEquals(1, done.get(), "the exchange was done once");
        assertEquals(1, calls.get(), "calls");
    }

    @Test
    void controlCallbackOfARouteBeforeTheProcessorThrows() throws Exception {
        // control, as before: the processor hands the exchange and the callback to a direct route (with the default
        // error handler) and then throws. Each step of that route runs ahead of the takeover queued on the reactive
        // executor, and the route schedules the callback (this task) there, so the route continues the exchange: the
        // exchange completes with the reply of that route, once. (A route with noErrorHandler() whose later steps are
        // queued only after the takeover loses to it, as any callback called by work queued after the takeover.)
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:inner").setBody(constant("inner")).setHeader("inner", constant("yes"));
            }
        });
        AtomicReference<AsyncProducer> producer = new AtomicReference<>();
        start(new AsyncProcessorSupport() {
            @Override
            public boolean process(Exchange exchange, AsyncCallback callback) {
                calls.incrementAndGet();
                producer.get().process(exchange, callback);
                throw new IllegalStateException("after the callback was handed to a route");
            }
        });
        AsyncProducer direct = context.getEndpoint("direct:inner").createAsyncProducer();
        direct.start();
        producer.set(direct);
        AtomicInteger done = countDoneExchanges();
        try {
            Exchange out = sendOnThisThread();
            assertNull(out.getException());
            assertEquals("inner", out.getMessage().getBody());
            assertEquals("yes", out.getMessage().getHeader("inner"));
            assertEquals(1, getMockEndpoint("mock:result").getReceivedCounter(), "mock:result");
            assertEquals(1, done.get(), "the exchange was done once");
            assertEquals(1, calls.get(), "calls");
        } finally {
            direct.stop();
        }
    }

    @Test
    void callbackCalledAfterTheProcessorThrewIsIgnored() throws Exception {
        // the processor hands the callback to another thread and then throws. The error handler cannot tell this from a
        // callback that never comes, so it takes over the thrown failure (before, the exchange waited for the callback),
        // and the late callback must not continue the exchange a second time
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Thread> late = new AtomicReference<>();
        AtomicReference<Throwable> lateFailure = new AtomicReference<>();
        AtomicInteger done = countDoneExchanges();
        start(new AsyncProcessorSupport() {
            @Override
            public boolean process(Exchange exchange, AsyncCallback callback) {
                calls.incrementAndGet();
                Thread thread = new Thread(() -> {
                    try {
                        if (release.await(10, TimeUnit.SECONDS)) {
                            callback.done(false);
                        }
                    } catch (Throwable t) {
                        lateFailure.set(t);
                    }
                }, "late-callback");
                late.set(thread);
                thread.start();
                throw new IllegalStateException("thrown");
            }
        });

        assertFailedWithTheThrownException(send());

        // now the processor calls the callback, after the exchange completed
        release.countDown();
        late.get().join(TimeUnit.SECONDS.toMillis(10));
        assertFalse(late.get().isAlive(), "the late callback thread ended");
        assertNull(lateFailure.get(), "the late callback failed");
        assertEquals(1, done.get(), "the exchange was done once");
        assertEquals(0, getMockEndpoint("mock:result").getReceivedCounter(), "mock:result");
    }

    @Test
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

    @Test
    void callbackAndThrowAtTheSameTimeContinueEachExchangeOnce() throws Exception {
        // the processor hands the callback to another thread, which calls it at once, and throws: the callback and the
        // takeover race, and whichever wins, each exchange is continued once (as a success or with the thrown failure)
        int count = 500;
        ExecutorService callbacks = Executors.newFixedThreadPool(2);
        try {
            start(new AsyncProcessorSupport() {
                @Override
                public boolean process(Exchange exchange, AsyncCallback callback) {
                    calls.incrementAndGet();
                    callbacks.execute(() -> callback.done(false));
                    throw new IllegalStateException("thrown");
                }
            });
            AtomicInteger done = countDoneExchanges();

            List<CompletableFuture<Exchange>> sends = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                sends.add(asyncSend("direct:start", false));
            }
            int succeeded = 0;
            for (CompletableFuture<Exchange> send : sends) {
                Exchange out = send.get(20, TimeUnit.SECONDS);
                if (out.getException() == null) {
                    succeeded++;
                } else {
                    assertEquals("thrown", out.getException().getMessage());
                }
            }
            await().atMost(10, TimeUnit.SECONDS).until(() -> done.get() >= count);
            // the callbacks that lost have run too
            callbacks.shutdown();
            assertTrue(callbacks.awaitTermination(10, TimeUnit.SECONDS), "the callbacks ran");

            assertEquals(count, done.get(), "each exchange was done once");
            assertEquals(succeeded, getMockEndpoint("mock:result").getReceivedCounter(), "mock:result");
            assertEquals(count, calls.get(), "calls");
        } finally {
            callbacks.shutdownNow();
        }
    }
}
