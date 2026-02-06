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
package org.apache.camel.opentelemetry.metrics.integration.eventnotifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.exporter.logging.LoggingMetricExporter;
import io.opentelemetry.sdk.metrics.data.GaugeData;
import io.opentelemetry.sdk.metrics.data.HistogramData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.opentelemetry.metrics.eventnotifier.OpenTelemetryExchangeEventNotifier;
import org.apache.camel.opentelemetry.metrics.integration.MemoryLogHandler;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_EXCHANGE_ELAPSED_TIMER;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_EXCHANGE_LAST_PROCESSED_TIME_INSTRUMENT;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_EXCHANGE_SENT_TIMER;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_ROUTES_EXCHANGES_INFLIGHT;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test auto-configuration of OpenTelemetryExchangeEventNotifier relying on OpenTelemetry global autoconfigure.
 */
public class ExchangeEventNotifierAutoConfigIT extends CamelTestSupport {

    private static final Long DELAY = 250L;

    @BeforeAll
    public static void init() {
        GlobalOpenTelemetry.resetForTest();
        // open telemetry auto configuration using console exporter that writes to logging
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
    public void testElapsedTimerEvents() throws Exception {
        Logger logger = Logger.getLogger(LoggingMetricExporter.class.getName());
        MemoryLogHandler handler = new MemoryLogHandler();
        logger.addHandler(handler);

        int count = 6;
        MockEndpoint mock = getMockEndpoint("mock://result");
        mock.expectedMessageCount(count);

        for (int i = 0; i < count; i++) {
            if (i % 2 == 0) {
                template.sendBody("direct:foo", "Hello " + i);
            } else {
                template.sendBody("direct:bar", "Hello " + i);
            }
        }

        mock.assertIsSatisfied();

        await().atMost(Duration.ofMillis(1000L)).until(() -> !handler.getLogs().isEmpty());

        List<LogRecord> logs = new ArrayList<>(handler.getLogs());
        Map<String, Integer> counts = new HashMap<>();
        for (LogRecord log : logs) {
            if (log.getParameters() != null && log.getParameters().length > 0) {
                MetricData metricData = (MetricData) log.getParameters()[0];
                counts.compute(metricData.getName(), (k, v) -> v == null ? 1 : v + 1);
                switch (metricData.getName()) {
                    case DEFAULT_CAMEL_EXCHANGE_ELAPSED_TIMER,
                            DEFAULT_CAMEL_EXCHANGE_SENT_TIMER -> {
                        // histogram
                        assertInstanceOf(HistogramData.class, metricData.getData());
                    }
                    case DEFAULT_CAMEL_EXCHANGE_LAST_PROCESSED_TIME_INSTRUMENT,
                            DEFAULT_CAMEL_ROUTES_EXCHANGES_INFLIGHT -> {
                        // gauge
                        assertInstanceOf(GaugeData.class, metricData.getData());
                    }
                    default -> fail();
                }
            }
        }
        assertEquals(4, counts.size());
        assertTrue(counts.get(DEFAULT_CAMEL_EXCHANGE_ELAPSED_TIMER) > 0,
                "Should have metric log for " + DEFAULT_CAMEL_EXCHANGE_ELAPSED_TIMER);
        assertTrue(counts.get(DEFAULT_CAMEL_EXCHANGE_SENT_TIMER) > 0,
                "Should have metric log for " + DEFAULT_CAMEL_EXCHANGE_SENT_TIMER);
        assertTrue(counts.get(DEFAULT_CAMEL_EXCHANGE_LAST_PROCESSED_TIME_INSTRUMENT) > 0,
                "Should have metric log for " + DEFAULT_CAMEL_EXCHANGE_LAST_PROCESSED_TIME_INSTRUMENT);
        assertTrue(counts.get(DEFAULT_CAMEL_ROUTES_EXCHANGES_INFLIGHT) > 0,
                "Should have metric log for " + DEFAULT_CAMEL_ROUTES_EXCHANGES_INFLIGHT);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct://foo").routeId("foo").to("mock://result");
                from("direct://bar").routeId("bar").delay(DELAY).to("mock://result");
            }
        };
    }
}
