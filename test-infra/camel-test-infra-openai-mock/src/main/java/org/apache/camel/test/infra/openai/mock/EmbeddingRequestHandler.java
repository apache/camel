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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles incoming embedding requests and matches them to appropriate mock expectations.
 */
public class EmbeddingRequestHandler {
    private static final Logger LOG = LoggerFactory.getLogger(EmbeddingRequestHandler.class);

    private final List<EmbeddingExpectation> expectations;
    private final EmbeddingResponseBuilder responseBuilder;
    private final ObjectMapper objectMapper;

    public EmbeddingRequestHandler(List<EmbeddingExpectation> expectations, ObjectMapper objectMapper) {
        this.expectations = expectations;
        this.objectMapper = objectMapper;
        this.responseBuilder = new EmbeddingResponseBuilder(objectMapper);
    }

    public String handleRequest(HttpExchange exchange) throws IOException {
        try {
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            LOG.debug("Processing embedding request: {}", requestBody);

            JsonNode rootNode = objectMapper.readTree(requestBody);
            List<String> inputs = extractInputs(rootNode);
            String encodingFormat = extractEncodingFormat(rootNode);

            List<EmbeddingExpectation> matchedExpectations = new ArrayList<>();
            for (String input : inputs) {
                matchedExpectations.add(findExpectationByInput(input));
            }

            return responseBuilder.createBatchEmbeddingResponse(inputs, matchedExpectations, encodingFormat);
        } catch (Exception e) {
            String errorMessage = "Error processing embedding request: " + e.getMessage();
            LOG.error(errorMessage, e);
            return createErrorResponse(500, errorMessage, exchange);
        }
    }

    private String extractEncodingFormat(JsonNode rootNode) {
        JsonNode encodingNode = rootNode.get("encoding_format");
        if (encodingNode != null) {
            return encodingNode.asText();
        }
        return "float"; // default
    }

    private List<String> extractInputs(JsonNode rootNode) {
        JsonNode inputNode = rootNode.get("input");
        if (inputNode == null) {
            throw new IllegalArgumentException("Missing 'input' field in embedding request");
        }

        List<String> inputs = new ArrayList<>();
        if (inputNode.isArray()) {
            if (inputNode.isEmpty()) {
                throw new IllegalArgumentException("Empty 'input' array in embedding request");
            }
            for (JsonNode node : inputNode) {
                inputs.add(node.asText());
            }
        } else {
            inputs.add(inputNode.asText());
        }
        return inputs;
    }

    private EmbeddingExpectation findExpectationByInput(String input) {
        return expectations.stream()
                .filter(expectation -> expectation.matches(input))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        String.format("No matching embedding expectation found for input: %s", input)));
    }

    private String createErrorResponse(int statusCode, String errorMessage, HttpExchange exchange) {
        String jsonErrorMessage = String.format("{\"error\": {\"message\": \"%s\", \"type\": \"invalid_request_error\"}}",
                errorMessage);
        try {
            exchange.sendResponseHeaders(statusCode, jsonErrorMessage.length());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return jsonErrorMessage;
    }
}
