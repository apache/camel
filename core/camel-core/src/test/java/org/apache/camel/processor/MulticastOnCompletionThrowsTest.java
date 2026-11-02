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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.ExchangeCompletedEvent;
import org.apache.camel.spi.CamelEvent.ExchangeFailedEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.builder.Builder.constant;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An {@link AggregationStrategy} whose {@code onCompletion} throws, in a multicast, split or recipient list: the
 * exchange must fail with the exception and go on, once, instead of hanging (the multicast was marked done before
 * {@code onCompletion}, so the failure could not complete it anymore), and the error handler of the route must handle
 * that failure as when the strategy throws from {@code aggregate}. Each send is bounded, so a hang fails the test.
 */
class MulticastOnCompletionThrowsTest extends ContextTestSupport {

    // releases the slow output of the timeout test
    private final CountDownLatch release = new CountDownLatch(1);

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @AfterEach
    void releaseSlowOutput() {
        release.countDown();
    }

    private static class Strategy implements AggregationStrategy {
        private final boolean throwOnCompletion;

        private Strategy(boolean throwOnCompletion) {
            this.throwOnCompletion = throwOnCompletion;
        }

        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            return newExchange;
        }

        @Override
        public void onCompletion(Exchange exchange, Exchange inputExchange) {
            if (throwOnCompletion) {
                throw new IllegalStateException("onCompletion");
            }
        }
    }

    /**
     * The EIP from direct:{id}, with the given strategy, followed by mock:{id}-result.
     */
    private interface Eip {
        ProcessorDefinition<?> configure(RouteDefinition route, AggregationStrategy strategy);
    }

    /**
     * The route with the strategy that does not throw (from direct:ok, the control) and with the one that throws (from
     * direct:start): the second fails with the exception from onCompletion, and both are done as many times.
     */
    private Exchange assertFailsOnce(Eip eip, String okOutcome, String thrownOutcome) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:slow").process(e -> release.await(20, TimeUnit.SECONDS));
                eip.configure(from("direct:ok"), new Strategy(false)).to("mock:ok-result");
                eip.configure(from("direct:start"), new Strategy(true)).to("mock:start-result");
            }
        });
        context.start();

        // the exchanges done (with the copies of a multicast): a second continuation would complete one again
        AtomicInteger done = countDoneExchanges();
        Exchange ok = template.asyncSend("direct:ok", e -> e.getMessage().setBody("a,b")).get(10, TimeUnit.SECONDS);
        assertEquals(okOutcome, outcome(ok, "ok"), "the strategy that does not throw");
        await().atMost(10, TimeUnit.SECONDS).until(() -> done.get() >= 1);
        int okDone = done.getAndSet(0);

        Exchange out = template.asyncSend("direct:start", e -> e.getMessage().setBody("a,b")).get(10, TimeUnit.SECONDS);
        assertEquals(thrownOutcome, outcome(out, "start"), "the strategy that throws from onCompletion");
        await().atMost(10, TimeUnit.SECONDS).until(() -> done.get() >= okDone);
        assertEquals(okDone, done.get(), "the exchanges done, as with the strategy that does not throw");
        return out;
    }

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

    private String outcome(Exchange out, String id) {
        Exception e = out.getException();
        // the message of a CamelExchangeException ends with the exchange, which differs between the routes
        String message = e == null ? null : e.getMessage().replaceAll("\\. Exchange\\[.*", "");
        return "exception=" + (e == null ? "none" : e.getClass().getSimpleName() + ":" + message)
               + " result=" + getMockEndpoint("mock:" + id + "-result").getReceivedCounter();
    }

    private static final String OK = "exception=none result=1";
    private static final String FAILED = "exception=IllegalStateException:onCompletion result=0";

    @Test
    void multicast() throws Exception {
        assertFailsOnce((r, s) -> r.multicast(s).to("mock:a", "mock:b").end(), OK, FAILED);
    }

    @Test
    void multicastParallel() throws Exception {
        assertFailsOnce((r, s) -> r.multicast(s).parallelProcessing().to("mock:a", "mock:b").end(), OK, FAILED);
    }

    @Test
    void multicastSynchronous() throws Exception {
        assertFailsOnce((r, s) -> r.multicast(s).synchronous().to("mock:a", "mock:b").end(), OK, FAILED);
    }

    @Test
    void multicastStopOnException() throws Exception {
        // the multicast is done from the callback of the failed output
        // (the exchange keeps the exception of the output, which tells where it failed, with the exception from
        // onCompletion as suppressed)
        String failed = "exception=CamelExchangeException:Multicast processing failed for number 1 result=0";
        Exchange out = assertFailsOnce((r, s) -> r.multicast(s).stopOnException().to("mock:a")
                .throwException(new IllegalArgumentException("failed")).end(), failed, failed);
        assertSuppressedOnCompletion(out);
    }

    @Test
    void multicastTimeout() throws Exception {
        // the multicast is done by the timeout, while the second output still waits
        assertFailsOnce((r, s) -> r.multicast(s).parallelProcessing().timeout(100).to("mock:a", "direct:slow").end(), OK,
                FAILED);
    }

    @Test
    void split() throws Exception {
        assertFailsOnce((r, s) -> r.split(body().tokenize(","), s).to("mock:a").end(), OK, FAILED);
    }

    @Test
    void splitStreaming() throws Exception {
        assertFailsOnce((r, s) -> r.split(body().tokenize(","), s).streaming().to("mock:a").end(), OK, FAILED);
    }

    @Test
    void recipientList() throws Exception {
        assertFailsOnce((r, s) -> r.recipientList(constant("mock:a,mock:b")).aggregationStrategy(s).end(), OK, FAILED);
    }

    @Test
    void splitInDoTry() throws Exception {
        // the doCatch runs once
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .doTry()
                            .split(body().tokenize(","), new Strategy(true)).synchronous()
                                .to("mock:a")
                            .end()
                            .to("mock:start-result")
                        .endDoTry()
                        .doCatch(IllegalStateException.class)
                            .to("mock:caught")
                        .end();
            }
        });
        context.start();
        getMockEndpoint("mock:caught").expectedMessageCount(1);

        Exchange out = template.asyncSend("direct:start", e -> e.getMessage().setBody("a,b")).get(10, TimeUnit.SECONDS);

        assertEquals("exception=none result=0", outcome(out, "start"));
        assertMockEndpointsSatisfied();
        assertEquals("onCompletion", out.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class).getMessage());
    }

    /**
     * The split with the given strategy, in a route whose error handler handles IllegalStateException
     * (onException(...).handled(true) to mock:handled) or redelivers it, so the failure must reach the error handler of
     * the route, not be handed back as exhausted.
     */
    private Exchange sendToSplitWithErrorHandler(AggregationStrategy strategy, boolean redeliver) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                if (redeliver) {
                    errorHandler(defaultErrorHandler().maximumRedeliveries(2).redeliveryDelay(0));
                } else {
                    onException(IllegalStateException.class).handled(true).to("mock:handled");
                }
                from("direct:start")
                        .split(body().tokenize(","), strategy)
                            .to("mock:a")
                        .end()
                        .to("mock:start-result");
            }
        });
        context.start();
        return template.asyncSend("direct:start", e -> e.getMessage().setBody("a,b")).get(10, TimeUnit.SECONDS);
    }

    @Test
    void splitFailureHandledByOnException() throws Exception {
        getMockEndpoint("mock:handled").expectedMessageCount(1);

        Exchange out = sendToSplitWithErrorHandler(new Strategy(true), false);

        assertEquals("exception=none result=0", outcome(out, "start"));
        assertMockEndpointsSatisfied();
        assertEquals("onCompletion", out.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class).getMessage());
    }

    @Test
    void splitFailureRedelivered() throws Exception {
        // the split is redelivered twice, as when the strategy throws from aggregate: each attempt sends both parts
        Exchange out = sendToSplitWithErrorHandler(new Strategy(true), true);

        assertEquals("exception=IllegalStateException:onCompletion result=0", outcome(out, "start"));
        assertEquals(6, getMockEndpoint("mock:a").getReceivedCounter());
    }

    @Test
    void splitAggregateFailureRedeliveredControl() throws Exception {
        // control (same on main): the strategy that throws from aggregate, on the first part, so each of the three
        // attempts sends one part
        Exchange out = sendToSplitWithErrorHandler(new Strategy(false) {
            @Override
            public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
                throw new IllegalStateException("aggregate");
            }
        }, true);

        assertEquals("exception=IllegalStateException:aggregate result=0", outcome(out, "start"));
        assertEquals(3, getMockEndpoint("mock:a").getReceivedCounter());
    }

    @Test
    void aggregateAndOnCompletionThrow() throws Exception {
        // the exchange keeps the exception of aggregate, with the one of onCompletion as suppressed
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .multicast(new Strategy(true) {
                            @Override
                            public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
                                throw new IllegalStateException("aggregate");
                            }
                        })
                        .to("mock:a", "mock:b")
                        .end()
                        .to("mock:start-result");
            }
        });
        context.start();

        Exchange out = template.asyncSend("direct:start", e -> e.getMessage().setBody("a,b")).get(10, TimeUnit.SECONDS);

        assertEquals("exception=IllegalStateException:aggregate result=0", outcome(out, "start"));
        assertSuppressedOnCompletion(out);
    }

    @Test
    void splitExpressionThrows() throws Exception {
        // the split fails before it sends anything: the exchange keeps the exception of the expression, with the one
        // of onCompletion as suppressed
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .split(method(MulticastOnCompletionThrowsTest.class, "fail"), new Strategy(true))
                            .to("mock:a")
                        .end()
                        .to("mock:start-result");
            }
        });
        context.start();

        Exchange out = template.asyncSend("direct:start", e -> e.getMessage().setBody("a,b")).get(10, TimeUnit.SECONDS);

        Throwable cause = out.getException();
        while (cause != null && !"expression".equals(cause.getMessage())) {
            cause = cause.getCause();
        }
        assertEquals("expression", cause != null ? cause.getMessage() : null, "the exception of the expression");
        assertSuppressedOnCompletion(out);
        assertEquals(0, getMockEndpoint("mock:a").getReceivedCounter());
    }

    public static Object fail(Exchange exchange) {
        throw new IllegalArgumentException("expression");
    }

    private static void assertSuppressedOnCompletion(Exchange out) {
        Throwable[] suppressed = out.getException().getSuppressed();
        assertEquals(1, suppressed.length);
        assertEquals("onCompletion", suppressed[0].getMessage());
    }
}
