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
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.JsonNode;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OpenAIMockSimpleAssertionTest {

    private final AtomicBoolean firstAssertionExecuted = new AtomicBoolean(false);
    private final AtomicBoolean secondAssertionExecuted = new AtomicBoolean(false);

    @RegisterExtension
    public OpenAIMock openAIMock = new OpenAIMock().builder()
            .when("first message")
            .assertRequest(request -> {
                firstAssertionExecuted.set(true);
                assertTrue(request.contains("first message"));
            })
            .replyWith("first response")
            .end()
            .when("second message")
            .assertRequest(request -> {
                secondAssertionExecuted.set(true);
                assertTrue(request.contains("second message"));
            })
            .replyWith("second response")
            .end()
            .build();

    @Test
    public void testBothAssertionsExecuted() throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        // First request
        HttpRequest request1 = HttpRequest.newBuilder()
                .uri(URI.create(openAIMock.getBaseUrl() + "/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers
                        .ofString("{\"messages\": [{\"role\": \"user\", \"content\": \"first message\"}]}"))
                .build();

        HttpResponse<String> response1 = client.send(request1, HttpResponse.BodyHandlers.ofString());
        String responseBody1 = response1.body();

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode responseJson1 = objectMapper.readTree(responseBody1);

        JsonNode choice1 = responseJson1.path("choices").get(0);
        JsonNode message1 = choice1.path("message");

        assertEquals("assistant", message1.path("role").asText());
        assertEquals("first response", message1.path("content").asText());

        // Verify first assertion was executed
        assertTrue(firstAssertionExecuted.get(), "First assertion should have been executed");

        // Second request
        HttpRequest request2 = HttpRequest.newBuilder()
                .uri(URI.create(openAIMock.getBaseUrl() + "/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers
                        .ofString("{\"messages\": [{\"role\": \"user\", \"content\": \"second message\"}]}"))
                .build();

        HttpResponse<String> response2 = client.send(request2, HttpResponse.BodyHandlers.ofString());
        String responseBody2 = response2.body();

        ObjectMapper objectMapper2 = new ObjectMapper();
        JsonNode responseJson2 = objectMapper2.readTree(responseBody2);

        JsonNode choice2 = responseJson2.path("choices").get(0);
        JsonNode message2 = choice2.path("message");

        assertEquals("assistant", message2.path("role").asText());
        assertEquals("second response", message2.path("content").asText());

        // Verify second assertion was executed
        assertTrue(secondAssertionExecuted.get(), "Second assertion should have been executed");
    }
}
