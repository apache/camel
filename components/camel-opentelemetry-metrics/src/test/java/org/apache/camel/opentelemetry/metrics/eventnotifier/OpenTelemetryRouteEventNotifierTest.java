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
package org.apache.camel.opentelemetry.metrics.eventnotifier;

import java.util.List;

import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.PointData;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.opentelemetry.metrics.AbstractOpenTelemetryTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_ROUTES_ADDED;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_ROUTES_RUNNING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class OpenTelemetryRouteEventNotifierTest extends AbstractOpenTelemetryTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        OpenTelemetryRouteEventNotifier ren = new OpenTelemetryRouteEventNotifier();
        ren.setMeter(otelExtension.getOpenTelemetry().getMeter("meterTest"));
        context.getManagementStrategy().addEventNotifier(ren);
        ren.init();
        return context;
    }

    @Test
    public void testCamelRouteEvents() throws Exception {

        verifyMetric(DEFAULT_CAMEL_ROUTES_ADDED, 1L);
        verifyMetric(DEFAULT_CAMEL_ROUTES_RUNNING, 1L);

        context.getRouteController().stopRoute("test");

        verifyMetric(DEFAULT_CAMEL_ROUTES_ADDED, 1L);
        verifyMetric(DEFAULT_CAMEL_ROUTES_RUNNING, 0L);

        context.removeRoute("test");

        verifyMetric(DEFAULT_CAMEL_ROUTES_ADDED, 0L);
        verifyMetric(DEFAULT_CAMEL_ROUTES_RUNNING, 0L);
    }

    private void verifyMetric(String metricName, long expected) {
        List<PointData> ls = getAllPointData(metricName);
        assertEquals(1, ls.size(), "Expected one point data");
        PointData pd = ls.get(0);

        assertInstanceOf(LongPointData.class, pd);
        assertEquals(expected, ((LongPointData) pd).getValue());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:in").routeId("test").to("mock:out");
            }
        };
    }
}
