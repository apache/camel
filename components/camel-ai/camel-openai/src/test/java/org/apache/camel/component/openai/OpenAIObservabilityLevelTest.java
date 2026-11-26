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
package org.apache.camel.component.openai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ai.observability.GenAiAttributes;
import org.apache.camel.component.ai.observability.GenAiObservabilityProperties;
import org.apache.camel.telemetry.Span;
import org.apache.camel.telemetry.SpanContextPropagationExtractor;
import org.apache.camel.telemetry.SpanContextPropagationInjector;
import org.apache.camel.telemetry.SpanLifecycleManager;
import org.apache.camel.telemetry.Tracer;
import org.apache.camel.test.infra.openai.mock.OpenAIMock;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAIObservabilityLevelTest extends CamelTestSupport {

    @RegisterExtension
    static OpenAIMock openAIMock = new OpenAIMock().builder()
            .when("hello")
            .replyWith("Hi from mock")
            .end()
            .build();

    private final List<RecordingSpan> closedSpans = new ArrayList<>();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        Tracer tracer = new Tracer() {
            @Override
            protected void initTracer() {
                setSpanLifecycleManager(new RecordingLifecycleManager());
            }
        };
        CamelContextAware.trySetCamelContext(tracer, context);
        tracer.init(context);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:chat")
                        .to("openai:chat-completion?model=gpt-4o&apiKey=dummy&temperature=0.3&maxTokens=50"
                            + "&systemMessage=Be brief&baseUrl=" + openAIMock.getBaseUrl() + "/v1");
            }
        };
    }

    private Map<String, Object> chat(String level) {
        context.getPropertiesComponent().addOverrideProperty(GenAiObservabilityProperties.LEVEL, level);
        template.request("direct:chat", e -> {
            e.getIn().setBody("hello");
            e.getIn().setHeader(OpenAIConstants.CONVERSATION_ID, "conv-7");
        });
        List<RecordingSpan> genAi = closedSpans.stream()
                .filter(span -> span.tags().containsKey(GenAiAttributes.OPERATION_NAME))
                .toList();
        assertThat(genAi).hasSize(1);
        return genAi.get(0).tags();
    }

    @Test
    void basicShouldNotRecordDetailsOrContent() {
        assertThat(chat("basic")).doesNotContainKeys(GenAiAttributes.CONVERSATION_ID,
                GenAiAttributes.REQUEST_PREFIX + "temperature", GenAiAttributes.INPUT_MESSAGES);
    }

    @Test
    void detailedShouldRecordRequestParametersAndConversation() {
        assertThat(chat("detailed"))
                .containsEntry(GenAiAttributes.CONVERSATION_ID, "conv-7")
                .containsEntry(GenAiAttributes.REQUEST_PREFIX + "temperature", 0.3)
                .containsEntry(GenAiAttributes.REQUEST_PREFIX + "max_tokens", 50L)
                .containsEntry(GenAiAttributes.PROVIDER_NAME, "openai")
                .doesNotContainKeys(GenAiAttributes.INPUT_MESSAGES, GenAiAttributes.OUTPUT_MESSAGES);
    }

    @Test
    void fullShouldRecordTheMessages() {
        Map<String, Object> tags = chat("full");

        assertThat(tags).containsEntry(GenAiAttributes.INPUT_MESSAGES,
                "[{\"role\":\"system\",\"parts\":[{\"type\":\"text\",\"content\":\"Be brief\"}]},"
                                                                       + "{\"role\":\"user\",\"parts\":[{\"type\":\"text\",\"content\":\"hello\"}]}]");
        assertThat((String) tags.get(GenAiAttributes.OUTPUT_MESSAGES))
                .startsWith("[{\"role\":\"assistant\",\"parts\":[{\"type\":\"text\",\"content\":\"Hi from mock\"}]");
    }

    private final class RecordingLifecycleManager implements SpanLifecycleManager {

        @Override
        public Span create(String spanName, String spanKind, Span parent, SpanContextPropagationExtractor extractor) {
            return new RecordingSpan(new HashMap<>());
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

    private record RecordingSpan(Map<String, Object> tags) implements Span {

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
