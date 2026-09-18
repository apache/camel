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
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class OpenAIMockErrorsTest {

    @RegisterExtension
    public OpenAIMock openAIMock = new OpenAIMock().builder()
            .when("slow down")
            .replyWithError(429, "rate_limit_exceeded", "Rate limit reached")
            .withRetryAfter(7)
            .end()
            .when("server trouble")
            .replyWithError(500, "server_error", "The server had an error")
            .end()
            .build();

    @Test
    public void testChatCompletionRateLimitError() throws Exception {
        HttpResponse<String> response = post("/v1/chat/completions",
                "{\"messages\": [{\"role\": \"user\", \"content\": \"slow down\"}]}");

        assertEquals(429, response.statusCode());
        assertEquals("7", response.headers().firstValue("Retry-After").orElse(null));
        JsonNode error = new ObjectMapper().readTree(response.body()).path("error");
        assertEquals("rate_limit_exceeded", error.path("type").asText());
        assertEquals("Rate limit reached", error.path("message").asText());
    }

    @Test
    public void testResponsesServerError() throws Exception {
        HttpResponse<String> response = post("/v1/responses", "{\"model\": \"gpt-5\", \"input\": \"server trouble\"}");

        assertEquals(500, response.statusCode());
        assertEquals("server_error", new ObjectMapper().readTree(response.body()).path("error").path("type").asText());
    }

    @Test
    public void testRetryAfterRequiresAnError() {
        OpenAIMockBuilder builder = new OpenAIMock().builder().when("no error");

        assertThrows(IllegalStateException.class, () -> builder.withRetryAfter(1));
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        try (CloseableHttpClient hc = new CloseableHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(openAIMock.getBaseUrl() + path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            return hc.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
