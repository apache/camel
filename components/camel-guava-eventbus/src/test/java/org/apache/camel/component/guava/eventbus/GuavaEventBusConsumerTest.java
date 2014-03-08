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
package org.apache.camel.component.guava.eventbus;

import java.util.Date;

import com.google.common.eventbus.EventBus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class GuavaEventBusConsumerTest extends CamelTestSupport {

    EventBus eventBus = new EventBus();

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("guava-eventbus:eventBus").to("mock:allEvents");
                from("guava-eventbus:eventBus").to("mock:multipliedConsumer");

                from("guava-eventbus:eventBus?eventClass=org.apache.camel.component.guava.eventbus.MessageWrapper").
                        to("mock:wrapperEvents");

                from("guava-eventbus:eventBus?listenerInterface=org.apache.camel.component.guava.eventbus.CustomListener").
                        to("mock:customListenerEvents");

                from("guava-eventbus:eventBus?listenerInterface=org.apache.camel.component.guava.eventbus.CustomMultiEventListener").
                        to("mock:customMultiEventListenerEvents");
            }
        };
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        registry.bind("eventBus", eventBus);
        return registry;
    }

    @Test
    public void shouldForwardMessageToCamel() throws InterruptedException {
        // Given
        String message = "message";

        // When
        eventBus.post(message);

        // Then
        getMockEndpoint("mock:allEvents").setExpectedMessageCount(1);
        assertMockEndpointsSatisfied();
        assertEquals(message, getMockEndpoint("mock:allEvents").getExchanges().get(0).getIn().getBody());
    }

    @Test
    public void shouldForwardMessageToMultipleConsumers() throws InterruptedException {
        // Given
        String message = "message";

        // When
        eventBus.post(message);

        // Then
        getMockEndpoint("mock:allEvents").setExpectedMessageCount(1);
        getMockEndpoint("mock:multipliedConsumer").setExpectedMessageCount(1);
        assertMockEndpointsSatisfied();
        assertEquals(message, getMockEndpoint("mock:allEvents").getExchanges().get(0).getIn().getBody());
        assertEquals(message, getMockEndpoint("mock:multipliedConsumer").getExchanges().get(0).getIn().getBody());
    }

    @Test
    public void shouldFilterForwardedMessages() throws InterruptedException {
        // Given
        MessageWrapper wrappedMessage = new MessageWrapper("message");

        // When
        eventBus.post(wrappedMessage);
        eventBus.post("String message.");

        // Then
        getMockEndpoint("mock:wrapperEvents").setExpectedMessageCount(1);
        assertMockEndpointsSatisfied();
        assertEquals(wrappedMessage, getMockEndpoint("mock:wrapperEvents").getExchanges().get(0).getIn().getBody());
    }

    @Test
    public void shouldUseCustomListener() throws InterruptedException {
        // Given
        MessageWrapper wrappedMessage = new MessageWrapper("message");

        // When
        eventBus.post(wrappedMessage);
        eventBus.post("String message.");

        // Then
        getMockEndpoint("mock:customListenerEvents").setExpectedMessageCount(1);
        assertMockEndpointsSatisfied();
        assertEquals(wrappedMessage, getMockEndpoint("mock:customListenerEvents").getExchanges().get(0).getIn().getBody());
    }

    @Test
    public void shouldSupportMultiEventCustomListener() throws InterruptedException {
        // Given
        String stringEvent = "stringEvent";
        Date dateEvent = new Date();

        // When
        eventBus.post(stringEvent);
        eventBus.post(dateEvent);

        // Then
        getMockEndpoint("mock:customMultiEventListenerEvents").setExpectedMessageCount(2);
        assertMockEndpointsSatisfied();
        assertEquals(stringEvent, getMockEndpoint("mock:customMultiEventListenerEvents").getExchanges().get(0).getIn().getBody());
        assertEquals(dateEvent, getMockEndpoint("mock:customMultiEventListenerEvents").getExchanges().get(1).getIn().getBody());
    }

}
