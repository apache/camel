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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.exporter.logging.LoggingMetricExporter;
import io.opentelemetry.sdk.metrics.data.HistogramPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.PointData;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.opentelemetry.metrics.integration.MemoryLogHandler;
import org.apache.camel.opentelemetry.metrics.messagehistory.OpenTelemetryMessageHistoryFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_MESSAGE_HISTORY_METER_NAME;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.ROUTE_ID_ATTRIBUTE;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ManagedMessageHistoryAutoConfigIT extends CamelTestSupport {

    @BeforeAll
    public static void init() {
        // Open telemetry autoconfiguration using an exporter that writes to the console via logging.
        // Other possible exporters include 'logging-otlp' and 'otlp'.
        GlobalOpenTelemetry.resetForTest();
        System.setProperty("otel.java.global-autoconfigure.enabled", "true");
        System.setProperty("otel.metrics.exporter", "console");
        System.setProperty("otel.traces.exporter", "none");
        System.setProperty("otel.logs.exporter", "none");
        System.setProperty("otel.propagators", "tracecontext");
        System.setProperty("otel.metric.export.interval", "300");
    }

    @AfterEach
    void cleanup() {
        GlobalOpenTelemetry.resetForTest();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        OpenTelemetryMessageHistoryFactory factory = new OpenTelemetryMessageHistoryFactory();
        context.setMessageHistoryFactory(factory);
        return context;
    }

    @Test
    public void testMessageHistory() throws Exception {
        Logger logger = Logger.getLogger(LoggingMetricExporter.class.getName());
        MemoryLogHandler handler = new MemoryLogHandler();
        logger.addHandler(handler);

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

        await().atMost(Duration.ofMillis(1000L)).until(handler::hasLogs);

        List<LogRecord> logs = new ArrayList<>(handler.getLogs());
        assertFalse(logs.isEmpty(), "No metrics were exported");
        int dataCount = 0;
        for (LogRecord log : logs) {
            if (log.getParameters() != null && log.getParameters().length > 0) {
                MetricData metricData = (MetricData) log.getParameters()[0];
                assertEquals(DEFAULT_CAMEL_MESSAGE_HISTORY_METER_NAME, metricData.getName());

                assertPointDataForRouteId(metricData, "route1");

                assertTrue(verifyMetricDataHasNodeId(metricData, "route1", "foo"));
                assertTrue(verifyMetricDataHasNodeId(metricData, "route2", "bar"));
                assertTrue(verifyMetricDataHasNodeId(metricData, "route2", "baz"));

                dataCount++;
            }
        }
        assertTrue(dataCount > 0, "No metric data found");
    }

    private boolean verifyMetricDataHasNodeId(MetricData metricData, String routeId, String nodeId) {
        return metricData.getData().getPoints().stream()
                .filter(point -> routeId.equals(getRouteId(point)))
                .anyMatch(point -> nodeId.equals(point.getAttributes().get(AttributeKey.stringKey("nodeId"))));
    }

    private void assertPointDataForRouteId(MetricData metricData, String routeId) {
        List<PointData> pdList = metricData.getData().getPoints().stream()
                .filter(point -> routeId.equals(getRouteId(point)))
                .collect(Collectors.toList());
        assertEquals(1, pdList.size(), "Should have one metric for routeId " + routeId);
        PointData pd = pdList.get(0);
        assertInstanceOf(HistogramPointData.class, pd);
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
