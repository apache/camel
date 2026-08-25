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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.camel.Exchange;
import org.apache.camel.component.ai.tool.AiToolExecutor;
import org.apache.camel.component.ai.tool.AiToolResult;
import org.apache.camel.component.ai.tool.AiToolSpec;
import org.apache.camel.support.service.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Executes a batch of MCP tool calls returned by the model in a single response.
 *
 * <p>
 * Shared by the agentic loop in {@link OpenAIProducer} and by the manual tool loop in
 * {@link OpenAIToolExecutionProducer} so that hallucinated tool name handling, argument parsing, error strategies and
 * {@code returnDirect} detection cannot drift between the two.
 *
 * <p>
 * When {@code parallelToolExecution=true} the calls in a batch are dispatched concurrently, since tool calls emitted in
 * the same assistant message are independent by design. Results are always returned in the original tool call order,
 * because the OpenAI API pairs each {@code tool} message with its {@code tool_call_id}.
 */
class McpToolCallExecutor extends ServiceSupport {

    private static final Logger LOG = LoggerFactory.getLogger(McpToolCallExecutor.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final OpenAIEndpoint endpoint;

    private ExecutorService executorService;

    McpToolCallExecutor(OpenAIEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    /**
     * The outcome of a single tool call.
     *
     * @param toolCallId   the id the model assigned to the call, used to pair the result back to the request
     * @param toolName     the name of the tool that was called
     * @param content      the textual result to feed back to the model
     * @param returnDirect whether the call succeeded and the tool is annotated with {@code returnDirect}
     */
    record ToolResult(String toolCallId, String toolName, String content, boolean returnDirect) {
    }

    @Override
    protected void doStart() throws Exception {
        if (endpoint.getConfiguration().isParallelToolExecution()) {
            // Deliberately goes through Camel's ExecutorServiceManager rather than creating a raw
            // ThreadPoolExecutor: the pool then honours the configured thread pool profile, is exposed
            // over JMX, is shut down with the CamelContext, and automatically becomes a thread-per-task
            // virtual thread executor when virtual threads are enabled.
            executorService = endpoint.getCamelContext().getExecutorServiceManager()
                    .newDefaultThreadPool(this, "OpenAIMcpToolCall");
        }
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        if (executorService != null) {
            endpoint.getCamelContext().getExecutorServiceManager().shutdownNow(executorService);
            executorService = null;
        }
        super.doStop();
    }

    /**
     * Executes every tool call in the batch and returns the results in the original order.
     *
     * @param  toolCalls the tool calls requested by the model
     * @return           one result per tool call, in the same order
     * @throws Exception when a tool call fails and the configured strategy is to fail the exchange
     */
    List<ToolResult> execute(List<ChatCompletionMessageToolCall> toolCalls) throws Exception {
        if (toolCalls.isEmpty()) {
            return List.of();
        }

        // Snapshot the (immutable) tool state once so the whole batch sees a consistent view even if an
        // MCP server publishes a tool list change or a sibling call triggers a reconnect while it runs
        McpToolState toolState = endpoint.getMcpToolState();

        // A single tool call gains nothing from a thread hand-off
        if (executorService == null || toolCalls.size() == 1) {
            List<ToolResult> results = new ArrayList<>(toolCalls.size());
            for (ChatCompletionMessageToolCall toolCall : toolCalls) {
                results.add(executeOne(toolCall, toolState));
            }
            return results;
        }

        return executeParallel(toolCalls, toolState);
    }

    private List<ToolResult> executeParallel(List<ChatCompletionMessageToolCall> toolCalls, McpToolState toolState)
            throws Exception {
        LOG.debug("Executing {} tool call(s) in parallel", toolCalls.size());

        // Carry the caller's MDC onto the worker threads so tool call logs stay correlated with the exchange
        Map<String, String> mdc = MDC.getCopyOfContextMap();

        List<Future<ToolResult>> futures = new ArrayList<>(toolCalls.size());
        for (ChatCompletionMessageToolCall toolCall : toolCalls) {
            futures.add(executorService.submit(withMdc(mdc, () -> executeOne(toolCall, toolState))));
        }

        long timeout = endpoint.getConfiguration().getParallelToolTimeout();
        long deadline = timeout > 0 ? System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout) : 0;

        ToolResult[] results = new ToolResult[toolCalls.size()];
        Exception failure = null;

        for (int i = 0; i < futures.size(); i++) {
            ChatCompletionMessageToolCall toolCall = toolCalls.get(i);
            try {
                results[i] = deadline > 0
                        ? futures.get(i).get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)
                        : futures.get(i).get();
            } catch (TimeoutException e) {
                futures.get(i).cancel(true);
                // Do not abandon the siblings: keep collecting so that every dispatched call is accounted for
                Exception timedOut = timeoutFailure(toolCall, timeout);
                if (timedOut != null) {
                    failure = failure != null ? failure : timedOut;
                } else {
                    results[i] = errorResult(toolCall, "Error: tool execution timed out after " + timeout + " ms");
                }
            } catch (ExecutionException e) {
                // executeOne only throws when the configured strategy is to fail the exchange
                failure = failure != null ? failure : asException(e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                futures.forEach(f -> f.cancel(true));
                throw e;
            }
        }

        if (failure != null) {
            throw failure;
        }
        return Arrays.asList(results);
    }

    /**
     * Builds the exception for a timed out call, or returns {@code null} when the configured strategy is to report the
     * timeout back to the model instead of failing the exchange.
     */
    private Exception timeoutFailure(ChatCompletionMessageToolCall toolCall, long timeout) {
        String toolName = toolCall.asFunction().function().name();
        if (endpoint.getConfiguration().getToolExecutionErrorStrategy() == ToolExecutionErrorStrategy.FAIL_EXCHANGE) {
            return new TimeoutException(
                    "MCP tool '" + toolName + "' did not complete within parallelToolTimeout of " + timeout + " ms");
        }
        LOG.warn("MCP tool '{}' timed out after {} ms, sending the timeout back to the model", toolName, timeout);
        return null;
    }

    private static Exception asException(Throwable cause) {
        return cause instanceof Exception e ? e : new IllegalStateException(cause);
    }

    private static Callable<ToolResult> withMdc(Map<String, String> mdc, Callable<ToolResult> task) {
        if (mdc == null || mdc.isEmpty()) {
            return task;
        }
        return () -> {
            MDC.setContextMap(mdc);
            try {
                return task.call();
            } finally {
                MDC.clear();
            }
        };
    }

    private ToolResult executeOne(ChatCompletionMessageToolCall toolCall, McpToolState toolState) throws Exception {
        OpenAIConfiguration config = endpoint.getConfiguration();
        String toolName = toolCall.asFunction().function().name();
        String argsJson = toolCall.asFunction().function().arguments();

        AiToolSpec routeSpec = toolState.routeTools().get(toolName);
        if (routeSpec != null) {
            return executeRouteTool(toolCall, routeSpec, toolState, config);
        }

        McpSyncClient mcpClient = toolState.toolClientMap().get(toolName);
        if (mcpClient == null) {
            if (config.getHallucinatedToolNameStrategy() == HallucinatedToolNameStrategy.FAIL_EXCHANGE) {
                throw new IllegalStateException("Tool '" + toolName + "' not found in any configured MCP server");
            }
            // repromptModel: send a corrective tool result listing available tools
            String available = String.join(", ", toolState.knownToolNames());
            LOG.warn("Hallucinated tool name '{}', sending corrective result to model", toolName);
            return errorResult(toolCall,
                    "Error: tool '" + toolName + "' does not exist. Available tools: " + available);
        }

        LOG.debug("Executing MCP tool '{}' with args: {}", toolName, argsJson);

        try {
            Map<String, Object> argsMap = OBJECT_MAPPER.readValue(argsJson, Map.class);
            McpSchema.CallToolResult toolResult = endpoint.callTool(mcpClient, toolName, argsMap);

            if (Boolean.TRUE.equals(toolResult.isError())) {
                String content = "Error: " + extractTextContent(toolResult.content());
                LOG.warn("MCP tool '{}' returned error: {}", toolName, content);
                return errorResult(toolCall, content);
            }

            String content = extractTextContent(toolResult.content());
            LOG.debug("Tool '{}' result: {}", toolName, content);
            return new ToolResult(
                    toolCall.asFunction().id(), toolName, content, toolState.returnDirectTools().contains(toolName));
        } catch (JsonProcessingException e) {
            if (config.getToolExecutionErrorStrategy() == ToolExecutionErrorStrategy.FAIL_EXCHANGE) {
                throw e;
            }
            LOG.warn("Invalid tool arguments for '{}': {}", toolName, argsJson, e);
            return errorResult(toolCall, "Error: invalid tool arguments: " + e.getMessage());
        } catch (Exception e) {
            if (config.getToolExecutionErrorStrategy() == ToolExecutionErrorStrategy.FAIL_EXCHANGE) {
                throw e;
            }
            LOG.warn("MCP tool '{}' execution failed: {}", toolName, e.getMessage(), e);
            return errorResult(toolCall, "Error: Tool execution failed: " + e.getMessage());
        }
    }

    private ToolResult executeRouteTool(
            ChatCompletionMessageToolCall toolCall,
            AiToolSpec spec,
            McpToolState toolState,
            OpenAIConfiguration config)
            throws Exception {
        String toolName = toolCall.asFunction().function().name();
        String argsJson = toolCall.asFunction().function().arguments();

        LOG.debug("Executing route tool '{}' with args: {}", toolName, argsJson);

        try {
            Map<String, Object> argsMap = OBJECT_MAPPER.readValue(argsJson, Map.class);
            Exchange toolExchange = spec.getConsumer().createExchange(false);
            try {
                AiToolResult result = AiToolExecutor.execute(spec, argsMap, toolExchange);
                if (result instanceof AiToolResult.Success success) {
                    LOG.debug("Route tool '{}' result: {}", toolName, success.value());
                    return new ToolResult(
                            toolCall.asFunction().id(), toolName, success.value(),
                            toolState.returnDirectTools().contains(toolName));
                } else if (result instanceof AiToolResult.ArgumentError error) {
                    LOG.warn("Route tool '{}' argument error: {}", toolName, error.message());
                    return errorResult(toolCall, "Error: invalid tool arguments: " + error.message());
                } else {
                    AiToolResult.ExecutionError error = (AiToolResult.ExecutionError) result;
                    if (config.getToolExecutionErrorStrategy() == ToolExecutionErrorStrategy.FAIL_EXCHANGE) {
                        if (error.cause() != null) {
                            throw error.cause();
                        }
                        throw new IllegalStateException(error.message());
                    }
                    LOG.warn("Route tool '{}' execution failed: {}", toolName, error.message(), error.cause());
                    return errorResult(toolCall, "Error: Tool execution failed: " + error.message());
                }
            } finally {
                spec.getConsumer().releaseExchange(toolExchange, false);
            }
        } catch (JsonProcessingException e) {
            if (config.getToolExecutionErrorStrategy() == ToolExecutionErrorStrategy.FAIL_EXCHANGE) {
                throw e;
            }
            LOG.warn("Invalid tool arguments for route tool '{}': {}", toolName, argsJson, e);
            return errorResult(toolCall, "Error: invalid tool arguments: " + e.getMessage());
        }
    }

    private static ToolResult errorResult(ChatCompletionMessageToolCall toolCall, String content) {
        return new ToolResult(
                toolCall.asFunction().id(), toolCall.asFunction().function().name(), content, false);
    }

    private static String extractTextContent(List<McpSchema.Content> contents) {
        if (contents == null || contents.isEmpty()) {
            return "";
        }
        return contents.stream()
                .filter(McpSchema.TextContent.class::isInstance)
                .map(McpSchema.TextContent.class::cast)
                .map(McpSchema.TextContent::text)
                .collect(Collectors.joining());
    }
}
