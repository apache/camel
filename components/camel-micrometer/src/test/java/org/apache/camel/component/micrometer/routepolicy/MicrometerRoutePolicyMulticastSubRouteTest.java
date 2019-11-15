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
package org.apache.camel.component.micrometer.routepolicy;

import java.util.List;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 * CAMEL-9226 - check metrics are counted correctly in multicast sub-routes
 */
public class MicrometerRoutePolicyMulticastSubRouteTest extends AbstractMicrometerRoutePolicyTest {

    @Test
    public void testMetricsRoutePolicy() throws Exception {

        int count = 10;
        getMockEndpoint("mock:foo").expectedMessageCount(count);
        getMockEndpoint("mock:bar1").expectedMessageCount(count);
        getMockEndpoint("mock:bar2").expectedMessageCount(count);

        for (int i = 0; i < count; i++) {
            template.sendBody("direct:multicast", "Hello World");
        }

        assertMockEndpointsSatisfied();

        // there should be 3 names
        List<Meter> meters = meterRegistry.getMeters();
        assertEquals(3, meters.size());


        meters.forEach(meter -> {
            Timer timer = (Timer) meter;
            assertEquals("Timer " + timer.getId() + " should have count of " + count,  count, timer.count());
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:foo").routeId("foo").to("mock:foo");

                from("direct:bar").routeId("bar").multicast().to("mock:bar1", "mock:bar2");

                from("direct:multicast").routeId("multicast").multicast().to("direct:foo", "direct:bar");

            }
        };
    }
}
