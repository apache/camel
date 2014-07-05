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
import org.apache.camel.management.event.CamelContextResumedEvent;
import org.apache.camel.management.event.CamelContextResumingEvent;
import org.apache.camel.management.event.CamelContextStartedEvent;
import org.apache.camel.management.event.CamelContextStartingEvent;
import org.apache.camel.management.event.CamelContextStoppedEvent;
import org.apache.camel.management.event.CamelContextStoppingEvent;
import org.apache.camel.management.event.CamelContextSuspendedEvent;
import org.apache.camel.management.event.CamelContextSuspendingEvent;
import org.apache.camel.management.event.ExchangeCompletedEvent;
import org.apache.camel.management.event.ExchangeCreatedEvent;
import org.apache.camel.management.event.ExchangeFailedEvent;
import org.apache.camel.management.event.ExchangeSendingEvent;
import org.apache.camel.management.event.ExchangeSentEvent;
import org.apache.camel.management.event.RouteAddedEvent;
import org.apache.camel.management.event.RouteRemovedEvent;
import org.apache.camel.management.event.RouteStartedEvent;
import org.apache.camel.management.event.RouteStoppedEvent;
import org.apache.camel.support.EventNotifierSupport;

/**
 * @version 
 */
public class EventNotifierEventsTest extends ContextTestSupport {

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
            }

            @Override
            protected void doStop() throws Exception {
            }
        });
        return context;
    }

    public void testExchangeDone() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        assertEquals(14, events.size());
        assertIsInstanceOf(CamelContextStartingEvent.class, events.get(0));
        assertIsInstanceOf(RouteAddedEvent.class, events.get(1));
        assertIsInstanceOf(RouteAddedEvent.class, events.get(2));
        assertIsInstanceOf(RouteStartedEvent.class, events.get(3));
        assertIsInstanceOf(RouteStartedEvent.class, events.get(4));
        assertIsInstanceOf(CamelContextStartedEvent.class, events.get(5));
        assertIsInstanceOf(ExchangeCreatedEvent.class, events.get(7));
        assertIsInstanceOf(ExchangeSentEvent.class, events.get(9));
        assertIsInstanceOf(ExchangeSentEvent.class, events.get(11));
        assertIsInstanceOf(ExchangeCompletedEvent.class, events.get(12));

        // this is the sent using the produce template to start the test
        assertIsInstanceOf(ExchangeSentEvent.class, events.get(13));

        context.stop();

        assertEquals(20, events.size());
        assertIsInstanceOf(CamelContextStoppingEvent.class, events.get(14));
        assertIsInstanceOf(RouteStoppedEvent.class, events.get(15));
        assertIsInstanceOf(RouteRemovedEvent.class, events.get(16));
        assertIsInstanceOf(RouteStoppedEvent.class, events.get(17));
        assertIsInstanceOf(RouteRemovedEvent.class, events.get(18));
        assertIsInstanceOf(CamelContextStoppedEvent.class, events.get(19));
    }

    public void testExchangeFailed() throws Exception {
        try {
            template.sendBody("direct:fail", "Hello World");
            fail("Should have thrown an exception");
        } catch (Exception e) {
            // expected
            assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
        }

        assertEquals(10, events.size());
        assertIsInstanceOf(CamelContextStartingEvent.class, events.get(0));
        assertIsInstanceOf(RouteAddedEvent.class, events.get(1));
        assertIsInstanceOf(RouteAddedEvent.class, events.get(2));
        assertIsInstanceOf(RouteStartedEvent.class, events.get(3));
        assertIsInstanceOf(RouteStartedEvent.class, events.get(4));
        assertIsInstanceOf(CamelContextStartedEvent.class, events.get(5));
        assertIsInstanceOf(ExchangeSendingEvent.class, events.get(6));
        assertIsInstanceOf(ExchangeCreatedEvent.class, events.get(7));
        assertIsInstanceOf(ExchangeFailedEvent.class, events.get(8));
        // this is the sent using the produce template to start the test
        assertIsInstanceOf(ExchangeSentEvent.class, events.get(9));

        context.stop();

        assertEquals(16, events.size());
        assertIsInstanceOf(CamelContextStoppingEvent.class, events.get(10));
        assertIsInstanceOf(RouteStoppedEvent.class, events.get(11));
        assertIsInstanceOf(RouteRemovedEvent.class, events.get(12));
        assertIsInstanceOf(RouteStoppedEvent.class, events.get(13));
        assertIsInstanceOf(RouteRemovedEvent.class, events.get(14));
        assertIsInstanceOf(CamelContextStoppedEvent.class, events.get(15));
    }

    public void testSuspendResume() throws Exception {
        assertEquals(6, events.size());
        assertIsInstanceOf(CamelContextStartingEvent.class, events.get(0));
        assertIsInstanceOf(RouteAddedEvent.class, events.get(1));
        assertIsInstanceOf(RouteAddedEvent.class, events.get(2));
        assertIsInstanceOf(RouteStartedEvent.class, events.get(3));
        assertIsInstanceOf(RouteStartedEvent.class, events.get(4));
        assertIsInstanceOf(CamelContextStartedEvent.class, events.get(5));

        context.suspend();

        assertEquals(8, events.size());
        assertIsInstanceOf(CamelContextSuspendingEvent.class, events.get(6));
        // notice direct component is not suspended (as they are internal)
        assertIsInstanceOf(CamelContextSuspendedEvent.class, events.get(7));

        context.resume();

        assertEquals(10, events.size());
        assertIsInstanceOf(CamelContextResumingEvent.class, events.get(8));
        assertIsInstanceOf(CamelContextResumedEvent.class, events.get(9));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("log:foo").to("mock:result");

                from("direct:fail").throwException(new IllegalArgumentException("Damn"));
            }
        };
    }

}
