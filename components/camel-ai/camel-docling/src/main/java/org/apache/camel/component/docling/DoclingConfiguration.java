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

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

/**
 * Configuration for the Docling component.
 */
@Configurer
@UriParams
public class DoclingConfiguration implements Cloneable {

    @UriParam
    @Metadata(required = true, defaultValue = "CONVERT_TO_MARKDOWN", description = "The operation to perform",
              enums = "CONVERT_TO_MARKDOWN,CONVERT_TO_HTML,CONVERT_TO_JSON,EXTRACT_TEXT,EXTRACT_STRUCTURED_DATA,SUBMIT_ASYNC_CONVERSION,CHECK_CONVERSION_STATUS,BATCH_CONVERT_TO_MARKDOWN,BATCH_CONVERT_TO_HTML,BATCH_CONVERT_TO_JSON,BATCH_EXTRACT_TEXT,BATCH_EXTRACT_STRUCTURED_DATA,EXTRACT_METADATA")
    private DoclingOperations operation = DoclingOperations.CONVERT_TO_MARKDOWN;

    @UriParam(label = "advanced")
    @Metadata(description = "Path to Docling Python executable or command")
    private String doclingCommand = "docling";

    @UriParam(label = "advanced")
    @Metadata(description = "Working directory for Docling execution")
    private String workingDirectory;

    @UriParam
    @Metadata(description = "Enable OCR processing for scanned documents", defaultValue = "true")
    private boolean enableOCR = true;

    @UriParam
    @Metadata(description = "Language code for OCR processing", defaultValue = "en",
              enums = "en,es,fr,de,it,pt,ru,zh,ja,ko,ar,hi,th,vi,tr,pl,nl,sv,da,no,fi,cs,hu,el,he,fa,ur,bn,ta,te,ml,kn,gu,pa,or,as,ne,si,my,lo,km,ka,hy,eu,mt,is,ga,cy,gd,br,oc,ca,gl,ast,an,lad,la,eo,ia,ie,vo,jbo,tlh")
    private String ocrLanguage = "en";

    @UriParam
    @Metadata(description = "Output format for document conversion", defaultValue = "markdown",
              enums = "md,json,html,html_split_page,text,doctags")
    private String outputFormat = "markdown";

    @UriParam(label = "advanced")
    @Metadata(description = "Timeout for Docling process execution in milliseconds", defaultValue = "30000")
    private long processTimeout = 30000;

    @UriParam
    @Metadata(description = "Show layout information with bounding boxes", defaultValue = "false")
    private boolean includeLayoutInfo = false;

    @UriParam(label = "security")
    @Metadata(description = "Maximum file size in bytes for processing", defaultValue = "52428800")
    private long maxFileSize = 50 * 1024 * 1024; // 50MB

    @UriParam
    @Metadata(description = "Include the content of the output file in the exchange body and delete the output file",
              defaultValue = "false")
    private boolean contentInBody = false;

    @UriParam
    @Metadata(description = "Use docling-serve API instead of CLI command", defaultValue = "false")
    private boolean useDoclingServe = false;

    @UriParam
    @Metadata(description = "Docling-serve API URL (e.g., http://localhost:5001)", defaultValue = "http://localhost:5001")
    private String doclingServeUrl = "http://localhost:5001";

    @UriParam(label = "security")
    @Metadata(description = "Authentication token for docling-serve API (Bearer token or API key)", secret = true)
    private String authenticationToken;

    @UriParam(label = "security")
    @Metadata(description = "Authentication scheme (BEARER, API_KEY, NONE)", defaultValue = "NONE",
              enums = "BEARER,API_KEY,NONE")
    private AuthenticationScheme authenticationScheme = AuthenticationScheme.NONE;

    @UriParam(label = "security")
    @Metadata(description = "Header name for API key authentication", defaultValue = "X-API-Key")
    private String apiKeyHeader = "X-API-Key";

    @UriParam(label = "advanced")
    @Metadata(description = "Use asynchronous conversion mode (docling-serve API only)", defaultValue = "false")
    private boolean useAsyncMode = false;

    @UriParam(label = "advanced")
    @Metadata(description = "Polling interval for async conversion status in milliseconds", defaultValue = "2000")
    private long asyncPollInterval = 2000;

    @UriParam(label = "advanced")
    @Metadata(description = "Maximum time to wait for async conversion completion in milliseconds", defaultValue = "300000")
    private long asyncTimeout = 300000; // 5 minutes

    @UriParam(label = "batch")
    @Metadata(description = "Maximum number of documents to process in a single batch (batch operations only)",
              defaultValue = "10")
    private int batchSize = 10;

    @UriParam(label = "batch")
    @Metadata(description = "Maximum time to wait for batch completion in milliseconds", defaultValue = "300000")
    private long batchTimeout = 300000;

    @UriParam(label = "batch")
    @Metadata(description = "Number of parallel threads for batch processing", defaultValue = "4")
    private int batchParallelism = 4;

    @UriParam(label = "batch")
    @Metadata(description = "Fail entire batch on first error (true) or continue processing remaining documents (false)",
              defaultValue = "true")
    private boolean batchFailOnFirstError = true;

    @UriParam(label = "batch")
    @Metadata(description = "Split batch results into individual exchanges (one per document) instead of single BatchProcessingResults",
              defaultValue = "false")
    private boolean splitBatchResults = false;

    @UriParam(label = "metadata")
    @Metadata(description = "Include metadata in message headers when extracting metadata", defaultValue = "true")
    private boolean includeMetadataInHeaders = true;

    @UriParam(label = "metadata")
    @Metadata(description = "Extract all available metadata fields including custom/raw fields", defaultValue = "false")
    private boolean extractAllMetadata = false;

    @UriParam(label = "metadata")
    @Metadata(description = "Include raw metadata as returned by the parser", defaultValue = "false")
    private boolean includeRawMetadata = false;

    public DoclingOperations getOperation() {
        return operation;
    }

    public void setOperation(DoclingOperations operation) {
        this.operation = operation;
    }

    public String getDoclingCommand() {
        return doclingCommand;
    }

    public void setDoclingCommand(String doclingCommand) {
        this.doclingCommand = doclingCommand;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    public boolean isEnableOCR() {
        return enableOCR;
    }

    public void setEnableOCR(boolean enableOCR) {
        this.enableOCR = enableOCR;
    }

    public String getOcrLanguage() {
        return ocrLanguage;
    }

    public void setOcrLanguage(String ocrLanguage) {
        this.ocrLanguage = ocrLanguage;
    }

    public String getOutputFormat() {
        return outputFormat;
    }

    public void setOutputFormat(String outputFormat) {
        this.outputFormat = outputFormat;
    }

    public long getProcessTimeout() {
        return processTimeout;
    }

    public void setProcessTimeout(long processTimeout) {
        this.processTimeout = processTimeout;
    }

    public boolean isIncludeLayoutInfo() {
        return includeLayoutInfo;
    }

    public void setIncludeLayoutInfo(boolean includeLayoutInfo) {
        this.includeLayoutInfo = includeLayoutInfo;
    }

    public long getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(long maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public boolean isContentInBody() {
        return contentInBody;
    }

    public void setContentInBody(boolean contentInBody) {
        this.contentInBody = contentInBody;
    }

    public boolean isUseDoclingServe() {
        return useDoclingServe;
    }

    public void setUseDoclingServe(boolean useDoclingServe) {
        this.useDoclingServe = useDoclingServe;
    }

    public String getDoclingServeUrl() {
        return doclingServeUrl;
    }

    public void setDoclingServeUrl(String doclingServeUrl) {
        this.doclingServeUrl = doclingServeUrl;
    }

    public String getAuthenticationToken() {
        return authenticationToken;
    }

    public void setAuthenticationToken(String authenticationToken) {
        this.authenticationToken = authenticationToken;
    }

    public AuthenticationScheme getAuthenticationScheme() {
        return authenticationScheme;
    }

    public void setAuthenticationScheme(AuthenticationScheme authenticationScheme) {
        this.authenticationScheme = authenticationScheme;
    }

    public String getApiKeyHeader() {
        return apiKeyHeader;
    }

    public void setApiKeyHeader(String apiKeyHeader) {
        this.apiKeyHeader = apiKeyHeader;
    }

    public boolean isUseAsyncMode() {
        return useAsyncMode;
    }

    public void setUseAsyncMode(boolean useAsyncMode) {
        this.useAsyncMode = useAsyncMode;
    }

    public long getAsyncPollInterval() {
        return asyncPollInterval;
    }

    public void setAsyncPollInterval(long asyncPollInterval) {
        this.asyncPollInterval = asyncPollInterval;
    }

    public long getAsyncTimeout() {
        return asyncTimeout;
    }

    public void setAsyncTimeout(long asyncTimeout) {
        this.asyncTimeout = asyncTimeout;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public long getBatchTimeout() {
        return batchTimeout;
    }

    public void setBatchTimeout(long batchTimeout) {
        this.batchTimeout = batchTimeout;
    }

    public int getBatchParallelism() {
        return batchParallelism;
    }

    public void setBatchParallelism(int batchParallelism) {
        this.batchParallelism = batchParallelism;
    }

    public boolean isBatchFailOnFirstError() {
        return batchFailOnFirstError;
    }

    public void setBatchFailOnFirstError(boolean batchFailOnFirstError) {
        this.batchFailOnFirstError = batchFailOnFirstError;
    }

    public boolean isSplitBatchResults() {
        return splitBatchResults;
    }

    public void setSplitBatchResults(boolean splitBatchResults) {
        this.splitBatchResults = splitBatchResults;
    }

    public boolean isIncludeMetadataInHeaders() {
        return includeMetadataInHeaders;
    }

    public void setIncludeMetadataInHeaders(boolean includeMetadataInHeaders) {
        this.includeMetadataInHeaders = includeMetadataInHeaders;
    }

    public boolean isExtractAllMetadata() {
        return extractAllMetadata;
    }

    public void setExtractAllMetadata(boolean extractAllMetadata) {
        this.extractAllMetadata = extractAllMetadata;
    }

    public boolean isIncludeRawMetadata() {
        return includeRawMetadata;
    }

    public void setIncludeRawMetadata(boolean includeRawMetadata) {
        this.includeRawMetadata = includeRawMetadata;
    }

    public DoclingConfiguration copy() {
        try {
            return (DoclingConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

}
