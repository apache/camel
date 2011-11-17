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

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.management.event.ExchangeSentEvent;

/**
 * @version 
 */
public class EventNotifierExchangeSentTest extends ContextTestSupport {

    private static List<EventObject> events = new ArrayList<EventObject>();

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

        assertEquals(4, events.size());
        ExchangeSentEvent e0 = assertIsInstanceOf(ExchangeSentEvent.class, events.get(0));
        ExchangeSentEvent e1 = assertIsInstanceOf(ExchangeSentEvent.class, events.get(1));
        ExchangeSentEvent e2 = assertIsInstanceOf(ExchangeSentEvent.class, events.get(2));
        ExchangeSentEvent e3 = assertIsInstanceOf(ExchangeSentEvent.class, events.get(3));

        assertEquals("log://foo", e0.getEndpoint().getEndpointUri());
        assertEquals("direct://bar", e1.getEndpoint().getEndpointUri());
        long time = e1.getTimeTaken();
        assertTrue("Should take about 0.5 sec, was: " + time, time > 400);

        assertEquals("mock://result", e2.getEndpoint().getEndpointUri());

        assertEquals("direct://start", e3.getEndpoint().getEndpointUri());
        time = e3.getTimeTaken();
        assertTrue("Should take about 0.5 sec, was: " + time, time > 400);
    }

    public void testExchangeSentRecipient() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:foo", "Hello World", "foo", "direct:cool,direct:start");

        assertMockEndpointsSatisfied();

        // give it time to complete
        Thread.sleep(100);

        assertEquals(6, events.size());
        ExchangeSentEvent e0 = assertIsInstanceOf(ExchangeSentEvent.class, events.get(0));
        ExchangeSentEvent e1 = assertIsInstanceOf(ExchangeSentEvent.class, events.get(1));
        ExchangeSentEvent e2 = assertIsInstanceOf(ExchangeSentEvent.class, events.get(2));
        ExchangeSentEvent e3 = assertIsInstanceOf(ExchangeSentEvent.class, events.get(3));
        ExchangeSentEvent e4 = assertIsInstanceOf(ExchangeSentEvent.class, events.get(4));
        ExchangeSentEvent e5 = assertIsInstanceOf(ExchangeSentEvent.class, events.get(5));

        assertEquals("direct://cool", e0.getEndpoint().getEndpointUri());
        assertEquals("log://foo", e1.getEndpoint().getEndpointUri());
        assertEquals("direct://bar", e2.getEndpoint().getEndpointUri());
        assertEquals("mock://result", e3.getEndpoint().getEndpointUri());
        assertEquals("direct://start", e4.getEndpoint().getEndpointUri());
        assertEquals("direct://foo", e5.getEndpoint().getEndpointUri());
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
            }
        };
    }

}