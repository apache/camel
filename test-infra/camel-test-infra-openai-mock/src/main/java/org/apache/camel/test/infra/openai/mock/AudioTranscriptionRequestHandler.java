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
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles incoming audio transcription requests and returns pre-configured mock responses. Does not parse the multipart
 * body — the mock returns the next configured expectation sequentially.
 */
public class AudioTranscriptionRequestHandler {
    private static final Logger LOG = LoggerFactory.getLogger(AudioTranscriptionRequestHandler.class);

    private final List<AudioTranscriptionExpectation> expectations;
    private final AudioTranscriptionResponseBuilder responseBuilder;
    private int callIndex = 0;

    public AudioTranscriptionRequestHandler(List<AudioTranscriptionExpectation> expectations, ObjectMapper objectMapper) {
        this.expectations = expectations;
        this.responseBuilder = new AudioTranscriptionResponseBuilder(objectMapper);
    }

    public String handleRequest(HttpExchange exchange) throws IOException {
        try {
            // consume the request body
            exchange.getRequestBody().readAllBytes();
            LOG.debug("Processing audio transcription request (call #{})", callIndex);

            if (expectations.isEmpty()) {
                throw new IllegalStateException("No audio transcription expectations configured");
            }

            AudioTranscriptionExpectation expectation = expectations.get(callIndex % expectations.size());
            callIndex++;

            return responseBuilder.createTranscriptionResponse(expectation);
        } catch (Exception e) {
            String errorMessage = "Error processing audio transcription request: " + e.getMessage();
            LOG.error(errorMessage, e);
            String jsonError = String.format(
                    "{\"error\": {\"message\": \"%s\", \"type\": \"invalid_request_error\"}}", errorMessage);
            try {
                exchange.sendResponseHeaders(500, jsonError.length());
            } catch (IOException ignored) {
                // headers may already be sent
            }
            return jsonError;
        }
    }
}
