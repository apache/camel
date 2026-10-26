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
package org.apache.camel.processor.routingslip;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.AggregationStrategies;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.direct.DirectConsumerNotAvailableException;
import org.apache.camel.model.CatchDefinition;
import org.apache.camel.model.FinallyDefinition;
import org.apache.camel.model.LoadBalanceDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RecipientListDefinition;
import org.apache.camel.model.RoutingSlipDefinition;
import org.apache.camel.model.ThrowExceptionDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.model.TryDefinition;
import org.apache.camel.model.language.ConstantExpression;
import org.apache.camel.model.loadbalancer.FailoverLoadBalancerDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * A recipient list is the first step of a doFinally after a called route's error handler handled a failure, and a
 * routing slip (or dynamic router) runs later on the same exchange. The recipient list must send to its recipients, and
 * the routing slip must route to its own endpoints, not to the value the recipient list evaluated.
 */
class RoutingSlipInDoFinallyAfterRecipientListTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    private void routes(boolean deadLetterChannel, boolean dynamicRouter, boolean calleeFails) throws Exception {
        AtomicInteger routerCalls = new AtomicInteger();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                if (deadLetterChannel) {
                    errorHandler(deadLetterChannel("mock:dead").redeliveryDelay(0));
                } else {
                    onException(Exception.class).handled(true).to("mock:dead");
                }

                var outerFinally = from("direct:start").doTry().to("direct:middle").doFinally();
                if (dynamicRouter) {
                    outerFinally.dynamicRouter(method(new Object() {
                        public String route() {
                            return routerCalls.getAndIncrement() == 0 ? "mock:slip" : null;
                        }
                    }, "route"));
                } else {
                    outerFinally.routingSlip(constant("mock:slip"));
                }
                outerFinally.end();

                from("direct:middle").doTry().to("direct:callee").doFinally().recipientList(constant("mock:a,mock:b"))
                        .end();

                if (calleeFails) {
                    from("direct:callee").throwException(new IOException("Forced"));
                } else {
                    from("direct:callee").to("mock:callee");
                }
            }
        });
        context.start();
    }

    private Exchange send() {
        return assertTimeoutPreemptively(Duration.ofSeconds(10),
                () -> template.send("direct:start", e -> e.getMessage().setBody("Hello")), "the send did not complete");
    }

    private void assertEachDestinationOnce(Exchange out) {
        assertNull(out.getException());
        assertEquals(1, getMockEndpoint("mock:a").getReceivedCounter(), "mock:a (recipient list)");
        assertEquals(1, getMockEndpoint("mock:b").getReceivedCounter(), "mock:b (recipient list)");
        assertEquals(1, getMockEndpoint("mock:slip").getReceivedCounter(), "mock:slip (the routing slip's own endpoint)");
        assertFalse(out.getProperties().containsKey(Exchange.EVALUATE_EXPRESSION_RESULT),
                "the recipient list removed the value it evaluated");
    }

    @Test
    void routingSlipWithDeadLetterChannel() throws Exception {
        routes(true, false, true);
        Exchange out = send();
        assertEquals(1, getMockEndpoint("mock:dead").getReceivedCounter(), "mock:dead");
        assertEachDestinationOnce(out);
    }

    @Test
    void routingSlipWithHandledOnException() throws Exception {
        routes(false, false, true);
        Exchange out = send();
        assertEquals(1, getMockEndpoint("mock:dead").getReceivedCounter(), "mock:dead");
        assertEachDestinationOnce(out);
    }

    @Test
    void dynamicRouterWithDeadLetterChannel() throws Exception {
        routes(true, true, true);
        Exchange out = send();
        assertEquals(1, getMockEndpoint("mock:dead").getReceivedCounter(), "mock:dead");
        assertEachDestinationOnce(out);
    }

    @Test
    void controlCalledRouteDoesNotFail() throws Exception {
        routes(true, false, false);
        Exchange out = send();
        assertEquals(0, getMockEndpoint("mock:dead").getReceivedCounter(), "mock:dead");
        assertEquals(1, getMockEndpoint("mock:callee").getReceivedCounter(), "mock:callee");
        assertEachDestinationOnce(out);
    }

    // A routing slip in a doFinally, in a failover, after a redelivery of the doTry. The recipient list's value
    // "direct:callee;mock:m1;direct:callee" is not a list for the routing slip (delimiter ",") but one direct endpoint
    // without a consumer, so the routing slip blocked for the direct timeout (30 seconds)
    @Test
    void routingSlipAfterRedeliveryDoesNotUseTheRecipientListValue() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("mock:dead").maximumRedeliveries(1).redeliveryDelay(0));

                // start: failover { doTry { throw } doFinally { routingSlip(direct:middle) } }
                LoadBalanceDefinition failover = new LoadBalanceDefinition();
                FailoverLoadBalancerDefinition type = new FailoverLoadBalancerDefinition();
                type.setMaximumFailoverAttempts("0");
                failover.setLoadBalancerType(type);
                failover.addOutput(doTry(List.of(throwException(new IllegalStateException("start"))), null, null,
                        List.of(new RoutingSlipDefinition<>(new ConstantExpression("direct:middle")))));
                from("direct:start").addOutput(failover);

                // middle: doTry {
                //           doTry { throw } doFinally { doTry { throw } doFinally { to(direct:callee) } }
                //         } doCatch(RuntimeException) {
                //           doTry { to(mock:m2) } doFinally { recipientList("direct:callee;mock:m1;direct:callee") }
                //         }
                ProcessorDefinition<?> innerFinally = doTry(List.of(throwException(new IllegalArgumentException("inner"))),
                        null, null, List.of(new ToDefinition("direct:callee")));
                ProcessorDefinition<?> body = doTry(List.of(throwException(new IllegalStateException("body"))), null, null,
                        List.of(innerFinally));
                RecipientListDefinition<?> recipientList
                        = new RecipientListDefinition<>(new ConstantExpression("direct:callee;mock:m1;direct:callee"));
                recipientList.setDelimiter(";");
                recipientList.aggregationStrategy(AggregationStrategies.useOriginal());
                ProcessorDefinition<?> inCatch
                        = doTry(List.of(new ToDefinition("mock:m2")), null, null, List.of(recipientList));
                from("direct:middle").addOutput(doTry(List.of(body), RuntimeException.class, List.of(inCatch), null));

                from("direct:callee").throwException(new IOException("Forced"));
            }
        });
        context.start();

        send();
        for (Exchange dead : getMockEndpoint("mock:dead").getReceivedExchanges()) {
            Exception caught = dead.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
            assertFalse(caught instanceof DirectConsumerNotAvailableException,
                    "the routing slip sent to an endpoint of the recipient list: " + caught);
        }
    }

    private static ProcessorDefinition<?> throwException(Exception e) {
        ThrowExceptionDefinition answer = new ThrowExceptionDefinition();
        answer.setException(e);
        return answer;
    }

    private static TryDefinition doTry(
            List<ProcessorDefinition<?>> body, Class<? extends Throwable> catchType,
            List<ProcessorDefinition<?>> catchBody, List<ProcessorDefinition<?>> finallyBody) {
        TryDefinition answer = new TryDefinition();
        body.forEach(answer::addOutput);
        if (catchType != null) {
            CatchDefinition doCatch = new CatchDefinition();
            doCatch.setExceptionClasses(List.of(catchType));
            catchBody.forEach(doCatch::addOutput);
            answer.addOutput(doCatch);
        }
        if (finallyBody != null) {
            FinallyDefinition doFinally = new FinallyDefinition();
            finallyBody.forEach(doFinally::addOutput);
            answer.addOutput(doFinally);
        }
        return answer;
    }
}
