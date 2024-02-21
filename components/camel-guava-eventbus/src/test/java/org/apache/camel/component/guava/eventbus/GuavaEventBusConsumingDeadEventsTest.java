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
package org.apache.camel.component.guava.eventbus;

import java.util.Date;

import com.google.common.eventbus.DeadEvent;
import com.google.common.eventbus.EventBus;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GuavaEventBusConsumingDeadEventsTest extends CamelTestSupport {

    @BindToRegistry("eventBus")
    EventBus eventBus = new EventBus();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("guava-eventbus:eventBus?listenerInterface=org.apache.camel.component.guava.eventbus.CustomListener")
                        .to("mock:customListenerEvents");

                from("guava-eventbus:eventBus?listenerInterface=org.apache.camel.component.guava.eventbus.DeadEventListener")
                        .to("mock:deadEvents");
            }
        };
    }

    @Test
    public void shouldForwardMessageToCamel() throws InterruptedException {
        // Given
        Date message = new Date();

        // When
        eventBus.post(message);

        // Then
        getMockEndpoint("mock:customListenerEvents").setExpectedMessageCount(0);
        MockEndpoint.assertIsSatisfied(context);
        getMockEndpoint("mock:deadEvents").setExpectedMessageCount(1);
        MockEndpoint.assertIsSatisfied(context);
        assertEquals(message,
                getMockEndpoint("mock:deadEvents").getExchanges().get(0).getIn().getBody(DeadEvent.class).getEvent());
    }

}
