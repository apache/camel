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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolContext;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolExecutionException;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolRegistry;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.jboss.logging.Logger;

/**
 * MCP Tool for tracing AI-specific exchange flow in running Camel applications.
 * <p>
 * Combines data from the message history, top processor statistics, and route structure to surface AI-specific metrics:
 * token usage, model IDs, guardrail outcomes, completion reasons, and per-component latency for AI components (Bedrock,
 * LangChain4j, Docling, Textract, OpenAI).
 */
@ApplicationScoped
public class AiTraceTools {

    private static final Logger LOG = Logger.getLogger(AiTraceTools.class);

    private static final String NAME_OR_PID_DESC
            = "Name or PID of the Camel process. Leave empty to auto-detect (works when exactly one Camel process is running)";

    private static final Set<String> AI_COMPONENT_SCHEMES = Set.of(
            "aws-bedrock", "aws-bedrock-agent", "aws-bedrock-agent-runtime",
            "aws2-textract", "docling",
            "langchain4j-chat", "langchain4j-embeddings", "langchain4j-embeddingstore",
            "langchain4j-tools", "langchain4j-agent", "langchain4j-web-search",
            "openai", "kserve", "tensorflow-serving", "djl",
            "huggingface", "ai-tool");

    private static final Set<String> AI_HEADER_PREFIXES = Set.of(
            "CamelAwsBedrock", "CamelAwsTextract",
            "CamelLangChain4j", "CamelLangchain4j",
            "CamelDocling", "CamelOpenAI",
            "CamelKServe", "CamelDjl", "CamelTensorFlowServing", "CamelHuggingFace");

    @Inject
    RuntimeService runtimeService;

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Trace AI-specific exchange flow in a running Camel application. "
                        + "Shows token usage, model IDs, guardrail outcomes, completion reasons, "
                        + "streaming chunk counts, and per-component latency for AI components "
                        + "(Bedrock, LangChain4j, Docling, Textract, OpenAI, KServe, DJL, "
                        + "TensorFlow Serving, HuggingFace). "
                        + "Combines message history with processor statistics filtered to AI steps.")
    public AiTraceResult camel_runtime_ai_trace(
            @ToolArg(description = NAME_OR_PID_DESC) String nameOrPid,
            @ToolArg(description = "Route ID filter (exact match or * for all, default: *)") String routeFilter) {

        RuntimeService.ProcessInfo p = runtimeService.findSingleProcess(nameOrPid);
        ToolContext ctx = new ToolContext();
        ctx.selectProcess(p.pid());

        String filter = routeFilter != null && !routeFilter.isBlank() ? routeFilter : "*";

        try {
            JsonObject history = executeRegistryTool("get_history", ctx, Map.of());
            JsonObject topProcessors = executeRegistryTool("get_top_processors", ctx, Map.of());
            JsonObject routeStructure
                    = executeRegistryTool("get_route_structure", ctx, Map.of("routeId", filter));

            List<AiComponentInfo> aiComponents = extractAiComponents(routeStructure);
            List<AiHeaderInfo> aiHeaders = extractAiHeaders(history);
            List<AiProcessorStat> aiProcessorStats = extractAiProcessorStats(topProcessors);
            AiTraceSummary summary = buildSummary(aiComponents, aiHeaders, aiProcessorStats);

            return new AiTraceResult(
                    p.name(), p.pid(),
                    aiComponents.isEmpty() ? null : aiComponents,
                    aiHeaders.isEmpty() ? null : aiHeaders,
                    aiProcessorStats.isEmpty() ? null : aiProcessorStats,
                    summary);
        } catch (ToolCallException e) {
            throw e;
        } catch (Exception e) {
            throw new ToolCallException(
                    "Failed to collect AI trace data: " + e.getMessage(), null);
        }
    }

    private JsonObject executeRegistryTool(String toolName, ToolContext ctx, Map<String, String> args) {
        try {
            Object result = ToolRegistry.execute(toolName, ctx, args);
            return RuntimeTools.toJsonObject(result);
        } catch (ToolExecutionException e) {
            LOG.debugf("AI trace: %s query failed: %s", toolName, e.getMessage());
            return new JsonObject();
        }
    }

    List<AiComponentInfo> extractAiComponents(JsonObject routeStructure) {
        List<AiComponentInfo> result = new ArrayList<>();
        collectAiComponentsRecursive(routeStructure, result);
        return result;
    }

    private void collectAiComponentsRecursive(Object node, List<AiComponentInfo> result) {
        if (node instanceof JsonObject jo) {
            String uri = jo.getString("uri");
            if (uri != null) {
                String scheme = extractScheme(uri);
                if (scheme != null && AI_COMPONENT_SCHEMES.contains(scheme)) {
                    result.add(new AiComponentInfo(
                            scheme, uri,
                            jo.getString("routeId"),
                            jo.getString("nodeId")));
                }
            }
            for (Object value : jo.values()) {
                collectAiComponentsRecursive(value, result);
            }
        } else if (node instanceof JsonArray ja) {
            for (Object item : ja) {
                collectAiComponentsRecursive(item, result);
            }
        }
    }

    List<AiHeaderInfo> extractAiHeaders(JsonObject history) {
        List<AiHeaderInfo> result = new ArrayList<>();
        collectAiHeadersRecursive(history, result);
        return result;
    }

    private void collectAiHeadersRecursive(Object node, List<AiHeaderInfo> result) {
        if (node instanceof JsonObject jo) {
            for (Map.Entry<String, Object> entry : jo.entrySet()) {
                String key = entry.getKey();
                if (isAiHeader(key) && entry.getValue() != null) {
                    result.add(new AiHeaderInfo(
                            key, truncateValue(entry.getValue().toString(), 200),
                            categorizeHeader(key)));
                }
                collectAiHeadersRecursive(entry.getValue(), result);
            }
        } else if (node instanceof JsonArray ja) {
            for (Object item : ja) {
                collectAiHeadersRecursive(item, result);
            }
        }
    }

    List<AiProcessorStat> extractAiProcessorStats(JsonObject topProcessors) {
        List<AiProcessorStat> result = new ArrayList<>();
        collectAiProcessorStatsRecursive(topProcessors, result);
        return result;
    }

    private void collectAiProcessorStatsRecursive(Object node, List<AiProcessorStat> result) {
        if (node instanceof JsonObject jo) {
            String id = jo.getString("id");
            String processorUri = jo.getString("uri");
            if (processorUri != null) {
                String scheme = extractScheme(processorUri);
                if (scheme != null && AI_COMPONENT_SCHEMES.contains(scheme)) {
                    result.add(new AiProcessorStat(
                            id != null ? id : "unknown",
                            processorUri,
                            jo.getString("total"),
                            jo.getString("mean"),
                            jo.getString("max"),
                            jo.getString("min"),
                            jo.getString("last")));
                }
            }
            for (Object value : jo.values()) {
                collectAiProcessorStatsRecursive(value, result);
            }
        } else if (node instanceof JsonArray ja) {
            for (Object item : ja) {
                collectAiProcessorStatsRecursive(item, result);
            }
        }
    }

    private AiTraceSummary buildSummary(
            List<AiComponentInfo> components,
            List<AiHeaderInfo> headers,
            List<AiProcessorStat> stats) {

        String tokenUsage = headers.stream()
                .filter(h -> "token_usage".equals(h.category()))
                .map(AiHeaderInfo::value)
                .findFirst().orElse(null);
        String modelId = headers.stream()
                .filter(h -> "model".equals(h.category()))
                .map(AiHeaderInfo::value)
                .findFirst().orElse(null);
        String completionReason = headers.stream()
                .filter(h -> "completion".equals(h.category()))
                .map(AiHeaderInfo::value)
                .findFirst().orElse(null);
        String guardrailAction = headers.stream()
                .filter(h -> "guardrail".equals(h.category()))
                .map(AiHeaderInfo::value)
                .findFirst().orElse(null);
        String chunkCount = headers.stream()
                .filter(h -> "streaming".equals(h.category()))
                .map(AiHeaderInfo::value)
                .findFirst().orElse(null);

        return new AiTraceSummary(
                components.size(), stats.size(),
                tokenUsage, modelId, completionReason,
                guardrailAction, chunkCount,
                components.isEmpty() ? "No AI components detected in running routes" : null);
    }

    static String extractScheme(String uri) {
        if (uri == null) {
            return null;
        }
        int idx = uri.indexOf(':');
        return idx > 0 ? uri.substring(0, idx) : null;
    }

    private boolean isAiHeader(String key) {
        for (String prefix : AI_HEADER_PREFIXES) {
            if (key.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private String categorizeHeader(String header) {
        if (header.contains("TokenCount") || header.contains("Usage")) {
            return "token_usage";
        }
        if (header.contains("Model")) {
            return "model";
        }
        if (header.contains("Guardrail")) {
            return "guardrail";
        }
        if (header.contains("CompletionReason") || header.contains("StopReason")) {
            return "completion";
        }
        if (header.contains("ChunkCount") || header.contains("Stream")) {
            return "streaming";
        }
        if (header.contains("Operation")) {
            return "operation";
        }
        return "other";
    }

    private static String truncateValue(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    // ---- Result records ----

    record AiTraceResult(
            String processName,
            long pid,
            List<AiComponentInfo> aiComponents,
            List<AiHeaderInfo> aiHeaders,
            List<AiProcessorStat> aiProcessorStats,
            AiTraceSummary summary) {
    }

    record AiComponentInfo(
            String scheme,
            String uri,
            String routeId,
            String nodeId) {
    }

    record AiHeaderInfo(
            String header,
            String value,
            String category) {
    }

    record AiProcessorStat(
            String processorId,
            String uri,
            String totalExchanges,
            String meanMs,
            String maxMs,
            String minMs,
            String lastMs) {
    }

    record AiTraceSummary(
            int aiComponentCount,
            int aiProcessorStatCount,
            String tokenUsage,
            String modelId,
            String completionReason,
            String guardrailAction,
            String chunkCount,
            String note) {
    }
}
