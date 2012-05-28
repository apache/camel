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
package org.apache.camel.processor.async;

import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.management.event.ExchangeSendingEvent;
import org.apache.camel.management.event.ExchangeSentEvent;
import org.apache.camel.support.EventNotifierSupport;

/**
 * @version 
 */
public class AsyncEndpointEventNotifierSendingTest extends ContextTestSupport {

    private final List<EventObject> events = new ArrayList<EventObject>();

    public void testAsyncEndpointEventNotifier() throws Exception {
        getMockEndpoint("mock:before").expectedBodiesReceived("Hello Camel");
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye Camel");

        String reply = template.requestBody("direct:start", "Hello Camel", String.class);
        assertEquals("Bye Camel", reply);

        assertMockEndpointsSatisfied();

        assertEquals(8, events.size());

        assertIsInstanceOf(ExchangeSendingEvent.class, events.get(0));
        assertIsInstanceOf(ExchangeSendingEvent.class, events.get(1));
        assertIsInstanceOf(ExchangeSentEvent.class, events.get(2));
        assertIsInstanceOf(ExchangeSendingEvent.class, events.get(3));
        assertIsInstanceOf(ExchangeSentEvent.class, events.get(4));
        assertIsInstanceOf(ExchangeSendingEvent.class, events.get(5));
        assertIsInstanceOf(ExchangeSentEvent.class, events.get(6));
        assertIsInstanceOf(ExchangeSentEvent.class, events.get(7));
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext(createRegistry());
        context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            public void notify(EventObject event) throws Exception {
                events.add(event);
            }

            public boolean isEnabled(EventObject event) {
                return event instanceof ExchangeSendingEvent || event instanceof ExchangeSentEvent;
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

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.addComponent("async", new MyAsyncComponent());

                from("direct:start")
                        .to("mock:before")
                        .to("async:bye:camel?delay=250")
                        .to("mock:result");
            }
        };
    }

}