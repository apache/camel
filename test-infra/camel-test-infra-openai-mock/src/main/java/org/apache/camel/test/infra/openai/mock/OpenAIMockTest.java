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
package org.apache.camel.test.infra.openai.mock;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.JsonNode;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OpenAIMockTest {

    @RegisterExtension
    public OpenAIMock openAIMock = new OpenAIMock().builder()
            .when("any sentence")
            .invokeTool("toolName")
            .withParam("param1", "value1")
            .end()
            .when("another sentence")
            .replyWith("hello World")
            .end()
            .when("multiple tools")
            .invokeTool("tool1")
            .withParam("p1", "v1")
            .andInvokeTool("tool2")
            .withParam("p2", "v2")
            .withParam("p3", "v3")
            .end()
            .when("custom response")
            .thenRespondWith(
                    (request, input) -> "Custom response for: " + input)
            .end()
            .when("assert request")
            .assertRequest(request -> {
                Assertions.assertEquals("test", request);
            })
            .replyWith("Request asserted successfully")
            .build();

    @Test
    public void testToolResponse() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(openAIMock.getBaseUrl() + "/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers
                        .ofString("{\"messages\": [{\"role\": \"user\", \"content\": \"any sentence\"}]}"))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String responseBody = response.body();

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode responseJson = objectMapper.readTree(responseBody);

        JsonNode choice = responseJson.path("choices").get(0);
        JsonNode message = choice.path("message");

        assertEquals("assistant", message.path("role").asText());
        assertEquals(true, message.path("content").isNull());
        assertEquals(true, message.path("refusal").isNull());

        JsonNode toolCalls = message.path("tool_calls");
        assertEquals(1, toolCalls.size());

        JsonNode toolCall = toolCalls.get(0);
        assertEquals("function", toolCall.path("type").asText());
        assertEquals("toolName", toolCall.path("function").path("name").asText());
        assertEquals("{\"param1\":\"value1\"}", toolCall.path("function").path("arguments").asText());
    }

    @Test
    public void testChatResponse() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(openAIMock.getBaseUrl() + "/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers
                        .ofString("{\"messages\": [{\"role\": \"user\", \"content\": \"another sentence\"}]}"))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String responseBody = response.body();

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode responseJson = objectMapper.readTree(responseBody);

        JsonNode choice = responseJson.path("choices").get(0);
        JsonNode message = choice.path("message");

        assertEquals("assistant", message.path("role").asText());
        assertEquals("hello World", message.path("content").asText());
        assertEquals(true, message.path("refusal").isNull());
        assertEquals(true, message.path("tool_calls").isMissingNode());
    }

    @Test
    public void testMultipleToolCallsResponse() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(openAIMock.getBaseUrl() + "/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers
                        .ofString("{\"messages\": [{\"role\": \"user\", \"content\": \"multiple tools\"}]}"))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String responseBody = response.body();

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode responseJson = objectMapper.readTree(responseBody);

        JsonNode choice = responseJson.path("choices").get(0);
        JsonNode message = choice.path("message");

        assertEquals("assistant", message.path("role").asText());
        assertEquals(true, message.path("content").isNull());
        assertEquals(true, message.path("refusal").isNull());

        JsonNode toolCalls = message.path("tool_calls");
        assertEquals(2, toolCalls.size());

        JsonNode toolCall1 = toolCalls.get(0);
        assertEquals("function", toolCall1.path("type").asText());
        assertEquals("tool1", toolCall1.path("function").path("name").asText());
        assertEquals("{\"p1\":\"v1\"}", toolCall1.path("function").path("arguments").asText());

        JsonNode toolCall2 = toolCalls.get(1);
        assertEquals("function", toolCall2.path("type").asText());
        assertEquals("tool2", toolCall2.path("function").path("name").asText());
        assertEquals("{\"p2\":\"v2\",\"p3\":\"v3\"}", toolCall2.path("function").path("arguments").asText());
    }

    @Test
    public void testCustomResponse() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(openAIMock.getBaseUrl() + "/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers
                        .ofString("{\"messages\": [{\"role\": \"user\", \"content\": \"custom response\"}]}"))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String responseBody = response.body();

        assertEquals("Custom response for: custom response", responseBody);
    }

    @Test
    public void testToolResponseAndStop() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request1 = HttpRequest.newBuilder()
                .uri(URI.create(openAIMock.getBaseUrl() + "/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers
                        .ofString("{\"messages\": [{\"role\": \"user\", \"content\": \"any sentence\"}]}"))
                .build();

        HttpResponse<String> response1 = client.send(request1, HttpResponse.BodyHandlers.ofString());
        String responseBody1 = response1.body();

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode responseJson1 = objectMapper.readTree(responseBody1);

        JsonNode choice1 = responseJson1.path("choices").get(0);
        JsonNode message1 = choice1.path("message");

        assertEquals("assistant", message1.path("role").asText());
        assertEquals(true, message1.path("content").isNull());
        assertEquals(true, message1.path("refusal").isNull());

        JsonNode toolCalls = message1.path("tool_calls");
        assertEquals(1, toolCalls.size());

        JsonNode toolCall = toolCalls.get(0);
        String toolCallId = toolCall.path("id").asText();
        assertEquals("function", toolCall.path("type").asText());
        assertEquals("toolName", toolCall.path("function").path("name").asText());
        assertEquals("{\"param1\":\"value1\"}", toolCall.path("function").path("arguments").asText());

        // Second request with tool result
        String secondRequestBody = String.format(
                "{\"messages\": [{\"role\": \"user\", \"content\": \"any sentence\"}, {\"role\":\"tool\", \"tool_call_id\":\"%s\", \"content\":\"{\\\"name\\\": \\\"pippo\\\"}\"}]}",
                toolCallId);
        HttpRequest request2 = HttpRequest.newBuilder()
                .uri(URI.create(openAIMock.getBaseUrl() + "/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(secondRequestBody))
                .build();
        HttpResponse<String> response2 = client.send(request2, HttpResponse.BodyHandlers.ofString());
        String responseBody2 = response2.body();
        JsonNode responseJson2 = objectMapper.readTree(responseBody2);

        JsonNode choice2 = responseJson2.path("choices").get(0);
        assertEquals("stop", choice2.path("finish_reason").asText());
    }
}
