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

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.junit.Before;
import org.junit.Test;

public class EventNotifierEventsTest extends ContextTestSupport {

    private static List<CamelEvent> events = new ArrayList<>();

    @Override
    @Before
    public void setUp() throws Exception {
        events.clear();
        super.setUp();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext(createRegistry());
        context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            public void notify(CamelEvent event) throws Exception {
                events.add(event);
            }
        });
        return context;
    }

    @Test
    public void testExchangeDone() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        assertEquals(16, events.size());
        assertIsInstanceOf(CamelContextStartingEvent.class, events.get(0));
        assertIsInstanceOf(CamelContextRoutesStartingEvent.class, events.get(1));
        assertIsInstanceOf(RouteAddedEvent.class, events.get(2));
        assertIsInstanceOf(RouteAddedEvent.class, events.get(3));
        assertIsInstanceOf(RouteStartedEvent.class, events.get(4));
        assertIsInstanceOf(RouteStartedEvent.class, events.get(5));
        assertIsInstanceOf(CamelContextRoutesStartedEvent.class, events.get(6));
        assertIsInstanceOf(CamelContextStartedEvent.class, events.get(7));
        assertIsInstanceOf(ExchangeCreatedEvent.class, events.get(9));
        assertIsInstanceOf(ExchangeSendingEvent.class, events.get(10));
        assertIsInstanceOf(ExchangeSentEvent.class, events.get(11));
        assertIsInstanceOf(ExchangeSendingEvent.class, events.get(12));
        assertIsInstanceOf(ExchangeSentEvent.class, events.get(13));
        assertIsInstanceOf(ExchangeCompletedEvent.class, events.get(14));

        // this is the sent using the produce template to start the test
        assertIsInstanceOf(ExchangeSentEvent.class, events.get(15));

        context.stop();

        assertEquals(24, events.size());
        assertIsInstanceOf(CamelContextStoppingEvent.class, events.get(16));
        assertIsInstanceOf(CamelContextRoutesStoppingEvent.class, events.get(17));
        assertIsInstanceOf(RouteStoppedEvent.class, events.get(18));
        assertIsInstanceOf(RouteRemovedEvent.class, events.get(19));
        assertIsInstanceOf(RouteStoppedEvent.class, events.get(20));
        assertIsInstanceOf(RouteRemovedEvent.class, events.get(21));
        assertIsInstanceOf(CamelContextRoutesStoppedEvent.class, events.get(22));
        assertIsInstanceOf(CamelContextStoppedEvent.class, events.get(23));
    }

    @Test
    public void testExchangeFailed() throws Exception {
        try {
            template.sendBody("direct:fail", "Hello World");
            fail("Should have thrown an exception");
        } catch (Exception e) {
            // expected
            assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
        }

        assertEquals(12, events.size());
        assertIsInstanceOf(CamelContextStartingEvent.class, events.get(0));
        assertIsInstanceOf(CamelContextRoutesStartingEvent.class, events.get(1));
        assertIsInstanceOf(RouteAddedEvent.class, events.get(2));
        assertIsInstanceOf(RouteAddedEvent.class, events.get(3));
        assertIsInstanceOf(RouteStartedEvent.class, events.get(4));
        assertIsInstanceOf(RouteStartedEvent.class, events.get(5));
        assertIsInstanceOf(CamelContextRoutesStartedEvent.class, events.get(6));
        assertIsInstanceOf(CamelContextStartedEvent.class, events.get(7));
        assertIsInstanceOf(ExchangeSendingEvent.class, events.get(8));
        assertIsInstanceOf(ExchangeCreatedEvent.class, events.get(9));
        assertIsInstanceOf(ExchangeFailedEvent.class, events.get(10));
        // this is the sent using the produce template to start the test
        assertIsInstanceOf(ExchangeSentEvent.class, events.get(11));

        context.stop();

        assertEquals(20, events.size());
        assertIsInstanceOf(CamelContextStoppingEvent.class, events.get(12));
        assertIsInstanceOf(CamelContextRoutesStoppingEvent.class, events.get(13));
        assertIsInstanceOf(RouteStoppedEvent.class, events.get(14));
        assertIsInstanceOf(RouteRemovedEvent.class, events.get(15));
        assertIsInstanceOf(RouteStoppedEvent.class, events.get(16));
        assertIsInstanceOf(RouteRemovedEvent.class, events.get(17));
        assertIsInstanceOf(CamelContextRoutesStoppedEvent.class, events.get(18));
        assertIsInstanceOf(CamelContextStoppedEvent.class, events.get(19));
    }

    @Test
    public void testSuspendResume() throws Exception {
        assertEquals(8, events.size());
        assertIsInstanceOf(CamelContextStartingEvent.class, events.get(0));
        assertIsInstanceOf(CamelContextRoutesStartingEvent.class, events.get(1));
        assertIsInstanceOf(RouteAddedEvent.class, events.get(2));
        assertIsInstanceOf(RouteAddedEvent.class, events.get(3));
        assertIsInstanceOf(RouteStartedEvent.class, events.get(4));
        assertIsInstanceOf(RouteStartedEvent.class, events.get(5));
        assertIsInstanceOf(CamelContextRoutesStartedEvent.class, events.get(6));
        assertIsInstanceOf(CamelContextStartedEvent.class, events.get(7));

        context.suspend();

        assertEquals(10, events.size());
        assertIsInstanceOf(CamelContextSuspendingEvent.class, events.get(8));
        // notice direct component is not suspended (as they are internal)
        assertIsInstanceOf(CamelContextSuspendedEvent.class, events.get(9));

        context.resume();

        assertEquals(12, events.size());
        assertIsInstanceOf(CamelContextResumingEvent.class, events.get(10));
        assertIsInstanceOf(CamelContextResumedEvent.class, events.get(11));
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
