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
package org.apache.camel.component.langchain4j.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ai.observability.GenAiAttributes;
import org.apache.camel.component.ai.observability.GenAiObservabilityProperties;
import org.apache.camel.component.langchain4j.agent.api.AgentConfiguration;
import org.apache.camel.component.langchain4j.agent.api.Headers;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.telemetry.Span;
import org.apache.camel.telemetry.SpanContextPropagationExtractor;
import org.apache.camel.telemetry.SpanContextPropagationInjector;
import org.apache.camel.telemetry.SpanLifecycleManager;
import org.apache.camel.telemetry.Tracer;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LangChain4jAgentObservabilityTest extends CamelTestSupport {

    private RecordingTracer tracer;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        tracer = new RecordingTracer();
        CamelContextAware.trySetCamelContext(tracer, context);
        tracer.init(context);
        return context;
    }

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("agentConfig", new AgentConfiguration()
                .withChatModel(new StubChatModel()));
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("langchain4j-agent:test?agentConfiguration=#agentConfig")
                        .to("mock:result");
            }
        };
    }

    @Test
    void shouldEmitGenAiSpanAndModelHeadersFromProducer() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", "Hello");

        mock.assertIsSatisfied(10, TimeUnit.SECONDS);

        assertThat(mock.getExchanges().get(0).getMessage().getBody(String.class)).isEqualTo("Hello back");
        assertThat(mock.getExchanges().get(0).getMessage().getHeader(Headers.REQUEST_MODEL)).isEqualTo("gpt-4o");
        assertThat(mock.getExchanges().get(0).getMessage().getHeader(Headers.INPUT_TOKEN_COUNT)).isEqualTo(3);
        assertThat(mock.getExchanges().get(0).getMessage().getHeader(Headers.OUTPUT_TOKEN_COUNT)).isEqualTo(2);
        assertThat(mock.getExchanges().get(0).getMessage().getHeader(Headers.TOTAL_TOKEN_COUNT)).isEqualTo(5);
        assertThat(mock.getExchanges().get(0).getMessage().getHeader(Headers.FINISH_REASON)).isEqualTo(FinishReason.STOP);
        assertThat(mock.getExchanges().get(0).getMessage().getHeader(Headers.RESPONSE_MODEL)).isNull();

        assertThat(tracer.genAiSpans()).hasSize(1);
        Map<String, String> tags = tracer.genAiSpans().get(0).tags();
        assertThat(tags.get(GenAiAttributes.OPERATION_NAME)).isEqualTo("generate_content");
        assertThat(tags.get(GenAiAttributes.SYSTEM)).isEqualTo("openai");
        assertThat(tags.get(GenAiAttributes.REQUEST_MODEL)).isEqualTo("gpt-4o");
        assertThat(tags.get(GenAiAttributes.INPUT_TOKENS)).isEqualTo("3");
        assertThat(tags.get(GenAiAttributes.OUTPUT_TOKENS)).isEqualTo("2");
        assertThat(tags.get(GenAiAttributes.FINISH_REASONS)).isEqualTo("STOP");
        assertThat(tags.get(GenAiAttributes.CAMEL_COMPONENT)).isEqualTo("langchain4j-agent");
    }

    @Test
    void shouldNotEmitGenAiSpanWhenDisabled() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(GenAiObservabilityProperties.ENABLED, "false");
        context.getPropertiesComponent().setOverrideProperties(properties);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", "Hello");

        mock.assertIsSatisfied(10, TimeUnit.SECONDS);

        assertThat(mock.getExchanges().get(0).getMessage().getBody(String.class)).isEqualTo("Hello back");
        assertThat(tracer.genAiSpans()).isEmpty();
    }

    private static final class StubChatModel implements ChatModel {
        @Override
        public ModelProvider provider() {
            return ModelProvider.OPEN_AI;
        }

        @Override
        public ChatRequestParameters defaultRequestParameters() {
            return DefaultChatRequestParameters.builder().modelName("gpt-4o").build();
        }

        @Override
        public ChatResponse doChat(ChatRequest request) {
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("Hello back"))
                    .modelName("gpt-4o")
                    .tokenUsage(new TokenUsage(3, 2, 5))
                    .finishReason(FinishReason.STOP)
                    .build();
        }
    }

    private static final class RecordingTracer extends Tracer {

        private final List<RecordingSpan> closedSpans = new ArrayList<>();

        @Override
        protected void initTracer() {
            setSpanLifecycleManager(new RecordingSpanLifecycleManager());
        }

        List<RecordingSpan> genAiSpans() {
            return closedSpans.stream()
                    .filter(span -> span.tags().containsKey(GenAiAttributes.OPERATION_NAME))
                    .toList();
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

        private final Map<String, String> tags = new HashMap<>();

        private RecordingSpan(String name) {
            tags.put("name", name);
        }

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
