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
package org.apache.camel.component.langchain4j.embeddings.openai;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;

import static java.time.Duration.ofSeconds;

public final class OpenAiEmbeddingModelBuilder {
    private String apiKey;
    private String modelName;
    private long timeout;
    private int maxRetries;
    private int dimensions;
    private boolean logRequests;
    private boolean logResponses;

    public OpenAiEmbeddingModelBuilder apiKey(String apiKey) {
        this.apiKey = apiKey;
        return this;
    }

    public OpenAiEmbeddingModelBuilder modelName(String modelName) {
        this.modelName = modelName;
        return this;
    }

    public OpenAiEmbeddingModelBuilder timeout(long timeout) {
        this.timeout = timeout;
        return this;
    }

    public OpenAiEmbeddingModelBuilder maxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
        return this;
    }

    public OpenAiEmbeddingModelBuilder dimensions(int dimensions) {
        this.dimensions = dimensions;
        return this;
    }

    public OpenAiEmbeddingModelBuilder logRequests(boolean logRequests) {
        this.logRequests = logRequests;
        return this;
    }

    public OpenAiEmbeddingModelBuilder logResponses(boolean logResponses) {
        this.logResponses = logResponses;
        return this;
    }

    public EmbeddingModel build() {
        return OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .timeout(ofSeconds(timeout))
                .maxRetries(maxRetries)
                .dimensions(dimensions)
                .logRequests(logRequests)
                .logRequests(logResponses)
                .build();
    }
}
