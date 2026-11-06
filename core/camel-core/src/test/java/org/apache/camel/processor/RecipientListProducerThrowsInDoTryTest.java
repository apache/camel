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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RecipientListDefinition;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.ExchangeCompletedEvent;
import org.apache.camel.spi.CamelEvent.ExchangeFailedEvent;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.EventNotifierSupport;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A recipient of a recipient list in a doTry has no error handler around its producer. When the producer throws from
 * {@code process(exchange, callback)} instead of setting the exception on the exchange, the exception reached the task
 * of the recipient, which the reactive executor runs and which dropped it: the recipient list waited for good. The
 * exchange must go on as when the producer sets the exception, and be continued exactly once. Each send is bounded, so
 * a hang fails the test.
 */
class RecipientListProducerThrowsInDoTryTest extends ContextTestSupport {

    private final AtomicInteger calls = new AtomicInteger();

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    /**
     * What the producer does with the exchange and its callback.
     */
    private interface Behaviour {
        boolean process(Exchange exchange, AsyncCallback callback);
    }

    private final class TestEndpoint extends DefaultEndpoint {
        private final Behaviour behaviour;

        private TestEndpoint(String uri, Behaviour behaviour) {
            this.behaviour = behaviour;
            setEndpointUri(uri);
        }

        @Override
        public Producer createProducer() {
            return new DefaultAsyncProducer(this) {
                @Override
                public boolean process(Exchange exchange, AsyncCallback callback) {
                    calls.incrementAndGet();
                    return behaviour.process(exchange, callback);
                }
            };
        }

        @Override
        public Consumer createConsumer(Processor processor) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected String createEndpointUri() {
            return getEndpointUri();
        }
    }

    private static boolean sets(Exchange exchange, AsyncCallback callback) {
        exchange.setException(new IllegalStateException("failed"));
        callback.done(true);
        return true;
    }

    private static boolean throwsFailure(Exchange exchange, AsyncCallback callback) {
        throw new IllegalStateException("failed");
    }

    private interface Options {
        void apply(RecipientListDefinition<?> recipientList);
    }

    /**
     * The routes direct:{id} with a recipient list of test:{id} in a doTry, to mock:{id}-result, and a doCatch to
     * mock:{id}-caught.
     */
    private void addRoute(RouteBuilder b, String id, Options options) {
        RecipientListDefinition<?> recipientList = b.from("direct:" + id)
                .doTry()
                .recipientList(b.constant("test:" + id));
        options.apply(recipientList);
        recipientList.end()
                .to("mock:" + id + "-result")
                .endDoTry()
                .doCatch(IllegalStateException.class)
                .to("mock:" + id + "-caught")
                .end();
    }

    private void start(Options options, Behaviour behaviour) throws Exception {
        context.addEndpoint("test:sets", new TestEndpoint("test:sets", RecipientListProducerThrowsInDoTryTest::sets));
        context.addEndpoint("test:start", new TestEndpoint("test:start", behaviour));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                addRoute(this, "sets", options);
                addRoute(this, "start", options);
            }
        });
        context.start();
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
        Exception caught = out.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        return "exception=" + describe(out.getException()) + " caught=" + describe(caught)
               + " result=" + getMockEndpoint("mock:" + id + "-result").getReceivedCounter()
               + " caughtMock=" + getMockEndpoint("mock:" + id + "-caught").getReceivedCounter();
    }

    private static String describe(Throwable t) {
        // the message of a CamelExchangeException ends with the exchange, which differs between the routes
        return t == null ? "none" : t.getClass().getSimpleName() + ":" + t.getMessage().replaceAll("\\. Exchange\\[.*", "");
    }

    /**
     * The route whose producer sets the exception (direct:sets) and the one whose producer behaves as given
     * (direct:start) must do the same, and be done as many times.
     */
    private void assertSameAsSettingTheException(Options options, Behaviour behaviour, String expected)
            throws Exception {
        start(options, behaviour);
        AtomicInteger done = countDoneExchanges();
        Exchange reference = template.asyncSend("direct:sets", e -> e.getMessage().setBody("Hello")).get(10,
                TimeUnit.SECONDS);
        assertEquals(expected, outcome(reference, "sets"), "the producer that sets the exception");
        await().atMost(10, TimeUnit.SECONDS).until(() -> done.get() >= 1);
        int referenceDone = done.getAndSet(0);
        calls.set(0);

        Exchange out = template.asyncSend("direct:start", e -> e.getMessage().setBody("Hello")).get(10, TimeUnit.SECONDS);
        assertEquals(expected, outcome(out, "start"), "the route whose producer does not set the exception");
        await().atMost(10, TimeUnit.SECONDS).until(() -> done.get() >= referenceDone);
        // wait until nothing is in flight, so a second continuation that comes later is counted as well
        await().atMost(10, TimeUnit.SECONDS).until(() -> context.getInflightRepository().size() == 0);
        assertEquals(1, calls.get(), "the producer is called once");
        // and send one more exchange, so a second continuation queued before it is counted as well
        template.asyncSend("direct:sets", e -> e.getMessage().setBody("Hello")).get(10, TimeUnit.SECONDS);
        await().atMost(10, TimeUnit.SECONDS).until(() -> done.get() >= 2 * referenceDone);
        assertEquals(2 * referenceDone, done.get(),
                "the exchanges done, as with the producer that sets the exception (and once more for the later exchange)");
    }

    private static final String CAUGHT
            = "exception=none caught=IllegalStateException:failed result=0 caughtMock=1";

    private static final String CAUGHT_STOP_ON_EXCEPTION
            = "exception=none caught=CamelExchangeException:Multicast processing failed for number 0 result=0 caughtMock=1";

    @Test
    void producerThrows() throws Exception {
        assertSameAsSettingTheException(r -> {
        }, RecipientListProducerThrowsInDoTryTest::throwsFailure, CAUGHT);
    }

    @Test
    void producerThrowsSynchronous() throws Exception {
        assertSameAsSettingTheException(r -> r.synchronous(), RecipientListProducerThrowsInDoTryTest::throwsFailure,
                CAUGHT);
    }

    @Test
    void producerThrowsParallel() throws Exception {
        assertSameAsSettingTheException(r -> r.parallelProcessing(), RecipientListProducerThrowsInDoTryTest::throwsFailure,
                CAUGHT);
    }

    @Test
    void producerThrowsStopOnException() throws Exception {
        assertSameAsSettingTheException(r -> r.stopOnException(), RecipientListProducerThrowsInDoTryTest::throwsFailure,
                CAUGHT_STOP_ON_EXCEPTION);
    }

    @Test
    void producerThrowsParallelTimeout() throws Exception {
        // with a timeout the recipient list did not hang, but returned after the timeout without the failure
        assertSameAsSettingTheException(r -> r.parallelProcessing().timeout(5000),
                RecipientListProducerThrowsInDoTryTest::throwsFailure, CAUGHT);
    }

    @Test
    void producerThrowsShareUnitOfWork() throws Exception {
        assertSameAsSettingTheException(r -> r.shareUnitOfWork(), RecipientListProducerThrowsInDoTryTest::throwsFailure,
                CAUGHT);
    }

    @Test
    void producerCallsBackThenThrows() throws Exception {
        // the callback came first and continued the exchange: the exception is only logged, as before (control: same
        // on main)
        assertSameAsSettingTheException(r -> {
        }, (exchange, callback) -> {
            exchange.setException(new IllegalStateException("failed"));
            callback.done(true);
            throw new IllegalStateException("thrown after the callback");
        }, CAUGHT);
    }

    @Test
    void producerHandsOnCallbackThenThrows() throws Exception {
        // the callback was queued on the reactive executor before the producer threw: it comes first and continues the
        // exchange once, the takeover is only logged (control: same on main; a takeover without the guard would
        // continue the exchange twice)
        assertSameAsSettingTheException(r -> {
        }, (exchange, callback) -> {
            exchange.setException(new IllegalStateException("failed"));
            exchange.getContext().getCamelContextExtension().getReactiveExecutor().schedule(() -> callback.done(false));
            throw new IllegalStateException("thrown after handing on the callback");
        }, CAUGHT);
    }

    @Test
    void sedaQueueFull() throws Exception {
        // a real producer that throws from process(exchange, callback): the seda producer throws IllegalStateException
        // "Queue full" when its bounded queue is full (the default, neither blockWhenFull nor discardWhenFull)
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:seda")
                        .doTry()
                        .recipientList(constant("seda:full?size=1"))
                        .end()
                        .to("mock:seda-result")
                        .endDoTry()
                        .doCatch(IllegalStateException.class)
                        .to("mock:seda-caught")
                        .end();
            }
        });
        context.start();
        // fills the queue (no consumer)
        template.sendBody("seda:full?size=1", "first");

        Exchange out = template.asyncSend("direct:seda", e -> e.getMessage().setBody("Hello")).get(10, TimeUnit.SECONDS);
        assertEquals("exception=none caught=IllegalStateException:Queue full result=0 caughtMock=1", outcome(out, "seda"));
    }
}
