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
package org.apache.camel.component.metrics.routepolicy;

import java.util.Map;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * CAMEL-9226 - check metrics are counted correctly in multicast sub-routes
 */
public class MetricsRoutePolicyMulticastSubRouteTest extends CamelTestSupport {

    private MetricRegistry registry = new MetricRegistry();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        MetricsRoutePolicyFactory factory = new MetricsRoutePolicyFactory();
        factory.setUseJmx(false);
        factory.setMetricsRegistry(registry);
        context.addRoutePolicyFactory(factory);

        return context;
    }

    @Test
    public void testMetricsRoutePolicy() throws Exception {
        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:bar1").expectedMessageCount(1);
        getMockEndpoint("mock:bar2").expectedMessageCount(1);

        template.sendBody("direct:multicast", "Hello World");

        assertMockEndpointsSatisfied();

        // there should be 3 names
        assertEquals(3, registry.getNames().size());

        // there should be 3 Counters
        assertEquals(3, registry.getTimers().size());

        for (Map.Entry<String, Timer> timerEntry : registry.getTimers().entrySet()) {
            String metricName = timerEntry.getKey();
            Timer timer = timerEntry.getValue();
            // each count should be 1
            assertEquals("Count is wrong for " + metricName, 1, timer.getCount());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:foo").routeId("foo").to("mock:foo");

                from("direct:bar").routeId("bar").multicast().to("mock:bar1", "mock:bar2");

                from("direct:multicast").routeId("multicast").multicast().to("direct:foo", "direct:bar");

            }
        };
    }
}
