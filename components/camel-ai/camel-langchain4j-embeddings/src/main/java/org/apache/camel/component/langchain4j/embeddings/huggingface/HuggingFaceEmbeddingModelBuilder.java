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
package org.apache.camel.component.langchain4j.embeddings.huggingface;

import dev.langchain4j.model.huggingface.HuggingFaceEmbeddingModel;

import static java.time.Duration.ofSeconds;

public final class HuggingFaceEmbeddingModelBuilder {
    private String accessToken;
    private String modelId;
    private boolean waitForModel;
    private int timeout;

    public HuggingFaceEmbeddingModelBuilder accessToken(String accessToken) {
        this.accessToken = accessToken;
        return this;
    }

    public HuggingFaceEmbeddingModelBuilder modelId(String modelId) {
        this.modelId = modelId;
        return this;
    }

    public HuggingFaceEmbeddingModelBuilder timeout(int timeout) {
        this.timeout = timeout;
        return this;
    }

    public HuggingFaceEmbeddingModelBuilder waitForModel(boolean waitForModel) {
        this.waitForModel = waitForModel;
        return this;
    }

    public HuggingFaceEmbeddingModel build() {
        return HuggingFaceEmbeddingModel.builder()
                .accessToken(accessToken)
                .modelId(modelId)
                .waitForModel(waitForModel)
                .timeout(ofSeconds(timeout))
                .build();
    }

}
