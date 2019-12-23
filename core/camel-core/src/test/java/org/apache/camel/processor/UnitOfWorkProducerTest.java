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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.ExchangeCompletedEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.junit.After;
import org.junit.Test;

public class UnitOfWorkProducerTest extends ContextTestSupport {

    private static List<CamelEvent> events = new ArrayList<>();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext(createRegistry());
        context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            public void notify(CamelEvent event) throws Exception {
                events.add(event);
            }

            public boolean isEnabled(CamelEvent event) {
                return event instanceof ExchangeCompletedEvent;
            }
        });
        return context;
    }

    @Override
    @After
    public void tearDown() throws Exception {
        events.clear();
        super.tearDown();
    }

    @Test
    public void testSedaBasedUnitOfWorkProducer() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        // sending to seda should cause 2 completed events
        template.sendBody("seda:foo", "Hello World");

        assertMockEndpointsSatisfied();

        oneExchangeDone.matchesMockWaitTime();

        // there should be 2 completed events
        // one for the producer template, and another for the Camel route
        assertEquals(2, events.size());
    }

    @Test
    public void testDirectBasedUnitOfWorkProducer() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        // sending to direct should cause 1 completed events
        template.sendBody("direct:bar", "Hello World");

        assertMockEndpointsSatisfied();

        oneExchangeDone.matchesMockWaitTime();

        // there should be 1 completed events as direct endpoint will be like a
        // direct method call
        // and the UoW will be re-used
        assertEquals(1, events.size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:foo").to("mock:result");

                from("direct:bar").to("mock:result");
            }
        };
    }
}
