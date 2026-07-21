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

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.openai.mock.OpenAIMock;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAIConversationHistoryTokenLimitTest extends CamelTestSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TURN_1 = "turn-1" + "a".repeat(34);
    private static final String TURN_2 = "turn-2" + "b".repeat(34);
    private static final String TURN_3 = "turn-3" + "c".repeat(34);
    private static final String TURN_4 = "turn-4" + "d".repeat(34);

    private final AtomicReference<String> fourthRequestBody = new AtomicReference<>();

    @RegisterExtension
    public OpenAIMock openAIMock = new OpenAIMock().builder()
            .when(TURN_1)
            .replyWith("reply-1")
            .end()
            .when(TURN_2)
            .replyWith("reply-2")
            .end()
            .when(TURN_3)
            .replyWith("reply-3")
            .end()
            .when(TURN_4)
            .assertRequest(fourthRequestBody::set)
            .replyWith("reply-4")
            .end()
            .build();

    @Override
    protected RouteBuilder createRouteBuilder() {
        String endpoint = "openai:chat-completion?model=gpt-5&apiKey=dummy&conversationMemory=true"
                          + "&maxHistoryTokens=15&baseUrl=" + openAIMock.getBaseUrl() + "/v1";

        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:token-limited")
                        .to(endpoint)
                        .setBody(constant(TURN_2))
                        .to(endpoint)
                        .setBody(constant(TURN_3))
                        .to(endpoint)
                        .setBody(constant(TURN_4))
                        .to(endpoint);
            }
        };
    }

    @Test
    void maxHistoryTokensShouldTrimHistoryAndAllowSubsequentCalls() throws Exception {
        Exchange result = template.request("direct:token-limited", e -> e.getIn().setBody(TURN_1));

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(String.class)).isEqualTo("reply-4");

        @SuppressWarnings("unchecked")
        List<ChatCompletionMessageParam> history
                = result.getProperty("CamelOpenAIConversationHistory", List.class);
        assertThat(history).isNotEmpty();
        assertThat(OpenAIConversationHistoryTrimmer.estimateTokens(history)).isLessThanOrEqualTo(15);

        String request = fourthRequestBody.get();
        assertThat(request).isNotNull();

        JsonNode messages = MAPPER.readTree(request).get("messages");
        assertThat(messages).isNotNull();
        assertThat(messages)
                .noneMatch(message -> TURN_1.equals(message.path("content").asText())
                        || "reply-1".equals(message.path("content").asText())
                        || TURN_2.equals(message.path("content").asText()));
        assertThat(messages)
                .anyMatch(message -> TURN_3.equals(message.path("content").asText()));
        assertThat(messages)
                .anyMatch(message -> "reply-3".equals(message.path("content").asText()));
        assertThat(messages)
                .anyMatch(message -> TURN_4.equals(message.path("content").asText()));
    }
}
