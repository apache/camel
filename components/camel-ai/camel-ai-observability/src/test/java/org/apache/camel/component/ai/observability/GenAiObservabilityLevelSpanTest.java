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
import java.util.concurrent.atomic.AtomicBoolean;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.telemetry.Span;
import org.apache.camel.telemetry.SpanContextPropagationExtractor;
import org.apache.camel.telemetry.SpanContextPropagationInjector;
import org.apache.camel.telemetry.SpanLifecycleManager;
import org.apache.camel.telemetry.SpanStorageManagerExchange;
import org.apache.camel.telemetry.Tracer;
import org.apache.camel.test.junit6.ExchangeTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenAiObservabilityLevelSpanTest extends ExchangeTestSupport {

    private RecordingTracer tracer;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        tracer = new RecordingTracer();
        CamelContextAware.trySetCamelContext(tracer, context);
        tracer.init(context);
        return context;
    }

    private void level(String level) {
        context.getPropertiesComponent().addOverrideProperty(GenAiObservabilityProperties.LEVEL, level);
    }

    private static GenAiObservationContext.Builder chat() {
        return GenAiObservationContext.builder()
                .operationName(GenAiOperationName.CHAT)
                .system("openai")
                .requestModel("gpt-4o")
                .componentScheme("openai")
                .conversationId("conv-1")
                .requestParameter("temperature", 0.2)
                .requestParameter("max_tokens", 256L);
    }

    private RecordingSpan observeChat(String level) {
        level(level);
        GenAiObservation observation = GenAiObservability.start(new DefaultExchange(context), chat().build());
        observation.recordInputMessages(() -> new GenAiMessages().text(GenAiMessages.USER, "Hello"));
        observation.recordOutputMessages(() -> new GenAiMessages().text(GenAiMessages.ASSISTANT, "Hi").finishReason("stop"));
        observation.recordCachedInputTokens(4L);
        observation.recordSuccess(GenAiUsage.of(12L, 8L, "stop", "gpt-4o-2024"));
        observation.close();
        assertThat(tracer.closedSpans()).hasSize(1);
        return tracer.closedSpans().get(0);
    }

    @Test
    void basicShouldRecordTheStableAttributesOnlyWithNumericTokens() {
        RecordingSpan span = observeChat("basic");

        assertThat(span.name()).isEqualTo("chat gpt-4o");
        assertThat(span.kind()).isEqualTo("CLIENT");
        assertThat(span.tags())
                .containsEntry(GenAiAttributes.OPERATION_NAME, "chat")
                .containsEntry(GenAiAttributes.REQUEST_MODEL, "gpt-4o")
                .containsEntry(GenAiAttributes.RESPONSE_MODEL, "gpt-4o-2024")
                .containsEntry(GenAiAttributes.INPUT_TOKENS, 12L)
                .containsEntry(GenAiAttributes.OUTPUT_TOKENS, 8L)
                .doesNotContainKeys(GenAiAttributes.CONVERSATION_ID, GenAiAttributes.PROVIDER_NAME,
                        GenAiAttributes.REQUEST_PREFIX + "temperature", GenAiAttributes.CACHE_READ_INPUT_TOKENS,
                        GenAiAttributes.INPUT_MESSAGES, GenAiAttributes.OUTPUT_MESSAGES);
    }

    @Test
    void detailedShouldAddRequestParametersCachedTokensAndConversationButNoContent() {
        RecordingSpan span = observeChat("detailed");

        assertThat(span.tags())
                .containsEntry(GenAiAttributes.PROVIDER_NAME, "openai")
                .containsEntry(GenAiAttributes.CONVERSATION_ID, "conv-1")
                .containsEntry(GenAiAttributes.REQUEST_PREFIX + "temperature", 0.2)
                .containsEntry(GenAiAttributes.REQUEST_PREFIX + "max_tokens", 256L)
                .containsEntry(GenAiAttributes.CACHE_READ_INPUT_TOKENS, 4L)
                .doesNotContainKeys(GenAiAttributes.INPUT_MESSAGES, GenAiAttributes.OUTPUT_MESSAGES);
    }

    @Test
    void fullShouldAddMessages() {
        RecordingSpan span = observeChat("full");

        assertThat(span.tags())
                .containsEntry(GenAiAttributes.INPUT_MESSAGES,
                        "[{\"role\":\"user\",\"parts\":[{\"type\":\"text\",\"content\":\"Hello\"}]}]")
                .containsEntry(GenAiAttributes.OUTPUT_MESSAGES,
                        "[{\"role\":\"assistant\",\"parts\":[{\"type\":\"text\",\"content\":\"Hi\"}],\"finish_reason\":\"stop\"}]");
    }

    @Test
    void shouldNotBuildContentBelowLevelFull() {
        level("detailed");
        AtomicBoolean built = new AtomicBoolean();
        GenAiObservation observation = GenAiObservability.start(new DefaultExchange(context), chat().build());
        observation.recordInputMessages(() -> {
            built.set(true);
            return new GenAiMessages();
        });
        observation.recordToolResult(() -> {
            built.set(true);
            return "result";
        });
        observation.close();

        assertThat(built).isFalse();
    }

    @Test
    void shouldCutContentLongerThanContentMaxLength() {
        level("full");
        context.getPropertiesComponent().addOverrideProperty(GenAiObservabilityProperties.CONTENT_MAX_LENGTH, "5");
        GenAiObservation observation = GenAiObservability.start(new DefaultExchange(context), chat().build());
        observation.recordInputMessages(() -> new GenAiMessages().text(GenAiMessages.USER, "Hello world"));
        observation.close();

        assertThat((String) tracer.closedSpans().get(0).tags().get(GenAiAttributes.INPUT_MESSAGES))
                .contains("\"content\":\"Hello...[truncated]\"");
    }

    @Test
    void shouldNestModelAndToolCallsUnderTheAgentRun() {
        level("full");
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        context.getRegistry().bind("meters", meters);
        Exchange exchange = new DefaultExchange(context);

        GenAiObservation agent = GenAiObservability.start(exchange, GenAiObservationContext.builder()
                .operationName(GenAiOperationName.INVOKE_AGENT)
                .agentName("order-agent")
                .system("openai")
                .requestModel("gpt-4o")
                .build());
        GenAiObservation call = GenAiObservability.start(exchange, chat().parent(agent).build());
        call.recordSuccess(GenAiUsage.of(10L, 5L, "tool_call", "gpt-4o"));
        call.close();
        // a tool call runs on a copy of the exchange, which carries the agent span
        Exchange toolExchange = exchange.copy();
        GenAiObservation tool = GenAiObservability.start(toolExchange, GenAiObservationContext.builder()
                .operationName(GenAiOperationName.EXECUTE_TOOL)
                .toolName("getOrderStatus")
                .toolCallId("call-1")
                .build());
        tool.recordToolArguments(() -> "{\"orderId\":\"A-1\"}");
        tool.recordToolResult(() -> "SHIPPED");
        tool.close();
        agent.close();

        RecordingSpan agentSpan = tracer.span("invoke_agent order-agent");
        RecordingSpan callSpan = tracer.span("chat gpt-4o");
        RecordingSpan toolSpan = tracer.span("execute_tool getOrderStatus");
        assertThat(agentSpan.kind()).isEqualTo("INTERNAL");
        assertThat(agentSpan.tags()).containsEntry(GenAiAttributes.AGENT_NAME, "order-agent")
                .containsEntry(GenAiAttributes.OPERATION_NAME, "invoke_agent");
        assertThat(callSpan.parent()).isSameAs(agentSpan);
        assertThat(toolSpan.parent()).isSameAs(agentSpan);
        assertThat(toolSpan.tags())
                .containsEntry(GenAiAttributes.TOOL_NAME, "getOrderStatus")
                .containsEntry(GenAiAttributes.TOOL_CALL_ID, "call-1")
                .containsEntry(GenAiAttributes.TOOL_CALL_ARGUMENTS, "{\"orderId\":\"A-1\"}")
                .containsEntry(GenAiAttributes.TOOL_CALL_RESULT, "SHIPPED")
                .doesNotContainKey(GenAiAttributes.REQUEST_MODEL);
        // the agent span is taken off the exchange when the run ends
        assertThat(new SpanStorageManagerExchange().peek(exchange)).isNull();
        // the client metrics count the model call only
        assertThat(meters.find(GenAiMetrics.CLIENT_OPERATION).timers()).hasSize(1);
        assertThat(meters.find(GenAiMetrics.CLIENT_OPERATION).tag(GenAiMetrics.TAG_OPERATION_NAME, "chat").timer())
                .isNotNull();
    }

    @Test
    void shouldRecordAnAgentRunAsOneModelCallWhenItsCallsWereNotObserved() {
        level("detailed");
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        context.getRegistry().bind("meters", meters);

        GenAiObservation agent = GenAiObservability.start(new DefaultExchange(context), GenAiObservationContext.builder()
                .operationName(GenAiOperationName.INVOKE_AGENT)
                .agentName("custom")
                .system("openai")
                .requestModel("gpt-4o")
                .build());
        agent.recordOperationName(GenAiOperationName.GENERATE_CONTENT);
        agent.recordSuccess(GenAiUsage.of(30L, 10L, "stop", null));
        agent.close();

        assertThat(tracer.span("invoke_agent custom").tags())
                .containsEntry(GenAiAttributes.OPERATION_NAME, "generate_content")
                .containsEntry(GenAiAttributes.INPUT_TOKENS, 30L);
        assertThat(meters.find(GenAiMetrics.CLIENT_OPERATION).tag(GenAiMetrics.TAG_OPERATION_NAME, "generate_content")
                .timer()).isNotNull();
    }

    @Test
    void shouldLeaveACallTracedByTheFrameworkToTheFramework() {
        GenAiObservation observation
                = GenAiObservability.start(new DefaultExchange(context), chat().tracedByFramework(true).build());
        observation.recordSuccess(GenAiUsage.of(12L, 8L, "stop", "gpt-4o"));
        observation.close();

        assertThat(tracer.closedSpans()).isEmpty();
    }

    @Test
    void shouldRecordACallTracedByTheFrameworkWhenNotDeferring() {
        context.getPropertiesComponent().addOverrideProperty(GenAiObservabilityProperties.DEFER_TO_FRAMEWORK, "false");
        GenAiObservation observation
                = GenAiObservability.start(new DefaultExchange(context), chat().tracedByFramework(true).build());
        observation.close();

        assertThat(tracer.closedSpans()).hasSize(1);
    }

    private static final class RecordingTracer extends Tracer {

        private final List<RecordingSpan> closedSpans = new ArrayList<>();

        @Override
        protected void initTracer() {
            setSpanLifecycleManager(new SpanLifecycleManager() {
                @Override
                public Span create(
                        String spanName, String spanKind, Span parent, SpanContextPropagationExtractor extractor) {
                    return new RecordingSpan(spanName, spanKind, (RecordingSpan) parent);
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
            });
        }

        List<RecordingSpan> closedSpans() {
            return closedSpans;
        }

        RecordingSpan span(String name) {
            return closedSpans.stream().filter(span -> span.name().equals(name)).findFirst().orElseThrow();
        }
    }

    private record RecordingSpan(String name, String kind, RecordingSpan parent, Map<String, Object> tags)
            implements
                Span {

        RecordingSpan(String name, String kind, RecordingSpan parent) {
            this(name, kind, parent, new HashMap<>());
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
        public void setTag(String key, long value) {
            tags.put(key, value);
        }

        @Override
        public void setTag(String key, double value) {
            tags.put(key, value);
        }

        @Override
        public void setComponent(String component) {
            tags.put("component", component);
        }

        @Override
        public void setError(boolean isError) {
            tags.put("error", isError);
        }
    }
}
