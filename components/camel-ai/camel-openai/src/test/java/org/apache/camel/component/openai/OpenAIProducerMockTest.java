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

import java.util.Iterator;

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

public class OpenAIProducerMockTest extends CamelTestSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @RegisterExtension
    public OpenAIMock openAIMock = new OpenAIMock().builder()
            .when("hello")
            .replyWith("Hi from mock")
            .end()
            .when("hello-extra")
            .assertRequest(request -> {
                try {
                    JsonNode root = OBJECT_MAPPER.readTree(request);
                    assertTrue(root.has("traceId"), "Expected traceId in request body");
                    assertEquals(123, root.get("traceId").asInt());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            })
            .replyWith("Hi from mock")
            .end()
            .when("json please")
            .replyWith("{\"ok\":true}")
            .end()
            .when("reasoning-test")
            .replyWith("The answer is 42")
            .replyWithReasoningContent("Let me think step by step about this problem...")
            .end()
            .when("reasoning-empty-content")
            .replyWith("")
            .replyWithReasoningContent("I thought about it but content is empty")
            .end()
            .when("reasoning-with-think")
            .replyWith("<think>inline thinking</think>The final answer")
            .replyWithReasoningContent("API-level reasoning content")
            .end()
            .when("no-reasoning")
            .replyWith("Plain response")
            .end()
            .build();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Basic chat route using the mock server
                from("direct:chat")
                        .to("openai:chat-completion?model=gpt-5&apiKey=dummy&baseUrl=" + openAIMock.getBaseUrl()
                            + "/v1");

                from("direct:chat-extra")
                        .to("openai:chat-completion?model=gpt-5&apiKey=dummy&baseUrl=" + openAIMock.getBaseUrl()
                            + "/v1&additionalBodyProperty.traceId=123");

                // Streaming chat route using the mock server
                from("direct:chat-stream")
                        .to("openai:chat-completion?model=gpt-5&apiKey=dummy&streaming=true&baseUrl="
                            + openAIMock.getBaseUrl() + "/v1");

                from("direct:chat-strip-thinking")
                        .to("openai:chat-completion?model=gpt-5&apiKey=dummy&stripThinking=true&baseUrl="
                            + openAIMock.getBaseUrl() + "/v1");

                from("direct:chat-custom-header")
                        .to("openai:chat-completion?model=gpt-5&apiKey=dummy&baseUrl=" + openAIMock.getBaseUrl()
                            + "/v1&additionalResponseHeader.reasoning_content=CamelCustomReasoning");
            }
        };
    }

    @Test
    void basicChatReturnsMockedResponse() {
        Exchange result = template.request("direct:chat", e -> e.getIn().setBody("hello"));
        assertEquals("Hi from mock", result.getMessage().getBody(String.class));
        assertNotNull(result.getMessage().getHeader(OpenAIConstants.RESPONSE_ID));
        assertEquals("openai-mock", result.getMessage().getHeader(OpenAIConstants.RESPONSE_MODEL));
    }

    @Test
    void jsonSchemaHeaderParsesJsonContent() {
        Exchange result = template.request("direct:chat", e -> {
            e.getIn().setBody("json please");
            e.getIn().setHeader(OpenAIConstants.JSON_SCHEMA,
                    "{\"type\":\"object\",\"properties\":{\"ok\":{\"type\":\"boolean\"}}}");
        });
        String body = result.getMessage().getBody(String.class);
        assertNotNull(body);
        assertEquals("{\"ok\":true}", body);
    }

    @Test
    void streamingChatReturnsIteratorOfChunks() {
        Exchange result = template.request("direct:chat-stream", e -> e.getIn().setBody("hello"));

        Object body = result.getMessage().getBody();
        assertNotNull(body);
        assertTrue(body instanceof Iterator);
    }

    @Test
    void additionalBodyPropertyIsIncludedInRequestBody() {
        Exchange result = template.request("direct:chat-extra", e -> e.getIn().setBody("hello-extra"));
        assertEquals("Hi from mock", result.getMessage().getBody(String.class));
    }

    @Test
    void reasoningContentIsExtractedIntoHeader() {
        Exchange result = template.request("direct:chat", e -> e.getIn().setBody("reasoning-test"));
        assertEquals("The answer is 42", result.getMessage().getBody(String.class));
        assertEquals("Let me think step by step about this problem...",
                result.getMessage().getHeader(OpenAIConstants.REASONING_CONTENT, String.class));
    }

    @Test
    void reasoningContentWithEmptyBody() {
        Exchange result = template.request("direct:chat", e -> e.getIn().setBody("reasoning-empty-content"));
        assertEquals("", result.getMessage().getBody(String.class));
        assertEquals("I thought about it but content is empty",
                result.getMessage().getHeader(OpenAIConstants.REASONING_CONTENT, String.class));
    }

    @Test
    void noReasoningContentHeaderWhenAbsent() {
        Exchange result = template.request("direct:chat", e -> e.getIn().setBody("no-reasoning"));
        assertEquals("Plain response", result.getMessage().getBody(String.class));
        assertNull(result.getMessage().getHeader(OpenAIConstants.REASONING_CONTENT));
    }

    @Test
    void bothReasoningContentAndInlineThinkingArePopulated() {
        Exchange result = template.request("direct:chat-strip-thinking",
                e -> e.getIn().setBody("reasoning-with-think"));
        assertEquals("The final answer", result.getMessage().getBody(String.class));
        assertEquals("inline thinking",
                result.getMessage().getHeader(OpenAIConstants.THINKING_CONTENT, String.class));
        assertEquals("API-level reasoning content",
                result.getMessage().getHeader(OpenAIConstants.REASONING_CONTENT, String.class));
    }

    @Test
    void additionalResponseHeaderMapsFieldToCustomHeader() {
        Exchange result = template.request("direct:chat-custom-header",
                e -> e.getIn().setBody("reasoning-test"));
        assertEquals("The answer is 42", result.getMessage().getBody(String.class));
        assertEquals("Let me think step by step about this problem...",
                result.getMessage().getHeader("CamelCustomReasoning", String.class));
        // Built-in reasoning header should also be populated
        assertEquals("Let me think step by step about this problem...",
                result.getMessage().getHeader(OpenAIConstants.REASONING_CONTENT, String.class));
    }

    @Test
    void additionalResponseHeaderNotSetWhenFieldAbsent() {
        Exchange result = template.request("direct:chat-custom-header",
                e -> e.getIn().setBody("no-reasoning"));
        assertEquals("Plain response", result.getMessage().getBody(String.class));
        assertNull(result.getMessage().getHeader("CamelCustomReasoning"));
    }

}
