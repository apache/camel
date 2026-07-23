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
import java.io.OutputStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * Dispatcher that routes incoming requests to the appropriate request handler.
 */
public class OpenAIMockServerHandler implements HttpHandler {
    private final RequestHandler chatRequestHandler;
    private final EmbeddingRequestHandler embeddingRequestHandler;
    private final AudioTranscriptionRequestHandler audioTranscriptionRequestHandler;
    private final AudioTranscriptionRequestHandler audioTranslationRequestHandler;
    private final SpeechRequestHandler speechRequestHandler;

    public OpenAIMockServerHandler(OpenAIMockExpectations expectations, ObjectMapper objectMapper) {
        this.chatRequestHandler = new RequestHandler(expectations.chat(), objectMapper);
        this.embeddingRequestHandler = new EmbeddingRequestHandler(expectations.embeddings(), objectMapper);
        this.audioTranscriptionRequestHandler
                = new AudioTranscriptionRequestHandler(expectations.transcriptions(), objectMapper);
        this.audioTranslationRequestHandler
                = new AudioTranscriptionRequestHandler(expectations.translations(), objectMapper);
        this.speechRequestHandler = new SpeechRequestHandler(expectations.speeches(), objectMapper);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            try {
                String path = exchange.getRequestURI().getPath();

                if (path.endsWith("/audio/speech")) {
                    speechRequestHandler.handleRequest(exchange);
                    return;
                }

                String response;

                if (path.endsWith("/audio/transcriptions")) {
                    response = audioTranscriptionRequestHandler.handleRequest(exchange);
                } else if (path.endsWith("/audio/translations")) {
                    response = audioTranslationRequestHandler.handleRequest(exchange);
                } else if (path.endsWith("/embeddings")) {
                    response = embeddingRequestHandler.handleRequest(exchange);
                } else {
                    response = chatRequestHandler.handleRequest(exchange);
                }

                byte[] responseBytes = response.getBytes();
                if (exchange.getResponseCode() == -1) {
                    exchange.sendResponseHeaders(200, responseBytes.length);
                }
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
