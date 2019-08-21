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
package org.apache.camel.util;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.Type;
import org.apache.camel.support.EventNotifierSupport;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EventHelperTest {

    @Test
    public void testStartStopEventsReceived() throws Exception {
        MyEventNotifier en1 = new MyEventNotifier();
        MyEventNotifier en2 = new MyEventNotifier();

        CamelContext camelContext = new DefaultCamelContext();
        camelContext.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:start").routeId("route-1").to("mock:end");
            }

        });

        camelContext.getManagementStrategy().addEventNotifier(en1);
        camelContext.getManagementStrategy().addEventNotifier(en2);

        camelContext.start();
        camelContext.stop();

        assertEquals(1, en1.routeStartedEvent.get());
        assertEquals(1, en1.routeStoppedEvent.get());
        assertEquals(1, en1.camelContextStoppingEvent.get());

        assertEquals(1, en2.routeStartedEvent.get());
        assertEquals(1, en2.routeStoppedEvent.get());
        assertEquals(1, en2.camelContextStoppingEvent.get());
    }

    @Test
    public void testStartStopEventsReceivedWhenTheFirstOneIgnoreTheseEvents() throws Exception {
        MyEventNotifier en1 = new MyEventNotifier();
        en1.setIgnoreRouteEvents(true);
        en1.setIgnoreCamelContextEvents(true);
        MyEventNotifier en2 = new MyEventNotifier();

        CamelContext camelContext = new DefaultCamelContext();
        camelContext.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:start").routeId("route-1").to("mock:end");
            }

        });

        camelContext.getManagementStrategy().addEventNotifier(en1);
        camelContext.getManagementStrategy().addEventNotifier(en2);

        camelContext.start();
        camelContext.stop();

        assertEquals(0, en1.routeStartedEvent.get());
        assertEquals(0, en1.routeStoppedEvent.get());
        assertEquals(0, en1.camelContextStoppingEvent.get());

        assertEquals(1, en2.routeStartedEvent.get());
        assertEquals(1, en2.routeStoppedEvent.get());
        assertEquals(1, en2.camelContextStoppingEvent.get());
    }

    @Test
    public void testStartStopEventsReceivedWhenTheSecondOneIgnoreTheseEvents() throws Exception {
        MyEventNotifier en1 = new MyEventNotifier();
        MyEventNotifier en2 = new MyEventNotifier();
        en2.setIgnoreRouteEvents(true);
        en2.setIgnoreCamelContextEvents(true);

        CamelContext camelContext = new DefaultCamelContext();
        camelContext.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:start").routeId("route-1").to("mock:end");
            }

        });

        camelContext.getManagementStrategy().addEventNotifier(en1);
        camelContext.getManagementStrategy().addEventNotifier(en2);

        camelContext.start();
        camelContext.stop();

        assertEquals(1, en1.routeStartedEvent.get());
        assertEquals(1, en1.routeStoppedEvent.get());
        assertEquals(1, en1.camelContextStoppingEvent.get());

        assertEquals(0, en2.routeStartedEvent.get());
        assertEquals(0, en2.routeStoppedEvent.get());
        assertEquals(0, en2.camelContextStoppingEvent.get());
    }

    static class MyEventNotifier extends EventNotifierSupport {

        AtomicInteger routeStartedEvent = new AtomicInteger();
        AtomicInteger routeStoppedEvent = new AtomicInteger();
        AtomicInteger camelContextStoppingEvent = new AtomicInteger();

        @Override
        public void notify(CamelEvent event) throws Exception {
            if (event.getType() == Type.RouteStarted) {
                routeStartedEvent.incrementAndGet();
            } else if (event.getType() == Type.RouteStopped) {
                routeStoppedEvent.incrementAndGet();
            } else if (event.getType() == Type.CamelContextStopping) {
                camelContextStoppingEvent.incrementAndGet();
            }
        }

        @Override
        public boolean isEnabled(CamelEvent event) {
            return true;
        }

        @Override
        protected void doStart() throws Exception {
        }

        @Override
        protected void doStop() throws Exception {
        }
    }
}
