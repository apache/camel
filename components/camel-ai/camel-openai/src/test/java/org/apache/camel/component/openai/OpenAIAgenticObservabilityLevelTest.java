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
import java.util.Set;

import com.openai.models.chat.completions.ChatCompletionFunctionTool;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * At observability level detailed, the automatic tool loop is one invoke_agent span with a chat generation per model
 * call and an execute_tool span per MCP tool call.
 */
class OpenAIAgenticObservabilityLevelTest extends CamelTestSupport {

    private static final String ENDPOINT = "openai:chat-completion?model=gpt-4o-mini&apiKey=dummy&autoToolExecution=true";

    @RegisterExtension
    static OpenAIMock openAIMock = new OpenAIMock().builder()
            .when("weather please")
            .invokeTool("get_weather")
            .replyWith("It is sunny")
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
        context.getPropertiesComponent().addOverrideProperty(GenAiObservabilityProperties.LEVEL, "full");
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:agent").routeId("weather-agent")
                        .to(ENDPOINT + "&baseUrl=" + openAIMock.getBaseUrl() + "/v1");
            }
        };
    }

    @Test
    void shouldRecordTheToolLoopAsAnAgentRun() {
        injectWeatherTool(ENDPOINT + "&baseUrl=" + openAIMock.getBaseUrl() + "/v1");

        assertThat(template.requestBody("direct:agent", "weather please", String.class)).isEqualTo("It is sunny");

        RecordingSpan run = span("invoke_agent weather-agent");
        List<RecordingSpan> calls = closedSpans.stream().filter(s -> s.name().equals("chat gpt-4o-mini")).toList();
        RecordingSpan tool = span("execute_tool get_weather");
        assertThat(run.tags()).containsEntry(GenAiAttributes.AGENT_NAME, "weather-agent")
                .doesNotContainKey(GenAiAttributes.INPUT_TOKENS);
        assertThat((String) run.tags().get(GenAiAttributes.INPUT_MESSAGES)).contains("weather please");
        assertThat(calls).hasSize(2).allSatisfy(call -> assertThat(call.parent()).isSameAs(run));
        assertThat(tool.parent()).isSameAs(run);
        assertThat(tool.tags()).containsEntry(GenAiAttributes.TOOL_NAME, "get_weather")
                .containsEntry(GenAiAttributes.TOOL_CALL_RESULT, "sunny");
    }

    private RecordingSpan span(String name) {
        List<RecordingSpan> spans = closedSpans.stream().filter(s -> s.name().equals(name)).toList();
        assertThat(spans).as("spans named %s", name).hasSize(1);
        return spans.get(0);
    }

    private void injectWeatherTool(String endpointUri) {
        OpenAIEndpoint endpoint = context.getEndpoint(endpointUri, OpenAIEndpoint.class);
        McpToolState current = endpoint.getMcpToolState();
        McpSyncClient client = mock(McpSyncClient.class);
        when(client.callTool(any(McpSchema.CallToolRequest.class))).thenReturn(McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent(null, "sunny", null)))
                .isError(false)
                .build());
        List<ChatCompletionFunctionTool> tools = new ArrayList<>(current.tools());
        tools.add(AiToolSpecToOpenAI.function("get_weather", "weather forecast", "{\"type\":\"object\"}"));
        endpoint.setMcpToolState(new McpToolState(
                tools, Map.of("get_weather", client), Map.of("get_weather", "stub"), Set.of(), current.routeTools()));
    }

    private final class RecordingLifecycleManager implements SpanLifecycleManager {

        @Override
        public Span create(String spanName, String spanKind, Span parent, SpanContextPropagationExtractor extractor) {
            return new RecordingSpan(spanName, parent, new HashMap<>());
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
            synchronized (closedSpans) {
                closedSpans.add((RecordingSpan) span);
            }
        }

        @Override
        public void inject(Span span, SpanContextPropagationInjector injector, boolean includeTracing) {
            // noop
        }
    }

    private record RecordingSpan(String name, Span parent, Map<String, Object> tags) implements Span {

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
        public void setComponent(String component) {
            tags.put("component", component);
        }

        @Override
        public void setError(boolean isError) {
            tags.put("error", isError);
        }
    }
}
