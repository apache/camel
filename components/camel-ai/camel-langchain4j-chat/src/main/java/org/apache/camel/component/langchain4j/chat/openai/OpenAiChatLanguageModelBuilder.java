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
package org.apache.camel.component.langchain4j.chat.openai;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import static java.time.Duration.ofSeconds;

public final class OpenAiChatLanguageModelBuilder {
    private String apiKey;
    private String modelName;
    private long timeout;
    private int maxRetries;
    private boolean logRequests;
    private boolean logResponses;

    private Double temperature;

    public OpenAiChatLanguageModelBuilder apiKey(String apiKey) {
        this.apiKey = apiKey;
        return this;
    }

    public OpenAiChatLanguageModelBuilder modelName(String modelName) {
        this.modelName = modelName;
        return this;
    }

    public OpenAiChatLanguageModelBuilder timeout(long timeout) {
        this.timeout = timeout;
        return this;
    }

    public OpenAiChatLanguageModelBuilder maxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
        return this;
    }

    public OpenAiChatLanguageModelBuilder logRequests(boolean logRequests) {
        this.logRequests = logRequests;
        return this;
    }

    public OpenAiChatLanguageModelBuilder logResponses(boolean logResponses) {
        this.logResponses = logResponses;
        return this;
    }

    public OpenAiChatLanguageModelBuilder temperature(Double temperature) {
        this.temperature = temperature;
        return this;
    }

    public ChatLanguageModel build() {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .timeout(ofSeconds(timeout))
                .maxRetries(maxRetries)
                .logRequests(logRequests)
                .logRequests(logResponses)
                .temperature(temperature)
                .build();
    }
}
