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
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles incoming requests and matches them to appropriate mock expectations.
 */
public class RequestHandler {
    private static final Logger LOG = LoggerFactory.getLogger(RequestHandler.class);

    private final List<MockExpectation> expectations;
    private final ResponseBuilder responseBuilder;
    private final ObjectMapper objectMapper;

    public RequestHandler(List<MockExpectation> expectations, ObjectMapper objectMapper) {
        this.expectations = expectations;
        this.objectMapper = objectMapper;
        this.responseBuilder = new ResponseBuilder(objectMapper);
    }

    public String handleRequest(HttpExchange exchange) throws IOException {
        try {
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            LOG.debug("Processing request: {}", requestBody);

            JsonNode rootNode = objectMapper.readTree(requestBody);
            RequestContext context = new RequestContext(rootNode);

            MockExpectation expectation = findExpectationByInput(context.getLastUserMessage());

            if (context.hasToolRole() && !expectation.getToolSequence().isEmpty()) {
                return handleToolSequenceResponse(context, exchange);
            } else {
                return handleUserInput(requestBody, context, exchange);
            }
        } catch (Exception e) {
            String errorMessage = "Error processing request: " + e.getMessage();
            LOG.error(errorMessage);
            return responseBuilder.createErrorResponse(500, errorMessage, exchange);
        }
    }

    private String handleToolSequenceResponse(RequestContext context, HttpExchange exchange) throws Exception {
        String originalInput = context.getLastUserMessage();
        if (originalInput == null) {
            LOG.warn("Could not find original user input in message history");
            return responseBuilder.createErrorResponse(400, "Original user input not found", exchange);
        }

        MockExpectation expectation = findExpectationByInput(originalInput);
        if (expectation == null) {
            LOG.warn("No matching expectation found for tool sequence with input: {}", originalInput);
            return responseBuilder.createErrorResponse(
                    404, "No matching expectation found for tool sequence", exchange);
        }

        expectation.advanceToNextToolStep();

        if (expectation.hasMoreToolSteps()) {
            LOG.debug("Executing next tool step for expectation: {}", originalInput);
            String result = createToolCallResponse(expectation);
            return result;
        } else {
            LOG.debug("Tool sequence completed for expectation: {}", originalInput);
            return responseBuilder.createFinalToolResponse(
                    context.getMessagesNode(), expectation.getExpectedResponse(), expectation.getToolContentResponse());
        }
    }

    private String handleUserInput(String requestBody, RequestContext context, HttpExchange exchange) throws Exception {
        String userInput = context.getLastUserMessage();
        if (userInput == null) {
            LOG.warn("User message content not found in request");
            throw new IllegalArgumentException("User message content not found in request");
        }

        MockExpectation expectation = findExpectationByInput(userInput);

        expectation.resetToolSequence();

        // Execute request assertion if present
        if (expectation.getRequestAssertion() != null) {
            expectation.getRequestAssertion().accept(requestBody);
        }

        return createResponse(expectation, userInput, exchange);
    }

    private String createResponse(MockExpectation expectation, String userInput, HttpExchange exchange)
            throws Exception {
        MockResponseType responseType = expectation.getResponseType();

        switch (responseType) {
            case CUSTOM_FUNCTION:
                LOG.debug("Using custom response function");
                return expectation.getCustomResponseFunction().apply(exchange, userInput);

            case TOOL_CALLS:
                return createToolCallResponse(expectation);

            case SIMPLE_TEXT:
            default:
                LOG.debug("Creating simple text response");
                return responseBuilder.createSimpleTextResponse(expectation.getExpectedResponse());
        }
    }

    private String createToolCallResponse(MockExpectation expectation) throws Exception {
        ToolExecutionStep currentStep = expectation.getCurrentToolStep();
        return responseBuilder.createToolCallResponse(expectation.getExpectedResponse(), currentStep.getToolCalls());
    }

    private MockExpectation findExpectationByInput(String input) {
        return expectations.stream()
                .filter(expectation -> expectation.matches(input))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        String.format("No matching mock expectation found for input: %s", input)));
    }
}
