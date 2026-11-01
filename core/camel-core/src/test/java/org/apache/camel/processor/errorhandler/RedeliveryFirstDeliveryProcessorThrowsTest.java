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
 * An {@link AsyncProcessor} that throws from {@code process(exchange, callback)} on the first delivery, instead of
 * setting the exception on the exchange, in a route whose error handler can redeliver, has exception policies or a dead
 * letter channel: the error handler must handle the thrown failure as one set on the exchange (redelivery, the dead
 * letter channel and onException apply, as for a redelivery that throws), and continue the exchange exactly once. Each
 * send is bounded, so a hang fails the test.
 */
class RedeliveryFirstDeliveryProcessorThrowsTest extends ContextTestSupport {

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

    /**
     * Throws on the first call, and fails the exchange as an AsyncProcessor should (the exception on the exchange) on
     * the later calls.
     */
    private final class ThrowsThenFails extends AsyncProcessorSupport {
        @Override
        public boolean process(Exchange exchange, AsyncCallback callback) {
            int call = calls.incrementAndGet();
            if (call == 1) {
                throw new IllegalStateException("thrown");
            }
            exchange.setException(new IllegalStateException("failed " + call));
            callback.done(true);
            return true;
        }
    }

    private void start(String errorHandler, Processor processor) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                switch (errorHandler) {
                    case "immediate" -> errorHandler(defaultErrorHandler().maximumRedeliveries(1).redeliveryDelay(0));
                    case "synchronousDelayed" ->
                        errorHandler(defaultErrorHandler().maximumRedeliveries(1).redeliveryDelay(10));
                    case "asynchronousDelayed" -> errorHandler(defaultErrorHandler().maximumRedeliveries(1)
                            .redeliveryDelay(10).asyncDelayedRedelivery());
                    case "deadLetterChannel" -> errorHandler(deadLetterChannel("mock:dead"));
                    case "deadLetterChannelRedelivery" ->
                        errorHandler(deadLetterChannel("mock:dead").maximumRedeliveries(1).redeliveryDelay(0));
                    case "deadLetterChannelRedeliveryOnExceptionWithout" -> {
                        errorHandler(deadLetterChannel("mock:dead").maximumRedeliveries(1).redeliveryDelay(0));
                        onException(IllegalStateException.class).maximumRedeliveries(0);
                    }
                    case "onException" -> onException(IllegalStateException.class).handled(true).to("mock:handled");
                    default -> throw new IllegalArgumentException(errorHandler);
                }
                from("direct:start").process(processor).to("mock:result");
            }
        });
        context.start();
    }

    private Exchange send() throws Exception {
        return send(false);
    }

    private Exchange send(boolean transacted) throws Exception {
        return asyncSend(transacted).get(10, TimeUnit.SECONDS);
    }

    private CompletableFuture<Exchange> asyncSend(boolean transacted) {
        // bounded by the caller: on a hang the future does not complete and get times out
        return template.asyncSend("direct:start", e -> {
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

    private void assertRedelivered(Exchange out) {
        assertNull(out.getException());
        assertEquals(2, calls.get(), "the processor was called for the delivery and the redelivery");
        assertEquals(1, out.getMessage().getHeader(Exchange.REDELIVERY_COUNTER, Integer.class), "redelivery counter");
        assertEquals(1, getMockEndpoint("mock:result").getReceivedCounter(), "mock:result");
    }

    private void assertDeadLetterChannelGotTheThrownFailure(Exchange out, MockEndpoint dead) throws Exception {
        assertNull(out.getException(), "the dead letter channel handled the failure");
        dead.assertIsSatisfied();
        assertEquals(1, dead.getReceivedCounter(), "mock:dead");
        Exception caught = dead.getReceivedExchanges().get(0).getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        assertInstanceOf(IllegalStateException.class, caught);
        assertEquals("thrown", caught.getMessage());
        assertEquals(0, getMockEndpoint("mock:result").getReceivedCounter(), "mock:result");
    }

    @Test
    void thrownFailureIsRedelivered() throws Exception {
        start("immediate", new ThrowsFirst());
        assertRedelivered(send());
    }

    @Test
    void thrownFailureIsRedeliveredAfterASynchronousDelay() throws Exception {
        start("synchronousDelayed", new ThrowsFirst());
        assertRedelivered(send());
    }

    @Test
    void thrownFailureIsRedeliveredAfterAnAsynchronousDelay() throws Exception {
        start("asynchronousDelayed", new ThrowsFirst());
        assertRedelivered(send());
    }

    @Test
    void transactedThrownFailureIsRedelivered() throws Exception {
        // a transacted exchange runs on the reactive executor's queue, and its redelivery is always synchronous
        start("synchronousDelayed", new ThrowsFirst());
        assertRedelivered(send(true));
    }

    @Test
    void exhaustedFailsWithTheFailureOfTheRedelivery() throws Exception {
        start("immediate", new ThrowsThenFails());
        Exchange out = send();
        assertInstanceOf(IllegalStateException.class, out.getException());
        assertEquals("failed 2", out.getException().getMessage());
        assertEquals(2, calls.get(), "the processor was called for the delivery and the redelivery");
        assertEquals(0, getMockEndpoint("mock:result").getReceivedCounter(), "mock:result");
    }

    @Test
    void throwsOnEveryAttempt() throws Exception {
        // the redelivery that throws is handled as since CAMEL-26430: the exchange fails with its exception
        start("immediate", new AsyncProcessorSupport() {
            @Override
            public boolean process(Exchange exchange, AsyncCallback callback) {
                throw new IllegalStateException("thrown " + calls.incrementAndGet());
            }
        });
        Exchange out = send();
        assertInstanceOf(IllegalStateException.class, out.getException());
        assertEquals("thrown 2", out.getException().getMessage());
        assertEquals(2, calls.get(), "the processor was called for the delivery and the redelivery");
        assertEquals(0, getMockEndpoint("mock:result").getReceivedCounter(), "mock:result");
    }

    @Test
    void deadLetterChannelGetsTheFailureOfTheRedelivery() throws Exception {
        MockEndpoint dead = getMockEndpoint("mock:dead");
        dead.expectedMessageCount(1);
        start("deadLetterChannelRedelivery", new ThrowsThenFails());
        Exchange out = send();
        assertNull(out.getException(), "the dead letter channel handled the failure");
        dead.assertIsSatisfied();
        Exception caught = dead.getReceivedExchanges().get(0).getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        assertEquals("failed 2", caught.getMessage());
        assertEquals(2, calls.get(), "the processor was called for the delivery and the redelivery");
        assertEquals(0, getMockEndpoint("mock:result").getReceivedCounter(), "mock:result");
    }

    @Test
    void notRedeliveredWithAnOnExceptionWithoutRedeliveries() throws Exception {
        // the way to not redeliver such a failure that the upgrade guide gives: the dead letter channel still gets it
        MockEndpoint dead = getMockEndpoint("mock:dead");
        dead.expectedMessageCount(1);
        start("deadLetterChannelRedeliveryOnExceptionWithout", new ThrowsFirst());
        assertDeadLetterChannelGotTheThrownFailure(send(), dead);
        assertEquals(1, calls.get(), "calls");
    }

    @Test
    void deadLetterChannelGetsTheThrownFailure() throws Exception {
        MockEndpoint dead = getMockEndpoint("mock:dead");
        dead.expectedMessageCount(1);
        start("deadLetterChannel", new ThrowsFirst());
        assertDeadLetterChannelGotTheThrownFailure(send(), dead);
        assertEquals(1, calls.get(), "calls");
    }

    @Test
    void onExceptionOfTheThrownFailureHandlesIt() throws Exception {
        start("onException", new ThrowsFirst());
        Exchange out = send();
        assertNull(out.getException(), "the onException handled the thrown failure");
        assertEquals(1, getMockEndpoint("mock:handled").getReceivedCounter(), "mock:handled");
        assertEquals(0, getMockEndpoint("mock:result").getReceivedCounter(), "mock:result");
        assertEquals(1, calls.get(), "calls");
    }

    @Test
    void nextExchangeIsProcessed() throws Exception {
        MockEndpoint dead = getMockEndpoint("mock:dead");
        dead.expectedMessageCount(1);
        start("deadLetterChannel", new ThrowsFirst());
        assertDeadLetterChannelGotTheThrownFailure(send(), dead);

        Exchange out = send();
        assertNull(out.getException());
        assertEquals(1, getMockEndpoint("mock:result").getReceivedCounter(), "mock:result");
        assertEquals(2, calls.get(), "calls");
    }

    @Test
    void controlFailureSetOnTheExchange() throws Exception {
        // control, as before: a processor that sets the exception on the exchange and calls the callback, as an
        // AsyncProcessor should, goes to the dead letter channel
        MockEndpoint dead = getMockEndpoint("mock:dead");
        dead.expectedMessageCount(1);
        start("deadLetterChannel", new AsyncProcessorSupport() {
            @Override
            public boolean process(Exchange exchange, AsyncCallback callback) {
                calls.incrementAndGet();
                exchange.setException(new IllegalStateException("thrown"));
                callback.done(true);
                return true;
            }
        });
        assertDeadLetterChannelGotTheThrownFailure(send(), dead);
        assertEquals(1, calls.get(), "calls");
    }

    @Test
    void controlPlainProcessorThrows() throws Exception {
        // control, as before: the converter of a plain Processor to an AsyncProcessor catches the exception and sets it
        // on the exchange, so the dead letter channel gets it
        MockEndpoint dead = getMockEndpoint("mock:dead");
        dead.expectedMessageCount(1);
        start("deadLetterChannel", exchange -> {
            calls.incrementAndGet();
            throw new IllegalStateException("thrown");
        });
        assertDeadLetterChannelGotTheThrownFailure(send(), dead);
        assertEquals(1, calls.get(), "calls");
    }

    @Test
    void callbackCalledBeforeTheProcessorThrowsContinuesTheExchangeOnce() throws Exception {
        // the processor calls the callback and then throws: the callback continued the exchange, so the exception is
        // only logged, and the exchange completes once (before, the catch of the task failed the exchange and completed
        // it a second time)
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);
        start("deadLetterChannel", new AsyncProcessorSupport() {
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
        assertEquals(0, getMockEndpoint("mock:dead").getReceivedCounter(), "mock:dead");
        assertEquals(1, done.get(), "the exchange was done once");
        assertEquals(1, calls.get(), "calls");
    }

    @Test
    void callbackOfARouteBeforeTheProcessorThrowsContinuesTheExchange() throws Exception {
        // the processor hands the exchange and the callback to a direct route (with the default error handler) and then
        // throws. Each step of that route runs on the reactive executor ahead of the takeover queued there and the route
        // calls the callback through it, so it continues the exchange: the exchange completes with the reply of that
        // route, once. (A route with noErrorHandler() would lose to the takeover, and its callback be ignored.)
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:inner").setBody(constant("inner")).setHeader("inner", constant("yes"));
            }
        });
        AtomicReference<AsyncProducer> producer = new AtomicReference<>();
        start("deadLetterChannel", new AsyncProcessorSupport() {
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
            assertEquals(0, getMockEndpoint("mock:dead").getReceivedCounter(), "mock:dead");
            assertEquals(1, done.get(), "the exchange was done once");
            assertEquals(1, calls.get(), "calls");
        } finally {
            direct.stop();
        }
    }

    @Test
    void callbackCalledAfterTheProcessorThrewIsIgnored() throws Exception {
        // the processor hands the callback to another thread and then throws. The error handler cannot tell this from a
        // callback that never comes, so it takes over the thrown failure, and the late callback must not continue the
        // exchange a second time
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Thread> late = new AtomicReference<>();
        AtomicReference<Throwable> lateFailure = new AtomicReference<>();
        MockEndpoint dead = getMockEndpoint("mock:dead");
        dead.expectedMessageCount(1);
        AtomicInteger done = countDoneExchanges();
        start("deadLetterChannel", new AsyncProcessorSupport() {
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

        assertDeadLetterChannelGotTheThrownFailure(send(), dead);

        // now the processor calls the callback, after the exchange completed
        release.countDown();
        late.get().join(TimeUnit.SECONDS.toMillis(10));
        assertFalse(late.get().isAlive(), "the late callback thread ended");
        assertNull(lateFailure.get(), "the late callback failed");
        assertEquals(1, done.get(), "the exchange was done once");
        assertEquals(1, dead.getReceivedCounter(), "mock:dead");
        assertEquals(0, getMockEndpoint("mock:result").getReceivedCounter(), "mock:result");
        assertEquals(1, calls.get(), "calls");
    }

    @Test
    void callbackCalledAfterTheProcessorThrewDoesNotContinueTheNextExchange() throws Exception {
        // as above, but the late callback comes while the next exchange waits for the callback of its own first
        // delivery: it must not continue that exchange (with pooled tasks, the next exchange may get the same task)
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch releaseSecond = new CountDownLatch(1);
        AtomicReference<Thread> first = new AtomicReference<>();
        AtomicReference<Thread> second = new AtomicReference<>();
        AtomicReference<Throwable> lateFailure = new AtomicReference<>();
        MockEndpoint dead = getMockEndpoint("mock:dead");
        dead.expectedMessageCount(1);
        start("deadLetterChannel", new AsyncProcessorSupport() {
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

        assertDeadLetterChannelGotTheThrownFailure(send(), dead);

        CompletableFuture<Exchange> next = asyncSend(false);
        await().atMost(10, TimeUnit.SECONDS).until(() -> second.get() != null);

        // the callback of the first delivery of the first exchange, while the next exchange waits for its own
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
        assertEquals(1, dead.getReceivedCounter(), "mock:dead");
        assertEquals(2, calls.get(), "calls");
    }

    @Test
    void callbackCalledTwiceContinuesTheExchangeOnce() throws Exception {
        // a processor that calls its callback twice (also a broken processor): the second call is ignored, also when the
        // first one released the task (pooled tasks are reset when they are released)
        AtomicReference<Throwable> secondCall = new AtomicReference<>();
        start("deadLetterChannel", new AsyncProcessorSupport() {
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
        // takeover race, and whichever wins, each exchange is continued once (to mock:result, or with the thrown
        // failure to the dead letter channel)
        int count = 500;
        ExecutorService callbacks = Executors.newFixedThreadPool(2);
        try {
            start("deadLetterChannel", new AsyncProcessorSupport() {
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
                sends.add(asyncSend(false));
            }
            for (CompletableFuture<Exchange> send : sends) {
                assertNull(send.get(20, TimeUnit.SECONDS).getException());
            }
            await().atMost(10, TimeUnit.SECONDS).until(() -> done.get() >= count);
            // the callbacks that lost have run too
            callbacks.shutdown();
            assertTrue(callbacks.awaitTermination(10, TimeUnit.SECONDS), "the callbacks ran");

            assertEquals(count, done.get(), "each exchange was done once");
            assertEquals(count, getMockEndpoint("mock:result").getReceivedCounter()
                                + getMockEndpoint("mock:dead").getReceivedCounter(),
                    "mock:result + mock:dead");
            assertEquals(count, calls.get(), "calls");
        } finally {
            callbacks.shutdownNow();
        }
    }
}
