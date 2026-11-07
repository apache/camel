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
package org.apache.camel.processor;

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
import org.apache.camel.Channel;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Navigate;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

abstract class StepWithoutErrorHandlerProcessorThrowsTestSupport extends ContextTestSupport {

    final AtomicInteger calls = new AtomicInteger();

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    /**
     * Throws on the first call, and succeeds on the later calls.
     */
    final class ThrowsFirst extends AsyncProcessorSupport {
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
     * Routes from direct:{id}, to the mocks {id}-result, {id}-caught and {id}-finally, so the processor that throws and
     * the one that sets the exception can run side by side.
     */
    interface Routes {
        void configure(RouteBuilder builder, String id, Processor processor);
    }

    static final Routes DO_TRY = (b, id, p) -> b
            .from("direct:" + id)
            .doTry()
                .process(p).to("mock:" + id + "-result")
            .doCatch(IllegalStateException.class)
                .to("mock:" + id + "-caught")
            .end();

    static final Routes DO_TRY_FINALLY = (b, id, p) -> b
            .from("direct:" + id)
            .doTry()
                .process(p).to("mock:" + id + "-result")
            .doFinally()
                .to("mock:" + id + "-finally")
            .end();

    static final Routes DO_CATCH = (b, id, p) -> b
            .from("direct:" + id)
            .doTry()
                .throwException(new IllegalArgumentException("failed"))
            .doCatch(IllegalArgumentException.class)
                .process(p).to("mock:" + id + "-result")
            .doFinally()
                .to("mock:" + id + "-finally")
            .end();

    static final Routes DO_FINALLY = (b, id, p) -> b
            .from("direct:" + id)
            .doTry()
                .to("mock:" + id + "-try")
            .doFinally()
                .process(p).to("mock:" + id + "-result")
            .end();

    static final Routes ON_EXCEPTION = (b, id, p) -> b
            .from("direct:" + id)
            .onException(IllegalArgumentException.class).handled(true)
                .process(p).to("mock:" + id + "-result")
            .end()
            .throwException(new IllegalArgumentException("failed"));

    static final Routes ON_EXCEPTION_NOT_HANDLED = (b, id, p) -> b
            .from("direct:" + id)
            .onException(IllegalArgumentException.class)
                .process(p).to("mock:" + id + "-result")
            .end()
            .throwException(new IllegalArgumentException("failed"));

    // the outputs of a multicast have no error handler in their channel (the multicast wraps each one in a clone of
    // the error handler of the route); the second output is the mock {id}-finally, so the outcome shows whether it ran
    static final Routes MULTICAST = (b, id, p) -> b
            .from("direct:" + id)
            .multicast()
                .process(p)
                .to("mock:" + id + "-finally")
            .end()
            .to("mock:" + id + "-result");

    // the clone of the error handler around each output of a multicast redelivers it
    static final Routes MULTICAST_REDELIVERY = (b, id, p) -> b
            .from("direct:" + id)
            .errorHandler(b.defaultErrorHandler().maximumRedeliveries(2).redeliveryDelay(0))
            .multicast()
                .process(p)
                .to("mock:" + id + "-finally")
            .end()
            .to("mock:" + id + "-result");

    void start(Routes routes, Processor processor) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                routes.configure(this, "start", processor);
            }
        });
        context.start();
    }

    Exchange send() throws Exception {
        return asyncSend().get(10, TimeUnit.SECONDS);
    }

    CompletableFuture<Exchange> asyncSend() {
        // bounded by the caller: on a hang the future does not complete and get times out
        return template.asyncSend("direct:start", e -> e.getMessage().setBody("Hello"));
    }

    /**
     * Sends on this thread, which runs the reactive work of the exchange until none is left: when it returns, the
     * takeover of a thrown failure has run too.
     */
    Exchange sendOnThisThread() {
        return template.send("direct:start", e -> e.getMessage().setBody("Hello"));
    }

    /**
     * Counts the exchanges done: a second continuation of an exchange would complete its unit of work again.
     */
    AtomicInteger countDoneExchanges() {
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

    /**
     * What a route does with an exchange: the failure, the caught exception and the mocks reached.
     */
    String outcome(Exchange out) {
        return outcome(out, "start", calls);
    }

    String outcome(Exchange out, String id, AtomicInteger calls) {
        Exception caught = out.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        return "exception=" + describe(out.getException()) + " caught=" + describe(caught)
               + " result=" + getMockEndpoint("mock:" + id + "-result").getReceivedCounter()
               + " caughtMock=" + getMockEndpoint("mock:" + id + "-caught").getReceivedCounter()
               + " finally=" + getMockEndpoint("mock:" + id + "-finally").getReceivedCounter()
               + " calls=" + calls.get();
    }

    static String describe(Throwable t) {
        return t == null ? "none" : t.getClass().getSimpleName() + ":" + t.getMessage();
    }

    /**
     * The route with the processor that sets the exception on the exchange (from direct:sets) and with the one that
     * throws (from direct:start) must do the same.
     */
    void assertSameAsSettingTheException(Routes routes, String expected) throws Exception {
        AtomicInteger setsCalls = new AtomicInteger();
        Processor sets = new AsyncProcessorSupport() {
            @Override
            public boolean process(Exchange exchange, AsyncCallback callback) {
                if (setsCalls.incrementAndGet() == 1) {
                    exchange.setException(new IllegalStateException("thrown"));
                }
                callback.done(true);
                return true;
            }
        };
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                routes.configure(this, "sets", sets);
                routes.configure(this, "start", new ThrowsFirst());
            }
        });
        context.start();

        // the exchanges done (with the copies of a multicast): a second continuation would complete one again
        AtomicInteger done = countDoneExchanges();
        Exchange reference = template.asyncSend("direct:sets", e -> e.getMessage().setBody("Hello")).get(10,
                TimeUnit.SECONDS);
        assertEquals(expected, outcome(reference, "sets", setsCalls), "the processor that sets the exception");
        await().atMost(10, TimeUnit.SECONDS).until(() -> done.get() >= 1);
        int referenceDone = done.getAndSet(0);

        Exchange out = send();
        assertEquals(expected, outcome(out), "the processor that throws");
        await().atMost(10, TimeUnit.SECONDS).until(() -> done.get() >= referenceDone);
        assertEquals(referenceDone, done.get(), "the exchanges done, as with the processor that sets the exception");
    }

    @Test
    @ExpectSanitizerViolation(Violation.THREW_BEFORE_CALLBACK)
    void nextExchangeIsProcessed() throws Exception {
        start(DO_TRY, new ThrowsFirst());
        assertEquals("exception=none caught=IllegalStateException:thrown result=0 caughtMock=1 finally=0 calls=1",
                outcome(send()));

        Exchange out = send();
        assertNull(out.getException());
        assertNull(out.getProperty(Exchange.EXCEPTION_CAUGHT));
        assertEquals(1, getMockEndpoint("mock:start-result").getReceivedCounter(), "mock:result");
        assertEquals(1, getMockEndpoint("mock:start-caught").getReceivedCounter(), "mock:caught");
        assertEquals(2, calls.get(), "calls");
    }

    static Channel findChannel(Navigate<Processor> nav, Processor processor) {
        if (nav == null || !nav.hasNext()) {
            return null;
        }
        for (Processor p : nav.next()) {
            if (p instanceof Channel channel && channel.getNextProcessor() == processor) {
                return channel;
            }
            if (p instanceof Navigate<?>) {
                @SuppressWarnings("unchecked")
                Channel found = findChannel((Navigate<Processor>) p, processor);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Test
    @ExpectSanitizerViolation({ Violation.THREW_BEFORE_CALLBACK, Violation.THREW_AFTER_CALLBACK })
    @ExpectSanitizerViolation({ Violation.CALLBACK_AFTER_THROW, Violation.THREW_AFTER_CALLBACK })
    void callbackCalledAfterTheProcessorThrewIsIgnored() throws Exception {
        // the processor hands the callback to another thread and then throws. The step cannot tell this from a callback
        // that never comes, so it fails the exchange with the thrown exception (before, the exchange hung), and the
        // late callback must not continue the exchange a second time
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Thread> late = new AtomicReference<>();
        AtomicReference<Throwable> lateFailure = new AtomicReference<>();
        start(DO_TRY, new AsyncProcessorSupport() {
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
        AtomicInteger done = countDoneExchanges();

        assertEquals("exception=none caught=IllegalStateException:thrown result=0 caughtMock=1 finally=0 calls=1",
                outcome(send()));

        // now the processor calls the callback, after the exchange completed
        release.countDown();
        late.get().join(TimeUnit.SECONDS.toMillis(10));
        assertFalse(late.get().isAlive(), "the late callback thread ended");
        assertNull(lateFailure.get(), "the late callback failed");
        assertEquals(1, done.get(), "the exchange was done once");
        assertEquals(0, getMockEndpoint("mock:start-result").getReceivedCounter(), "mock:result");
        assertEquals(1, getMockEndpoint("mock:start-caught").getReceivedCounter(), "mock:caught");
    }

    @Test
    @ExpectSanitizerViolation({ Violation.THREW_BEFORE_CALLBACK, Violation.THREW_AFTER_CALLBACK })
    @ExpectSanitizerViolation({ Violation.CALLBACK_AFTER_THROW, Violation.THREW_AFTER_CALLBACK })
    void callbackCalledAfterTheProcessorThrewDoesNotContinueTheNextExchange() throws Exception {
        // as above, but the late callback comes while the next exchange waits for the callback of its own call of the
        // processor: it must not continue that exchange
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch releaseSecond = new CountDownLatch(1);
        AtomicReference<Thread> first = new AtomicReference<>();
        AtomicReference<Thread> second = new AtomicReference<>();
        AtomicReference<Throwable> lateFailure = new AtomicReference<>();
        start(DO_TRY, new AsyncProcessorSupport() {
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

        assertEquals("exception=none caught=IllegalStateException:thrown result=0 caughtMock=1 finally=0 calls=1",
                outcome(send()));

        CompletableFuture<Exchange> next = asyncSend();
        await().atMost(10, TimeUnit.SECONDS).until(() -> second.get() != null);

        // the callback of the first call, while the second call waits for its own
        releaseFirst.countDown();
        first.get().join(TimeUnit.SECONDS.toMillis(10));
        assertFalse(first.get().isAlive(), "the late callback thread ended");
        assertFalse(next.isDone(), "the late callback of the first exchange continued the next exchange");
        assertEquals(0, getMockEndpoint("mock:start-result").getReceivedCounter(), "mock:result");

        releaseSecond.countDown();
        Exchange out = next.get(10, TimeUnit.SECONDS);
        assertNull(out.getException());
        assertEquals(1, getMockEndpoint("mock:start-result").getReceivedCounter(), "mock:result");
        second.get().join(TimeUnit.SECONDS.toMillis(10));
        assertNull(lateFailure.get(), "a callback failed");
        assertEquals(2, calls.get(), "calls");
    }

    @Test
    @ExpectSanitizerViolation({ Violation.THREW_BEFORE_CALLBACK, Violation.THREW_AFTER_CALLBACK })
    @ExpectSanitizerViolation({ Violation.CALLBACK_AFTER_THROW, Violation.THREW_AFTER_CALLBACK })
    void callbackAndThrowAtTheSameTimeContinueEachExchangeOnce() throws Exception {
        // the processor hands the callback to another thread, which calls it at once, and throws: the callback and the
        // takeover race, and whichever wins, each exchange is continued once (as a success, or caught by the doCatch)
        int count = 50;
        ExecutorService callbacks = Executors.newFixedThreadPool(2);
        try {
            start(DO_TRY, new AsyncProcessorSupport() {
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
                sends.add(asyncSend());
            }
            int succeeded = 0;
            int caught = 0;
            for (CompletableFuture<Exchange> send : sends) {
                Exchange out = send.get(20, TimeUnit.SECONDS);
                assertNull(out.getException());
                Exception e = out.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                if (e == null) {
                    succeeded++;
                } else {
                    assertEquals("thrown", e.getMessage());
                    caught++;
                }
            }
            await().atMost(10, TimeUnit.SECONDS).until(() -> done.get() >= count);
            // the callbacks that lost have run too
            callbacks.shutdown();
            assertTrue(callbacks.awaitTermination(10, TimeUnit.SECONDS), "the callbacks ran");

            assertEquals(count, done.get(), "each exchange was done once");
            assertEquals(succeeded, getMockEndpoint("mock:start-result").getReceivedCounter(), "mock:result");
            assertEquals(caught, getMockEndpoint("mock:start-caught").getReceivedCounter(), "mock:caught");
            assertEquals(count, calls.get(), "calls");
        } finally {
            callbacks.shutdownNow();
        }
    }

    @Test
    @ExpectSanitizerViolation(Violation.COMPLETED_TWICE)
    void callbackCalledTwiceContinuesTheExchangeOnce() throws Exception {
        // a processor that calls its callback twice (also a broken processor): the second call is ignored
        AtomicReference<Throwable> secondCall = new AtomicReference<>();
        start(DO_TRY, new AsyncProcessorSupport() {
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
        assertEquals("exception=none caught=none result=1 caughtMock=0 finally=0 calls=1", outcome(out));
        assertNull(secondCall.get(), "the second call of the callback failed");
        assertEquals(1, done.get(), "the exchange was done once");
    }
}
