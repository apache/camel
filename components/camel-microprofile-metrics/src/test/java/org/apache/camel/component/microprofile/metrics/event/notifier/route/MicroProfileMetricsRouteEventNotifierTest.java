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
package org.apache.camel.component.microprofile.metrics.event.notifier.route;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.microprofile.metrics.MicroProfileMetricsTestSupport;
import org.eclipse.microprofile.metrics.Gauge;
import org.junit.Test;

import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.DEFAULT_CAMEL_ROUTES_ADDED_METRIC_NAME;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.DEFAULT_CAMEL_ROUTES_RUNNING_METRIC_NAME;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsHelper.findMetric;

public class MicroProfileMetricsRouteEventNotifierTest extends MicroProfileMetricsTestSupport {

    private MicroProfileMetricsRouteEventNotifier eventNotifier;

    @Test
    public void testMicroProfileMetricsRouteEventNotifier() throws Exception {
        Gauge routesAdded = findMetric(metricRegistry, DEFAULT_CAMEL_ROUTES_ADDED_METRIC_NAME, Gauge.class);
        Gauge routesRunning = findMetric(metricRegistry, DEFAULT_CAMEL_ROUTES_RUNNING_METRIC_NAME, Gauge.class);

        assertEquals(1, routesAdded.getValue());
        assertEquals(1, routesRunning.getValue());

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:foo").id("test")
                        .to("mock:bar");
            }
        });

        assertEquals(2, routesAdded.getValue());
        assertEquals(2, routesRunning.getValue());

        context.getRouteController().stopRoute("test");
        assertEquals(2, routesAdded.getValue());
        assertEquals(1, routesRunning.getValue());

        context.removeRoute("test");
        assertEquals(1, routesAdded.getValue());
        assertEquals(1, routesRunning.getValue());
    }

    @Test
    public void testMicroProfileMetricsRouteEventNotifierStop() {
        assertEquals(2, metricRegistry.getMetricIDs().size());
        eventNotifier.stop();
        assertEquals(0, metricRegistry.getMetricIDs().size());
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        eventNotifier = new MicroProfileMetricsRouteEventNotifier();
        eventNotifier.setMetricRegistry(metricRegistry);

        CamelContext camelContext = super.createCamelContext();
        camelContext.getManagementStrategy().addEventNotifier(eventNotifier);
        return camelContext;
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .to("mock:result");
            }
        };
    }
}
