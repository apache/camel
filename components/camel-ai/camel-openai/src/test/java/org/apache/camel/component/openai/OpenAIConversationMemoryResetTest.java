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
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.openai.mock.OpenAIMock;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the documented conversation history reset: "When systemMessage is set and conversationMemory is enabled, the
 * conversation history is reset" (openai-mcp.adoc, and the systemMessage option description in OpenAIConfiguration).
 */
public class OpenAIConversationMemoryResetTest extends CamelTestSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AtomicReference<String> requestBody = new AtomicReference<>();

    @RegisterExtension
    public OpenAIMock openAIMock = new OpenAIMock().builder()
            .when("hello")
            .assertRequest(requestBody::set)
            .replyWith("hi")
            .end()
            .build();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:chat")
                        .to("openai:chat-completion?model=gpt-5&apiKey=dummy&conversationMemory=true"
                            + "&systemMessage=You are a helpful assistant&baseUrl=" + openAIMock.getBaseUrl() + "/v1");
            }
        };
    }

    @Test
    void systemMessageWithConversationMemoryMustResetHistory() throws Exception {
        List<ChatCompletionMessageParam> staleHistory = new ArrayList<>();
        staleHistory.add(ChatCompletionMessageParam.ofUser(
                ChatCompletionUserMessageParam.builder()
                        .content(ChatCompletionUserMessageParam.Content.ofText("stale-history-entry"))
                        .build()));

        Exchange result = template.request("direct:chat", e -> {
            e.setProperty("CamelOpenAIConversationHistory", staleHistory);
            e.getIn().setBody("hello");
        });

        assertThat(result.getException()).isNull();

        String request = requestBody.get();
        assertThat(request)
                .as("The mock should have captured the chat completion request")
                .isNotNull();

        JsonNode messages = MAPPER.readTree(request).get("messages");
        assertThat(messages).isNotNull();

        assertThat(messages)
                .as("systemMessage + conversationMemory=true is documented to reset the conversation history, "
                    + "so the stale history entry must not be sent to the model. Request was: %s", request)
                .noneMatch(message -> "stale-history-entry".equals(message.path("content").asText()));
    }
}
