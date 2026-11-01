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
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
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
}
