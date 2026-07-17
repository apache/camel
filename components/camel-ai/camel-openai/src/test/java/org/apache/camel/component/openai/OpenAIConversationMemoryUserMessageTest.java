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

import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.openai.mock.OpenAIMock;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproducer for the conversation memory contract documented in openai-component.adoc ("Conversation Memory (Per
 * Exchange)"): with {@code conversationMemory=true}, a second call on the same Exchange must send the previous
 * <b>user</b> turn to the model, not only the previous assistant turn.
 *
 * <p>
 * Currently {@code OpenAIProducer.updateConversationHistory} only appends the assistant response to the history
 * property, so user messages are silently dropped from the conversation sent on subsequent turns.
 */
public class OpenAIConversationMemoryUserMessageTest extends CamelTestSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AtomicReference<String> secondRequestBody = new AtomicReference<>();

    @RegisterExtension
    public OpenAIMock openAIMock = new OpenAIMock().builder()
            .when("My name is Alice")
            .replyWith("Nice to meet you!")
            .end()
            .when("What is my name?")
            .assertRequest(secondRequestBody::set)
            .replyWith("Your name is Alice.")
            .end()
            .build();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:conversation")
                        .to("openai:chat-completion?model=gpt-5&apiKey=dummy&conversationMemory=true&baseUrl="
                            + openAIMock.getBaseUrl() + "/v1")
                        .setBody(constant("What is my name?"))
                        .to("openai:chat-completion?model=gpt-5&apiKey=dummy&conversationMemory=true&baseUrl="
                            + openAIMock.getBaseUrl() + "/v1");
            }
        };
    }

    @Test
    void conversationHistoryMustIncludePreviousUserMessage() throws Exception {
        Exchange result = template.request("direct:conversation", e -> e.getIn().setBody("My name is Alice"));

        assertNull(result.getException());
        assertEquals("Your name is Alice.", result.getMessage().getBody(String.class));

        String request = secondRequestBody.get();
        assertNotNull(request, "The mock should have captured the second chat completion request");

        JsonNode messages = MAPPER.readTree(request).get("messages");
        assertNotNull(messages);

        boolean previousUserTurnPresent = false;
        boolean previousAssistantTurnPresent = false;
        for (JsonNode message : messages) {
            String role = message.path("role").asText();
            String content = message.path("content").asText();
            if ("user".equals(role) && "My name is Alice".equals(content)) {
                previousUserTurnPresent = true;
            }
            if ("assistant".equals(role) && "Nice to meet you!".equals(content)) {
                previousAssistantTurnPresent = true;
            }
        }

        assertTrue(previousAssistantTurnPresent,
                "The previous assistant turn must be part of the conversation history sent to the model");
        assertTrue(previousUserTurnPresent,
                "The previous user turn must be part of the conversation history sent to the model, "
                                            + "otherwise the model sees assistant answers without the questions that produced them. Request was: "
                                            + request);
    }
}
