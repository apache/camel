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

import com.ibm.watsonx.ai.chat.ChatService;
import com.ibm.watsonx.ai.deployment.DeploymentService;
import com.ibm.watsonx.ai.detection.DetectionService;
import com.ibm.watsonx.ai.embedding.EmbeddingService;
import com.ibm.watsonx.ai.foundationmodel.FoundationModelService;
import com.ibm.watsonx.ai.rerank.RerankService;
import com.ibm.watsonx.ai.textgeneration.TextGenerationService;
import com.ibm.watsonx.ai.textprocessing.textclassification.TextClassificationService;
import com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionService;
import com.ibm.watsonx.ai.timeseries.TimeSeriesService;
import com.ibm.watsonx.ai.tokenization.TokenizationService;
import com.ibm.watsonx.ai.tool.ToolService;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.ibm.watsonx.ai.service.WatsonxAiServiceFactory;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Interact with IBM watsonx.ai foundation models for text generation, chat, embeddings, and more.
 */
@UriEndpoint(firstVersion = "4.18.0",
             scheme = "ibm-watsonx-ai",
             title = "IBM watsonx.ai",
             syntax = "ibm-watsonx-ai:label",
             producerOnly = true,
             category = { Category.AI, Category.CLOUD },
             headersClass = WatsonxAiConstants.class)
public class WatsonxAiEndpoint extends DefaultEndpoint implements EndpointServiceLocation {

    @UriPath(description = "Logical name for the endpoint")
    @Metadata(required = true)
    private String label;

    @UriParam
    private WatsonxAiConfiguration configuration;

    // Lazy-initialized service clients
    private TextGenerationService textGenerationService;
    private ChatService chatService;
    private EmbeddingService embeddingService;
    private RerankService rerankService;
    private TokenizationService tokenizationService;
    private DetectionService detectionService;
    private TextExtractionService textExtractionService;
    private TextClassificationService textClassificationService;
    private TimeSeriesService timeSeriesService;
    private FoundationModelService foundationModelService;
    private DeploymentService deploymentService;
    private ToolService toolService;

    public WatsonxAiEndpoint(String uri, WatsonxAiComponent component, WatsonxAiConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new WatsonxAiProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer not supported for IBM watsonx.ai");
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        validateConfiguration();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        textGenerationService = null;
        chatService = null;
        embeddingService = null;
        rerankService = null;
        tokenizationService = null;
        detectionService = null;
        textExtractionService = null;
        textClassificationService = null;
        timeSeriesService = null;
        foundationModelService = null;
        deploymentService = null;
        toolService = null;
    }

    private void validateConfiguration() {
        // processToolCalls is a local operation that doesn't require API credentials
        // It uses the ToolRegistry from headers to execute tool calls
        if (configuration.getOperation() == WatsonxAiOperations.processToolCalls) {
            return;
        }

        if (configuration.getApiKey() == null || configuration.getApiKey().isEmpty()) {
            throw new IllegalArgumentException("API key is required");
        }
        if (configuration.getBaseUrl() == null || configuration.getBaseUrl().isEmpty()) {
            throw new IllegalArgumentException("Base URL is required");
        }
    }

    /**
     * Gets or creates the TextGenerationService instance.
     */
    public TextGenerationService getTextGenerationService() {
        if (textGenerationService == null) {
            textGenerationService = WatsonxAiServiceFactory.createTextGenerationService(configuration);
        }
        return textGenerationService;
    }

    /**
     * Gets or creates the ChatService instance.
     */
    public ChatService getChatService() {
        if (chatService == null) {
            chatService = WatsonxAiServiceFactory.createChatService(configuration);
        }
        return chatService;
    }

    /**
     * Gets or creates the EmbeddingService instance.
     */
    public EmbeddingService getEmbeddingService() {
        if (embeddingService == null) {
            embeddingService = WatsonxAiServiceFactory.createEmbeddingService(configuration);
        }
        return embeddingService;
    }

    /**
     * Gets or creates the RerankService instance.
     */
    public RerankService getRerankService() {
        if (rerankService == null) {
            rerankService = WatsonxAiServiceFactory.createRerankService(configuration);
        }
        return rerankService;
    }

    /**
     * Gets or creates the TokenizationService instance.
     */
    public TokenizationService getTokenizationService() {
        if (tokenizationService == null) {
            tokenizationService = WatsonxAiServiceFactory.createTokenizationService(configuration);
        }
        return tokenizationService;
    }

    /**
     * Gets or creates the DetectionService instance.
     */
    public DetectionService getDetectionService() {
        if (detectionService == null) {
            detectionService = WatsonxAiServiceFactory.createDetectionService(configuration);
        }
        return detectionService;
    }

    /**
     * Gets or creates the TextExtractionService instance.
     */
    public TextExtractionService getTextExtractionService() {
        if (textExtractionService == null) {
            textExtractionService = WatsonxAiServiceFactory.createTextExtractionService(configuration);
        }
        return textExtractionService;
    }

    /**
     * Gets or creates the TextClassificationService instance.
     */
    public TextClassificationService getTextClassificationService() {
        if (textClassificationService == null) {
            textClassificationService = WatsonxAiServiceFactory.createTextClassificationService(configuration);
        }
        return textClassificationService;
    }

    /**
     * Gets or creates the TimeSeriesService instance.
     */
    public TimeSeriesService getTimeSeriesService() {
        if (timeSeriesService == null) {
            timeSeriesService = WatsonxAiServiceFactory.createTimeSeriesService(configuration);
        }
        return timeSeriesService;
    }

    /**
     * Gets or creates the FoundationModelService instance.
     */
    public FoundationModelService getFoundationModelService() {
        if (foundationModelService == null) {
            foundationModelService = WatsonxAiServiceFactory.createFoundationModelService(configuration);
        }
        return foundationModelService;
    }

    /**
     * Gets or creates the DeploymentService instance.
     */
    public DeploymentService getDeploymentService() {
        if (deploymentService == null) {
            deploymentService = WatsonxAiServiceFactory.createDeploymentService(configuration);
        }
        return deploymentService;
    }

    /**
     * Gets or creates the ToolService instance.
     */
    public ToolService getToolService() {
        if (toolService == null) {
            toolService = WatsonxAiServiceFactory.createToolService(configuration);
        }
        return toolService;
    }

    // Getters and Setters

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public WatsonxAiConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(WatsonxAiConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public String getServiceUrl() {
        return configuration.getBaseUrl();
    }

    @Override
    public String getServiceProtocol() {
        return "https";
    }
}
