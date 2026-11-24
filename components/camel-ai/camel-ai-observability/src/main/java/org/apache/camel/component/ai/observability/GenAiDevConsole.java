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
package org.apache.camel.component.ai.observability;

import java.util.List;
import java.util.Map;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonObject;

/**
 * The GenAI calls of the Camel AI components: the model calls per model, and the newest agent runs with their model
 * calls and tool calls in order. Recorded without a tracer while the dev console is enabled.
 */
@DevConsole(name = "genai", displayName = "GenAI",
            description = "The model calls, agent runs and tool calls of the Camel AI components")
public class GenAiDevConsole extends AbstractDevConsole {

    @Metadata(label = "query", description = "The number of the newest top-level calls to list", defaultValue = "20",
              javaType = "java.lang.Integer")
    public static final String LIMIT = "limit";

    @Metadata(label = "query",
              description = "The id of one call (an agent run, a model call or a tool call) to answer with its content",
              javaType = "java.lang.String")
    public static final String ID = "id";

    @Metadata(label = "query",
              description = "Whether to add the message content to the listed calls (only recorded at level full)",
              defaultValue = "false", javaType = "java.lang.Boolean")
    public static final String CONTENT = "content";

    static final int DEFAULT_LIMIT = 20;

    /** The answer: the totals and the newest top-level calls, or with {@code id} only that call. */
    public record Response(
            @Metadata(description = "Whether the calls are recorded: while the dev console is enabled") boolean recording,
            @Metadata(description = "The observability level: basic, detailed or full") String level,
            @Metadata(description = "The cap on each recorded text") int contentMaxLength,
            @Metadata(description = "The model calls since start") long modelCalls,
            @Metadata(description = "The agent runs since start") long agentRuns,
            @Metadata(description = "The tool calls since start") long toolCalls,
            @Metadata(description = "The calls that failed since start") long errors,
            @Metadata(description = "The input tokens of the model calls since start") long inputTokens,
            @Metadata(description = "The output tokens of the model calls since start") long outputTokens,
            @Metadata(description = "The input tokens read from the cache of the provider since start") long cachedInputTokens,
            @Metadata(description = "The model calls since start per provider and model") List<Model> models,
            @Metadata(description = "The newest top-level calls (agent runs, and model calls outside a run), newest first") List<Call> calls,
            @Metadata(description = "The top-level calls not listed: beyond the limit, or no longer kept") Long moreCalls,
            @Metadata(description = "The call asked for with the id option") Call call,
            @Metadata(description = "Why the call asked for is not there") String error) {
    }

    /** The model calls of one provider and model. */
    public record Model(
            @Metadata(description = "The provider, such as openai or ollama") String provider,
            @Metadata(description = "The model, the response model when known") String model,
            @Metadata(description = "The model calls") long calls,
            @Metadata(description = "The model calls that failed") long errors,
            @Metadata(description = "The input tokens") long inputTokens,
            @Metadata(description = "The output tokens") long outputTokens,
            @Metadata(description = "The input tokens read from the cache of the provider") long cachedInputTokens,
            @Metadata(description = "The time of the model calls in millis") long durationMs) {
    }

    /** A top-level call with its steps. */
    public record Call(
            @Metadata(description = "The id of the call") String id,
            @Metadata(description = "The GenAI operation: invoke_agent, chat, embeddings, ...") String operation,
            @Metadata(description = "The name of the call, such as chat gpt-4o") String name,
            @Metadata(description = "The agent of an agent run") String agentName,
            @Metadata(description = "The provider") String provider,
            @Metadata(description = "The model") String model,
            @Metadata(description = "The Camel component") String component,
            @Metadata(description = "The conversation (chat memory) id") String conversationId,
            @Metadata(description = "The route of the call") String routeId,
            @Metadata(description = "The exchange of the call") String exchangeId,
            @Metadata(description = "When the call started, in epoch millis") long timestamp,
            @Metadata(description = "Whether the call is still running") boolean running,
            @Metadata(description = "The time of the call in millis, when it ended") Long durationMs,
            @Metadata(description = "The input tokens (of its model calls, for an agent run)") Long inputTokens,
            @Metadata(description = "The output tokens (of its model calls, for an agent run)") Long outputTokens,
            @Metadata(description = "The input tokens read from the cache (of its model calls, for an agent run)") Long cachedInputTokens,
            @Metadata(description = "The finish reason, such as stop or tool_calls") String finishReason,
            @Metadata(description = "The exception type when the call failed") String error,
            @Metadata(description = "The model calls of an agent run") Integer modelCalls,
            @Metadata(description = "The tool calls of an agent run") Integer toolCalls,
            @Metadata(description = "The time of the model calls of an agent run in millis") Long modelMs,
            @Metadata(description = "The time of the tool calls of an agent run in millis") Long toolMs,
            @Metadata(description = "The calls made inside this one, in the order they started") List<Step> steps,
            @Metadata(description = "The steps not kept, beyond the cap per call") Integer droppedSteps,
            @Metadata(description = "The first user message, with content at level full") String question,
            @Metadata(description = "The last assistant message, with content at level full") String answer,
            @Metadata(description = "The content at level full: systemInstructions, toolDefinitions, inputMessages, outputMessages, toolArguments, toolResult") Map<String, Object> content) {
    }

    /** A model call or tool call made inside a top-level call. */
    public record Step(
            @Metadata(description = "The id of the call") String id,
            @Metadata(description = "The id of the call it was made in") String parentId,
            @Metadata(description = "The GenAI operation: chat, execute_tool, ...") String operation,
            @Metadata(description = "The name of the call") String name,
            @Metadata(description = "The tool of a tool call") String toolName,
            @Metadata(description = "The id the model gave to the tool call") String toolCallId,
            @Metadata(description = "The provider") String provider,
            @Metadata(description = "The model of a model call") String model,
            @Metadata(description = "When the call started, in epoch millis") long timestamp,
            @Metadata(description = "Whether the call is still running") boolean running,
            @Metadata(description = "The time of the call in millis, when it ended") Long durationMs,
            @Metadata(description = "The input tokens of a model call") Long inputTokens,
            @Metadata(description = "The output tokens of a model call") Long outputTokens,
            @Metadata(description = "The input tokens read from the cache") Long cachedInputTokens,
            @Metadata(description = "The finish reason, such as stop or tool_calls") String finishReason,
            @Metadata(description = "The exception type when the call failed") String error,
            @Metadata(description = "The content at level full") Map<String, Object> content) {
    }

    public GenAiDevConsole() {
        super("camel", "genai", "GenAI", "The model calls, agent runs and tool calls of the Camel AI components");
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        GenAiRecorder recorder = GenAiRecorder.get(getCamelContext());
        String id = optionString(options, ID);
        if (id != null) {
            JsonObject root = recorder.toJson(0, false);
            root.remove("calls");
            root.remove("moreCalls");
            JsonObject call = recorder.toJson(id);
            if (call != null) {
                root.put("call", call);
            } else {
                root.put("error", "No GenAI call with id " + id + ": it may no longer be kept (the newest "
                                  + GenAiRecorder.MAX_CALLS + " top-level calls are)");
            }
            return root;
        }
        int limit = Math.max(0, optionInt(options, LIMIT, DEFAULT_LIMIT));
        return recorder.toJson(limit, optionBoolean(options, CONTENT, false));
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        return GenAiConsoleText.format(doCallJson(options));
    }
}
