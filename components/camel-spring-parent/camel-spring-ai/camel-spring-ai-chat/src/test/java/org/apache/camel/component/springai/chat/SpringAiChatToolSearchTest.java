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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ai.tool.AiToolSearch;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpringAiChatToolSearchTest extends CamelTestSupport {

    private final List<List<String>> turns = new CopyOnWriteArrayList<>();
    private final AtomicInteger calls = new AtomicInteger();
    private ChatModel chatModel;

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();
        chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            turns.add(toolNames(prompt));
            if (calls.getAndIncrement() == 0) {
                AssistantMessage search = AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(new AssistantMessage.ToolCall(
                                "call-1", "function", AiToolSearch.TOOL_NAME, "{\"terms\":[\"invoice\"]}")))
                        .build();
                return new ChatResponse(List.of(new Generation(search)));
            }
            return new ChatResponse(List.of(new Generation(new AssistantMessage("done"))));
        });
    }

    @Test
    void searchHidesTheSearchableRouteToolUntilItIsFound() {
        String response = template.requestBody("direct:search", "find invoice", String.class);

        assertThat(response).isEqualTo("done");
        assertThat(turns).hasSizeGreaterThanOrEqualTo(2);
        assertThat(turns.get(0)).contains("ping", AiToolSearch.TOOL_NAME);
        assertThat(turns.get(0)).doesNotContain("invoice");
        assertThat(turns.get(1)).contains("invoice");
    }

    @Test
    void conversationIdsDoNotShareDiscoveries() {
        template.requestBodyAndHeader("direct:search", "first",
                SpringAiChatConstants.CONVERSATION_ID, "conversation-a", String.class);
        assertThat(turns.get(1)).contains("invoice");

        calls.set(100);
        turns.clear();
        template.requestBodyAndHeader("direct:search", "second",
                SpringAiChatConstants.CONVERSATION_ID, "conversation-b", String.class);
        assertThat(turns.get(0)).doesNotContain("invoice");
    }

    @Test
    void aChangedCallbackSetDropsAStaleName() throws Exception {
        template.requestBodyAndHeader("direct:search", "first",
                SpringAiChatConstants.CONVERSATION_ID, "conversation-c", String.class);
        assertThat(turns.get(1)).contains("invoice");

        context.getRouteController().stopRoute("invoice-route");
        calls.set(100);
        turns.clear();
        template.requestBodyAndHeader("direct:search", "again",
                SpringAiChatConstants.CONVERSATION_ID, "conversation-c", String.class);
        assertThat(turns.get(0)).doesNotContain("invoice");
    }

    @Test
    void prebuiltChatClientWithToolSearchFailsStartup() {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        ChatClient client = ChatClient.builder(model).build();
        try (DefaultCamelContext ctx = new DefaultCamelContext()) {
            ctx.getRegistry().bind("client", client);
            ctx.getRegistry().bind("model", model);
            ctx.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:prebuilt")
                            .to("spring-ai-chat:chat?chatClient=#client&chatModel=#model&toolSearch=true");
                }
            });
            assertThatThrownBy(ctx::start).hasMessageContaining("chatModel");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static List<String> toolNames(Prompt prompt) {
        if (!(prompt.getOptions() instanceof ToolCallingChatOptions options) || options.getToolCallbacks() == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        options.getToolCallbacks().forEach(callback -> names.add(callback.getToolDefinition().name()));
        return names;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.getRegistry().bind("chatModel", chatModel);
                from("ai-tool:invoice?tags=erp&searchable=true&description=billing-document-invoice")
                        .routeId("invoice-route")
                        .setBody(constant("invoice-ok"));
                from("ai-tool:ping?tags=erp&description=health-check")
                        .setBody(constant("pong"));
                from("direct:search")
                        .to("spring-ai-chat:chat?chatModel=#chatModel&tags=erp&toolSearch=true&toolSearchMaxResults=5");
            }
        };
    }
}
