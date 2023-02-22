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
import java.util.Date;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class EventNotifierExchangeCompletedTest extends ContextTestSupport {

    private final List<CamelEvent> events = new ArrayList<>();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext(createRegistry());
        context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            public void notify(CamelEvent event) throws Exception {
                events.add(event);
            }

            public boolean isEnabled(CamelEvent event) {
                // we only want the completed event
                return event instanceof ExchangeCompletedEvent;
                // you can add additional filtering such as the exchange
                // should be from a specific endpoint or route
                // just return true for the events you like
            }
        });
        return context;
    }

    @Test
    public void testExchangeCompleted() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        assertEquals(1, events.size());

        // get the event
        ExchangeCompletedEvent event = (ExchangeCompletedEvent) events.get(0);
        assertNotNull(event.getExchange());
        assertNotNull(event.getExchange().getFromEndpoint());
        assertEquals("direct://start", event.getExchange().getFromEndpoint().getEndpointUri());

        // grab the created timestamp
        long created = event.getExchange().getCreated();
        assertTrue(created > 0);

        // calculate elapsed time
        Date now = new Date();
        long elapsed = now.getTime() - created;
        assertTrue(elapsed > 400, "Should be > 400, was: " + elapsed);

        log.info("Elapsed time in millis: {}", elapsed);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("log:foo").to("direct:bar").to("mock:result");

                from("direct:bar").delay(500).to("mock:bar");
            }
        };
    }

}
