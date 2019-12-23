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

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class GuavaEventBusProducerTest extends CamelTestSupport {

    @BindToRegistry("eventBus")
    EventBus eventBus = new EventBus();

    Object receivedEvent;

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("guava-eventbus:eventBus");
            }
        };
    }

    @Test
    public void shouldReceiveMessageFromCamel() throws InterruptedException {
        // Given
        String message = "message";
        eventBus.register(this);

        // When
        sendBody("direct:start", message);

        // Then
        assertEquals(message, receivedEvent);
    }

    @Subscribe
    public void receiveEvent(Object event) {
        this.receivedEvent = event;
    }

}
