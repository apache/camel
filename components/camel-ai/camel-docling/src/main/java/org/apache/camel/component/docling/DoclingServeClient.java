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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client for interacting with Docling-Serve API.
 */
public class DoclingServeClient {

    private static final Logger LOG = LoggerFactory.getLogger(DoclingServeClient.class);
    private static final String DEFAULT_CONVERT_ENDPOINT = "/v1/convert/source";
    private static final String DEFAULT_ASYNC_CONVERT_ENDPOINT = "/v1/convert/source/async";

    private final String baseUrl;
    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;
    private final PoolingHttpClientConnectionManager connectionManager;
    private final AuthenticationScheme authenticationScheme;
    private final String authenticationToken;
    private final String apiKeyHeader;
    private final String convertEndpoint;
    private final long asyncPollInterval;
    private final long asyncTimeout;

    public DoclingServeClient(String baseUrl) {
        this(baseUrl, AuthenticationScheme.NONE, null, "X-API-Key", DEFAULT_CONVERT_ENDPOINT, 2000, 300000,
             20, 10, 30000, 60000, 30000, -1, 2000, true, 60000);
    }

    public DoclingServeClient(
                              String baseUrl, AuthenticationScheme authenticationScheme, String authenticationToken,
                              String apiKeyHeader) {
        this(baseUrl, authenticationScheme, authenticationToken, apiKeyHeader, DEFAULT_CONVERT_ENDPOINT, 2000, 300000,
             20, 10, 30000, 60000, 30000, -1, 2000, true, 60000);
    }

    public DoclingServeClient(
                              String baseUrl, AuthenticationScheme authenticationScheme, String authenticationToken,
                              String apiKeyHeader, String convertEndpoint) {
        this(baseUrl, authenticationScheme, authenticationToken, apiKeyHeader, convertEndpoint, 2000, 300000,
             20, 10, 30000, 60000, 30000, -1, 2000, true, 60000);
    }

    public DoclingServeClient(
                              String baseUrl, AuthenticationScheme authenticationScheme, String authenticationToken,
                              String apiKeyHeader, String convertEndpoint, long asyncPollInterval, long asyncTimeout) {
        this(baseUrl, authenticationScheme, authenticationToken, apiKeyHeader, convertEndpoint, asyncPollInterval,
             asyncTimeout, 20, 10, 30000, 60000, 30000, -1, 2000, true, 60000);
    }

    public DoclingServeClient(
                              String baseUrl, AuthenticationScheme authenticationScheme, String authenticationToken,
                              String apiKeyHeader, String convertEndpoint, long asyncPollInterval, long asyncTimeout,
                              int maxTotalConnections, int maxConnectionsPerRoute, int connectionTimeout,
                              int socketTimeout, int connectionRequestTimeout, long connectionTimeToLive,
                              int validateAfterInactivity, boolean evictIdleConnections, long maxIdleTime) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.objectMapper = new ObjectMapper();
        this.authenticationScheme = authenticationScheme != null ? authenticationScheme : AuthenticationScheme.NONE;
        this.authenticationToken = authenticationToken;
        this.apiKeyHeader = apiKeyHeader != null ? apiKeyHeader : "X-API-Key";
        this.convertEndpoint = convertEndpoint != null ? convertEndpoint : DEFAULT_CONVERT_ENDPOINT;
        this.asyncPollInterval = asyncPollInterval;
        this.asyncTimeout = asyncTimeout;

        // Build connection pool and HTTP client with custom configuration
        this.connectionManager = buildConnectionManager(maxTotalConnections, maxConnectionsPerRoute, connectionTimeout,
                socketTimeout, connectionTimeToLive, validateAfterInactivity);
        this.httpClient = buildHttpClient(connectionManager, connectionRequestTimeout, socketTimeout,
                evictIdleConnections, maxIdleTime);

        LOG.info(
                "DoclingServeClient initialized with connection pool: maxTotal={}, maxPerRoute={}, connTimeout={}ms, socketTimeout={}ms",
                maxTotalConnections, maxConnectionsPerRoute, connectionTimeout, socketTimeout);
    }

    /**
     * Build a configured connection manager with pooling support.
     */
    private PoolingHttpClientConnectionManager buildConnectionManager(
            int maxTotalConnections, int maxConnectionsPerRoute, int connectionTimeout,
            int socketTimeout, long connectionTimeToLive, int validateAfterInactivity) {

        // Configure socket settings
        SocketConfig socketConfig = SocketConfig.custom()
                .setSoTimeout(Timeout.ofMilliseconds(socketTimeout))
                .build();

        // Configure connection settings
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(connectionTimeout))
                .setSocketTimeout(Timeout.ofMilliseconds(socketTimeout))
                .setValidateAfterInactivity(TimeValue.ofMilliseconds(validateAfterInactivity))
                .setTimeToLive(connectionTimeToLive > 0
                        ? TimeValue.ofMilliseconds(connectionTimeToLive) : TimeValue.NEG_ONE_MILLISECOND)
                .build();

        // Build the pooling connection manager
        PoolingHttpClientConnectionManager connManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(maxTotalConnections)
                .setMaxConnPerRoute(maxConnectionsPerRoute)
                .setDefaultSocketConfig(socketConfig)
                .setDefaultConnectionConfig(connectionConfig)
                .build();

        LOG.debug("Connection manager configured: maxTotal={}, maxPerRoute={}, validateAfterInactivity={}ms, ttl={}ms",
                maxTotalConnections, maxConnectionsPerRoute, validateAfterInactivity,
                connectionTimeToLive > 0 ? connectionTimeToLive : "infinite");

        return connManager;
    }

    /**
     * Build an HTTP client with the configured connection manager and request settings.
     */
    private CloseableHttpClient buildHttpClient(
            PoolingHttpClientConnectionManager connectionManager, int connectionRequestTimeout,
            int socketTimeout, boolean evictIdleConnections, long maxIdleTime) {

        // Configure request settings
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(connectionRequestTimeout))
                .setResponseTimeout(Timeout.ofMilliseconds(socketTimeout))
                .build();

        // Build the HTTP client
        org.apache.hc.client5.http.impl.classic.HttpClientBuilder clientBuilder = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig);

        // Enable idle connection eviction if configured
        if (evictIdleConnections) {
            clientBuilder.evictIdleConnections(TimeValue.ofMilliseconds(maxIdleTime));
            LOG.debug("Idle connection eviction enabled: maxIdleTime={}ms", maxIdleTime);
        }

        CloseableHttpClient client = clientBuilder.build();

        LOG.debug("HTTP client configured: connectionRequestTimeout={}ms, socketTimeout={}ms",
                connectionRequestTimeout, socketTimeout);

        return client;
    }

    /**
     * Convert a document using the docling-serve API.
     *
     * @param  inputSource  File path or URL to the document
     * @param  outputFormat Output format (md, json, html, text)
     * @return              Converted document content
     * @throws IOException  If the API call fails
     */
    public String convertDocument(String inputSource, String outputFormat) throws IOException {
        LOG.debug("Converting document using docling-serve API: {}", inputSource);

        // Check if input is a URL or file path
        if (inputSource.startsWith("http://") || inputSource.startsWith("https://")) {
            return convertFromUrl(inputSource, outputFormat);
        } else {
            return convertFromFile(inputSource, outputFormat);
        }
    }

    private String convertFromUrl(String url, String outputFormat) throws IOException {
        Map<String, Object> requestBody = new HashMap<>();
        Map<String, String> source = new HashMap<>();
        source.put("kind", "http");
        source.put("url", url);
        requestBody.put("sources", Collections.singletonList(source));

        // Add output format if specified
        if (outputFormat != null && !outputFormat.isEmpty()) {
            Map<String, Object> options = new HashMap<>();
            options.put("to_formats", Collections.singletonList(mapOutputFormat(outputFormat)));
            requestBody.put("options", options);
        }

        String jsonRequest = objectMapper.writeValueAsString(requestBody);
        LOG.debug("Request body: {}", jsonRequest);

        HttpPost httpPost = new HttpPost(baseUrl + convertEndpoint);
        httpPost.setEntity(new StringEntity(jsonRequest, ContentType.APPLICATION_JSON));
        httpPost.setHeader("Accept", "application/json");
        applyAuthentication(httpPost);

        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
            int statusCode = response.getCode();
            String responseBody;
            try {
                responseBody = EntityUtils.toString(response.getEntity());
            } catch (org.apache.hc.core5.http.ParseException e) {
                throw new IOException("Failed to parse response from docling-serve API", e);
            }

            if (statusCode >= 200 && statusCode < 300) {
                return extractConvertedContent(responseBody, outputFormat);
            } else {
                throw new IOException(
                        "Docling-serve API request failed with status " + statusCode + ": " + responseBody);
            }
        }
    }

    private String convertFromFile(String filePath, String outputFormat) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IOException("File not found: " + filePath);
        }

        // Read file and encode as base64
        byte[] fileBytes = Files.readAllBytes(file.toPath());
        String base64Content = Base64.getEncoder().encodeToString(fileBytes);

        // Build request body with base64-encoded file
        Map<String, Object> requestBody = new HashMap<>();
        Map<String, String> source = new HashMap<>();
        source.put("kind", "file");
        source.put("base64_string", base64Content);
        source.put("filename", file.getName());
        requestBody.put("sources", Collections.singletonList(source));

        // Add output format if specified
        if (outputFormat != null && !outputFormat.isEmpty()) {
            Map<String, Object> options = new HashMap<>();
            options.put("to_formats", Collections.singletonList(mapOutputFormat(outputFormat)));
            requestBody.put("options", options);
        }

        String jsonRequest = objectMapper.writeValueAsString(requestBody);
        LOG.debug("Request body: {}", jsonRequest);

        HttpPost httpPost = new HttpPost(baseUrl + convertEndpoint);
        httpPost.setEntity(new StringEntity(jsonRequest, ContentType.APPLICATION_JSON));
        httpPost.setHeader("Accept", "application/json");
        applyAuthentication(httpPost);

        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
            int statusCode = response.getCode();
            String responseBody;
            try {
                responseBody = EntityUtils.toString(response.getEntity());
            } catch (org.apache.hc.core5.http.ParseException e) {
                throw new IOException("Failed to parse response from docling-serve API", e);
            }

            if (statusCode >= 200 && statusCode < 300) {
                return extractConvertedContent(responseBody, outputFormat);
            } else {
                throw new IOException(
                        "Docling-serve API request failed with status " + statusCode + ": " + responseBody);
            }
        }
    }

    private String extractConvertedContent(String responseBody, String outputFormat) throws IOException {
        try {
            JsonNode rootNode = objectMapper.readTree(responseBody);

            // The response structure may vary, so we'll try to extract the content
            // This is a simplified implementation - adjust based on actual API response
            if (rootNode.has("documents") && rootNode.get("documents").isArray()
                    && rootNode.get("documents").size() > 0) {
                JsonNode firstDoc = rootNode.get("documents").get(0);

                // Try different possible response formats
                if (firstDoc.has("content")) {
                    return firstDoc.get("content").asText();
                } else if (firstDoc.has("markdown")) {
                    return firstDoc.get("markdown").asText();
                } else if (firstDoc.has("text")) {
                    return firstDoc.get("text").asText();
                } else {
                    // Return the entire document as JSON string
                    return objectMapper.writeValueAsString(firstDoc);
                }
            } else if (rootNode.has("content")) {
                return rootNode.get("content").asText();
            } else {
                // Return the entire response as a formatted JSON string
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode);
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse JSON response, returning raw response", e);
            return responseBody;
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

    /**
     * Apply authentication headers to the HTTP request based on the configured authentication scheme.
     *
     * @param httpPost The HTTP POST request to add authentication to
     */
    private void applyAuthentication(HttpPost httpPost) {
        if (authenticationScheme == null || authenticationScheme == AuthenticationScheme.NONE) {
            return;
        }

        if (authenticationToken == null || authenticationToken.isEmpty()) {
            LOG.warn("Authentication scheme is set to {} but no authentication token provided", authenticationScheme);
            return;
        }

        switch (authenticationScheme) {
            case BEARER:
                httpPost.setHeader("Authorization", "Bearer " + authenticationToken);
                LOG.debug("Applied Bearer token authentication");
                break;
            case API_KEY:
                httpPost.setHeader(apiKeyHeader, authenticationToken);
                LOG.debug("Applied API Key authentication with header: {}", apiKeyHeader);
                break;
            default:
                LOG.warn("Unknown authentication scheme: {}", authenticationScheme);
        }
    }

    /**
     * Apply authentication headers to the HTTP GET request based on the configured authentication scheme.
     *
     * @param httpGet The HTTP GET request to add authentication to
     */
    private void applyAuthenticationGet(HttpGet httpGet) {
        if (authenticationScheme == null || authenticationScheme == AuthenticationScheme.NONE) {
            return;
        }

        if (authenticationToken == null || authenticationToken.isEmpty()) {
            LOG.warn("Authentication scheme is set to {} but no authentication token provided", authenticationScheme);
            return;
        }

        switch (authenticationScheme) {
            case BEARER:
                httpGet.setHeader("Authorization", "Bearer " + authenticationToken);
                LOG.debug("Applied Bearer token authentication");
                break;
            case API_KEY:
                httpGet.setHeader(apiKeyHeader, authenticationToken);
                LOG.debug("Applied API Key authentication with header: {}", apiKeyHeader);
                break;
            default:
                LOG.warn("Unknown authentication scheme: {}", authenticationScheme);
        }
    }

    /**
     * Convert a document using the docling-serve async API and return the task ID.
     *
     * @param  inputSource  File path or URL to the document
     * @param  outputFormat Output format (md, json, html, text)
     * @return              Task ID for the async conversion
     * @throws IOException  If the API call fails
     */
    public String convertDocumentAsync(String inputSource, String outputFormat) throws IOException {
        LOG.debug("Starting async document conversion using docling-serve API: {}", inputSource);

        String asyncEndpoint = convertEndpoint.replace("/v1/convert/source", DEFAULT_ASYNC_CONVERT_ENDPOINT);

        // Check if input is a URL or file path
        Map<String, Object> requestBody = buildRequestBody(inputSource, outputFormat);

        String jsonRequest = objectMapper.writeValueAsString(requestBody);
        LOG.debug("Async request body: {}", jsonRequest);

        HttpPost httpPost = new HttpPost(baseUrl + asyncEndpoint);
        httpPost.setEntity(new StringEntity(jsonRequest, ContentType.APPLICATION_JSON));
        httpPost.setHeader("Accept", "application/json");
        applyAuthentication(httpPost);

        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
            int statusCode = response.getCode();
            String responseBody;
            try {
                responseBody = EntityUtils.toString(response.getEntity());
            } catch (org.apache.hc.core5.http.ParseException e) {
                throw new IOException("Failed to parse response from docling-serve API", e);
            }

            if (statusCode >= 200 && statusCode < 300) {
                // Extract task ID from response
                JsonNode rootNode = objectMapper.readTree(responseBody);
                if (rootNode.has("task_id")) {
                    return rootNode.get("task_id").asText();
                } else if (rootNode.has("id")) {
                    return rootNode.get("id").asText();
                } else {
                    throw new IOException("No task ID found in async conversion response: " + responseBody);
                }
            } else {
                throw new IOException(
                        "Docling-serve async API request failed with status " + statusCode + ": " + responseBody);
            }
        }
    }

    /**
     * Check the status of an async conversion task.
     *
     * @param  taskId      The task ID returned from convertDocumentAsync
     * @return             ConversionStatus object with current status
     * @throws IOException If the API call fails
     */
    public ConversionStatus checkConversionStatus(String taskId) throws IOException {
        LOG.debug("Checking status for task: {}", taskId);

        String statusEndpoint = "/v1/status/poll/" + taskId;
        HttpGet httpGet = new HttpGet(baseUrl + statusEndpoint);
        httpGet.setHeader("Accept", "application/json");
        applyAuthenticationGet(httpGet);

        try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
            int statusCode = response.getCode();
            String responseBody;
            try {
                responseBody = EntityUtils.toString(response.getEntity());
            } catch (org.apache.hc.core5.http.ParseException e) {
                throw new IOException("Failed to parse response from docling-serve API", e);
            }

            if (statusCode >= 200 && statusCode < 300) {
                JsonNode rootNode = objectMapper.readTree(responseBody);
                return parseConversionStatus(taskId, rootNode);
            } else {
                throw new IOException(
                        "Failed to check task status. Status code: " + statusCode + ", Response: " + responseBody);
            }
        }
    }

    /**
     * Convert a document asynchronously and wait for completion by polling.
     *
     * @param  inputSource  File path or URL to the document
     * @param  outputFormat Output format (md, json, html, text)
     * @return              Converted document content
     * @throws IOException  If the API call fails or timeout occurs
     */
    public String convertDocumentAsyncAndWait(String inputSource, String outputFormat) throws IOException {
        String taskId = convertDocumentAsync(inputSource, outputFormat);
        LOG.debug("Started async conversion with task ID: {}", taskId);

        long startTime = System.currentTimeMillis();
        long deadline = startTime + asyncTimeout;

        while (System.currentTimeMillis() < deadline) {
            ConversionStatus status = checkConversionStatus(taskId);
            LOG.debug("Task {} status: {}", taskId, status.getStatus());

            if (status.isCompleted()) {
                LOG.debug("Task {} completed successfully", taskId);
                return status.getResult();
            } else if (status.isFailed()) {
                throw new IOException("Async conversion failed: " + status.getErrorMessage());
            }

            // Wait before next poll
            try {
                Thread.sleep(asyncPollInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Async conversion interrupted", e);
            }
        }

        throw new IOException(
                "Async conversion timed out after " + asyncTimeout + "ms for task: " + taskId);
    }

    private Map<String, Object> buildRequestBody(String inputSource, String outputFormat) {
        Map<String, Object> requestBody = new HashMap<>();

        if (inputSource.startsWith("http://") || inputSource.startsWith("https://")) {
            Map<String, String> source = new HashMap<>();
            source.put("kind", "http");
            source.put("url", inputSource);
            requestBody.put("sources", Collections.singletonList(source));
        } else {
            try {
                File file = new File(inputSource);
                byte[] fileBytes = Files.readAllBytes(file.toPath());
                String base64Content = Base64.getEncoder().encodeToString(fileBytes);

                Map<String, String> source = new HashMap<>();
                source.put("kind", "file");
                source.put("base64_string", base64Content);
                source.put("filename", file.getName());
                requestBody.put("sources", Collections.singletonList(source));
            } catch (IOException e) {
                throw new RuntimeException("Failed to read file: " + inputSource, e);
            }
        }

        // Add output format if specified
        if (outputFormat != null && !outputFormat.isEmpty()) {
            Map<String, Object> options = new HashMap<>();
            options.put("to_formats", Collections.singletonList(mapOutputFormat(outputFormat)));
            requestBody.put("options", options);
        }

        return requestBody;
    }

    private ConversionStatus parseConversionStatus(String taskId, JsonNode statusNode) {
        // Docling-serve uses "task_status" field with values: pending, started, success, failure
        String statusStr = statusNode.has("task_status") ? statusNode.get("task_status").asText() : "unknown";
        ConversionStatus.Status status;

        switch (statusStr.toLowerCase()) {
            case "pending":
                status = ConversionStatus.Status.PENDING;
                break;
            case "started":
            case "in_progress":
            case "running":
            case "processing":
                status = ConversionStatus.Status.IN_PROGRESS;
                break;
            case "success":
            case "completed":
                status = ConversionStatus.Status.COMPLETED;
                break;
            case "failure":
            case "failed":
            case "error":
                status = ConversionStatus.Status.FAILED;
                break;
            default:
                LOG.warn("Unknown task status: {}", statusStr);
                status = ConversionStatus.Status.UNKNOWN;
        }

        String result = null;
        String errorMessage = null;

        // For completed tasks, fetch the result
        if (status == ConversionStatus.Status.COMPLETED) {
            try {
                result = fetchTaskResult(taskId);
            } catch (IOException e) {
                LOG.warn("Failed to fetch result for completed task: {}", taskId, e);
            }
        }

        // For failed tasks, extract error message
        if (status == ConversionStatus.Status.FAILED) {
            if (statusNode.has("task_meta") && statusNode.get("task_meta").has("error")) {
                errorMessage = statusNode.get("task_meta").get("error").asText();
            } else if (statusNode.has("error")) {
                errorMessage = statusNode.get("error").asText();
            } else {
                errorMessage = "Task failed without error message";
            }
        }

        Integer progress = statusNode.has("task_position") ? statusNode.get("task_position").asInt() : null;

        return new ConversionStatus(taskId, status, result, errorMessage, progress);
    }

    /**
     * Fetch the result of a completed async conversion task.
     *
     * @param  taskId      The task ID
     * @return             Converted document content
     * @throws IOException If the API call fails
     */
    private String fetchTaskResult(String taskId) throws IOException {
        LOG.debug("Fetching result for task: {}", taskId);

        String resultEndpoint = "/v1/result/" + taskId;
        HttpGet httpGet = new HttpGet(baseUrl + resultEndpoint);
        httpGet.setHeader("Accept", "application/json");
        applyAuthenticationGet(httpGet);

        try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
            int statusCode = response.getCode();
            String responseBody;
            try {
                responseBody = EntityUtils.toString(response.getEntity());
            } catch (org.apache.hc.core5.http.ParseException e) {
                throw new IOException("Failed to parse response from docling-serve API", e);
            }

            if (statusCode >= 200 && statusCode < 300) {
                return extractConvertedContent(responseBody, null);
            } else {
                throw new IOException(
                        "Failed to fetch task result. Status code: " + statusCode + ", Response: " + responseBody);
            }
        }
    }

    /**
     * Convert multiple documents in batch using parallel processing.
     *
     * @param  inputSources     List of file paths or URLs to documents
     * @param  outputFormat     Output format (md, json, html, text)
     * @param  batchSize        Maximum number of documents in this batch
     * @param  parallelism      Number of parallel threads to use
     * @param  failOnFirstError Whether to fail entire batch on first error
     * @param  useAsync         Whether to use async mode for individual conversions
     * @param  batchTimeout     Maximum time to wait for batch completion in milliseconds
     * @return                  BatchProcessingResults containing all conversion results
     */
    public BatchProcessingResults convertDocumentsBatch(
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
                            converted = convertDocument(inputSource, outputFormat);
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

    public void close() throws IOException {
        if (httpClient != null) {
            try {
                httpClient.close();
                LOG.debug("HTTP client closed successfully");
            } catch (IOException e) {
                LOG.warn("Error closing HTTP client", e);
                throw e;
            }
        }
        if (connectionManager != null) {
            connectionManager.close();
            LOG.debug("Connection manager closed successfully");
        }
    }

    /**
     * Get connection pool statistics for monitoring.
     *
     * @return Connection pool statistics as a formatted string
     */
    public String getPoolStats() {
        if (connectionManager != null) {
            var stats = connectionManager.getTotalStats();
            return String.format("ConnectionPool[available=%d, leased=%d, pending=%d, max=%d]",
                    stats.getAvailable(), stats.getLeased(), stats.getPending(), stats.getMax());
        }
        return "ConnectionPool[not initialized]";
    }
}
