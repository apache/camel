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

public class OpenAIMockResponsesTest {

    @RegisterExtension
    public OpenAIMock openAIMock = new OpenAIMock().builder()
            .when("approval please")
            .replyWithResponsesOutput("""
                    [{"type":"mcp_approval_request","id":"mcpr_1","server_label":"deepwiki",
                      "name":"ask_question","arguments":"{}"}]""")
            .end()
            .when("custom")
            .thenRespondWith((exchange, input) -> "{\"id\":\"resp_custom\",\"object\":\"response\",\"output\":[]}")
            .end()
            .build();

    @Test
    public void testOutputItemsAreReturned() throws Exception {
        JsonNode response = post("{\"model\":\"gpt-5\",\"input\":\"approval please\"}");

        assertEquals("completed", response.path("status").asText());
        assertEquals("mcp_approval_request", response.path("output").get(0).path("type").asText());
        assertEquals("deepwiki", response.path("output").get(0).path("server_label").asText());
    }

    @Test
    public void testCustomResponseFunctionIsUsed() throws Exception {
        JsonNode response = post("{\"model\":\"gpt-5\",\"input\":\"custom\"}");

        assertEquals("resp_custom", response.path("id").asText());
    }

    private JsonNode post(String body) throws Exception {
        try (CloseableHttpClient hc = new CloseableHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(openAIMock.getBaseUrl() + "/v1/responses"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            return new ObjectMapper().readTree(hc.send(request, HttpResponse.BodyHandlers.ofString()).body());
        }
    }
}
