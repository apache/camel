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
import org.apache.camel.spi.CamelEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Collects per-iteration agentic traces and emits lightweight lifecycle events for EventNotifier listeners.
 */
final class OpenAIAgenticObservability {

    private static final Logger LOG = LoggerFactory.getLogger(OpenAIAgenticObservability.class);
    static final int MAX_TRACE_TEXT_LENGTH = 512;

    private final Exchange exchange;
    private final List<AgenticIterationTrace> trace = new ArrayList<>();
    private boolean loopStarted;
    private boolean loopCompleted;

    OpenAIAgenticObservability(Exchange exchange) {
        this.exchange = exchange;
    }

    List<AgenticIterationTrace> trace() {
        return trace;
    }

    void onLoopStarted(int toolCount, int maxIterations) {
        loopStarted = true;
        notify(new OpenAIAgenticLoopStartedEvent(exchange, toolCount, maxIterations));
    }

    void onLoopCompleted(int iterationCount, OpenAIAgenticTokenTracker tokenTracker, String stopReason) {
        loopCompleted = true;
        notify(new OpenAIAgenticLoopCompletedEvent(
                exchange, iterationCount, tokenTracker.getTotalTokens(), stopReason));
        publishTrace();
    }

    void onToolCallExecuted(int iteration, McpToolCallExecutor.ToolResult result) {
        notify(new OpenAIAgenticToolCallExecutedEvent(
                exchange, iteration, result.toolName(), result.durationMs(), result.success()));
    }

    void publishTrace() {
        exchange.setProperty(OpenAIConstants.AGENTIC_TRACE, List.copyOf(trace));
    }

    void finalizeObservability(OpenAIAgenticTokenTracker tokenTracker, int iterationCount, String stopReason) {
        if (loopStarted && !loopCompleted) {
            onLoopCompleted(iterationCount, tokenTracker, stopReason);
        } else if (!trace.isEmpty()) {
            publishTrace();
        }
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

    void recordFinalIteration(
            int iteration,
            long iterationStartNanos,
            long promptTokens,
            long completionTokens) {
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - iterationStartNanos);
        trace.add(new AgenticIterationTrace(iteration, List.of(), promptTokens, completionTokens, durationMs));
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

    private void notify(CamelEvent event) {
        if (exchange.getContext().getManagementStrategy().getEventNotifiers().isEmpty()) {
            return;
        }
        try {
            exchange.getContext().getManagementStrategy().notify(event);
        } catch (Exception e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Unable to notify agentic lifecycle event {}", event.getClass().getSimpleName(), e);
            }
        }
    }
}
