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
package org.apache.camel.component.google.vertexai;

import com.google.cloud.aiplatform.v1.PredictionServiceClient;
import com.google.genai.Client;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class GoogleVertexAIConfiguration implements Cloneable {

    @UriPath(label = "common", description = "Google Cloud Project ID")
    @Metadata(required = true)
    private String projectId;

    @UriPath(label = "common", description = "Google Cloud location/region (e.g., us-central1)")
    @Metadata(required = true)
    private String location;

    @UriPath(label = "common", description = "Model ID to use for predictions")
    @Metadata(required = true)
    private String modelId;

    @UriParam(label = "common",
              description = "The Service account key that can be used as credentials for the Vertex AI client. "
                            +
                            "It can be loaded by default from classpath, but you can prefix with classpath:, file:, or http: to load the resource from different systems.")
    private String serviceAccountKey;

    @UriParam(label = "producer",
              enums = "generateText,generateChat,generateChatStreaming,generateImage,generateEmbeddings,generateCode,generateMultimodal,rawPredict,streamRawPredict")
    private GoogleVertexAIOperations operation;

    // ==================== Partner Model / rawPredict Configuration ====================

    @UriParam(label = "producer",
              description = "Publisher name for partner models (e.g., anthropic, meta, mistralai). Required for rawPredict operations.")
    private String publisher;

    @UriParam(label = "producer",
              description = "Anthropic API version for Claude models. Required when publisher is 'anthropic'.",
              defaultValue = "vertex-2023-10-16")
    private String anthropicVersion = "vertex-2023-10-16";

    @UriParam(label = "producer", description = "Temperature parameter for generation (0.0-1.0)", defaultValue = "0.7")
    private Float temperature = 0.7f;

    @UriParam(label = "producer", description = "Top-P parameter for nucleus sampling", defaultValue = "0.95")
    private Float topP = 0.95f;

    @UriParam(label = "producer", description = "Top-K parameter for generation", defaultValue = "40")
    private Integer topK = 40;

    @UriParam(label = "producer", description = "Maximum number of output tokens", defaultValue = "1024")
    private Integer maxOutputTokens = 1024;

    @UriParam(label = "producer", description = "Number of candidate responses to generate", defaultValue = "1")
    private Integer candidateCount = 1;

    @UriParam(label = "producer", description = "Streaming output mode: complete (default) or chunks",
              defaultValue = "complete", enums = "complete,chunks")
    private String streamOutputMode = "complete";

    @UriParam(label = "producer", description = "Whether to use JSON request/response format", defaultValue = "false")
    private boolean jsonMode;

    @UriParam(label = "advanced")
    @Metadata(autowired = true)
    private Client client;

    @UriParam(label = "advanced")
    @Metadata(autowired = true)
    private PredictionServiceClient predictionServiceClient;

    public String getProjectId() {
        return projectId;
    }

    /**
     * Google Cloud Project ID
     */
    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getLocation() {
        return location;
    }

    /**
     * Google Cloud location/region (e.g., us-central1, europe-west1)
     */
    public void setLocation(String location) {
        this.location = location;
    }

    public String getModelId() {
        return modelId;
    }

    /**
     * Model ID to use for predictions (e.g., gemini-1.5-pro, text-bison)
     */
    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getServiceAccountKey() {
        return serviceAccountKey;
    }

    /**
     * The Service account key that can be used as credentials for the Vertex AI client. It can be loaded by default
     * from classpath, but you can prefix with "classpath:", "file:", or "http:" to load the resource from different
     * systems.
     */
    public void setServiceAccountKey(String serviceAccountKey) {
        this.serviceAccountKey = serviceAccountKey;
    }

    public GoogleVertexAIOperations getOperation() {
        return operation;
    }

    /**
     * Set the operation for the producer
     */
    public void setOperation(GoogleVertexAIOperations operation) {
        this.operation = operation;
    }

    public Float getTemperature() {
        return temperature;
    }

    /**
     * Controls randomness in generation. Lower values make output more deterministic. Range: 0.0 to 1.0
     */
    public void setTemperature(Float temperature) {
        this.temperature = temperature;
    }

    public Float getTopP() {
        return topP;
    }

    /**
     * Nucleus sampling parameter. Considers tokens with top_p probability mass. Range: 0.0 to 1.0
     */
    public void setTopP(Float topP) {
        this.topP = topP;
    }

    public Integer getTopK() {
        return topK;
    }

    /**
     * Only sample from the top K options for each subsequent token
     */
    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    public Integer getMaxOutputTokens() {
        return maxOutputTokens;
    }

    /**
     * Maximum number of tokens to generate in the response
     */
    public void setMaxOutputTokens(Integer maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public Integer getCandidateCount() {
        return candidateCount;
    }

    /**
     * Number of response variations to generate
     */
    public void setCandidateCount(Integer candidateCount) {
        this.candidateCount = candidateCount;
    }

    public String getStreamOutputMode() {
        return streamOutputMode;
    }

    /**
     * For streaming operations: 'complete' returns full accumulated response, 'chunks' emits each chunk separately
     */
    public void setStreamOutputMode(String streamOutputMode) {
        this.streamOutputMode = streamOutputMode;
    }

    public boolean isJsonMode() {
        return jsonMode;
    }

    /**
     * Whether to use JSON format for requests and responses
     */
    public void setJsonMode(boolean jsonMode) {
        this.jsonMode = jsonMode;
    }

    public Client getClient() {
        return client;
    }

    /**
     * The Google GenAI client for Vertex AI
     */
    public void setClient(Client client) {
        this.client = client;
    }

    public PredictionServiceClient getPredictionServiceClient() {
        return predictionServiceClient;
    }

    /**
     * The Google Cloud AI Platform Prediction Service client for rawPredict operations
     */
    public void setPredictionServiceClient(PredictionServiceClient predictionServiceClient) {
        this.predictionServiceClient = predictionServiceClient;
    }

    public String getPublisher() {
        return publisher;
    }

    /**
     * Publisher name for partner models (e.g., anthropic, meta, mistralai). Required for rawPredict operations.
     */
    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }

    public String getAnthropicVersion() {
        return anthropicVersion;
    }

    /**
     * Anthropic API version for Claude models. Required when publisher is 'anthropic'. Default: vertex-2023-10-16
     */
    public void setAnthropicVersion(String anthropicVersion) {
        this.anthropicVersion = anthropicVersion;
    }

    public GoogleVertexAIConfiguration copy() {
        try {
            return (GoogleVertexAIConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
