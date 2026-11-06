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
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Channel;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.StepHook;
import org.apache.camel.spi.StepListener;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.processor.DelegateAsyncProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@link StepListener}s see each call of the processor of each step: each attempt of the error handler, the steps
 * without an error handler, the steps of doTry, doCatch and doFinally, and callbacks on another thread. They run with a
 * {@link StepHook}, and a listener that fails does not change the routing.
 */
class StepListenerTest extends ContextTestSupport {

    private final StepListenerRecorder recorder = new StepListenerRecorder();
    private final FailingListener failing = new FailingListener();
    private final Map<String, AtomicInteger> hookAsked = new ConcurrentHashMap<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> new Thread(r, "StepListenerWorker"));
    // the async step calls its callback on the worker thread after the listener saw process return
    private final CountDownLatch asyncReturned = new CountDownLatch(1);
    private final CountDownLatch gate = new CountDownLatch(1);
    private final IllegalStateException forced = new IllegalStateException("Forced");
    private final AtomicReference<Object> caught = new AtomicReference<>();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        // the failing listener first, so its failures come before the recorder is called
        context.getCamelContextExtension().addStepListener(failing);
        context.getCamelContextExtension().addStepListener(recorder);
        context.getCamelContextExtension().setStepHook(this::beforeStep);
        return context;
    }

    private long beforeStep(Exchange exchange, String nodeId) {
        int n = hookAsked.computeIfAbsent(nodeId, k -> new AtomicInteger()).incrementAndGet();
        if ("hookedStep".equals(nodeId) && n == 1) {
            exchange.setException(new IllegalArgumentException("Failed by the step hook"));
        }
        return StepHook.PROCEED;
    }

    @AfterEach
    void stopWorker() {
        gate.countDown();
        worker.shutdownNow();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:steps").routeId("steps")
                        .setHeader("greeting", constant("Hello")).id("setHeader")
                        .process(e -> e.getMessage().setBody("Bye")).id("process")
                        .to("mock:result").id("toResult");

                AtomicInteger flakyCalls = new AtomicInteger();
                from("direct:retry").routeId("retry")
                        .errorHandler(defaultErrorHandler().maximumRedeliveries(2).redeliveryDelay(0))
                        .process(e -> {
                            if (flakyCalls.incrementAndGet() <= 2) {
                                e.setException(new IllegalArgumentException("Flaky " + flakyCalls.get()));
                            }
                        }).id("flaky")
                        .to("mock:retried").id("retried");

                AtomicInteger throwingCalls = new AtomicInteger();
                from("direct:throwOnce").routeId("throwOnce")
                        .errorHandler(defaultErrorHandler().maximumRedeliveries(1).redeliveryDelay(0))
                        .process(new AsyncProcessorSupport() {
                            @Override
                            public boolean process(Exchange exchange, AsyncCallback callback) {
                                if (throwingCalls.incrementAndGet() == 1) {
                                    throw new IllegalStateException("Thrown once");
                                }
                                callback.done(true);
                                return true;
                            }
                        }).id("throwsOnce")
                        .to("mock:afterThrowOnce").id("afterThrowOnce");

                from("direct:noErrorHandler").routeId("noErrorHandler")
                        .errorHandler(noErrorHandler())
                        .to("mock:noEh1").id("noEh1")
                        .to("mock:noEh2").id("noEh2");

                from("direct:try").routeId("try")
                        .doTry().id("doTry")
                        .process(e -> {
                            throw new IllegalArgumentException("Forced in try");
                        }).id("inTry")
                        .doCatch(IllegalArgumentException.class).id("doCatch")
                        .to("mock:caught").id("inCatch")
                        .doFinally().id("doFinally")
                        .to("mock:finally").id("inFinally")
                        .end()
                        .to("mock:afterTry").id("afterTry");

                from("direct:async").routeId("async")
                        .process(new AsyncProcessorSupport() {
                            @Override
                            public boolean process(Exchange exchange, AsyncCallback callback) {
                                worker.submit(() -> {
                                    try {
                                        asyncReturned.await(10, TimeUnit.SECONDS);
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    }
                                    exchange.getMessage().setBody("Async");
                                    callback.done(false);
                                });
                                return false;
                            }
                        }).id("asyncStep")
                        .to("mock:afterAsync").id("afterAsync");

                from("direct:gated").routeId("gated")
                        .process(new AsyncProcessorSupport() {
                            @Override
                            public boolean process(Exchange exchange, AsyncCallback callback) {
                                worker.submit(() -> {
                                    try {
                                        gate.await(10, TimeUnit.SECONDS);
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    }
                                    callback.done(false);
                                });
                                return false;
                            }
                        }).id("gatedStep")
                        .to("mock:gated").id("toGated");

                from("direct:hooked").routeId("hooked")
                        .errorHandler(defaultErrorHandler().maximumRedeliveries(1).redeliveryDelay(0))
                        .to("mock:hooked").id("hookedStep");

                from("direct:listenerFails").routeId("listenerFails")
                        .process(e -> e.getMessage().setBody("A")).id("callFails")
                        .process(e -> e.getMessage().setBody(e.getMessage().getBody(String.class) + "B")).id("othersFail")
                        .to("mock:listenerFails").id("toListenerFails");

                from("direct:throwUnchanged").routeId("throwUnchanged")
                        .onException(IllegalStateException.class).handled(true)
                        .process(e -> caught.set(e.getProperty(Exchange.EXCEPTION_CAUGHT))).id("handler")
                        .end()
                        .process(new AsyncProcessorSupport() {
                            @Override
                            public boolean process(Exchange exchange, AsyncCallback callback) {
                                throw forced;
                            }
                        }).id("throwsUnchanged");
            }
        };
    }

    @Test
    void testEveryStepIsSeen() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye");

        template.sendBody("direct:steps", "Hello");

        assertMockEndpointsSatisfied();
        assertEquals(List.of(
                "call steps/setHeader", "callback steps/setHeader true", "return steps/setHeader true",
                "call steps/process", "callback steps/process true", "return steps/process true",
                "call steps/toResult", "callback steps/toResult true", "return steps/toResult true"),
                recorder.of("steps"));
    }

    @Test
    void testEachRedeliveryIsSeen() throws Exception {
        getMockEndpoint("mock:retried").expectedMessageCount(1);

        template.sendBody("direct:retry", "Hello");

        assertMockEndpointsSatisfied();
        List<String> flaky = new ArrayList<>(recorder.of("retry"));
        flaky.removeIf(e -> !e.contains("/flaky"));
        assertEquals(List.of(
                "call retry/flaky", "callback retry/flaky true", "return retry/flaky true",
                "call retry/flaky", "callback retry/flaky true", "return retry/flaky true",
                "call retry/flaky", "callback retry/flaky true", "return retry/flaky true"),
                flaky);
    }

    @Test
    void testProcessorThatThrowsIsSeenAndRedelivered() throws Exception {
        getMockEndpoint("mock:afterThrowOnce").expectedMessageCount(1);

        template.sendBody("direct:throwOnce", "Hello");

        assertMockEndpointsSatisfied();
        assertEquals(List.of(
                "call throwOnce/throwsOnce", "throw throwOnce/throwsOnce Thrown once",
                "call throwOnce/throwsOnce", "callback throwOnce/throwsOnce true", "return throwOnce/throwsOnce true",
                "call throwOnce/afterThrowOnce", "callback throwOnce/afterThrowOnce true",
                "return throwOnce/afterThrowOnce true"),
                recorder.of("throwOnce"));
    }

    @Test
    void testStepsWithoutErrorHandler() throws Exception {
        getMockEndpoint("mock:noEh2").expectedMessageCount(1);

        template.sendBody("direct:noErrorHandler", "Hello");

        assertMockEndpointsSatisfied();
        assertEquals(List.of(
                "call noErrorHandler/noEh1", "callback noErrorHandler/noEh1 true", "return noErrorHandler/noEh1 true",
                "call noErrorHandler/noEh2", "callback noErrorHandler/noEh2 true", "return noErrorHandler/noEh2 true"),
                recorder.of("noErrorHandler"));
    }

    @Test
    void testStepsOfDoTryDoCatchDoFinally() throws Exception {
        getMockEndpoint("mock:caught").expectedMessageCount(1);
        getMockEndpoint("mock:finally").expectedMessageCount(1);
        getMockEndpoint("mock:afterTry").expectedMessageCount(1);

        template.sendBody("direct:try", "Hello");

        assertMockEndpointsSatisfied();
        List<String> events = recorder.of("try");
        // the doTry step itself, which the intercept strategies skip, and each step inside it
        for (String step : List.of("doTry", "inTry", "inCatch", "inFinally", "afterTry")) {
            assertEquals(1, events.stream().filter(e -> e.equals("call try/" + step)).count(), step + " in " + events);
            assertEquals(1, events.stream().filter(e -> e.startsWith("callback try/" + step + " ")).count(),
                    step + " in " + events);
            assertEquals(1, events.stream().filter(e -> e.startsWith("return try/" + step + " ")).count(),
                    step + " in " + events);
        }
        assertTrue(events.indexOf("call try/doTry") < events.indexOf("call try/inTry"), events.toString());
        assertTrue(events.indexOf("call try/inTry") < events.indexOf("call try/inCatch"), events.toString());
        assertTrue(events.indexOf("call try/inCatch") < events.indexOf("call try/inFinally"), events.toString());
        assertTrue(events.indexOf("call try/inFinally") < events.indexOf("call try/afterTry"), events.toString());
    }

    @Test
    void testCallbackOnAnotherThread() throws Exception {
        // the async step calls its callback once the listener saw process return
        StepListener onReturn = new StepListener() {
            @Override
            public void onStepReturn(Object state, boolean doneSync) {
                asyncReturned.countDown();
            }
        };
        context.getCamelContextExtension().addStepListener(onReturn);
        getMockEndpoint("mock:afterAsync").expectedBodiesReceived("Async");

        Object out = template.requestBody("direct:async", "Hello");

        assertEquals("Async", out);
        assertMockEndpointsSatisfied();
        List<String> events = recorder.of("async");
        assertEquals(List.of("call async/asyncStep", "return async/asyncStep false", "callback async/asyncStep false"),
                events.subList(0, 3));
        assertEquals(Thread.currentThread().getName(), recorder.returnThreads.get("async/asyncStep"));
        assertEquals("StepListenerWorker", recorder.callbackThreads.get("async/asyncStep"));
    }

    @Test
    void testStepFailedByTheStepHookIsNotACall() throws Exception {
        getMockEndpoint("mock:hooked").expectedMessageCount(1);

        template.sendBody("direct:hooked", "Hello");

        assertMockEndpointsSatisfied();
        // the hook is asked for each attempt, and fails the first one before the processor is called
        assertEquals(2, hookAsked.get("hookedStep").get());
        assertEquals(List.of("call hooked/hookedStep", "callback hooked/hookedStep true", "return hooked/hookedStep true"),
                recorder.of("hooked"));
    }

    @Test
    void testFailingListenerDoesNotChangeTheRouting() throws Exception {
        getMockEndpoint("mock:listenerFails").expectedBodiesReceived("AB");

        template.sendBody("direct:listenerFails", "Hello");

        assertMockEndpointsSatisfied();
        // the other listener sees every event
        assertEquals(List.of(
                "call listenerFails/callFails", "callback listenerFails/callFails true",
                "return listenerFails/callFails true",
                "call listenerFails/othersFail", "callback listenerFails/othersFail true",
                "return listenerFails/othersFail true",
                "call listenerFails/toListenerFails", "callback listenerFails/toListenerFails true",
                "return listenerFails/toListenerFails true"),
                recorder.of("listenerFails"));
        // a listener whose onStepCall threw gets no more events for that call
        assertEquals(List.of("call callFails", "call othersFail", "callback othersFail", "return othersFail",
                "call toListenerFails", "callback toListenerFails", "return toListenerFails"),
                new ArrayList<>(failing.events));
    }

    @Test
    void testExceptionOfTheProcessorIsThrownOnUnchanged() throws Exception {
        template.sendBody("direct:throwUnchanged", "Hello");

        // the failing listener threw from onStepThrow, and the error handler got the exception of the processor
        assertSame(forced, caught.get());
        assertTrue(failing.events.contains("throw throwsUnchanged"), failing.events.toString());
        assertTrue(recorder.of("throwUnchanged").contains("throw throwUnchanged/throwsUnchanged Forced"),
                recorder.of("throwUnchanged").toString());
    }

    @Test
    void testRemovedListenerGetsTheCallbackOfACallItSawStart() throws Exception {
        StepListenerRecorder late = new StepListenerRecorder();
        CountDownLatch lateCalled = new CountDownLatch(1);
        // added after late, so late has recorded the return of the call when the gate is opened
        StepListener lateCall = new StepListener() {
            @Override
            public void onStepReturn(Object state, boolean doneSync) {
                lateCalled.countDown();
            }
        };
        context.getCamelContextExtension().addStepListener(late);
        context.getCamelContextExtension().addStepListener(lateCall);
        getMockEndpoint("mock:gated").expectedMessageCount(2);

        Future<Object> first = template.asyncRequestBody("direct:gated", "Hello");
        assertTrue(lateCalled.await(10, TimeUnit.SECONDS));
        context.getCamelContextExtension().removeStepListener(late);
        context.getCamelContextExtension().removeStepListener(lateCall);
        assertFalse(context.getCamelContextExtension().getStepListeners().contains(late));
        gate.countDown();
        first.get(10, TimeUnit.SECONDS);

        // the step that started before the removal, and none after it
        assertEquals(List.of("call gated/gatedStep", "return gated/gatedStep false", "callback gated/gatedStep false"),
                late.of("gated"));

        template.requestBody("direct:gated", "Hello");
        assertMockEndpointsSatisfied();
        assertEquals(3, late.of("gated").size());
    }

    @Test
    void testManyListenersInTheOrderTheyWereAdded() {
        List<StepListener> listeners = context.getCamelContextExtension().getStepListeners();
        assertEquals(List.of(failing, recorder), listeners);
        // the same instance twice is ignored
        context.getCamelContextExtension().addStepListener(recorder);
        assertEquals(List.of(failing, recorder), context.getCamelContextExtension().getStepListeners());
        assertThrows(UnsupportedOperationException.class, () -> listeners.add(recorder));
    }

    @Test
    void testTheWrapperIsBelowTheErrorHandlerAroundTheProcessor() {
        Route route = context.getRoute("steps");
        List<Channel> channels = StepListenerRecorder.channels(route);
        assertEquals(3, channels.size());
        for (Channel channel : channels) {
            Processor step = channel.getNextProcessor();
            Processor call = StepListenerRecorder.callOf(channel);
            assertNotSame(step, call);
            DelegateAsyncProcessor wrapper = assertInstanceOf(DelegateAsyncProcessor.class, call);
            assertEquals("StepListenerProcessor", wrapper.getClass().getSimpleName());
            assertSame(step, wrapper.getProcessor());
            assertEquals(step.toString(), wrapper.toString());
        }
        // navigating the route still finds the processors of the steps
        assertEquals(1, route.filter("toResult").size());
        assertSame(channels.get(2).getNextProcessor(), route.filter("toResult").get(0));
    }

    /**
     * A listener that fails: in onStepCall at the step callFails, and in the other methods at the steps othersFail and
     * throwsUnchanged. It records the events it gets.
     */
    static final class FailingListener implements StepListener {

        final Queue<String> events = new ConcurrentLinkedQueue<>();

        @Override
        public Object onStepCall(Exchange exchange, String routeId, String nodeId) {
            events.add("call " + nodeId);
            if ("callFails".equals(nodeId)) {
                throw new IllegalStateException("onStepCall failed");
            }
            return nodeId;
        }

        @Override
        public void onStepReturn(Object state, boolean doneSync) {
            events.add("return " + state);
            failAt(state);
        }

        @Override
        public void onStepThrow(Object state, Throwable cause) {
            events.add("throw " + state);
            failAt(state);
        }

        @Override
        public void onStepCallback(Object state, boolean doneSync) {
            events.add("callback " + state);
            failAt(state);
        }

        private static void failAt(Object state) {
            if ("othersFail".equals(state) || "throwsUnchanged".equals(state)) {
                throw new IllegalStateException("Listener failed at " + state);
            }
        }
    }
}
