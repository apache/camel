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
import org.apache.camel.management.event.ExchangeCompletedEvent;
import org.apache.camel.management.event.ExchangeCreatedEvent;
import org.apache.camel.management.event.ExchangeFailureHandledEvent;
import org.apache.camel.management.event.ExchangeFailureHandlingEvent;
import org.apache.camel.management.event.ExchangeRedeliveryEvent;
import org.apache.camel.management.event.ExchangeSendingEvent;
import org.apache.camel.management.event.ExchangeSentEvent;
import org.apache.camel.support.EventNotifierSupport;

/**
 * @version 
 */
public class EventNotifierRedeliveryEventsTest extends ContextTestSupport {

    private static List<EventObject> events = new ArrayList<EventObject>();

    @Override
    public void setUp() throws Exception {
        events.clear();
        super.setUp();
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
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
                setIgnoreCamelContextEvents(true);
                setIgnoreRouteEvents(true);
                setIgnoreServiceEvents(true);
            }

            @Override
            protected void doStop() throws Exception {
            }
        });
        return context;
    }

    public void testExchangeRedeliverySync() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dead").maximumRedeliveries(4).redeliveryDelay(0));

                from("direct:start").throwException(new IllegalArgumentException("Damn"));
            }
        });
        context.start();

        getMockEndpoint("mock:dead").expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();
        assertTrue(oneExchangeDone.matchesMockWaitTime());

        assertEquals(12, events.size());

        assertIsInstanceOf(ExchangeSendingEvent.class, events.get(0));
        assertIsInstanceOf(ExchangeCreatedEvent.class, events.get(1));
        ExchangeRedeliveryEvent e = assertIsInstanceOf(ExchangeRedeliveryEvent.class, events.get(2));
        assertEquals(1, e.getAttempt());
        e = assertIsInstanceOf(ExchangeRedeliveryEvent.class, events.get(3));
        assertEquals(2, e.getAttempt());
        e = assertIsInstanceOf(ExchangeRedeliveryEvent.class, events.get(4));
        assertEquals(3, e.getAttempt());
        e = assertIsInstanceOf(ExchangeRedeliveryEvent.class, events.get(5));
        assertEquals(4, e.getAttempt());
        assertIsInstanceOf(ExchangeFailureHandlingEvent.class, events.get(6));
        assertIsInstanceOf(ExchangeSendingEvent.class, events.get(7));
        assertIsInstanceOf(ExchangeSentEvent.class, events.get(8));
        assertIsInstanceOf(ExchangeFailureHandledEvent.class, events.get(9));
        assertIsInstanceOf(ExchangeCompletedEvent.class, events.get(10));
        assertIsInstanceOf(ExchangeSentEvent.class, events.get(11));
    }

    public void testExchangeRedeliveryAsync() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dead").maximumRedeliveries(4).asyncDelayedRedelivery().redeliveryDelay(10));

                from("direct:start").throwException(new IllegalArgumentException("Damn"));
            }
        });
        context.start();

        getMockEndpoint("mock:dead").expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();
        assertTrue(oneExchangeDone.matchesMockWaitTime());

        assertEquals(12, events.size());

        assertIsInstanceOf(ExchangeSendingEvent.class, events.get(0));
        assertIsInstanceOf(ExchangeCreatedEvent.class, events.get(1));
        ExchangeRedeliveryEvent e = assertIsInstanceOf(ExchangeRedeliveryEvent.class, events.get(2));
        assertEquals(1, e.getAttempt());
        e = assertIsInstanceOf(ExchangeRedeliveryEvent.class, events.get(3));
        assertEquals(2, e.getAttempt());
        e = assertIsInstanceOf(ExchangeRedeliveryEvent.class, events.get(4));
        assertEquals(3, e.getAttempt());
        e = assertIsInstanceOf(ExchangeRedeliveryEvent.class, events.get(5));
        assertEquals(4, e.getAttempt());

        // since its async the ordering of the rest can be different depending per OS and timing
    }

}