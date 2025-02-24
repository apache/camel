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
package org.apache.camel.opentelemetry2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.log4j.appender.v2_17.OpenTelemetryAppender;
import io.opentelemetry.sdk.OpenTelemetrySdk;
//import io.opentelemetry.sdk.extension.incubator.trace.LeakDetectingSpanProcessor;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.internal.SdkMeterProviderUtil;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/**
 * Adapted from
 * https://github.com/open-telemetry/opentelemetry-java/blob/main/sdk/testing/src/main/java/io/opentelemetry/sdk/testing/junit5/OpenTelemetryExtension.java
 */
final class CamelOpenTelemetryExtension implements BeforeEachCallback, AfterEachCallback {

    /**
     * Returns an extension with a default SDK initialized with an in-memory span exporter and W3C trace context
     * propagation.
     */
    static CamelOpenTelemetryExtension create() {
        InMemorySpanExporter spanExporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                //.addSpanProcessor(LeakDetectingSpanProcessor.create())
                .addSpanProcessor(new LoggingSpanProcessor())
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();

        InMemoryMetricReader metricReader = InMemoryMetricReader.create();
        SdkMeterProvider meterProvider = SdkMeterProvider.builder().registerMetricReader(metricReader).build();

        InMemoryLogRecordExporter logRecordExporter = InMemoryLogRecordExporter.create();
        SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
                .addLogRecordProcessor(SimpleLogRecordProcessor.create(logRecordExporter))
                .build();

        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .setTracerProvider(tracerProvider)
                .setMeterProvider(meterProvider)
                .setLoggerProvider(loggerProvider)
                .build();

        return new CamelOpenTelemetryExtension(openTelemetry, spanExporter, metricReader, logRecordExporter);
    }

    private final OpenTelemetrySdk openTelemetry;
    private final InMemorySpanExporter spanExporter;
    private final InMemoryMetricReader metricReader;
    private final InMemoryLogRecordExporter logRecordExporter;

    private CamelOpenTelemetryExtension(OpenTelemetrySdk openTelemetry, InMemorySpanExporter spanExporter,
                                        InMemoryMetricReader metricReader, InMemoryLogRecordExporter logRecordExporter) {
        this.openTelemetry = openTelemetry;
        this.spanExporter = spanExporter;
        this.metricReader = metricReader;
        this.logRecordExporter = logRecordExporter;
    }

    /**
     * Returns the {@link OpenTelemetrySdk} created by this extension.
     */
    public OpenTelemetry getOpenTelemetry() {
        return openTelemetry;
    }

    /**
     * Returns all the exported {@link SpanData} so far.
     */
    public List<SpanData> getSpans() {
        return spanExporter.getFinishedSpanItems();
    }

    /**
     * Returns the current {@link MetricData} in {@link AggregationTemporality#CUMULATIVE} format.
     */
    public List<MetricData> getMetrics() {
        return new ArrayList<>(metricReader.collectAllMetrics());
    }

    /**
     * Returns all the exported {@link LogRecordData} so far.
     */
    public List<LogRecordData> getLogRecords() {
        return new ArrayList<>(logRecordExporter.getFinishedLogRecordItems());
    }

    /**
     * Clears the collected exported {@link SpanData}. Consider making your test smaller instead of manually clearing
     * state using this method.
     */
    public void clearSpans() {
        spanExporter.reset();
    }

    /**
     * Clears all registered metric instruments, such that {@link #getMetrics()} is empty.
     */
    public void clearMetrics() {
        SdkMeterProviderUtil.resetForTest(openTelemetry.getSdkMeterProvider());
    }

    /**
     * Clears the collected exported {@link LogRecordData}. Consider making your test smaller instead of manually
     * clearing state using this method.
     */
    public void clearLogRecords() {
        logRecordExporter.reset();
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        GlobalOpenTelemetry.resetForTest();
        GlobalOpenTelemetry.set(openTelemetry);
        OpenTelemetryAppender.install(openTelemetry);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        GlobalOpenTelemetry.resetForTest();
        openTelemetry.close();
    }

    static class LoggingSpanProcessor implements SpanProcessor {
        private static final Logger LOG = LoggerFactory.getLogger(LoggingSpanProcessor.class);
        private static final Marker OTEL_MARKER = MarkerFactory.getMarker("OTEL");

        @Override
        public void onStart(Context context, ReadWriteSpan readWriteSpan) {
            LOG.info(OTEL_MARKER, "Span started: name - '{}', kind - '{}', id - '{}-{}", readWriteSpan.getName(),
                    readWriteSpan.getKind(),
                    readWriteSpan.getSpanContext().getTraceId(), readWriteSpan.getSpanContext().getSpanId());
        }

        @Override
        public boolean isStartRequired() {
            return true;
        }

        @Override
        public void onEnd(ReadableSpan readableSpan) {
            LOG.info(OTEL_MARKER, "Span ended: name - '{}', kind - '{}', id - '{}-{}", readableSpan.getName(),
                    readableSpan.getKind(),
                    readableSpan.getSpanContext().getTraceId(), readableSpan.getSpanContext().getSpanId());
        }

        @Override
        public boolean isEndRequired() {
            return true;
        }
    }

    Map<String, OtelTrace> getTraces() {
        Map<String, OtelTrace> answer = new HashMap<>();
        for (SpanData span : this.getSpans()) {
            String traceId = span.getTraceId();
            OtelTrace trace = answer.get(traceId);
            if (trace == null) {
                trace = new OtelTrace(traceId);
                answer.put(traceId, trace);
            }
            trace.addSpan(span);
        }

        // Sort the spans for all traces
        answer.forEach((id, trace) -> Collections.sort(trace.getSpans(), new SpanComparator()));

        return answer;
    }

    class OtelTrace {
        String traceId;
        List<SpanData> spans;

        OtelTrace(String traceId) {
            this.traceId = traceId;
            this.spans = new ArrayList<>();
        }

        void addSpan(SpanData span) {
            this.spans.add(span);
        }

        List<SpanData> getSpans() {
            return this.spans;
        }

        @Override
        public String toString() {
            return traceId + " " + spans;
        }
    }

    class SpanComparator implements java.util.Comparator<SpanData> {
        @Override
        public int compare(SpanData a, SpanData b) {
            Long nanosA = a.getStartEpochNanos();
            Long nanosB = b.getStartEpochNanos();
            return (int) (nanosA - nanosB);
        }
    }
}
