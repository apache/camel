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

package org.apache.camel.management;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Ordered;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

@DisabledOnOs(OS.AIX)
public class AddEventNotifierOrderedTest extends ContextTestSupport {

    private static final List<String> events = new ArrayList<>();

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        events.clear();
        super.setUp();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext(createCamelRegistry());

        context.getManagementStrategy().addEventNotifier(new MyNotifier("notifier1", -100));
        context.getManagementStrategy().addEventNotifier(new MyNotifier("notifier2", -200));

        return context;
    }

    @Test
    public void testAdd() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();

        Assertions.assertEquals(2, events.size());
        Assertions.assertEquals("notifier2", events.get(0));
        Assertions.assertEquals("notifier1", events.get(1));

        // add new notifier after started
        resetMocks();
        events.clear();

        context.getManagementStrategy().addEventNotifier(new MyNotifier("notifier3", -300));

        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();

        Assertions.assertEquals(3, events.size());
        Assertions.assertEquals("notifier3", events.get(0));
        Assertions.assertEquals("notifier2", events.get(1));
        Assertions.assertEquals("notifier1", events.get(2));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("mock:result");
            }
        };
    }

    private static class MyNotifier extends EventNotifierSupport implements Ordered {

        private final String name;
        private final int order;

        private MyNotifier(String name, int order) {
            this.name = name;
            this.order = order;
        }

        @Override
        public int getOrder() {
            return order;
        }

        @Override
        public void notify(CamelEvent event) throws Exception {
            if (event instanceof CamelEvent.ExchangeCompletedEvent) {
                events.add(name);
            }
        }
    }
}
