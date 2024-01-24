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
package org.apache.camel.component.chat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.NoSuchHeaderException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Requires too much network resources")
public class LangchainChatIT extends OllamaTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        this.context.getRegistry().bind("chatModel", chatLanguageModel);

        return new RouteBuilder() {
            public void configure() {
                from("direct:send-simple-message")
                        .to("langchain-chat:test1?chatModel=#chatModel&chatOperation=CHAT_SINGLE_MESSAGE")
                        .onException(InvalidPayloadException.class) // Handle InvalidPayloadException
                            .handled(true)
                            .to("mock:invalid-payload")
                        .end()
                        .to("mock:response");

                from("direct:send-message-prompt")
                        .to("langchain-chat:test2?chatModel=#chatModel&chatOperation=CHAT_SINGLE_MESSAGE_WITH_PROMPT")
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
                        .to("langchain-chat:test2?chatModel=#chatModel&chatOperation=CHAT_MULTIPLE_MESSAGES")
                        .onException(InvalidPayloadException.class) // Handle InvalidPayloadException
                            .handled(true)
                            .to("mock:invalid-payload")
                        .end()
                        .to("mock:response");

            };
        };
    }

    @Test
    void testSendMessage() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        String response = template.requestBody("direct:send-simple-message", "Hello my name is Darth Vader!", String.class);
        mockEndpoint.assertIsSatisfied();
    }

    @Test
    void testSendChatMessage() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        ChatMessage userMessage = new UserMessage("Hello my name is Darth Vader!");

        String response = template.requestBody("direct:send-simple-message", userMessage,
                String.class);
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

        String response = template.requestBodyAndHeader("direct:send-message-prompt", variables,
                LangchainChat.Headers.PROMPT_TEMPLATE, promptTemplate, String.class);
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

        template.sendBodyAndHeader("direct:send-message-prompt", null,
                LangchainChat.Headers.PROMPT_TEMPLATE, promptTemplate);
        mockEndpoint.assertIsSatisfied();
    }

    @Test
    void testSendMultipleMessages() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage("You are asked to provide recommendations for a restaurant based on user reviews."));
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

}
