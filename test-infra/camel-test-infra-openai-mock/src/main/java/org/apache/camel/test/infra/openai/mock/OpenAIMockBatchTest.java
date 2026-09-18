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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.JsonNode;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the Files and Batch APIs of the mock over plain HTTP: the multipart upload, the status progression and the
 * cancel flow.
 */
public class OpenAIMockBatchTest {

    private static final String LINE
            = "{\"custom_id\":\"ticket-1\",\"method\":\"POST\",\"url\":\"/v1/chat/completions\",\"body\":{}}";

    @RegisterExtension
    public OpenAIMock openAIMock = new OpenAIMock().builder()
            .whenBatchRequest("ticket-1").replyWithBatchContent("billing").end()
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testUploadWithABoundaryHoldingRegexMetacharacters() throws Exception {
        try (CloseableHttpClient hc = new CloseableHttpClient()) {
            // RFC 2046 allows these characters in a boundary, and a client is free to use them
            String fileId = upload(hc, "----Boundary(x+1)?.");
            JsonNode batch = createBatch(hc, fileId);

            JsonNode completed = retrieveUntil(hc, batch.path("id").asText(), "completed");

            // the single line of the file was found, so the boundary split worked
            assertEquals(1, completed.path("request_counts").path("total").asInt());
            assertEquals(1, completed.path("request_counts").path("completed").asInt());
        }
    }

    @Test
    public void testCancelMovesTheBatchToCancelled() throws Exception {
        try (CloseableHttpClient hc = new CloseableHttpClient()) {
            String fileId = upload(hc, "----Boundary");
            String batchId = createBatch(hc, fileId).path("id").asText();

            HttpResponse<String> cancel = hc.send(post(openAIMock.getBaseUrl() + "/v1/batches/" + batchId + "/cancel",
                    "application/json", ""), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, cancel.statusCode());
            assertEquals("cancelling", objectMapper.readTree(cancel.body()).path("status").asText());

            JsonNode cancelled = retrieve(hc, batchId);
            assertEquals("cancelled", cancelled.path("status").asText());
            // a cancelled batch is final: it stays cancelled and cannot be cancelled again
            assertEquals("cancelled", retrieve(hc, batchId).path("status").asText());
            HttpResponse<String> again = hc.send(post(openAIMock.getBaseUrl() + "/v1/batches/" + batchId + "/cancel",
                    "application/json", ""), HttpResponse.BodyHandlers.ofString());
            assertEquals(400, again.statusCode());
        }
    }

    private String upload(CloseableHttpClient hc, String boundary) throws Exception {
        String body = "--" + boundary + "\r\n"
                      + "Content-Disposition: form-data; name=\"purpose\"\r\n\r\n"
                      + "batch\r\n"
                      + "--" + boundary + "\r\n"
                      + "Content-Disposition: form-data; name=\"file\"; filename=\"input.jsonl\"\r\n"
                      + "Content-Type: application/octet-stream\r\n\r\n"
                      + LINE + "\n\r\n"
                      + "--" + boundary + "--\r\n";
        HttpResponse<String> response = hc.send(
                post(openAIMock.getBaseUrl() + "/v1/files", "multipart/form-data; boundary=" + boundary, body),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        JsonNode file = objectMapper.readTree(response.body());
        assertEquals("input.jsonl", file.path("filename").asText());
        assertEquals(LINE.length() + 1, file.path("bytes").asInt());
        return file.path("id").asText();
    }

    private JsonNode createBatch(CloseableHttpClient hc, String fileId) throws Exception {
        HttpResponse<String> response = hc.send(post(openAIMock.getBaseUrl() + "/v1/batches", "application/json",
                "{\"input_file_id\":\"" + fileId + "\",\"endpoint\":\"/v1/chat/completions\","
                                                                                                                  + "\"completion_window\":\"24h\"}"),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        return objectMapper.readTree(response.body());
    }

    private JsonNode retrieveUntil(CloseableHttpClient hc, String batchId, String status) throws Exception {
        JsonNode batch = retrieve(hc, batchId);
        for (int i = 0; i < 10 && !status.equals(batch.path("status").asText()); i++) {
            batch = retrieve(hc, batchId);
        }
        assertEquals(status, batch.path("status").asText());
        return batch;
    }

    private JsonNode retrieve(CloseableHttpClient hc, String batchId) throws Exception {
        HttpResponse<String> response = hc.send(HttpRequest.newBuilder()
                .uri(URI.create(openAIMock.getBaseUrl() + "/v1/batches/" + batchId))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        return objectMapper.readTree(response.body());
    }

    private static HttpRequest post(String url, String contentType, String body) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }
}
