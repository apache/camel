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
package org.apache.camel.component.microprofile.metrics;

import java.util.SortedMap;

import io.smallrye.metrics.MetricsRegistryImpl;
import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.junit.Test;

public class MicroProfileMetricsCustomRegistryTest extends CamelTestSupport {

    private MetricRegistry metricRegistry;

    @Test
    public void testMicroProfileMetricsComponentWithCustomMetricRegistry() {
        template.sendBody("direct:start", null);
        SortedMap<MetricID, Counter> counters = metricRegistry.getCounters((metricID, metric) -> metricID.getName().equals("test-counter"));
        MetricID metricID = counters.firstKey();
        assertEquals(1, counters.get(metricID).getCount());
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        MicroProfileMetricsComponent component = new MicroProfileMetricsComponent();
        metricRegistry = new MetricsRegistryImpl();
        component.setMetricRegistry(metricRegistry);
        camelContext.addComponent("microprofile-metrics", component);
        return camelContext;
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("microprofile-metrics:counter:test-counter");
            }
        };
    }
}
