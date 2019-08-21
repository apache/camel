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
package org.apache.camel.component.microprofile.metrics.route.policy;

import java.util.SortedMap;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.microprofile.metrics.MicroProfileMetricsTestSupport;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.Timer;
import org.junit.Test;

public class MicroProfileMetricsRoutePolicyMulticastSubRouteTest extends MicroProfileMetricsTestSupport {

    @Test
    public void testMetricsRoutePolicy() throws Exception {
        int count = 10;
        getMockEndpoint("mock:foo").expectedMessageCount(count);
        getMockEndpoint("mock:bar1").expectedMessageCount(count);
        getMockEndpoint("mock:bar2").expectedMessageCount(count);

        for (int i = 0; i < count; i++) {
            template.sendBody("direct:multicast", null);
        }

        assertMockEndpointsSatisfied();

        SortedMap<MetricID, Timer> timers = metricRegistry.getTimers();
        assertEquals(3, timers.size());

        timers.forEach((metricId, timer) -> {
            assertEquals(count, timer.getCount());
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

    @Override
    protected CamelContext createCamelContext() throws Exception {
        MicroProfileMetricsRoutePolicyFactory factory = new MicroProfileMetricsRoutePolicyFactory();
        factory.setMetricRegistry(metricRegistry);

        CamelContext camelContext = super.createCamelContext();
        camelContext.addRoutePolicyFactory(factory);
        return camelContext;
    }
}
