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
              description = "The task to run, such as text-classification or chat. The name is case insensitive, and a dash"
                            + " equals an underscore. CUSTOM, or any other name, needs the predictorBean option, whose bean"
                            + " then runs the task.")
    private HuggingFaceTask task;

    @UriParam
    @Metadata(required = true,
              description = "The ID of the model on the Hugging Face Hub, for example"
                            + " distilbert-base-uncased-finetuned-sst-2-english or Qwen/Qwen2.5-3B-Instruct. The model must"
                            + " support the task.")
    private String modelId;

    @UriParam
    @Metadata(description = "The revision of the model on the Hugging Face Hub: a branch name, a tag name or a commit ID.")
    private String revision = "main";

    @UriParam
    @Metadata(description = "The device to run the model on, such as auto, cpu, cuda or mps. Most tasks give it to the"
                            + " Transformers pipeline as device_map, where auto spreads the model over the available devices."
                            + " sentence-embeddings uses cuda for auto when it is available, else cpu, and text-to-image moves"
                            + " the pipeline to this device.")
    private String device = "auto";

    @UriParam
    @Metadata(description = "The maximum number of new tokens that the model generates, as max_new_tokens, for the"
                            + " text-generation, summarization and chat tasks.")
    private int maxTokens = 512;

    @UriParam
    @Metadata(description = "The sampling temperature for the text-generation, summarization and chat tasks. A higher value"
                            + " gives more random text. A value of 0 or less turns sampling off, so the model picks the most"
                            + " likely token each time.")
    private float temperature = 1.0f;

    @UriParam(label = "security", security = "secret")
    @Metadata(description = "The Hugging Face user access token, needed to download gated or private models. When the"
                            + " oauthProfile option is set, the token that it obtains replaces this option.")
    private String authToken;

    @UriParam(label = "security")
    @Metadata(description = "OAuth profile name for obtaining an access token via the OAuth 2.0 Client Credentials grant. "
                            + "When set, the token is acquired from the configured identity provider and used as authToken. "
                            + "Requires camel-oauth on the classpath.")
    private String oauthProfile;

    @UriParam
    @Metadata(description = "The minimum length, in tokens, of the summary that the summarization task generates.")
    private int minLength = 30;

    @UriParam
    @Metadata(description = "Whether the zero-shot-classification task scores each candidate label on its own, so that"
                            + " several labels can be true. If false, the scores of all the labels add up to 1.")
    private boolean multiLabel = false;

    @UriParam
    @Metadata(description = "The time in seconds that DJL waits for the Python process to start and load the model. With 0"
                            + " or a negative value, DJL uses the MODEL_LOADING_TIMEOUT environment variable, or else 240"
                            + " seconds.")
    private int modelLoadingTimeout = -1;

    @UriParam
    @Metadata(description = "The time in seconds that DJL waits for the Python process to answer one request. With 0 or a"
                            + " negative value, DJL uses the PREDICT_TIMEOUT environment variable, or else 120 seconds.")
    private int predictTimeout = -1;

    @UriParam(defaultValue = "CamelChatMemoryId",
              description = "The name of the header that holds the memory ID for the chat task. Each memory ID has its own"
                            + " chat history, so that several users can chat at the same time. A message without this"
                            + " header uses the memory ID default.")
    private String memoryIdHeader = "CamelChatMemoryId";

    @UriParam(defaultValue = "1000",
              description = "The maximum number of chat histories, one per memory ID, that the chat task keeps in memory."
                            + " When there are more, the history that was updated least recently is evicted first. Zero or"
                            + " a negative value keeps them all")
    private int maxChatHistories = 1000;

    @UriParam(defaultValue = "50",
              description = "The maximum number of messages that the chat task keeps in a chat history, besides the system"
                            + " prompt. The oldest messages are dropped first, and the kept messages start with a user"
                            + " message, so an odd value keeps one message less. 0 keeps every message. 1 and negative"
                            + " values are rejected, as they would keep no reply")
    private int maxHistoryMessages = 50;

    @UriParam(defaultValue = "user",
              description = "The role of the message that the chat task adds to the chat history from the message body. Set"
                            + " it to the role that the chat template of the model expects, for example human.")
    private String userRole = "user";

    @UriParam
    @Metadata(description = "The system message that the chat task puts first in a new chat history, for example You are a"
                            + " helpful assistant named Alan.")
    private String systemPrompt;

    @UriParam(defaultValue = "true",
              description = "Whether the zero-shot-classification task returns only the label with the highest score, as a"
                            + " String. If false, it returns a DJL Classifications with all the labels and their scores.")
    private boolean autoSelect = true;

    @UriParam
    @Metadata(description = "The name of a bean of type org.apache.camel.component.huggingface.tasks.TaskPredictor that"
                            + " runs the task instead of a built-in predictor. It is required for a task that is not built in."
                            + " The producer fails to start if the registry has no such bean.")
    private String predictorBean;

    @UriParam(defaultValue = "5",
              description = "The number of labels with the highest scores that the text-classification task returns.")
    private int topK = 5;

    @UriParam(defaultValue = "true",
              description = "Whether all requests share one Python process, which loads the model once and runs one request"
                            + " at a time. If false, each request starts a new Python process, which loads the model again,"
                            + " and stops it afterwards.")
    private boolean pooling = true;

    public boolean isPooling() {
        return pooling;
    }

    public void setPooling(boolean pooling) {
        this.pooling = pooling;
    }

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

    public int getMaxChatHistories() {
        return maxChatHistories;
    }

    public void setMaxChatHistories(int maxChatHistories) {
        this.maxChatHistories = maxChatHistories;
    }

    public int getMaxHistoryMessages() {
        return maxHistoryMessages;
    }

    public void setMaxHistoryMessages(int maxHistoryMessages) {
        this.maxHistoryMessages = maxHistoryMessages;
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

    public String getOauthProfile() {
        return oauthProfile;
    }

    public void setOauthProfile(String oauthProfile) {
        this.oauthProfile = oauthProfile;
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
