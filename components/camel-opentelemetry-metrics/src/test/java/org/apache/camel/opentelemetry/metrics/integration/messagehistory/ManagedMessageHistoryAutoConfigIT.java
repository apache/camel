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
package org.apache.camel.opentelemetry.metrics.integration.messagehistory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.metrics.data.HistogramPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.PointData;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.opentelemetry.metrics.messagehistory.OpenTelemetryMessageHistoryFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_MESSAGE_HISTORY_METER_NAME;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.ROUTE_ID_ATTRIBUTE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ManagedMessageHistoryAutoConfigIT extends CamelTestSupport {

    // Registers an in-memory OTel SDK as GlobalOpenTelemetry before the Camel context is
    // created, so the message history factory's GlobalOpenTelemetry.get() call returns this
    // test SDK. Avoids all JUL log-capture complexity and periodic-export timing races.
    @RegisterExtension
    static OpenTelemetryExtension otelExtension = OpenTelemetryExtension.create();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        OpenTelemetryMessageHistoryFactory factory = new OpenTelemetryMessageHistoryFactory();
        context.setMessageHistoryFactory(factory);
        return context;
    }

    @Test
    public void testMessageHistory() throws Exception {
        int count = 10;
        getMockEndpoint("mock:foo").expectedMessageCount(count / 2);
        getMockEndpoint("mock:bar").expectedMessageCount(count / 2);
        getMockEndpoint("mock:baz").expectedMessageCount(count / 2);

        for (int i = 0; i < count; i++) {
            if (i % 2 == 0) {
                template.sendBody("seda:foo", "Hello " + i);
            } else {
                template.sendBody("seda:bar", "Hello " + i);
            }
        }

        MockEndpoint.assertIsSatisfied(context);

        List<MetricData> metrics = otelExtension.getMetrics();
        MetricData camelMetric = metrics.stream()
                .filter(md -> DEFAULT_CAMEL_MESSAGE_HISTORY_METER_NAME.equals(md.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No Camel metric data found"));

        assertPointDataForRouteId(camelMetric, "route1");
        assertMetricDataHasNodeId(camelMetric, "route1", "foo");
        assertMetricDataHasNodeId(camelMetric, "route2", "bar");
        assertMetricDataHasNodeId(camelMetric, "route2", "baz");
    }

    private void assertMetricDataHasNodeId(MetricData metricData, String routeId, String nodeId) {
        assertThat(metricData.getData().getPoints())
                .anyMatch(point -> routeId.equals(getRouteId(point))
                        && nodeId.equals(point.getAttributes().get(AttributeKey.stringKey("nodeId"))),
                        "No metric data found for node " + nodeId + " of route " + routeId);
    }

    private void assertPointDataForRouteId(MetricData metricData, String routeId) {
        List<PointData> pdList = metricData.getData().getPoints().stream()
                .filter(point -> routeId.equals(getRouteId(point)))
                .collect(Collectors.toList());
        assertEquals(1, pdList.size(), "Should have one metric for routeId " + routeId);
        assertInstanceOf(HistogramPointData.class, pdList.get(0));
    }

    protected String getRouteId(PointData pd) {
        Map<AttributeKey<?>, Object> m = pd.getAttributes().asMap();
        assertTrue(m.containsKey(AttributeKey.stringKey(ROUTE_ID_ATTRIBUTE)));
        return (String) m.get(AttributeKey.stringKey(ROUTE_ID_ATTRIBUTE));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:foo")
                        .routeId("route1")
                        .to("mock:foo").id("foo");

                from("seda:bar")
                        .routeId("route2")
                        .to("mock:bar").id("bar")
                        .to("mock:baz").id("baz");
            }
        };
    }
}
