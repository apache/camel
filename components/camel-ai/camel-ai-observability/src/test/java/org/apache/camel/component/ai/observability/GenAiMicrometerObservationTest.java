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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.micrometer.common.KeyValue;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.telemetry.Span;
import org.apache.camel.telemetry.SpanContextPropagationExtractor;
import org.apache.camel.telemetry.SpanContextPropagationInjector;
import org.apache.camel.telemetry.SpanLifecycleManager;
import org.apache.camel.telemetry.Tracer;
import org.apache.camel.test.junit6.ExchangeTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenAiMicrometerObservationTest extends ExchangeTestSupport {

    @Test
    void shouldStartAndStopObservation() {
        RecordingObservationHandler handler = bindObservationRegistry();

        observeSuccess();

        assertThat(handler.started).hasSize(1);
        assertThat(handler.stopped).hasSize(1);
        assertThat(handler.errors).isEmpty();
        assertThat(handler.stopped.get(0).getName()).isEqualTo(GenAiMetrics.CLIENT_OPERATION);
        assertThat(handler.stopped.get(0).getContextualName()).isEqualTo("chat test-model");
    }

    @Test
    void shouldRecordLowCardinalityKeysWithoutHighCardinalityData() {
        RecordingObservationHandler handler = bindObservationRegistry();

        observeSuccess();

        Map<String, String> lowCardinality = lowCardinalityMap(handler.stopped.get(0));
        assertThat(lowCardinality)
                .containsEntry(GenAiAttributes.OPERATION_NAME, "chat")
                .containsEntry(GenAiAttributes.SYSTEM, "openai")
                .containsEntry(GenAiAttributes.REQUEST_MODEL, "test-model")
                .containsEntry(GenAiAttributes.CAMEL_COMPONENT, "langchain4j-chat")
                .doesNotContainKeys(
                        GenAiAttributes.INPUT_TOKENS,
                        GenAiAttributes.OUTPUT_TOKENS,
                        GenAiAttributes.FINISH_REASONS,
                        GenAiAttributes.RESPONSE_MODEL,
                        "prompt",
                        "completion");
        assertThat(highCardinalityMap(handler.stopped.get(0))).isEmpty();
    }

    @Test
    void shouldRecordErrorThenStopObservation() {
        RecordingObservationHandler handler = bindObservationRegistry();
        ObservationRegistry registry = boundObservationRegistry();
        IllegalStateException failure = new IllegalStateException("rate limited");

        Exchange exchange = new DefaultExchange(context);
        GenAiObservation observation = GenAiObservability.start(exchange, chatContext());
        Observation current = registry.getCurrentObservation();
        assertThat(current).isNotNull();
        assertThat(current.getContext().getName()).isEqualTo(GenAiMetrics.CLIENT_OPERATION);

        observation.recordError(failure);
        observation.close();

        assertThat(handler.started).hasSize(1);
        assertThat(handler.errors).hasSize(1);
        assertThat(handler.stopped).hasSize(1);
        assertThat(handler.errors.get(0).getError()).isSameAs(failure);
        assertThat(lowCardinalityMap(handler.stopped.get(0)))
                .containsEntry(GenAiAttributes.ERROR_TYPE, "IllegalStateException");
        assertThat(registry.getCurrentObservation()).isNull();
    }

    @Test
    void shouldMakeObservationCurrentWhileOperationIsActive() {
        bindObservationRegistry();
        ObservationRegistry registry = boundObservationRegistry();

        Exchange exchange = new DefaultExchange(context);
        GenAiObservation observation = GenAiObservability.start(exchange, chatContext());

        Observation current = registry.getCurrentObservation();
        assertThat(current).isNotNull();
        assertThat(current.getContext().getName()).isEqualTo(GenAiMetrics.CLIENT_OPERATION);
        assertThat(current.getContext().getContextualName()).isEqualTo("chat test-model");

        Observation nested = Observation.createNotStarted("nested.http", registry).start();
        try (Observation.Scope nestedScope = nested.openScope()) {
            assertThat(registry.getCurrentObservation()).isSameAs(nested);
            assertThat(nested.getContext().getParentObservation()).isSameAs(current);
        } finally {
            nested.stop();
        }
        assertThat(registry.getCurrentObservation()).isSameAs(current);

        observation.recordSuccess(GenAiUsage.of(10, 5, "stop", "test-model"));
        observation.close();

        assertThat(registry.getCurrentObservation()).isNull();
    }

    @Test
    void shouldPreferObservationOverTelemetrySpan() {
        RecordingObservationHandler handler = bindObservationRegistry();
        RecordingTracer tracer = bindRecordingTracer();

        observeSuccess();

        assertThat(handler.stopped).hasSize(1);
        assertThat(tracer.closedSpans()).isEmpty();
    }

    @Test
    void shouldNotDuplicateOperationTimerAndShouldKeepTokenCounters() {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        context.getRegistry().bind("metricsRegistry", meters);

        ObservationRegistry observationRegistry = ObservationRegistry.create();
        RecordingObservationHandler handler = new RecordingObservationHandler();
        observationRegistry.observationConfig()
                .observationHandler(handler)
                .observationHandler(new DefaultMeterObservationHandler(meters));
        context.getRegistry().bind("observationRegistry", observationRegistry);

        observeSuccess();

        assertThat(handler.stopped).hasSize(1);
        Timer timer = meters.find(GenAiMetrics.CLIENT_OPERATION).timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(meters.find(GenAiMetrics.CLIENT_TOKEN_USAGE)
                .tag(GenAiMetrics.TAG_TOKEN_TYPE, GenAiMetrics.TOKEN_TYPE_INPUT)
                .counter()
                .count()).isEqualTo(10);
        assertThat(meters.find(GenAiMetrics.CLIENT_TOKEN_USAGE)
                .tag(GenAiMetrics.TAG_TOKEN_TYPE, GenAiMetrics.TOKEN_TYPE_OUTPUT)
                .counter()
                .count()).isEqualTo(5);
    }

    @Test
    void shouldKeepExistingBackendsWhenObservationRegistryIsAbsent() {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        context.getRegistry().bind("metricsRegistry", meters);
        RecordingTracer tracer = bindRecordingTracer();

        observeSuccess();

        assertThat(tracer.closedSpans()).hasSize(1);
        assertThat(tracer.closedSpans().get(0).tags())
                .containsEntry(GenAiAttributes.OPERATION_NAME, "chat")
                .containsEntry(GenAiAttributes.INPUT_TOKENS, "10");
        assertThat(meters.find(GenAiMetrics.CLIENT_OPERATION).timer()).isNotNull();
        assertThat(meters.find(GenAiMetrics.CLIENT_OPERATION).timer().count()).isEqualTo(1);
        assertThat(meters.find(GenAiMetrics.CLIENT_TOKEN_USAGE)
                .tag(GenAiMetrics.TAG_TOKEN_TYPE, GenAiMetrics.TOKEN_TYPE_INPUT)
                .counter()
                .count()).isEqualTo(10);
        assertThat(meters.find(GenAiMetrics.CLIENT_TOKEN_USAGE)
                .tag(GenAiMetrics.TAG_TOKEN_TYPE, GenAiMetrics.TOKEN_TYPE_OUTPUT)
                .counter()
                .count()).isEqualTo(5);
    }

    @Test
    void shouldReturnNoopWhenNoBackendIsAvailable() {
        Exchange exchange = new DefaultExchange(context);
        GenAiObservation observation = GenAiObservability.start(exchange, chatContext());
        observation.recordSuccess(GenAiUsage.of(10, 5, "stop", "test-model"));
        observation.close();

        assertThat(observation).isNotNull();
        assertThat(context.hasService(Tracer.class)).isNull();
        assertThat(context.getRegistry().findByType(ObservationRegistry.class)).isEmpty();
        assertThat(context.getRegistry().findByType(SimpleMeterRegistry.class)).isEmpty();
    }

    @Test
    void shouldIgnoreNoopObservationRegistry() {
        context.getRegistry().bind("observationRegistry", ObservationRegistry.NOOP);
        RecordingTracer tracer = bindRecordingTracer();

        observeSuccess();

        assertThat(tracer.closedSpans()).hasSize(1);
    }

    private void observeSuccess() {
        Exchange exchange = new DefaultExchange(context);
        GenAiObservation observation = GenAiObservability.start(exchange, chatContext());
        observation.recordSuccess(GenAiUsage.of(10, 5, "stop", "test-model"));
        observation.close();
    }

    private static GenAiObservationContext chatContext() {
        return GenAiObservationContext.builder()
                .operationName(GenAiOperationName.CHAT)
                .system("openai")
                .requestModel("test-model")
                .componentScheme("langchain4j-chat")
                .build();
    }

    private RecordingObservationHandler bindObservationRegistry() {
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        RecordingObservationHandler handler = new RecordingObservationHandler();
        observationRegistry.observationConfig().observationHandler(handler);
        context.getRegistry().bind("observationRegistry", observationRegistry);
        return handler;
    }

    private ObservationRegistry boundObservationRegistry() {
        return context.getRegistry().lookupByNameAndType("observationRegistry", ObservationRegistry.class);
    }

    private RecordingTracer bindRecordingTracer() {
        RecordingTracer tracer = new RecordingTracer();
        CamelContextAware.trySetCamelContext(tracer, context);
        tracer.init(context);
        return tracer;
    }

    private static Map<String, String> lowCardinalityMap(Observation.Context observationContext) {
        Map<String, String> values = new HashMap<>();
        for (KeyValue keyValue : observationContext.getLowCardinalityKeyValues()) {
            values.put(keyValue.getKey(), keyValue.getValue());
        }
        return values;
    }

    private static Map<String, String> highCardinalityMap(Observation.Context observationContext) {
        Map<String, String> values = new HashMap<>();
        for (KeyValue keyValue : observationContext.getHighCardinalityKeyValues()) {
            values.put(keyValue.getKey(), keyValue.getValue());
        }
        return values;
    }

    private static final class RecordingObservationHandler implements ObservationHandler<Observation.Context> {

        private final List<Observation.Context> started = new ArrayList<>();
        private final List<Observation.Context> stopped = new ArrayList<>();
        private final List<Observation.Context> errors = new ArrayList<>();

        @Override
        public void onStart(Observation.Context context) {
            started.add(context);
        }

        @Override
        public void onError(Observation.Context context) {
            errors.add(context);
        }

        @Override
        public void onStop(Observation.Context context) {
            stopped.add(context);
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }
    }

    private static final class RecordingTracer extends Tracer {

        private final List<RecordingSpan> closedSpans = new ArrayList<>();

        @Override
        protected void initTracer() {
            setSpanLifecycleManager(new RecordingSpanLifecycleManager());
        }

        List<RecordingSpan> closedSpans() {
            return closedSpans;
        }

        private final class RecordingSpanLifecycleManager implements SpanLifecycleManager {

            @Override
            public Span create(String spanName, String spanKind, Span parent, SpanContextPropagationExtractor extractor) {
                return new RecordingSpan();
            }

            @Override
            public void activate(Span span) {
                // noop
            }

            @Override
            public void deactivate(Span span) {
                // noop
            }

            @Override
            public void close(Span span) {
                closedSpans.add((RecordingSpan) span);
            }

            @Override
            public void inject(Span span, SpanContextPropagationInjector injector, boolean includeTracing) {
                // noop
            }
        }
    }

    private static final class RecordingSpan implements Span {

        private final Map<String, String> tags = new HashMap<>();

        Map<String, String> tags() {
            return tags;
        }

        @Override
        public void log(Map<String, String> fields) {
            // noop
        }

        @Override
        public void setTag(String key, String value) {
            tags.put(key, value);
        }

        @Override
        public void setComponent(String component) {
            tags.put("component", component);
        }

        @Override
        public void setError(boolean isError) {
            // noop
        }
    }
}
