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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for Spring AI Chat multiple messages (conversation history) operation.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Disabled unless running in CI")
public class SpringAiChatMultipleMessagesIT extends OllamaTestSupport {

    @Test
    public void testConversationHistoryWithContext() {
        List<Message> conversation = new ArrayList<>();
        conversation.add(new SystemMessage("You are a helpful assistant that answers questions about geography."));
        conversation.add(new UserMessage("What is the capital of Italy?"));
        conversation.add(new AssistantMessage("Rome"));
        conversation.add(new UserMessage("What is its population? Answer with just the approximate number in millions."));

        String response = template().requestBody("direct:conversation", conversation, String.class);

        assertThat(response).isNotNull();
        assertThat(response).isNotEmpty();
        // The assistant should understand "its" refers to Rome from context
        assertThat(response).containsAnyOf("2", "3", "4", "million");
    }

    @Test
    public void testMultipleMessagesWithTokenTracking() {
        List<Message> messages = new ArrayList<>();
        messages.add(new UserMessage("Say 'hello world' in one word"));

        var exchange = template().request("direct:conversation", e -> {
            e.getIn().setBody(messages);
        });

        String response = exchange.getMessage().getBody(String.class);
        assertThat(response).isNotNull();

        // Verify token usage
        Integer inputTokens = exchange.getMessage().getHeader(SpringAiChatConstants.INPUT_TOKEN_COUNT, Integer.class);
        Integer outputTokens = exchange.getMessage().getHeader(SpringAiChatConstants.OUTPUT_TOKEN_COUNT, Integer.class);
        Integer totalTokens = exchange.getMessage().getHeader(SpringAiChatConstants.TOTAL_TOKEN_COUNT, Integer.class);

        assertThat(inputTokens).isNotNull().isGreaterThan(0);
        assertThat(outputTokens).isNotNull().isGreaterThan(0);
        assertThat(totalTokens).isEqualTo(inputTokens + outputTokens);
    }

    @Test
    public void testMultiTurnConversation() {
        List<Message> conversation = new ArrayList<>();
        conversation.add(new UserMessage("My favorite color is blue."));
        conversation.add(new AssistantMessage("That's nice! Blue is a calming color."));
        conversation.add(new UserMessage("What did I say my favorite color was? Answer in one word."));

        String response = template().requestBody("direct:conversation", conversation, String.class);

        assertThat(response).isNotNull();
        assertThat(response.toLowerCase()).contains("blue");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                bindChatModel(this.getCamelContext());

                from("direct:conversation")
                        .to("spring-ai-chat:conversation?chatModel=#chatModel&chatOperation=CHAT_MULTIPLE_MESSAGES");
            }
        };
    }
}
