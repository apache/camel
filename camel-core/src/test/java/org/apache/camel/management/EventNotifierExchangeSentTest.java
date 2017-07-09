/**
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
package org.apache.camel.management;

import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.management.event.ExchangeSendingEvent;
import org.apache.camel.management.event.ExchangeSentEvent;
import org.apache.camel.support.EventNotifierSupport;

import static org.awaitility.Awaitility.await;

/**
 * @version 
 */
public class EventNotifierExchangeSentTest extends ContextTestSupport {

    protected static List<EventObject> events = new ArrayList<EventObject>();

    @Override
    public void setUp() throws Exception {
        events.clear();
        super.setUp();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext(createRegistry());
        context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            public void notify(EventObject event) throws Exception {
                events.add(event);
            }

            public boolean isEnabled(EventObject event) {
                return true;
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

            @Override
            protected void doStop() throws Exception {
            }
        });
        return context;
    }

    public void testExchangeSent() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        assertEquals(8, events.size());
        ExchangeSendingEvent e0 = assertIsInstanceOf(ExchangeSendingEvent.class, events.get(0));
        ExchangeSendingEvent e1 = assertIsInstanceOf(ExchangeSendingEvent.class, events.get(1));
        ExchangeSentEvent e2 = assertIsInstanceOf(ExchangeSentEvent.class, events.get(2));
        ExchangeSendingEvent e3 = assertIsInstanceOf(ExchangeSendingEvent.class, events.get(3));
        ExchangeSentEvent e4 = assertIsInstanceOf(ExchangeSentEvent.class, events.get(4));
        ExchangeSendingEvent e5 = assertIsInstanceOf(ExchangeSendingEvent.class, events.get(5));
        ExchangeSentEvent e6 = assertIsInstanceOf(ExchangeSentEvent.class, events.get(6));
        ExchangeSentEvent e7 = assertIsInstanceOf(ExchangeSentEvent.class, events.get(7));

        assertEquals("direct://start", e0.getEndpoint().getEndpointUri());

        assertEquals("log://foo", e1.getEndpoint().getEndpointUri());
        assertEquals("log://foo", e2.getEndpoint().getEndpointUri());

        assertEquals("direct://bar", e3.getEndpoint().getEndpointUri());
        assertEquals("direct://bar", e4.getEndpoint().getEndpointUri());
        long time = e4.getTimeTaken();
        assertTrue("Should take about 0.5 sec, was: " + time, time > 400);

        assertEquals("mock://result", e5.getEndpoint().getEndpointUri());
        assertEquals("mock://result", e6.getEndpoint().getEndpointUri());

        assertEquals("direct://start", e7.getEndpoint().getEndpointUri());
        time = e7.getTimeTaken();
        assertTrue("Should take about 0.5 sec, was: " + time, time > 400);
    }

    public void testExchangeSentRecipient() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:foo", "Hello World", "foo", "direct:cool,direct:start");

        assertMockEndpointsSatisfied();

        assertTrue(oneExchangeDone.matchesMockWaitTime());

        assertEquals(12, events.size());
        ExchangeSendingEvent e0 = assertIsInstanceOf(ExchangeSendingEvent.class, events.get(0));
        ExchangeSendingEvent e1 = assertIsInstanceOf(ExchangeSendingEvent.class, events.get(1));
        ExchangeSentEvent e2 = assertIsInstanceOf(ExchangeSentEvent.class, events.get(2));
        ExchangeSendingEvent e3 = assertIsInstanceOf(ExchangeSendingEvent.class, events.get(3));
        ExchangeSendingEvent e4 = assertIsInstanceOf(ExchangeSendingEvent.class, events.get(4));
        ExchangeSentEvent e5 = assertIsInstanceOf(ExchangeSentEvent.class, events.get(5));
        ExchangeSendingEvent e6 = assertIsInstanceOf(ExchangeSendingEvent.class, events.get(6));
        ExchangeSentEvent e7 = assertIsInstanceOf(ExchangeSentEvent.class, events.get(7));
        ExchangeSendingEvent e8 = assertIsInstanceOf(ExchangeSendingEvent.class, events.get(8));
        ExchangeSentEvent e9 = assertIsInstanceOf(ExchangeSentEvent.class, events.get(9));
        ExchangeSentEvent e10 = assertIsInstanceOf(ExchangeSentEvent.class, events.get(10));
        ExchangeSentEvent e11 = assertIsInstanceOf(ExchangeSentEvent.class, events.get(11));

        assertEquals("direct://foo", e0.getEndpoint().getEndpointUri());
        assertEquals("direct://cool", e1.getEndpoint().getEndpointUri());
        assertEquals("direct://cool", e2.getEndpoint().getEndpointUri());
        assertEquals("direct://start", e3.getEndpoint().getEndpointUri());
        assertEquals("log://foo", e4.getEndpoint().getEndpointUri());
        assertEquals("log://foo", e5.getEndpoint().getEndpointUri());
        assertEquals("direct://bar", e6.getEndpoint().getEndpointUri());
        assertEquals("direct://bar", e7.getEndpoint().getEndpointUri());
        assertEquals("mock://result", e8.getEndpoint().getEndpointUri());
        assertEquals("mock://result", e9.getEndpoint().getEndpointUri());
        assertEquals("direct://start", e10.getEndpoint().getEndpointUri());
        assertEquals("direct://foo", e11.getEndpoint().getEndpointUri());
    }

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
        for (EventObject event : events) {
            if (event instanceof ExchangeSendingEvent) {
                ExchangeSendingEvent sending = (ExchangeSendingEvent) event;
                String uri = sending.getEndpoint().getEndpointUri();
                if ("log://foo".equals(uri))  {
                    found = true;
                }
            } else if (event instanceof ExchangeSentEvent) {
                ExchangeSentEvent sent = (ExchangeSentEvent) event;
                String uri = sent.getEndpoint().getEndpointUri();
                if ("log://foo".equals(uri))  {
                    found2 = true;
                }
            }
        }

        assertTrue("We should find log:foo being wire tapped", found);
        assertTrue("We should find log:foo being wire tapped", found2);
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