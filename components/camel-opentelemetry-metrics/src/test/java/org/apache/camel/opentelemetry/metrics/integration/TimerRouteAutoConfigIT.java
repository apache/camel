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
package org.apache.camel.opentelemetry.metrics.integration;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.data.HistogramPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.PointData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.opentelemetry.metrics.eventnotifier.OpenTelemetryExchangeEventNotifier;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for OpenTelemetry Timer metric autoconfiguration in a Camel route.
 *
 * Uses InMemoryMetricReader for deterministic, synchronous metric collection instead of relying on the
 * PeriodicMetricReader + LoggingMetricExporter + JUL log capture chain, which is inherently timing-dependent and flaky.
 */
public class TimerRouteAutoConfigIT extends CamelTestSupport {

    private static final long DELAY = 20L;

    private static InMemoryMetricReader metricReader;

    @BeforeAll
    public static void init() {
        GlobalOpenTelemetry.resetForTest();
        metricReader = InMemoryMetricReader.create();
        // Still use OTel autoconfigure (the "AutoConfig" in the test name) but with
        // InMemoryMetricReader instead of the periodic LoggingMetricExporter.
        AutoConfiguredOpenTelemetrySdk.builder()
                .addPropertiesSupplier(() -> Map.of(
                        "otel.metrics.exporter", "none",
                        "otel.traces.exporter", "none",
                        "otel.logs.exporter", "none",
                        "otel.propagators", "tracecontext"))
                .addMeterProviderCustomizer((builder, config) -> builder.registerMetricReader(metricReader))
                .setResultAsGlobal()
                .build();
    }

    @AfterEach
    void cleanup() {
        GlobalOpenTelemetry.resetForTest();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        // not setting any meter explicitly, relying on opentelemetry autoconfigure
        OpenTelemetryExchangeEventNotifier eventNotifier = new OpenTelemetryExchangeEventNotifier();
        context.getManagementStrategy().addEventNotifier(eventNotifier);
        eventNotifier.init();
        return context;
    }

    @Test
    public void testOverrideMetricsName() throws Exception {
        Object body = new Object();
        MockEndpoint mockEndpoint = getMockEndpoint("mock:out");
        mockEndpoint.expectedBodiesReceived(body);
        template.sendBody("direct:in1", body);

        // Collect metrics synchronously -- no timing dependency on periodic export
        Collection<MetricData> allMetrics = metricReader.collectAllMetrics();
        List<MetricData> aMetrics = allMetrics.stream()
                .filter(md -> "A".equals(md.getName()))
                .toList();
        assertFalse(aMetrics.isEmpty(), "No metric data found with name A");

        MetricData md = aMetrics.get(0);
        PointData pd = md.getData()
                .getPoints()
                .stream()
                .findFirst()
                .orElseThrow();
        assertInstanceOf(HistogramPointData.class, pd, "Expected HistogramPointData");
        HistogramPointData hpd = (HistogramPointData) pd;
        assertEquals(1L, hpd.getCount());
        assertTrue(hpd.getMin() >= DELAY);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:in1")
                        .to("opentelemetry-metrics:timer:A?action=start")
                        .delay(DELAY)
                        .to("opentelemetry-metrics:timer:A?action=stop")
                        .to("mock:out");
            }
        };
    }
}
