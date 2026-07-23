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
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles incoming audio speech (text-to-speech) requests and returns pre-configured raw audio bytes. Does not parse
 * the request body — the mock returns the next configured expectation sequentially.
 */
public class SpeechRequestHandler {
    private static final Logger LOG = LoggerFactory.getLogger(SpeechRequestHandler.class);

    private final List<SpeechExpectation> expectations;
    private final ObjectMapper objectMapper;
    private int callIndex = 0;

    public SpeechRequestHandler(List<SpeechExpectation> expectations, ObjectMapper objectMapper) {
        this.expectations = expectations;
        this.objectMapper = objectMapper;
    }

    public void handleRequest(HttpExchange exchange) throws IOException {
        try {
            // consume the request body
            byte[] requestBody;
            try (InputStream requestStream = exchange.getRequestBody()) {
                requestBody = requestStream.readAllBytes();
            }
            LOG.debug("Processing audio speech request (call #{})", callIndex);

            if (expectations.isEmpty()) {
                throw new IllegalStateException("No audio speech expectations configured");
            }

            SpeechExpectation expectation = expectations.get(callIndex % expectations.size());
            callIndex++;

            byte[] audioData = expectation.getAudioData();
            if (audioData == null) {
                throw new IllegalStateException("No audio data configured for speech expectation");
            }
            String contentType = resolveContentType(requestBody, expectation);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, audioData.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(audioData);
            }
        } catch (Exception e) {
            String errorMessage = "Error processing audio speech request: " + e.getMessage();
            LOG.error(errorMessage, e);
            String jsonError = String.format(
                    "{\"error\": {\"message\": \"%s\", \"type\": \"invalid_request_error\"}}", errorMessage);
            byte[] errorBytes = jsonError.getBytes(StandardCharsets.UTF_8);
            try {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(500, errorBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(errorBytes);
                }
            } catch (IOException ignored) {
                // headers may already be sent
            }
        }
    }

    private String resolveContentType(byte[] requestBody, SpeechExpectation expectation) {
        if (expectation.isExplicitContentType()) {
            return expectation.getContentType();
        }
        String responseFormat = extractResponseFormat(requestBody);
        if (responseFormat != null) {
            return contentTypeFor(responseFormat);
        }
        return expectation.getContentType();
    }

    private String extractResponseFormat(byte[] requestBody) {
        try {
            JsonNode node = objectMapper.readTree(requestBody);
            JsonNode format = node.get("response_format");
            if (format != null && !format.isNull()) {
                return format.asText();
            }
        } catch (IOException e) {
            LOG.debug("Could not parse speech request body for response_format", e);
        }
        return null;
    }

    private static String contentTypeFor(String responseFormat) {
        return switch (responseFormat.toLowerCase()) {
            case "opus" -> "audio/opus";
            case "aac" -> "audio/aac";
            case "flac" -> "audio/flac";
            case "wav" -> "audio/wav";
            case "pcm" -> "audio/pcm";
            default -> "audio/mpeg";
        };
    }
}
