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

import java.util.ArrayList;
import java.util.List;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.log4j.appender.v2_17.OpenTelemetryAppender;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.internal.SdkMeterProviderUtil;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Adapted from
 * https://github.com/open-telemetry/opentelemetry-java/blob/main/sdk/testing/src/main/java/io/opentelemetry/sdk/testing/junit5/OpenTelemetryExtension.java
 */
public final class CamelOpenTelemetryExtension implements BeforeEachCallback, AfterEachCallback {

    /**
     * Returns an extension with a default SDK initialized with an in-memory metric reader.
     */
    public static CamelOpenTelemetryExtension create() {
        InMemoryMetricReader metricReader = InMemoryMetricReader.create();
        SdkMeterProvider meterProvider =
                SdkMeterProvider.builder().registerMetricReader(metricReader).build();
        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .setMeterProvider(meterProvider)
                .build();

        return new CamelOpenTelemetryExtension(openTelemetry, metricReader);
    }

    private final OpenTelemetrySdk openTelemetry;
    private final InMemoryMetricReader metricReader;

    private CamelOpenTelemetryExtension(OpenTelemetrySdk openTelemetry, InMemoryMetricReader metricReader) {
        this.openTelemetry = openTelemetry;
        this.metricReader = metricReader;
    }

    /**
     * Returns the {@link OpenTelemetrySdk} created by this extension.
     */
    public OpenTelemetry getOpenTelemetry() {
        return openTelemetry;
    }

    /**
     * Returns the current {@link MetricData} in {@link AggregationTemporality#CUMULATIVE} format.
     */
    public List<MetricData> getMetrics() {
        return new ArrayList<>(metricReader.collectAllMetrics());
    }

    /**
     * Clears all registered metric instruments, such that {@link #getMetrics()} is empty.
     */
    public void clearMetrics() {
        SdkMeterProviderUtil.resetForTest(openTelemetry.getSdkMeterProvider());
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
}
