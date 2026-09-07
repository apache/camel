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
package org.apache.camel.component.openai;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for CAMEL-23078: the shared MCP tool call batch executor, covering both the sequential and the parallel
 * ({@code parallelToolExecution=true}) execution paths.
 */
class McpToolCallExecutorTest {

    private static final long AWAIT_SECONDS = 10;

    private final DefaultCamelContext context = new DefaultCamelContext();

    private McpToolCallExecutor executor;

    @BeforeEach
    void startContext() {
        context.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (executor != null) {
            executor.stop();
        }
        context.stop();
    }

    // ------------------------------------------------------------------
    // Ordering
    // ------------------------------------------------------------------

    @Test
    void parallelResultsKeepToolCallOrder() throws Exception {
        // Every tool blocks until all of them have started, so the batch can only complete when it runs
        // concurrently. The results must still come back in the order the model requested them.
        CountDownLatch rendezvous = new CountDownLatch(3);

        OpenAIEndpoint endpoint = newEndpoint(true, 0, Map.of(
                "slow_a", rendezvousClient(rendezvous, "A"),
                "slow_b", rendezvousClient(rendezvous, "B"),
                "slow_c", rendezvousClient(rendezvous, "C")),
                Set.of());
        executor = startExecutor(endpoint);

        List<McpToolCallExecutor.ToolResult> results
                = executor.execute(List.of(toolCall("id-a", "slow_a"), toolCall("id-b", "slow_b"),
                        toolCall("id-c", "slow_c")));

        assertThat(results).extracting(McpToolCallExecutor.ToolResult::toolCallId)
                .containsExactly("id-a", "id-b", "id-c");
        assertThat(results).extracting(McpToolCallExecutor.ToolResult::content)
                .containsExactly("A", "B", "C");
    }

    @Test
    void sequentialExecutionProducesTheSameResults() throws Exception {
        OpenAIEndpoint endpoint = newEndpoint(false, 0, Map.of(
                "tool_a", staticClient("A"),
                "tool_b", staticClient("B")),
                Set.of());
        executor = startExecutor(endpoint);

        List<McpToolCallExecutor.ToolResult> results
                = executor.execute(List.of(toolCall("id-a", "tool_a"), toolCall("id-b", "tool_b")));

        assertThat(results).extracting(McpToolCallExecutor.ToolResult::toolCallId)
                .containsExactly("id-a", "id-b");
        assertThat(results).extracting(McpToolCallExecutor.ToolResult::content).containsExactly("A", "B");
    }

    @Test
    void emptyBatchReturnsNoResults() throws Exception {
        OpenAIEndpoint endpoint = newEndpoint(true, 0, Map.of("tool_a", staticClient("A")), Set.of());
        executor = startExecutor(endpoint);

        assertThat(executor.execute(List.of())).isEmpty();
    }

    // ------------------------------------------------------------------
    // returnDirect
    // ------------------------------------------------------------------

    @Test
    void returnDirectIsReportedPerToolCall() throws Exception {
        OpenAIEndpoint endpoint = newEndpoint(true, 0, Map.of(
                "direct_tool", staticClient("A"),
                "normal_tool", staticClient("B")),
                Set.of("direct_tool"));
        executor = startExecutor(endpoint);

        List<McpToolCallExecutor.ToolResult> results
                = executor.execute(List.of(toolCall("id-a", "direct_tool"), toolCall("id-b", "normal_tool")));

        assertThat(results).extracting(McpToolCallExecutor.ToolResult::returnDirect).containsExactly(true, false);
    }

    @Test
    void returnDirectIsNotReportedForAToolThatReturnedAnError() throws Exception {
        McpSyncClient failing = mock(McpSyncClient.class);
        when(failing.callTool(any(McpSchema.CallToolRequest.class))).thenReturn(
                McpSchema.CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(null, "boom", null)))
                        .isError(true)
                        .build());

        OpenAIEndpoint endpoint
                = newEndpoint(true, 0, Map.of("direct_tool", failing), Set.of("direct_tool"));
        executor = startExecutor(endpoint);

        List<McpToolCallExecutor.ToolResult> results = executor.execute(List.of(toolCall("id-a", "direct_tool")));

        assertThat(results).singleElement()
                .satisfies(r -> {
                    assertThat(r.returnDirect()).isFalse();
                    assertThat(r.content()).isEqualTo("Error: boom");
                });
    }

    // ------------------------------------------------------------------
    // Error handling
    // ------------------------------------------------------------------

    @Test
    void parallelFailureLetsSiblingToolsCompleteBeforeFailingTheExchange() throws Exception {
        AtomicInteger siblingCalls = new AtomicInteger();
        McpSyncClient sibling = mock(McpSyncClient.class);
        when(sibling.callTool(any(McpSchema.CallToolRequest.class))).thenAnswer(invocation -> {
            siblingCalls.incrementAndGet();
            return textResult("ok");
        });

        McpSyncClient failing = mock(McpSyncClient.class);
        when(failing.callTool(any(McpSchema.CallToolRequest.class)))
                .thenThrow(new IllegalStateException("tool blew up"));

        // failing tool first, so a mid-loop abort would skip the sibling entirely
        OpenAIEndpoint endpoint = newEndpoint(true, 0, Map.of(
                "failing_tool", failing,
                "sibling_tool", sibling),
                Set.of());
        executor = startExecutor(endpoint);

        assertThatThrownBy(
                () -> executor.execute(List.of(toolCall("id-a", "failing_tool"), toolCall("id-b", "sibling_tool"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("tool blew up");

        assertThat(siblingCalls).hasValue(1);
    }

    @Test
    void toolFailureIsSentBackToTheModelWhenRepromptingIsConfigured() throws Exception {
        McpSyncClient failing = mock(McpSyncClient.class);
        when(failing.callTool(any(McpSchema.CallToolRequest.class)))
                .thenThrow(new IllegalStateException("tool blew up"));

        OpenAIEndpoint endpoint = newEndpoint(true, 0, Map.of(
                "failing_tool", failing,
                "ok_tool", staticClient("fine")),
                Set.of());
        endpoint.getConfiguration().setToolExecutionErrorStrategy(ToolExecutionErrorStrategy.REPROMPT_MODEL);
        executor = startExecutor(endpoint);

        List<McpToolCallExecutor.ToolResult> results
                = executor.execute(List.of(toolCall("id-a", "failing_tool"), toolCall("id-b", "ok_tool")));

        assertThat(results).extracting(McpToolCallExecutor.ToolResult::content)
                .containsExactly("Error: Tool execution failed: tool blew up", "fine");
    }

    @Test
    void hallucinatedToolNameFailsTheExchangeByDefault() throws Exception {
        OpenAIEndpoint endpoint = newEndpoint(true, 0, Map.of("known_tool", staticClient("A")), Set.of());
        executor = startExecutor(endpoint);

        assertThatThrownBy(
                () -> executor.execute(List.of(toolCall("id-a", "known_tool"), toolCall("id-b", "made_up_tool"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("made_up_tool");
    }

    @Test
    void hallucinatedToolNameIsCorrectedWhenRepromptingIsConfigured() throws Exception {
        OpenAIEndpoint endpoint = newEndpoint(true, 0, Map.of("known_tool", staticClient("A")), Set.of());
        endpoint.getConfiguration().setHallucinatedToolNameStrategy(HallucinatedToolNameStrategy.REPROMPT_MODEL);
        executor = startExecutor(endpoint);

        List<McpToolCallExecutor.ToolResult> results
                = executor.execute(List.of(toolCall("id-a", "made_up_tool"), toolCall("id-b", "known_tool")));

        assertThat(results.get(0).content()).contains("made_up_tool", "known_tool");
        assertThat(results.get(0).returnDirect()).isFalse();
        assertThat(results.get(1).content()).isEqualTo("A");
    }

    // ------------------------------------------------------------------
    // Timeout
    // ------------------------------------------------------------------

    @Test
    void batchTimeoutFailsTheExchangeButLetsFastToolsFinish() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger fastCalls = new AtomicInteger();
        try {
            McpSyncClient fast = mock(McpSyncClient.class);
            when(fast.callTool(any(McpSchema.CallToolRequest.class))).thenAnswer(invocation -> {
                fastCalls.incrementAndGet();
                return textResult("fast");
            });

            OpenAIEndpoint endpoint = newEndpoint(true, 200, Map.of(
                    "blocking_tool", blockingClient(release),
                    "fast_tool", fast),
                    Set.of());
            executor = startExecutor(endpoint);

            assertThatThrownBy(
                    () -> executor.execute(List.of(toolCall("id-a", "blocking_tool"), toolCall("id-b", "fast_tool"))))
                    .isInstanceOf(TimeoutException.class)
                    .hasMessageContaining("blocking_tool")
                    .hasMessageContaining("parallelToolTimeout");

            // the slow tool must not have cancelled its sibling
            assertThat(fastCalls).hasValue(1);
        } finally {
            release.countDown();
        }
    }

    @Test
    void batchTimeoutIsSentBackToTheModelWhenRepromptingIsConfigured() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        try {
            OpenAIEndpoint endpoint = newEndpoint(true, 200, Map.of(
                    "blocking_tool", blockingClient(release)),
                    Set.of());
            endpoint.getConfiguration().setToolExecutionErrorStrategy(ToolExecutionErrorStrategy.REPROMPT_MODEL);
            executor = startExecutor(endpoint);

            // two calls, so the batch is dispatched in parallel rather than run inline
            List<McpToolCallExecutor.ToolResult> results
                    = executor.execute(List.of(toolCall("id-a", "blocking_tool"), toolCall("id-b", "blocking_tool")));

            assertThat(results).extracting(McpToolCallExecutor.ToolResult::content)
                    .allSatisfy(content -> assertThat(content).contains("timed out after 200 ms"));
        } finally {
            release.countDown();
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private McpToolCallExecutor startExecutor(OpenAIEndpoint endpoint) throws Exception {
        McpToolCallExecutor answer = new McpToolCallExecutor(endpoint);
        answer.start();
        return answer;
    }

    private OpenAIEndpoint newEndpoint(
            boolean parallel, long timeout, Map<String, McpSyncClient> toolClients, Set<String> returnDirectTools) {
        OpenAIConfiguration configuration = new OpenAIConfiguration();
        configuration.setParallelToolExecution(parallel);
        configuration.setParallelToolTimeout(timeout);

        OpenAIComponent component = new OpenAIComponent();
        component.setCamelContext(context);

        OpenAIEndpoint endpoint = new OpenAIEndpoint("openai:chat-completion", component, configuration);
        endpoint.setCamelContext(context);
        endpoint.setMcpToolState(new McpToolState(
                List.of(), toolClients, Map.of(), returnDirectTools, Map.of()));
        return endpoint;
    }

    private static ChatCompletionMessageToolCall toolCall(String id, String toolName) {
        return ChatCompletionMessageToolCall.ofFunction(
                ChatCompletionMessageFunctionToolCall.builder()
                        .id(id)
                        .function(ChatCompletionMessageFunctionToolCall.Function.builder()
                                .name(toolName)
                                .arguments("{}")
                                .build())
                        .build());
    }

    private static McpSyncClient staticClient(String resultText) {
        McpSyncClient client = mock(McpSyncClient.class);
        when(client.callTool(any(McpSchema.CallToolRequest.class))).thenReturn(textResult(resultText));
        return client;
    }

    /**
     * A client that only returns once every tool in the batch has entered its call, so the batch can complete only when
     * the calls are dispatched concurrently.
     */
    private static McpSyncClient rendezvousClient(CountDownLatch rendezvous, String resultText) {
        McpSyncClient client = mock(McpSyncClient.class);
        when(client.callTool(any(McpSchema.CallToolRequest.class))).thenAnswer(invocation -> {
            rendezvous.countDown();
            if (!rendezvous.await(AWAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("tool calls were not dispatched concurrently");
            }
            return textResult(resultText);
        });
        return client;
    }

    private static McpSyncClient blockingClient(CountDownLatch release) {
        McpSyncClient client = mock(McpSyncClient.class);
        when(client.callTool(any(McpSchema.CallToolRequest.class))).thenAnswer(invocation -> {
            release.await(AWAIT_SECONDS, TimeUnit.SECONDS);
            return textResult("released");
        });
        return client;
    }

    private static McpSchema.CallToolResult textResult(String text) {
        return McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent(null, text, null)))
                .isError(false)
                .build();
    }
}
