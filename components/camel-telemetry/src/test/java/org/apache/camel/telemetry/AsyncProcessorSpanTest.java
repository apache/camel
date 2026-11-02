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
package org.apache.camel.telemetry;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.telemetry.mock.MockSpanAdapter;
import org.apache.camel.telemetry.mock.MockTrace;
import org.apache.camel.telemetry.mock.MockTracer;
import org.apache.camel.test.junit6.ExchangeTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The span of a step that calls back from another thread (an asynchronous delay, the threads EIP, a bean that returns a
 * CompletableFuture) ends before the next step of the route starts, so the next steps are children of the span that
 * encloses the step (the route span, or the span of an EIP such as a choice) and not of the asynchronous step.
 */
class AsyncProcessorSpanTest extends ExchangeTestSupport {

    MockTracer mockTracer;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        this.mockTracer = new MockTracer();
        CamelContextAware.trySetCamelContext(mockTracer, context);
        mockTracer.init(context);
        context.getRegistry().bind("futureBean", new FutureBean());
        return context;
    }

    @Test
    void stepsAfterAsyncDelayAreChildrenOfTheRoute() {
        checkStepsAfter("direct:delay", "async-delay", "delay");
    }

    @Test
    void stepsAfterThreadsAreChildrenOfTheRoute() {
        checkStepsAfter("direct:threads", "threads-threads", "threads");
    }

    @Test
    void stepsAfterBeanReturningFutureAreChildrenOfTheRoute() {
        checkStepsAfter("direct:future", "future-bean", "future");
    }

    @Test
    void stepsAfterSyncDelayAreChildrenOfTheRoute() {
        // control: a delay that completes on the same thread (passes without the fix as well)
        checkStepsAfter("direct:syncdelay", "sync-delay", "sync");
    }

    @Test
    void stepsAfterAsyncDelayInsideChoiceAreChildrenOfTheChoice() {
        List<Span> spans = send("direct:choice");
        MockSpanAdapter route = SpanTestSupport.getSpan(spans, "direct://choice", Op.EVENT_RECEIVED);
        MockSpanAdapter choice = span(spans, "choice-choice");
        MockSpanAdapter delay = span(spans, "innerDelay-delay");
        MockSpanAdapter inner = span(spans, "inner-setHeader");
        MockSpanAdapter after = span(spans, "after-setHeader");
        allDone(spans);
        assertEquals(route.getTag("spanid"), choice.getTag("parentSpan"));
        assertEquals(choice.getTag("spanid"), delay.getTag("parentSpan"));
        assertEquals(choice.getTag("spanid"), inner.getTag("parentSpan"));
        assertEquals(route.getTag("spanid"), after.getTag("parentSpan"));
        endsBeforeStart(delay, inner);
        endsBeforeStart(choice, after);
    }

    @Test
    void calledRouteWithAsyncDelayEndsItsOwnSpans() {
        List<Span> spans = send("direct:caller");
        MockSpanAdapter caller = SpanTestSupport.getSpan(spans, "direct://caller", Op.EVENT_RECEIVED);
        MockSpanAdapter send = SpanTestSupport.getSpan(spans, "direct://called", Op.EVENT_SENT);
        MockSpanAdapter called = SpanTestSupport.getSpan(spans, "direct://called", Op.EVENT_RECEIVED);
        MockSpanAdapter calledDelay = span(spans, "calledDelay-delay");
        MockSpanAdapter calledStep = span(spans, "calledStep-setHeader");
        MockSpanAdapter callerStep = span(spans, "callerStep-setHeader");
        allDone(spans);
        assertEquals(caller.getTag("spanid"), send.getTag("parentSpan"));
        assertEquals(send.getTag("spanid"), called.getTag("parentSpan"));
        assertEquals(called.getTag("spanid"), calledDelay.getTag("parentSpan"));
        assertEquals(called.getTag("spanid"), calledStep.getTag("parentSpan"));
        assertEquals(caller.getTag("spanid"), callerStep.getTag("parentSpan"));
        // each span is ended by its own step: the called route and the send end after the steps they contain
        endsBeforeStart(calledDelay, calledStep);
        endsNotBefore(called, calledStep);
        endsNotBefore(send, called);
        endsBeforeStart(send, callerStep);
        endsNotBefore(caller, callerStep);
    }

    @Test
    void recipientListToRoutesWithAsyncDelayEndsAllSpans() {
        List<Span> spans = send("direct:recipients");
        // the template's send, the route, the recipient list, two sends, two called routes, two delays and two steps
        assertEquals(11, spans.size(), "Spans: " + spans);
        allDone(spans);
        MockSpanAdapter route = SpanTestSupport.getSpan(spans, "direct://recipients", Op.EVENT_RECEIVED);
        MockSpanAdapter recipientList = span(spans, "recipients-recipientList");
        assertEquals(route.getTag("spanid"), recipientList.getTag("parentSpan"));
        for (String step : List.of("rl1Step-setHeader", "rl2Step-setHeader")) {
            MockSpanAdapter parent = byId(spans, span(spans, step).getTag("parentSpan"));
            assertEquals(Op.EVENT_RECEIVED.toString(), parent.getTag("op"),
                    "The step " + step + " is not a child of its route");
        }
    }

    @Test
    void failureAfterAsyncDelayIsOnlyOnTheFailingStep() {
        assertThrows(CamelExecutionException.class, () -> template.sendBody("direct:fail", "my-body"));
        List<Span> spans = spans();
        MockSpanAdapter route = SpanTestSupport.getSpan(spans, "direct://fail", Op.EVENT_RECEIVED);
        MockSpanAdapter delay = span(spans, "failDelay-delay");
        MockSpanAdapter failing = span(spans, "failStep-throwException");
        allDone(spans);
        assertEquals(route.getTag("spanid"), failing.getTag("parentSpan"));
        assertEquals("true", failing.getTag(TagConstants.ERROR));
        assertNotEquals("true", delay.getTag(TagConstants.ERROR));
    }

    private void checkStepsAfter(String uri, String asyncStep, String prefix) {
        List<Span> spans = send(uri);
        MockSpanAdapter route = SpanTestSupport.getSpan(spans, uri.replace(":", "://"), Op.EVENT_RECEIVED);
        MockSpanAdapter async = span(spans, asyncStep);
        MockSpanAdapter step2 = span(spans, prefix + "Step2-setHeader");
        MockSpanAdapter step3 = span(spans, prefix + "Step3-setHeader");
        allDone(spans);
        assertEquals(route.getTag("spanid"), async.getTag("parentSpan"));
        assertEquals(route.getTag("spanid"), step2.getTag("parentSpan"));
        assertEquals(route.getTag("spanid"), step3.getTag("parentSpan"));
        endsBeforeStart(async, step2);
    }

    private List<Span> send(String uri) {
        template.sendBody(uri, "my-body");
        return spans();
    }

    private List<Span> spans() {
        Map<String, MockTrace> traces = mockTracer.traces();
        assertEquals(1, traces.size());
        return traces.values().iterator().next().spans();
    }

    private static MockSpanAdapter span(List<Span> spans, String component) {
        MockSpanAdapter answer = null;
        for (Span s : spans) {
            MockSpanAdapter msa = (MockSpanAdapter) s;
            if (component.equals(msa.getTag(TagConstants.COMPONENT))
                    && Op.EVENT_PROCESS.toString().equals(msa.getTag("op"))) {
                answer = msa;
            }
        }
        assertNotNull(answer, "No processor span " + component + " in " + spans);
        return answer;
    }

    private static MockSpanAdapter byId(List<Span> spans, String spanId) {
        for (Span s : spans) {
            MockSpanAdapter msa = (MockSpanAdapter) s;
            if (spanId != null && spanId.equals(msa.getTag("spanid"))) {
                return msa;
            }
        }
        throw new IllegalArgumentException("No span with id " + spanId);
    }

    private static void allDone(List<Span> spans) {
        for (Span s : spans) {
            assertEquals("true", ((MockSpanAdapter) s).getTag("isDone"), "Span not ended: " + s);
        }
    }

    private static void endsBeforeStart(MockSpanAdapter first, MockSpanAdapter next) {
        long end = Long.parseLong(first.getTag("endTimestamp"));
        long start = Long.parseLong(next.getTag("initTimestamp"));
        assertTrue(end <= start, "The span " + first.tags() + " ends after the span " + next.tags() + " starts");
    }

    private static void endsNotBefore(MockSpanAdapter outer, MockSpanAdapter inner) {
        long outerEnd = Long.parseLong(outer.getTag("endTimestamp"));
        long innerEnd = Long.parseLong(inner.getTag("endTimestamp"));
        assertTrue(outerEnd >= innerEnd, "The span " + outer.tags() + " ends before the span " + inner.tags());
    }

    public static class FutureBean {
        public CompletableFuture<String> call(String body) {
            // completes on another thread, after the bean has returned
            return CompletableFuture.supplyAsync(() -> body + "-reply",
                    CompletableFuture.delayedExecutor(10, TimeUnit.MILLISECONDS));
        }
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // a delay is asynchronous by default
                from("direct:delay").routeId("delayRoute")
                        .delay(10).id("async")
                        .setHeader("step2", constant("2")).id("delayStep2")
                        .setHeader("step3", constant("3")).id("delayStep3");

                from("direct:threads").routeId("threadsRoute")
                        .threads(1).id("threads")
                        .setHeader("step2", constant("2")).id("threadsStep2")
                        .setHeader("step3", constant("3")).id("threadsStep3");

                from("direct:future").routeId("futureRoute")
                        .bean("futureBean", "call").id("future")
                        .setHeader("step2", constant("2")).id("futureStep2")
                        .setHeader("step3", constant("3")).id("futureStep3");

                from("direct:syncdelay").routeId("syncDelayRoute")
                        .delay(10).syncDelayed().id("sync")
                        .setHeader("step2", constant("2")).id("syncStep2")
                        .setHeader("step3", constant("3")).id("syncStep3");

                // the choice is built in two statements: in one chain, end() after delay() does not end the choice
                RouteDefinition choiceRoute = from("direct:choice").routeId("choiceRoute");
                choiceRoute.choice().id("choice")
                        .when(constant(true))
                        .delay(10).id("innerDelay")
                        .setHeader("inner", constant("x")).id("inner");
                choiceRoute.setHeader("after", constant("y")).id("after");

                from("direct:caller").routeId("callerRoute")
                        .to("direct:called")
                        .setHeader("caller", constant("x")).id("callerStep");

                from("direct:called").routeId("calledRoute")
                        .delay(10).id("calledDelay")
                        .setHeader("called", constant("y")).id("calledStep");

                from("direct:recipients").routeId("recipientsRoute")
                        .recipientList(constant("direct:rl1,direct:rl2")).id("recipients");

                from("direct:rl1").routeId("rl1Route")
                        .delay(10).id("rl1Delay")
                        .setHeader("rl", constant("1")).id("rl1Step");

                from("direct:rl2").routeId("rl2Route")
                        .delay(10).id("rl2Delay")
                        .setHeader("rl", constant("2")).id("rl2Step");

                from("direct:fail").routeId("failRoute")
                        .delay(10).id("failDelay")
                        .throwException(new IllegalStateException("fail")).id("failStep");
            }
        };
    }

}
