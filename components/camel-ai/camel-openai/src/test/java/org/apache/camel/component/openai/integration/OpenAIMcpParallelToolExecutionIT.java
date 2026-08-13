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
package org.apache.camel.component.openai.integration;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.openai.OpenAIConstants;
import org.apache.camel.test.infra.mcp.everything.services.McpEverythingService;
import org.apache.camel.test.infra.mcp.everything.services.McpEverythingServiceFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for CAMEL-23078: the agentic loop with {@code parallelToolExecution=true} against a real MCP server
 * and a real LLM backend.
 *
 * <p>
 * How many tool calls the model emits per response is up to the model, and a batch of one is executed inline rather
 * than dispatched to the pool, so reaching the parallel path at all depends on the backend in use. With
 * {@code qwen3.5:2b-mlx} the multi-tool prompt below reliably produces two-call batches and does exercise it; smaller
 * or older models often serialise the same prompt into one tool call per iteration instead. These tests are therefore
 * written to assert that enabling the option yields the same correct outcome as the sequential default whatever the
 * model chooses to do, rather than asserting a particular batch size, which would be flaky. The deterministic proof
 * that a batch really is dispatched concurrently lives in {@code McpToolCallExecutorTest} and
 * {@code OpenAIParallelToolExecutionTest}, which rendezvous the tool calls on a latch that can only be released if they
 * run at the same time.
 *
 * <p>
 * Uses the MCP Everything Server (Streamable HTTP) and Ollama as the LLM backend. See {@code test_execution.md} for how
 * to run these against a local Ollama.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Requires too much network resources")
public class OpenAIMcpParallelToolExecutionIT extends OpenAITestSupport {

    private static final String MCP_PROTOCOL_VERSIONS = "2024-11-05,2025-03-26,2025-06-18";

    /**
     * Models call tools one at a time unless told otherwise. This gives a capable model the chance to emit a real
     * batch, which is what reaches the parallel dispatch path.
     */
    private static final String PARALLEL_SYSTEM_MESSAGE
            = "You must call all the tools you need at once, in a single response, in parallel.";

    private static final String MULTI_TOOL_PROMPT
            = "Add 3 and 4 using the add tool, and echo the word hello using the echo tool. Call both tools now, together.";

    @RegisterExtension
    static McpEverythingService MCP_EVERYTHING = McpEverythingServiceFactory.createSingletonService();

    @Override
    protected RouteBuilder createRouteBuilder() {
        String mcpConfig = "&mcpServer.everything.transportType=streamableHttp"
                           + "&mcpServer.everything.url=" + MCP_EVERYTHING.url()
                           + "&mcpProtocolVersions=" + MCP_PROTOCOL_VERSIONS;

        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:parallel")
                        .toF("openai:chat-completion?parallelToolExecution=true%s", mcpConfig);

                from("direct:parallel-timeout")
                        .toF("openai:chat-completion?parallelToolExecution=true&parallelToolTimeout=60000%s", mcpConfig);

                from("direct:sequential")
                        .toF("openai:chat-completion?%s", mcpConfig.substring(1));
            }
        };
    }

    @Test
    void parallelToolExecutionCompletesTheAgenticLoop() {
        Exchange result = template.request("direct:parallel", this::multiToolRequest);

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(String.class)).contains("7").containsIgnoringCase("hello");
        assertThat(toolCalls(result)).contains("add", "echo");
    }

    @Test
    void parallelToolExecutionHonorsABatchTimeout() {
        // a generous timeout must not disturb tool calls that complete well within it
        Exchange result = template.request("direct:parallel-timeout", this::multiToolRequest);

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(String.class)).contains("7");
        assertThat(toolCalls(result)).contains("add", "echo");
    }

    @Test
    void parallelAndSequentialExecutionAgreeOnTheOutcome() {
        Exchange sequential = template.request("direct:sequential",
                e -> e.getIn().setBody("Use the add tool to add 15 and 27. What is the result?"));
        Exchange parallel = template.request("direct:parallel",
                e -> e.getIn().setBody("Use the add tool to add 15 and 27. What is the result?"));

        assertThat(sequential.getException()).isNull();
        assertThat(parallel.getException()).isNull();
        assertThat(sequential.getMessage().getBody(String.class)).contains("42");
        assertThat(parallel.getMessage().getBody(String.class)).contains("42");
        assertThat(toolCalls(parallel)).isEqualTo(toolCalls(sequential));
    }

    private void multiToolRequest(Exchange exchange) {
        exchange.getIn().setHeader(OpenAIConstants.SYSTEM_MESSAGE, PARALLEL_SYSTEM_MESSAGE);
        exchange.getIn().setBody(MULTI_TOOL_PROMPT);
    }

    @SuppressWarnings("unchecked")
    private static List<String> toolCalls(Exchange exchange) {
        return exchange.getMessage().getHeader(OpenAIConstants.MCP_TOOL_CALLS, List.class);
    }
}
