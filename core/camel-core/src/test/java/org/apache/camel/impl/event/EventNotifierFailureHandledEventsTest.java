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
import org.apache.camel.DelegateProcessor;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.processor.SendProcessor;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.junit.Before;
import org.junit.Test;

public class EventNotifierFailureHandledEventsTest extends ContextTestSupport {

    private static List<CamelEvent> events = new ArrayList<>();

    @Override
    @Before
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
            public void notify(CamelEvent event) throws Exception {
                events.add(event);
            }
        });
        return context;
    }

    @Test
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

        assertEquals(14, events.size());
        assertIsInstanceOf(CamelContextStartingEvent.class, events.get(0));
        assertIsInstanceOf(CamelContextRoutesStartingEvent.class, events.get(1));
        assertIsInstanceOf(RouteAddedEvent.class, events.get(2));
        assertIsInstanceOf(RouteStartedEvent.class, events.get(3));
        assertIsInstanceOf(CamelContextRoutesStartedEvent.class, events.get(4));
        assertIsInstanceOf(CamelContextStartedEvent.class, events.get(5));
        assertIsInstanceOf(ExchangeSendingEvent.class, events.get(6));
        assertIsInstanceOf(ExchangeCreatedEvent.class, events.get(7));

        ExchangeFailureHandlingEvent e0 = assertIsInstanceOf(ExchangeFailureHandlingEvent.class, events.get(8));
        assertEquals("should be DLC", true, e0.isDeadLetterChannel());
        assertEquals("mock://dead", e0.getDeadLetterUri());

        assertIsInstanceOf(ExchangeSendingEvent.class, events.get(9));
        assertIsInstanceOf(ExchangeSentEvent.class, events.get(10));

        ExchangeFailureHandledEvent e = assertIsInstanceOf(ExchangeFailureHandledEvent.class, events.get(11));
        assertEquals("should be DLC", true, e.isDeadLetterChannel());
        assertTrue("should be marked as failure handled", e.isHandled());
        assertFalse("should not be continued", e.isContinued());
        Processor fh = e.getFailureHandler();
        if (fh.getClass().getName().endsWith("ProcessorToReactiveProcessorBridge")) {
            fh = ((DelegateProcessor)fh).getProcessor();
        }
        SendProcessor send = assertIsInstanceOf(SendProcessor.class, fh);
        assertEquals("mock://dead", send.getDestination().getEndpointUri());
        assertEquals("mock://dead", e.getDeadLetterUri());

        // dead letter channel will mark the exchange as completed
        assertIsInstanceOf(ExchangeCompletedEvent.class, events.get(12));
        // and the last event should be the direct:start
        assertIsInstanceOf(ExchangeSentEvent.class, events.get(13));
        ExchangeSentEvent sent = (ExchangeSentEvent)events.get(13);
        assertEquals("direct://start", sent.getEndpoint().getEndpointUri());
    }

    @Test
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

        assertEquals(14, events.size());
        assertIsInstanceOf(CamelContextStartingEvent.class, events.get(0));
        assertIsInstanceOf(CamelContextRoutesStartingEvent.class, events.get(1));
        assertIsInstanceOf(RouteAddedEvent.class, events.get(2));
        assertIsInstanceOf(RouteStartedEvent.class, events.get(3));
        assertIsInstanceOf(CamelContextRoutesStartedEvent.class, events.get(4));
        assertIsInstanceOf(CamelContextStartedEvent.class, events.get(5));
        assertIsInstanceOf(ExchangeSendingEvent.class, events.get(6));
        assertIsInstanceOf(ExchangeCreatedEvent.class, events.get(7));

        ExchangeFailureHandlingEvent e0 = assertIsInstanceOf(ExchangeFailureHandlingEvent.class, events.get(8));
        assertEquals("should NOT be DLC", false, e0.isDeadLetterChannel());

        assertIsInstanceOf(ExchangeSendingEvent.class, events.get(9));
        assertIsInstanceOf(ExchangeSentEvent.class, events.get(10));

        ExchangeFailureHandledEvent e = assertIsInstanceOf(ExchangeFailureHandledEvent.class, events.get(11));
        assertEquals("should NOT be DLC", false, e.isDeadLetterChannel());
        assertTrue("should be marked as failure handled", e.isHandled());
        assertFalse("should not be continued", e.isContinued());

        // onException will handle the exception
        assertIsInstanceOf(ExchangeCompletedEvent.class, events.get(12));
        // and the last event should be the direct:start
        assertIsInstanceOf(ExchangeSentEvent.class, events.get(13));
        ExchangeSentEvent sent = (ExchangeSentEvent)events.get(13);
        assertEquals("direct://start", sent.getEndpoint().getEndpointUri());
    }

    @Test
    public void testExchangeDoTryDoCatch() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").doTry().throwException(new IllegalArgumentException("Damn")).doCatch(IllegalArgumentException.class).to("mock:dead").end();
            }
        });
        context.start();

        getMockEndpoint("mock:dead").expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();

        assertEquals(14, events.size());
        assertIsInstanceOf(CamelContextStartingEvent.class, events.get(0));
        assertIsInstanceOf(CamelContextRoutesStartingEvent.class, events.get(1));
        assertIsInstanceOf(RouteAddedEvent.class, events.get(2));
        assertIsInstanceOf(RouteStartedEvent.class, events.get(3));
        assertIsInstanceOf(CamelContextRoutesStartedEvent.class, events.get(4));
        assertIsInstanceOf(CamelContextStartedEvent.class, events.get(5));
        assertIsInstanceOf(ExchangeSendingEvent.class, events.get(6));
        assertIsInstanceOf(ExchangeCreatedEvent.class, events.get(7));

        ExchangeFailureHandlingEvent e0 = assertIsInstanceOf(ExchangeFailureHandlingEvent.class, events.get(8));
        assertEquals("should NOT be DLC", false, e0.isDeadLetterChannel());

        assertIsInstanceOf(ExchangeSendingEvent.class, events.get(9));
        assertIsInstanceOf(ExchangeSentEvent.class, events.get(10));

        ExchangeFailureHandledEvent e = assertIsInstanceOf(ExchangeFailureHandledEvent.class, events.get(11));
        assertEquals("should NOT be DLC", false, e.isDeadLetterChannel());
        assertFalse("should not be marked as failure handled as it was continued instead", e.isHandled());
        assertTrue("should be continued", e.isContinued());

        // onException will handle the exception
        assertIsInstanceOf(ExchangeCompletedEvent.class, events.get(12));
        // and the last event should be the direct:start
        assertIsInstanceOf(ExchangeSentEvent.class, events.get(13));
        ExchangeSentEvent sent = (ExchangeSentEvent)events.get(13);
        assertEquals("direct://start", sent.getEndpoint().getEndpointUri());
    }

}
