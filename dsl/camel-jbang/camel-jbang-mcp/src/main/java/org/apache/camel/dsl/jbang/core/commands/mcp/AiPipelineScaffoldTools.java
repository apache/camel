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

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;

/**
 * MCP Tool for generating AI document-processing pipeline scaffolds.
 * <p>
 * Generates runnable YAML DSL routes that combine document processing (Docling, Textract) with AI services (Bedrock)
 * for common use cases: summarization, extraction, RAG, and classification.
 */
@ApplicationScoped
public class AiPipelineScaffoldTools {

    private static final String DEFAULT_MODEL = "us.anthropic.claude-sonnet-4-20250514-v1:0";
    private static final String DEFAULT_REGION = "us-east-1";

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Generate a YAML DSL Camel route for an AI document-processing pipeline. "
                        + "Combines document processors (Docling for open-source/on-prem, Textract for AWS-managed) "
                        + "with Bedrock LLM services for summarization, extraction, RAG, or classification. "
                        + "Returns a runnable route and matching application.properties template.")
    public ScaffoldResult camel_ai_pipeline_scaffold(
            @ToolArg(description = "Pipeline type: summarization, extraction, rag, or classification") String pipelineType,
            @ToolArg(description = "Document processor: docling (open-source, default), textract (AWS), "
                                   + "or combined (Docling for text + Textract for tables)") String documentProcessor,
            @ToolArg(description = "Document source: file (local path, default), s3 (AWS S3 bucket), "
                                   + "or url (HTTP/HTTPS URL)") String documentSource,
            @ToolArg(description = "Bedrock model ID (default: Claude Sonnet 4). "
                                   + "Examples: us.anthropic.claude-sonnet-4-20250514-v1:0, "
                                   + "us.anthropic.claude-opus-4-20250514-v1:0, "
                                   + "amazon.nova-pro-v1:0") String modelId,
            @ToolArg(description = "AWS region for Bedrock and Textract (default: us-east-1)") String region) {

        if (pipelineType == null || pipelineType.isBlank()) {
            throw new ToolCallException(
                    "pipelineType is required. Use: summarization, extraction, rag, or classification", null);
        }

        String resolvedType = pipelineType.toLowerCase().trim();
        String resolvedProcessor = documentProcessor != null && !documentProcessor.isBlank()
                ? documentProcessor.toLowerCase().trim() : "docling";
        String resolvedSource = documentSource != null && !documentSource.isBlank()
                ? documentSource.toLowerCase().trim() : "file";
        String resolvedModel = modelId != null && !modelId.isBlank() ? modelId.trim() : DEFAULT_MODEL;
        String resolvedRegion = region != null && !region.isBlank() ? region.trim() : DEFAULT_REGION;

        validateInputs(resolvedType, resolvedProcessor, resolvedSource);

        String yamlRoute = generateRoute(resolvedType, resolvedProcessor, resolvedSource, resolvedModel, resolvedRegion);
        String properties = generateProperties(resolvedProcessor, resolvedSource, resolvedModel, resolvedRegion);
        String description = generateDescription(resolvedType, resolvedProcessor, resolvedSource);

        return new ScaffoldResult(yamlRoute, properties, description);
    }

    private void validateInputs(String type, String processor, String source) {
        switch (type) {
            case "summarization", "extraction", "rag", "classification" -> {
            }
            default -> throw new ToolCallException(
                    "Unknown pipeline type: " + type + ". Use: summarization, extraction, rag, or classification", null);
        }
        switch (processor) {
            case "docling", "textract", "combined" -> {
            }
            default -> throw new ToolCallException(
                    "Unknown document processor: " + processor + ". Use: docling, textract, or combined", null);
        }
        switch (source) {
            case "file", "s3", "url" -> {
            }
            default -> throw new ToolCallException(
                    "Unknown document source: " + source + ". Use: file, s3, or url", null);
        }
    }

    // ---- Route generation ----

    private String generateRoute(String type, String processor, String source, String model, String region) {
        return switch (type) {
            case "summarization" -> generateSummarizationRoute(processor, source, model, region);
            case "extraction" -> generateExtractionRoute(processor, source, model, region);
            case "rag" -> generateRagRoute(processor, source, model, region);
            case "classification" -> generateClassificationRoute(processor, source, model, region);
            default -> throw new ToolCallException("Unsupported pipeline type: " + type, null);
        };
    }

    private String generateSummarizationRoute(String processor, String source, String model, String region) {
        StringBuilder sb = new StringBuilder();
        sb.append("# AI Document Summarization Pipeline\n");
        sb.append("# Extracts text from documents and generates summaries via Bedrock\n");
        sb.append("- route:\n");
        sb.append("    id: ai-summarization\n");
        sb.append("    from:\n");
        appendSourceEndpoint(sb, source);
        sb.append("    steps:\n");
        appendDocumentProcessing(sb, processor);
        sb.append("      - setHeader:\n");
        sb.append("          name: CamelAwsBedrockModelId\n");
        sb.append("          constant: \"").append(model).append("\"\n");
        sb.append("      - setHeader:\n");
        sb.append("          name: CamelAwsBedrockInputType\n");
        sb.append("          constant: \"text\"\n");
        sb.append("      - process:\n");
        sb.append("          ref: \"#buildSummarizationPrompt\"\n");
        appendBedrockConverse(sb, region);
        sb.append("      - log:\n");
        sb.append("          message: \"Summary generated: ${body}\"\n");
        return sb.toString();
    }

    private String generateExtractionRoute(String processor, String source, String model, String region) {
        StringBuilder sb = new StringBuilder();
        sb.append("# AI Structured Data Extraction Pipeline\n");
        sb.append("# Extracts text from documents and pulls structured data via Bedrock\n");
        sb.append("- route:\n");
        sb.append("    id: ai-extraction\n");
        sb.append("    from:\n");
        appendSourceEndpoint(sb, source);
        sb.append("    steps:\n");
        appendDocumentProcessing(sb, processor);
        sb.append("      - setHeader:\n");
        sb.append("          name: CamelAwsBedrockModelId\n");
        sb.append("          constant: \"").append(model).append("\"\n");
        sb.append("      - process:\n");
        sb.append("          ref: \"#buildExtractionPrompt\"\n");
        appendBedrockConverse(sb, region);
        sb.append("      - unmarshal:\n");
        sb.append("          json:\n");
        sb.append("            unmarshalType: java.util.Map\n");
        sb.append("      - log:\n");
        sb.append("          message: \"Extracted data: ${body}\"\n");
        return sb.toString();
    }

    private String generateRagRoute(String processor, String source, String model, String region) {
        StringBuilder sb = new StringBuilder();
        sb.append("# RAG (Retrieval-Augmented Generation) Pipeline\n");
        sb.append("# Ingests documents into a vector store, then answers queries using Bedrock\n");
        sb.append("#\n");
        sb.append("# This pipeline has two routes:\n");
        sb.append("# 1. Ingestion: documents -> chunk -> embed -> vector store\n");
        sb.append("# 2. Query: user question -> retrieve context -> Bedrock answer\n\n");

        // Ingestion route
        sb.append("- route:\n");
        sb.append("    id: rag-ingestion\n");
        sb.append("    from:\n");
        appendSourceEndpoint(sb, source);
        sb.append("    steps:\n");
        appendDocumentProcessing(sb, processor);
        sb.append("      # Chunk the extracted text for embedding\n");
        sb.append("      - split:\n");
        sb.append("          tokenize: \"\\n\\n\"\n");
        sb.append("          streaming: true\n");
        sb.append("        steps:\n");
        sb.append("          - to:\n");
        sb.append("              uri: \"langchain4j-embeddings:embed\"\n");
        sb.append("              parameters:\n");
        sb.append("                embeddingModelId: \"#bedrockEmbedding\"\n");
        sb.append("          # TODO: Configure your vector store endpoint\n");
        sb.append("          - to: \"log:ingested?showBody=false&showHeaders=true\"\n\n");

        // Query route
        sb.append("- route:\n");
        sb.append("    id: rag-query\n");
        sb.append("    from:\n");
        sb.append("      uri: \"direct:query\"\n");
        sb.append("    steps:\n");
        sb.append("      # TODO: Retrieve relevant chunks from vector store\n");
        sb.append("      # - to: \"langchain4j-embeddings:embed\" # embed the query\n");
        sb.append("      # - to: \"qdrant:search\"                # search vector store\n");
        sb.append("      - setHeader:\n");
        sb.append("          name: CamelAwsBedrockModelId\n");
        sb.append("          constant: \"").append(model).append("\"\n");
        sb.append("      - process:\n");
        sb.append("          ref: \"#buildRagPrompt\"\n");
        appendBedrockConverse(sb, region);
        sb.append("      - log:\n");
        sb.append("          message: \"RAG answer: ${body}\"\n");
        return sb.toString();
    }

    private String generateClassificationRoute(String processor, String source, String model, String region) {
        StringBuilder sb = new StringBuilder();
        sb.append("# AI Document Classification Pipeline\n");
        sb.append("# Extracts text and classifies documents into categories via Bedrock\n");
        sb.append("- route:\n");
        sb.append("    id: ai-classification\n");
        sb.append("    from:\n");
        appendSourceEndpoint(sb, source);
        sb.append("    steps:\n");
        appendDocumentProcessing(sb, processor);
        sb.append("      - setHeader:\n");
        sb.append("          name: CamelAwsBedrockModelId\n");
        sb.append("          constant: \"").append(model).append("\"\n");
        sb.append("      - process:\n");
        sb.append("          ref: \"#buildClassificationPrompt\"\n");
        appendBedrockConverse(sb, region);
        sb.append("      - choice:\n");
        sb.append("          when:\n");
        sb.append("            - simple: \"${body} contains 'invoice'\"\n");
        sb.append("              steps:\n");
        sb.append("                - to: \"direct:handle-invoice\"\n");
        sb.append("            - simple: \"${body} contains 'contract'\"\n");
        sb.append("              steps:\n");
        sb.append("                - to: \"direct:handle-contract\"\n");
        sb.append("          otherwise:\n");
        sb.append("            steps:\n");
        sb.append("              - to: \"direct:handle-other\"\n");
        return sb.toString();
    }

    // ---- Source endpoint helpers ----

    private void appendSourceEndpoint(StringBuilder sb, String source) {
        switch (source) {
            case "file" -> {
                sb.append("      uri: \"file:{{document.input.dir}}\"\n");
                sb.append("      parameters:\n");
                sb.append("        noop: true\n");
                sb.append("        include: \".*\\\\.(pdf|docx|png|jpg|tiff)\"\n");
            }
            case "s3" -> {
                sb.append("      uri: \"aws2-s3:{{document.s3.bucket}}\"\n");
                sb.append("      parameters:\n");
                sb.append("        region: \"{{aws.region}}\"\n");
                sb.append("        deleteAfterRead: false\n");
            }
            case "url" -> {
                sb.append("      uri: \"direct:process-url\"\n");
                sb.append("      # Send document URLs to this endpoint via: template.sendBody(\"direct:process-url\", url)\n");
            }
            default -> sb.append("      uri: \"direct:start\"\n");
        }
    }

    // ---- Document processing helpers ----

    private void appendDocumentProcessing(StringBuilder sb, String processor) {
        switch (processor) {
            case "docling" -> appendDoclingStep(sb);
            case "textract" -> appendTextractStep(sb);
            case "combined" -> {
                appendDoclingStep(sb);
                sb.append("      # Also extract tables/forms via Textract for structured data\n");
                appendTextractStep(sb);
                sb.append("      - process:\n");
                sb.append("          ref: \"#mergeDoclingAndTextract\"\n");
            }
            default -> appendDoclingStep(sb);
        }
    }

    private void appendDoclingStep(StringBuilder sb) {
        sb.append("      - to:\n");
        sb.append("          uri: \"docling:convert\"\n");
        sb.append("          parameters:\n");
        sb.append("            operation: CONVERT_TO_MARKDOWN\n");
        sb.append("            serverUrl: \"{{docling.server.url}}\"\n");
    }

    private void appendTextractStep(StringBuilder sb) {
        sb.append("      - to:\n");
        sb.append("          uri: \"aws2-textract:detect\"\n");
        sb.append("          parameters:\n");
        sb.append("            operation: detectDocumentText\n");
        sb.append("            region: \"{{aws.region}}\"\n");
    }

    private void appendBedrockConverse(StringBuilder sb, String region) {
        sb.append("      - to:\n");
        sb.append("          uri: \"aws-bedrock:label\"\n");
        sb.append("          parameters:\n");
        sb.append("            operation: converse\n");
        sb.append("            region: \"").append(region).append("\"\n");
    }

    // ---- Properties generation ----

    private String generateProperties(String processor, String source, String model, String region) {
        StringBuilder sb = new StringBuilder();
        sb.append("# === AI Pipeline Configuration ===\n\n");

        // AWS common
        sb.append("# AWS Configuration\n");
        sb.append("aws.region=").append(region).append("\n");
        sb.append("# aws.accessKey={{aws-access-key}}\n");
        sb.append("# aws.secretKey={{aws-secret-key}}\n\n");

        // Document source
        switch (source) {
            case "file" -> {
                sb.append("# Document Input\n");
                sb.append("document.input.dir=/path/to/documents\n\n");
            }
            case "s3" -> {
                sb.append("# S3 Document Source\n");
                sb.append("document.s3.bucket=my-document-bucket\n\n");
            }
            default -> {
            }
        }

        // Document processor
        if ("docling".equals(processor) || "combined".equals(processor)) {
            sb.append("# Docling Server (start with: docker run -p 5001:5001 quay.io/docling-project/docling-serve)\n");
            sb.append("docling.server.url=http://localhost:5001\n\n");
        }

        // Bedrock
        sb.append("# Bedrock LLM\n");
        sb.append("# Model: ").append(model).append("\n");
        sb.append("# Ensure IAM credentials have bedrock:InvokeModel permission\n\n");

        return sb.toString();
    }

    // ---- Description generation ----

    private String generateDescription(String type, String processor, String source) {
        String processorDesc = switch (processor) {
            case "docling" -> "Docling (open-source document converter)";
            case "textract" -> "AWS Textract (managed OCR/table extraction)";
            case "combined" -> "Docling (text) + Textract (tables/forms)";
            default -> processor;
        };
        String sourceDesc = switch (source) {
            case "file" -> "local filesystem";
            case "s3" -> "AWS S3 bucket";
            case "url" -> "HTTP/HTTPS URLs";
            default -> source;
        };
        return String.format(
                "%s pipeline using %s for document processing. Documents sourced from %s, processed by AWS Bedrock.",
                capitalize(type), processorDesc, sourceDesc);
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ---- Result record ----

    record ScaffoldResult(
            String yamlRoute,
            String applicationProperties,
            String description) {
    }
}
