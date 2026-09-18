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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serves the Files and Batch APIs: uploading an input file, creating a batch from it, reporting its status and serving
 * the result files.
 * <p>
 * A batch walks through validating, in_progress, finalizing and completed, one step per retrieve, so a route that polls
 * sees the same sequence of states as it would against the real API. A cancel moves a running batch to
 * {@code cancelling}, and the next retrieve to {@code cancelled}. Once a final status is reached the output and error
 * files are built from the input file, matching each request line to a {@link BatchExpectation} by its
 * {@code custom_id}.
 */
public class BatchRequestHandler {
    private static final Logger LOG = LoggerFactory.getLogger(BatchRequestHandler.class);

    /**
     * The statuses a batch walks through, one step per retrieve.
     */
    private static final List<String> STATUSES = List.of("validating", "in_progress", "finalizing", "completed");

    private static final String DEFAULT_RESPONSE_BODY
            = "{\"id\":\"chatcmpl-mock\",\"object\":\"chat.completion\",\"model\":\"openai-mock\","
              + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"mock response\"},"
              + "\"finish_reason\":\"stop\"}]}";

    private final List<BatchExpectation> expectations;
    private final BatchStore store;
    private final ObjectMapper objectMapper;

    public BatchRequestHandler(List<BatchExpectation> expectations, BatchStore store, ObjectMapper objectMapper) {
        this.expectations = expectations;
        this.store = store;
        this.objectMapper = objectMapper;
    }

    /**
     * Whether this handler serves the given path, so the dispatcher can route Files and Batch calls here before the
     * handlers of the inference APIs.
     */
    public static boolean handles(String path) {
        return path.endsWith("/files") || path.contains("/files/")
                || path.endsWith("/batches") || path.contains("/batches/");
    }

    public void handleRequest(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        try {
            if (path.endsWith("/files") && "POST".equalsIgnoreCase(method)) {
                uploadFile(exchange);
            } else if (path.endsWith("/content") && "GET".equalsIgnoreCase(method)) {
                fileContent(exchange, idBefore(path, "/content"));
            } else if (path.contains("/files/") && "DELETE".equalsIgnoreCase(method)) {
                deleteFile(exchange, lastSegment(path));
            } else if (path.endsWith("/batches") && "POST".equalsIgnoreCase(method)) {
                createBatch(exchange);
            } else if (path.endsWith("/cancel") && "POST".equalsIgnoreCase(method)) {
                cancelBatch(exchange, idBefore(path, "/cancel"));
            } else if (path.contains("/batches/") && "GET".equalsIgnoreCase(method)) {
                retrieveBatch(exchange, lastSegment(path));
            } else {
                sendError(exchange, 404, "invalid_request_error", "Unsupported request: " + method + " " + path);
            }
        } catch (Exception e) {
            LOG.error("Error processing batch request {} {}", method, path, e);
            sendError(exchange, 500, "internal_error", String.valueOf(e.getMessage()));
        }
    }

    private void uploadFile(HttpExchange exchange) throws IOException {
        byte[] body;
        try (InputStream is = exchange.getRequestBody()) {
            body = is.readAllBytes();
        }
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        String boundary = contentType != null && contentType.contains("boundary=")
                ? contentType.substring(contentType.indexOf("boundary=") + "boundary=".length()).trim() : null;
        if (boundary == null) {
            sendError(exchange, 400, "invalid_request_error", "The file upload must be a multipart request");
            return;
        }

        // ISO-8859-1 maps every byte to one character, so the file part survives the split unchanged
        String raw = new String(body, StandardCharsets.ISO_8859_1);
        String filename = "unknown";
        String purpose = "unknown";
        byte[] content = new byte[0];
        // the boundary may hold regex metacharacters, so it is quoted rather than spliced into the pattern
        for (String part : raw.split(Pattern.quote("--" + boundary))) {
            int headerEnd = part.indexOf("\r\n\r\n");
            if (headerEnd < 0) {
                continue;
            }
            String headers = part.substring(0, headerEnd);
            String value = part.substring(headerEnd + 4);
            if (value.endsWith("\r\n")) {
                value = value.substring(0, value.length() - 2);
            }
            if (headers.contains("name=\"file\"")) {
                filename = between(headers, "filename=\"", "\"");
                content = value.getBytes(StandardCharsets.ISO_8859_1);
            } else if (headers.contains("name=\"purpose\"")) {
                purpose = value.trim();
            }
        }

        String id = store.addFile(filename, purpose, content);
        LOG.debug("Stored uploaded file {} ({} bytes, purpose {})", id, content.length, purpose);
        sendJson(exchange, 200, fileNode(id, filename, purpose, content.length).toString());
    }

    private void fileContent(HttpExchange exchange, String fileId) throws IOException {
        byte[] content = store.getFileContent(fileId);
        if (content == null) {
            sendError(exchange, 404, "invalid_request_error", "No such file: " + fileId);
            return;
        }
        exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
        exchange.sendResponseHeaders(200, content.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(content);
        }
    }

    private void deleteFile(HttpExchange exchange, String fileId) throws IOException {
        if (store.file(fileId) == null) {
            sendError(exchange, 404, "invalid_request_error", "No such file: " + fileId);
            return;
        }
        store.removeFile(fileId);
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", fileId);
        node.put("object", "file");
        node.put("deleted", true);
        sendJson(exchange, 200, node.toString());
    }

    private void createBatch(HttpExchange exchange) throws IOException {
        JsonNode request;
        try (InputStream is = exchange.getRequestBody()) {
            request = objectMapper.readTree(is.readAllBytes());
        }
        String inputFileId = request.path("input_file_id").asText();
        if (store.file(inputFileId) == null) {
            sendError(exchange, 404, "invalid_request_error", "No such file: " + inputFileId);
            return;
        }
        JsonNode metadata = request.get("metadata");
        BatchStore.StoredBatch batch = store.addBatch(inputFileId, request.path("endpoint").asText(),
                metadata != null && !metadata.isNull() ? metadata.toString() : null);
        maybeBuildResults(batch);
        LOG.debug("Created batch {} for input file {}", batch.id, inputFileId);
        sendJson(exchange, 200, batchNode(batch).toString());
    }

    private void retrieveBatch(HttpExchange exchange, String batchId) throws IOException {
        BatchStore.StoredBatch batch = store.batch(batchId);
        if (batch == null) {
            sendError(exchange, 404, "invalid_request_error", "No such batch: " + batchId);
            return;
        }
        if (batch.cancelling) {
            // a cancelled batch is final, so the retrieve after the cancel reports it as such, as the API does
            batch.cancelling = false;
            batch.cancelled = true;
        } else if (!batch.cancelled && batch.statusIndex < STATUSES.size() - 1) {
            batch.statusIndex++;
        }
        maybeBuildResults(batch);
        sendJson(exchange, 200, batchNode(batch).toString());
    }

    private void cancelBatch(HttpExchange exchange, String batchId) throws IOException {
        BatchStore.StoredBatch batch = store.batch(batchId);
        if (batch == null) {
            sendError(exchange, 404, "invalid_request_error", "No such batch: " + batchId);
            return;
        }
        if (isFinal(status(batch))) {
            sendError(exchange, 400, "invalid_request_error",
                    "Cannot cancel a batch with status '" + status(batch) + "'.");
            return;
        }
        batch.cancelling = true;
        sendJson(exchange, 200, batchNode(batch).toString());
    }

    private String status(BatchStore.StoredBatch batch) {
        if (batch.cancelled) {
            return "cancelled";
        }
        if (batch.cancelling) {
            return "cancelling";
        }
        return STATUSES.get(Math.min(batch.statusIndex, STATUSES.size() - 1));
    }

    private boolean isFinal(String status) {
        return "completed".equals(status) || "expired".equals(status) || "cancelled".equals(status);
    }

    /**
     * Builds the output and error files once the batch reaches a final status, as the API does when it finishes
     * processing the input file.
     */
    private void maybeBuildResults(BatchStore.StoredBatch batch) throws IOException {
        if (batch.resultsBuilt || !isFinal(status(batch))) {
            return;
        }
        batch.resultsBuilt = true;

        BatchStore.StoredFile input = store.file(batch.inputFileId);
        List<String> outputLines = new ArrayList<>();
        List<String> errorLines = new ArrayList<>();
        int line = 0;
        for (String requestLine : new String(input.content(), StandardCharsets.UTF_8).split("\r?\n")) {
            if (requestLine.isBlank()) {
                continue;
            }
            line++;
            String customId = objectMapper.readTree(requestLine).path("custom_id").asText();
            String requestId = "batch_req_" + batch.id + "_" + line;
            BatchExpectation expectation = expectations.stream()
                    .filter(candidate -> candidate.matches(customId))
                    .findFirst().orElse(null);
            if (expectation == null) {
                errorLines.add(errorLine(requestId, customId, 400, "mock_no_expectation",
                        "No batch expectation for custom_id " + customId));
            } else if (expectation.hasError()) {
                errorLines.add(errorLine(requestId, customId, expectation.getErrorStatusCode(),
                        expectation.getErrorType(), expectation.getErrorMessage()));
            } else {
                String responseBody = expectation.getResponseBody() != null
                        ? expectation.getResponseBody() : DEFAULT_RESPONSE_BODY;
                outputLines.add(outputLine(requestId, customId, responseBody));
            }
        }

        batch.total = line;
        batch.completed = outputLines.size();
        batch.failed = errorLines.size();
        if (!outputLines.isEmpty()) {
            batch.outputFileId = store.addFile(batch.id + "_output.jsonl", "batch_output",
                    String.join("\n", outputLines).getBytes(StandardCharsets.UTF_8));
        }
        if (!errorLines.isEmpty()) {
            batch.errorFileId = store.addFile(batch.id + "_error.jsonl", "batch_output",
                    String.join("\n", errorLines).getBytes(StandardCharsets.UTF_8));
        }
    }

    private String outputLine(String requestId, String customId, String responseBody) throws IOException {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("status_code", 200);
        response.put("request_id", requestId);
        response.set("body", objectMapper.readTree(responseBody));
        return resultLine(requestId, customId, response);
    }

    private String errorLine(String requestId, String customId, int statusCode, String type, String message) {
        ObjectNode error = objectMapper.createObjectNode();
        error.put("message", message);
        error.put("type", type);
        ObjectNode body = objectMapper.createObjectNode();
        body.set("error", error);
        ObjectNode response = objectMapper.createObjectNode();
        response.put("status_code", statusCode);
        response.put("request_id", requestId);
        response.set("body", body);
        return resultLine(requestId, customId, response);
    }

    private String resultLine(String requestId, String customId, ObjectNode response) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", requestId);
        node.put("custom_id", customId);
        node.set("response", response);
        node.putNull("error");
        return node.toString();
    }

    private ObjectNode fileNode(String id, String filename, String purpose, int bytes) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", id);
        node.put("object", "file");
        node.put("bytes", bytes);
        node.put("created_at", System.currentTimeMillis() / 1000);
        node.put("filename", filename);
        node.put("purpose", purpose);
        node.put("status", "processed");
        return node;
    }

    private ObjectNode batchNode(BatchStore.StoredBatch batch) throws IOException {
        String status = status(batch);
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", batch.id);
        node.put("object", "batch");
        node.put("endpoint", batch.endpoint);
        node.put("input_file_id", batch.inputFileId);
        node.put("completion_window", "24h");
        node.put("status", status);
        node.put("created_at", System.currentTimeMillis() / 1000);
        if (batch.outputFileId != null) {
            node.put("output_file_id", batch.outputFileId);
        }
        if (batch.errorFileId != null) {
            node.put("error_file_id", batch.errorFileId);
        }
        if (batch.metadata != null) {
            node.set("metadata", objectMapper.readTree(batch.metadata));
        }
        if (isFinal(status)) {
            ObjectNode counts = objectMapper.createObjectNode();
            counts.put("total", batch.total);
            counts.put("completed", batch.completed);
            counts.put("failed", batch.failed);
            node.set("request_counts", counts);
        }
        return node;
    }

    private void sendJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendError(HttpExchange exchange, int statusCode, String type, String message) throws IOException {
        ObjectNode error = objectMapper.createObjectNode();
        error.put("message", message);
        error.put("type", type);
        ObjectNode node = objectMapper.createObjectNode();
        node.set("error", error);
        sendJson(exchange, statusCode, node.toString());
    }

    private static String lastSegment(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static String idBefore(String path, String suffix) {
        String withoutSuffix = path.substring(0, path.length() - suffix.length());
        return lastSegment(withoutSuffix);
    }

    private static String between(String text, String start, String end) {
        int from = text.indexOf(start);
        if (from < 0) {
            return "unknown";
        }
        from += start.length();
        int to = text.indexOf(end, from);
        return to < 0 ? "unknown" : text.substring(from, to);
    }
}
