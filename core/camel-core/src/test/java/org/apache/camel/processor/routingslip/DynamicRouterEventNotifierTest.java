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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.Body;
import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.ExchangeSendingEvent;
import org.apache.camel.spi.CamelEvent.ExchangeSentEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DynamicRouterEventNotifierTest extends ContextTestSupport {

    private final MyEventNotifier notifier = new MyEventNotifier();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getManagementStrategy().addEventNotifier(notifier);
        return context;
    }

    @Test
    public void testDynamicRouterEventNotifier() throws Exception {
        getMockEndpoint("mock:x").expectedMessageCount(1);
        getMockEndpoint("mock:y").expectedMessageCount(1);
        getMockEndpoint("mock:z").expectedMessageCount(1);
        getMockEndpoint("mock:end").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        assertEquals(5, notifier.getSending(), "Should have 5 sending events");
        assertEquals(5, notifier.getSent(), "Should have 5 sent events");
        // the sent event of each step comes before the next step is sent (the steps complete asynchronously, as the
        // error handler of each step schedules its work)
        assertEquals(List.of("sending direct://start", "sending mock://x", "sent mock://x", "sending mock://y",
                "sent mock://y", "sending mock://z", "sent mock://z", "sending mock://end", "sent mock://end",
                "sent direct://start"), notifier.getEvents());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").dynamicRouter(method(DynamicRouterEventNotifierTest.class, "slip")).to("mock:end");
            }
        };
    }

    public String slip(@Body String body, @Header(Exchange.SLIP_ENDPOINT) String previous) {
        if (previous == null) {
            return "mock:x";
        } else if ("mock://x".equals(previous)) {
            return "mock:y";
        } else if ("mock://y".equals(previous)) {
            return "mock:z";
        }

        // no more so return null
        return null;
    }

    private final class MyEventNotifier extends EventNotifierSupport {

        private final List<String> events = new CopyOnWriteArrayList<>();
        private int sending;
        private int sent;

        @Override
        public void notify(CamelEvent event) {
            if (event instanceof ExchangeSendingEvent) {
                events.add("sending " + ((ExchangeSendingEvent) event).getEndpoint().getEndpointUri());
                log.info("Sending: {}", event);
                sending++;
            } else {
                events.add("sent " + ((ExchangeSentEvent) event).getEndpoint().getEndpointUri());
                sent++;
            }
        }

        @Override
        public boolean isEnabled(CamelEvent event) {
            return event instanceof ExchangeSendingEvent || event instanceof ExchangeSentEvent;
        }

        public int getSending() {
            return sending;
        }

        public int getSent() {
            return sent;
        }

        public List<String> getEvents() {
            return events;
        }
    }
}
