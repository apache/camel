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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ExchangeSentEvent of a step of a routing slip or dynamic router is fired when that step is done, before the next
 * step is sent, also when the step completes asynchronously, and its time taken is the time of that step only.
 */
class RoutingSlipSentEventOrderTest extends ContextTestSupport {

    private final List<String> events = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Long> timeTaken = new ConcurrentHashMap<>();
    private final Map<String, String> visitedAtSent = new ConcurrentHashMap<>();
    private final Map<String, Boolean> failedAtSent = new ConcurrentHashMap<>();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            @Override
            public void notify(CamelEvent event) {
                if (event instanceof CamelEvent.ExchangeSendingEvent sending
                        && isStep(sending.getEndpoint().getEndpointUri())) {
                    events.add("sending " + name(sending.getEndpoint().getEndpointUri()));
                } else if (event instanceof CamelEvent.ExchangeSentEvent sent && isStep(sent.getEndpoint().getEndpointUri())) {
                    String step = name(sent.getEndpoint().getEndpointUri());
                    events.add("sent " + step);
                    timeTaken.put(step, sent.getTimeTaken());
                    String visited = sent.getExchange().getMessage().getHeader("visited", String.class);
                    if (visited != null) {
                        visitedAtSent.put(step, visited);
                    }
                    failedAtSent.put(step, sent.getExchange().isFailed());
                }
            }
        });
        return context;
    }

    private static boolean isStep(String uri) {
        return uri.startsWith("direct://step");
    }

    private static String name(String uri) {
        // step1 and step1sync are both step1
        return uri.substring("direct://".length()).replace("sync", "");
    }

    @Test
    void routingSlipWithAsyncSteps() {
        template.sendBody("direct:slipAsync", "Hello");
        checkOrder();
    }

    @Test
    void routingSlipWithStepsThatDelaySynchronously() {
        // the steps still complete asynchronously, as the error handler of each step schedules its work
        template.sendBody("direct:slipSync", "Hello");
        checkOrder();
    }

    @Test
    void routingSlipWithPrototypeEndpoints() {
        template.sendBody("direct:slipPrototype", "Hello");
        checkOrder();
    }

    @Test
    void dynamicRouterWithAsyncSteps() {
        template.sendBody("direct:dynamicAsync", "Hello");
        checkOrder();
    }

    @Test
    void sentEventOfAStepSeesTheMessageOfThatStep() {
        Exchange out = template.request("direct:slipAsync", e -> e.getMessage().setBody("Hello"));
        // the routing slip result is the message after its last step, and the sent event of each step sees the message
        // of that step (not yet the result of the steps after it)
        assertEquals("step1,step2", out.getMessage().getHeader("visited"));
        assertEquals("step1", visitedAtSent.get("step1"));
        assertEquals("step1,step2", visitedAtSent.get("step2"));
    }

    @Test
    void sentEventOfAStepIsNotFailedByALaterStep() {
        Exchange out = template.request("direct:slipFail", e -> e.getMessage().setBody("Hello"));
        assertTrue(out.isFailed());
        assertEquals(List.of("sending step1", "sent step1", "sending stepFail", "sent stepFail"), events);
        assertFalse(failedAtSent.get("step1"), "The sent event of step1 is failed by the failure of the next step");
        assertTrue(failedAtSent.get("stepFail"));
    }

    private void checkOrder() {
        assertEquals(List.of("sending step1", "sent step1", "sending step2", "sent step2"), events);
        // step1 takes about 10 ms and step2 about 300 ms: the time of step1 does not include step2
        assertNotNull(timeTaken.get("step2"));
        assertTrue(timeTaken.get("step1") < timeTaken.get("step2"), "Time taken of step1 includes step2: " + timeTaken);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:slipAsync").routingSlip(constant("direct:step1,direct:step2"));
                from("direct:slipSync").routingSlip(constant("direct:step1sync,direct:step2sync"));
                from("direct:slipPrototype").routingSlip(constant("direct:step1,direct:step2")).cacheSize(-1);
                from("direct:slipFail").routingSlip(constant("direct:step1,direct:stepFail"));
                from("direct:dynamicAsync").dynamicRouter(method(RoutingSlipSentEventOrderTest.class, "next"));

                // a delay is asynchronous by default
                from("direct:step1").delay(10).setHeader("visited", constant("step1"));
                from("direct:step2").delay(300).setHeader("visited", simple("${header.visited},step2"));
                from("direct:stepFail").throwException(new IllegalStateException("step failed"));
                from("direct:step1sync").delay(10).syncDelayed().setHeader("visited", constant("step1"));
                from("direct:step2sync").delay(300).syncDelayed().setHeader("visited", simple("${header.visited},step2"));
            }
        };
    }

    public static String next(Exchange exchange) {
        String previous = exchange.getProperty(Exchange.SLIP_ENDPOINT, String.class);
        if (previous == null) {
            return "direct:step1";
        } else if (previous.endsWith("step1")) {
            return "direct:step2";
        }
        return null;
    }
}
