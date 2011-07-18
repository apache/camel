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
import org.apache.camel.management.event.CamelContextStartedEvent;
import org.apache.camel.management.event.CamelContextStartingEvent;
import org.apache.camel.management.event.ExchangeCompletedEvent;
import org.apache.camel.management.event.ExchangeCreatedEvent;
import org.apache.camel.management.event.ExchangeFailureHandledEvent;
import org.apache.camel.management.event.ExchangeSentEvent;
import org.apache.camel.management.event.RouteStartedEvent;
import org.apache.camel.processor.SendProcessor;

/**
 * @version 
 */
public class EventNotifierFailureHandledEventsTest extends ContextTestSupport {

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
            }

            @Override
            protected void doStop() throws Exception {
            }
        });
        return context;
    }

    public void testExchangeDeadLetterChannel() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dead"));

                from("direct:start").throwException(new IllegalArgumentException("Damn"));
            }
        });
        context.start();

        getMockEndpoint("mock:dead").expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();

        assertEquals(8, events.size());
        assertIsInstanceOf(CamelContextStartingEvent.class, events.get(0));
        assertIsInstanceOf(RouteStartedEvent.class, events.get(1));
        assertIsInstanceOf(CamelContextStartedEvent.class, events.get(2));
        assertIsInstanceOf(ExchangeCreatedEvent.class, events.get(3));

        ExchangeFailureHandledEvent e = assertIsInstanceOf(ExchangeFailureHandledEvent.class, events.get(4));
        assertEquals("should be DLC", true, e.isDeadLetterChannel());
        SendProcessor send = assertIsInstanceOf(SendProcessor.class, e.getFailureHandler());
        assertEquals("mock://dead", send.getDestination().getEndpointUri());

        // dead letter channel will mark the exchange as completed
        assertIsInstanceOf(ExchangeCompletedEvent.class, events.get(5));
        // and the sent will be logged after they are complete sending as it record the time taken as well
        assertIsInstanceOf(ExchangeSentEvent.class, events.get(6));
        assertIsInstanceOf(ExchangeSentEvent.class, events.get(7));
    }

    public void testExchangeOnException() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(IllegalArgumentException.class).handled(true).to("mock:dead");

                from("direct:start").throwException(new IllegalArgumentException("Damn"));
            }
        });
        context.start();

        getMockEndpoint("mock:dead").expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();

        assertEquals(8, events.size());
        assertIsInstanceOf(CamelContextStartingEvent.class, events.get(0));
        assertIsInstanceOf(RouteStartedEvent.class, events.get(1));
        assertIsInstanceOf(CamelContextStartedEvent.class, events.get(2));
        assertIsInstanceOf(ExchangeCreatedEvent.class, events.get(3));

        ExchangeFailureHandledEvent e = assertIsInstanceOf(ExchangeFailureHandledEvent.class, events.get(4));
        assertEquals("should NOT be DLC", false, e.isDeadLetterChannel());

        // onException will handle the exception
        assertIsInstanceOf(ExchangeCompletedEvent.class, events.get(5));
        assertIsInstanceOf(ExchangeSentEvent.class, events.get(6));
        assertIsInstanceOf(ExchangeSentEvent.class, events.get(7));
    }

}
