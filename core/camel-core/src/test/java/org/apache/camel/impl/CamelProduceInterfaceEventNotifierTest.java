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
package org.apache.camel.impl;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Produce;
import org.apache.camel.spi.CamelBeanPostProcessor;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CamelProduceInterfaceEventNotifierTest extends ContextTestSupport {

    private final List<CamelEvent> events = new ArrayList<>();

    private CamelBeanPostProcessor postProcessor;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext(createRegistry());
        context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            public void notify(CamelEvent event) throws Exception {
                if (event instanceof CamelEvent.ExchangeSendingEvent || event instanceof CamelEvent.ExchangeSentEvent) {
                    events.add(event);
                }
            }
        });
        return context;
    }

    @Test
    public void testPostProcessor() throws Exception {
        events.clear();

        int before = events.size();
        assertEquals(0, before);

        MySender sender = new MySender();

        postProcessor.postProcessBeforeInitialization(sender, "foo");
        postProcessor.postProcessAfterInitialization(sender, "foo");

        getMockEndpoint("mock:result").expectedMessageCount(1);

        sender.hello.sayHello("Hello World");

        assertMockEndpointsSatisfied();

        int after = events.size();
        // should be 2 events
        assertEquals(2, after);
        boolean b1 = events.get(0) instanceof CamelEvent.ExchangeSendingEvent;
        assertTrue(b1);
        boolean b = events.get(1) instanceof CamelEvent.ExchangeSentEvent;
        assertTrue(b);
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        postProcessor = PluginHelper.getBeanPostProcessor(context);
    }

    interface FooService {

        void sayHello(String hello);
    }

    static class MySender {

        @Produce("mock:result")
        FooService hello;

    }

}
