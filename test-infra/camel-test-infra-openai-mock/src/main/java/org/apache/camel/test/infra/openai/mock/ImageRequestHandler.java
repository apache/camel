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
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles image generation and image edit requests. Image generation is a JSON request whose prompt is used to select
 * an expectation, while image edit is a multipart request that is consumed without being parsed, so its expectations
 * are matched in the order they were declared.
 */
public class ImageRequestHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ImageRequestHandler.class);

    private final List<ImageExpectation> expectations;
    private final ImageResponseBuilder responseBuilder;
    private final ObjectMapper objectMapper;
    private final boolean multipart;
    private int callIndex;

    public ImageRequestHandler(List<ImageExpectation> expectations, ObjectMapper objectMapper, boolean multipart) {
        this.expectations = expectations;
        this.objectMapper = objectMapper;
        this.multipart = multipart;
        this.responseBuilder = new ImageResponseBuilder(objectMapper);
    }

    public String handleRequest(HttpExchange exchange) throws IOException {
        try {
            byte[] requestBody;
            try (InputStream is = exchange.getRequestBody()) {
                requestBody = is.readAllBytes();
            }

            if (expectations.isEmpty()) {
                throw new IllegalStateException("No image expectations configured");
            }

            ImageExpectation expectation;
            if (multipart) {
                LOG.debug("Processing image edit request (call #{})", callIndex);
                expectation = expectations.get(callIndex % expectations.size());
                callIndex++;
            } else {
                String body = new String(requestBody, StandardCharsets.UTF_8);
                LOG.debug("Processing image generation request: {}", body);
                expectation = findExpectationByPrompt(extractPrompt(body));
            }

            if (expectation.getRequestAssertion() != null) {
                expectation.getRequestAssertion().accept(requestBody);
            }

            return responseBuilder.createImageResponse(expectation);
        } catch (Exception e) {
            String errorMessage = "Error processing image request: " + e.getMessage();
            LOG.error(errorMessage, e);
            return createErrorResponse(500, errorMessage, exchange);
        }
    }

    private String extractPrompt(String requestBody) throws IOException {
        JsonNode promptNode = objectMapper.readTree(requestBody).get("prompt");
        if (promptNode == null) {
            throw new IllegalArgumentException("Missing 'prompt' field in image generation request");
        }
        return promptNode.asText();
    }

    private ImageExpectation findExpectationByPrompt(String prompt) {
        return expectations.stream()
                .filter(expectation -> expectation.matches(prompt))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        String.format("No matching image expectation found for prompt: %s", prompt)));
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
