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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

/**
 * Integration test for message metadata functionality.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Disabled unless running in CI")
public class SpringAiChatMetadataIT extends OllamaTestSupport {

    @Test
    public void testUserMetadataViaHeader() {
        Map<String, Object> userMetadata = new HashMap<>();
        userMetadata.put("messageId", "msg-123");
        userMetadata.put("userId", "user-456");
        userMetadata.put("priority", "high");

        var exchange = template().request("direct:chat", e -> {
            e.getIn().setBody("What is the capital of France?");
            e.getIn().setHeader(SpringAiChatConstants.USER_METADATA, userMetadata);
        });

        String response = exchange.getMessage().getBody(String.class);
        assertThat(response).isNotNull();
        assertThat(response.toLowerCase()).contains("paris");
    }

    @Test
    public void testSystemMetadataViaHeader() {
        Map<String, Object> systemMetadata = new HashMap<>();
        systemMetadata.put("version", "1.0");
        systemMetadata.put("promptVersion", "2024-01");

        var exchange = template().request("direct:chat", e -> {
            e.getIn().setBody("Tell me one exercise.");
            e.getIn()
                    .setHeader(
                            SpringAiChatConstants.SYSTEM_MESSAGE,
                            "You are a fitness coach. Recommend one physical activity.");
            e.getIn().setHeader(SpringAiChatConstants.SYSTEM_METADATA, systemMetadata);
        });

        String response = exchange.getMessage().getBody(String.class);
        assertThat(response).isNotNull();
        assertThat(response).isNotEmpty();
    }

    @Test
    public void testBothUserAndSystemMetadata() {
        Map<String, Object> userMetadata = new HashMap<>();
        userMetadata.put("messageId", "msg-789");
        userMetadata.put("customerId", "cust-123");

        Map<String, Object> systemMetadata = new HashMap<>();
        systemMetadata.put("model", "test-model");
        systemMetadata.put("config", "standard");

        var exchange = template().request("direct:chat", e -> {
            e.getIn().setBody("What is 2 + 2?");
            e.getIn()
                    .setHeader(
                            SpringAiChatConstants.SYSTEM_MESSAGE,
                            "You are a math tutor. Provide clear, concise answers.");
            e.getIn().setHeader(SpringAiChatConstants.USER_METADATA, userMetadata);
            e.getIn().setHeader(SpringAiChatConstants.SYSTEM_METADATA, systemMetadata);
        });

        String response = exchange.getMessage().getBody(String.class);
        assertThat(response).isNotNull();
        assertThat(response).contains("4");
    }

    @Test
    public void testUserMetadataViaEndpointConfiguration() {
        // Metadata is bound in the route builder
        String response =
                template().requestBody("direct:chat-with-metadata", "What is the capital of Italy?", String.class);

        assertThat(response).isNotNull();
        assertThat(response.toLowerCase()).contains("rome");
    }

    @Test
    public void testMetadataWithPromptTemplate() {
        Map<String, Object> userMetadata = new HashMap<>();
        userMetadata.put("requestId", "req-456");
        userMetadata.put("source", "web");

        String template = "What is the capital of {country}?";
        Map<String, Object> variables = new HashMap<>();
        variables.put("country", "Germany");

        var exchange = template().request("direct:chat-prompt", e -> {
            e.getIn().setBody(variables);
            e.getIn().setHeader(SpringAiChatConstants.PROMPT_TEMPLATE, template);
            e.getIn().setHeader(SpringAiChatConstants.USER_METADATA, userMetadata);
        });

        String response = exchange.getMessage().getBody(String.class);
        assertThat(response).isNotNull();
        assertThat(response.toLowerCase()).contains("berlin");
    }

    @Test
    public void testMetadataOverridesBetweenHeaderAndConfig() {
        // Config has default metadata, header should override
        Map<String, Object> headerMetadata = new HashMap<>();
        headerMetadata.put("messageId", "override-123");
        headerMetadata.put("source", "header");

        var exchange = template().request("direct:chat-with-metadata", e -> {
            e.getIn().setBody("What is 10 + 5?");
            e.getIn().setHeader(SpringAiChatConstants.USER_METADATA, headerMetadata);
        });

        String response = exchange.getMessage().getBody(String.class);
        assertThat(response).isNotNull();
        assertThat(response).contains("15");
    }

    @Test
    public void testMetadataViaUrlParameters() {
        // Test using URL parameter syntax: userMetadata.key=value
        // This demonstrates Camel's nested property binding for Map types
        var exchange = template().request("direct:chat-url-params", e -> {
            e.getIn().setBody("What is the capital of Spain?");
            e.getIn()
                    .setHeader(
                            SpringAiChatConstants.SYSTEM_MESSAGE,
                            "You are a geography expert. Provide short, accurate answers.");
        });

        String response = exchange.getMessage().getBody(String.class);
        assertThat(response).isNotNull();
        assertThat(response.toLowerCase()).contains("madrid");
    }

    @Test
    public void testMetadataUrlParametersWithoutSystemMessage() {
        // Test metadata via URL parameters without system message
        // Only userMetadata should be applied
        String response = template().requestBody("direct:chat-url-params", "What is 5 * 5?", String.class);

        assertThat(response).isNotNull();
        assertThat(response).contains("25");
    }

    @Test
    public void testResponseMetadataHeaders() {
        // Test that response metadata headers are populated
        var exchange = template().request("direct:chat", e -> {
            e.getIn().setBody("What is the capital of France?");
        });

        String response = exchange.getMessage().getBody(String.class);
        assertThat(response).isNotNull();

        // Check that response metadata headers are set
        // Note: Not all models provide all metadata fields
        Object finishReason = exchange.getMessage().getHeader(SpringAiChatConstants.FINISH_REASON);
        Object modelName = exchange.getMessage().getHeader(SpringAiChatConstants.MODEL_NAME);
        Object responseId = exchange.getMessage().getHeader(SpringAiChatConstants.RESPONSE_ID);
        Object responseMetadata = exchange.getMessage().getHeader(SpringAiChatConstants.RESPONSE_METADATA);

        // At minimum, we should have some metadata
        // Most models provide finish reason
        assertThat(finishReason != null || modelName != null || responseId != null || responseMetadata != null)
                .as("At least one metadata field should be present")
                .isTrue();

        // Token usage should also be set
        Integer inputTokens = exchange.getMessage().getHeader(SpringAiChatConstants.INPUT_TOKEN_COUNT, Integer.class);
        Integer outputTokens = exchange.getMessage().getHeader(SpringAiChatConstants.OUTPUT_TOKEN_COUNT, Integer.class);
        Integer totalTokens = exchange.getMessage().getHeader(SpringAiChatConstants.TOTAL_TOKEN_COUNT, Integer.class);

        // Most models provide token counts
        if (inputTokens != null) {
            assertThat(inputTokens).isGreaterThan(0);
        }
        if (outputTokens != null) {
            assertThat(outputTokens).isGreaterThan(0);
        }
        if (totalTokens != null) {
            assertThat(totalTokens).isGreaterThan(0);
        }
    }

    @Test
    public void testResponseMetadataMap() {
        // Test that the full response metadata map is populated correctly
        var exchange = template().request("direct:chat", e -> {
            e.getIn().setBody("Tell me a fun fact.");
        });

        String response = exchange.getMessage().getBody(String.class);
        assertThat(response).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata =
                exchange.getMessage().getHeader(SpringAiChatConstants.RESPONSE_METADATA, Map.class);

        if (metadata != null) {
            assertThat(metadata).isNotEmpty();

            // If finish reason is present in individual header, it should also be in map
            Object finishReason = exchange.getMessage().getHeader(SpringAiChatConstants.FINISH_REASON);
            if (finishReason != null) {
                assertThat(metadata).containsKey("finishReason");
                assertThat(metadata.get("finishReason")).isEqualTo(finishReason);
            }

            // If model name is present in individual header, it should also be in map
            Object modelName = exchange.getMessage().getHeader(SpringAiChatConstants.MODEL_NAME);
            if (modelName != null) {
                assertThat(metadata).containsKey("model");
                assertThat(metadata.get("model")).isEqualTo(modelName);
            }

            // If response ID is present in individual header, it should also be in map
            Object responseId = exchange.getMessage().getHeader(SpringAiChatConstants.RESPONSE_ID);
            if (responseId != null) {
                assertThat(metadata).containsKey("id");
                assertThat(metadata.get("id")).isEqualTo(responseId);
            }
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                bindChatModel(this.getCamelContext());

                // Create default metadata maps
                Map<String, Object> defaultUserMetadata = new HashMap<>();
                defaultUserMetadata.put("application", "test-app");
                defaultUserMetadata.put("environment", "test");

                Map<String, Object> defaultSystemMetadata = new HashMap<>();
                defaultSystemMetadata.put("version", "1.0");
                defaultSystemMetadata.put("model", "default");

                getCamelContext().getRegistry().bind("defaultUserMetadata", defaultUserMetadata);
                getCamelContext().getRegistry().bind("defaultSystemMetadata", defaultSystemMetadata);

                from("direct:chat").to("spring-ai-chat:test?chatModel=#chatModel");

                from("direct:chat-with-metadata")
                        .to("spring-ai-chat:metadata?chatModel=#chatModel"
                                + "&userMetadata=#defaultUserMetadata"
                                + "&systemMetadata=#defaultSystemMetadata");

                from("direct:chat-prompt")
                        .to("spring-ai-chat:prompt?chatModel=#chatModel"
                                + "&chatOperation=CHAT_SINGLE_MESSAGE_WITH_PROMPT");

                // Route demonstrating URL parameter syntax for metadata
                // This shows how to set metadata using nested properties in the endpoint URI
                from("direct:chat-url-params")
                        .to("spring-ai-chat:urlparams?chatModel=#chatModel"
                                + "&userMetadata.test=t1"
                                + "&userMetadata.test2=t2"
                                + "&systemMetadata.test3=r3");
            }
        };
    }
}
