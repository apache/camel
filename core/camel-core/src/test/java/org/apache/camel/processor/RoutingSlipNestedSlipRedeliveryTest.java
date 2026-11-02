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

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.TryDefinition;
import org.apache.camel.support.ExpressionAdapter;
import org.junit.jupiter.api.Test;

import static org.apache.camel.builder.Builder.constant;
import static org.apache.camel.builder.Builder.header;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A routing slip or dynamic router step is redelivered by its error handler, and the route it called ran a routing slip
 * or dynamic router on the same exchange before failing. The error handler sends the redelivery to the producer kept in
 * the exchange property {@link Exchange#SLIP_PRODUCER}, which the slip in the called route must leave as it found it.
 * After the step, the exchange property {@link Exchange#SLIP_ENDPOINT} is the endpoint of the step again, not the last
 * endpoint of the slip in the called route. Every send is bounded, so a lost callback fails the test instead of
 * blocking it.
 */
class RoutingSlipNestedSlipRedeliveryTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void routingSlipCallsRouteWithRoutingSlipInDoTry() throws Exception {
        addRoutes(false, false, false);
        getMockEndpoint("mock:inner").expectedMessageCount(2);

        Exchange out = send();

        assertBoom(out);
        assertMockEndpointsSatisfied();
    }

    @Test
    void routingSlipCallsRouteWithRoutingSlipNoErrorHandlerDeadLetterChannel() throws Exception {
        addRoutes(false, false, true);
        getMockEndpoint("mock:inner").expectedMessageCount(3);
        getMockEndpoint("mock:dead").expectedMessageCount(1);

        Exchange out = send();

        assertNull(out.getException());
        assertMockEndpointsSatisfied();
    }

    @Test
    void routingSlipCallsRouteWithDynamicRouterInDoTry() throws Exception {
        addRoutes(false, true, false);
        getMockEndpoint("mock:inner").expectedMessageCount(2);

        Exchange out = send();

        assertBoom(out);
        assertMockEndpointsSatisfied();
    }

    @Test
    void dynamicRouterCallsRouteWithRoutingSlipInDoTry() throws Exception {
        addRoutes(true, false, false);
        getMockEndpoint("mock:inner").expectedMessageCount(2);

        Exchange out = send();

        assertBoom(out);
        assertMockEndpointsSatisfied();
    }

    @Test
    void dynamicRouterCallsRouteWithRoutingSlipNoErrorHandlerDeadLetterChannel() throws Exception {
        addRoutes(true, false, true);
        getMockEndpoint("mock:inner").expectedMessageCount(3);
        getMockEndpoint("mock:dead").expectedMessageCount(1);

        Exchange out = send();

        assertNull(out.getException());
        assertMockEndpointsSatisfied();
    }

    @Test
    void dynamicRouterCallsRouteWithDynamicRouterInDoTry() throws Exception {
        addRoutes(true, true, false);
        getMockEndpoint("mock:inner").expectedMessageCount(2);

        Exchange out = send();

        assertBoom(out);
        assertMockEndpointsSatisfied();
    }

    @Test
    void dynamicRouterCallsRouteWithDynamicRouterNoErrorHandlerDeadLetterChannel() throws Exception {
        addRoutes(true, true, true);
        getMockEndpoint("mock:inner").expectedMessageCount(3);
        getMockEndpoint("mock:dead").expectedMessageCount(1);

        Exchange out = send();

        assertNull(out.getException());
        assertMockEndpointsSatisfied();
    }

    @Test
    void onExceptionHandledWithRedelivery() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                onException(IllegalStateException.class).handled(true).maximumRedeliveries(1).redeliveryDelay(0)
                        .to("mock:handled");
                from("direct:start").routingSlip(constant("direct:sub"));
                from("direct:sub").errorHandler(noErrorHandler())
                        .routingSlip(constant("mock:inner"))
                        .throwException(new IllegalStateException("boom"));
            }
        });
        context.start();
        getMockEndpoint("mock:inner").expectedMessageCount(2);
        getMockEndpoint("mock:handled").expectedMessageCount(1);

        Exchange out = send();

        assertNull(out.getException());
        assertMockEndpointsSatisfied();
    }

    @Test
    void innerRoutingSlipStepCompletesAsynchronously() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("mock:dead").maximumRedeliveries(2).redeliveryDelay(0));
                from("direct:start").routingSlip(constant("direct:sub"));
                from("direct:sub").errorHandler(noErrorHandler())
                        .routingSlip(constant("direct:async"))
                        .throwException(new IllegalStateException("boom"));
                // the rest of this route runs on another thread, so the inner slip step completes asynchronously
                from("direct:async").threads(1).to("mock:inner");
            }
        });
        context.start();
        getMockEndpoint("mock:inner").expectedMessageCount(3);
        getMockEndpoint("mock:dead").expectedMessageCount(1);

        Exchange out = send();

        assertNull(out.getException());
        assertMockEndpointsSatisfied();
    }

    @Test
    void innerRoutingSlipStepIsRedeliveredToo() throws Exception {
        AtomicInteger flakyCalls = new AtomicInteger();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(defaultErrorHandler().maximumRedeliveries(1).redeliveryDelay(0));
                from("direct:start").errorHandler(defaultErrorHandler().maximumRedeliveries(3).redeliveryDelay(0))
                        .routingSlip(constant("direct:sub"));
                // the error handler of this route redelivers the inner slip step while the outer step's producer is kept
                from("direct:sub").to("mock:sub")
                        .routingSlip(constant("direct:flaky"))
                        .doTry()
                            .throwException(new IllegalStateException("boom"))
                        .endDoTry()
                        .doFinally()
                        .end();
                from("direct:flaky").errorHandler(noErrorHandler())
                        .process(e -> {
                            if (flakyCalls.incrementAndGet() == 1) {
                                throw new IllegalArgumentException("flaky");
                            }
                        });
            }
        });
        context.start();
        // the outer step is redelivered at least once
        getMockEndpoint("mock:sub").expectedMinimumMessageCount(2);

        Exchange out = send();

        assertBoom(out);
        assertMockEndpointsSatisfied();
        // the inner step was redelivered once, on top of one call per run of direct:sub
        assertEquals(getMockEndpoint("mock:sub").getReceivedCounter() + 1, flakyCalls.get());
    }

    @Test
    void originalCampaignRoute() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                onException(IllegalStateException.class).handled(true).maximumRedeliveries(1).redeliveryDelay(0);
                from("direct:start").routingSlip(constant("mock:m2,direct:r1,direct:r1"));
                from("direct:r1")
                        .doTry()
                            .doTry()
                                .throwException(new IllegalStateException("y"))
                            .doFinally()
                                .to("direct:r2")
                            .endDoTry()
                        .endDoTry()
                        .doFinally()
                        .end();
                from("direct:r2").doTry().routingSlip(constant("mock:m1")).endDoTry().doFinally().end();
            }
        });
        context.start();
        getMockEndpoint("mock:m2").expectedMessageCount(1);

        Exchange out = send();

        assertNull(out.getException());
        assertMockEndpointsSatisfied();
    }

    @Test
    void missingSlipProducerFailsTheExchange() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(defaultErrorHandler().maximumRedeliveries(1).redeliveryDelay(0));
                from("direct:start").routingSlip(constant("direct:sub"));
                from("direct:sub").errorHandler(noErrorHandler())
                        .process(e -> e.removeProperty(Exchange.SLIP_PRODUCER))
                        .throwException(new IllegalStateException("boom"));
            }
        });
        context.start();

        Exchange out = send();

        IllegalStateException e = assertInstanceOf(IllegalStateException.class, out.getException());
        assertTrue(e.getMessage().contains(Exchange.SLIP_PRODUCER), e.getMessage());
    }

    // controls that pass without the fix too

    @Test
    void recipientListInCalledRoute() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(defaultErrorHandler().maximumRedeliveries(1).redeliveryDelay(0));
                from("direct:start").routingSlip(constant("direct:sub"));
                from("direct:sub").errorHandler(noErrorHandler())
                        .recipientList(constant("mock:inner"))
                        .throwException(new IllegalStateException("boom"));
            }
        });
        context.start();
        getMockEndpoint("mock:inner").expectedMessageCount(2);

        Exchange out = send();

        assertBoom(out);
        assertMockEndpointsSatisfied();
    }

    @Test
    void noRedelivery() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routingSlip(constant("direct:sub"));
                from("direct:sub").errorHandler(noErrorHandler())
                        .routingSlip(constant("mock:inner"))
                        .throwException(new IllegalStateException("boom"));
            }
        });
        context.start();
        getMockEndpoint("mock:inner").expectedMessageCount(1);

        Exchange out = send();

        assertBoom(out);
        assertMockEndpointsSatisfied();
    }

    @Test
    void noSlipProducerLeftAfterNestedSlips() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(defaultErrorHandler().maximumRedeliveries(1).redeliveryDelay(0));
                from("direct:start").routingSlip(constant("direct:sub,mock:end"));
                from("direct:sub").routingSlip(constant("mock:inner"));
            }
        });
        context.start();
        getMockEndpoint("mock:inner").expectedMessageCount(1);
        getMockEndpoint("mock:end").expectedMessageCount(1);

        Exchange out = send();

        assertNull(out.getException());
        assertNull(out.getProperty(Exchange.SLIP_PRODUCER));
        assertMockEndpointsSatisfied();
    }

    @Test
    void dynamicRouterSeesItsStepAsSlipEndpointAfterNestedRoutingSlip() throws Exception {
        List<String> seen = addDynamicRouterRecordingSlipEndpoint(constant("mock:inner"));
        getMockEndpoint("mock:inner").expectedMessageCount(1);

        Exchange out = send();

        assertNull(out.getException());
        assertEquals(List.of("none", "direct://sub"), seen);
        assertMockEndpointsSatisfied();
    }

    @Test
    void dynamicRouterSeesItsStepAsSlipEndpointAfterNestedEmptyRoutingSlip() throws Exception {
        // the empty routing slip in the called route removes the slip endpoint when it starts: the dynamic router
        // would then take its first step again, and again
        List<String> seen = addDynamicRouterRecordingSlipEndpoint(header("noSuchHeader"));

        Exchange out = send();

        assertNull(out.getException());
        assertEquals(List.of("none", "direct://sub"), seen);
    }

    /**
     * Adds a dynamic router that routes to direct:sub once and records the slip endpoint it sees each time. The called
     * route runs a routing slip with the given expression. The router stops after five steps in any case.
     */
    private List<String> addDynamicRouterRecordingSlipEndpoint(Expression innerSlip) throws Exception {
        List<String> seen = new CopyOnWriteArrayList<>();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").dynamicRouter(new ExpressionAdapter() {
                    @Override
                    public Object evaluate(Exchange exchange) {
                        String current = exchange.getProperty(Exchange.SLIP_ENDPOINT, String.class);
                        seen.add(current == null ? "none" : current);
                        return current == null && seen.size() <= 5 ? "direct:sub" : null;
                    }
                });
                from("direct:sub").routingSlip(innerSlip);
            }
        });
        context.start();
        return seen;
    }

    /**
     * Adds a route with a routing slip or dynamic router step that calls direct:sub, which runs a routing slip or
     * dynamic router to mock:inner and then fails: either at the top of a doTry without doCatch (the default error
     * handler redelivers once), or with noErrorHandler (a dead letter channel redelivers twice).
     */
    private void addRoutes(boolean outerDynamicRouter, boolean innerDynamicRouter, boolean deadLetterChannel)
            throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                if (deadLetterChannel) {
                    errorHandler(deadLetterChannel("mock:dead").maximumRedeliveries(2).redeliveryDelay(0));
                } else {
                    errorHandler(defaultErrorHandler().maximumRedeliveries(1).redeliveryDelay(0));
                }

                if (outerDynamicRouter) {
                    from("direct:start").dynamicRouter(once("direct:sub"));
                } else {
                    from("direct:start").routingSlip(constant("direct:sub"));
                }

                RouteDefinition sub = from("direct:sub");
                if (deadLetterChannel) {
                    sub.errorHandler(noErrorHandler());
                    slipThenFail(sub, innerDynamicRouter);
                } else {
                    TryDefinition block = sub.doTry();
                    slipThenFail(block, innerDynamicRouter);
                    block.endDoTry().doFinally().to("mock:finally").end();
                }
            }
        });
        context.start();
    }

    private static void slipThenFail(ProcessorDefinition<?> block, boolean dynamicRouter) {
        if (dynamicRouter) {
            block.dynamicRouter(once("mock:inner"));
        } else {
            block.routingSlip(constant("mock:inner"));
        }
        block.throwException(new IllegalStateException("boom"));
    }

    /**
     * A dynamic router expression that routes to the uri once: a dynamic router removes the slip endpoint property when
     * it starts, and sets it after each step.
     */
    private static Expression once(String uri) {
        return new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                return exchange.getProperty(Exchange.SLIP_ENDPOINT) == null ? uri : null;
            }
        };
    }

    private Exchange send() {
        return assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> template.send("direct:start", e -> e.getMessage().setBody("in")),
                "the send did not complete, the callback of the redelivered step was lost");
    }

    private static void assertBoom(Exchange out) {
        IllegalStateException e = assertInstanceOf(IllegalStateException.class, out.getException());
        assertEquals("boom", e.getMessage());
    }
}
