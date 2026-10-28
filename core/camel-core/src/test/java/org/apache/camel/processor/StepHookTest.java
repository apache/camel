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
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.DisabledAware;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.StepHook;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@link StepHook} is asked before every step of a route, inside the error handler of the step when it has one, and
 * can delay or fail the step (CAMEL-26338).
 */
class StepHookTest extends ContextTestSupport {

    private static final String FAILED = "Failed by the step hook";

    // the node ids the hook was asked for, in order
    private final Queue<String> asked = new ConcurrentLinkedQueue<>();
    // per node id: how many times the hook fails the step (-1: always), or a delay
    private final Map<String, Integer> failures = new ConcurrentHashMap<>();
    private final Map<String, Long> delays = new ConcurrentHashMap<>();
    private final TestHook hook = new TestHook();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getCamelContextExtension().setStepHook(hook);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:steps").routeId("steps")
                        .setHeader("greeting", constant("Hello")).id("setHeader")
                        .log("${body}").id("log")
                        .process(e -> e.getMessage().setBody("Bye")).id("process")
                        .to("mock:result").id("toResult");

                from("direct:onException").routeId("onException")
                        .onException(IllegalStateException.class).handled(true).to("mock:handled").id("toHandled").end()
                        .to("mock:failing").id("failing")
                        .to("mock:afterFailing").id("afterFailing");

                from("direct:deadLetter").routeId("deadLetter")
                        .errorHandler(deadLetterChannel("mock:dead").maximumRedeliveries(2).redeliveryDelay(0))
                        .to("mock:flaky").id("flaky");

                from("direct:realFailure").routeId("realFailure")
                        .errorHandler(deadLetterChannel("mock:realDead").maximumRedeliveries(2).redeliveryDelay(0))
                        .process(e -> {
                            throw new IllegalStateException(FAILED);
                        }).id("realFlaky");

                from("direct:retry").routeId("retry")
                        .errorHandler(defaultErrorHandler().maximumRedeliveries(2).redeliveryDelay(0))
                        .to("mock:retried").id("retried");

                from("direct:try").routeId("try")
                        .doTry()
                            .to("mock:try").id("inTry")
                        .doCatch(IllegalStateException.class)
                            .to("mock:caught").id("caught")
                        .end()
                        .to("mock:afterTry").id("afterTry");

                from("direct:noErrorHandler").routeId("noErrorHandler")
                        .errorHandler(noErrorHandler())
                        .to("mock:noEh1").id("noEh1")
                        .to("mock:noEh2").id("noEh2");

                from("direct:split").routeId("split")
                        .onException(IllegalStateException.class).handled(true).to("mock:splitHandled").end()
                        .split(body()).id("splitStep")
                            .to("mock:splitItem").id("splitItem")
                        .end()
                        .to("mock:afterSplit").id("afterSplit");

                from("direct:multicast").routeId("multicast")
                        .errorHandler(deadLetterChannel("mock:multicastDead").maximumRedeliveries(2).redeliveryDelay(0))
                        .multicast().id("multicastStep")
                            .to("mock:mc1").id("mc1")
                            .to("mock:mc2").id("mc2")
                        .end()
                        .to("mock:afterMulticast").id("afterMulticast");

                from("direct:choice").routeId("choice")
                        .onException(IllegalStateException.class).handled(true).to("mock:choiceHandled").end()
                        .choice().id("choiceStep")
                            .when(header("to").isEqualTo("a"))
                                .to("mock:a").id("toA")
                            .otherwise()
                                .to("mock:other").id("toOther")
                        .end()
                        .to("mock:afterChoice").id("afterChoice");

                from("direct:dynamic").routeId("dynamic")
                        .errorHandler(deadLetterChannel("mock:dynamicDead").maximumRedeliveries(1).redeliveryDelay(0))
                        .recipientList(constant("mock:rl1,mock:rl2")).id("recipientList")
                        .routingSlip(constant("mock:rs1,mock:rs2")).id("routingSlip")
                        .dynamicRouter(method(StepHookTest.class, "slip")).id("dynamicRouter")
                        .to("mock:afterDynamic").id("afterDynamic");

                from("direct:delay").routeId("delay")
                        .to("mock:beforeDelay").id("beforeDelay")
                        .process(e -> e.getMessage().setHeader("stepThread", Thread.currentThread().getName()))
                        .id("delayed")
                        .to("mock:afterDelay").id("afterDelay");

                from("direct:disabled").routeId("disabled")
                        // "Hello" cannot be converted to an integer: this step fails unless it is disabled
                        .convertBodyTo(Integer.class).id("disabledStep")
                        .to("mock:disabled").id("toDisabled");
            }
        };
    }

    @Test
    void testHookAskedForEachStep() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye");
        getMockEndpoint("mock:result").expectedHeaderReceived("greeting", "Hello");
        template.sendBody("direct:steps", "Hello");
        assertMockEndpointsSatisfied();
        assertEquals(List.of("setHeader", "log", "process", "toResult"), new ArrayList<>(asked));
    }

    @Test
    void testFailureIsHandledByOnException() throws Exception {
        failures.put("failing", -1);
        getMockEndpoint("mock:handled").expectedBodiesReceived("Hello");
        getMockEndpoint("mock:failing").expectedMessageCount(0);
        getMockEndpoint("mock:afterFailing").expectedMessageCount(0);

        template.sendBody("direct:onException", "Hello");

        assertMockEndpointsSatisfied();
        Exception caught = getMockEndpoint("mock:handled").getReceivedExchanges().get(0)
                .getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        assertEquals(FAILED, caught.getMessage());
        // no redelivery: asked once, and the step inside onException was asked too
        assertEquals(List.of("failing", "toHandled"), new ArrayList<>(asked));
    }

    @Test
    void testFailureIsRedeliveredAndDeadLettered() throws Exception {
        failures.put("flaky", -1);
        MockEndpoint dead = getMockEndpoint("mock:dead");
        dead.expectedBodiesReceived("Hello");
        getMockEndpoint("mock:flaky").expectedMessageCount(0);

        template.sendBody("direct:deadLetter", "Hello");

        assertMockEndpointsSatisfied();
        Exception caught = dead.getReceivedExchanges().get(0).getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        assertEquals(FAILED, caught.getMessage());
        // the first attempt and the two redeliveries each asked the hook
        assertEquals(List.of("flaky", "flaky", "flaky"), new ArrayList<>(asked));
    }

    @Test
    void testFailureLooksLikeAFailureOfTheProcessor() throws Exception {
        failures.put("flaky", -1);
        getMockEndpoint("mock:dead").expectedMessageCount(1);
        getMockEndpoint("mock:realDead").expectedMessageCount(1);

        template.sendBody("direct:deadLetter", "Hello");
        template.sendBody("direct:realFailure", "Hello");

        assertMockEndpointsSatisfied();
        // the dead letter channel gets the same message whether the hook or the processor failed
        Exchange byHook = getMockEndpoint("mock:dead").getReceivedExchanges().get(0);
        Exchange byProcessor = getMockEndpoint("mock:realDead").getReceivedExchanges().get(0);
        assertEquals(byProcessor.getMessage().getHeaders(), byHook.getMessage().getHeaders());
        assertEquals(byProcessor.getMessage().getBody(), byHook.getMessage().getBody());
        assertEquals(byProcessor.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class).getMessage(),
                byHook.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class).getMessage());
    }

    @Test
    void testRedeliveryAsksTheHookAgain() throws Exception {
        // fails the first attempt and the first redelivery, then lets the step run
        failures.put("retried", 2);
        MockEndpoint retried = getMockEndpoint("mock:retried");
        retried.expectedBodiesReceived("Hello");
        retried.expectedHeaderReceived(Exchange.REDELIVERY_COUNTER, 2);

        template.sendBody("direct:retry", "Hello");

        assertMockEndpointsSatisfied();
        assertEquals(List.of("retried", "retried", "retried"), new ArrayList<>(asked));
    }

    @Test
    void testFailureIsCaughtByDoCatch() throws Exception {
        failures.put("inTry", -1);
        getMockEndpoint("mock:try").expectedMessageCount(0);
        getMockEndpoint("mock:caught").expectedBodiesReceived("Hello");
        getMockEndpoint("mock:afterTry").expectedBodiesReceived("Hello");

        template.sendBody("direct:try", "Hello");

        assertMockEndpointsSatisfied();
        Exception caught = getMockEndpoint("mock:caught").getReceivedExchanges().get(0)
                .getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        assertEquals(FAILED, caught.getMessage());
        assertEquals(1, count("inTry"));
        assertEquals(1, count("caught"));
        assertEquals(1, count("afterTry"));
    }

    @Test
    void testNoErrorHandlerFailsTheExchange() throws Exception {
        failures.put("noEh1", -1);
        getMockEndpoint("mock:noEh1").expectedMessageCount(0);
        getMockEndpoint("mock:noEh2").expectedMessageCount(0);

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:noErrorHandler", "Hello"));

        assertInstanceOf(IllegalStateException.class, e.getCause());
        assertEquals(FAILED, e.getCause().getMessage());
        assertMockEndpointsSatisfied();
        // no error handler, so no redelivery: the channel asked the hook once
        assertEquals(List.of("noEh1"), new ArrayList<>(asked));
    }

    @Test
    void testNoErrorHandlerSteps() throws Exception {
        getMockEndpoint("mock:noEh1").expectedBodiesReceived("Hello");
        getMockEndpoint("mock:noEh2").expectedBodiesReceived("Hello");
        template.sendBody("direct:noErrorHandler", "Hello");
        assertMockEndpointsSatisfied();
        assertEquals(List.of("noEh1", "noEh2"), new ArrayList<>(asked));
    }

    @Test
    void testStepsInsideSplit() throws Exception {
        failures.put("splitItem#b", -1);
        getMockEndpoint("mock:splitItem").expectedBodiesReceived("a", "c");
        getMockEndpoint("mock:splitHandled").expectedBodiesReceived("b");
        getMockEndpoint("mock:afterSplit").expectedMessageCount(1);

        template.sendBody("direct:split", List.of("a", "b", "c"));

        assertMockEndpointsSatisfied();
        assertEquals(1, count("splitStep"));
        assertEquals(3, count("splitItem"));
        assertEquals(1, count("afterSplit"));
    }

    @Test
    void testStepsInsideMulticast() throws Exception {
        failures.put("mc2", -1);
        getMockEndpoint("mock:mc1").expectedBodiesReceived("Hello");
        getMockEndpoint("mock:mc2").expectedMessageCount(0);
        getMockEndpoint("mock:multicastDead").expectedBodiesReceived("Hello");

        template.sendBody("direct:multicast", "Hello");

        assertMockEndpointsSatisfied();
        assertEquals(1, count("multicastStep"));
        assertEquals(1, count("mc1"));
        // the error handler that the multicast keeps for each output redelivers the failed output, which asks again
        assertEquals(3, count("mc2"));
    }

    @Test
    void testStepsInsideChoice() throws Exception {
        failures.put("toA", -1);
        getMockEndpoint("mock:a").expectedMessageCount(0);
        getMockEndpoint("mock:choiceHandled").expectedBodiesReceived("Hello");
        getMockEndpoint("mock:other").expectedBodiesReceived("World");
        getMockEndpoint("mock:afterChoice").expectedBodiesReceived("World");

        template.sendBodyAndHeader("direct:choice", "Hello", "to", "a");
        template.sendBodyAndHeader("direct:choice", "World", "to", "b");

        assertMockEndpointsSatisfied();
        assertEquals(2, count("choiceStep"));
        assertEquals(1, count("toA"));
        assertEquals(1, count("toOther"));
        assertEquals(1, count("afterChoice"));
    }

    @Test
    void testRecipientListRoutingSlipDynamicRouter() throws Exception {
        getMockEndpoint("mock:rl1").expectedBodiesReceived("Hello");
        getMockEndpoint("mock:rl2").expectedBodiesReceived("Hello");
        getMockEndpoint("mock:rs1").expectedBodiesReceived("Hello");
        getMockEndpoint("mock:rs2").expectedBodiesReceived("Hello");
        getMockEndpoint("mock:dr1").expectedBodiesReceived("Hello");
        getMockEndpoint("mock:afterDynamic").expectedBodiesReceived("Hello");

        template.sendBody("direct:dynamic", "Hello");

        assertMockEndpointsSatisfied();
        // the error handlers these EIPs keep for each recipient are not the error handler of a step, so they do not ask
        assertEquals(List.of("recipientList", "routingSlip", "dynamicRouter", "afterDynamic"), new ArrayList<>(asked));
    }

    @Test
    void testFailureOfRecipientListIsRedelivered() throws Exception {
        failures.put("recipientList", 1);
        getMockEndpoint("mock:rl1").expectedBodiesReceived("Hello");
        getMockEndpoint("mock:rl2").expectedBodiesReceived("Hello");
        getMockEndpoint("mock:dynamicDead").expectedMessageCount(0);

        template.sendBody("direct:dynamic", "Hello");

        assertMockEndpointsSatisfied();
        assertEquals(2, count("recipientList"));
        assertEquals(1, count("afterDynamic"));
    }

    public static String slip(Exchange exchange) {
        // one endpoint, then the end of the dynamic router
        if (exchange.getProperty("dynamicRouterDone") != null) {
            return null;
        }
        exchange.setProperty("dynamicRouterDone", true);
        return "mock:dr1";
    }

    @Test
    void testDelayWaitsOnTheThreadThatRunsTheStep() throws Exception {
        delays.put("delayed", 5L);
        MockEndpoint after = getMockEndpoint("mock:afterDelay");
        after.expectedBodiesReceived("Hello");

        template.sendBody("direct:delay", "Hello");

        assertMockEndpointsSatisfied();
        // the thread that runs the route waited for the delay, as for a slow step, and then ran the step
        String stepThread = after.getReceivedExchanges().get(0).getMessage().getHeader("stepThread", String.class);
        assertEquals(Thread.currentThread().getName(), stepThread);
        assertEquals(List.of("beforeDelay", "delayed", "afterDelay"), new ArrayList<>(asked));
    }

    @Test
    void testTransactedDelayWaitsOnTheThread() throws Exception {
        delays.put("delayed", 1L);
        MockEndpoint after = getMockEndpoint("mock:afterDelay");
        after.expectedBodiesReceived("Hello");

        Exchange exchange = createExchangeWithBody("Hello");
        exchange.getExchangeExtension().setTransacted(true);
        template.send("direct:delay", exchange);

        assertMockEndpointsSatisfied();
        assertNull(exchange.getException());
        String stepThread = after.getReceivedExchanges().get(0).getMessage().getHeader("stepThread", String.class);
        assertEquals(Thread.currentThread().getName(), stepThread);
    }

    @Test
    void testReservedValueFailsTheStep() throws Exception {
        delays.put("flaky", -5L);
        getMockEndpoint("mock:flaky").expectedMessageCount(0);
        MockEndpoint dead = getMockEndpoint("mock:dead");
        dead.expectedMessageCount(1);

        template.sendBody("direct:deadLetter", "Hello");

        // a failure of the step: redelivered (the hook asked again each time) and dead-lettered
        assertMockEndpointsSatisfied();
        Exception caught = dead.getReceivedExchanges().get(0).getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        assertInstanceOf(IllegalArgumentException.class, caught);
        assertEquals("StepHook.beforeStep returned -5: negative values are reserved", caught.getMessage());
        assertEquals(3, count("flaky"));
    }

    @Test
    void testTransactedFailureInsideDoTry() throws Exception {
        failures.put("inTry", -1);
        getMockEndpoint("mock:caught").expectedBodiesReceived("Hello");
        getMockEndpoint("mock:afterTry").expectedBodiesReceived("Hello");

        Exchange exchange = createExchangeWithBody("Hello");
        exchange.getExchangeExtension().setTransacted(true);
        template.send("direct:try", exchange);

        assertMockEndpointsSatisfied();
        assertNull(exchange.getException());
        assertEquals(1, count("inTry"));
    }

    @Test
    void testDisabledStepIsNotAsked() throws Exception {
        ((DisabledAware) context.getProcessor("disabledStep")).setDisabled(true);
        getMockEndpoint("mock:disabled").expectedBodiesReceived("Hello");

        template.sendBody("direct:disabled", "Hello");

        assertMockEndpointsSatisfied();
        assertEquals(List.of("toDisabled"), new ArrayList<>(asked));
    }

    @Test
    void testNoHook() throws Exception {
        context.getCamelContextExtension().setStepHook(null);
        failures.put("flaky", -1);
        getMockEndpoint("mock:flaky").expectedBodiesReceived("Hello");
        getMockEndpoint("mock:dead").expectedMessageCount(0);
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye");

        template.sendBody("direct:deadLetter", "Hello");
        template.sendBody("direct:steps", "Hello");

        assertMockEndpointsSatisfied();
        assertTrue(asked.isEmpty(), asked::toString);

        // installed again, it is asked by the routes that already run
        context.getCamelContextExtension().setStepHook(hook);
        template.sendBody("direct:steps", "Hello");
        assertEquals(List.of("setHeader", "log", "process", "toResult"), new ArrayList<>(asked));
    }

    @Test
    void testHookThatThrowsFailsTheStep() throws Exception {
        failures.put("failing", -2);
        getMockEndpoint("mock:handled").expectedBodiesReceived("Hello");
        getMockEndpoint("mock:failing").expectedMessageCount(0);

        template.sendBody("direct:onException", "Hello");

        assertMockEndpointsSatisfied();
        Exception caught = getMockEndpoint("mock:handled").getReceivedExchanges().get(0)
                .getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        assertEquals("Thrown by the step hook", caught.getMessage());
    }

    private int count(String nodeId) {
        return (int) asked.stream().filter(nodeId::equals).count();
    }

    /**
     * Fails or delays the steps configured by the test, by node id, or by node id and body ("node#body").
     */
    private final class TestHook implements StepHook {

        private final Map<String, AtomicInteger> failed = new ConcurrentHashMap<>();

        @Override
        public long beforeStep(Exchange exchange, String nodeId) {
            asked.add(nodeId);
            String key = nodeId + "#" + exchange.getMessage().getBody();
            if (!failures.containsKey(key) && !delays.containsKey(key)) {
                key = nodeId;
            }
            Integer times = failures.get(key);
            if (times != null) {
                if (times == -2) {
                    throw new IllegalStateException("Thrown by the step hook");
                }
                int n = failed.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
                if (times == -1 || n <= times) {
                    exchange.setException(new IllegalStateException(FAILED));
                }
                return PROCEED;
            }
            Long delay = delays.get(key);
            return delay != null ? delay : PROCEED;
        }
    }
}
