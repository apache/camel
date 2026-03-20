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
package org.apache.camel.component.camelevent;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.event.AbstractContextEvent;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EventCustomEventClassTest extends CamelTestSupport {

    /**
     * Custom event for testing.
     */
    public static class MyCustomEvent extends AbstractContextEvent {
        private static final long serialVersionUID = 1L;
        private final String payload;

        public MyCustomEvent(CamelContext context, String payload) {
            super(context);
            this.payload = payload;
        }

        @Override
        public Type getType() {
            return Type.Custom;
        }

        public String getPayload() {
            return payload;
        }
    }

    /**
     * Another custom event for verifying filtering.
     */
    public static class OtherCustomEvent extends AbstractContextEvent {
        private static final long serialVersionUID = 1L;

        public OtherCustomEvent(CamelContext context) {
            super(context);
        }

        @Override
        public Type getType() {
            return Type.Custom;
        }
    }

    @Test
    void testCustomEventClassFilter() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:customFiltered");
        mock.expectedMinimumMessageCount(1);

        // Fire a MyCustomEvent - should be captured
        context.getManagementStrategy().notify(new MyCustomEvent(context, "test-payload"));

        // Fire an OtherCustomEvent - should NOT be captured
        context.getManagementStrategy().notify(new OtherCustomEvent(context));

        mock.assertIsSatisfied();

        // All received events should be MyCustomEvent
        mock.getExchanges().forEach(e -> {
            CamelEvent event = e.getIn().getBody(CamelEvent.class);
            assertInstanceOf(MyCustomEvent.class, event,
                    "Should only receive MyCustomEvent, but got " + event.getClass().getName());
        });
    }

    @Test
    void testCustomEventWithoutClassFilter() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:allCustom");
        mock.expectedMinimumMessageCount(2);

        // Fire both custom events - both should be captured
        context.getManagementStrategy().notify(new MyCustomEvent(context, "payload1"));
        context.getManagementStrategy().notify(new OtherCustomEvent(context));

        mock.assertIsSatisfied();

        // Should have received both event types
        boolean hasMyCustom = mock.getExchanges().stream()
                .anyMatch(e -> e.getIn().getBody(CamelEvent.class) instanceof MyCustomEvent);
        boolean hasOtherCustom = mock.getExchanges().stream()
                .anyMatch(e -> e.getIn().getBody(CamelEvent.class) instanceof OtherCustomEvent);
        assertTrue(hasMyCustom, "Should have MyCustomEvent");
        assertTrue(hasOtherCustom, "Should have OtherCustomEvent");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("event:Custom?customEventClass=%s",
                        MyCustomEvent.class.getName())
                        .routeId("customFilteredConsumer")
                        .to("mock:customFiltered");

                from("event:Custom")
                        .routeId("allCustomConsumer")
                        .to("mock:allCustom");
            }
        };
    }
}
