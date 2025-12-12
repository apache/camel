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
package org.apache.camel.opentelemetry.metrics;

import java.util.Collection;

import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.sdk.metrics.data.HistogramPointData;
import io.opentelemetry.sdk.metrics.data.PointData;
import org.apache.camel.BindToRegistry;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.HEADER_HISTOGRAM_VALUE;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.HEADER_METRIC_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class DistributionSummaryRouteTest extends CamelTestSupport {

    public final CamelOpenTelemetryExtension otelExtension = CamelOpenTelemetryExtension.create();

    @BindToRegistry("metrics")
    private Meter meter = otelExtension.getOpenTelemetry().getMeter("meterTest");

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:in1")
                        .to("opentelemetry-metrics:summary:A?value=332491")
                        .to("mock:out");

                from("direct:in2")
                        .to("opentelemetry-metrics:summary:${body}?value=${header.nextValue}")
                        .to("mock:out");

                from("direct:in3")
                        .setHeader(OpenTelemetryConstants.HEADER_HISTOGRAM_VALUE, constant(992))
                        .to("opentelemetry-metrics:summary:C?value=700")
                        .to("mock:out");
            }
        };
    }

    @Test
    public void testOverrideMetricsName() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:out");
        mockEndpoint.expectedMessageCount(1);
        template.sendBodyAndHeader("direct:in1", new Object(), HEADER_METRIC_NAME, "B");

        HistogramPointData hpd = getMetric("B");
        assertEquals(1L, hpd.getCount());
        assertEquals(332491.0, hpd.getSum());
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testValue() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:out");
        mockEndpoint.expectedMessageCount(1);
        template.sendBody("direct:in1", new Object());

        HistogramPointData hpd = getMetric("A");
        assertEquals(1L, hpd.getCount());
        assertEquals(332491.0, hpd.getSum());
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testOverrideValue() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:out");
        mockEndpoint.expectedMessageCount(1);
        template.sendBodyAndHeader("direct:in1", new Object(), HEADER_HISTOGRAM_VALUE, 181);

        HistogramPointData hpd = getMetric("A");
        assertEquals(1L, hpd.getCount());
        assertEquals(181.0, hpd.getSum());
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testScriptEvaluationValue() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:out");
        mockEndpoint.expectedMessageCount(1);
        template.sendBodyAndHeader("direct:in2", "C", "nextValue", "181");

        HistogramPointData hpd = getMetric("C");
        assertEquals(1L, hpd.getCount());
        assertEquals(181.0, hpd.getSum());
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testIterativeScriptEvaluation() throws Exception {
        int count = 10;
        MockEndpoint mockEndpoint = getMockEndpoint("mock:out");
        mockEndpoint.expectedMessageCount(count);
        for (int i = 0; i < count; i++) {
            template.sendBodyAndHeader("direct:in2", "D", "nextValue", "5");
        }

        HistogramPointData hpd = getMetric("D");
        assertEquals(10, hpd.getCount());
        assertEquals(50, hpd.getSum());
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testMultipleName() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:out");
        mockEndpoint.expectedMessageCount(3);

        template.sendBodyAndHeader("direct:in2", "E", "nextValue", "5");

        template.sendBodyAndHeader("direct:in2", "F", "nextValue", "5");
        template.sendBodyAndHeader("direct:in2", "F", "nextValue", "5");

        HistogramPointData hpd = getMetric("E");
        assertEquals(1, hpd.getCount());
        assertEquals(5, hpd.getSum());

        hpd = getMetric("F");
        assertEquals(2, hpd.getCount());
        assertEquals(10, hpd.getSum());
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testOverrideHistogramValue() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:out");
        mockEndpoint.expectedMessageCount(1);
        template.sendBody("direct:in3", new Object());

        HistogramPointData hpd = getMetric("C");
        assertEquals(1L, hpd.getCount());
        assertEquals(992.0, hpd.getSum());
        MockEndpoint.assertIsSatisfied(context);
    }

    protected HistogramPointData getMetric(String metricName) {
        PointData pd = otelExtension.getMetrics().stream()
                .filter(d -> d.getName().equals(metricName))
                .map(metricData -> metricData.getData().getPoints())
                .flatMap(Collection::stream)
                .findFirst().orElse(null);

        assertInstanceOf(HistogramPointData.class, pd, "Expected HistogramPointData");
        return (HistogramPointData) pd;
    }
}
