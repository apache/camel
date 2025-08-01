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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.JsonNode;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OpenAIMockReplyWithAfterToolTest {

    @RegisterExtension
    public OpenAIMock openAIMock = new OpenAIMock().builder()
            .when("What is the weather in london?")
            .invokeTool("FindsTheLatitudeAndLongitudeOfAGivenCity")
            .withParam("name", "London")
            .replyWith("the latitude of london is 1")
            .end()
            .build();

    @Test
    public void testReplyWithAfterTool() throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        // First request - should trigger tool call
        HttpRequest request1 = HttpRequest.newBuilder()
                .uri(URI.create(openAIMock.getBaseUrl() + "/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers
                        .ofString("{\"messages\": [{\"role\": \"user\", \"content\": \"What is the weather in london?\"}]}"))
                .build();

        HttpResponse<String> response1 = client.send(request1, HttpResponse.BodyHandlers.ofString());
        String responseBody1 = response1.body();

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode responseJson1 = objectMapper.readTree(responseBody1);

        JsonNode choice1 = responseJson1.path("choices").get(0);
        JsonNode message1 = choice1.path("message");

        assertEquals("assistant", message1.path("role").asText());
        assertEquals("tool_calls", choice1.path("finish_reason").asText());

        JsonNode toolCalls = message1.path("tool_calls");
        assertEquals(1, toolCalls.size());

        JsonNode toolCall = toolCalls.get(0);
        String toolCallId = toolCall.path("id").asText();
        assertEquals("function", toolCall.path("type").asText());
        assertEquals("FindsTheLatitudeAndLongitudeOfAGivenCity", toolCall.path("function").path("name").asText());
        assertEquals("{\"name\":\"London\"}", toolCall.path("function").path("arguments").asText());

        // Second request with tool result - should return custom reply
        String secondRequestBody = String.format(
                "{\"messages\": [{\"role\": \"user\", \"content\": \"What is the weather in london?\"}, {\"role\":\"tool\", \"tool_call_id\":\"%s\", \"content\":\"{\\\"latitude\\\": \\\"51.5074\\\", \\\"longitude\\\": \\\"-0.1278\\\"}\"}]}",
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
        JsonNode message2 = choice2.path("message");

        assertEquals("assistant", message2.path("role").asText());
        assertEquals("stop", choice2.path("finish_reason").asText());
        assertEquals("the latitude of london is 1", message2.path("content").asText());
    }
}
