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
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Service;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.junit.Before;
import org.junit.Test;

public class EventNotifierServiceStoppingFailedEventTest extends ContextTestSupport {

    private static List<CamelEvent> events = new ArrayList<>();
    private static String stopOrder;

    @Override
    @Before
    public void setUp() throws Exception {
        events.clear();
        super.setUp();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.init();
        context.addService(new MyService("A", false));
        context.addService(new MyService("B", true));
        context.addService(new MyService("C", false));

        context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            public void notify(CamelEvent event) throws Exception {
                events.add(event);
            }
        });
        return context;
    }

    @Test
    public void testStopWithFailure() throws Exception {
        stopOrder = "";

        context.stop();

        assertEquals("CBA", stopOrder);

        assertEquals(9, events.size());

        assertIsInstanceOf(CamelContextStartingEvent.class, events.get(0));
        assertIsInstanceOf(CamelContextRoutesStartingEvent.class, events.get(1));
        assertIsInstanceOf(CamelContextRoutesStartedEvent.class, events.get(2));
        assertIsInstanceOf(CamelContextStartedEvent.class, events.get(3));
        assertIsInstanceOf(CamelContextStoppingEvent.class, events.get(4));
        assertIsInstanceOf(CamelContextRoutesStoppingEvent.class, events.get(5));
        assertIsInstanceOf(CamelContextRoutesStoppedEvent.class, events.get(6));
        ServiceStopFailureEvent event = assertIsInstanceOf(ServiceStopFailureEvent.class, events.get(7));
        assertIsInstanceOf(CamelContextStoppedEvent.class, events.get(8));

        assertEquals("Fail B", event.getCause().getMessage());
        assertEquals("Failure to stop service: B due to Fail B", event.toString());
    }

    private static final class MyService implements Service {

        private String name;
        private boolean fail;

        private MyService(String name, boolean fail) {
            this.name = name;
            this.fail = fail;
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
            stopOrder = stopOrder + name;

            if (fail) {
                throw new IllegalArgumentException("Fail " + name);
            }
        }

        @Override
        public String toString() {
            return name;
        }
    }

}
