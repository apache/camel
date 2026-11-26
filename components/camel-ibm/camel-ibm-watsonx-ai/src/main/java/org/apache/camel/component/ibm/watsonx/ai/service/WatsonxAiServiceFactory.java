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
package org.apache.camel.component.ibm.watsonx.ai.service;

import java.time.Duration;
import java.util.function.Consumer;

import com.ibm.watsonx.ai.chat.ChatService;
import com.ibm.watsonx.ai.core.auth.Authenticator;
import com.ibm.watsonx.ai.core.auth.ibmcloud.IBMCloudAuthenticator;
import com.ibm.watsonx.ai.deployment.DeploymentService;
import com.ibm.watsonx.ai.detection.DetectionService;
import com.ibm.watsonx.ai.embedding.EmbeddingService;
import com.ibm.watsonx.ai.foundationmodel.FoundationModelService;
import com.ibm.watsonx.ai.rerank.RerankService;
import com.ibm.watsonx.ai.textgeneration.TextGenerationService;
import com.ibm.watsonx.ai.textprocessing.CosReference;
import com.ibm.watsonx.ai.textprocessing.textclassification.TextClassificationService;
import com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionService;
import com.ibm.watsonx.ai.timeseries.TimeSeriesService;
import com.ibm.watsonx.ai.tokenization.TokenizationService;
import com.ibm.watsonx.ai.tool.ToolService;
import org.apache.camel.CamelContext;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiConfiguration;
import org.apache.camel.util.ObjectHelper;

/**
 * Factory for creating IBM watsonx.ai service instances with centralized configuration.
 */
public final class WatsonxAiServiceFactory {

    private WatsonxAiServiceFactory() {
        // Utility class
    }

    /**
     * Creates the authenticator that every service of an endpoint shares, so that they share one access token: the
     * {@code authenticator} option when it is set, else a Bearer token from the {@code oauthProfile}, else an IBM Cloud
     * IAM token for the {@code apiKey}, obtained with the {@code verifySsl} and {@code timeout} options of the
     * endpoint.
     *
     * @return the authenticator, or {@code null} when no credentials are configured
     */
    public static Authenticator createAuthenticator(WatsonxAiConfiguration config, CamelContext camelContext) {
        if (config.getAuthenticator() != null) {
            return config.getAuthenticator();
        }
        if (ObjectHelper.isNotEmpty(config.getOauthProfile())) {
            return new OAuthProfileAuthenticator(camelContext, config.getOauthProfile());
        }
        if (ObjectHelper.isEmpty(config.getApiKey())) {
            return null;
        }
        IBMCloudAuthenticator.Builder builder = IBMCloudAuthenticator.builder().apiKey(config.getApiKey());
        applyIfNotNull(config.getVerifySsl(), builder::verifySsl);
        applyIfNotNull(config.getTimeout(), t -> builder.timeout(Duration.ofMillis(t)));
        return builder.build();
    }

    public static TextGenerationService createTextGenerationService(
            WatsonxAiConfiguration config, Authenticator authenticator) {
        TextGenerationService.Builder builder = TextGenerationService.builder()
                .baseUrl(config.getBaseUrl())
                .authenticator(authenticator);

        applyIfNotNull(config.getProjectId(), builder::projectId);
        applyIfNotNull(config.getSpaceId(), builder::spaceId);
        applyIfNotNull(config.getModelId(), builder::modelId);
        applyIfNotNull(config.getVerifySsl(), builder::verifySsl);
        applyIfNotNull(config.getTimeout(), t -> builder.timeout(Duration.ofMillis(t)));
        applyIfTrue(config.getLogRequests(), () -> builder.logRequests(true));
        applyIfTrue(config.getLogResponses(), () -> builder.logResponses(true));

        return builder.build();
    }

    public static ChatService createChatService(WatsonxAiConfiguration config, Authenticator authenticator) {
        ChatService.Builder builder = ChatService.builder()
                .baseUrl(config.getBaseUrl())
                .authenticator(authenticator);

        applyIfNotNull(config.getProjectId(), builder::projectId);
        applyIfNotNull(config.getSpaceId(), builder::spaceId);
        applyIfNotNull(config.getModelId(), builder::modelId);
        applyIfNotNull(config.getVerifySsl(), builder::verifySsl);
        applyIfNotNull(config.getTimeout(), t -> builder.timeout(Duration.ofMillis(t)));
        applyIfTrue(config.getLogRequests(), () -> builder.logRequests(true));
        applyIfTrue(config.getLogResponses(), () -> builder.logResponses(true));

        return builder.build();
    }

    public static EmbeddingService createEmbeddingService(WatsonxAiConfiguration config, Authenticator authenticator) {
        EmbeddingService.Builder builder = EmbeddingService.builder()
                .baseUrl(config.getBaseUrl())
                .authenticator(authenticator);

        applyIfNotNull(config.getProjectId(), builder::projectId);
        applyIfNotNull(config.getSpaceId(), builder::spaceId);
        applyIfNotNull(config.getModelId(), builder::modelId);
        applyIfNotNull(config.getVerifySsl(), builder::verifySsl);
        applyIfNotNull(config.getTimeout(), t -> builder.timeout(Duration.ofMillis(t)));
        applyIfTrue(config.getLogRequests(), () -> builder.logRequests(true));
        applyIfTrue(config.getLogResponses(), () -> builder.logResponses(true));

        return builder.build();
    }

    public static RerankService createRerankService(WatsonxAiConfiguration config, Authenticator authenticator) {
        RerankService.Builder builder = RerankService.builder()
                .baseUrl(config.getBaseUrl())
                .authenticator(authenticator);

        applyIfNotNull(config.getProjectId(), builder::projectId);
        applyIfNotNull(config.getSpaceId(), builder::spaceId);
        applyIfNotNull(config.getModelId(), builder::modelId);
        applyIfNotNull(config.getVerifySsl(), builder::verifySsl);
        applyIfNotNull(config.getTimeout(), t -> builder.timeout(Duration.ofMillis(t)));
        applyIfTrue(config.getLogRequests(), () -> builder.logRequests(true));
        applyIfTrue(config.getLogResponses(), () -> builder.logResponses(true));

        return builder.build();
    }

    public static TokenizationService createTokenizationService(WatsonxAiConfiguration config, Authenticator authenticator) {
        TokenizationService.Builder builder = TokenizationService.builder()
                .baseUrl(config.getBaseUrl())
                .authenticator(authenticator);

        applyIfNotNull(config.getProjectId(), builder::projectId);
        applyIfNotNull(config.getSpaceId(), builder::spaceId);
        applyIfNotNull(config.getModelId(), builder::modelId);
        applyIfNotNull(config.getVerifySsl(), builder::verifySsl);
        applyIfNotNull(config.getTimeout(), t -> builder.timeout(Duration.ofMillis(t)));
        applyIfTrue(config.getLogRequests(), () -> builder.logRequests(true));
        applyIfTrue(config.getLogResponses(), () -> builder.logResponses(true));

        return builder.build();
    }

    public static DetectionService createDetectionService(WatsonxAiConfiguration config, Authenticator authenticator) {
        DetectionService.Builder builder = DetectionService.builder()
                .baseUrl(config.getBaseUrl())
                .authenticator(authenticator);

        applyIfNotNull(config.getProjectId(), builder::projectId);
        applyIfNotNull(config.getSpaceId(), builder::spaceId);
        // DetectionService doesn't have modelId
        applyIfNotNull(config.getGovernanceInstanceId(), builder::governanceInstanceId);
        applyIfNotNull(config.getVerifySsl(), builder::verifySsl);
        applyIfNotNull(config.getTimeout(), t -> builder.timeout(Duration.ofMillis(t)));
        applyIfTrue(config.getLogRequests(), () -> builder.logRequests(true));
        applyIfTrue(config.getLogResponses(), () -> builder.logResponses(true));

        return builder.build();
    }

    public static TextExtractionService createTextExtractionService(
            WatsonxAiConfiguration config, Authenticator authenticator) {
        validateCosConfiguration(config);

        TextExtractionService.Builder builder = TextExtractionService.builder()
                .baseUrl(config.getBaseUrl())
                .authenticator(authenticator)
                .cosUrl(config.getCosUrl())
                .documentReference(CosReference.of(
                        config.getDocumentConnectionId(),
                        config.getDocumentBucket()))
                .resultReference(CosReference.of(
                        config.getResultConnectionId(),
                        config.getResultBucket()));

        applyIfNotNull(config.getProjectId(), builder::projectId);
        applyIfNotNull(config.getSpaceId(), builder::spaceId);
        // TextExtractionService doesn't have modelId
        applyIfNotNull(config.getVerifySsl(), builder::verifySsl);
        applyIfNotNull(config.getTimeout(), t -> builder.timeout(Duration.ofMillis(t)));
        applyIfTrue(config.getLogRequests(), () -> builder.logRequests(true));
        applyIfTrue(config.getLogResponses(), () -> builder.logResponses(true));

        return builder.build();
    }

    public static TextClassificationService createTextClassificationService(
            WatsonxAiConfiguration config, Authenticator authenticator) {
        validateCosConfigurationForClassification(config);

        TextClassificationService.Builder builder = TextClassificationService.builder()
                .baseUrl(config.getBaseUrl())
                .authenticator(authenticator)
                .cosUrl(config.getCosUrl())
                .documentReference(CosReference.of(
                        config.getDocumentConnectionId(),
                        config.getDocumentBucket()));

        applyIfNotNull(config.getProjectId(), builder::projectId);
        applyIfNotNull(config.getSpaceId(), builder::spaceId);
        // TextClassificationService doesn't have modelId
        applyIfNotNull(config.getVerifySsl(), builder::verifySsl);
        applyIfNotNull(config.getTimeout(), t -> builder.timeout(Duration.ofMillis(t)));
        applyIfTrue(config.getLogRequests(), () -> builder.logRequests(true));
        applyIfTrue(config.getLogResponses(), () -> builder.logResponses(true));

        return builder.build();
    }

    public static TimeSeriesService createTimeSeriesService(WatsonxAiConfiguration config, Authenticator authenticator) {
        TimeSeriesService.Builder builder = TimeSeriesService.builder()
                .baseUrl(config.getBaseUrl())
                .authenticator(authenticator);

        applyIfNotNull(config.getProjectId(), builder::projectId);
        applyIfNotNull(config.getSpaceId(), builder::spaceId);
        applyIfNotNull(config.getModelId(), builder::modelId);
        applyIfNotNull(config.getVerifySsl(), builder::verifySsl);
        applyIfNotNull(config.getTimeout(), t -> builder.timeout(Duration.ofMillis(t)));
        applyIfTrue(config.getLogRequests(), () -> builder.logRequests(true));
        applyIfTrue(config.getLogResponses(), () -> builder.logResponses(true));

        return builder.build();
    }

    public static FoundationModelService createFoundationModelService(WatsonxAiConfiguration config) {
        FoundationModelService.Builder builder = FoundationModelService.builder()
                .baseUrl(config.getBaseUrl());

        // FoundationModelService doesn't have projectId, spaceId, modelId
        applyIfNotNull(config.getVerifySsl(), builder::verifySsl);
        applyIfNotNull(config.getTimeout(), t -> builder.timeout(Duration.ofMillis(t)));
        applyIfTrue(config.getLogRequests(), () -> builder.logRequests(true));
        applyIfTrue(config.getLogResponses(), () -> builder.logResponses(true));

        return builder.build();
    }

    public static DeploymentService createDeploymentService(WatsonxAiConfiguration config, Authenticator authenticator) {
        DeploymentService.Builder builder = DeploymentService.builder()
                .baseUrl(config.getBaseUrl())
                .authenticator(authenticator);

        // DeploymentService doesn't have projectId, spaceId, modelId
        applyIfNotNull(config.getVerifySsl(), builder::verifySsl);
        applyIfNotNull(config.getTimeout(), t -> builder.timeout(Duration.ofMillis(t)));
        applyIfTrue(config.getLogRequests(), () -> builder.logRequests(true));
        applyIfTrue(config.getLogResponses(), () -> builder.logResponses(true));

        return builder.build();
    }

    public static ToolService createToolService(WatsonxAiConfiguration config, Authenticator authenticator) {
        String toolBaseUrl = config.getWxUrl();
        if (toolBaseUrl == null || toolBaseUrl.isEmpty()) {
            throw new IllegalArgumentException(
                    "WX URL is required for tool operations. "
                                               + "Set 'wxUrl' configuration (e.g., https://api.dataplatform.cloud.ibm.com/wx)");
        }

        ToolService.Builder builder = ToolService.builder()
                .baseUrl(toolBaseUrl)
                .authenticator(authenticator);

        // ToolService doesn't have projectId, spaceId, modelId
        applyIfNotNull(config.getVerifySsl(), builder::verifySsl);
        applyIfNotNull(config.getTimeout(), t -> builder.timeout(Duration.ofMillis(t)));
        applyIfTrue(config.getLogRequests(), () -> builder.logRequests(true));
        applyIfTrue(config.getLogResponses(), () -> builder.logResponses(true));

        return builder.build();
    }

    private static <T> void applyIfNotNull(T value, Consumer<T> setter) {
        if (value != null) {
            setter.accept(value);
        }
    }

    private static void applyIfTrue(Boolean value, Runnable action) {
        if (Boolean.TRUE.equals(value)) {
            action.run();
        }
    }

    private static void validateCosConfiguration(WatsonxAiConfiguration config) {
        if (config.getCosUrl() == null || config.getCosUrl().isEmpty()) {
            throw new IllegalArgumentException("COS URL is required for text extraction operations");
        }
        if (config.getDocumentConnectionId() == null || config.getDocumentConnectionId().isEmpty()) {
            throw new IllegalArgumentException("Document connection ID is required for text extraction operations");
        }
        if (config.getDocumentBucket() == null || config.getDocumentBucket().isEmpty()) {
            throw new IllegalArgumentException("Document bucket is required for text extraction operations");
        }
        if (config.getResultConnectionId() == null || config.getResultConnectionId().isEmpty()) {
            throw new IllegalArgumentException("Result connection ID is required for text extraction operations");
        }
        if (config.getResultBucket() == null || config.getResultBucket().isEmpty()) {
            throw new IllegalArgumentException("Result bucket is required for text extraction operations");
        }
    }

    private static void validateCosConfigurationForClassification(WatsonxAiConfiguration config) {
        if (config.getCosUrl() == null || config.getCosUrl().isEmpty()) {
            throw new IllegalArgumentException("COS URL is required for text classification operations");
        }
        if (config.getDocumentConnectionId() == null || config.getDocumentConnectionId().isEmpty()) {
            throw new IllegalArgumentException("Document connection ID is required for text classification operations");
        }
        if (config.getDocumentBucket() == null || config.getDocumentBucket().isEmpty()) {
            throw new IllegalArgumentException("Document bucket is required for text classification operations");
        }
    }
}
