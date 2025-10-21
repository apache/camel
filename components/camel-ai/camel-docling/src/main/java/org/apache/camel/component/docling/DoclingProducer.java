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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
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
    private DoclingServeClient doclingServeClient;

    public DoclingProducer(DoclingEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
        this.configuration = endpoint.getConfiguration();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (configuration.isUseDoclingServe()) {
            doclingServeClient = new DoclingServeClient(
                    configuration.getDoclingServeUrl(),
                    configuration.getAuthenticationScheme(),
                    configuration.getAuthenticationToken(),
                    configuration.getApiKeyHeader(),
                    configuration.getConvertEndpoint(),
                    configuration.getAsyncPollInterval(),
                    configuration.getAsyncTimeout(),
                    configuration.getMaxTotalConnections(),
                    configuration.getMaxConnectionsPerRoute(),
                    configuration.getConnectionTimeout(),
                    configuration.getSocketTimeout(),
                    configuration.getConnectionRequestTimeout(),
                    configuration.getConnectionTimeToLive(),
                    configuration.getValidateAfterInactivity(),
                    configuration.isEvictIdleConnections(),
                    configuration.getMaxIdleTime());
            LOG.info("DoclingProducer configured to use docling-serve API at: {}{} with authentication: {} (async mode: {})",
                    configuration.getDoclingServeUrl(),
                    configuration.getConvertEndpoint(),
                    configuration.getAuthenticationScheme(),
                    configuration.isUseAsyncMode());
            LOG.debug("Connection pool stats: {}", doclingServeClient.getPoolStats());
        } else {
            LOG.info("DoclingProducer configured to use docling CLI command");
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (doclingServeClient != null) {
            LOG.debug("Shutting down DoclingServeClient. Final pool stats: {}", doclingServeClient.getPoolStats());
            doclingServeClient.close();
            doclingServeClient = null;
            LOG.info("DoclingServeClient closed successfully");
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

        // Submit async conversion and get task ID
        String taskId = doclingServeClient.convertDocumentAsync(inputPath, outputFormat);

        LOG.debug("Async conversion submitted with task ID: {}", taskId);

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

        // Check conversion status
        ConversionStatus status = doclingServeClient.checkConversionStatus(taskId);

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

    private String convertUsingDoclingServe(String inputPath, String outputFormat) throws Exception {
        return convertUsingDoclingServe(inputPath, outputFormat, null);
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
            return doclingServeClient.convertDocumentAsyncAndWait(inputPath, outputFormat);
        } else {
            LOG.debug("Using sync mode for conversion");
            return doclingServeClient.convertDocument(inputPath, outputFormat);
        }
    }

    private String getInputPath(Exchange exchange) throws InvalidPayloadException, IOException {
        String inputPath = exchange.getIn().getHeader(DoclingHeaders.INPUT_FILE_PATH, String.class);

        if (inputPath != null) {
            validateFileSize(inputPath);
            return inputPath;
        }

        Object body = exchange.getIn().getBody();
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
        try {
            if (Files.exists(directory)) {
                Files.walk(directory)
                        .sorted((a, b) -> b.compareTo(a)) // Delete files before directories
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                LOG.warn("Failed to delete temporary file: {}", path, e);
                            }
                        });
            }
        } catch (IOException e) {
            LOG.warn("Failed to clean up temporary directory: {}", directory, e);
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
