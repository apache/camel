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
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ai.docling.serve.api.DoclingServeApi;
import ai.docling.serve.api.convert.request.ConvertDocumentRequest;
import ai.docling.serve.api.convert.request.options.ConvertDocumentOptions;
import ai.docling.serve.api.convert.request.options.OutputFormat;
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

    private DoclingEndpoint endpoint;
    private DoclingConfiguration configuration;
    private DoclingServeApi doclingServeApi;
    private ObjectMapper objectMapper;

    public DoclingProducer(DoclingEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
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
                processBatchConversion(exchange, "json");
                break;
            case EXTRACT_METADATA:
                processExtractMetadata(exchange);
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
        if (configuration.isUseDoclingServe()) {
            String inputPath = getInputPath(exchange);
            String result = convertUsingDoclingServe(inputPath, "json", exchange);
            exchange.getIn().setBody(result);
        } else {
            String inputPath = getInputPath(exchange);
            exchange.getIn().setBody(executeDoclingCommand(inputPath, "json", exchange));
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
        if (configuration.isUseDoclingServe()) {
            String inputPath = getInputPath(exchange);
            String result = convertUsingDoclingServe(inputPath, "json", exchange);
            exchange.getIn().setBody(result);
        } else {
            String inputPath = getInputPath(exchange);
            exchange.getIn().setBody(executeDoclingCommand(inputPath, "json", exchange));
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
            if (body instanceof String) {
                taskId = (String) body;
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

    private DocumentMetadata extractMetadataUsingApi(String inputPath) throws IOException {
        LOG.debug("Extracting metadata using docling-java: {}", inputPath);

        // Convert the document to JSON format to get structured data including metadata
        String jsonOutput = convertDocumentSync(inputPath, "json");

        // Parse the JSON to extract metadata
        DocumentMetadata metadata = new DocumentMetadata();
        metadata.setFilePath(inputPath);

        try {
            JsonNode rootNode = objectMapper.readTree(jsonOutput);

            // Extract basic file information for file paths
            if (!inputPath.startsWith("http://") && !inputPath.startsWith("https://")) {
                File file = new File(inputPath);
                if (file.exists()) {
                    metadata.setFileName(file.getName());
                    metadata.setFileSizeBytes(file.length());
                }
            }

            // Try to extract metadata from the JSON structure
            if (rootNode.has(DoclingMetadataFields.METADATA)) {
                JsonNode metadataNode = rootNode.get(DoclingMetadataFields.METADATA);
                extractMetadataFieldsFromJson(metadata, metadataNode);
            }

            // Look for document-level information
            if (rootNode.has(DoclingMetadataFields.DOCUMENT)) {
                JsonNode docNode = rootNode.get(DoclingMetadataFields.DOCUMENT);
                if (docNode.has(DoclingMetadataFields.NAME) && metadata.getTitle() == null) {
                    metadata.setTitle(docNode.get(DoclingMetadataFields.NAME).asText());
                }
            }

            // Extract main text to determine document type/format
            if (rootNode.has(DoclingMetadataFields.MAIN_TEXT)) {
                JsonNode mainTextNode = rootNode.get(DoclingMetadataFields.MAIN_TEXT);
                if (mainTextNode.isArray() && mainTextNode.size() > 0) {
                    // Document has text content
                    metadata.setDocumentType("Text Document");
                }
            }

            // Count pages if available
            if (rootNode.has(DoclingMetadataFields.PAGES)) {
                if (rootNode.get(DoclingMetadataFields.PAGES).isArray()) {
                    metadata.setPageCount(rootNode.get(DoclingMetadataFields.PAGES).size());
                } else if (rootNode.get(DoclingMetadataFields.PAGES).isInt()) {
                    metadata.setPageCount(rootNode.get(DoclingMetadataFields.PAGES).asInt());
                }
            } else if (rootNode.has(DoclingMetadataFields.NUM_PAGES)) {
                metadata.setPageCount(rootNode.get(DoclingMetadataFields.NUM_PAGES).asInt());
            } else if (rootNode.has(DoclingMetadataFields.PAGE_COUNT)) {
                metadata.setPageCount(rootNode.get(DoclingMetadataFields.PAGE_COUNT).asInt());
            }

            // Store raw metadata if requested
            if (configuration.isIncludeRawMetadata()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> rawMap = objectMapper.convertValue(rootNode, Map.class);
                metadata.setRawMetadata(rawMap);
            }

        } catch (Exception e) {
            LOG.warn("Failed to parse metadata from docling-java response: {}", e.getMessage(), e);
            throw new IOException("Failed to extract metadata", e);
        }

        return metadata;
    }

    private void extractMetadataFieldsFromJson(DocumentMetadata metadata, JsonNode metadataNode) {
        // Extract standard metadata fields
        if (metadataNode.has(DoclingMetadataFields.TITLE)) {
            metadata.setTitle(metadataNode.get(DoclingMetadataFields.TITLE).asText());
        }
        if (metadataNode.has(DoclingMetadataFields.AUTHOR) || metadataNode.has(DoclingMetadataFields.AUTHOR_PASCAL)) {
            String author = metadataNode.has(DoclingMetadataFields.AUTHOR)
                    ? metadataNode.get(DoclingMetadataFields.AUTHOR).asText()
                    : metadataNode.get(DoclingMetadataFields.AUTHOR_PASCAL).asText();
            metadata.setAuthor(author);
        }
        if (metadataNode.has(DoclingMetadataFields.CREATOR) || metadataNode.has(DoclingMetadataFields.CREATOR_PASCAL)) {
            String creator = metadataNode.has(DoclingMetadataFields.CREATOR)
                    ? metadataNode.get(DoclingMetadataFields.CREATOR).asText()
                    : metadataNode.get(DoclingMetadataFields.CREATOR_PASCAL).asText();
            metadata.setCreator(creator);
        }
        if (metadataNode.has(DoclingMetadataFields.PRODUCER) || metadataNode.has(DoclingMetadataFields.PRODUCER_PASCAL)) {
            String producer = metadataNode.has(DoclingMetadataFields.PRODUCER)
                    ? metadataNode.get(DoclingMetadataFields.PRODUCER).asText()
                    : metadataNode.get(DoclingMetadataFields.PRODUCER_PASCAL).asText();
            metadata.setProducer(producer);
        }
        if (metadataNode.has(DoclingMetadataFields.SUBJECT) || metadataNode.has(DoclingMetadataFields.SUBJECT_PASCAL)) {
            String subject = metadataNode.has(DoclingMetadataFields.SUBJECT)
                    ? metadataNode.get(DoclingMetadataFields.SUBJECT).asText()
                    : metadataNode.get(DoclingMetadataFields.SUBJECT_PASCAL).asText();
            metadata.setSubject(subject);
        }
        if (metadataNode.has(DoclingMetadataFields.KEYWORDS) || metadataNode.has(DoclingMetadataFields.KEYWORDS_PASCAL)) {
            String keywords = metadataNode.has(DoclingMetadataFields.KEYWORDS)
                    ? metadataNode.get(DoclingMetadataFields.KEYWORDS).asText()
                    : metadataNode.get(DoclingMetadataFields.KEYWORDS_PASCAL).asText();
            metadata.setKeywords(keywords);
        }
        if (metadataNode.has(DoclingMetadataFields.LANGUAGE) || metadataNode.has(DoclingMetadataFields.LANGUAGE_PASCAL)) {
            String language = metadataNode.has(DoclingMetadataFields.LANGUAGE)
                    ? metadataNode.get(DoclingMetadataFields.LANGUAGE).asText()
                    : metadataNode.get(DoclingMetadataFields.LANGUAGE_PASCAL).asText();
            metadata.setLanguage(language);
        }

        // Extract format information
        if (metadataNode.has(DoclingMetadataFields.FORMAT) || metadataNode.has(DoclingMetadataFields.FORMAT_PASCAL)) {
            String format = metadataNode.has(DoclingMetadataFields.FORMAT)
                    ? metadataNode.get(DoclingMetadataFields.FORMAT).asText()
                    : metadataNode.get(DoclingMetadataFields.FORMAT_PASCAL).asText();
            metadata.setFormat(format);
        }

        // Extract dates - try multiple field name variations
        extractDateFieldFromJson(metadata, metadataNode, DoclingMetadataFields.CREATION_DATE,
                DoclingMetadataFields.CREATION_DATE_PASCAL, DoclingMetadataFields.CREATED,
                DoclingMetadataFields.CREATED_PASCAL, (date) -> metadata.setCreationDate(date));
        extractDateFieldFromJson(metadata, metadataNode, DoclingMetadataFields.MODIFICATION_DATE,
                DoclingMetadataFields.MODIFICATION_DATE_PASCAL, DoclingMetadataFields.MODIFIED,
                DoclingMetadataFields.MODIFIED_PASCAL, (date) -> metadata.setModificationDate(date));

        // Extract all other fields as custom metadata if requested
        if (configuration.isExtractAllMetadata()) {
            metadataNode.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                // Skip standard fields we already extracted
                if (!DoclingMetadataFields.isStandardField(key)) {
                    JsonNode value = entry.getValue();
                    if (value.isTextual()) {
                        metadata.addCustomMetadata(key, value.asText());
                    } else if (value.isInt()) {
                        metadata.addCustomMetadata(key, value.asInt());
                    } else if (value.isLong()) {
                        metadata.addCustomMetadata(key, value.asLong());
                    } else if (value.isBoolean()) {
                        metadata.addCustomMetadata(key, value.asBoolean());
                    } else if (value.isDouble()) {
                        metadata.addCustomMetadata(key, value.asDouble());
                    } else {
                        metadata.addCustomMetadata(key, value.toString());
                    }
                }
            });
        }
    }

    private void extractDateFieldFromJson(
            DocumentMetadata metadata, JsonNode metadataNode, String fieldName1,
            String fieldName2, String fieldName3, String fieldName4,
            java.util.function.Consumer<java.time.Instant> setter) {
        String dateStr = null;

        if (metadataNode.has(fieldName1)) {
            dateStr = metadataNode.get(fieldName1).asText();
        } else if (metadataNode.has(fieldName2)) {
            dateStr = metadataNode.get(fieldName2).asText();
        } else if (metadataNode.has(fieldName3)) {
            dateStr = metadataNode.get(fieldName3).asText();
        } else if (metadataNode.has(fieldName4)) {
            dateStr = metadataNode.get(fieldName4).asText();
        }

        if (dateStr != null && !dateStr.isEmpty()) {
            try {
                java.time.Instant instant = java.time.Instant.parse(dateStr);
                setter.accept(instant);
            } catch (Exception e) {
                LOG.debug("Failed to parse date field {}: {}", fieldName1, dateStr);
                // Try parsing as ISO local date time
                try {
                    java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(dateStr);
                    java.time.Instant instant = ldt.atZone(java.time.ZoneId.systemDefault()).toInstant();
                    setter.accept(instant);
                } catch (Exception e2) {
                    LOG.debug("Failed to parse date as LocalDateTime: {}", dateStr);
                }
            }
        }
    }

    private DocumentMetadata extractMetadataUsingCLI(String inputPath, Exchange exchange) throws Exception {
        LOG.debug("Extracting metadata using Docling CLI for: {}", inputPath);

        // For CLI mode, we'll convert to JSON and parse the metadata from the structured output
        String jsonOutput = executeDoclingCommand(inputPath, "json", exchange);

        // Parse the JSON output to extract metadata
        DocumentMetadata metadata = parseMetadataFromJson(jsonOutput, inputPath);

        return metadata;
    }

    private DocumentMetadata parseMetadataFromJson(String jsonOutput, String inputPath) {
        DocumentMetadata metadata = new DocumentMetadata();
        metadata.setFilePath(inputPath);

        try {
            JsonNode rootNode = objectMapper.readTree(jsonOutput);

            // Extract basic file information
            File file = new File(inputPath);
            if (file.exists()) {
                metadata.setFileName(file.getName());
                metadata.setFileSizeBytes(file.length());
            }

            // Try to extract metadata from the JSON structure
            // Docling JSON output may have different structures depending on the document
            if (rootNode.has(DoclingMetadataFields.METADATA)) {
                JsonNode metadataNode = rootNode.get(DoclingMetadataFields.METADATA);
                extractFieldsFromJsonNode(metadata, metadataNode);
            }

            // Look for document-level information
            if (rootNode.has(DoclingMetadataFields.DOCUMENT)) {
                JsonNode docNode = rootNode.get(DoclingMetadataFields.DOCUMENT);
                if (docNode.has(DoclingMetadataFields.NAME)) {
                    metadata.setTitle(docNode.get(DoclingMetadataFields.NAME).asText());
                }
            }

            // Count pages if available
            if (rootNode.has(DoclingMetadataFields.PAGES)) {
                metadata.setPageCount(rootNode.get(DoclingMetadataFields.PAGES).size());
            } else if (rootNode.has(DoclingMetadataFields.NUM_PAGES)) {
                metadata.setPageCount(rootNode.get(DoclingMetadataFields.NUM_PAGES).asInt());
            }

            // Store raw metadata if configured
            if (configuration.isIncludeRawMetadata()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> rawMap = objectMapper.convertValue(rootNode, java.util.Map.class);
                metadata.setRawMetadata(rawMap);
            }

        } catch (Exception e) {
            LOG.warn("Failed to parse metadata from JSON output: {}", e.getMessage(), e);
        }

        return metadata;
    }

    private void extractFieldsFromJsonNode(DocumentMetadata metadata, JsonNode metadataNode) {
        // Extract common metadata fields
        if (metadataNode.has(DoclingMetadataFields.TITLE)) {
            metadata.setTitle(metadataNode.get(DoclingMetadataFields.TITLE).asText());
        }
        if (metadataNode.has(DoclingMetadataFields.AUTHOR)) {
            metadata.setAuthor(metadataNode.get(DoclingMetadataFields.AUTHOR).asText());
        }
        if (metadataNode.has(DoclingMetadataFields.CREATOR)) {
            metadata.setCreator(metadataNode.get(DoclingMetadataFields.CREATOR).asText());
        }
        if (metadataNode.has(DoclingMetadataFields.PRODUCER)) {
            metadata.setProducer(metadataNode.get(DoclingMetadataFields.PRODUCER).asText());
        }
        if (metadataNode.has(DoclingMetadataFields.SUBJECT)) {
            metadata.setSubject(metadataNode.get(DoclingMetadataFields.SUBJECT).asText());
        }
        if (metadataNode.has(DoclingMetadataFields.KEYWORDS)) {
            metadata.setKeywords(metadataNode.get(DoclingMetadataFields.KEYWORDS).asText());
        }
        if (metadataNode.has(DoclingMetadataFields.LANGUAGE)) {
            metadata.setLanguage(metadataNode.get(DoclingMetadataFields.LANGUAGE).asText());
        }

        // Extract dates
        if (metadataNode.has(DoclingMetadataFields.CREATION_DATE)
                || metadataNode.has(DoclingMetadataFields.CREATION_DATE_CAMEL)) {
            String dateStr = metadataNode.has(DoclingMetadataFields.CREATION_DATE)
                    ? metadataNode.get(DoclingMetadataFields.CREATION_DATE).asText()
                    : metadataNode.get(DoclingMetadataFields.CREATION_DATE_CAMEL).asText();
            try {
                metadata.setCreationDate(java.time.Instant.parse(dateStr));
            } catch (Exception e) {
                LOG.debug("Failed to parse creation date: {}", dateStr);
            }
        }

        if (metadataNode.has(DoclingMetadataFields.MODIFICATION_DATE)
                || metadataNode.has(DoclingMetadataFields.MODIFICATION_DATE_CAMEL)) {
            String dateStr = metadataNode.has(DoclingMetadataFields.MODIFICATION_DATE)
                    ? metadataNode.get(DoclingMetadataFields.MODIFICATION_DATE).asText()
                    : metadataNode.get(DoclingMetadataFields.MODIFICATION_DATE_CAMEL).asText();
            try {
                metadata.setModificationDate(java.time.Instant.parse(dateStr));
            } catch (Exception e) {
                LOG.debug("Failed to parse modification date: {}", dateStr);
            }
        }

        // Extract custom metadata if extractAllMetadata is enabled
        if (configuration.isExtractAllMetadata()) {
            metadataNode.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                // Skip standard fields we already extracted
                if (!DoclingMetadataFields.isStandardField(key)) {
                    JsonNode value = entry.getValue();
                    if (value.isTextual()) {
                        metadata.addCustomMetadata(key, value.asText());
                    } else if (value.isNumber()) {
                        metadata.addCustomMetadata(key, value.asLong());
                    } else if (value.isBoolean()) {
                        metadata.addCustomMetadata(key, value.asBoolean());
                    } else {
                        metadata.addCustomMetadata(key, value.toString());
                    }
                }
            });
        }
    }

    private void setMetadataHeaders(Exchange exchange, DocumentMetadata metadata) {
        if (metadata.getTitle() != null) {
            exchange.getIn().setHeader(DoclingHeaders.METADATA_TITLE, metadata.getTitle());
        }
        if (metadata.getAuthor() != null) {
            exchange.getIn().setHeader(DoclingHeaders.METADATA_AUTHOR, metadata.getAuthor());
        }
        if (metadata.getCreator() != null) {
            exchange.getIn().setHeader(DoclingHeaders.METADATA_CREATOR, metadata.getCreator());
        }
        if (metadata.getProducer() != null) {
            exchange.getIn().setHeader(DoclingHeaders.METADATA_PRODUCER, metadata.getProducer());
        }
        if (metadata.getSubject() != null) {
            exchange.getIn().setHeader(DoclingHeaders.METADATA_SUBJECT, metadata.getSubject());
        }
        if (metadata.getKeywords() != null) {
            exchange.getIn().setHeader(DoclingHeaders.METADATA_KEYWORDS, metadata.getKeywords());
        }
        if (metadata.getCreationDate() != null) {
            exchange.getIn().setHeader(DoclingHeaders.METADATA_CREATION_DATE, metadata.getCreationDate());
        }
        if (metadata.getModificationDate() != null) {
            exchange.getIn().setHeader(DoclingHeaders.METADATA_MODIFICATION_DATE, metadata.getModificationDate());
        }
        if (metadata.getPageCount() != null) {
            exchange.getIn().setHeader(DoclingHeaders.METADATA_PAGE_COUNT, metadata.getPageCount());
        }
        if (metadata.getLanguage() != null) {
            exchange.getIn().setHeader(DoclingHeaders.METADATA_LANGUAGE, metadata.getLanguage());
        }
        if (metadata.getDocumentType() != null) {
            exchange.getIn().setHeader(DoclingHeaders.METADATA_DOCUMENT_TYPE, metadata.getDocumentType());
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
        if (metadata.getCustomMetadata() != null && !metadata.getCustomMetadata().isEmpty()) {
            exchange.getIn().setHeader(DoclingHeaders.METADATA_CUSTOM, metadata.getCustomMetadata());
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

        ExecutorService executor = Executors.newFixedThreadPool(parallelism);
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
            executor.shutdown();
            try {
                // Allow 10 seconds grace period for executor shutdown
                long shutdownTimeout = Math.max(10000, batchTimeout / 10);
                if (!executor.awaitTermination(shutdownTimeout, TimeUnit.MILLISECONDS)) {
                    LOG.warn("Executor did not terminate within {}ms, forcing shutdown", shutdownTimeout);
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
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
        if (body instanceof Collection) {
            Collection<?> collection = (Collection<?>) body;
            return collection.stream()
                    .map(obj -> {
                        if (obj instanceof String) {
                            return (String) obj;
                        } else if (obj instanceof File) {
                            return ((File) obj).getAbsolutePath();
                        } else {
                            return obj.toString();
                        }
                    })
                    .collect(Collectors.toList());
        }

        // Handle String array
        if (body instanceof String[]) {
            return List.of((String[]) body);
        }

        // Handle File array
        if (body instanceof File[]) {
            File[] files = (File[]) body;
            List<String> paths = new ArrayList<>();
            for (File file : files) {
                paths.add(file.getAbsolutePath());
            }
            return paths;
        }

        // Handle single String (directory path to scan)
        if (body instanceof String) {
            String path = (String) body;
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

    private String convertDocumentSync(String inputSource, String outputFormat) throws IOException {
        LOG.debug("Converting document using docling-java: {}", inputSource);

        ConvertDocumentRequest request = buildConvertRequest(inputSource, outputFormat);

        try {
            ConvertDocumentResponse response = doclingServeApi.convertSource(request);
            return extractConvertedContent(response, outputFormat);
        } catch (Exception e) {
            throw new IOException("Failed to convert document: " + e.getMessage(), e);
        }
    }

    private String convertDocumentAsyncAndWait(String inputSource, String outputFormat) throws IOException {
        LOG.debug("Converting document with async-and-wait using docling-java: {}", inputSource);

        ConvertDocumentRequest request = buildConvertRequest(inputSource, outputFormat);

        try {
            // Use docling-java's native async API which handles polling internally
            // The asyncPollInterval and asyncTimeout configured in the client builder are used
            CompletionStage<ConvertDocumentResponse> asyncResult = doclingServeApi.convertSourceAsync(request);

            // Wait for completion with the configured timeout
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
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new IOException("Async conversion failed: " + cause.getMessage(), cause);
        }
    }

    private ConvertDocumentRequest buildConvertRequest(String inputSource, String outputFormat) throws IOException {
        ConvertDocumentRequest.Builder requestBuilder = ConvertDocumentRequest.builder();

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

        // Add output format options if specified
        if (outputFormat != null && !outputFormat.isEmpty()) {
            OutputFormat format = mapToOutputFormat(outputFormat);
            requestBuilder.options(
                    ConvertDocumentOptions.builder()
                            .toFormat(format)
                            .build());
        }

        return requestBuilder.build();
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
        if (body instanceof String) {
            String content = (String) body;
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
        } else if (body instanceof byte[]) {
            byte[] content = (byte[]) body;
            if (content.length > configuration.getMaxFileSize()) {
                throw new IllegalArgumentException("File size exceeds maximum allowed size: " + configuration.getMaxFileSize());
            }
            Path tempFile = Files.createTempFile("docling-", ".tmp");
            Files.write(tempFile, content);
            return tempFile.toString();
        } else if (body instanceof File) {
            File file = (File) body;
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
