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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.exporter.logging.LoggingMetricExporter;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.PointData;
import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.opentelemetry.metrics.eventnotifier.OpenTelemetryExchangeEventNotifier;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for OpenTelemetry Counter metric autoconfiguration in a Camel route.
 */
public class CounterRouteAutoConfigIT extends CamelTestSupport {

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
        System.setProperty("otel.metric.export.interval", "50");
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
    public void testIncrement() throws Exception {
        Logger logger = Logger.getLogger(LoggingMetricExporter.class.getName());
        MemoryLogHandler handler = new MemoryLogHandler();
        logger.addHandler(handler);

        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(1);
        template.sendBody("direct:in1", new Object());

        // capture logs from the LoggingMetricExporter
        await().atMost(Duration.ofMillis(1000L)).until(handler::hasLogs);

        List<LogRecord> logs = new ArrayList<>(handler.getLogs());
        assertFalse(logs.isEmpty(), "No metrics were exported");
        long dataCount = logs.stream()
                .map(LogRecord::getParameters)
                .filter(Objects::nonNull)
                .flatMap(Arrays::stream)
                .filter(MetricData.class::isInstance)
                .map(MetricData.class::cast)
                .filter(md -> "B".equals(md.getName()))
                .peek(md -> {
                    PointData pd = md.getData()
                            .getPoints()
                            .stream()
                            .findFirst()
                            .orElseThrow();

                    assertInstanceOf(LongPointData.class, pd, "Expected LongPointData");
                    assertEquals(5, ((LongPointData) pd).getValue());
                })
                .count();
        assertTrue(dataCount > 0, "No metric data found with name B");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:in1")
                        .to("opentelemetry-metrics:counter:B?increment=5")
                        .to("mock:result");
            }
        };
    }
}
