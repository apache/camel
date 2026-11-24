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
package org.apache.camel.sanitizer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.sanitizer.junit.CamelSanitizer;
import org.apache.camel.sanitizer.junit.ExpectSanitizerViolation;
import org.apache.camel.spi.StepListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rule R3, its first part: the value process returns is the value its callback is called with. Processors of a user
 * that break it are reported at their step, with the threads; correct asynchronous processors and Camel's own EIPs that
 * go on other threads, or hand the callback to the reactive executor (the channel of a step without an error handler),
 * are not.
 */
@CamelSanitizer
class SanitizerDoneSyncTest extends ContextTestSupport {

    private static final int MANY = 200;

    private final ExecutorService pool = Executors.newFixedThreadPool(4, r -> new Thread(r, "user-pool"));
    private final Recorder recorder = new Recorder("choiceNoErrorHandler");

    @AfterEach
    void shutdownPool() {
        pool.shutdownNow();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getCamelContextExtension().addStepListener(recorder);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:trueAfterAsync").routeId("prices")
                        .process(new BrokenUserCode.ReturnsTrueAfterAsyncDone()).id("lookup").to("mock:result");
                from("direct:trueThenCallback")
                        .process(new BrokenUserCode.ReturnsTrueThenCallsBack()).id("early").to("mock:result");
                from("direct:falseCallbackTrue")
                        .process(new BrokenUserCode.ReturnsFalseCallbackTrue()).id("worker").to("mock:result");
                from("direct:falseAfterSyncDone")
                        .process(new BrokenUserCode.ReturnsFalseAfterSyncDone()).id("sync").to("mock:result");

                // correct code, and Camel's EIPs that go on other threads or through the reactive executor
                from("direct:correctAsync").process(new BrokenUserCode.CorrectAsync(pool)).id("async").to("mock:result");
                from("direct:toDirect").to("direct:sub").id("toSub").to("mock:result");
                from("direct:sub").process(e -> e.getMessage().setHeader("sub", true));
                from("direct:toAsyncSub").to("direct:asyncSub").id("toAsyncSub").to("mock:result");
                // without an error handler, the channel of a step hands its callback to the reactive executor and
                // returns the value of its processor: the choice returns true (the value of the channel of its only
                // step), and its callback comes with false on the same thread (the case the contract allows)
                from("direct:noErrorHandler").errorHandler(noErrorHandler())
                        .choice().id("choiceNoErrorHandler")
                            .when(constant(true)).setHeader("sub", constant(true))
                        .end()
                        .to("mock:result");
                from("direct:asyncSub").process(new BrokenUserCode.CorrectAsync(pool)).to("log:asyncSub?level=OFF");
                from("direct:eips")
                        .threads(2).id("threads")
                        .split(body().tokenize(",")).parallelProcessing().id("split")
                        .process(new BrokenUserCode.CorrectAsync(pool))
                        .to("direct:sub")
                        .end()
                        .delay(1).asyncDelayed().id("delay")
                        .to("seda:out").id("toSeda")
                        .to("mock:result");
                from("seda:out").to("log:out?level=OFF");
            }
        };
    }

    @Test
    @ExpectSanitizerViolation(value = Violation.RETURNED_TRUE_CALLBACK_ON_OTHER_THREAD, step = "lookup")
    void returnedTrueWhileTheCallbackCameWithFalseOnAnotherThread() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBody("direct:trueAfterAsync", "price?");
        assertMockEndpointsSatisfied();

        SanitizerViolation v = awaitOne(Violation.RETURNED_TRUE_CALLBACK_ON_OTHER_THREAD);
        assertEquals("prices", v.getRouteId());
        assertEquals("lookup", v.getStepId());
        assertEquals(BrokenUserCode.ReturnsTrueAfterAsyncDone.class.getName(), v.getProcessor());
        String text = v.getText();
        assertTrue(text.startsWith("Exchange sanitizer: process returned true, and its callback was called on another thread"
                                   + " (R3)"),
                text);
        // the stack of the worker that called the callback starts at the code of the user
        assertTrue(text.contains("the callback was called with false: thread user-worker"), text);
        assertTrue(text.contains("at " + BrokenUserCode.ReturnsTrueAfterAsyncDone.class.getName() + ".lambda$process$"),
                text);
        assertTrue(text.contains("then process returned true: thread "), text);
        assertTrue(text.contains("must return the value its callback is called with (see AsyncProcessor)"), text);
        assertTrue(text.contains("Usual fix: when the work goes on another thread, return false"), text);
    }

    @Test
    @ExpectSanitizerViolation(value = {
            Violation.RETURNED_TRUE_BEFORE_CALLBACK, Violation.RETURNED_TRUE_CALLBACK_ON_OTHER_THREAD },
                              step = "early")
    void returnedTrueBeforeTheCallback() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBody("direct:trueThenCallback", "x");
        assertMockEndpointsSatisfied();

        Sanitizer sanitizer = Sanitizer.get(context);
        await().atMost(Duration.ofSeconds(10))
                .until(() -> sanitizer.getViolationCount(Violation.RETURNED_TRUE_BEFORE_CALLBACK)
                             + sanitizer.getViolationCount(Violation.RETURNED_TRUE_CALLBACK_ON_OTHER_THREAD) > 0);
        assertEquals(1, sanitizer.getViolations().size());
        assertTrue(sanitizer.getViolations().get(0).getText().contains("thread user-worker"),
                sanitizer.getViolations().get(0).getText());
    }

    @Test
    @ExpectSanitizerViolation(value = Violation.RETURNED_FALSE_CALLBACK_TRUE, step = "worker")
    void returnedFalseWhileTheCallbackCameWithTrue() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBody("direct:falseCallbackTrue", "x");
        assertMockEndpointsSatisfied();

        SanitizerViolation v = awaitOne(Violation.RETURNED_FALSE_CALLBACK_TRUE);
        assertEquals(BrokenUserCode.ReturnsFalseCallbackTrue.class.getName(), v.getProcessor());
        // whichever came first, the report has the thread of the callback
        assertTrue(v.getText().contains("thread user-worker"), v.getText());
        assertTrue(v.getText().contains("Usual fix: call the callback with the value process returns"), v.getText());
    }

    @Test
    @ExpectSanitizerViolation(value = Violation.RETURNED_FALSE_CALLBACK_TRUE, step = "sync")
    void returnedFalseAfterTheCallbackWithTrueOnTheCallingThread() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBody("direct:falseAfterSyncDone", "x");
        assertMockEndpointsSatisfied();

        SanitizerViolation v = awaitOne(Violation.RETURNED_FALSE_CALLBACK_TRUE);
        // a callback on the calling thread before the return keeps no stack; the return is reported with its own
        assertTrue(v.getText().contains("the callback was called with true: on the thread that called process, inside"
                                        + " process (no stack is kept for it)"),
                v.getText());
        assertTrue(v.getText().contains("then process returned false: thread "), v.getText());
    }

    @Test
    void correctProcessorsAndCamelEipsAreNotReported() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(5);
        template.sendBody("direct:correctAsync", "x");
        template.sendBody("direct:toDirect", "x");
        template.sendBody("direct:toAsyncSub", "x");
        template.sendBody("direct:noErrorHandler", "x");
        template.sendBody("direct:eips", "a,b,c");
        assertMockEndpointsSatisfied();

        assertNoViolations();
        // the choice without an error handler took the path the contract allows: true, and the callback with false on
        // the calling thread
        assertEquals(List.of("returned true", "callback false on the calling thread"), recorder.events);
    }

    @Test
    void manyExchangesAtOnceThroughCorrectCodeAreNotReported() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(3 * MANY);
        List<CompletableFuture<?>> sent = new ArrayList<>();
        for (int i = 0; i < MANY; i++) {
            sent.add(template.asyncSendBody("direct:correctAsync", i));
            sent.add(template.asyncSendBody("direct:toAsyncSub", i));
            sent.add(template.asyncSendBody("direct:eips", "a,b"));
        }
        CompletableFuture.allOf(sent.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);
        assertMockEndpointsSatisfied(60, TimeUnit.SECONDS);

        assertNoViolations();
    }

    @Test
    @ExpectSanitizerViolation(value = Violation.RETURNED_TRUE_CALLBACK_ON_OTHER_THREAD, step = "lookup")
    void eachOfManyBrokenCallsIsReportedOnce() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(MANY);
        List<CompletableFuture<?>> sent = new ArrayList<>();
        for (int i = 0; i < MANY; i++) {
            sent.add(template.asyncSendBody("direct:trueAfterAsync", i));
        }
        CompletableFuture.allOf(sent.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);
        assertMockEndpointsSatisfied(60, TimeUnit.SECONDS);

        Sanitizer sanitizer = Sanitizer.get(context);
        await().atMost(Duration.ofSeconds(10))
                .until(() -> sanitizer.getViolationCount(Violation.RETURNED_TRUE_CALLBACK_ON_OTHER_THREAD) == MANY);
        assertEquals(MANY, sanitizer.getViolations().size());
    }

    @Test
    void offWithoutTheCheckThreads() throws Exception {
        // a context that checks R1 and R2 only does not install the listener of R3
        CamelContext other = createCamelContext();
        try {
            other.getPropertiesComponent().addOverrideProperty("camel.sanitizer.checks", "callback,commit");
            other.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start").process(new BrokenUserCode.ReturnsTrueAfterAsyncDone());
                }
            });
            other.start();
            Sanitizer sanitizer = Sanitizer.get(other);
            assertNotNull(sanitizer);
            assertFalse(sanitizer.isCheckingThreads());
            assertTrue(other.getCamelContextExtension().getStepListeners().stream()
                    .noneMatch(SanitizerDoneSyncListener.class::isInstance));
            Exchange out = other.createProducerTemplate().send("direct:start", e -> e.getMessage().setBody("x"));
            assertNull(out.getException());
            assertEquals(0, sanitizer.getViolations().size());
        } finally {
            other.stop();
        }
    }

    /**
     * Records the return and the first callback of the calls of one step, with whether the callback came on the thread
     * that called process.
     */
    private static final class Recorder implements StepListener {

        private final String step;
        private final List<String> events = new CopyOnWriteArrayList<>();

        Recorder(String step) {
            this.step = step;
        }

        @Override
        public Object onStepCall(Exchange exchange, String routeId, String nodeId) {
            return step.equals(nodeId) ? Thread.currentThread() : null;
        }

        @Override
        public void onStepReturn(Object state, boolean doneSync) {
            if (state != null) {
                events.add("returned " + doneSync);
            }
        }

        @Override
        public void onStepCallback(Object state, boolean doneSync) {
            if (state != null) {
                events.add("callback " + doneSync + (state == Thread.currentThread() ? " on the calling thread" : ""));
            }
        }
    }

    private void assertNoViolations() {
        Sanitizer sanitizer = Sanitizer.get(context);
        assertNotNull(sanitizer, "the sanitizer is installed for a test marked with @CamelSanitizer");
        assertTrue(sanitizer.isCheckingThreads(), "rule R3 runs in a test");
        assertTrue(sanitizer.getCallCount() > 0);
        assertEquals(List.of(), sanitizer.getViolations());
    }

    private SanitizerViolation awaitOne(Violation kind) {
        Sanitizer sanitizer = Sanitizer.get(context);
        assertNotNull(sanitizer, "the sanitizer is installed for a test marked with @CamelSanitizer");
        await().atMost(Duration.ofSeconds(10)).until(() -> sanitizer.getViolationCount(kind) > 0);
        List<SanitizerViolation> found = sanitizer.getViolations().stream().filter(v -> v.getKind() == kind).toList();
        assertEquals(1, found.size(), found::toString);
        assertEquals(1, sanitizer.getViolations().size(), () -> sanitizer.getViolations().toString());
        return found.get(0);
    }
}
