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

package org.apache.camel.component.langchain4j.chat;

import static org.apache.camel.component.langchain4j.chat.LangChain4jChatHeaders.AUGMENTED_DATA;
import static org.apache.camel.component.langchain4j.chat.LangChain4jChatHeaders.PROMPT_TEMPLATE;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.DefaultContent;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.NoSuchHeaderException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.langchain4j.chat.rag.LangChain4jRagAggregatorStrategy;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Requires too much network resources")
public class LangChain4jChatIT extends OllamaTestSupport {

    static final String AUGMENTEG_DATA_FOR_RAG =
            "Sweden's Armand Duplantis set a new world record of 6.25m after winning gold in the men's pole vault at Paris 2024 Olympics.";
    static final String QUESTION_FOR_RAG = "Who got the gold medal in pole vault at Paris 2024?";

    @Override
    protected RouteBuilder createRouteBuilder() {
        this.context.getRegistry().bind("chatModel", chatModel);
        LangChain4jRagAggregatorStrategy aggregatorStrategy = new LangChain4jRagAggregatorStrategy();

        return new RouteBuilder() {
            public void configure() {
                from("direct:send-simple-message")
                        .to("langchain4j-chat:test1?chatModel=#chatModel&chatOperation=CHAT_SINGLE_MESSAGE")
                        .onException(InvalidPayloadException.class) // Handle InvalidPayloadException
                        .handled(true)
                        .to("mock:invalid-payload")
                        .end()
                        .to("mock:response");

                from("direct:send-message-prompt")
                        .to("langchain4j-chat:test2?chatModel=#chatModel&chatOperation=CHAT_SINGLE_MESSAGE_WITH_PROMPT")
                        .onException(InvalidPayloadException.class) // Handle InvalidPayloadException
                        .handled(true)
                        .to("mock:invalid-payload")
                        .end()
                        .onException(NoSuchHeaderException.class) // Handle NoSuchHeaderException
                        .handled(true)
                        .to("mock:invalid-header")
                        .end()
                        .to("mock:response");

                from("direct:send-multiple")
                        .to("langchain4j-chat:test2?chatModel=#chatModel&chatOperation=CHAT_MULTIPLE_MESSAGES")
                        .onException(InvalidPayloadException.class) // Handle InvalidPayloadException
                        .handled(true)
                        .to("mock:invalid-payload")
                        .end()
                        .to("mock:response");

                from("direct:send-with-rag")
                        .enrich("direct:add-augmented-data", aggregatorStrategy)
                        .to("direct:send-simple-message");

                from("direct:send-message-prompt-enrich")
                        .enrich("direct:add-augmented-data", aggregatorStrategy)
                        .to("direct:send-message-prompt");

                from("direct:send-multiple-enrich")
                        .enrich("direct:add-augmented-data", aggregatorStrategy)
                        .to("direct:send-multiple");

                from("direct:add-augmented-data").process(exchange -> {
                    List<String> augmentedData = List.of(AUGMENTEG_DATA_FOR_RAG);
                    exchange.getIn().setBody(augmentedData);
                });
            }
        };
    }

    @Test
    void testSendMessage() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        String response =
                template.requestBody("direct:send-simple-message", "Hello my name is Darth Vader!", String.class);
        mockEndpoint.assertIsSatisfied();
    }

    @Test
    void testSendChatMessage() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        ChatMessage userMessage = new UserMessage("Hello my name is Darth Vader!");

        String response = template.requestBody("direct:send-simple-message", userMessage, String.class);
        mockEndpoint.assertIsSatisfied();
        assertNotNull(response);
    }

    @Test
    void testSendEmptyMessage() throws InterruptedException {
        MockEndpoint mockErrorHandler = this.context.getEndpoint("mock:invalid-payload", MockEndpoint.class);
        mockErrorHandler.expectedMessageCount(1);

        template.sendBody("direct:send-simple-message", null);
        // Assert that the error message is routed to the mock error endpoint
        mockErrorHandler.assertIsSatisfied();
    }

    @Test
    void testSendMessageWithPrompt() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        // Example copied from Langchain4j examples
        var promptTemplate = "Create a recipe for a {{dishType}} with the following ingredients: {{ingredients}}";

        Map<String, Object> variables = new HashMap<>();
        variables.put("dishType", "oven dish");
        variables.put("ingredients", "potato, tomato, feta, olive oil");

        String response = template.requestBodyAndHeader(
                "direct:send-message-prompt", variables, PROMPT_TEMPLATE, promptTemplate, String.class);
        mockEndpoint.assertIsSatisfied();

        assertTrue(response.contains("potato"));
        assertTrue(response.contains("tomato"));
        assertTrue(response.contains("feta"));
        assertTrue(response.contains("olive oil"));
    }

    @Test
    void testSendMessageEmptyPrompt() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:invalid-header", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        Map<String, Object> variables = new HashMap<>();
        variables.put("dishType", "oven dish");
        variables.put("ingredients", "potato, tomato, feta, olive oil");

        template.sendBody("direct:send-message-prompt", variables);
        mockEndpoint.assertIsSatisfied();
    }

    @Test
    void testSendMessageEmptyVariables() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:invalid-payload", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        // Example copied from Langchain4j examples
        var promptTemplate = "Create a recipe for a {{dishType}} with the following ingredients: {{ingredients}}";

        template.sendBodyAndHeader("direct:send-message-prompt", null, PROMPT_TEMPLATE, promptTemplate);
        mockEndpoint.assertIsSatisfied();
    }

    @Test
    void testSendMultipleMessages() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(
                new SystemMessage("You are asked to provide recommendations for a restaurant based on user reviews."));
        messages.add(new UserMessage("Hello, my name is Karen."));
        messages.add(new AiMessage("Hello Karen, how can I help you?"));
        messages.add(new UserMessage("I'd like you to recommend a restaurant for me."));
        messages.add(new AiMessage("Sure, what type of cuisine are you interested in?"));
        messages.add(new UserMessage("I'd like Moroccan food."));
        messages.add(new AiMessage("Sure, do you have a preference for the location?"));
        messages.add(new UserMessage("Paris, Rue Montorgueil."));

        String response = template.requestBody("direct:send-multiple", messages, String.class);
        mockEndpoint.assertIsSatisfied();

        assertNotNull(response);
    }

    @Test
    void testSendMultipleEmpty() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:invalid-payload", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        template.sendBody("direct:send-multiple", null);
        mockEndpoint.assertIsSatisfied();
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    void testSimpleMessageWithEnrichRAG() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        var response = template.requestBody("direct:send-with-rag", QUESTION_FOR_RAG, String.class);

        // this test could change if using an LLM updated after results of Olympics 2024
        assertTrue(response.toLowerCase().contains("armand duplantis"));
        mockEndpoint.assertIsSatisfied();
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testSimpleMessageWithHeaderhRAG() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        Content augmentedContent = new DefaultContent(AUGMENTEG_DATA_FOR_RAG);

        List<Content> contents = List.of(augmentedContent);

        var response = template.requestBodyAndHeader(
                "direct:send-simple-message", QUESTION_FOR_RAG, AUGMENTED_DATA, contents, String.class);

        // this test could change if using an LLM updated after results of Olympics 2024
        assertTrue(response.toLowerCase().contains("armand duplantis"));
        mockEndpoint.assertIsSatisfied();
    }

    @Test
    void testSendMessageWithPromptRagEnrich() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        var promptTemplate = " Who got the gold medal in {{field}} at: {{competition}}";

        Map<String, Object> variables = new HashMap<>();
        variables.put("field", "pole vault");
        variables.put("competition", "Paris 2024");

        String response = template.requestBodyAndHeader(
                "direct:send-message-prompt-enrich", variables, PROMPT_TEMPLATE, promptTemplate, String.class);
        mockEndpoint.assertIsSatisfied();

        // this test could change if using an LLM updated after results of Olympics 2024
        assertTrue(response.toLowerCase().contains("armand duplantis"));
    }

    @Test
    void testSendMessageWithPromptRagHeader() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        var promptTemplate = " Who got the gold medal in {{field}} at: {{competition}}";

        Map<String, Object> variables = new HashMap<>();
        variables.put("field", "pole vault");
        variables.put("competition", "Paris 2024");

        Content augmentedContent = new DefaultContent(AUGMENTEG_DATA_FOR_RAG);

        List<Content> contents = List.of(augmentedContent);

        Map<String, Object> headerValues = new HashMap<>();
        headerValues.put(PROMPT_TEMPLATE, promptTemplate);
        headerValues.put(AUGMENTED_DATA, contents);

        String response =
                template.requestBodyAndHeaders("direct:send-message-prompt", variables, headerValues, String.class);
        mockEndpoint.assertIsSatisfied();

        // this test could change if using an LLM updated after results of Olympics 2024
        assertTrue(response.toLowerCase().contains("armand duplantis"));
    }

    @Test
    void testSendMultipleMessagesRagEnrich() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage(
                "You are asked to provide names for athletes that won medals at Paris 2024 Olympics."));
        messages.add(new UserMessage("Hello, my name is Karen."));
        messages.add(new AiMessage("Hello Karen, how can I help you?"));
        messages.add(new UserMessage(QUESTION_FOR_RAG));

        String response = template.requestBody("direct:send-multiple-enrich", messages, String.class);
        mockEndpoint.assertIsSatisfied();

        // this test could change if using an LLM updated after results of Olympics 2024
        assertTrue(response.toLowerCase().contains("armand duplantis"));
    }

    @Test
    void testSendMultipleMessagesRagHeader() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage(
                "You are asked to provide names for athletes that won medals at Paris 2024 Olympics."));
        messages.add(new UserMessage("Hello, my name is Karen."));
        messages.add(new AiMessage("Hello Karen, how can I help you?"));
        messages.add(new UserMessage(QUESTION_FOR_RAG));

        Content augmentedContent = new DefaultContent(AUGMENTEG_DATA_FOR_RAG);
        List<Content> contents = List.of(augmentedContent);

        String response =
                template.requestBodyAndHeader("direct:send-multiple", messages, AUGMENTED_DATA, contents, String.class);
        mockEndpoint.assertIsSatisfied();

        // this test could change if using an LLM updated after results of Olympics 2024
        assertTrue(response.toLowerCase().contains("armand duplantis"));
    }
}
