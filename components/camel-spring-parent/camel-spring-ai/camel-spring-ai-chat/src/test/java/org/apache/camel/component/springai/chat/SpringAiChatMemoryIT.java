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

import org.apache.camel.builder.RouteBuilder;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test demonstrating how to use Spring AI's ChatMemory with Camel.
 *
 * This test shows how to configure and use MessageWindowChatMemory (InMemoryChatMemory) to maintain conversation
 * history across multiple interactions.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Disabled unless running in CI")
public class SpringAiChatMemoryIT extends OllamaTestSupport {

    private static final String CONVERSATION_ID = "user-123";

    @Test
    public void testChatMemoryRetainsConversationHistory() {
        // Create an in-memory chat memory
        ChatMemory chatMemory = createChatMemory();

        // First interaction - tell the assistant something
        List<Message> messages1 = new ArrayList<>();
        messages1.add(new UserMessage("My favorite color is blue."));

        String response1 = template().requestBody("direct:chat", messages1, String.class);
        assertThat(response1).isNotNull();

        // Store the conversation in memory
        chatMemory.add(CONVERSATION_ID, messages1.get(0));
        chatMemory.add(CONVERSATION_ID, new AssistantMessage(response1));

        // Second interaction - ask about what we told it before
        List<Message> messages2 = new ArrayList<>();
        // Retrieve conversation history
        List<Message> history = chatMemory.get(CONVERSATION_ID); // Get messages
        messages2.addAll(history);
        messages2.add(new UserMessage("What did I say my favorite color was? Answer in one word."));

        String response2 = template().requestBody("direct:chat", messages2, String.class);
        assertThat(response2).isNotNull();
        assertThat(response2.toLowerCase()).contains("blue");

        // Store this interaction too
        chatMemory.add(CONVERSATION_ID, messages2.get(messages2.size() - 1));
        chatMemory.add(CONVERSATION_ID, new AssistantMessage(response2));
    }

    private static @NotNull ChatMemory createChatMemory() {
        InMemoryChatMemoryRepository inMemoryChatMemoryRepository = new InMemoryChatMemoryRepository();

        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(inMemoryChatMemoryRepository)
                .maxMessages(10)
                .build();
        return chatMemory;
    }

    @Test
    public void testMultipleConversationIds() {
        ChatMemory chatMemory = createChatMemory();

        // User 1 conversation
        chatMemory.add("user-1", new UserMessage("I like pizza"));
        chatMemory.add("user-1", new AssistantMessage("Pizza is delicious!"));

        // User 2 conversation
        chatMemory.add("user-2", new UserMessage("I like sushi"));
        chatMemory.add("user-2", new AssistantMessage("Sushi is great!"));

        // Verify conversations are isolated
        List<Message> user1History = chatMemory.get("user-1");
        List<Message> user2History = chatMemory.get("user-2");

        assertThat(user1History).hasSize(2);
        assertThat(user1History.get(0).getText()).contains("pizza");

        assertThat(user2History).hasSize(2);
        assertThat(user2History.get(0).getText()).contains("sushi");
    }

    @Test
    public void testChatMemoryWithCamelProcessor() {
        ChatMemory chatMemory = createChatMemory();
        String conversationId = "conversation-with-processor";

        // First message
        var exchange1 = template().request("direct:chat-with-memory", e -> {
            e.getIn().setBody("My name is Alice.");
            e.getIn().setHeader("conversationId", conversationId);
            e.getIn().setHeader("chatMemory", chatMemory);
        });

        String response1 = exchange1.getMessage().getBody(String.class);
        assertThat(response1).isNotNull();

        // Second message - should remember the name
        var exchange2 = template().request("direct:chat-with-memory", e -> {
            e.getIn().setBody("What is my name? Answer in one word.");
            e.getIn().setHeader("conversationId", conversationId);
            e.getIn().setHeader("chatMemory", chatMemory);
        });

        String response2 = exchange2.getMessage().getBody(String.class);
        assertThat(response2).isNotNull();
        assertThat(response2.toLowerCase()).contains("alice");
    }

    @Test
    public void testClearingChatMemory() {
        ChatMemory chatMemory = createChatMemory();

        // Add some messages
        chatMemory.add(CONVERSATION_ID, new UserMessage("Remember this"));
        chatMemory.add(CONVERSATION_ID, new AssistantMessage("I will remember"));

        // Verify messages exist
        List<Message> before = chatMemory.get(CONVERSATION_ID);
        assertThat(before).hasSize(2);

        // Clear the conversation
        chatMemory.clear(CONVERSATION_ID);

        // Verify conversation is cleared
        List<Message> after = chatMemory.get(CONVERSATION_ID);
        assertThat(after).isEmpty();
    }

    @Test
    public void testAutomaticChatMemoryWithChatClient() {
        String conversationId = "auto-memory-test";

        // Note: This test demonstrates ChatMemory with ChatClient
        // The MessageChatMemoryAdvisor stores conversation in memory automatically

        // First message
        var exchange1 = template().request("direct:chat-with-auto-memory", e -> {
            e.getIn().setBody("My favorite number is 42. Please remember this.");
            e.getIn().setHeader("conversationId", conversationId);
        });

        String response1 = exchange1.getMessage().getBody(String.class);
        assertThat(response1).isNotNull();

        // Second message - the advisor should remember the context
        var exchange2 = template().request("direct:chat-with-auto-memory", e -> {
            e.getIn().setBody("What is my favorite number? Answer with just the number.");
            e.getIn().setHeader("conversationId", conversationId);
        });

        String response2 = exchange2.getMessage().getBody(String.class);
        assertThat(response2).isNotNull();

        // The MessageChatMemoryAdvisor should maintain context
        // If this fails, it means the advisor is not storing messages properly
        assertThat(response2).contains("42");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                bindChatModel(this.getCamelContext());

                // Create and register ChatMemory for automatic memory management
                ChatMemory chatMemory = createChatMemory();
                this.getCamelContext().getRegistry().bind("chatMemory", chatMemory);

                // Simple route for multiple messages
                from("direct:chat")
                        .to("spring-ai-chat:memory?chatModel=#chatModel&chatOperation=CHAT_MULTIPLE_MESSAGES");

                // Route with automatic memory management via ChatClient
                from("direct:chat-with-auto-memory")
                        .to("spring-ai-chat:auto-memory?chatModel=#chatModel&chatMemory=#chatMemory");

                // Route with memory management in processor (manual approach)
                from("direct:chat-with-memory")
                        .process(exchange -> {
                            ChatMemory memory = exchange.getIn().getHeader("chatMemory", ChatMemory.class);
                            String convId = exchange.getIn().getHeader("conversationId", String.class);
                            String userMessage = exchange.getIn().getBody(String.class);

                            // Retrieve conversation history
                            List<Message> messages = new ArrayList<>();
                            if (memory != null && convId != null) {
                                List<Message> history = memory.get(convId);
                                messages.addAll(history);
                            }

                            // Add current user message
                            UserMessage currentMessage = new UserMessage(userMessage);
                            messages.add(currentMessage);

                            // Store user message in memory
                            if (memory != null && convId != null) {
                                memory.add(convId, currentMessage);
                            }

                            exchange.getIn().setBody(messages);
                        })
                        .to("spring-ai-chat:memory?chatModel=#chatModel&chatOperation=CHAT_MULTIPLE_MESSAGES")
                        .process(exchange -> {
                            // Store assistant response in memory
                            ChatMemory memory = exchange.getIn().getHeader("chatMemory", ChatMemory.class);
                            String convId = exchange.getIn().getHeader("conversationId", String.class);
                            String response = exchange.getIn().getBody(String.class);

                            if (memory != null && convId != null) {
                                memory.add(convId, new AssistantMessage(response));
                            }
                        });
            }
        };
    }
}
