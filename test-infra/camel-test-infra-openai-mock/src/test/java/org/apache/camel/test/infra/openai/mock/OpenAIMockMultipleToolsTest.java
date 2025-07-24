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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class OpenAIMockMultipleToolsTest {

    @RegisterExtension
    OpenAIMock openAIMock = new OpenAIMock().builder()
            .when("What is the weather in london?")
            .invokeTool("FindsTheLatitudeAndLongitudeOfAGivenCity")
            .withParam("name", "London")
            .andThenInvokeTool("ForecastsTheWeatherForTheGivenLatitudeAndLongitude")
            .withParam("latitude", "51.50758961965397")
            .withParam("longitude", "-0.13388057363742217")
            .build();

    @Test
    void testInvokeToolAndThenInvokeTool() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        ObjectMapper objectMapper = new ObjectMapper();

        // First request: User asks for weather in London
        HttpRequest request1 = HttpRequest.newBuilder()
                .uri(URI.create(openAIMock.getBaseUrl() + "/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers
                        .ofString("{\"messages\": [{\"role\": \"user\", \"content\": \"What is the weather in london?\"}]}"))
                .build();

        HttpResponse<String> response1 = client.send(request1, HttpResponse.BodyHandlers.ofString());
        String responseBody1 = response1.body();
        JsonNode responseJson1 = objectMapper.readTree(responseBody1);

        JsonNode choice1 = responseJson1.path("choices").get(0);
        JsonNode message1 = choice1.path("message");

        // Assert first tool call
        Assertions.assertEquals("assistant", message1.path("role").asText());
        JsonNode toolCalls1 = message1.path("tool_calls");
        Assertions.assertEquals(1, toolCalls1.size());
        JsonNode toolCall1 = toolCalls1.get(0);
        Assertions.assertEquals("FindsTheLatitudeAndLongitudeOfAGivenCity", toolCall1.path("function").path("name").asText());
        Assertions.assertEquals("{\"name\":\"London\"}", toolCall1.path("function").path("arguments").asText());
        String toolCallId1 = toolCall1.path("id").asText();
        Assertions.assertEquals("tool_calls", choice1.path("finish_reason").asText());

        // Second request: LLM provides tool output for the first tool call
        String secondRequestBody = String.format(
                "{\"messages\": [{\"role\": \"user\", \"content\": \"What is the weather in london?\"}, {\"role\":\"assistant\", \"tool_calls\": [{\"id\":\"%s\", \"type\":\"function\", \"function\":{\"name\":\"FindsTheLatitudeAndLongitudeOfAGivenCity\", \"arguments\":\"{\\\"name\\\":\\\"London\\\"}\"}}]}, {\"role\":\"tool\", \"tool_call_id\":\"%s\", \"content\":\"{\\\"latitude\\\": \\\"51.50758961965397\\\", \\\"longitude\\\": \\\"-0.13388057363742217\\\"}\"}]}",
                toolCallId1, toolCallId1);
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

        // Assert second tool call
        Assertions.assertEquals("assistant", message2.path("role").asText());
        JsonNode toolCalls2 = message2.path("tool_calls");
        Assertions.assertEquals(1, toolCalls2.size());
        JsonNode toolCall2 = toolCalls2.get(0);
        Assertions.assertEquals("ForecastsTheWeatherForTheGivenLatitudeAndLongitude",
                toolCall2.path("function").path("name").asText());
        Assertions.assertEquals("{\"latitude\":\"51.50758961965397\",\"longitude\":\"-0.13388057363742217\"}",
                toolCall2.path("function").path("arguments").asText());
        Assertions.assertEquals("tool_calls", choice2.path("finish_reason").asText());
    }
}
