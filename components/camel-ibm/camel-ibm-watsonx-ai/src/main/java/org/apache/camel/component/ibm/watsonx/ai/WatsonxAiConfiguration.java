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
package org.apache.camel.component.ibm.watsonx.ai;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

/**
 * Configuration for IBM watsonx.ai component.
 */
@UriParams
public class WatsonxAiConfiguration implements Cloneable {

    // Authentication
    @UriParam(label = "security", secret = true, description = "IBM Cloud API key for authentication")
    @Metadata(required = true)
    private String apiKey;

    // Connection
    @UriParam(label = "common", description = "The watsonx.ai base URL (e.g., https://us-south.ml.cloud.ibm.com)")
    @Metadata(required = true)
    private String baseUrl;

    @UriParam(label = "common",
              description = "The watsonx.ai WX platform URL for tool operations (e.g., https://api.dataplatform.cloud.ibm.com/wx)")
    private String wxUrl;

    // Project/Space Context
    @UriParam(label = "common", description = "IBM Cloud project ID")
    private String projectId;

    @UriParam(label = "common", description = "IBM Cloud deployment space ID (alternative to projectId)")
    private String spaceId;

    // Model
    @UriParam(label = "producer", description = "Foundation model ID (e.g., ibm/granite-13b-instruct-v2)")
    private String modelId;

    @UriParam(label = "producer", description = "Deployed model ID (for deployment operations)")
    private String deploymentId;

    // Operation
    @UriParam(label = "producer", description = "The operation to perform")
    private WatsonxAiOperations operation;

    // Text Generation Parameters
    @UriParam(label = "producer", description = "Temperature for randomness (0.0 to 2.0)")
    private Double temperature;

    @UriParam(label = "producer", description = "Maximum new tokens to generate")
    private Integer maxNewTokens;

    @UriParam(label = "producer", description = "Top P (nucleus sampling)")
    private Double topP;

    @UriParam(label = "producer", description = "Top K (top-k sampling)")
    private Integer topK;

    @UriParam(label = "producer", description = "Repetition penalty")
    private Double repetitionPenalty;

    // Chat Parameters
    @UriParam(label = "producer", description = "Maximum completion tokens for chat")
    private Integer maxCompletionTokens;

    @UriParam(label = "producer", description = "Frequency penalty for chat")
    private Double frequencyPenalty;

    @UriParam(label = "producer", description = "Presence penalty for chat")
    private Double presencePenalty;

    // Embedding Parameters
    @UriParam(label = "producer",
              description = "Maximum number of tokens accepted per input for embeddings. Truncates from the end if exceeded.")
    private Integer truncateInputTokens;

    // Rerank Parameters
    @UriParam(label = "producer", description = "Number of top results to return for reranking")
    private Integer rerankTopN;

    @UriParam(label = "producer", description = "Whether to return documents in rerank response")
    private Boolean returnDocuments;

    // Text Extraction (COS)
    @UriParam(label = "producer", description = "Cloud Object Storage URL")
    private String cosUrl;

    @UriParam(label = "producer", description = "COS connection ID for document storage")
    private String documentConnectionId;

    @UriParam(label = "producer", description = "COS bucket for document storage")
    private String documentBucket;

    @UriParam(label = "producer", description = "COS connection ID for result storage")
    private String resultConnectionId;

    @UriParam(label = "producer", description = "COS bucket for result storage")
    private String resultBucket;

    // Detection
    @UriParam(label = "producer", description = "Whether to detect PII (Personal Identifiable Information)")
    private Boolean detectPii;

    @UriParam(label = "producer", description = "Whether to detect HAP (Harmful, Abusive, Profane content)")
    private Boolean detectHap;

    @UriParam(label = "producer", description = "Detection threshold (0.0 to 1.0)")
    private Double detectionThreshold;

    // Advanced
    @UriParam(label = "advanced", description = "Request timeout in milliseconds")
    private Long timeout;

    @UriParam(label = "advanced", defaultValue = "true", description = "Whether to verify SSL certificates")
    private Boolean verifySsl = true;

    @UriParam(label = "advanced", defaultValue = "false", description = "Whether to log HTTP requests to the watsonx.ai API")
    private Boolean logRequests = false;

    @UriParam(label = "advanced", defaultValue = "false", description = "Whether to log HTTP responses from the watsonx.ai API")
    private Boolean logResponses = false;

    public WatsonxAiConfiguration copy() {
        try {
            return (WatsonxAiConfiguration) clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    // Getters and Setters

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getWxUrl() {
        return wxUrl;
    }

    public void setWxUrl(String wxUrl) {
        this.wxUrl = wxUrl;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getSpaceId() {
        return spaceId;
    }

    public void setSpaceId(String spaceId) {
        this.spaceId = spaceId;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }

    public WatsonxAiOperations getOperation() {
        return operation;
    }

    public void setOperation(WatsonxAiOperations operation) {
        this.operation = operation;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxNewTokens() {
        return maxNewTokens;
    }

    public void setMaxNewTokens(Integer maxNewTokens) {
        this.maxNewTokens = maxNewTokens;
    }

    public Double getTopP() {
        return topP;
    }

    public void setTopP(Double topP) {
        this.topP = topP;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    public Double getRepetitionPenalty() {
        return repetitionPenalty;
    }

    public void setRepetitionPenalty(Double repetitionPenalty) {
        this.repetitionPenalty = repetitionPenalty;
    }

    public Integer getMaxCompletionTokens() {
        return maxCompletionTokens;
    }

    public void setMaxCompletionTokens(Integer maxCompletionTokens) {
        this.maxCompletionTokens = maxCompletionTokens;
    }

    public Double getFrequencyPenalty() {
        return frequencyPenalty;
    }

    public void setFrequencyPenalty(Double frequencyPenalty) {
        this.frequencyPenalty = frequencyPenalty;
    }

    public Double getPresencePenalty() {
        return presencePenalty;
    }

    public void setPresencePenalty(Double presencePenalty) {
        this.presencePenalty = presencePenalty;
    }

    public Integer getTruncateInputTokens() {
        return truncateInputTokens;
    }

    public void setTruncateInputTokens(Integer truncateInputTokens) {
        this.truncateInputTokens = truncateInputTokens;
    }

    public Integer getRerankTopN() {
        return rerankTopN;
    }

    public void setRerankTopN(Integer rerankTopN) {
        this.rerankTopN = rerankTopN;
    }

    public Boolean getReturnDocuments() {
        return returnDocuments;
    }

    public void setReturnDocuments(Boolean returnDocuments) {
        this.returnDocuments = returnDocuments;
    }

    public String getCosUrl() {
        return cosUrl;
    }

    public void setCosUrl(String cosUrl) {
        this.cosUrl = cosUrl;
    }

    public String getDocumentConnectionId() {
        return documentConnectionId;
    }

    public void setDocumentConnectionId(String documentConnectionId) {
        this.documentConnectionId = documentConnectionId;
    }

    public String getDocumentBucket() {
        return documentBucket;
    }

    public void setDocumentBucket(String documentBucket) {
        this.documentBucket = documentBucket;
    }

    public String getResultConnectionId() {
        return resultConnectionId;
    }

    public void setResultConnectionId(String resultConnectionId) {
        this.resultConnectionId = resultConnectionId;
    }

    public String getResultBucket() {
        return resultBucket;
    }

    public void setResultBucket(String resultBucket) {
        this.resultBucket = resultBucket;
    }

    public Boolean getDetectPii() {
        return detectPii;
    }

    public void setDetectPii(Boolean detectPii) {
        this.detectPii = detectPii;
    }

    public Boolean getDetectHap() {
        return detectHap;
    }

    public void setDetectHap(Boolean detectHap) {
        this.detectHap = detectHap;
    }

    public Double getDetectionThreshold() {
        return detectionThreshold;
    }

    public void setDetectionThreshold(Double detectionThreshold) {
        this.detectionThreshold = detectionThreshold;
    }

    public Long getTimeout() {
        return timeout;
    }

    public void setTimeout(Long timeout) {
        this.timeout = timeout;
    }

    public Boolean getVerifySsl() {
        return verifySsl;
    }

    public void setVerifySsl(Boolean verifySsl) {
        this.verifySsl = verifySsl;
    }

    public Boolean getLogRequests() {
        return logRequests;
    }

    public void setLogRequests(Boolean logRequests) {
        this.logRequests = logRequests;
    }

    public Boolean getLogResponses() {
        return logResponses;
    }

    public void setLogResponses(Boolean logResponses) {
        this.logResponses = logResponses;
    }
}
