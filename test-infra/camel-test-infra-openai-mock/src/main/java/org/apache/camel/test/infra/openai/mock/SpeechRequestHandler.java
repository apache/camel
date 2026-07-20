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
import java.util.List;

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
    private int callIndex = 0;

    public SpeechRequestHandler(List<SpeechExpectation> expectations) {
        this.expectations = expectations;
    }

    public void handleRequest(HttpExchange exchange) throws IOException {
        // consume the request body
        try (InputStream requestBody = exchange.getRequestBody()) {
            requestBody.readAllBytes();
        }
        LOG.debug("Processing audio speech request (call #{})", callIndex);

        if (expectations.isEmpty()) {
            throw new IllegalStateException("No audio speech expectations configured");
        }

        SpeechExpectation expectation = expectations.get(callIndex % expectations.size());
        callIndex++;

        byte[] audioData = expectation.getAudioData();
        exchange.getResponseHeaders().set("Content-Type", expectation.getContentType());
        exchange.sendResponseHeaders(200, audioData.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(audioData);
        }
    }
}
