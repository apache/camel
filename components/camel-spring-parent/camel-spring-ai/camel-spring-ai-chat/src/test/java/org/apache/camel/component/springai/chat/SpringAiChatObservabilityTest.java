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
package org.apache.camel.component.springai.chat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ai.observability.GenAiAttributes;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.telemetry.Span;
import org.apache.camel.telemetry.SpanContextPropagationExtractor;
import org.apache.camel.telemetry.SpanContextPropagationInjector;
import org.apache.camel.telemetry.SpanLifecycleManager;
import org.apache.camel.telemetry.Tracer;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiChatObservabilityTest extends CamelTestSupport {

    private RecordingTracer tracer;
    private ChatModel chatModel;

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
        chatModel = new org.springframework.ai.openai.StubOpenAiChatModel();
        registry.bind("chatModel", chatModel);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                SpringAiChatComponent component = new SpringAiChatComponent();
                component.setChatModel(chatModel);
                context.addComponent("spring-ai-chat", component);

                from("direct:start")
                        .to("spring-ai-chat:test")
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
        assertThat(mock.getExchanges().get(0).getMessage().getHeader(SpringAiChatConstants.MODEL_NAME))
                .isEqualTo("gpt-4o-mini");
        assertThat(mock.getExchanges().get(0).getMessage().getHeader(SpringAiChatConstants.INPUT_TOKEN_COUNT))
                .isEqualTo(3);
        assertThat(mock.getExchanges().get(0).getMessage().getHeader(SpringAiChatConstants.OUTPUT_TOKEN_COUNT))
                .isEqualTo(2);

        assertThat(tracer.genAiSpans()).hasSize(1);
        Map<String, String> tags = tracer.genAiSpans().get(0).tags();
        assertThat(tags.get(GenAiAttributes.OPERATION_NAME)).isEqualTo("chat");
        assertThat(tags.get(GenAiAttributes.REQUEST_MODEL)).isEqualTo("gpt-4o");
        assertThat(tags.get(GenAiAttributes.RESPONSE_MODEL)).isEqualTo("gpt-4o-mini");
        assertThat(tags.get(GenAiAttributes.CAMEL_COMPONENT)).isEqualTo("spring-ai-chat");
        assertThat(tags.get(GenAiAttributes.SYSTEM)).isEqualTo("openai");
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
