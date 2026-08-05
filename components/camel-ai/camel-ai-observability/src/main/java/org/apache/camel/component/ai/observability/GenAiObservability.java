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
package org.apache.camel.component.ai.observability;

import java.lang.reflect.Constructor;
import java.util.Optional;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.telemetry.Span;
import org.apache.camel.telemetry.SpanLifecycleManager;
import org.apache.camel.telemetry.SpanStorageManagerExchange;
import org.apache.camel.telemetry.Tracer;
import org.apache.camel.telemetry.propagation.CamelHeadersSpanContextPropagationExtractor;
import org.apache.camel.util.ObjectHelper;

/**
 * Entry point for GenAI observability in Camel AI producers.
 */
public final class GenAiObservability {

    private static final String CLIENT_SPAN_KIND = "CLIENT";
    private static final String METER_REGISTRY_CLASS = "io.micrometer.core.instrument.MeterRegistry";
    private static final String MICROMETER_SUPPORT_CLASS
            = "org.apache.camel.component.ai.observability.GenAiMicrometerSupport";
    private static final GenAiObservation NOOP = new NoopGenAiObservation();

    private GenAiObservability() {
    }

    /**
     * Whether GenAI observability is enabled for the given context.
     */
    public static boolean isEnabled(CamelContext camelContext) {
        if (camelContext == null) {
            return false;
        }
        Optional<String> property
                = camelContext.getPropertiesComponent().resolveProperty(GenAiObservabilityProperties.ENABLED);
        if (property.isPresent()) {
            return Boolean.parseBoolean(property.get().trim());
        }
        return true;
    }

    /**
     * Starts a GenAI observation for a single LLM client call. Returns a no-op when disabled or no backend is
     * available.
     */
    public static GenAiObservation start(Exchange exchange, GenAiObservationContext context) {
        if (exchange == null || context == null || !isEnabled(exchange.getContext())) {
            return NOOP;
        }
        Tracer tracer = exchange.getContext().hasService(Tracer.class);
        GenAiMetricsBackend metricsBackend = resolveMetricsBackend(exchange.getContext());
        if (tracer == null && (metricsBackend == null || !metricsBackend.isAvailable())) {
            return NOOP;
        }
        return new DefaultGenAiObservation(exchange, context, tracer, metricsBackend);
    }

    private static GenAiMetricsBackend resolveMetricsBackend(CamelContext camelContext) {
        try {
            Class.forName(METER_REGISTRY_CLASS);
            Class<?> supportClass = Class.forName(MICROMETER_SUPPORT_CLASS);
            Constructor<?> constructor = supportClass.getDeclaredConstructor(CamelContext.class);
            constructor.setAccessible(true);
            return (GenAiMetricsBackend) constructor.newInstance(camelContext);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    private static final class DefaultGenAiObservation implements GenAiObservation {

        private final Exchange exchange;
        private final GenAiObservationContext context;
        private final Tracer tracer;
        private final GenAiMetricsBackend metricsBackend;
        private final long startNanos;
        private Span span;
        private GenAiUsage usage;
        private Throwable error;
        private boolean closed;

        private DefaultGenAiObservation(
                                        Exchange exchange, GenAiObservationContext context, Tracer tracer,
                                        GenAiMetricsBackend metricsBackend) {
            this.exchange = exchange;
            this.context = context;
            this.tracer = tracer;
            this.metricsBackend = metricsBackend;
            this.startNanos = System.nanoTime();
            startSpan();
        }

        private void startSpan() {
            if (tracer == null || tracer.getSpanLifecycleManager() == null) {
                return;
            }
            SpanStorageManagerExchange storage = new SpanStorageManagerExchange();
            Span parent = storage.peek(exchange);
            SpanLifecycleManager lifecycleManager = tracer.getSpanLifecycleManager();
            var extractor = new CamelHeadersSpanContextPropagationExtractor(exchange.getIn().getHeaders());
            span = lifecycleManager.create(context.spanName(), CLIENT_SPAN_KIND, parent, extractor);
            lifecycleManager.activate(span);
            applyContextAttributes(span, context, null, null);
        }

        @Override
        public void recordSuccess(GenAiUsage usage) {
            this.usage = usage;
        }

        @Override
        public void recordError(Throwable error) {
            this.error = error;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            closeSpan();
            recordMetrics();
        }

        private void closeSpan() {
            if (span == null || tracer == null || tracer.getSpanLifecycleManager() == null) {
                return;
            }
            SpanLifecycleManager lifecycleManager = tracer.getSpanLifecycleManager();
            if (error != null) {
                span.setError(true);
                span.setTag(GenAiAttributes.ERROR_TYPE, error.getClass().getSimpleName());
            }
            if (usage != null) {
                applyContextAttributes(span, context, usage, error == null ? usage.responseModel() : null);
            }
            lifecycleManager.deactivate(span);
            lifecycleManager.close(span);
            span = null;
        }

        private void recordMetrics() {
            if (metricsBackend == null) {
                return;
            }
            metricsBackend.recordMetrics(context, usage, error, startNanos);
        }

        private static void applyContextAttributes(
                Span span, GenAiObservationContext context, GenAiUsage usage, String responseModel) {
            span.setTag(GenAiAttributes.OPERATION_NAME, context.operationName().value());
            span.setTag(GenAiAttributes.SYSTEM, nullToUnknown(context.system()));
            span.setTag(GenAiAttributes.REQUEST_MODEL, nullToUnknown(context.requestModel()));
            if (ObjectHelper.isNotEmpty(context.componentScheme())) {
                span.setTag(GenAiAttributes.CAMEL_COMPONENT, context.componentScheme());
            }
            if (responseModel != null && !responseModel.isBlank()) {
                span.setTag(GenAiAttributes.RESPONSE_MODEL, responseModel);
            }
            if (usage != null) {
                if (usage.inputTokens() != null) {
                    span.setTag(GenAiAttributes.INPUT_TOKENS, usage.inputTokens().toString());
                }
                if (usage.outputTokens() != null) {
                    span.setTag(GenAiAttributes.OUTPUT_TOKENS, usage.outputTokens().toString());
                }
                if (usage.finishReason() != null) {
                    span.setTag(GenAiAttributes.FINISH_REASONS, usage.finishReason());
                }
                if (usage.responseModel() != null && !usage.responseModel().isBlank()) {
                    span.setTag(GenAiAttributes.RESPONSE_MODEL, usage.responseModel());
                }
            }
        }

        private static String nullToUnknown(String value) {
            return value == null || value.isBlank() ? "unknown" : value;
        }
    }

    private static final class NoopGenAiObservation implements GenAiObservation {
        @Override
        public void recordSuccess(GenAiUsage usage) {
            // noop
        }

        @Override
        public void recordError(Throwable error) {
            // noop
        }

        @Override
        public void close() {
            // noop
        }
    }
}
