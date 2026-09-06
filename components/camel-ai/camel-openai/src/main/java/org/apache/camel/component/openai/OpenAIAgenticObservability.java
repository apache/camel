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
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import org.apache.camel.Exchange;

/**
 * Collects per-iteration agentic traces and emits lightweight lifecycle events for EventNotifier listeners.
 */
final class OpenAIAgenticObservability {

    static final int MAX_TRACE_TEXT_LENGTH = 512;

    private final Exchange exchange;
    private final List<AgenticIterationTrace> trace = new ArrayList<>();

    OpenAIAgenticObservability(Exchange exchange) {
        this.exchange = exchange;
    }

    List<AgenticIterationTrace> trace() {
        return trace;
    }

    void onLoopStarted(int toolCount, int maxIterations) {
        notify(new OpenAIAgenticLoopStartedEvent(exchange, toolCount, maxIterations));
    }

    void onLoopCompleted(int iterationCount, OpenAIAgenticTokenTracker tokenTracker, String stopReason) {
        notify(new OpenAIAgenticLoopCompletedEvent(
                exchange, iterationCount, tokenTracker.getTotalTokens(), stopReason));
        exchange.setProperty(OpenAIConstants.AGENTIC_TRACE, List.copyOf(trace));
    }

    void onToolCallExecuted(int iteration, McpToolCallExecutor.ToolResult result) {
        notify(new OpenAIAgenticToolCallExecutedEvent(
                exchange, iteration, result.toolName(), result.durationMs(), result.success()));
    }

    AgenticIterationTrace recordIteration(
            int iteration,
            long iterationStartNanos,
            long promptTokens,
            long completionTokens,
            List<ChatCompletionMessageToolCall> requestedToolCalls,
            List<McpToolCallExecutor.ToolResult> toolResults) {
        List<AgenticToolCallTrace> toolTraces = new ArrayList<>(requestedToolCalls.size());
        for (int i = 0; i < requestedToolCalls.size(); i++) {
            ChatCompletionMessageToolCall toolCall = requestedToolCalls.get(i);
            McpToolCallExecutor.ToolResult result = toolResults.get(i);
            onToolCallExecuted(iteration, result);
            toolTraces.add(new AgenticToolCallTrace(
                    result.toolName(),
                    summarize(toolCall.asFunction().function().arguments()),
                    summarize(result.content()),
                    result.durationMs(),
                    result.success()));
        }
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - iterationStartNanos);
        AgenticIterationTrace iterationTrace
                = new AgenticIterationTrace(iteration, List.copyOf(toolTraces), promptTokens, completionTokens, durationMs);
        trace.add(iterationTrace);
        return iterationTrace;
    }

    AgenticIterationTrace recordFinalIteration(
            int iteration,
            long iterationStartNanos,
            long promptTokens,
            long completionTokens) {
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - iterationStartNanos);
        AgenticIterationTrace iterationTrace
                = new AgenticIterationTrace(iteration, List.of(), promptTokens, completionTokens, durationMs);
        trace.add(iterationTrace);
        return iterationTrace;
    }

    static String summarize(String value) {
        if (value == null) {
            return null;
        }
        if (value.length() <= MAX_TRACE_TEXT_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_TRACE_TEXT_LENGTH) + "...";
    }

    private void notify(org.apache.camel.spi.CamelEvent event) {
        try {
            exchange.getContext().getManagementStrategy().notify(event);
        } catch (Exception e) {
            // Event notifiers must not break the agentic loop
        }
    }
}
