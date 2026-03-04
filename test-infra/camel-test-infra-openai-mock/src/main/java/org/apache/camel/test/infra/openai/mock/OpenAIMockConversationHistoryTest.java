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

public class OpenAIMockConversationHistoryTest {

    private final AtomicBoolean secondAssertionExecuted = new AtomicBoolean(false);

    @RegisterExtension
    public OpenAIMock openAIMock = new OpenAIMock().builder()
            .when("What's his preferred vehicle type?")
            .assertRequest(request -> {
                secondAssertionExecuted.set(true);
                // Assert that memory is working as expected
                assertTrue(request.contains("Hi! Can you look up user 123 and tell me about our rental policies?"));
            })
            .replyWith("SUV")
            .end()
            .build();

    @Test
    public void testConversationHistoryAssertion() throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        // Send request with conversation history
        String requestBody = "{\"messages\": [" +
                             "{\"role\": \"user\", \"content\": \"Hi! Can you look up user 123 and tell me about our rental policies?\"},"
                             +
                             "{\"role\": \"assistant\", \"content\": \"Previous response\"}," +
                             "{\"role\": \"user\", \"content\": \"What's his preferred vehicle type?\"}" +
                             "]}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(openAIMock.getBaseUrl() + "/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String responseBody = response.body();

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode responseJson = objectMapper.readTree(responseBody);

        JsonNode choice = responseJson.path("choices").get(0);
        JsonNode message = choice.path("message");

        assertEquals("assistant", message.path("role").asText());
        assertEquals("SUV", message.path("content").asText());

        // Verify assertion was executed
        assertTrue(secondAssertionExecuted.get(), "Second assertion should have been executed");
    }
}
