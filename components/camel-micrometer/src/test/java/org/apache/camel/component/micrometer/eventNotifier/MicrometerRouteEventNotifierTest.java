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
package org.apache.camel.component.micrometer.eventNotifier;

import io.micrometer.core.instrument.Gauge;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.micrometer.eventnotifier.AbstractMicrometerEventNotifier;
import org.apache.camel.component.micrometer.eventnotifier.MicrometerRouteEventNotifier;
import org.junit.Test;

import static org.apache.camel.component.micrometer.MicrometerConstants.DEFAULT_CAMEL_ROUTES_ADDED;
import static org.apache.camel.component.micrometer.MicrometerConstants.DEFAULT_CAMEL_ROUTES_RUNNING;

/**
 * @author Christian Ohr
 */
public class MicrometerRouteEventNotifierTest extends AbstractMicrometerEventNotifierTest {

    private static final String ROUTE_ID = "test";

    @Override
    protected AbstractMicrometerEventNotifier<?> getEventNotifier() {
        return new MicrometerRouteEventNotifier();
    }

    @Test
    public void testCamelRouteEvents() throws Exception {
        Gauge added = meterRegistry.find(DEFAULT_CAMEL_ROUTES_ADDED).gauge();
        Gauge running = meterRegistry.find(DEFAULT_CAMEL_ROUTES_RUNNING).gauge();
        assertEquals(0.0d, added.value(), 0.0001d);
        assertEquals(0.0d, running.value(), 0.0001d);
        context.addRoutes(new TestRoute());
        assertEquals(1.0d, added.value(), 0.0001d);
        assertEquals(1.0d, running.value(), 0.0001d);
        context.getRouteController().stopRoute(ROUTE_ID);
        assertEquals(1.0d, added.value(), 0.0001d);
        assertEquals(0.0d, running.value(), 0.0001d);
        context.removeRoute(ROUTE_ID);
        assertEquals(0.0d, added.value(), 0.0001d);
        assertEquals(0.0d, running.value(), 0.0001d);
    }


    private class TestRoute extends RouteBuilder {
        @Override
        public void configure() throws Exception {
            from("direct:in").routeId(ROUTE_ID).to("mock:out");
        }
    }
}
