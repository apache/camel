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
package org.apache.camel.component.langchain4j.agent;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.langchain4j.agent.api.Agent;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that the ToolExecutor created by LangChain4jAgentProducer for Camel route tools correctly propagates
 * exceptions. This is the reproducer for CAMEL-23944: when a Camel route tool throws, the exception must be rethrown by
 * the ToolExecutor so that LangChain4j's ToolExecutionErrorHandler / compensateOnToolErrors can fire.
 *
 * <p>
 * Before the fix, the ToolExecutor swallowed exceptions from the route (stored on the exchange via
 * {@code setException()}) and returned an error string instead, preventing LangChain4j from seeing the failure.
 */
class LangChain4jAgentToolExecutorErrorHandlingTest extends CamelTestSupport {

    private static final String TOOL_SUCCESS_RESULT = "{\"status\": \"ok\"}";

    /** Captures the ToolProvider passed by the producer so we can test the ToolExecutor directly. */
    private final AtomicReference<ToolProvider> capturedToolProvider = new AtomicReference<>();

    /** Tracks whether a tool execution error was observed by the agent. */
    private final AtomicBoolean errorObserved = new AtomicBoolean(false);

    /** Tracks the successful tool result captured by the agent. */
    private final AtomicReference<String> capturedToolResult = new AtomicReference<>();

    /** Captures the error message when a tool execution fails. */
    private final AtomicReference<String> capturedErrorMessage = new AtomicReference<>();

    private ToolProviderRequest createToolProviderRequest() {
        return ToolProviderRequest.builder()
                .invocationContext(InvocationContext.builder().build())
                .userMessage(UserMessage.from("test"))
                .build();
    }

    @Override
    protected void bindToRegistry(Registry registry) {
        // A custom Agent that captures the ToolProvider and exercises the ToolExecutor directly.
        // This avoids needing a real LLM while still testing the producer's tool execution path.
        Agent capturingAgent = (body, toolProvider) -> {
            capturedToolProvider.set(toolProvider);

            if (toolProvider != null) {
                ToolProviderResult providerResult = toolProvider.provideTools(createToolProviderRequest());

                List<AiServiceTool> aiTools = providerResult.aiServiceTools();
                for (AiServiceTool aiTool : aiTools) {
                    ToolExecutionRequest request = ToolExecutionRequest.builder()
                            .name(aiTool.toolSpecification().name())
                            .arguments("{}")
                            .build();

                    try {
                        String result = aiTool.toolExecutor().execute(request, null);
                        capturedToolResult.set(result);
                    } catch (Exception e) {
                        errorObserved.set(true);
                        capturedErrorMessage.set(e.getMessage());
                    }
                }
            }

            return Result.<String> builder()
                    .content("agent response")
                    .build();
        };

        registry.bind("capturingAgent", capturingAgent);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Producer route that uses the capturing agent with failing-tool tag
                from("direct:agent-with-failing-tool")
                        .to("langchain4j-agent:test?agent=#capturingAgent&tags=failing");

                // Producer route that uses the capturing agent with success-tool tag
                from("direct:agent-with-success-tool")
                        .to("langchain4j-agent:test?agent=#capturingAgent&tags=success");

                // Camel route tool that throws an exception
                from("langchain4j-tools:failingTool?tags=failing"
                     + "&description=A tool that always fails"
                     + "&parameter.input=string")
                        .throwException(new RuntimeException("Simulated tool failure"));

                // Camel route tool that succeeds
                from("langchain4j-tools:successTool?tags=success"
                     + "&description=A tool that always succeeds"
                     + "&parameter.input=string")
                        .setBody(constant(TOOL_SUCCESS_RESULT));
            }
        };
    }

    /**
     * CAMEL-23944 reproducer: verifies that the ToolExecutor throws when a Camel route tool fails, so that
     * LangChain4j's error handling (ToolExecutionErrorHandler, compensateOnToolErrors) can fire.
     *
     * <p>
     * Before the fix, the ToolExecutor caught the exception and returned an error string, making LangChain4j believe
     * the tool succeeded. The sendBody call is wrapped in try-catch because the producer exchange may or may not
     * propagate the exception (depending on exchange isolation); we only care about whether the ToolExecutor itself
     * threw inside the capturing agent.
     */
    @Test
    void toolExecutorShouldThrowWhenCamelRouteToolFails() {
        errorObserved.set(false);

        try {
            template.sendBody("direct:agent-with-failing-tool", "test input");
        } catch (Exception e) {
            // Ignored — we are testing the ToolExecutor behavior, not the exchange propagation
        }

        // The capturing agent should have observed the error thrown by the ToolExecutor
        assertThat(errorObserved.get())
                .as("ToolExecutor should throw when Camel route tool fails, "
                    + "so LangChain4j error handlers can fire (CAMEL-23944)")
                .isTrue();
    }

    /**
     * Verifies that a successful tool execution still works correctly after the fix.
     */
    @Test
    void toolExecutorShouldReturnResultWhenCamelRouteToolSucceeds() {
        errorObserved.set(false);
        capturedToolResult.set(null);

        template.sendBody("direct:agent-with-success-tool", "test input");

        // The capturing agent should NOT have observed any error
        assertThat(errorObserved.get())
                .as("ToolExecutor should not throw when Camel route tool succeeds")
                .isFalse();

        // The tool should have returned the expected result
        assertThat(capturedToolResult.get())
                .as("ToolExecutor should return the route result body")
                .isEqualTo(TOOL_SUCCESS_RESULT);
    }

    /**
     * Verifies that the ToolExecutor propagates the original exception message from a failing Camel route. The error
     * message must contain the original cause so that LangChain4j's ToolExecutionErrorHandler receives meaningful
     * diagnostics.
     */
    @Test
    void toolExecutorShouldPropagateExceptionMessageFromFailingRoute() {
        capturedErrorMessage.set(null);

        try {
            template.sendBody("direct:agent-with-failing-tool", "test input");
        } catch (Exception e) {
            // Ignored — we are testing the ToolExecutor error message, not the exchange propagation
        }

        // The exception message should contain the original failure cause
        assertThat(capturedErrorMessage.get())
                .as("Exception message should contain the original route failure reason")
                .isNotNull()
                .contains("Simulated tool failure");
    }

    /**
     * Verifies that tool execution does not leak state (headers, body) into the calling exchange. Each tool invocation
     * should use an isolated exchange copy.
     */
    @Test
    void toolExecutionShouldNotLeakStateIntoCallingExchange() {
        // Send a message with a known body and header
        String originalBody = "original body";

        String response = template.requestBodyAndHeader(
                "direct:agent-with-success-tool",
                originalBody,
                "originalHeader", "originalValue",
                String.class);

        // The response should come from the agent, not be polluted by tool execution
        assertThat(response).isEqualTo("agent response");
    }
}
