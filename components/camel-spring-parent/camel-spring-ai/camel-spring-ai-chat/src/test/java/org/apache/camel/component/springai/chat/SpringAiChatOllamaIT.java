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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

/**
 * Integration test for Spring AI Chat component using Ollama.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Disabled unless running in CI")
public class SpringAiChatOllamaIT extends OllamaTestSupport {

    @Test
    public void testSimpleChatWithOllama() {
        String response =
                template.requestBody("direct:chat", "What is the capital of Italy? Answer in one word.", String.class);

        assertThat(response).isNotNull();
        assertThat(response).isNotEmpty();
        assertThat(response.toLowerCase())
                .contains("rome")
                .as("Expected to contain rome but was " + response.toLowerCase());
    }

    @Test
    public void testChatWithTokenUsageHeaders() {
        var exchange = template.request("direct:chat", e -> {
            e.getIn().setBody("Say 'hello' in one word");
        });

        String response = exchange.getMessage().getBody(String.class);
        assertThat(response).isNotNull();
        assertThat(response).isNotEmpty();

        // Verify token usage headers are set
        Integer inputTokens = exchange.getMessage().getHeader(SpringAiChatConstants.INPUT_TOKEN_COUNT, Integer.class);
        Integer outputTokens = exchange.getMessage().getHeader(SpringAiChatConstants.OUTPUT_TOKEN_COUNT, Integer.class);
        Integer totalTokens = exchange.getMessage().getHeader(SpringAiChatConstants.TOTAL_TOKEN_COUNT, Integer.class);

        assertThat(inputTokens).isNotNull();
        assertThat(outputTokens).isNotNull();
        assertThat(totalTokens).isNotNull();
        assertThat(totalTokens).isEqualTo(inputTokens + outputTokens);
    }

    @Test
    public void testMultipleChatRequests() {
        String response1 =
                template.requestBody("direct:chat", "What is 2+2? Answer with just the number.", String.class);
        String response2 =
                template.requestBody("direct:chat", "What is the color of the sky? Answer in one word.", String.class);

        assertThat(response1).isNotNull();
        assertThat(response1).contains("4").as("Expected to contain 4 " + response1);

        assertThat(response2).isNotNull();
        assertThat(response2.toLowerCase())
                .containsAnyOf("blue", "azure")
                .as("Expected to contain any of blue or azure, but was " + response2);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                SpringAiChatComponent component = new SpringAiChatComponent();
                component.setChatModel(chatModel);
                context.addComponent("spring-ai-chat", component);

                from("direct:chat").to("spring-ai-chat:test");
            }
        };
    }
}
