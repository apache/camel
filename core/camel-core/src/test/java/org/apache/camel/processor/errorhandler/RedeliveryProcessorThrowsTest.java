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

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.AsyncProducer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.ExchangeCompletedEvent;
import org.apache.camel.spi.CamelEvent.ExchangeFailedEvent;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.EventNotifierSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * An {@link AsyncProcessor} that throws from {@code process(exchange, callback)} on a redelivery, instead of setting
 * the exception on the exchange: the exchange must complete (failed or handled) instead of hanging, for an immediate, a
 * synchronous delayed, an asynchronous delayed and a transacted redelivery. Each send is bounded, so a hang fails the
 * test.
 */
class RedeliveryProcessorThrowsTest extends ContextTestSupport {

    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicInteger onRedeliveries = new AtomicInteger();

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    /**
     * The first call fails the exchange as an AsyncProcessor should (the exception on the exchange), the second call
     * throws instead, and the later calls succeed.
     */
    private final class FailsThenThrows extends AsyncProcessorSupport {
        @Override
        public boolean process(Exchange exchange, AsyncCallback callback) {
            int call = calls.incrementAndGet();
            if (call == 1) {
                exchange.setException(new IOException("first"));
            } else if (call == 2) {
                throw new IllegalStateException("second");
            }
            callback.done(true);
            return true;
        }
    }

    private void start(String errorHandler, AsyncProcessor processor) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                switch (errorHandler) {
                    case "immediate" -> errorHandler(defaultErrorHandler().maximumRedeliveries(1).redeliveryDelay(0));
                    case "synchronousDelayed" ->
                        errorHandler(defaultErrorHandler().maximumRedeliveries(1).redeliveryDelay(10));
                    case "asynchronousDelayed" -> errorHandler(defaultErrorHandler().maximumRedeliveries(1)
                            .redeliveryDelay(10).asyncDelayedRedelivery());
                    // the asynchronous redelivery then wakes up to check whether Camel is stopping before it redelivers
                    case "asynchronousDelayedNotWhileStopping" -> errorHandler(defaultErrorHandler().maximumRedeliveries(1)
                            .redeliveryDelay(10).asyncDelayedRedelivery().allowRedeliveryWhileStopping(false));
                    case "twoRedeliveries" -> errorHandler(defaultErrorHandler().maximumRedeliveries(2).redeliveryDelay(0));
                    case "deadLetterChannel" ->
                        errorHandler(deadLetterChannel("mock:dead").maximumRedeliveries(1).redeliveryDelay(0));
                    case "deadLetterChannelAsynchronousDelayed" -> errorHandler(deadLetterChannel("mock:dead")
                            .maximumRedeliveries(1).redeliveryDelay(10).asyncDelayedRedelivery());
                    case "onRedelivery" -> errorHandler(defaultErrorHandler().maximumRedeliveries(2).redeliveryDelay(0)
                            .onRedelivery(e -> onRedeliveries.incrementAndGet()));
                    case "onException" -> {
                        onException(IOException.class).maximumRedeliveries(1).redeliveryDelay(0);
                        onException(IllegalStateException.class).handled(true).to("mock:handled");
                    }
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
        // bounded: on a hang the future does not complete and get times out
        return template.asyncSend("direct:start", e -> {
            e.getMessage().setBody("Hello");
            e.getExchangeExtension().setTransacted(transacted);
        }).get(10, TimeUnit.SECONDS);
    }

    private void assertFailedWithTheThrownException(Exchange out) {
        assertInstanceOf(IllegalStateException.class, out.getException());
        assertEquals("second", out.getException().getMessage());
        assertEquals(2, calls.get(), "the processor was called for the delivery and the redelivery");
        assertEquals(0, getMockEndpoint("mock:result").getReceivedCounter(), "mock:result");
    }

    @Test
    void immediateRedelivery() throws Exception {
        start("immediate", new FailsThenThrows());
        assertFailedWithTheThrownException(send());
    }

    @Test
    void synchronousDelayedRedelivery() throws Exception {
        start("synchronousDelayed", new FailsThenThrows());
        assertFailedWithTheThrownException(send());
    }

    @Test
    void asynchronousDelayedRedelivery() throws Exception {
        start("asynchronousDelayed", new FailsThenThrows());
        assertFailedWithTheThrownException(send());
    }

    @Test
    void asynchronousDelayedRedeliveryNotAllowedWhileStopping() throws Exception {
        start("asynchronousDelayedNotWhileStopping", new FailsThenThrows());
        assertFailedWithTheThrownException(send());
    }

    @Test
    void transactedImmediateRedelivery() throws Exception {
        // a transacted exchange runs on the reactive executor's queue, and its redelivery is always synchronous
        start("immediate", new FailsThenThrows());
        assertFailedWithTheThrownException(send(true));
    }

    @Test
    void transactedSynchronousDelayedRedelivery() throws Exception {
        start("synchronousDelayed", new FailsThenThrows());
        assertFailedWithTheThrownException(send(true));
    }

    @Test
    void thrownFailureIsRedelivered() throws Exception {
        // the error handler handles the thrown failure as any failure of the processor: it redelivers again, and the
        // third call succeeds
        start("twoRedeliveries", new FailsThenThrows());
        Exchange out = send();
        assertNull(out.getException());
        assertEquals(3, calls.get(), "calls");
        assertEquals(1, getMockEndpoint("mock:result").getReceivedCounter(), "mock:result");
        assertEquals(2, out.getMessage().getHeader(Exchange.REDELIVERY_COUNTER, Integer.class), "redelivery counter");
    }

    @Test
    void deadLetterChannelGetsTheThrownFailure() throws Exception {
        // with an asynchronous delayed redelivery
        MockEndpoint dead = getMockEndpoint("mock:dead");
        dead.expectedMessageCount(1);
        start("deadLetterChannelAsynchronousDelayed", new FailsThenThrows());

        Exchange out = send();
        assertNull(out.getException(), "the dead letter channel handled the failure");
        dead.assertIsSatisfied();
        Exception caught = dead.getReceivedExchanges().get(0).getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        assertInstanceOf(IllegalStateException.class, caught);
        assertEquals("second", caught.getMessage());
        assertEquals(1, dead.getReceivedCounter(), "mock:dead");
        assertEquals(2, calls.get(), "calls");
    }

    @Test
    void onRedeliveryRunsBeforeEachRedelivery() throws Exception {
        // the thrown failure is redelivered like any other, so the onRedelivery processor runs before each redelivery
        start("onRedelivery", new FailsThenThrows());
        Exchange out = send();
        assertNull(out.getException());
        assertEquals(3, calls.get(), "calls");
        assertEquals(2, onRedeliveries.get(), "onRedelivery calls");
        assertEquals(1, getMockEndpoint("mock:result").getReceivedCounter(), "mock:result");
    }

    @Test
    void onExceptionOfTheThrownFailureHandlesIt() throws Exception {
        // the exception policy is chosen again for the thrown failure, as for any new failure
        start("onException", new FailsThenThrows());

        Exchange out = send();
        assertNull(out.getException(), "the onException handled the thrown failure");
        assertEquals(1, getMockEndpoint("mock:handled").getReceivedCounter(), "mock:handled");
        assertEquals(2, calls.get(), "calls");
    }

    @Test
    void controlCallbackCalledBeforeTheProcessorThrows() throws Exception {
        // the processor calls the callback and then throws: the callback continued the exchange, so the exception is
        // left to the reactive executor (which logs it), as before, and the exchange completes once
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);
        start("immediate", new AsyncProcessorSupport() {
            @Override
            public boolean process(Exchange exchange, AsyncCallback callback) {
                int call = calls.incrementAndGet();
                if (call == 1) {
                    exchange.setException(new IOException("first"));
                    callback.done(true);
                    return true;
                }
                callback.done(true);
                throw new IllegalStateException("after the callback");
            }
        });

        Exchange out = send();
        assertNull(out.getException());
        result.assertIsSatisfied();
        assertEquals(1, result.getReceivedCounter(), "mock:result");
        assertEquals(2, calls.get(), "calls");
    }

    @Test
    void controlQueuedCallbackBeforeTheProcessorThrows() throws Exception {
        // the processor hands the callback to a direct route and then throws. The route runs on the reactive executor
        // and calls the callback through it, before the error handler would take over the thrown failure, so it
        // continues the exchange as before: the exchange completes with the reply of that route
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:inner").setBody(constant("inner")).setHeader("inner", constant("yes"));
            }
        });
        AtomicReference<AsyncProducer> inner = new AtomicReference<>();
        start("immediate", new AsyncProcessorSupport() {
            @Override
            public boolean process(Exchange exchange, AsyncCallback callback) {
                int call = calls.incrementAndGet();
                if (call == 1) {
                    exchange.setException(new IOException("first"));
                    callback.done(true);
                    return true;
                }
                inner.get().process(exchange, callback);
                throw new IllegalStateException("after the callback");
            }
        });
        AsyncProducer producer = context.getEndpoint("direct:inner").createAsyncProducer();
        producer.start();
        inner.set(producer);
        try {
            Exchange out = send();
            assertNull(out.getException());
            assertEquals("inner", out.getMessage().getBody());
            assertEquals("yes", out.getMessage().getHeader("inner"));
            assertEquals(1, getMockEndpoint("mock:result").getReceivedCounter(), "mock:result");
            assertEquals(2, calls.get(), "calls");
        } finally {
            producer.stop();
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
        MockEndpoint dead = getMockEndpoint("mock:dead");
        dead.expectedMessageCount(1);
        // the exchange must be done once: a second continuation would complete its unit of work again
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
        start("deadLetterChannel", new AsyncProcessorSupport() {
            @Override
            public boolean process(Exchange exchange, AsyncCallback callback) {
                int call = calls.incrementAndGet();
                if (call == 1) {
                    exchange.setException(new IOException("first"));
                    callback.done(true);
                    return true;
                }
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
                throw new IllegalStateException("second");
            }
        });

        Exchange out = send();
        assertNull(out.getException(), "the dead letter channel handled the thrown failure");
        dead.assertIsSatisfied();

        // now the processor calls the callback, after the exchange completed
        release.countDown();
        late.get().join(TimeUnit.SECONDS.toMillis(10));
        assertFalse(late.get().isAlive(), "the late callback thread ended");
        assertNull(lateFailure.get(), "the late callback failed");
        assertEquals(1, done.get(), "the exchange was done once");
        assertEquals(1, dead.getReceivedCounter(), "mock:dead");
        assertEquals(0, getMockEndpoint("mock:result").getReceivedCounter(), "mock:result");
        assertEquals(2, calls.get(), "calls");
    }
}
