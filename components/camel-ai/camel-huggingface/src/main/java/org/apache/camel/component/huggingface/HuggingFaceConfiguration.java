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
package org.apache.camel.component.huggingface;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.huggingface.tasks.HuggingFaceTask;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class HuggingFaceConfiguration implements Cloneable {

    @UriPath(name = "task")
    @Metadata(required = true,
              enums = "TEXT_CLASSIFICATION,TEXT_GENERATION,QUESTION_ANSWERING,SUMMARIZATION,SENTENCE_EMBEDDINGS,ZERO_SHOT_CLASSIFICATION,TEXT_TO_IMAGE,CHAT,AUTOMATIC_SPEECH_RECOGNITION,TEXT_TO_SPEECH",
              description = "The Hugging Face task to perform (e.g., TEXT_CLASSIFICATION)")
    private HuggingFaceTask task;

    @UriParam
    @Metadata(required = true, description = "Hugging Face model ID (e.g., distilbert-base-uncased-finetuned-sst-2-english)")
    private String modelId;

    @UriParam
    @Metadata(description = "Model revision or branch (default: main)")
    private String revision = "main";

    @UriParam
    @Metadata(description = "Device for inference (cpu, gpu, auto)")
    private String device = "auto";

    @UriParam
    @Metadata(description = "Max tokens for generation tasks")
    private int maxTokens = 512;

    @UriParam
    @Metadata(description = "Temperature for sampling (0.0-1.0)")
    private float temperature = 1.0f;

    @UriParam
    @Metadata(description = "HF API token for private models")
    private String authToken;

    @UriParam
    @Metadata(description = "Min tokens for summarization tasks")
    private int minLength = 30;

    @UriParam
    @Metadata(description = "Allow multi-label classifications for zero-shot tasks")
    private boolean multiLabel = false;

    @UriParam
    @Metadata(description = "Model loading timeout in seconds, if negative then use default (240 seconds)")
    private int modelLoadingTimeout = -1;

    @UriParam
    @Metadata(description = "Predict timeout in seconds, if negative then use default (120 seconds)")
    private int predictTimeout = -1;

    @UriParam(defaultValue = "CamelChatMemoryId", description = "Header name for conversation memory ID (for multi-user chats)")
    private String memoryIdHeader = "CamelChatMemoryId";

    @UriParam(defaultValue = "user", description = "Role for user messages in chat history (e.g., 'user' or 'human')")
    private String userRole = "user";

    @UriParam
    @Metadata(description = "Initial system prompt for chat tasks (e.g., 'You are a helpful assistant named Alan.')")
    private String systemPrompt;

    @UriParam(defaultValue = "true",
              description = "If true, auto-select the best label (highest score) for zero-shot classification")
    private boolean autoSelect = true;

    @UriParam
    @Metadata(description = "Bean name of a custom TaskPredictor implementation (for tasks not covered by built-in predictors)")
    private String predictorBean;

    @UriParam(defaultValue = "5", description = "Top-k parameter for classification tasks")
    private int topK = 5;

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public String getPredictorBean() {
        return predictorBean;
    }

    public void setPredictorBean(String predictorBean) {
        this.predictorBean = predictorBean;
    }

    public boolean isAutoSelect() {
        return autoSelect;
    }

    public void setAutoSelect(boolean autoSelect) {
        this.autoSelect = autoSelect;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public String getUserRole() {
        return userRole;
    }

    public void setUserRole(String userRole) {
        this.userRole = userRole;
    }

    public String getMemoryIdHeader() {
        return memoryIdHeader;
    }

    public void setMemoryIdHeader(String memoryIdHeader) {
        this.memoryIdHeader = memoryIdHeader;
    }

    public boolean isMultiLabel() {
        return multiLabel;
    }

    public void setMultiLabel(boolean multiLabel) {
        this.multiLabel = multiLabel;
    }

    public int getMinLength() {
        return minLength;
    }

    public void setMinLength(int minLength) {
        this.minLength = minLength;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getRevision() {
        return revision;
    }

    public void setRevision(String revision) {
        this.revision = revision;
    }

    public String getDevice() {
        return device;
    }

    public void setDevice(String device) {
        this.device = device;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public float getTemperature() {
        return temperature;
    }

    public void setTemperature(float temperature) {
        this.temperature = temperature;
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public HuggingFaceTask getTask() {
        return task;
    }

    public void setTask(HuggingFaceTask task) {
        this.task = task;
    }

    public int getModelLoadingTimeout() {
        return modelLoadingTimeout;
    }

    public void setModelLoadingTimeout(int modelLoadingTimeout) {
        this.modelLoadingTimeout = modelLoadingTimeout;
    }

    public int getPredictTimeout() {
        return predictTimeout;
    }

    public void setPredictTimeout(int predictTimeout) {
        this.predictTimeout = predictTimeout;
    }

    public HuggingFaceConfiguration copy() {
        try {
            return (HuggingFaceConfiguration) clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

}
