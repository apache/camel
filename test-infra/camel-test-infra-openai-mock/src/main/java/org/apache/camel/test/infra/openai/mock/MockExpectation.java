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

import java.util.function.BiFunction;
import java.util.function.Consumer;

import com.sun.net.httpserver.HttpExchange;

/**
 * Represents a mock expectation for a specific user input. Contains the expected input, tool execution sequence, and
 * response configuration.
 */
public class MockExpectation {
    private final String expectedInput;
    private final ToolExecutionSequence toolSequence;
    private String expectedResponse;
    private String toolContentResponse;
    private BiFunction<HttpExchange, String, String> customResponseFunction;
    private Consumer<String> requestAssertion;

    public MockExpectation(String expectedInput) {
        this.expectedInput = expectedInput;
        this.toolSequence = new ToolExecutionSequence();
    }

    // Getters
    public String getExpectedInput() {
        return expectedInput;
    }

    public String getExpectedResponse() {
        return expectedResponse;
    }

    public String getToolContentResponse() {
        return toolContentResponse;
    }

    public BiFunction<HttpExchange, String, String> getCustomResponseFunction() {
        return customResponseFunction;
    }

    public Consumer<String> getRequestAssertion() {
        return requestAssertion;
    }

    public ToolExecutionSequence getToolSequence() {
        return toolSequence;
    }

    // Setters
    public void setExpectedResponse(String expectedResponse) {
        this.expectedResponse = expectedResponse;
    }

    public void setCustomResponseFunction(BiFunction<HttpExchange, String, String> customResponseFunction) {
        this.customResponseFunction = customResponseFunction;
    }

    public void setRequestAssertion(Consumer<String> requestAssertion) {
        this.requestAssertion = requestAssertion;
    }

    public void setToolContentResponse(String toolContentResponse) {
        this.toolContentResponse = toolContentResponse;
    }

    // Tool sequence delegation methods
    public void addToolExecutionStep(ToolExecutionStep step) {
        toolSequence.addStep(step);
    }

    public ToolExecutionStep getCurrentToolStep() {
        return toolSequence.getCurrentStep();
    }

    public void advanceToNextToolStep() {
        toolSequence.advanceToNextStep();
    }

    public boolean hasMoreToolSteps() {
        return toolSequence.hasMoreSteps();
    }

    public boolean isInToolSequence() {
        return toolSequence.isInProgress();
    }

    public void resetToolSequence() {
        toolSequence.reset();
    }

    // Response type determination
    public MockResponseType getResponseType() {
        if (customResponseFunction != null) {
            return MockResponseType.CUSTOM_FUNCTION;
        }

        if (!toolSequence.isEmpty() && toolSequence.hasCurrentStep()) {
            return MockResponseType.TOOL_CALLS;
        }

        return MockResponseType.SIMPLE_TEXT;
    }

    public boolean matches(String input) {
        return expectedInput.equals(input);
    }

    @Override
    public String toString() {
        return String.format(
                "MockExpectation{input='%s', response='%s', toolSteps=%d}",
                expectedInput, expectedResponse, toolSequence.getTotalSteps());
    }
}
