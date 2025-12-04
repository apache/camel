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

package org.apache.camel.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.junit.jupiter.api.Test;

public class StepEventNotifierOneOutputTest extends ContextTestSupport {

    private final MyEventNotifier notifier = new MyEventNotifier();

    @Test
    public void testStepEventNotifier() throws Exception {
        context.addService(notifier);
        context.getManagementStrategy().addEventNotifier(notifier);

        assertEquals(0, notifier.getEvents().size());

        getMockEndpoint("mock:foo").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        assertEquals(2, notifier.getEvents().size());
        assertIsInstanceOf(
                CamelEvent.StepStartedEvent.class, notifier.getEvents().get(0));
        assertIsInstanceOf(
                CamelEvent.StepCompletedEvent.class, notifier.getEvents().get(1));
        assertEquals("foo", ((CamelEvent.StepEvent) notifier.getEvents().get(0)).getStepId());
        assertEquals("foo", ((CamelEvent.StepEvent) notifier.getEvents().get(1)).getStepId());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").step("foo").to("mock:foo").end();
            }
        };
    }

    private static class MyEventNotifier extends EventNotifierSupport {

        private final List<CamelEvent> events = new ArrayList<>();

        public MyEventNotifier() {
            setIgnoreCamelContextEvents(true);
            setIgnoreExchangeEvents(true);
            setIgnoreRouteEvents(true);
            setIgnoreServiceEvents(true);
            setIgnoreStepEvents(false);
        }

        @Override
        public void notify(CamelEvent event) {
            events.add(event);
        }

        public List<CamelEvent> getEvents() {
            return events;
        }
    }
}
