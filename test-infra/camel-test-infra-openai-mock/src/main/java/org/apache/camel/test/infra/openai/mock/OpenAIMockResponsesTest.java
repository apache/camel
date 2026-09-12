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
            .when("weather in Rome")
            .invokeTool("get_weather")
            .withParam("city", "Rome")
            .replyWith("It is sunny in Rome")
            .end()
            .when("count to three")
            .replyWith("1, 2, 3")
            .end()
            .build();

    @Test
    public void testFunctionCallsAreFollowedByTheFinalAnswer() throws Exception {
        JsonNode first = post("{\"model\":\"gpt-5\",\"input\":\"weather in Rome\"}");
        JsonNode call = first.path("output").get(0);
        assertEquals("function_call", call.path("type").asText());
        assertEquals("get_weather", call.path("name").asText());
        assertEquals("Rome", new ObjectMapper().readTree(call.path("arguments").asText()).path("city").asText());

        String callId = call.path("call_id").asText();
        JsonNode second = post("""
                {"model":"gpt-5","input":[
                  {"role":"user","content":"weather in Rome"},
                  {"type":"function_call","call_id":"%s","name":"get_weather","arguments":"{}"},
                  {"type":"function_call_output","call_id":"%s","output":"sunny"}]}""".formatted(callId, callId));
        assertEquals("It is sunny in Rome", second.path("output").get(0).path("content").get(0).path("text").asText());
    }

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

    @Test
    public void testBackgroundResponseIsQueuedThenRetrievedCompleted() throws Exception {
        JsonNode queued = post("{\"model\":\"gpt-5\",\"input\":\"count to three\",\"background\":true}");
        assertEquals("queued", queued.path("status").asText());
        assertEquals(0, queued.path("output").size());

        JsonNode retrieved = send("GET", "/v1/responses/" + queued.path("id").asText(), null);
        assertEquals("completed", retrieved.path("status").asText());
        assertEquals("1, 2, 3", retrieved.path("output").get(0).path("content").get(0).path("text").asText());
    }

    @Test
    public void testResponseCanBeCancelled() throws Exception {
        JsonNode queued = post("{\"model\":\"gpt-5\",\"input\":\"count to three\",\"background\":true}");
        String path = "/v1/responses/" + queued.path("id").asText();

        assertEquals("cancelled", send("POST", path + "/cancel", "").path("status").asText());
        assertEquals("cancelled", send("GET", path, null).path("status").asText());
    }

    @Test
    public void testUnknownResponseIdIsNotFound() throws Exception {
        try (CloseableHttpClient hc = new CloseableHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(openAIMock.getBaseUrl() + "/v1/responses/resp_unknown"))
                    .GET()
                    .build();
            assertEquals(404, hc.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
        }
    }

    private JsonNode post(String body) throws Exception {
        return send("POST", "/v1/responses", body);
    }

    private JsonNode send(String method, String path, String body) throws Exception {
        try (CloseableHttpClient hc = new CloseableHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(openAIMock.getBaseUrl() + path))
                    .header("Content-Type", "application/json")
                    .method(method, body != null
                            ? HttpRequest.BodyPublishers.ofString(body) : HttpRequest.BodyPublishers.noBody())
                    .build();
            return new ObjectMapper().readTree(hc.send(request, HttpResponse.BodyHandlers.ofString()).body());
        }
    }
}
