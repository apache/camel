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
package org.apache.camel.impl.event;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EventNotifierExchangeSentTest extends ContextTestSupport {

    protected List<CamelEvent> events = new ArrayList<>();

    @BeforeEach
    public void clearEvents() throws Exception {
        events.clear();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext(createRegistry());
        context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            public void notify(CamelEvent event) throws Exception {
                events.add(event);
            }

            @Override
            protected void doStart() throws Exception {
                // filter out unwanted events
                setIgnoreCamelContextEvents(true);
                setIgnoreServiceEvents(true);
                setIgnoreRouteEvents(true);
                setIgnoreExchangeCreatedEvent(true);
                setIgnoreExchangeCompletedEvent(true);
                setIgnoreExchangeFailedEvents(true);
                setIgnoreExchangeRedeliveryEvents(true);
            }
        });
        return context;
    }

    @Test
    public void testExchangeSent() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        // TODO (limolkova)
        assertEquals(9, events.size());
        // direct:start is sync
        ExchangeSendingEvent e0 = assertIsInstanceOf(ExchangeSendingEvent.class, events.get(0));
        // log is sync
        ExchangeSendingEvent e1 = assertIsInstanceOf(ExchangeSendingEvent.class, events.get(1));
        ExchangeSentEvent e2 = assertIsInstanceOf(ExchangeSentEvent.class, events.get(2));

        // direct:bar is async
        ExchangeSendingEvent e3 = assertIsInstanceOf(ExchangeSendingEvent.class, events.get(3));
        assertIsInstanceOf(ExchangeAsyncProcessingStartedEvent.class, events.get(4));

        ExchangeSentEvent e5 = assertIsInstanceOf(ExchangeSentEvent.class, events.get(5));
        ExchangeSendingEvent e6 = assertIsInstanceOf(ExchangeSendingEvent.class, events.get(6));
        ExchangeSentEvent e7 = assertIsInstanceOf(ExchangeSentEvent.class, events.get(7));
        ExchangeSentEvent e8 = assertIsInstanceOf(ExchangeSentEvent.class, events.get(8));

        assertEquals("direct://start", e0.getEndpoint().getEndpointUri());

        assertEquals("log://foo", e1.getEndpoint().getEndpointUri());
        assertEquals("log://foo", e2.getEndpoint().getEndpointUri());

        assertEquals("direct://bar", e3.getEndpoint().getEndpointUri());
        assertEquals("direct://bar", e5.getEndpoint().getEndpointUri());
        long time = e5.getTimeTaken();
        assertTrue(time > 400, "Should take about 0.5 sec, was: " + time);

        assertEquals("mock://result", e6.getEndpoint().getEndpointUri());
        assertEquals("mock://result", e7.getEndpoint().getEndpointUri());

        assertEquals("direct://start", e8.getEndpoint().getEndpointUri());
        time = e8.getTimeTaken();
        assertTrue(time > 400, "Should take about 0.5 sec, was: " + time);
    }

    @Test
    public void testExchangeSentRecipient() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:foo", "Hello World", "foo", "direct:cool,direct:start");

        assertMockEndpointsSatisfied();

        assertTrue(oneExchangeDone.matchesWaitTime());

        assertEquals(15, events.size());
        ExchangeSendingEvent e0 = assertIsInstanceOf(ExchangeSendingEvent.class, events.get(0));
        ExchangeSendingEvent e1 = assertIsInstanceOf(ExchangeSendingEvent.class, events.get(1));

        // cool is async
        assertIsInstanceOf(ExchangeAsyncProcessingStartedEvent.class, events.get(2));

        ExchangeSentEvent e3 = assertIsInstanceOf(ExchangeSentEvent.class, events.get(3));

        ExchangeSendingEvent e4 = assertIsInstanceOf(ExchangeSendingEvent.class, events.get(4));
        // multicast processor is always async, so start also gets async started event
        assertIsInstanceOf(ExchangeAsyncProcessingStartedEvent.class, events.get(5));

        ExchangeSendingEvent e6 = assertIsInstanceOf(ExchangeSendingEvent.class, events.get(6));
        ExchangeSentEvent e7 = assertIsInstanceOf(ExchangeSentEvent.class, events.get(7));
        ExchangeSendingEvent e8 = assertIsInstanceOf(ExchangeSendingEvent.class, events.get(8));

        // direct:bar is async
        assertIsInstanceOf(ExchangeAsyncProcessingStartedEvent.class, events.get(9));

        ExchangeSentEvent e10 = assertIsInstanceOf(ExchangeSentEvent.class, events.get(10));
        ExchangeSendingEvent e11 = assertIsInstanceOf(ExchangeSendingEvent.class, events.get(11));
        ExchangeSentEvent e12 = assertIsInstanceOf(ExchangeSentEvent.class, events.get(12));
        ExchangeSentEvent e13 = assertIsInstanceOf(ExchangeSentEvent.class, events.get(13));
        ExchangeSentEvent e14 = assertIsInstanceOf(ExchangeSentEvent.class, events.get(14));

        assertEquals("direct://foo", e0.getEndpoint().getEndpointUri());
        assertEquals("direct://cool", e1.getEndpoint().getEndpointUri());
        assertEquals("direct://cool", e3.getEndpoint().getEndpointUri());
        assertEquals("direct://start", e4.getEndpoint().getEndpointUri());
        assertEquals("log://foo", e6.getEndpoint().getEndpointUri());
        assertEquals("log://foo", e7.getEndpoint().getEndpointUri());
        assertEquals("direct://bar", e8.getEndpoint().getEndpointUri());
        assertEquals("direct://bar", e10.getEndpoint().getEndpointUri());
        assertEquals("mock://result", e11.getEndpoint().getEndpointUri());
        assertEquals("mock://result", e12.getEndpoint().getEndpointUri());
        assertEquals("direct://start", e13.getEndpoint().getEndpointUri());
        assertEquals("direct://foo", e14.getEndpoint().getEndpointUri());
    }

    @Test
    public void testExchangeWireTap() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:tap", "Hello World");

        assertMockEndpointsSatisfied();

        // give it time to complete
        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(6, events.size()));

        // we should find log:foo which we tapped
        // which runs async so they can be in random order
        boolean found = false;
        boolean found2 = false;
        for (CamelEvent event : events) {
            if (event instanceof ExchangeSendingEvent) {
                ExchangeSendingEvent sending = (ExchangeSendingEvent) event;
                String uri = sending.getEndpoint().getEndpointUri();
                if ("log://foo".equals(uri)) {
                    found = true;
                }
            } else if (event instanceof ExchangeSentEvent) {
                ExchangeSentEvent sent = (ExchangeSentEvent) event;
                String uri = sent.getEndpoint().getEndpointUri();
                if ("log://foo".equals(uri)) {
                    found2 = true;
                }
            }
        }

        assertTrue(found, "We should find log:foo being wire tapped");
        assertTrue(found2, "We should find log:foo being wire tapped");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("log:foo").to("direct:bar").to("mock:result");

                from("direct:bar").delay(500);

                from("direct:foo").recipientList().header("foo");

                from("direct:cool").delay(1000);

                from("direct:tap").wireTap("log:foo").to("mock:result");
            }
        };
    }

}
