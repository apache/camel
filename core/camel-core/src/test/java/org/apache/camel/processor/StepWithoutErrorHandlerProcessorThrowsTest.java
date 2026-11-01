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
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Channel;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.ExchangeCompletedEvent;
import org.apache.camel.spi.CamelEvent.ExchangeFailedEvent;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.EventNotifierSupport;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An {@link AsyncProcessor} that throws from {@code process(exchange, callback)}, instead of setting the exception on
 * the exchange, in a step that has no error handler (the steps of doTry, doCatch, doFinally and onException): the
 * exchange must go on as when the processor sets the exception on the exchange, instead of hanging, and be continued
 * exactly once. Each send is bounded, so a hang fails the test.
 */
class StepWithoutErrorHandlerProcessorThrowsTest extends ContextTestSupport {

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
     * Routes from direct:{id}, to the mocks {id}-result, {id}-caught and {id}-finally, so the processor that throws and
     * the one that sets the exception can run side by side.
     */
    private interface Routes {
        void configure(RouteBuilder builder, String id, Processor processor);
    }

    private static final Routes DO_TRY = (b, id, p) -> b
            .from("direct:" + id)
            .doTry()
                .process(p).to("mock:" + id + "-result")
            .doCatch(IllegalStateException.class)
                .to("mock:" + id + "-caught")
            .end();

    private static final Routes DO_TRY_FINALLY = (b, id, p) -> b
            .from("direct:" + id)
            .doTry()
                .process(p).to("mock:" + id + "-result")
            .doFinally()
                .to("mock:" + id + "-finally")
            .end();

    private static final Routes DO_CATCH = (b, id, p) -> b
            .from("direct:" + id)
            .doTry()
                .throwException(new IllegalArgumentException("failed"))
            .doCatch(IllegalArgumentException.class)
                .process(p).to("mock:" + id + "-result")
            .doFinally()
                .to("mock:" + id + "-finally")
            .end();

    private static final Routes DO_FINALLY = (b, id, p) -> b
            .from("direct:" + id)
            .doTry()
                .to("mock:" + id + "-try")
            .doFinally()
                .process(p).to("mock:" + id + "-result")
            .end();

    private static final Routes ON_EXCEPTION = (b, id, p) -> b
            .from("direct:" + id)
            .onException(IllegalArgumentException.class).handled(true)
                .process(p).to("mock:" + id + "-result")
            .end()
            .throwException(new IllegalArgumentException("failed"));

    private static final Routes ON_EXCEPTION_NOT_HANDLED = (b, id, p) -> b
            .from("direct:" + id)
            .onException(IllegalArgumentException.class)
                .process(p).to("mock:" + id + "-result")
            .end()
            .throwException(new IllegalArgumentException("failed"));

    // the outputs of a multicast have no error handler in their channel (the multicast wraps each one in a clone of
    // the error handler of the route); the second output is the mock {id}-finally, so the outcome shows whether it ran
    private static final Routes MULTICAST = (b, id, p) -> b
            .from("direct:" + id)
            .multicast()
                .process(p)
                .to("mock:" + id + "-finally")
            .end()
            .to("mock:" + id + "-result");

    // the clone of the error handler around each output of a multicast redelivers it
    private static final Routes MULTICAST_REDELIVERY = (b, id, p) -> b
            .from("direct:" + id)
            .errorHandler(b.defaultErrorHandler().maximumRedeliveries(2).redeliveryDelay(0))
            .multicast()
                .process(p)
                .to("mock:" + id + "-finally")
            .end()
            .to("mock:" + id + "-result");

    private void start(Routes routes, Processor processor) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                routes.configure(this, "start", processor);
            }
        });
        context.start();
    }

    private Exchange send() throws Exception {
        return asyncSend().get(10, TimeUnit.SECONDS);
    }

    private CompletableFuture<Exchange> asyncSend() {
        // bounded by the caller: on a hang the future does not complete and get times out
        return template.asyncSend("direct:start", e -> e.getMessage().setBody("Hello"));
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

    /**
     * What a route does with an exchange: the failure, the caught exception and the mocks reached.
     */
    private String outcome(Exchange out) {
        return outcome(out, "start", calls);
    }

    private String outcome(Exchange out, String id, AtomicInteger calls) {
        Exception caught = out.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        return "exception=" + describe(out.getException()) + " caught=" + describe(caught)
               + " result=" + getMockEndpoint("mock:" + id + "-result").getReceivedCounter()
               + " caughtMock=" + getMockEndpoint("mock:" + id + "-caught").getReceivedCounter()
               + " finally=" + getMockEndpoint("mock:" + id + "-finally").getReceivedCounter()
               + " calls=" + calls.get();
    }

    private static String describe(Throwable t) {
        return t == null ? "none" : t.getClass().getSimpleName() + ":" + t.getMessage();
    }

    /**
     * The route with the processor that sets the exception on the exchange (from direct:sets) and with the one that
     * throws (from direct:start) must do the same.
     */
    private void assertSameAsSettingTheException(Routes routes, String expected) throws Exception {
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
    void doTry() throws Exception {
        assertSameAsSettingTheException(DO_TRY,
                "exception=none caught=IllegalStateException:thrown result=0 caughtMock=1 finally=0 calls=1");
    }

    @Test
    void doTryWithDoFinally() throws Exception {
        assertSameAsSettingTheException(DO_TRY_FINALLY,
                "exception=IllegalStateException:thrown caught=IllegalStateException:thrown result=0 caughtMock=0 finally=1 calls=1");
    }

    @Test
    void doCatch() throws Exception {
        assertSameAsSettingTheException(DO_CATCH,
                "exception=IllegalStateException:thrown caught=IllegalStateException:thrown result=0 caughtMock=0"
                                                  + " finally=1 calls=1");
    }

    @Test
    void doFinally() throws Exception {
        assertSameAsSettingTheException(DO_FINALLY,
                "exception=IllegalStateException:thrown caught=none result=0 caughtMock=0 finally=0 calls=1");
    }

    @Test
    void onException() throws Exception {
        assertSameAsSettingTheException(ON_EXCEPTION,
                "exception=IllegalStateException:thrown caught=IllegalStateException:thrown result=0 caughtMock=0"
                                                      + " finally=0 calls=1");
    }

    @Test
    void onExceptionNotHandled() throws Exception {
        assertSameAsSettingTheException(ON_EXCEPTION_NOT_HANDLED,
                "exception=IllegalStateException:thrown caught=IllegalStateException:thrown result=0 caughtMock=0"
                                                                  + " finally=0 calls=1");
    }

    @Test
    void multicastOutput() throws Exception {
        // the failed output fails the exchange at the end of the multicast, and the next output still runs
        assertSameAsSettingTheException(MULTICAST,
                "exception=IllegalStateException:thrown caught=none result=0 caughtMock=0 finally=1 calls=1");
    }

    @Test
    void multicastOutputRedelivered() throws Exception {
        // the output is redelivered as when it sets the exception, and succeeds on the second call (before, the
        // exception thrown on the first call failed the exchange)
        assertSameAsSettingTheException(MULTICAST_REDELIVERY,
                "exception=none caught=none result=1 caughtMock=0 finally=1 calls=2");
    }

    @Test
    void afterAnAsynchronousStep() throws Exception {
        // the step runs on the thread of the asynchronous delay, not on the caller's
        start((b, id, p) -> b
                .from("direct:" + id)
                .doTry()
                    .delay(10).asyncDelayed().process(p).to("mock:" + id + "-result")
                .endDoTry()
                .doCatch(IllegalStateException.class)
                    .to("mock:" + id + "-caught")
                .end(), new ThrowsFirst());

        assertEquals("exception=none caught=IllegalStateException:thrown result=0 caughtMock=1 finally=0 calls=1",
                outcome(send()));
    }

    @Test
    void withMdcLogging() throws Exception {
        // with MDC logging the unit of work wraps the callback of each step (the takeover runs its after processing
        // instead of that wrapper)
        context.setUseMDCLogging(true);
        start(DO_TRY, new ThrowsFirst());

        assertEquals("exception=none caught=IllegalStateException:thrown result=0 caughtMock=1 finally=0 calls=1",
                outcome(send()));
    }

    @Test
    void controlTransacted() throws Exception {
        // control: a transacted exchange calls the step synchronously, which catches the exception, on main too
        start(DO_TRY, new ThrowsFirst());

        Exchange out = template.asyncSend("direct:start", e -> {
            e.getMessage().setBody("Hello");
            e.getExchangeExtension().setTransacted(true);
        }).get(10, TimeUnit.SECONDS);
        assertEquals("exception=none caught=IllegalStateException:thrown result=0 caughtMock=1 finally=0 calls=1",
                outcome(out));
    }

    @Test
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

    @Test
    void controlPlainProcessorThrows() throws Exception {
        // control, as before: the converter of a plain Processor to an AsyncProcessor catches the exception and sets it
        // on the exchange
        start(DO_TRY, exchange -> {
            calls.incrementAndGet();
            throw new IllegalStateException("thrown");
        });

        assertEquals("exception=none caught=IllegalStateException:thrown result=0 caughtMock=1 finally=0 calls=1",
                outcome(send()));
    }

    @Test
    void controlCallbackCalledBeforeTheProcessorThrows() throws Exception {
        // control, as before: the processor calls the callback and then throws. The callback continued the exchange, so
        // the exception is left to the caller (the reactive executor, which logs it), and the exchange completes once
        start(DO_TRY, new AsyncProcessorSupport() {
            @Override
            public boolean process(Exchange exchange, AsyncCallback callback) {
                calls.incrementAndGet();
                callback.done(true);
                throw new IllegalStateException("after the callback");
            }
        });
        AtomicInteger done = countDoneExchanges();

        Exchange out = sendOnThisThread();
        assertEquals("exception=none caught=none result=1 caughtMock=0 finally=0 calls=1", outcome(out));
        assertEquals(1, done.get(), "the exchange was done once");
    }

    @Test
    void controlCallbackCalledBeforeTheProcessorThrowsReachesASynchronousCaller() throws Exception {
        // control, as before: a caller that calls the step through the synchronous API (as a circuit breaker does with
        // its steps) gets the exception that the processor throws after it called the callback, as the callback only
        // released that caller
        Processor processor = new AsyncProcessorSupport() {
            @Override
            public boolean process(Exchange exchange, AsyncCallback callback) {
                calls.incrementAndGet();
                callback.done(true);
                throw new IllegalStateException("after the callback");
            }
        };
        start(DO_TRY, processor);
        Channel channel = findChannel(context.getRoutes().get(0).navigate(), processor);
        assertNotNull(channel, "the channel of the step");

        Exchange exchange = context.getEndpoint("direct:start").createExchange();
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> channel.process(exchange));
        assertEquals("after the callback", e.getMessage());
        assertEquals(1, calls.get(), "calls");
    }

    private static Channel findChannel(Navigate<Processor> nav, Processor processor) {
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
    void controlCallbackQueuedBeforeTheProcessorThrows() throws Exception {
        // control, as before: the processor queues the call of the callback on the reactive executor and then throws.
        // The thrown failure is taken over through the reactive executor, after the work already queued there, so the
        // queued callback comes first and continues the exchange
        start(DO_TRY, new AsyncProcessorSupport() {
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
        assertEquals("exception=none caught=none result=1 caughtMock=0 finally=0 calls=1", outcome(out));
        assertEquals("queued", out.getMessage().getBody());
        assertEquals(1, done.get(), "the exchange was done once");
    }

    @Test
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

    @Test
    void callbackAndThrowAtTheSameTimeContinueEachExchangeOnce() throws Exception {
        // the processor hands the callback to another thread, which calls it at once, and throws: the callback and the
        // takeover race, and whichever wins, each exchange is continued once (as a success, or caught by the doCatch)
        int count = 500;
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
}
