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
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.langchain4j.agent.api.Agent;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that the ToolExecutor created by LangChain4jAgentProducer for Camel route tools correctly propagates exceptions
 * and isolates tool execution state. This is the reproducer for CAMEL-23944: when a Camel route tool throws, the
 * exception must be rethrown by the ToolExecutor so that LangChain4j's ToolExecutionErrorHandler /
 * compensateOnToolErrors can fire.
 *
 * <p>
 * Before the fix, the ToolExecutor had two bugs:
 * <ol>
 * <li>Exceptions from tool routes were swallowed and returned as a plain string ("Tool execution failed"), making
 * LangChain4j believe the tool succeeded.</li>
 * <li>The live producer exchange was shared with tool execution, causing tool side-effects (headers, body, exceptions)
 * to leak into the calling exchange.</li>
 * </ol>
 */
class LangChain4jAgentToolExecutorErrorHandlingTest extends CamelTestSupport {

    private static final String TOOL_SUCCESS_RESULT = "{\"status\": \"ok\"}";

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

            if (toolProvider != null) {
                ToolProviderResult providerResult = toolProvider.provideTools(createToolProviderRequest());

                List<AiServiceTool> aiTools = providerResult.aiServiceTools();
                for (AiServiceTool aiTool : aiTools) {
                    ToolExecutionRequest request = ToolExecutionRequest.builder()
                            .name(aiTool.toolSpecification().name())
                            .arguments("{\"input\": \"test value\"}")
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
                from("ai-tool:failingTool?tags=failing"
                     + "&description=A tool that always fails"
                     + "&parameter.input=string")
                        .throwException(new RuntimeException("Simulated tool failure"));

                // Camel route tool that succeeds and sets a side-effect header
                from("ai-tool:successTool?tags=success"
                     + "&description=A tool that always succeeds"
                     + "&parameter.input=string")
                        .setHeader("toolSideEffect", constant("leaked"))
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
     *
     * <p>
     * Without exchange isolation, AiToolExecutor sets tool argument headers (like "input") and the tool route may set
     * additional side-effect headers (like "toolSideEffect") directly on the live producer exchange. This test verifies
     * that the calling exchange's original headers survive and that no tool-related headers leak through.
     */
    @Test
    void toolExecutionShouldNotLeakStateIntoCallingExchange() {
        Exchange result = template.request("direct:agent-with-success-tool", exchange -> {
            exchange.getMessage().setBody("original body");
            exchange.getMessage().setHeader("originalHeader", "originalValue");
        });

        // The response body comes from the agent (via result.content()),
        // not from the tool route
        assertThat(result.getMessage().getBody(String.class))
                .as("Response should come from the agent, not the tool route")
                .isEqualTo("agent response");

        // The caller's original header must survive tool execution
        assertThat(result.getMessage().getHeader("originalHeader"))
                .as("Original header should survive tool execution")
                .isEqualTo("originalValue");

        // Tool argument headers must NOT leak into the calling exchange.
        // AiToolExecutor sets each tool argument as an exchange header (e.g. "input");
        // with exchange isolation, these stay on the copy.
        assertThat(result.getMessage().getHeader("input"))
                .as("Tool argument header 'input' should not leak into calling exchange")
                .isNull();

        // Side-effect headers set by the tool route must NOT leak.
        // The success tool route sets "toolSideEffect" = "leaked"; with exchange
        // isolation, this stays on the copy.
        assertThat(result.getMessage().getHeader("toolSideEffect"))
                .as("Tool route side-effect header should not leak into calling exchange")
                .isNull();
    }

    /**
     * Verifies that a failing tool also does not leak state into the calling exchange. The exchange isolation must be
     * unconditional — it must happen for both success and failure paths.
     */
    @Test
    void failingToolExecutionShouldNotLeakStateIntoCallingExchange() {
        Exchange result = template.request("direct:agent-with-failing-tool", exchange -> {
            exchange.getMessage().setBody("original body");
            exchange.getMessage().setHeader("originalHeader", "originalValue");
        });

        // The response body comes from the agent (the capturing agent always
        // returns "agent response" regardless of tool success/failure)
        assertThat(result.getMessage().getBody(String.class))
                .as("Response should come from the agent even after tool failure")
                .isEqualTo("agent response");

        // The caller's original header must survive even when the tool fails
        assertThat(result.getMessage().getHeader("originalHeader"))
                .as("Original header should survive failing tool execution")
                .isEqualTo("originalValue");

        // Tool argument headers must NOT leak even on the failure path
        assertThat(result.getMessage().getHeader("input"))
                .as("Tool argument header 'input' should not leak on failure path")
                .isNull();
    }
}
