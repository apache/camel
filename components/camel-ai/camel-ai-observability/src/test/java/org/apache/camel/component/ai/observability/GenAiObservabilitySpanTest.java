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

import org.apache.camel.CamelContext;
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

class GenAiObservabilitySpanTest extends ExchangeTestSupport {

    private RecordingTracer tracer;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        tracer = new RecordingTracer();
        CamelContextAware.trySetCamelContext(tracer, context);
        tracer.init(context);
        return context;
    }

    @Test
    void shouldCreateSpanWithGenAiAttributesOnSuccess() {
        Exchange exchange = new DefaultExchange(context);
        GenAiObservationContext observationContext = GenAiObservationContext.builder()
                .operationName(GenAiOperationName.CHAT)
                .system("openai")
                .requestModel("gpt-4o")
                .componentScheme("langchain4j-chat")
                .build();

        GenAiObservation observation = GenAiObservability.start(exchange, observationContext);
        observation.recordSuccess(GenAiUsage.of(12, 8, "stop", "gpt-4o-mini"));
        observation.close();

        assertThat(tracer.closedSpans()).hasSize(1);
        Map<String, String> tags = tracer.closedSpans().get(0).tags();
        assertThat(tags.get(GenAiAttributes.OPERATION_NAME)).isEqualTo("chat");
        assertThat(tags.get(GenAiAttributes.SYSTEM)).isEqualTo("openai");
        assertThat(tags.get(GenAiAttributes.REQUEST_MODEL)).isEqualTo("gpt-4o");
        assertThat(tags.get(GenAiAttributes.RESPONSE_MODEL)).isEqualTo("gpt-4o-mini");
        assertThat(tags.get(GenAiAttributes.INPUT_TOKENS)).isEqualTo("12");
        assertThat(tags.get(GenAiAttributes.OUTPUT_TOKENS)).isEqualTo("8");
        assertThat(tags.get(GenAiAttributes.FINISH_REASONS)).isEqualTo("stop");
        assertThat(tags.get(GenAiAttributes.CAMEL_COMPONENT)).isEqualTo("langchain4j-chat");
    }

    @Test
    void shouldRecordLargeTokenCountsOnSpanAttributes() {
        long largeInput = Integer.MAX_VALUE + 4096L;
        long largeOutput = Integer.MAX_VALUE + 8192L;

        Exchange exchange = new DefaultExchange(context);
        GenAiObservation observation = GenAiObservability.start(exchange, GenAiObservationContext.builder()
                .operationName(GenAiOperationName.CHAT)
                .system("openai")
                .requestModel("gpt-4.1")
                .componentScheme("openai")
                .build());
        observation.recordSuccess(GenAiUsage.of(largeInput, largeOutput, "stop", "gpt-4.1"));
        observation.close();

        Map<String, String> tags = tracer.closedSpans().get(0).tags();
        assertThat(tags.get(GenAiAttributes.INPUT_TOKENS)).isEqualTo(Long.toString(largeInput));
        assertThat(tags.get(GenAiAttributes.OUTPUT_TOKENS)).isEqualTo(Long.toString(largeOutput));
    }

    @Test
    void shouldMarkSpanAsErrorWhenFailureRecorded() {
        Exchange exchange = new DefaultExchange(context);
        GenAiObservation observation = GenAiObservability.start(exchange, GenAiObservationContext.builder()
                .operationName(GenAiOperationName.EMBEDDINGS)
                .requestModel("text-embedding-3-small")
                .build());
        observation.recordError(new IllegalStateException("rate limited"));
        observation.close();

        assertThat(tracer.closedSpans()).hasSize(1);
        RecordingSpan span = tracer.closedSpans().get(0);
        assertThat(span.error()).isTrue();
        assertThat(span.tags().get(GenAiAttributes.ERROR_TYPE)).isEqualTo("IllegalStateException");
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
                return new RecordingSpan(spanName);
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

        private final String name;
        private final Map<String, String> tags = new HashMap<>();
        private boolean error;

        private RecordingSpan(String name) {
            this.name = name;
        }

        Map<String, String> tags() {
            return tags;
        }

        boolean error() {
            return error;
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
            this.error = isError;
        }

        String name() {
            return name;
        }
    }
}
