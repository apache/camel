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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.telemetry.Span;
import org.apache.camel.telemetry.SpanLifecycleManager;
import org.apache.camel.telemetry.SpanStorageManagerExchange;
import org.apache.camel.telemetry.Tracer;
import org.apache.camel.util.ObjectHelper;

/**
 * Entry point for GenAI observability in Camel AI producers.
 */
public final class GenAiObservability {

    private static final String INTERNAL_SPAN_KIND = "INTERNAL";
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
        MeterRegistry meterRegistry = resolveMeterRegistry(exchange.getContext());
        if (tracer == null && meterRegistry == null) {
            return NOOP;
        }
        return new DefaultGenAiObservation(exchange, context, tracer, meterRegistry);
    }

    private static MeterRegistry resolveMeterRegistry(CamelContext camelContext) {
        MeterRegistry registry = CamelContextHelper.findSingleByType(camelContext, MeterRegistry.class);
        if (registry != null) {
            return registry;
        }
        return camelContext.getRegistry().lookupByNameAndType("metricsRegistry", MeterRegistry.class);
    }

    private static final class DefaultGenAiObservation implements GenAiObservation {

        private final Exchange exchange;
        private final GenAiObservationContext context;
        private final Tracer tracer;
        private final MeterRegistry meterRegistry;
        private final long startNanos;
        private Span span;
        private GenAiUsage usage;
        private Throwable error;
        private boolean closed;

        private DefaultGenAiObservation(
                                        Exchange exchange, GenAiObservationContext context, Tracer tracer,
                                        MeterRegistry meterRegistry) {
            this.exchange = exchange;
            this.context = context;
            this.tracer = tracer;
            this.meterRegistry = meterRegistry;
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
            span = lifecycleManager.create(context.spanName(), INTERNAL_SPAN_KIND, parent, null);
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
            if (meterRegistry == null) {
                return;
            }
            Iterable<Tag> baseTags = baseTags(context, error);
            Timer.builder(GenAiMetrics.CLIENT_OPERATION)
                    .tags(baseTags)
                    .register(meterRegistry)
                    .record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);

            if (usage != null) {
                recordTokenCounter(usage.inputTokens(), GenAiMetrics.TOKEN_TYPE_INPUT);
                recordTokenCounter(usage.outputTokens(), GenAiMetrics.TOKEN_TYPE_OUTPUT);
            }
        }

        private void recordTokenCounter(Integer tokens, String tokenType) {
            if (tokens == null || tokens <= 0) {
                return;
            }
            Counter.builder(GenAiMetrics.CLIENT_TOKEN_USAGE)
                    .tags(baseTags(context, error))
                    .tag(GenAiMetrics.TAG_TOKEN_TYPE, tokenType)
                    .register(meterRegistry)
                    .increment(tokens);
        }

        private static Iterable<Tag> baseTags(GenAiObservationContext context, Throwable error) {
            List<Tag> tags = new ArrayList<>();
            tags.add(Tag.of(GenAiMetrics.TAG_OPERATION_NAME, context.operationName().value()));
            tags.add(Tag.of(GenAiMetrics.TAG_SYSTEM, nullToUnknown(context.system())));
            tags.add(Tag.of(GenAiMetrics.TAG_REQUEST_MODEL, nullToUnknown(context.requestModel())));
            if (ObjectHelper.isNotEmpty(context.componentScheme())) {
                tags.add(Tag.of(GenAiMetrics.TAG_CAMEL_COMPONENT, context.componentScheme()));
            }
            if (error != null) {
                tags.add(Tag.of(GenAiMetrics.TAG_ERROR_TYPE, error.getClass().getSimpleName()));
            }
            return tags;
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
