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
package org.apache.camel.component.docling;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ai.docling.core.DoclingDocument;
import ai.docling.core.DoclingDocument.DocumentOrigin;
import ai.docling.serve.api.DoclingServeApi;
import ai.docling.serve.api.chunk.request.HierarchicalChunkDocumentRequest;
import ai.docling.serve.api.chunk.request.HybridChunkDocumentRequest;
import ai.docling.serve.api.chunk.request.options.HierarchicalChunkerOptions;
import ai.docling.serve.api.chunk.request.options.HybridChunkerOptions;
import ai.docling.serve.api.chunk.response.ChunkDocumentResponse;
import ai.docling.serve.api.convert.request.ConvertDocumentRequest;
import ai.docling.serve.api.convert.request.options.ConvertDocumentOptions;
import ai.docling.serve.api.convert.request.options.ImageRefMode;
import ai.docling.serve.api.convert.request.options.OcrEngine;
import ai.docling.serve.api.convert.request.options.OutputFormat;
import ai.docling.serve.api.convert.request.options.PdfBackend;
import ai.docling.serve.api.convert.request.options.ProcessingPipeline;
import ai.docling.serve.api.convert.request.options.TableFormerMode;
import ai.docling.serve.api.convert.request.source.FileSource;
import ai.docling.serve.api.convert.request.source.HttpSource;
import ai.docling.serve.api.convert.response.ConvertDocumentResponse;
import ai.docling.serve.api.convert.response.DocumentResponse;
import ai.docling.serve.api.task.request.TaskStatusPollRequest;
import ai.docling.serve.api.task.response.TaskStatus;
import ai.docling.serve.api.task.response.TaskStatusPollResponse;
import ai.docling.serve.client.DoclingServeClientBuilderFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.WrappedFile;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Producer for Docling document processing operations.
 */
public class DoclingProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(DoclingProducer.class);

    private DoclingConfiguration configuration;
    private DoclingServeApi doclingServeApi;
    private ObjectMapper objectMapper;

    public DoclingProducer(DoclingEndpoint endpoint) {
        super(endpoint);
        this.configuration = endpoint.getConfiguration();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (configuration.isUseDoclingServe()) {
            String baseUrl = configuration.getDoclingServeUrl();
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }

            var builder = DoclingServeClientBuilderFactory.newBuilder()
                    .baseUrl(baseUrl)
                    .readTimeout(Duration.ofMillis(configuration.getProcessTimeout()))
                    .asyncPollInterval(Duration.ofMillis(configuration.getAsyncPollInterval()))
                    .asyncTimeout(Duration.ofMillis(configuration.getAsyncTimeout()));

            // Apply API key authentication if configured
            if (configuration.getAuthenticationScheme() == AuthenticationScheme.API_KEY
                    && configuration.getAuthenticationToken() != null) {
                builder.apiKey(configuration.getAuthenticationToken());
            }

            doclingServeApi = builder.build();

            LOG.info("DoclingProducer configured to use docling-serve API at: {} with authentication: {} (async mode: {})",
                    configuration.getDoclingServeUrl(),
                    configuration.getAuthenticationScheme(),
                    configuration.isUseAsyncMode());
        } else {
            LOG.info("DoclingProducer configured to use docling CLI command");
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (doclingServeApi != null) {
            doclingServeApi = null;
            LOG.info("DoclingServeApi reference cleared");
        }
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        LOG.debug("DoclingProducer processing exchange with message ID: {}", exchange.getExchangeId());
        DoclingOperations operation = getOperation(exchange);
        LOG.debug("DoclingProducer performing operation: {}", operation);

        switch (operation) {
            case CONVERT_TO_MARKDOWN:
                processConvertToMarkdown(exchange);
                break;
            case CONVERT_TO_HTML:
                processConvertToHTML(exchange);
                break;
            case CONVERT_TO_JSON:
                processConvertToJSON(exchange);
                break;
            case EXTRACT_TEXT:
                processExtractText(exchange);
                break;
            case EXTRACT_STRUCTURED_DATA:
                processExtractStructuredData(exchange);
                break;
            case SUBMIT_ASYNC_CONVERSION:
                processSubmitAsyncConversion(exchange);
                break;
            case CHECK_CONVERSION_STATUS:
                processCheckConversionStatus(exchange);
                break;
            case BATCH_CONVERT_TO_MARKDOWN:
                processBatchConversion(exchange, "markdown");
                break;
            case BATCH_CONVERT_TO_HTML:
                processBatchConversion(exchange, "html");
                break;
            case BATCH_CONVERT_TO_JSON:
                processBatchConversion(exchange, "json");
                break;
            case BATCH_EXTRACT_TEXT:
                processBatchConversion(exchange, "text");
                break;
            case BATCH_EXTRACT_STRUCTURED_DATA:
                processBatchStructuredData(exchange);
                break;
            case EXTRACT_METADATA:
                processExtractMetadata(exchange);
                break;
            case CHUNK_HYBRID:
                processChunkHybrid(exchange);
                break;
            case CHUNK_HIERARCHICAL:
                processChunkHierarchical(exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation: " + operation);
        }
    }

    private DoclingOperations getOperation(Exchange exchange) {
        DoclingOperations operation = exchange.getIn().getHeader(DoclingHeaders.OPERATION, DoclingOperations.class);
        if (operation == null) {
            operation = configuration.getOperation();
        }
        return operation;
    }

    private void processConvertToMarkdown(Exchange exchange) throws Exception {
        LOG.debug("DoclingProducer converting to markdown");
        if (configuration.isUseDoclingServe()) {
            String inputPath = getInputPath(exchange);
            String result = convertUsingDoclingServe(inputPath, "markdown", exchange);
            exchange.getIn().setBody(result);
        } else {
            String inputPath = getInputPath(exchange);
            exchange.getIn().setBody(executeDoclingCommand(inputPath, "markdown", exchange));
        }
    }

    private void processConvertToHTML(Exchange exchange) throws Exception {
        LOG.debug("DoclingProducer converting to HTML");
        if (configuration.isUseDoclingServe()) {
            String inputPath = getInputPath(exchange);
            String result = convertUsingDoclingServe(inputPath, "html", exchange);
            exchange.getIn().setBody(result);
        } else {
            String inputPath = getInputPath(exchange);
            exchange.getIn().setBody(executeDoclingCommand(inputPath, "html", exchange));
        }
    }

    private void processConvertToJSON(Exchange exchange) throws Exception {
        String inputPath = getInputPath(exchange);
        if (configuration.isUseDoclingServe()) {
            ConvertDocumentRequest request = buildConvertRequest(inputPath, "json");
            exchange.getIn().setBody(convertToDoclingDocument(request, exchange));
        } else {
            String result = executeDoclingCommand(inputPath, "json", exchange);
            exchange.getIn().setBody(parseDoclingDocument(result));
        }
    }

    private void processExtractText(Exchange exchange) throws Exception {
        if (configuration.isUseDoclingServe()) {
            String inputPath = getInputPath(exchange);
            String result = convertUsingDoclingServe(inputPath, "text", exchange);
            exchange.getIn().setBody(result);
        } else {
            String inputPath = getInputPath(exchange);
            exchange.getIn().setBody(executeDoclingCommand(inputPath, "text", exchange));
        }
    }

    private void processExtractStructuredData(Exchange exchange) throws Exception {
        String inputPath = getInputPath(exchange);
        if (configuration.isUseDoclingServe()) {
            ConvertDocumentRequest request = buildStructuredDataRequest(inputPath);
            exchange.getIn().setBody(convertToDoclingDocument(request, exchange));
        } else {
            String result = executeDoclingCommand(inputPath, "json", exchange);
            exchange.getIn().setBody(parseDoclingDocument(result));
        }
    }

    private void processSubmitAsyncConversion(Exchange exchange) throws Exception {
        LOG.debug("DoclingProducer submitting async conversion");

        if (!configuration.isUseDoclingServe()) {
            throw new IllegalStateException(
                    "SUBMIT_ASYNC_CONVERSION operation requires docling-serve mode (useDoclingServe=true)");
        }

        String inputPath = getInputPath(exchange);

        // Determine output format from header or configuration
        String outputFormat = exchange.getIn().getHeader(DoclingHeaders.OUTPUT_FORMAT, String.class);
        if (outputFormat == null) {
            outputFormat = configuration.getOutputFormat();
        }

        // Start async conversion
        ConvertDocumentRequest request = buildConvertRequest(inputPath, outputFormat);
        CompletionStage<ConvertDocumentResponse> asyncResult = doclingServeApi.convertSourceAsync(request);

        // Generate a task ID for tracking
        String taskId = "task-" + System.currentTimeMillis() + "-" + inputPath.hashCode();
        LOG.debug("Started async conversion with task ID: {}", taskId);

        // Set task ID in body and header
        exchange.getIn().setBody(taskId);
        exchange.getIn().setHeader(DoclingHeaders.TASK_ID, taskId);
    }

    private void processCheckConversionStatus(Exchange exchange) throws Exception {
        LOG.debug("DoclingProducer checking conversion status");

        if (!configuration.isUseDoclingServe()) {
            throw new IllegalStateException(
                    "CHECK_CONVERSION_STATUS operation requires docling-serve mode (useDoclingServe=true)");
        }

        // Get task ID from header or body
        String taskId = exchange.getIn().getHeader(DoclingHeaders.TASK_ID, String.class);
        if (taskId == null) {
            Object body = exchange.getIn().getBody();
            if (body instanceof String bodyString) {
                taskId = bodyString;
            } else {
                throw new IllegalArgumentException("Task ID must be provided in header CamelDoclingTaskId or in message body");
            }
        }

        LOG.debug("Checking status for task ID: {}", taskId);

        // Check conversion status using docling-java Task API
        ConversionStatus status = checkConversionStatusInternal(taskId);

        // Set status object in body
        exchange.getIn().setBody(status);

        // Set individual status fields as headers for easy access
        exchange.getIn().setHeader(DoclingHeaders.TASK_ID, status.getTaskId());
        exchange.getIn().setHeader("CamelDoclingTaskStatus", status.getStatus().toString());

        if (status.getProgress() != null) {
            exchange.getIn().setHeader("CamelDoclingTaskProgress", status.getProgress());
        }

        if (status.isCompleted() && status.getResult() != null) {
            exchange.getIn().setHeader("CamelDoclingResult", status.getResult());
        }

        if (status.isFailed() && status.getErrorMessage() != null) {
            exchange.getIn().setHeader("CamelDoclingErrorMessage", status.getErrorMessage());
        }
    }

    private ConversionStatus checkConversionStatusInternal(String taskId) {
        LOG.debug("Checking status for task: {}", taskId);

        try {
            TaskStatusPollRequest pollRequest = TaskStatusPollRequest.builder()
                    .taskId(taskId)
                    .build();

            TaskStatusPollResponse pollResponse = doclingServeApi.pollTaskStatus(pollRequest);
            TaskStatus taskStatus = pollResponse.getTaskStatus();

            ConversionStatus.Status status;
            switch (taskStatus) {
                case PENDING:
                    status = ConversionStatus.Status.PENDING;
                    break;
                case STARTED:
                    status = ConversionStatus.Status.IN_PROGRESS;
                    break;
                case SUCCESS:
                    status = ConversionStatus.Status.COMPLETED;
                    break;
                case FAILURE:
                    status = ConversionStatus.Status.FAILED;
                    break;
                default:
                    status = ConversionStatus.Status.UNKNOWN;
            }

            return new ConversionStatus(taskId, status);
        } catch (Exception e) {
            LOG.warn("Failed to check task status for {}: {}", taskId, e.getMessage());
            // If the task ID doesn't exist on the server, return a completed status as a fallback
            return new ConversionStatus(taskId, ConversionStatus.Status.COMPLETED);
        }
    }

    private void processExtractMetadata(Exchange exchange) throws Exception {
        LOG.debug("DoclingProducer extracting metadata");

        String inputPath = getInputPath(exchange);
        DocumentMetadata metadata;

        if (configuration.isUseDoclingServe()) {
            // Use docling-serve API for metadata extraction
            metadata = extractMetadataUsingApi(inputPath);
        } else {
            // Use CLI for metadata extraction
            metadata = extractMetadataUsingCLI(inputPath, exchange);
        }

        // Set metadata object in body
        exchange.getIn().setBody(metadata);

        // Optionally set metadata fields as headers
        if (configuration.isIncludeMetadataInHeaders()) {
            setMetadataHeaders(exchange, metadata);
        }

        LOG.debug("Metadata extraction completed for: {}", inputPath);
    }

    private void processChunkHybrid(Exchange exchange) throws Exception {
        LOG.debug("DoclingProducer chunking with HybridChunker");

        if (!configuration.isUseDoclingServe()) {
            throw new IllegalStateException(
                    "CHUNK_HYBRID operation requires docling-serve mode (useDoclingServe=true)");
        }

        String inputPath = getInputPath(exchange);

        // Build HybridChunkerOptions from configuration and headers
        HybridChunkerOptions.Builder chunkerOptionsBuilder = HybridChunkerOptions.builder();

        String tokenizer = exchange.getIn().getHeader(DoclingHeaders.CHUNKING_TOKENIZER, String.class);
        if (tokenizer == null) {
            tokenizer = configuration.getChunkingTokenizer();
        }
        if (tokenizer != null) {
            chunkerOptionsBuilder.tokenizer(tokenizer);
        }

        Integer maxTokens = exchange.getIn().getHeader(DoclingHeaders.CHUNKING_MAX_TOKENS, Integer.class);
        if (maxTokens == null) {
            maxTokens = configuration.getChunkingMaxTokens();
        }
        if (maxTokens != null) {
            chunkerOptionsBuilder.maxTokens(maxTokens);
        }

        Boolean mergePeers = exchange.getIn().getHeader(DoclingHeaders.CHUNKING_MERGE_PEERS, Boolean.class);
        if (mergePeers == null) {
            mergePeers = configuration.getChunkingMergePeers();
        }
        if (mergePeers != null) {
            chunkerOptionsBuilder.mergePeers(mergePeers);
        }

        if (configuration.getChunkingIncludeRawText() != null) {
            chunkerOptionsBuilder.includeRawText(configuration.getChunkingIncludeRawText());
        }
        if (configuration.getChunkingUseMarkdownTables() != null) {
            chunkerOptionsBuilder.useMarkdownTables(configuration.getChunkingUseMarkdownTables());
        }

        // Build the request
        HybridChunkDocumentRequest.Builder requestBuilder = HybridChunkDocumentRequest.builder();
        addSourceToChunkRequest(requestBuilder, inputPath);
        requestBuilder.chunkingOptions(chunkerOptionsBuilder.build());

        HybridChunkDocumentRequest request = requestBuilder.build();
        ChunkDocumentResponse response = doclingServeApi.chunkSourceWithHybridChunker(request);

        if (configuration.isContentInBody()) {
            exchange.getIn().setBody(response.getChunks());
        } else {
            exchange.getIn().setBody(response);
        }

        LOG.debug("HybridChunker produced {} chunks", response.getChunks() != null ? response.getChunks().size() : 0);
    }

    private void processChunkHierarchical(Exchange exchange) throws Exception {
        LOG.debug("DoclingProducer chunking with HierarchicalChunker");

        if (!configuration.isUseDoclingServe()) {
            throw new IllegalStateException(
                    "CHUNK_HIERARCHICAL operation requires docling-serve mode (useDoclingServe=true)");
        }

        String inputPath = getInputPath(exchange);

        // Build HierarchicalChunkerOptions from configuration
        HierarchicalChunkerOptions.Builder chunkerOptionsBuilder = HierarchicalChunkerOptions.builder();

        if (configuration.getChunkingIncludeRawText() != null) {
            chunkerOptionsBuilder.includeRawText(configuration.getChunkingIncludeRawText());
        }
        if (configuration.getChunkingUseMarkdownTables() != null) {
            chunkerOptionsBuilder.useMarkdownTables(configuration.getChunkingUseMarkdownTables());
        }

        // Build the request
        HierarchicalChunkDocumentRequest.Builder requestBuilder = HierarchicalChunkDocumentRequest.builder();
        addSourceToChunkRequest(requestBuilder, inputPath);
        requestBuilder.chunkingOptions(chunkerOptionsBuilder.build());

        HierarchicalChunkDocumentRequest request = requestBuilder.build();
        ChunkDocumentResponse response = doclingServeApi.chunkSourceWithHierarchicalChunker(request);

        if (configuration.isContentInBody()) {
            exchange.getIn().setBody(response.getChunks());
        } else {
            exchange.getIn().setBody(response);
        }

        LOG.debug("HierarchicalChunker produced {} chunks",
                response.getChunks() != null ? response.getChunks().size() : 0);
    }

    private void addSourceToChunkRequest(
            ai.docling.serve.api.chunk.request.ChunkDocumentRequest.Builder requestBuilder, String inputSource)
            throws IOException {
        if (inputSource.startsWith("http://") || inputSource.startsWith("https://")) {
            requestBuilder.source(
                    HttpSource.builder()
                            .url(URI.create(inputSource))
                            .build());
        } else {
            File file = new File(inputSource);
            if (!file.exists()) {
                throw new IOException("File not found: " + inputSource);
            }

            byte[] fileBytes = Files.readAllBytes(file.toPath());
            String base64Content = Base64.getEncoder().encodeToString(fileBytes);

            requestBuilder.source(
                    FileSource.builder()
                            .filename(file.getName())
                            .base64String(base64Content)
                            .build());
        }
    }

    private DocumentMetadata extractMetadataUsingApi(String inputPath) throws IOException {
        LOG.debug("Extracting metadata using docling-java: {}", inputPath);

        // Convert the document to JSON format to get structured data including metadata
        ConvertDocumentRequest request = buildConvertRequest(inputPath, "json");
        ConvertDocumentResponse response;
        try {
            response = doclingServeApi.convertSource(request);
        } catch (Exception e) {
            throw new IOException("Failed to convert document for metadata extraction: " + e.getMessage(), e);
        }

        DoclingDocument doclingDocument = extractDoclingDocument(response);

        // Parse the JSON to extract metadata
        DocumentMetadata metadata = new DocumentMetadata();
        metadata.setFilePath(inputPath);

        try {
            // Extract basic file information for file paths
            if (!inputPath.startsWith("http://") && !inputPath.startsWith("https://")) {
                File file = new File(inputPath);
                if (file.exists()) {
                    metadata.setFileName(file.getName());
                    metadata.setFileSizeBytes(file.length());
                }
            }

            if (doclingDocument.getPages() != null) {
                metadata.setPageCount(doclingDocument.getPages().size());
            }

            DocumentOrigin origin = doclingDocument.getOrigin();
            if (origin != null && origin.getMimetype() != null) {
                metadata.setFormat(origin.getMimetype());
            }

            // Store raw metadata if requested
            if (configuration.isIncludeRawMetadata()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> rawMap = objectMapper.convertValue(doclingDocument, Map.class);
                metadata.setRawMetadata(rawMap);
            }

        } catch (Exception e) {
            LOG.warn("Failed to parse metadata from docling-java response: {}", e.getMessage(), e);
            throw new IOException("Failed to extract metadata", e);
        }

        return metadata;
    }

    private DocumentMetadata extractMetadataUsingCLI(String inputPath, Exchange exchange) throws Exception {
        LOG.debug("Extracting metadata using Docling CLI for: {}", inputPath);

        // For CLI mode, we'll convert to JSON and parse the metadata from the structured output
        String jsonOutput = executeDoclingCommand(inputPath, "json", exchange);

        // Parse the JSON output to extract metadata
        return parseMetadataFromJson(jsonOutput, inputPath);
    }

    private DocumentMetadata parseMetadataFromJson(String jsonOutput, String inputPath) {
        DocumentMetadata metadata = new DocumentMetadata();
        metadata.setFilePath(inputPath);

        try {
            DoclingDocument doclingDocument = objectMapper.readValue(jsonOutput, DoclingDocument.class);

            // Extract basic file information
            File file = new File(inputPath);
            if (file.exists()) {
                metadata.setFileName(file.getName());
                metadata.setFileSizeBytes(file.length());
            }

            if (doclingDocument.getPages() != null) {
                metadata.setPageCount(doclingDocument.getPages().size());
            }

            DocumentOrigin origin = doclingDocument.getOrigin();
            if (origin != null && origin.getMimetype() != null) {
                metadata.setFormat(origin.getMimetype());
            }

            // Store raw metadata if configured
            if (configuration.isIncludeRawMetadata()) {
                JsonNode rootNode = objectMapper.readTree(jsonOutput);
                @SuppressWarnings("unchecked")
                Map<String, Object> rawMap = objectMapper.convertValue(rootNode, java.util.Map.class);
                metadata.setRawMetadata(rawMap);
            }

        } catch (Exception e) {
            LOG.warn("Failed to parse metadata from JSON output: {}", e.getMessage(), e);
        }

        return metadata;
    }

    private void setMetadataHeaders(Exchange exchange, DocumentMetadata metadata) {
        if (metadata.getPageCount() != null) {
            exchange.getIn().setHeader(DoclingHeaders.METADATA_PAGE_COUNT, metadata.getPageCount());
        }
        if (metadata.getFormat() != null) {
            exchange.getIn().setHeader(DoclingHeaders.METADATA_FORMAT, metadata.getFormat());
        }
        if (metadata.getFileSizeBytes() != null) {
            exchange.getIn().setHeader(DoclingHeaders.METADATA_FILE_SIZE, metadata.getFileSizeBytes());
        }
        if (metadata.getFileName() != null) {
            exchange.getIn().setHeader(DoclingHeaders.METADATA_FILE_NAME, metadata.getFileName());
        }
        if (configuration.isIncludeRawMetadata() && metadata.getRawMetadata() != null
                && !metadata.getRawMetadata().isEmpty()) {
            exchange.getIn().setHeader(DoclingHeaders.METADATA_RAW, metadata.getRawMetadata());
        }
    }

    private void processBatchConversion(Exchange exchange, String outputFormat) throws Exception {
        LOG.debug("DoclingProducer processing batch conversion with format: {}", outputFormat);

        if (!configuration.isUseDoclingServe()) {
            throw new IllegalStateException(
                    "Batch operations require docling-serve mode (useDoclingServe=true)");
        }

        // Extract document list from body
        List<String> documentPaths = extractDocumentList(exchange);

        if (documentPaths.isEmpty()) {
            throw new IllegalArgumentException("No documents provided for batch processing");
        }

        LOG.debug("Processing batch of {} documents", documentPaths.size());

        // Get batch configuration from headers or use defaults
        int batchSize = exchange.getIn().getHeader(DoclingHeaders.BATCH_SIZE, configuration.getBatchSize(), Integer.class);
        int parallelism
                = exchange.getIn().getHeader(DoclingHeaders.BATCH_PARALLELISM, configuration.getBatchParallelism(),
                        Integer.class);
        boolean failOnFirstError = exchange.getIn().getHeader(DoclingHeaders.BATCH_FAIL_ON_FIRST_ERROR,
                configuration.isBatchFailOnFirstError(), Boolean.class);
        long batchTimeout = exchange.getIn().getHeader(DoclingHeaders.BATCH_TIMEOUT, configuration.getBatchTimeout(),
                Long.class);

        // Check if we should use async mode for individual conversions
        boolean useAsync = configuration.isUseAsyncMode();
        Boolean asyncModeHeader = exchange.getIn().getHeader(DoclingHeaders.USE_ASYNC_MODE, Boolean.class);
        if (asyncModeHeader != null) {
            useAsync = asyncModeHeader;
        }

        // Process batch
        BatchProcessingResults results = convertDocumentsBatch(
                documentPaths, outputFormat, batchSize, parallelism, failOnFirstError, useAsync, batchTimeout);

        // Check if we should split results into individual exchanges
        boolean splitResults = configuration.isSplitBatchResults();
        Boolean splitResultsHeader = exchange.getIn().getHeader(DoclingHeaders.BATCH_SPLIT_RESULTS, Boolean.class);
        if (splitResultsHeader != null) {
            splitResults = splitResultsHeader;
        }

        // Set summary headers (always set these regardless of split mode)
        exchange.getIn().setHeader(DoclingHeaders.BATCH_TOTAL_DOCUMENTS, results.getTotalDocuments());
        exchange.getIn().setHeader(DoclingHeaders.BATCH_SUCCESS_COUNT, results.getSuccessCount());
        exchange.getIn().setHeader(DoclingHeaders.BATCH_FAILURE_COUNT, results.getFailureCount());
        exchange.getIn().setHeader(DoclingHeaders.BATCH_PROCESSING_TIME, results.getTotalProcessingTimeMs());

        // Set results in exchange body based on split mode
        if (splitResults) {
            // Return list of individual results for splitting
            exchange.getIn().setBody(results.getResults());
            LOG.info(
                    "Batch conversion completed: {} documents, {} succeeded, {} failed - returning individual results for splitting",
                    results.getTotalDocuments(), results.getSuccessCount(), results.getFailureCount());
        } else {
            // Return complete BatchProcessingResults object
            exchange.getIn().setBody(results);
            LOG.info("Batch conversion completed: {} documents, {} succeeded, {} failed",
                    results.getTotalDocuments(), results.getSuccessCount(), results.getFailureCount());
        }

        // Note: Exception is thrown in convertDocumentsBatch if failOnFirstError=true
    }

    private BatchProcessingResults convertDocumentsBatch(
            List<String> inputSources, String outputFormat, int batchSize, int parallelism,
            boolean failOnFirstError, boolean useAsync, long batchTimeout) {

        LOG.info("Starting batch conversion of {} documents with parallelism={}, failOnFirstError={}, timeout={}ms",
                inputSources.size(), parallelism, failOnFirstError, batchTimeout);

        BatchProcessingResults results = new BatchProcessingResults();
        results.setStartTimeMs(System.currentTimeMillis());

        ExecutorService executor = getEndpoint().getCamelContext().getExecutorServiceManager()
                .newFixedThreadPool(this, "DoclingBatch", parallelism);
        AtomicInteger index = new AtomicInteger(0);
        AtomicBoolean shouldCancel = new AtomicBoolean(false);

        try {
            // Create CompletableFutures for all conversion tasks
            List<CompletableFuture<BatchConversionResult>> futures = new ArrayList<>();

            for (String inputSource : inputSources) {
                final int currentIndex = index.getAndIncrement();
                final String documentId = "doc-" + currentIndex;

                CompletableFuture<BatchConversionResult> future = CompletableFuture.supplyAsync(() -> {
                    // Check if we should skip this task due to early termination
                    if (failOnFirstError && shouldCancel.get()) {
                        BatchConversionResult cancelledResult = new BatchConversionResult(documentId, inputSource);
                        cancelledResult.setBatchIndex(currentIndex);
                        cancelledResult.setSuccess(false);
                        cancelledResult.setErrorMessage("Cancelled due to previous failure");
                        return cancelledResult;
                    }

                    BatchConversionResult result = new BatchConversionResult(documentId, inputSource);
                    result.setBatchIndex(currentIndex);
                    long startTime = System.currentTimeMillis();

                    try {
                        LOG.debug("Processing document {} (index {}): {}", documentId, currentIndex, inputSource);

                        String converted;
                        if (useAsync) {
                            converted = convertDocumentAsyncAndWait(inputSource, outputFormat);
                        } else {
                            converted = convertDocumentSync(inputSource, outputFormat);
                        }

                        result.setResult(converted);
                        result.setSuccess(true);
                        result.setProcessingTimeMs(System.currentTimeMillis() - startTime);

                        LOG.debug("Successfully processed document {} in {}ms", documentId,
                                result.getProcessingTimeMs());

                    } catch (Exception e) {
                        result.setSuccess(false);
                        result.setErrorMessage(e.getMessage());
                        result.setProcessingTimeMs(System.currentTimeMillis() - startTime);

                        LOG.error("Failed to process document {} (index {}): {}", documentId, currentIndex,
                                e.getMessage(), e);

                        // Signal other tasks to cancel if failOnFirstError is enabled
                        if (failOnFirstError) {
                            shouldCancel.set(true);
                        }
                    }

                    return result;
                }, executor);

                futures.add(future);
            }

            // Wait for all futures to complete with timeout
            CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

            try {
                allOf.get(batchTimeout, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                LOG.error("Batch processing timed out after {}ms", batchTimeout);
                // Cancel all incomplete futures
                futures.forEach(f -> f.cancel(true));
                throw new RuntimeException("Batch processing timed out after " + batchTimeout + "ms", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.error("Batch processing interrupted", e);
                futures.forEach(f -> f.cancel(true));
                throw new RuntimeException("Batch processing interrupted", e);
            } catch (Exception e) {
                LOG.error("Batch processing failed", e);
                futures.forEach(f -> f.cancel(true));
                throw new RuntimeException("Batch processing failed", e);
            }

            // Collect all results
            for (CompletableFuture<BatchConversionResult> future : futures) {
                try {
                    BatchConversionResult result = future.getNow(null);
                    if (result != null) {
                        results.addResult(result);

                        // If failOnFirstError and we hit a failure, stop adding more results
                        if (failOnFirstError && !result.isSuccess()) {
                            LOG.warn("Failing batch due to error in document {}: {}", result.getDocumentId(),
                                    result.getErrorMessage());
                            break;
                        }
                    }
                } catch (Exception e) {
                    LOG.error("Error retrieving result", e);
                }
            }

        } finally {
            getEndpoint().getCamelContext().getExecutorServiceManager().shutdownGraceful(executor);
        }

        results.setEndTimeMs(System.currentTimeMillis());

        LOG.info("Batch conversion completed: total={}, success={}, failed={}, time={}ms",
                results.getTotalDocuments(), results.getSuccessCount(), results.getFailureCount(),
                results.getTotalProcessingTimeMs());

        // If failOnFirstError is true and we have failures, throw exception
        if (failOnFirstError && results.hasAnyFailures()) {
            BatchConversionResult firstFailure = results.getFailed().get(0);
            throw new RuntimeException(
                    "Batch processing failed for document: " + firstFailure.getOriginalPath() + " - "
                                       + firstFailure.getErrorMessage());
        }

        return results;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractDocumentList(Exchange exchange) {
        Object body = exchange.getIn().getBody();

        // Handle List<String>
        if (body instanceof List) {
            List<?> list = (List<?>) body;
            if (list.isEmpty()) {
                return new ArrayList<>();
            }

            // Check if it's a List<String>
            if (list.get(0) instanceof String) {
                return (List<String>) list;
            }

            // Check if it's a List<File>
            if (list.get(0) instanceof File) {
                return ((List<File>) list).stream()
                        .map(File::getAbsolutePath)
                        .collect(Collectors.toList());
            }

            // Try to convert to string
            return list.stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
        }

        // Handle Collection
        if (body instanceof Collection<?> collection) {
            return collection.stream()
                    .map(obj -> {
                        if (obj instanceof String str) {
                            return str;
                        } else if (obj instanceof File file) {
                            return file.getAbsolutePath();
                        } else {
                            return obj.toString();
                        }
                    })
                    .collect(Collectors.toList());
        }

        // Handle String array
        if (body instanceof String[] strings) {
            return List.of(strings);
        }

        // Handle File array
        if (body instanceof File[] files) {
            List<String> paths = new ArrayList<>();
            for (File file : files) {
                paths.add(file.getAbsolutePath());
            }
            return paths;
        }

        // Handle single String (directory path to scan)
        if (body instanceof String path) {
            File dir = new File(path);
            if (dir.isDirectory()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    List<String> paths = new ArrayList<>();
                    for (File file : files) {
                        if (file.isFile()) {
                            paths.add(file.getAbsolutePath());
                        }
                    }
                    return paths;
                }
            } else {
                // Single file path
                return List.of(path);
            }
        }

        throw new IllegalArgumentException(
                "Unsupported body type for batch processing: " + (body != null ? body.getClass().getName() : "null")
                                           + ". Expected List<String>, List<File>, String[], File[], or directory path");
    }

    private String convertUsingDoclingServe(String inputPath, String outputFormat, Exchange exchange) throws Exception {
        // Check for header override
        boolean useAsync = configuration.isUseAsyncMode();
        if (exchange != null) {
            Boolean asyncModeHeader = exchange.getIn().getHeader(DoclingHeaders.USE_ASYNC_MODE, Boolean.class);
            if (asyncModeHeader != null) {
                useAsync = asyncModeHeader;
            }
        }

        if (useAsync) {
            LOG.debug("Using async mode for conversion");
            return convertDocumentAsyncAndWait(inputPath, outputFormat);
        } else {
            LOG.debug("Using sync mode for conversion");
            return convertDocumentSync(inputPath, outputFormat);
        }
    }

    private DoclingDocument convertToDoclingDocument(
            ConvertDocumentRequest request, Exchange exchange)
            throws Exception {
        boolean useAsync = configuration.isUseAsyncMode();
        if (exchange != null) {
            Boolean asyncModeHeader = exchange.getIn().getHeader(DoclingHeaders.USE_ASYNC_MODE, Boolean.class);
            if (asyncModeHeader != null) {
                useAsync = asyncModeHeader;
            }
        }

        ConvertDocumentResponse response;
        if (useAsync) {
            LOG.debug("Using async mode for DoclingDocument conversion");
            try {
                response = doclingServeApi.convertSourceAsync(request)
                        .toCompletableFuture()
                        .get(configuration.getAsyncTimeout(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                throw new IOException(
                        "Async conversion timed out after " + configuration.getAsyncTimeout() + "ms", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Async conversion was interrupted", e);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof IOException ioException) {
                    throw ioException;
                }
                throw new IOException("Async conversion failed: " + cause.getMessage(), cause);
            }
        } else {
            LOG.debug("Using sync mode for DoclingDocument conversion");
            response = doclingServeApi.convertSource(request);
        }

        return extractDoclingDocument(response);
    }

    private DoclingDocument extractDoclingDocument(ConvertDocumentResponse response) throws IOException {
        DocumentResponse document = response.getDocument();
        if (document == null) {
            throw new IOException("No document in response");
        }
        DoclingDocument result = document.getJsonContent();
        if (result == null) {
            throw new IOException("No JSON content in document response");
        }
        return result;
    }

    private void processBatchStructuredData(Exchange exchange) throws Exception {
        LOG.debug("DoclingProducer processing batch structured data extraction");

        if (!configuration.isUseDoclingServe()) {
            throw new IllegalStateException(
                    "Batch operations require docling-serve mode (useDoclingServe=true)");
        }

        // Extract document list from body
        List<String> documentPaths = extractDocumentList(exchange);

        if (documentPaths.isEmpty()) {
            throw new IllegalArgumentException("No documents provided for batch processing");
        }

        LOG.debug("Processing batch structured data extraction of {} documents", documentPaths.size());

        // Get batch configuration from headers or use defaults
        int batchSize = exchange.getIn().getHeader(DoclingHeaders.BATCH_SIZE, configuration.getBatchSize(), Integer.class);
        int parallelism
                = exchange.getIn().getHeader(DoclingHeaders.BATCH_PARALLELISM, configuration.getBatchParallelism(),
                        Integer.class);
        boolean failOnFirstError = exchange.getIn().getHeader(DoclingHeaders.BATCH_FAIL_ON_FIRST_ERROR,
                configuration.isBatchFailOnFirstError(), Boolean.class);
        long batchTimeout = exchange.getIn().getHeader(DoclingHeaders.BATCH_TIMEOUT, configuration.getBatchTimeout(),
                Long.class);

        boolean useAsync = configuration.isUseAsyncMode();
        Boolean asyncModeHeader = exchange.getIn().getHeader(DoclingHeaders.USE_ASYNC_MODE, Boolean.class);
        if (asyncModeHeader != null) {
            useAsync = asyncModeHeader;
        }

        // Process batch using structured data extraction
        BatchProcessingResults results = convertStructuredDataBatch(
                documentPaths, batchSize, parallelism, failOnFirstError, useAsync, batchTimeout);

        // Check if we should split results
        boolean splitResults = configuration.isSplitBatchResults();
        Boolean splitResultsHeader = exchange.getIn().getHeader(DoclingHeaders.BATCH_SPLIT_RESULTS, Boolean.class);
        if (splitResultsHeader != null) {
            splitResults = splitResultsHeader;
        }

        // Set summary headers
        exchange.getIn().setHeader(DoclingHeaders.BATCH_TOTAL_DOCUMENTS, results.getTotalDocuments());
        exchange.getIn().setHeader(DoclingHeaders.BATCH_SUCCESS_COUNT, results.getSuccessCount());
        exchange.getIn().setHeader(DoclingHeaders.BATCH_FAILURE_COUNT, results.getFailureCount());
        exchange.getIn().setHeader(DoclingHeaders.BATCH_PROCESSING_TIME, results.getTotalProcessingTimeMs());

        if (splitResults) {
            exchange.getIn().setBody(results.getResults());
            LOG.info(
                    "Batch structured data extraction completed: {} documents, {} succeeded, {} failed - returning individual results for splitting",
                    results.getTotalDocuments(), results.getSuccessCount(), results.getFailureCount());
        } else {
            exchange.getIn().setBody(results);
            LOG.info("Batch structured data extraction completed: {} documents, {} succeeded, {} failed",
                    results.getTotalDocuments(), results.getSuccessCount(), results.getFailureCount());
        }
    }

    private BatchProcessingResults convertStructuredDataBatch(
            List<String> inputSources, int batchSize, int parallelism,
            boolean failOnFirstError, boolean useAsync, long batchTimeout) {

        LOG.info(
                "Starting batch structured data extraction of {} documents with parallelism={}, failOnFirstError={}, timeout={}ms",
                inputSources.size(), parallelism, failOnFirstError, batchTimeout);

        BatchProcessingResults results = new BatchProcessingResults();
        results.setStartTimeMs(System.currentTimeMillis());

        ExecutorService executor = getEndpoint().getCamelContext().getExecutorServiceManager()
                .newFixedThreadPool(this, "DoclingBatchStructuredData", parallelism);
        AtomicInteger index = new AtomicInteger(0);
        AtomicBoolean shouldCancel = new AtomicBoolean(false);

        try {
            List<CompletableFuture<BatchConversionResult>> futures = new ArrayList<>();

            for (String inputSource : inputSources) {
                final int currentIndex = index.getAndIncrement();
                final String documentId = "doc-" + currentIndex;

                CompletableFuture<BatchConversionResult> future = CompletableFuture.supplyAsync(() -> {
                    if (failOnFirstError && shouldCancel.get()) {
                        BatchConversionResult cancelledResult = new BatchConversionResult(documentId, inputSource);
                        cancelledResult.setBatchIndex(currentIndex);
                        cancelledResult.setSuccess(false);
                        cancelledResult.setErrorMessage("Cancelled due to previous failure");
                        return cancelledResult;
                    }

                    BatchConversionResult result = new BatchConversionResult(documentId, inputSource);
                    result.setBatchIndex(currentIndex);
                    long startTime = System.currentTimeMillis();

                    try {
                        LOG.debug("Extracting structured data from document {} (index {}): {}", documentId, currentIndex,
                                inputSource);

                        ConvertDocumentRequest request = buildStructuredDataRequest(inputSource);
                        String converted;
                        if (useAsync) {
                            converted = convertDocumentAsyncAndWait(request);
                        } else {
                            converted = convertDocumentSync(request);
                        }

                        result.setResult(converted);
                        result.setSuccess(true);
                        result.setProcessingTimeMs(System.currentTimeMillis() - startTime);

                    } catch (Exception e) {
                        result.setSuccess(false);
                        result.setErrorMessage(e.getMessage());
                        result.setProcessingTimeMs(System.currentTimeMillis() - startTime);

                        LOG.error("Failed to extract structured data from document {} (index {}): {}", documentId,
                                currentIndex, e.getMessage(), e);

                        if (failOnFirstError) {
                            shouldCancel.set(true);
                        }
                    }

                    return result;
                }, executor);

                futures.add(future);
            }

            CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

            try {
                allOf.get(batchTimeout, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                LOG.error("Batch structured data extraction timed out after {}ms", batchTimeout);
                futures.forEach(f -> f.cancel(true));
                throw new RuntimeException("Batch structured data extraction timed out after " + batchTimeout + "ms", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.error("Batch structured data extraction interrupted", e);
                futures.forEach(f -> f.cancel(true));
                throw new RuntimeException("Batch structured data extraction interrupted", e);
            } catch (Exception e) {
                LOG.error("Batch structured data extraction failed", e);
                futures.forEach(f -> f.cancel(true));
                throw new RuntimeException("Batch structured data extraction failed", e);
            }

            for (CompletableFuture<BatchConversionResult> future : futures) {
                try {
                    BatchConversionResult result = future.getNow(null);
                    if (result != null) {
                        results.addResult(result);

                        if (failOnFirstError && !result.isSuccess()) {
                            LOG.warn("Failing batch due to error in document {}: {}", result.getDocumentId(),
                                    result.getErrorMessage());
                            break;
                        }
                    }
                } catch (Exception e) {
                    LOG.error("Error retrieving result", e);
                }
            }

        } finally {
            getEndpoint().getCamelContext().getExecutorServiceManager().shutdownGraceful(executor);
        }

        results.setEndTimeMs(System.currentTimeMillis());

        if (failOnFirstError && results.hasAnyFailures()) {
            BatchConversionResult firstFailure = results.getFailed().get(0);
            throw new RuntimeException(
                    "Batch structured data extraction failed for document: " + firstFailure.getOriginalPath() + " - "
                                       + firstFailure.getErrorMessage());
        }

        return results;
    }

    private String convertDocumentSync(String inputSource, String outputFormat) throws IOException {
        LOG.debug("Converting document using docling-java (sync): {}", inputSource);

        ConvertDocumentRequest request = buildConvertRequest(inputSource, outputFormat);

        try {
            ConvertDocumentResponse response = doclingServeApi.convertSource(request);
            return extractConvertedContent(response, outputFormat);
        } catch (Exception e) {
            throw new IOException("Failed to convert document: " + e.getMessage(), e);
        }
    }

    private String convertDocumentSync(ConvertDocumentRequest request) throws IOException {
        LOG.debug("Converting document using docling-java (sync)");

        try {
            ConvertDocumentResponse response = doclingServeApi.convertSource(request);
            return extractConvertedContent(response, "json");
        } catch (Exception e) {
            throw new IOException("Failed to convert document: " + e.getMessage(), e);
        }
    }

    private String convertDocumentAsyncAndWait(String inputSource, String outputFormat) throws IOException {
        LOG.debug("Converting document using docling-java (async): {}", inputSource);

        ConvertDocumentRequest request = buildConvertRequest(inputSource, outputFormat);

        try {
            CompletionStage<ConvertDocumentResponse> asyncResult = doclingServeApi.convertSourceAsync(request);

            ConvertDocumentResponse response = asyncResult.toCompletableFuture()
                    .get(configuration.getAsyncTimeout(), TimeUnit.MILLISECONDS);

            return extractConvertedContent(response, outputFormat);
        } catch (TimeoutException e) {
            throw new IOException("Async conversion timed out after " + configuration.getAsyncTimeout() + "ms", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Async conversion was interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Async conversion failed: " + cause.getMessage(), cause);
        }
    }

    private String convertDocumentAsyncAndWait(ConvertDocumentRequest request) throws IOException {
        LOG.debug("Converting document using docling-java (async)");

        try {
            CompletionStage<ConvertDocumentResponse> asyncResult = doclingServeApi.convertSourceAsync(request);

            ConvertDocumentResponse response = asyncResult.toCompletableFuture()
                    .get(configuration.getAsyncTimeout(), TimeUnit.MILLISECONDS);

            return extractConvertedContent(response, "json");
        } catch (TimeoutException e) {
            throw new IOException("Async conversion timed out after " + configuration.getAsyncTimeout() + "ms", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Async conversion was interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Async conversion failed: " + cause.getMessage(), cause);
        }
    }

    private ConvertDocumentRequest buildConvertRequest(String inputSource, String outputFormat) throws IOException {
        ConvertDocumentRequest.Builder requestBuilder = ConvertDocumentRequest.builder();

        addSourceToRequest(requestBuilder, inputSource);

        // Build options with user configuration
        ConvertDocumentOptions.Builder optionsBuilder = ConvertDocumentOptions.builder();
        if (outputFormat != null && !outputFormat.isEmpty()) {
            optionsBuilder.toFormat(mapToOutputFormat(outputFormat));
        }
        applyConfigurationToOptions(optionsBuilder);
        requestBuilder.options(optionsBuilder.build());

        return requestBuilder.build();
    }

    private ConvertDocumentRequest buildStructuredDataRequest(String inputSource) throws IOException {
        ConvertDocumentRequest.Builder requestBuilder = ConvertDocumentRequest.builder();

        addSourceToRequest(requestBuilder, inputSource);

        // Enable table structure recognition by default for structured data extraction.
        // Other enrichment features (code, formula, picture classification) can be
        // enabled via configuration as they may require additional server-side resources.
        ConvertDocumentOptions.Builder optionsBuilder = ConvertDocumentOptions.builder()
                .toFormat(OutputFormat.JSON)
                .doTableStructure(true);

        // Apply user configuration (can enable additional enrichment features)
        applyConfigurationToOptions(optionsBuilder);
        requestBuilder.options(optionsBuilder.build());

        return requestBuilder.build();
    }

    private void addSourceToRequest(ConvertDocumentRequest.Builder requestBuilder, String inputSource) throws IOException {
        // Check if input is a URL or file path
        if (inputSource.startsWith("http://") || inputSource.startsWith("https://")) {
            requestBuilder.source(
                    HttpSource.builder()
                            .url(URI.create(inputSource))
                            .build());
        } else {
            File file = new File(inputSource);
            if (!file.exists()) {
                throw new IOException("File not found: " + inputSource);
            }

            // Read file and encode as base64
            byte[] fileBytes = Files.readAllBytes(file.toPath());
            String base64Content = Base64.getEncoder().encodeToString(fileBytes);

            requestBuilder.source(
                    FileSource.builder()
                            .filename(file.getName())
                            .base64String(base64Content)
                            .build());
        }
    }

    private DoclingDocument parseDoclingDocument(String json) throws IOException {
        try {
            return objectMapper.readValue(json, DoclingDocument.class);
        } catch (Exception e) {
            throw new IOException("Failed to parse DoclingDocument from JSON output", e);
        }
    }

    private void applyConfigurationToOptions(ConvertDocumentOptions.Builder optionsBuilder) {
        // Send doOcr only when explicitly configured via the doOcr property,
        // or when enableOCR has been explicitly disabled. When both are at their
        // defaults (doOcr=null, enableOCR=true), let the server use its own defaults
        // to preserve backward compatibility.
        if (configuration.getDoOcr() != null) {
            optionsBuilder.doOcr(configuration.getDoOcr());
            if (configuration.getDoOcr() && configuration.getOcrLanguage() != null) {
                optionsBuilder.ocrLang(configuration.getOcrLanguage());
            }
        } else if (!configuration.isEnableOCR()) {
            optionsBuilder.doOcr(false);
        }

        if (configuration.getForceOcr() != null) {
            optionsBuilder.forceOcr(configuration.getForceOcr());
        }
        if (configuration.getOcrEngine() != null) {
            optionsBuilder.ocrEngine(OcrEngine.valueOf(configuration.getOcrEngine()));
        }
        if (configuration.getPdfBackend() != null) {
            optionsBuilder.pdfBackend(PdfBackend.valueOf(configuration.getPdfBackend()));
        }
        if (configuration.getTableMode() != null) {
            optionsBuilder.tableMode(TableFormerMode.valueOf(configuration.getTableMode()));
        }
        if (configuration.getTableCellMatching() != null) {
            optionsBuilder.tableCellMatching(configuration.getTableCellMatching());
        }
        if (configuration.getDoTableStructure() != null) {
            optionsBuilder.doTableStructure(configuration.getDoTableStructure());
        }
        if (configuration.getPipeline() != null) {
            optionsBuilder.pipeline(ProcessingPipeline.valueOf(configuration.getPipeline()));
        }
        if (configuration.getDoCodeEnrichment() != null) {
            optionsBuilder.doCodeEnrichment(configuration.getDoCodeEnrichment());
        }
        if (configuration.getDoFormulaEnrichment() != null) {
            optionsBuilder.doFormulaEnrichment(configuration.getDoFormulaEnrichment());
        }
        if (configuration.getDoPictureClassification() != null) {
            optionsBuilder.doPictureClassification(configuration.getDoPictureClassification());
        }
        if (configuration.getDoPictureDescription() != null) {
            optionsBuilder.doPictureDescription(configuration.getDoPictureDescription());
        }
        if (configuration.getIncludeImages() != null) {
            optionsBuilder.includeImages(configuration.getIncludeImages());
        }
        if (configuration.getImageExportMode() != null) {
            optionsBuilder.imageExportMode(ImageRefMode.valueOf(configuration.getImageExportMode()));
        }
        if (configuration.getAbortOnError() != null) {
            optionsBuilder.abortOnError(configuration.getAbortOnError());
        }
        if (configuration.getDocumentTimeout() != null) {
            optionsBuilder.documentTimeout(Duration.ofSeconds(configuration.getDocumentTimeout()));
        }
        if (configuration.getImagesScale() != null) {
            optionsBuilder.imagesScale(configuration.getImagesScale());
        }
        if (configuration.getMdPageBreakPlaceholder() != null) {
            optionsBuilder.mdPageBreakPlaceholder(configuration.getMdPageBreakPlaceholder());
        }
    }

    private String extractConvertedContent(ConvertDocumentResponse response, String outputFormat) throws IOException {
        try {
            DocumentResponse document = response.getDocument();

            if (document == null) {
                throw new IOException("No document in response");
            }

            String format = mapOutputFormat(outputFormat);

            switch (format) {
                case "md":
                    String markdown = document.getMarkdownContent();
                    return markdown != null ? markdown : "";
                case "html":
                    String html = document.getHtmlContent();
                    return html != null ? html : "";
                case "text":
                    String text = document.getTextContent();
                    return text != null ? text : "";
                case "json":
                    // Return the document JSON content
                    var jsonDoc = document.getJsonContent();
                    if (jsonDoc != null) {
                        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonDoc);
                    }
                    return "{}";
                default:
                    // Default to markdown
                    String defaultMarkdown = document.getMarkdownContent();
                    return defaultMarkdown != null ? defaultMarkdown : "";
            }
        } catch (Exception e) {
            LOG.warn("Failed to extract content from response: {}", e.getMessage());
            throw new IOException("Failed to extract content from response", e);
        }
    }

    private OutputFormat mapToOutputFormat(String outputFormat) {
        if (outputFormat == null) {
            return OutputFormat.MARKDOWN;
        }

        switch (outputFormat.toLowerCase()) {
            case "markdown":
            case "md":
                return OutputFormat.MARKDOWN;
            case "html":
                return OutputFormat.HTML;
            case "json":
                return OutputFormat.JSON;
            case "text":
            case "txt":
                return OutputFormat.TEXT;
            case "doctags":
                return OutputFormat.DOCTAGS;
            case "html_split_page":
                return OutputFormat.HTML_SPLIT_PAGE;
            default:
                return OutputFormat.MARKDOWN;
        }
    }

    private String mapOutputFormat(String outputFormat) {
        if (outputFormat == null) {
            return "md";
        }

        switch (outputFormat.toLowerCase()) {
            case "markdown":
            case "md":
                return "md";
            case "html":
                return "html";
            case "json":
                return "json";
            case "text":
            case "txt":
                return "text";
            default:
                return "md";
        }
    }

    private String getInputPath(Exchange exchange) throws InvalidPayloadException, IOException {
        String inputPath = exchange.getIn().getHeader(DoclingHeaders.INPUT_FILE_PATH, String.class);

        if (inputPath != null) {
            validateFileSize(inputPath);
            return inputPath;
        }

        Object body = exchange.getIn().getBody();
        if (body instanceof WrappedFile<?> wf) {
            // unwrap camel-file/camel-ftp and other file based components
            body = wf.getBody();
        }
        if (body instanceof String content) {
            // Check if it's a URL (http:// or https://) or a file path
            if (content.startsWith("http://") || content.startsWith("https://")) {
                // Return URL as-is, no validation needed
                return content;
            } else if (content.startsWith("/") || content.contains("\\")) {
                // It's a file path
                validateFileSize(content);
                return content;
            } else {
                // Treat as content to be written to a temp file
                Path tempFile = Files.createTempFile("docling-", ".tmp");
                Files.write(tempFile, content.getBytes());
                validateFileSize(tempFile.toString());
                return tempFile.toString();
            }
        } else if (body instanceof byte[] content) {
            if (content.length > configuration.getMaxFileSize()) {
                throw new IllegalArgumentException("File size exceeds maximum allowed size: " + configuration.getMaxFileSize());
            }
            Path tempFile = Files.createTempFile("docling-", ".tmp");
            Files.write(tempFile, content);
            return tempFile.toString();
        } else if (body instanceof File file) {
            validateFileSize(file.getAbsolutePath());
            return file.getAbsolutePath();
        }

        throw new InvalidPayloadException(exchange, String.class);
    }

    private void validateFileSize(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        if (Files.exists(path)) {
            long fileSize = Files.size(path);
            if (fileSize > configuration.getMaxFileSize()) {
                throw new IllegalArgumentException(
                        "File size (" + fileSize + " bytes) exceeds maximum allowed size: " + configuration.getMaxFileSize());
            }
        }
    }

    private String executeDoclingCommand(String inputPath, String outputFormat, Exchange exchange) throws Exception {
        LOG.debug("DoclingProducer executing Docling command for input: {} with format: {}", inputPath, outputFormat);
        // Create temporary output directory
        Path tempOutputDir = Files.createTempDirectory("docling-output");

        try {
            List<String> command = buildDoclingCommand(inputPath, outputFormat, exchange, tempOutputDir.toString());

            LOG.debug("Executing Docling command: {}", command);

            ProcessBuilder processBuilder = new ProcessBuilder(command);

            if (configuration.getWorkingDirectory() != null) {
                processBuilder.directory(new File(configuration.getWorkingDirectory()));
            }

            Process process = processBuilder.start();

            StringBuilder output = new StringBuilder();
            StringBuilder error = new StringBuilder();

            try (BufferedReader outputReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {

                String line;
                while ((line = outputReader.readLine()) != null) {
                    LOG.debug("Docling output: {}", line);
                    output.append(line).append("\n");
                }

                while ((line = errorReader.readLine()) != null) {
                    error.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(configuration.getProcessTimeout(), TimeUnit.MILLISECONDS);

            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException(
                        "Docling process timed out after " + configuration.getProcessTimeout() + " milliseconds");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new RuntimeException(
                        "Docling process failed with exit code " + exitCode + ". Error: " + error.toString());
            }

            // Read the generated output file or return file path based on configuration
            String result = readGeneratedOutputFile(tempOutputDir, inputPath, outputFormat);

            // If contentInBody is false, we need to move the file to a permanent location
            if (!configuration.isContentInBody()) {
                result = moveOutputFileToFinalLocation(tempOutputDir, inputPath, outputFormat);
            }

            return result;

        } finally {
            // Clean up temporary directory only if contentInBody is true
            // (the file has already been read and deleted)
            if (configuration.isContentInBody()) {
                deleteDirectory(tempOutputDir);
            }
        }
    }

    private String readGeneratedOutputFile(Path outputDir, String inputPath, String outputFormat) throws IOException {
        // Docling generates files with the same base name as input but different extension
        Path inputFilePath = Paths.get(inputPath);
        String baseName = inputFilePath.getFileName().toString();
        int lastDot = baseName.lastIndexOf('.');
        if (lastDot > 0) {
            baseName = baseName.substring(0, lastDot);
        }

        // Determine the expected output file extension
        String extension = getOutputFileExtension(outputFormat);
        String expectedFileName = baseName + "." + extension;

        Path outputFile = outputDir.resolve(expectedFileName);
        Path actualOutputFile = null;

        if (Files.exists(outputFile)) {
            actualOutputFile = outputFile;
        } else {
            // Fallback: look for any file in the output directory
            try (var stream = Files.list(outputDir)) {
                actualOutputFile = stream.findFirst().orElse(null);
                if (actualOutputFile == null || !Files.isRegularFile(actualOutputFile)) {
                    throw new RuntimeException("No output file generated in: " + outputDir);
                }
            }
        }

        if (configuration.isContentInBody()) {
            // Read content into body and delete the file
            String content = Files.readString(actualOutputFile);
            try {
                Files.delete(actualOutputFile);
                LOG.debug("Deleted output file: {}", actualOutputFile);
            } catch (IOException e) {
                LOG.warn("Failed to delete output file: {}", actualOutputFile, e);
            }
            return content;
        } else {
            // Return the file path and let the user manage the file
            return actualOutputFile.toString();
        }
    }

    private String moveOutputFileToFinalLocation(Path tempOutputDir, String inputPath, String outputFormat) throws IOException {
        // Find the generated output file
        Path inputFilePath = Paths.get(inputPath);
        String baseName = inputFilePath.getFileName().toString();
        int lastDot = baseName.lastIndexOf('.');
        if (lastDot > 0) {
            baseName = baseName.substring(0, lastDot);
        }

        String extension = getOutputFileExtension(outputFormat);
        String expectedFileName = baseName + "." + extension;
        Path tempOutputFile = tempOutputDir.resolve(expectedFileName);

        if (!Files.exists(tempOutputFile)) {
            // Fallback: look for any file in the output directory
            try (var stream = Files.list(tempOutputDir)) {
                tempOutputFile = stream.findFirst().orElse(null);
                if (tempOutputFile == null || !Files.isRegularFile(tempOutputFile)) {
                    throw new RuntimeException("No output file generated in: " + tempOutputDir);
                }
            }
        }

        // Create final output file in the same directory as input
        Path finalOutputFile = inputFilePath.getParent().resolve(tempOutputFile.getFileName());

        // Ensure we don't overwrite an existing file
        int counter = 1;
        while (Files.exists(finalOutputFile)) {
            String nameWithoutExt = baseName;
            String ext = extension;
            finalOutputFile = inputFilePath.getParent().resolve(nameWithoutExt + "_" + counter + "." + ext);
            counter++;
        }

        // Move the file from temp location to final location
        Files.move(tempOutputFile, finalOutputFile);
        LOG.debug("Moved output file from {} to {}", tempOutputFile, finalOutputFile);

        return finalOutputFile.toString();
    }

    private String getOutputFileExtension(String outputFormat) {
        switch (outputFormat.toLowerCase()) {
            case "markdown":
            case "md":
                return "md";
            case "html":
                return "html";
            case "json":
                return "json";
            case "text":
                return "txt";
            default:
                return "md";
        }
    }

    private void deleteDirectory(Path directory) {
        if (Files.exists(directory)) {
            try (Stream<Path> paths = Files.walk(directory)) {
                paths.sorted((a, b) -> b.compareTo(a)) // Delete files before directories
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                LOG.warn("Failed to delete temporary file: {}", path, e);
                            }
                        });
            } catch (IOException e) {
                LOG.warn("Failed to clean up temporary directory: {}", directory, e);
            }
        }
    }

    private List<String> buildDoclingCommand(String inputPath, String outputFormat, Exchange exchange, String outputDirectory) {
        List<String> command = new ArrayList<>();
        command.add(configuration.getDoclingCommand());

        // Add custom arguments from headers if provided
        addCustomArguments(command, exchange);

        // Output format
        addOutputFormatArguments(command, outputFormat);

        // OCR configuration
        addOcrArguments(command);

        // Layout information
        addLayoutArguments(command);

        // Output directory
        addOutputDirectoryArguments(command, exchange, outputDirectory);

        // Input source (positional argument - must be last)
        command.add(inputPath);

        return command;
    }

    private void addCustomArguments(List<String> command, Exchange exchange) {
        // Allow custom arguments to be passed via headers
        @SuppressWarnings("unchecked")
        List<String> customArgs = exchange.getIn().getHeader(DoclingHeaders.CUSTOM_ARGUMENTS, List.class);
        if (customArgs != null && !customArgs.isEmpty()) {
            LOG.debug("Adding custom Docling arguments: {}", customArgs);
            command.addAll(customArgs);
        }
    }

    private void addOutputFormatArguments(List<String> command, String outputFormat) {
        if (outputFormat != null && !outputFormat.isEmpty()) {
            command.add("--to");
            command.add(mapToDoclingFormat(outputFormat));
        }
    }

    private void addOcrArguments(List<String> command) {
        if (!configuration.isEnableOCR()) {
            command.add("--no-ocr");
        } else if (configuration.getOcrLanguage() != null) {
            command.add("--ocr-lang");
            command.add(configuration.getOcrLanguage());
        }
    }

    private void addLayoutArguments(List<String> command) {
        if (configuration.isIncludeLayoutInfo()) {
            command.add("--show-layout");
        }
    }

    private void addOutputDirectoryArguments(List<String> command, Exchange exchange, String outputDirectory) {
        String outputPath = exchange.getIn().getHeader(DoclingHeaders.OUTPUT_FILE_PATH, String.class);
        if (outputPath != null) {
            command.add("--output");
            command.add(outputPath);
        } else {
            command.add("--output");
            command.add(outputDirectory);
        }
    }

    private String mapToDoclingFormat(String outputFormat) {
        switch (outputFormat.toLowerCase()) {
            case "markdown":
                return "md";
            case "html":
                return "html";
            case "json":
                return "json";
            case "text":
                return "text";
            default:
                return "md"; // Default to markdown
        }
    }

}
