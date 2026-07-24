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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;

/**
 * MCP Tool for estimating API costs of a Camel route based on its component usage.
 * <p>
 * Particularly useful for AI pipelines where Bedrock, Textract, and S3 calls have per-unit pricing. Provides
 * per-execution cost estimates and projections at a given throughput.
 * <p>
 * Cost data is approximate and based on published AWS pricing as of 2025. Actual costs depend on model, region, and
 * volume tiers.
 */
@ApplicationScoped
public class RouteCostEstimateTools {

    private static final Pattern YAML_SCHEME_PATTERN = Pattern.compile(
            "(?:uri:\\s*[\"']?|from:[ \\t]+[\"']?|to:[ \\t]+[\"']?|toD:[ \\t]+[\"']?)"
                                                                       + "([a-zA-Z][a-zA-Z0-9+.-]*):(?://)?",
            Pattern.MULTILINE);

    private static final Pattern XML_SCHEME_PATTERN = Pattern.compile(
            "(?:<from|<to|<toD)\\s+uri=[\"']([a-zA-Z][a-zA-Z0-9+.-]*):",
            Pattern.CASE_INSENSITIVE);

    private static final Map<String, ComponentCostProfile> COST_PROFILES = buildCostProfiles();

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Estimate API costs for a Camel route based on its component usage. "
                        + "Analyzes a YAML or XML route definition and identifies components with "
                        + "pay-per-use pricing. Currently covers: Bedrock (runtime and agent-runtime), "
                        + "Textract, S3, SQS, SNS, Kinesis, OpenAI, LangChain4j (chat and embeddings), "
                        + "and Docling (free/self-hosted). "
                        + "Returns per-execution cost estimate and monthly projection at a given throughput. "
                        + "Cost data is approximate based on published AWS pricing (2025-Q2).")
    public CostEstimateResult camel_route_cost_estimate(
            @ToolArg(description = "The Camel route definition (YAML or XML)") String route,
            @ToolArg(description = "Expected messages per hour for cost projection (default: 100)") Integer messagesPerHour,
            @ToolArg(description = "Average document pages per message for Textract/Docling (default: 5)") Integer avgPages,
            @ToolArg(description = "Average LLM input tokens per request (default: 1000)") Integer avgInputTokens,
            @ToolArg(description = "Average LLM output tokens per request (default: 500)") Integer avgOutputTokens) {

        if (route == null || route.isBlank()) {
            throw new ToolCallException("Route content is required", null);
        }

        try {
            return doEstimate(route, messagesPerHour, avgPages, avgInputTokens, avgOutputTokens);
        } catch (ToolCallException e) {
            throw e;
        } catch (Throwable e) {
            throw new ToolCallException(
                    "Failed to estimate route cost (" + e.getClass().getName() + "): " + e.getMessage(), null);
        }
    }

    private CostEstimateResult doEstimate(
            String route, Integer messagesPerHour, Integer avgPages,
            Integer avgInputTokens, Integer avgOutputTokens) {

        int throughput = messagesPerHour != null && messagesPerHour > 0 ? messagesPerHour : 100;
        int pages = avgPages != null && avgPages > 0 ? avgPages : 5;
        int inTokens = avgInputTokens != null && avgInputTokens > 0 ? avgInputTokens : 1000;
        int outTokens = avgOutputTokens != null && avgOutputTokens > 0 ? avgOutputTokens : 500;

        List<String> detectedSchemes = extractSchemes(route);

        List<ComponentCostBreakdown> breakdown = new ArrayList<>();
        double totalPerExecution = 0;

        for (String scheme : detectedSchemes) {
            ComponentCostProfile profile = COST_PROFILES.get(scheme);
            if (profile == null) {
                continue;
            }

            double costPerExec = profile.estimateCostPerExecution(pages, inTokens, outTokens);
            totalPerExecution += costPerExec;
            breakdown.add(new ComponentCostBreakdown(
                    scheme, profile.displayName(), profile.pricingModel(),
                    costPerExec, profile.pricingNote()));
        }

        breakdown.sort(Comparator.comparingDouble(ComponentCostBreakdown::estimatedCostPerExecution).reversed());

        double hourly = totalPerExecution * throughput;
        double daily = hourly * 24;
        double monthly = daily * 30;

        String mostExpensive = breakdown.isEmpty() ? null : breakdown.get(0).scheme();

        List<String> optimizationTips = buildOptimizationTips(detectedSchemes, breakdown);

        CostProjection projection = new CostProjection(
                throughput,
                formatCost(hourly), formatCost(daily), formatCost(monthly));

        CostSummary summary = new CostSummary(
                formatCost(totalPerExecution),
                breakdown.size(),
                detectedSchemes.size(),
                mostExpensive,
                breakdown.isEmpty() ? "No pay-per-use components detected in this route" : null);

        return new CostEstimateResult(
                breakdown.isEmpty() ? null : breakdown,
                projection, summary,
                optimizationTips.isEmpty() ? null : optimizationTips,
                "Cost estimates are approximate based on published AWS pricing (us-east-1). "
                                                                      + "Actual costs vary by region, volume tier, and model.",
                "2025-Q2");
    }

    List<String> extractSchemes(String route) {
        List<String> schemes = new ArrayList<>();
        addSchemeMatches(schemes, YAML_SCHEME_PATTERN, route);
        addSchemeMatches(schemes, XML_SCHEME_PATTERN, route);
        return schemes;
    }

    private void addSchemeMatches(List<String> schemes, Pattern pattern, String route) {
        Matcher m = pattern.matcher(route);
        while (m.find()) {
            String scheme = m.group(1);
            if (!schemes.contains(scheme)) {
                schemes.add(scheme);
            }
        }
    }

    private List<String> buildOptimizationTips(List<String> schemes, List<ComponentCostBreakdown> breakdown) {
        List<String> tips = new ArrayList<>();

        if (schemes.contains("aws-bedrock")) {
            tips.add("Consider using a smaller/cheaper Bedrock model for simpler tasks "
                     + "(e.g., Nova Lite instead of Claude for classification)");
            tips.add("Use streaming to reduce perceived latency without affecting token costs");
        }
        if (schemes.contains("aws2-textract") && schemes.contains("docling")) {
            tips.add("Using both Textract and Docling — consider using only Docling (free, open-source) "
                     + "for text extraction and reserving Textract for table/form extraction");
        }
        if (schemes.contains("aws2-s3")) {
            tips.add("S3 GET costs are minimal but add up at scale — consider caching frequently accessed documents");
        }
        if (breakdown.size() > 1) {
            tips.add("Most expensive component: " + breakdown.get(0).displayName()
                     + " — focus optimization here for maximum savings");
        }
        return tips;
    }

    private static String formatCost(double cost) {
        if (cost < 0.01) {
            return String.format("$%.6f", cost);
        }
        return String.format("$%.4f", cost);
    }

    private static Map<String, ComponentCostProfile> buildCostProfiles() {
        Map<String, ComponentCostProfile> profiles = new LinkedHashMap<>();

        profiles.put("aws-bedrock", new ComponentCostProfile(
                "AWS Bedrock (model-dependent, estimated as Claude Sonnet 4)", "per-token",
                "Estimate uses Claude Sonnet 4 pricing ($3/$15 per MTok). Actual cost depends on model: "
                                                                                            + "Nova Lite ~$0.06/$0.24, Haiku ~$0.25/$1.25, Opus ~$15/$75") {
            @Override
            double estimateCostPerExecution(int pages, int inputTokens, int outputTokens) {
                return (inputTokens * 3.0 / 1_000_000) + (outputTokens * 15.0 / 1_000_000);
            }
        });

        profiles.put("aws-bedrock-agent-runtime", new ComponentCostProfile(
                "AWS Bedrock Agent Runtime (RAG/Flows)", "per-session",
                "RetrieveAndGenerate: model invocation cost + ~$0.02/1000 retrieval units. "
                                                                        + "InvokeFlow: depends on flow steps") {
            @Override
            double estimateCostPerExecution(int pages, int inputTokens, int outputTokens) {
                return (inputTokens * 3.0 / 1_000_000) + (outputTokens * 15.0 / 1_000_000) + 0.02 / 1000;
            }
        });

        profiles.put("aws2-textract", new ComponentCostProfile(
                "AWS Textract", "per-page",
                "DetectDocumentText: ~$1.50/1000 pages, AnalyzeDocument: ~$15/1000 pages") {
            @Override
            double estimateCostPerExecution(int pages, int inputTokens, int outputTokens) {
                return pages * 1.50 / 1000;
            }
        });

        profiles.put("docling", new ComponentCostProfile(
                "Docling (self-hosted)", "free",
                "Open-source, self-hosted. Cost is only infrastructure (compute/memory)") {
            @Override
            double estimateCostPerExecution(int pages, int inputTokens, int outputTokens) {
                return 0;
            }
        });

        profiles.put("aws2-s3", new ComponentCostProfile(
                "AWS S3", "per-request",
                "GET: ~$0.40/1M requests, PUT: ~$5/1M requests") {
            @Override
            double estimateCostPerExecution(int pages, int inputTokens, int outputTokens) {
                return 0.40 / 1_000_000;
            }
        });

        profiles.put("aws2-sqs", new ComponentCostProfile(
                "AWS SQS", "per-request",
                "~$0.40 per 1M requests (Standard)") {
            @Override
            double estimateCostPerExecution(int pages, int inputTokens, int outputTokens) {
                return 0.40 / 1_000_000;
            }
        });

        profiles.put("aws2-sns", new ComponentCostProfile(
                "AWS SNS", "per-publish",
                "~$0.50 per 1M publishes") {
            @Override
            double estimateCostPerExecution(int pages, int inputTokens, int outputTokens) {
                return 0.50 / 1_000_000;
            }
        });

        profiles.put("aws2-kinesis", new ComponentCostProfile(
                "AWS Kinesis", "per-shard-hour + per-PUT",
                "~$0.015/shard/hour + $0.014/1M PUT units") {
            @Override
            double estimateCostPerExecution(int pages, int inputTokens, int outputTokens) {
                return 0.014 / 1_000_000;
            }
        });

        profiles.put("langchain4j-chat", new ComponentCostProfile(
                "LangChain4j Chat (model-dependent)", "per-token",
                "Cost depends on the underlying model provider. Estimated as Claude Sonnet 4; "
                                                                   + "free with Ollama/local models") {
            @Override
            double estimateCostPerExecution(int pages, int inputTokens, int outputTokens) {
                return (inputTokens * 3.0 / 1_000_000) + (outputTokens * 15.0 / 1_000_000);
            }
        });

        profiles.put("langchain4j-embeddings", new ComponentCostProfile(
                "LangChain4j Embeddings (model-dependent)", "per-token",
                "Estimated as Titan Embed (~$0.02/1M tokens); free with Ollama/local models") {
            @Override
            double estimateCostPerExecution(int pages, int inputTokens, int outputTokens) {
                return inputTokens * 0.02 / 1_000_000;
            }
        });

        profiles.put("openai", new ComponentCostProfile(
                "OpenAI API", "per-token",
                "GPT-4o: ~$2.50/MTok input, ~$10/MTok output") {
            @Override
            double estimateCostPerExecution(int pages, int inputTokens, int outputTokens) {
                return (inputTokens * 2.50 / 1_000_000) + (outputTokens * 10.0 / 1_000_000);
            }
        });

        return profiles;
    }

    // ---- Cost profile base ----

    abstract static class ComponentCostProfile {
        private final String displayName;
        private final String pricingModel;
        private final String pricingNote;

        ComponentCostProfile(String displayName, String pricingModel, String pricingNote) {
            this.displayName = displayName;
            this.pricingModel = pricingModel;
            this.pricingNote = pricingNote;
        }

        String displayName() {
            return displayName;
        }

        String pricingModel() {
            return pricingModel;
        }

        String pricingNote() {
            return pricingNote;
        }

        abstract double estimateCostPerExecution(int pages, int inputTokens, int outputTokens);
    }

    // ---- Result records ----

    public record CostEstimateResult(
            List<ComponentCostBreakdown> costBreakdown,
            CostProjection projection,
            CostSummary summary,
            List<String> optimizationTips,
            String disclaimer,
            String pricingDataAsOf) {
    }

    public record ComponentCostBreakdown(
            String scheme,
            String displayName,
            String pricingModel,
            double estimatedCostPerExecution,
            String pricingNote) {
    }

    public record CostProjection(
            int messagesPerHour,
            String hourly,
            String daily,
            String monthly) {
    }

    public record CostSummary(
            String estimatedCostPerExecution,
            int pricedComponentCount,
            int totalComponentCount,
            String mostExpensiveComponent,
            String note) {
    }
}
