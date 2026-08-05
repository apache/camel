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

import java.util.Arrays;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for selecting tools by name via the {@code toolNames} option and the
 * {@link SpringAiChatConstants#TOOL_NAMES} header.
 *
 * Spring AI 2.0 removed {@code ChatClient.ChatClientRequestSpec.toolNames(String...)} and the Spring bean based
 * {@code SpringBeanToolCallbackResolver}, so the component resolves the names itself. These tests capture the
 * {@link Prompt} handed to the {@link ChatModel} and assert which tool callbacks made it through.
 */
class SpringAiChatToolNamesTest extends CamelTestSupport {

    private ChatModel mockChatModel;

    @Override
    protected void doPreSetup() throws Exception {
        super.doPreSetup();

        mockChatModel = mock(ChatModel.class);

        ChatResponse chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("mock answer"))));
        // a tool capable model: ChatClient merges the request options into the model defaults, so the
        // defaults must be ToolCallingChatOptions for the tool callbacks to survive the merge
        when(mockChatModel.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(mockChatModel.call(any(Prompt.class))).thenReturn(chatResponse);
    }

    /**
     * A name that also appears in {@code toolCallbacks} must be registered once: Spring AI 2.0 rejects a
     * {@code ToolCallingChatOptions} carrying two tools with the same name.
     */
    @Test
    void testToolNameAlreadyInConfiguredCallbacksIsNotRegisteredTwice() {
        String response = template().requestBody("direct:named", "What is the capital of France?", String.class);

        assertThat(response).isEqualTo("mock answer");
        assertThat(capturedToolNames()).containsExactlyInAnyOrder("getCapital", "getCurrentDateTime");
    }

    @Test
    void testToolNamesFromHeaderAreAdditive() {
        String response = template().requestBodyAndHeader("direct:callbacks", "What time is it?",
                SpringAiChatConstants.TOOL_NAMES, "getCurrentDateTime", String.class);

        assertThat(response).isEqualTo("mock answer");
        assertThat(capturedToolNames()).containsExactlyInAnyOrder("getCapital", "getCurrentDateTime");
    }

    /**
     * A tool bound in the Camel registry under a name of its own resolves even when it is not among the callbacks
     * otherwise available to the endpoint.
     */
    @Test
    void testToolResolvedFromRegistryByBeanName() {
        String response = template().requestBodyAndHeader("direct:callbacks", "What is the weather?",
                SpringAiChatConstants.TOOL_NAMES, "weatherTool", String.class);

        assertThat(response).isEqualTo("mock answer");
        assertThat(capturedToolNames()).containsExactlyInAnyOrder("getCapital", "getCurrentDateTime", "getWeather");
    }

    @Test
    void testUnknownToolNameFailsTheExchange() {
        Exchange exchange = template().request("direct:plain", e -> {
            e.getIn().setBody("Anything");
            e.getIn().setHeader(SpringAiChatConstants.TOOL_NAMES, "noSuchTool");
        });

        assertThat(exchange.getException()).isInstanceOf(IllegalArgumentException.class);
        assertThat(exchange.getException().getMessage()).contains("noSuchTool");
    }

    /**
     * The tool names carried by the {@link Prompt} the component handed to the {@link ChatModel}.
     */
    private List<String> capturedToolNames() {
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(mockChatModel).call(promptCaptor.capture());

        ChatOptions options = promptCaptor.getValue().getOptions();
        assertThat(options).isInstanceOf(ToolCallingChatOptions.class);

        return ((ToolCallingChatOptions) options).getToolCallbacks().stream()
                .map(callback -> callback.getToolDefinition().name())
                .toList();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.getRegistry().bind("chatModel", mockChatModel);

                ToolCallbackProvider provider = MethodToolCallbackProvider.builder()
                        .toolObjects(new MyTools())
                        .build();
                List<ToolCallback> callbacks = Arrays.asList(provider.getToolCallbacks());
                context.getRegistry().bind("myTools", callbacks);

                // a ToolCallback that is not part of the myTools list, bound under a registry name of its
                // own, to cover the registry lookup fallback
                ToolCallbackProvider extraProvider = MethodToolCallbackProvider.builder()
                        .toolObjects(new ExtraTools())
                        .build();
                context.getRegistry().bind("weatherTool", extraProvider.getToolCallbacks()[0]);

                from("direct:named")
                        .to("spring-ai-chat:named?chatModel=#chatModel&toolCallbacks=#myTools&toolNames=getCapital");

                from("direct:callbacks")
                        .to("spring-ai-chat:callbacks?chatModel=#chatModel&toolCallbacks=#myTools");

                from("direct:plain")
                        .to("spring-ai-chat:plain?chatModel=#chatModel");
            }
        };
    }

    public static class MyTools {

        @Tool(description = "Get the current date and time")
        public String getCurrentDateTime() {
            return "2026-08-05T10:30:00Z";
        }

        @Tool(description = "Get the capital city of a country")
        public String getCapital(String country) {
            return "The capital of " + country + " is unknown";
        }
    }

    public static class ExtraTools {

        @Tool(description = "Get the weather of a city")
        public String getWeather(String city) {
            return "The weather in " + city + " is unknown";
        }
    }
}
