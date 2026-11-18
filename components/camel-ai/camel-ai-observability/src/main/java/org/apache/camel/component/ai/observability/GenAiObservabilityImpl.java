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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.telemetry.Span;
import org.apache.camel.telemetry.SpanLifecycleManager;
import org.apache.camel.telemetry.SpanStorageManagerExchange;
import org.apache.camel.telemetry.Tracer;
import org.apache.camel.telemetry.propagation.CamelHeadersSpanContextPropagationExtractor;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GenAI observability implementation, discovered by {@link GenAiObservability} through the factory finder.
 */
@JdkService(GenAiObservationFactory.FACTORY)
public final class GenAiObservabilityImpl implements GenAiObservationFactory {

    private static final Logger LOG = LoggerFactory.getLogger(GenAiObservabilityImpl.class);
    private static final GenAiObservation NOOP = new NoopGenAiObservation();
    private static final String CLIENT_SPAN_KIND = "CLIENT";
    private static final String MICROMETER_TRACER_CLASS
            = "org.apache.camel.micrometer.observability.MicrometerObservabilityTracer";
    private static final String INTERNAL_SPAN_KIND = "INTERNAL";
    private static final String METER_REGISTRY_CLASS = "io.micrometer.core.instrument.MeterRegistry";
    private static final String OBSERVATION_REGISTRY_CLASS = "io.micrometer.observation.ObservationRegistry";
    private static final GenAiMetricsBackend NO_METRICS_BACKEND = new NoMetricsBackend();
    private static final GenAiMicrometerObservationBackend NO_OBSERVATION_BACKEND = new NoObservationBackend();

    // one instance per CamelContext (GenAiObservability keeps it as a context plugin), so the backends found in its
    // registry are resolved once and go away with it
    private volatile Backends backends;

    @Override
    public GenAiObservation start(Exchange exchange, GenAiObservationContext context) {
        CamelContext camelContext = exchange.getContext();
        Backends resolved = backends(camelContext);
        GenAiMicrometerObservationBackend observationBackend = resolved.observation();
        Tracer tracer = camelContext.hasService(Tracer.class);
        GenAiMetricsBackend metricsBackend = resolved.metrics();
        if (!observationBackend.isAvailable() && tracer == null && !metricsBackend.isAvailable()) {
            return NOOP;
        }
        return new DefaultGenAiObservation(exchange, context, tracer, metricsBackend, observationBackend);
    }

    /**
     * Whether the camel-telemetry tracer is camel-micrometer-observability's, which traces through Micrometer: then the
     * GenAI calls keep the Micrometer Observation path.
     */
    private static boolean isMicrometerTracer(Tracer tracer) {
        for (Class<?> type = tracer.getClass(); type != null; type = type.getSuperclass()) {
            if (MICROMETER_TRACER_CLASS.equals(type.getName())) {
                return true;
            }
        }
        return false;
    }

    private Backends backends(CamelContext camelContext) {
        Backends answer = backends;
        if (answer == null) {
            synchronized (this) {
                answer = backends;
                if (answer == null) {
                    answer = new Backends(createMetricsBackend(camelContext), createObservationBackend(camelContext));
                    backends = answer;
                }
            }
        }
        return answer;
    }

    private static GenAiMetricsBackend createMetricsBackend(CamelContext camelContext) {
        try {
            // Micrometer is optional: GenAiMicrometerSupport only loads once the presence check passes
            Class.forName(METER_REGISTRY_CLASS);
            return new GenAiMicrometerSupport(camelContext);
        } catch (ClassNotFoundException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Micrometer metrics backend unavailable for GenAI observability", e);
            }
            return NO_METRICS_BACKEND;
        } catch (RuntimeException | LinkageError e) {
            // such as a Micrometer version mismatch: only this backend is disabled, the others keep working
            LOG.warn("Unable to create the Micrometer metrics backend for GenAI observability; it is disabled", e);
            return NO_METRICS_BACKEND;
        }
    }

    private static GenAiMicrometerObservationBackend createObservationBackend(CamelContext camelContext) {
        try {
            // Micrometer Observation is optional: GenAiMicrometerObservationSupport only loads once the presence
            // check passes
            Class.forName(OBSERVATION_REGISTRY_CLASS);
            return new GenAiMicrometerObservationSupport(camelContext);
        } catch (ClassNotFoundException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Micrometer Observation backend unavailable for GenAI observability", e);
            }
            return NO_OBSERVATION_BACKEND;
        } catch (RuntimeException | LinkageError e) {
            // such as a Micrometer version mismatch: only this backend is disabled, the others keep working
            LOG.warn("Unable to create the Micrometer Observation backend for GenAI observability; it is disabled", e);
            return NO_OBSERVATION_BACKEND;
        }
    }

    private record Backends(GenAiMetricsBackend metrics, GenAiMicrometerObservationBackend observation) {
    }

    private static final class DefaultGenAiObservation implements GenAiObservation {

        private static final SpanStorageManagerExchange STORAGE = new SpanStorageManagerExchange();
        private static final Set<String> LOW_CARDINALITY_KEYS = Set.of(
                GenAiAttributes.OPERATION_NAME, GenAiAttributes.SYSTEM, GenAiAttributes.REQUEST_MODEL,
                GenAiAttributes.CAMEL_COMPONENT);

        private final Exchange exchange;
        private final Tracer tracer;
        private final GenAiMetricsBackend metricsBackend;
        private final GenAiMicrometerObservationBackend.Handle micrometerObservation;
        private final GenAiObservabilityLevel level;
        private final int contentMaxLength;
        private final long startNanos;
        // attributes decided while the call runs, applied when it ends
        private final Map<String, Object> endAttributes = new LinkedHashMap<>();
        private final GenAiObservationContext context;
        private Span span;
        private boolean pushed;
        private GenAiUsage usage;
        private Throwable error;
        private boolean closed;

        private DefaultGenAiObservation(
                                        Exchange exchange, GenAiObservationContext context, Tracer tracer,
                                        GenAiMetricsBackend metricsBackend,
                                        GenAiMicrometerObservationBackend observationBackend) {
            this.exchange = exchange;
            this.context = context;
            this.tracer = tracer;
            this.metricsBackend = metricsBackend;
            this.level = GenAiObservability.level(exchange.getContext());
            this.contentMaxLength = GenAiObservability.contentMaxLength(exchange.getContext());
            this.startNanos = System.nanoTime();
            DefaultGenAiObservation parentObservation
                    = context.parent() instanceof DefaultGenAiObservation p ? p : null;
            // a camel-telemetry tracer (camel-opentelemetry2) keeps the GenAI spans in the same tree as the route spans,
            // so it is preferred; the Micrometer Observation path serves setups that trace through Micrometer only
            boolean camelTracing = tracer != null && tracer.getSpanLifecycleManager() != null && !isMicrometerTracer(tracer);
            this.micrometerObservation = observationBackend.isAvailable() && !camelTracing
                    ? observationBackend.start(context,
                            parentObservation != null ? parentObservation.micrometerObservation : null)
                    : null;
            if (this.micrometerObservation == null) {
                startSpan(parentObservation);
            }
        }

        private boolean isAgentOrTool() {
            return context.operationName() == GenAiOperationName.INVOKE_AGENT
                    || context.operationName() == GenAiOperationName.EXECUTE_TOOL;
        }

        private void startSpan(DefaultGenAiObservation parentObservation) {
            if (tracer == null || tracer.getSpanLifecycleManager() == null) {
                return;
            }
            Span parent = parentObservation != null && parentObservation.span != null
                    ? parentObservation.span : STORAGE.peek(exchange);
            SpanLifecycleManager lifecycleManager = tracer.getSpanLifecycleManager();
            var extractor = new CamelHeadersSpanContextPropagationExtractor(exchange.getIn().getHeaders());
            span = lifecycleManager.create(context.spanName(), isAgentOrTool() ? INTERNAL_SPAN_KIND : CLIENT_SPAN_KIND,
                    parent, extractor);
            lifecycleManager.activate(span);
            startAttributes().forEach((key, value) -> setTag(span, key, value));
            if (isAgentOrTool()) {
                // spans started on this exchange (or on the copies made for tool calls) nest under the agent or tool
                STORAGE.push(exchange, span);
                pushed = true;
            }
        }

        private Map<String, Object> startAttributes() {
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put(GenAiAttributes.OPERATION_NAME, context.operationName().value());
            if (context.operationName() != GenAiOperationName.EXECUTE_TOOL) {
                attributes.put(GenAiAttributes.SYSTEM, nullToUnknown(context.system()));
                attributes.put(GenAiAttributes.REQUEST_MODEL, nullToUnknown(context.requestModel()));
            }
            if (ObjectHelper.isNotEmpty(context.componentScheme())) {
                attributes.put(GenAiAttributes.CAMEL_COMPONENT, context.componentScheme());
            }
            if (level.includes(GenAiObservabilityLevel.DETAILED)) {
                if (ObjectHelper.isNotEmpty(context.system()) && !"unknown".equals(context.system())) {
                    attributes.put(GenAiAttributes.PROVIDER_NAME, context.system());
                }
                putIfNotEmpty(attributes, GenAiAttributes.AGENT_NAME, context.agentName());
                putIfNotEmpty(attributes, GenAiAttributes.TOOL_NAME, context.toolName());
                putIfNotEmpty(attributes, GenAiAttributes.TOOL_CALL_ID, context.toolCallId());
                putIfNotEmpty(attributes, GenAiAttributes.CONVERSATION_ID, context.conversationId());
                context.requestParameters()
                        .forEach((name, value) -> attributes.put(GenAiAttributes.REQUEST_PREFIX + name, value));
            }
            return attributes;
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
        public void recordCachedInputTokens(Long cachedInputTokens) {
            if (cachedInputTokens != null && level.includes(GenAiObservabilityLevel.DETAILED)) {
                endAttributes.put(GenAiAttributes.CACHE_READ_INPUT_TOKENS, cachedInputTokens);
            }
        }

        @Override
        public void recordInputMessages(Supplier<GenAiMessages> messages) {
            recordMessages(GenAiAttributes.INPUT_MESSAGES, messages);
        }

        @Override
        public void recordOutputMessages(Supplier<GenAiMessages> messages) {
            recordMessages(GenAiAttributes.OUTPUT_MESSAGES, messages);
        }

        @Override
        public void recordSystemInstructions(Supplier<String> instructions) {
            recordContent(GenAiAttributes.SYSTEM_INSTRUCTIONS,
                    () -> {
                        String text = instructions.get();
                        return text != null ? GenAiMessages.systemInstructions(text, contentMaxLength) : null;
                    });
        }

        @Override
        public void recordToolDefinitions(Supplier<GenAiToolDefinitions> definitions) {
            recordContent(GenAiAttributes.TOOL_DEFINITIONS, () -> {
                GenAiToolDefinitions value = definitions.get();
                return value != null && !value.isEmpty() ? value.toJson(contentMaxLength) : null;
            });
        }

        @Override
        public void recordToolArguments(Supplier<String> arguments) {
            recordContent(GenAiAttributes.TOOL_CALL_ARGUMENTS,
                    () -> GenAiMessages.truncate(arguments.get(), contentMaxLength));
        }

        @Override
        public void recordToolResult(Supplier<String> result) {
            recordContent(GenAiAttributes.TOOL_CALL_RESULT, () -> GenAiMessages.truncate(result.get(), contentMaxLength));
        }

        private void recordMessages(String key, Supplier<GenAiMessages> messages) {
            recordContent(key, () -> {
                GenAiMessages value = messages.get();
                return value != null && !value.isEmpty() ? value.toJson(contentMaxLength) : null;
            });
        }

        private void recordContent(String key, Supplier<String> content) {
            if (closed || !level.includes(GenAiObservabilityLevel.FULL)) {
                return;
            }
            try {
                String value = content.get();
                if (value != null) {
                    endAttributes.put(key, value);
                }
            } catch (RuntimeException e) {
                // content capture must never break the call being observed
                LOG.debug("Unable to record GenAI content {}", key, e);
            }
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (micrometerObservation != null) {
                // the Observation got the low-cardinality keys when it started; the rest goes as high-cardinality values
                Map<String, Object> attributes = startAttributes();
                attributes.keySet().removeAll(LOW_CARDINALITY_KEYS);
                attributes.putAll(endAttributes());
                micrometerObservation.stop(attributes, error);
            } else {
                closeSpan(endAttributes());
            }
            recordMetrics();
        }

        private Map<String, Object> endAttributes() {
            Map<String, Object> attributes = new LinkedHashMap<>(endAttributes);
            if (usage != null) {
                if (error == null && ObjectHelper.isNotEmpty(usage.responseModel())) {
                    attributes.put(GenAiAttributes.RESPONSE_MODEL, usage.responseModel());
                }
                if (usage.inputTokens() != null) {
                    attributes.put(GenAiAttributes.INPUT_TOKENS, usage.inputTokens());
                }
                if (usage.outputTokens() != null) {
                    attributes.put(GenAiAttributes.OUTPUT_TOKENS, usage.outputTokens());
                }
                if (usage.finishReason() != null) {
                    attributes.put(GenAiAttributes.FINISH_REASONS, usage.finishReason());
                }
            }
            if (error != null) {
                attributes.put(GenAiAttributes.ERROR_TYPE, error.getClass().getSimpleName());
            }
            return attributes;
        }

        private void closeSpan(Map<String, Object> attributes) {
            if (span == null || tracer == null || tracer.getSpanLifecycleManager() == null) {
                return;
            }
            SpanLifecycleManager lifecycleManager = tracer.getSpanLifecycleManager();
            if (error != null) {
                span.setError(true);
            }
            attributes.forEach((key, value) -> setTag(span, key, value));
            if (pushed && STORAGE.peek(exchange) == span) {
                STORAGE.pull(exchange);
            }
            lifecycleManager.deactivate(span);
            lifecycleManager.close(span);
            span = null;
        }

        private void recordMetrics() {
            // the GenAI client metrics measure model calls; agent runs and tool calls are only traced
            if (!metricsBackend.isAvailable() || isAgentOrTool()) {
                return;
            }
            if (micrometerObservation != null) {
                metricsBackend.recordTokenUsage(context, usage, error);
            } else {
                metricsBackend.recordMetrics(context, usage, error, startNanos);
            }
        }

        private static void setTag(Span span, String key, Object value) {
            if (value instanceof Long || value instanceof Integer || value instanceof Short) {
                span.setTag(key, ((Number) value).longValue());
            } else if (value instanceof Double || value instanceof Float) {
                span.setTag(key, ((Number) value).doubleValue());
            } else if (value != null) {
                span.setTag(key, value.toString());
            }
        }

        private static void putIfNotEmpty(Map<String, Object> attributes, String key, String value) {
            if (ObjectHelper.isNotEmpty(value)) {
                attributes.put(key, value);
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

    private static final class NoMetricsBackend implements GenAiMetricsBackend {
        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public void recordMetrics(GenAiObservationContext context, GenAiUsage usage, Throwable error, long startNanos) {
            // noop
        }

        @Override
        public void recordTokenUsage(GenAiObservationContext context, GenAiUsage usage, Throwable error) {
            // noop
        }
    }

    private static final class NoObservationBackend implements GenAiMicrometerObservationBackend {
        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public Handle start(GenAiObservationContext context, Handle parent) {
            return null;
        }
    }
}
