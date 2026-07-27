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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;

/**
 * Handles OpenAI Responses API ({@code /v1/responses}) mock requests.
 */
public class ResponsesRequestHandler {

    private final List<MockExpectation> expectations;
    private final ResponseBuilder responseBuilder;
    private final ObjectMapper objectMapper;

    public ResponsesRequestHandler(List<MockExpectation> expectations, ObjectMapper objectMapper) {
        this.expectations = expectations;
        this.responseBuilder = new ResponseBuilder(objectMapper);
        this.objectMapper = objectMapper;
    }

    public String handleRequest(HttpExchange exchange) throws Exception {
        String requestBody;
        try (InputStream is = exchange.getRequestBody()) {
            requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        JsonNode root = objectMapper.readTree(requestBody);
        RequestContext context = new RequestContext(root);
        String userInput = context.getResponsesInputText();
        if (userInput == null) {
            return responseBuilder.createErrorResponse(400, "No input text in Responses API request", exchange);
        }

        MockExpectation expectation = findExpectationByInput(userInput);
        if (expectation == null) {
            return responseBuilder.createErrorResponse(404, "No matching expectation for input: " + userInput, exchange);
        }
        if (expectation.getRequestAssertion() != null) {
            expectation.getRequestAssertion().accept(requestBody);
        }
        int promptTokens = expectation.getUsagePromptTokens() != null
                ? expectation.getUsagePromptTokens() : ResponseBuilder.DEFAULT_PROMPT_TOKENS;
        int completionTokens = expectation.getUsageCompletionTokens() != null
                ? expectation.getUsageCompletionTokens() : ResponseBuilder.DEFAULT_COMPLETION_TOKENS;
        return responseBuilder.createResponsesTextResponse(
                expectation.getExpectedResponse(), promptTokens, completionTokens);
    }

    private MockExpectation findExpectationByInput(String input) {
        for (MockExpectation expectation : expectations) {
            if (expectation.matches(input)) {
                return expectation;
            }
        }
        return null;
    }
}
