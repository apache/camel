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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EventNotifierEdgeCasesTest extends ContextTestSupport {

    private static class MyNotifier extends EventNotifierSupport {
        final List<CamelEvent> events = new CopyOnWriteArrayList<>();
        final AtomicInteger starts = new AtomicInteger();
        final AtomicInteger stops = new AtomicInteger();

        @Override
        public void notify(CamelEvent event) {
            events.add(event);
        }

        @Override
        protected void doStart() throws Exception {
            starts.incrementAndGet();
        }

        @Override
        protected void doStop() throws Exception {
            stops.incrementAndGet();
        }

        long count(Class<?> type) {
            return events.stream().filter(type::isInstance).count();
        }
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        return new DefaultCamelContext(createCamelRegistry());
    }

    @Test
    public void testExceptionFromIsEnabledDoesNotAffectRouting() throws Exception {
        MyNotifier notifier = new MyNotifier() {
            @Override
            public boolean isEnabled(CamelEvent event) {
                if (event instanceof CamelEvent.StepStartedEvent || event instanceof CamelEvent.ExchangeSendingEvent
                        || event instanceof CamelEvent.RouteStartedEvent) {
                    throw new IllegalStateException("Forced");
                }
                return true;
            }
        };
        context.getManagementStrategy().addEventNotifier(notifier);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").step("foo").to("mock:result").end();
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSentEventsWhenSendingEventsAreIgnored() throws Exception {
        MyNotifier notifier = new MyNotifier();
        notifier.setIgnoreExchangeSendingEvents(true);
        assertSentEvents(notifier);
        assertEquals(0, notifier.count(CamelEvent.ExchangeSendingEvent.class));
    }

    @Test
    public void testSentEventsWhenSendingEventsAreNotEnabled() throws Exception {
        MyNotifier notifier = new MyNotifier() {
            @Override
            public boolean isEnabled(CamelEvent event) {
                return event instanceof CamelEvent.ExchangeSentEvent;
            }
        };
        assertSentEvents(notifier);
    }

    private static void assertSentEvents(MyNotifier notifier) throws Exception {
        // use a plain camel context without other event notifiers (such as the one from the test support)
        try (CamelContext camel = new DefaultCamelContext()) {
            camel.getManagementStrategy().addEventNotifier(notifier);
            camel.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start").to("mock:result");
                }
            });
            camel.start();

            MockEndpoint mock = camel.getEndpoint("mock:result", MockEndpoint.class);
            mock.expectedMessageCount(1);
            camel.createProducerTemplate().sendBody("direct:start", "Hello World");
            mock.assertIsSatisfied();

            // sent to direct:start and mock:result
            assertEquals(2, notifier.count(CamelEvent.ExchangeSentEvent.class));
        }
    }

    @Test
    public void testIgnoreRedeliveryEvents() throws Exception {
        MyNotifier ignoreRedelivery = new MyNotifier();
        ignoreRedelivery.setIgnoreExchangeRedeliveryEvents(true);
        MyNotifier ignoreFailed = new MyNotifier();
        ignoreFailed.setIgnoreExchangeFailedEvents(true);
        context.getManagementStrategy().addEventNotifier(ignoreRedelivery);
        context.getManagementStrategy().addEventNotifier(ignoreFailed);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("mock:dead").maximumRedeliveries(2).redeliveryDelay(0));

                from("direct:start").throwException(new IllegalArgumentException("Forced"));
            }
        });
        context.start();

        getMockEndpoint("mock:dead").expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();

        assertEquals(0, ignoreRedelivery.count(CamelEvent.ExchangeRedeliveryEvent.class));
        assertEquals(2, ignoreFailed.count(CamelEvent.ExchangeRedeliveryEvent.class));
    }

    @Test
    public void testNotifierAddedAfterStartIsStarted() throws Exception {
        context.start();

        MyNotifier notifier = new MyNotifier();
        context.getManagementStrategy().addEventNotifier(notifier);
        assertTrue(notifier.isStarted());
        assertEquals(1, notifier.starts.get());

        assertTrue(context.getManagementStrategy().removeEventNotifier(notifier));
        assertFalse(notifier.isStarted());
        assertEquals(1, notifier.stops.get());
    }
}
