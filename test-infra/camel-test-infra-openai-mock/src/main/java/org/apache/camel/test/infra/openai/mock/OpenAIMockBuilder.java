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

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import com.sun.net.httpserver.HttpExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fluent builder for creating OpenAI mock expectations.
 */
public class OpenAIMockBuilder {
    private static final Logger log = LoggerFactory.getLogger(OpenAIMockBuilder.class);

    private final OpenAIMock mock;
    private final List<MockExpectation> expectations;
    private final List<EmbeddingExpectation> embeddingExpectations;
    private MockExpectation currentExpectation;
    private EmbeddingExpectation currentEmbeddingExpectation;

    public OpenAIMockBuilder(OpenAIMock mock, List<MockExpectation> expectations,
                             List<EmbeddingExpectation> embeddingExpectations) {
        this.mock = mock;
        this.expectations = expectations;
        this.embeddingExpectations = embeddingExpectations;
    }

    public OpenAIMockBuilder when(String expectedInput) {
        log.debug("Setting up expectation for input: {}", expectedInput);
        currentExpectation = new MockExpectation(expectedInput);
        return this;
    }

    public OpenAIMockBuilder replyWith(String expectedResponse) {
        validateCurrentExpectation("replyWith()");
        log.debug("Setting expected response: {}", expectedResponse);
        currentExpectation.setExpectedResponse(expectedResponse);
        return this;
    }

    public OpenAIMockBuilder replyWithToolContent(String customMessage) {
        validateCurrentExpectation("replyWithToolContent()");
        log.debug("Setting tool content response with custom message: {}", customMessage);
        currentExpectation.setToolContentResponse(customMessage);
        return this;
    }

    public OpenAIMockBuilder invokeTool(String toolName) {
        validateCurrentExpectation("invokeTool()");
        log.debug("Adding new tool execution step with tool: {}", toolName);

        ToolExecutionStep newStep = new ToolExecutionStep();
        newStep.addToolCall(new ToolCallDefinition(toolName));
        currentExpectation.addToolExecutionStep(newStep);

        return this;
    }

    public OpenAIMockBuilder andInvokeTool(String toolName) {
        validateCurrentExpectation("andInvokeTool()");
        validateHasToolSteps("andInvokeTool()");

        log.debug("Adding parallel tool to current step: {}", toolName);
        ToolExecutionStep currentStep = currentExpectation.getCurrentToolStep();
        currentStep.addToolCall(new ToolCallDefinition(toolName));

        return this;
    }

    public OpenAIMockBuilder withParam(String key, Object value) {
        validateCurrentExpectation("withParam()");
        validateHasToolSteps("withParam()");

        ToolExecutionStep currentStep = currentExpectation.getCurrentToolStep();
        if (currentStep.isEmpty()) {
            throw new IllegalStateException("No tool calls in current step to add parameters to");
        }

        ToolCallDefinition lastTool = currentStep.getLastToolCall();
        log.debug("Adding parameter {} = {} to tool: {}", key, value, lastTool.getName());
        lastTool.addArgument(key, value);

        return this;
    }

    public OpenAIMockBuilder thenRespondWith(BiFunction<HttpExchange, String, String> responseFunction) {
        validateCurrentExpectation("thenRespondWith()");
        log.debug("Setting custom response function");
        currentExpectation.setCustomResponseFunction(responseFunction);
        return this;
    }

    public OpenAIMockBuilder assertRequest(Consumer<String> requestAssertion) {
        validateCurrentExpectation("assertRequest()");
        log.debug("Setting request assertion");
        currentExpectation.setRequestAssertion(requestAssertion);
        return this;
    }

    public OpenAIMockBuilder andThenInvokeTool(String toolName) {
        validateCurrentExpectation("andThenInvokeTool()");
        validateHasToolSteps("andThenInvokeTool()");

        log.debug("Creating new sequential step with tool: {}", toolName);
        ToolExecutionStep newStep = new ToolExecutionStep();
        newStep.addToolCall(new ToolCallDefinition(toolName));
        currentExpectation.addToolExecutionStep(newStep);
        currentExpectation.advanceToNextToolStep();

        return this;
    }

    // Embedding API methods

    public OpenAIMockBuilder whenEmbedding(String expectedInput) {
        log.debug("Setting up embedding expectation for input: {}", expectedInput);
        currentEmbeddingExpectation = new EmbeddingExpectation(expectedInput);
        return this;
    }

    public OpenAIMockBuilder replyWithEmbedding(float[] vector) {
        validateCurrentEmbeddingExpectation("replyWithEmbedding()");
        log.debug("Setting explicit embedding vector of size: {}", vector.length);
        List<Float> floatList = new ArrayList<>(vector.length);
        for (float f : vector) {
            floatList.add(f);
        }
        currentEmbeddingExpectation.setEmbeddingVector(floatList);
        return this;
    }

    public OpenAIMockBuilder replyWithEmbedding(List<Float> vector) {
        validateCurrentEmbeddingExpectation("replyWithEmbedding()");
        log.debug("Setting explicit embedding vector (List) of size: {}", vector.size());
        currentEmbeddingExpectation.setEmbeddingVector(vector);
        return this;
    }

    public OpenAIMockBuilder replyWithEmbedding(int size) {
        validateCurrentEmbeddingExpectation("replyWithEmbedding()");
        log.debug("Setting auto-generated embedding of size: {}", size);
        currentEmbeddingExpectation.setEmbeddingSize(size);
        return this;
    }

    public OpenAIMockBuilder end() {
        if (currentExpectation != null) {
            log.debug("Finalizing expectation for input: {}", currentExpectation.getExpectedInput());
            expectations.add(currentExpectation);
            currentExpectation = null;
        } else if (currentEmbeddingExpectation != null) {
            log.debug("Finalizing embedding expectation for input: {}", currentEmbeddingExpectation.getExpectedInput());
            embeddingExpectations.add(currentEmbeddingExpectation);
            currentEmbeddingExpectation = null;
        } else {
            throw new IllegalStateException("Call when() or whenEmbedding() before end()");
        }
        return this;
    }

    public OpenAIMock build() {
        if (currentExpectation != null) {
            log.debug("Auto-finalizing current expectation during build");
            expectations.add(currentExpectation);
            currentExpectation = null;
        }
        if (currentEmbeddingExpectation != null) {
            log.debug("Auto-finalizing current embedding expectation during build");
            embeddingExpectations.add(currentEmbeddingExpectation);
            currentEmbeddingExpectation = null;
        }
        log.info("Built OpenAIMock with {} chat expectations and {} embedding expectations",
                expectations.size(), embeddingExpectations.size());
        return mock;
    }

    private void validateCurrentExpectation(String methodName) {
        if (currentExpectation == null) {
            throw new IllegalStateException("Call when() before " + methodName);
        }
    }

    private void validateCurrentEmbeddingExpectation(String methodName) {
        if (currentEmbeddingExpectation == null) {
            throw new IllegalStateException("Call whenEmbedding() before " + methodName);
        }
    }

    private void validateHasToolSteps(String methodName) {
        if (currentExpectation.getToolSequence().isEmpty()) {
            throw new IllegalStateException("Call invokeTool() before " + methodName);
        }
    }
}
