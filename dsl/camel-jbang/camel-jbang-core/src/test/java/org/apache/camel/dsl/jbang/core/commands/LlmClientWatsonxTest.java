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
package org.apache.camel.dsl.jbang.core.commands;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for watsonx.ai URL normalization and provider configuration in {@link LlmClient}.
 */
class LlmClientWatsonxTest {

    @Test
    void normalizeWatsonxChatUrlAppendsGatewayPath() {
        LlmClient client = LlmClient.create().withApiType(LlmClient.ApiType.watsonx);

        assertThat(client.normalizeWatsonxChatUrl("https://us-south.ml.cloud.ibm.com"))
                .isEqualTo("https://us-south.ml.cloud.ibm.com/ml/gateway/v1/chat/completions");
    }

    @Test
    void normalizeWatsonxChatUrlStripsTrailingSlash() {
        LlmClient client = LlmClient.create().withApiType(LlmClient.ApiType.watsonx);

        assertThat(client.normalizeWatsonxChatUrl("https://us-south.ml.cloud.ibm.com/"))
                .isEqualTo("https://us-south.ml.cloud.ibm.com/ml/gateway/v1/chat/completions");
    }

    @Test
    void normalizeWatsonxChatUrlPreservesFullGatewayUrl() {
        LlmClient client = LlmClient.create().withApiType(LlmClient.ApiType.watsonx);

        String fullUrl = "https://us-south.ml.cloud.ibm.com/ml/gateway/v1/chat/completions";
        assertThat(client.normalizeWatsonxChatUrl(fullUrl)).isEqualTo(fullUrl);
    }

    @Test
    void normalizeWatsonxChatUrlRebuildsFromPartialGatewayPath() {
        LlmClient client = LlmClient.create().withApiType(LlmClient.ApiType.watsonx);

        assertThat(client.normalizeWatsonxChatUrl("https://us-south.ml.cloud.ibm.com/ml/gateway"))
                .isEqualTo("https://us-south.ml.cloud.ibm.com/ml/gateway/v1/chat/completions");
    }

    @Test
    void normalizeWatsonxModelsUrlAppendsGatewayModelsPath() {
        LlmClient client = LlmClient.create().withApiType(LlmClient.ApiType.watsonx);

        assertThat(client.normalizeWatsonxModelsUrl("https://us-south.ml.cloud.ibm.com"))
                .isEqualTo("https://us-south.ml.cloud.ibm.com/ml/gateway/v1/models");
    }

    @Test
    void normalizeWatsonxModelsUrlPreservesFullModelsUrl() {
        LlmClient client = LlmClient.create().withApiType(LlmClient.ApiType.watsonx);

        String fullUrl = "https://eu-de.ml.cloud.ibm.com/ml/gateway/v1/models";
        assertThat(client.normalizeWatsonxModelsUrl(fullUrl)).isEqualTo(fullUrl);
    }

    @Test
    void normalizeOpenAiUrlDelegatesToWatsonxForWatsonxApiType() {
        LlmClient client = LlmClient.create().withApiType(LlmClient.ApiType.watsonx);

        assertThat(client.normalizeOpenAiUrl("https://us-south.ml.cloud.ibm.com"))
                .isEqualTo("https://us-south.ml.cloud.ibm.com/ml/gateway/v1/chat/completions");
    }

    @Test
    void normalizeWatsonxChatUrlWorksDifferentRegions() {
        LlmClient client = LlmClient.create().withApiType(LlmClient.ApiType.watsonx);

        assertThat(client.normalizeWatsonxChatUrl("https://eu-de.ml.cloud.ibm.com"))
                .isEqualTo("https://eu-de.ml.cloud.ibm.com/ml/gateway/v1/chat/completions");
        assertThat(client.normalizeWatsonxChatUrl("https://jp-tok.ml.cloud.ibm.com"))
                .isEqualTo("https://jp-tok.ml.cloud.ibm.com/ml/gateway/v1/chat/completions");
    }

    @Test
    void watsonxApiTypeExistsInEnum() {
        assertThat(LlmClient.ApiType.valueOf("watsonx")).isEqualTo(LlmClient.ApiType.watsonx);
    }

    @Test
    void normalizeOpenAiUrlProducesGatewayPathNotStandardOpenAiPath() {
        LlmClient client = LlmClient.create()
                .withApiType(LlmClient.ApiType.watsonx)
                .withUrl("https://us-south.ml.cloud.ibm.com")
                .withModel("ibm/granite-3.1-8b-instruct")
                .withApiKey("test-key");

        // The normalized URL should use the watsonx gateway path, not the standard /v1/chat/completions
        String normalized = client.normalizeOpenAiUrl("https://us-south.ml.cloud.ibm.com");
        assertThat(normalized)
                .isEqualTo("https://us-south.ml.cloud.ibm.com/ml/gateway/v1/chat/completions")
                .startsWith("https://us-south.ml.cloud.ibm.com/ml/gateway/");
    }
}
