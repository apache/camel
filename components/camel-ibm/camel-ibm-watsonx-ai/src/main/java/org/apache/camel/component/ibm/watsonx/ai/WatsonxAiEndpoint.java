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

import java.util.function.Consumer;

import com.ibm.watsonx.ai.chat.ChatService;
import com.ibm.watsonx.ai.core.auth.Authenticator;
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
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.ibm.watsonx.ai.service.OAuthProfileAuthenticator;
import org.apache.camel.component.ibm.watsonx.ai.service.WatsonxAiServiceFactory;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
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
@BodyTypes(producerIn = @BodyType(value = Carrier.ANY,
                                  byOption = {
                                          @BodyType.When(option = "operation", value = "chat",
                                                         carriers = {
                                                                 Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.LIST }),
                                          @BodyType.When(option = "operation", value = "chatStreaming",
                                                         carriers = {
                                                                 Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.LIST }),
                                          @BodyType.When(option = "operation", value = "deploymentChat",
                                                         carriers = {
                                                                 Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.LIST }),
                                          @BodyType.When(option = "operation", value = "embedding",
                                                         carriers = {
                                                                 Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.LIST }),
                                          @BodyType.When(option = "operation", value = "rerank",
                                                         carriers = {
                                                                 Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.LIST }),
                                          @BodyType.When(option = "operation", value = "textExtractionUpload",
                                                         carriers = {
                                                                 Carrier.FILE, Carrier.STREAM, Carrier.BYTES, Carrier.TEXT }),
                                          @BodyType.When(option = "operation", value = "textExtractionUploadAndFetch",
                                                         carriers = {
                                                                 Carrier.FILE, Carrier.STREAM, Carrier.BYTES, Carrier.TEXT }),
                                          @BodyType.When(option = "operation", value = "textExtractionUploadFile",
                                                         carriers = {
                                                                 Carrier.FILE, Carrier.STREAM, Carrier.BYTES, Carrier.TEXT }),
                                          @BodyType.When(option = "operation", value = "textClassificationUpload",
                                                         carriers = {
                                                                 Carrier.FILE, Carrier.STREAM, Carrier.BYTES, Carrier.TEXT }),
                                          @BodyType.When(option = "operation", value = "textClassificationUploadAndFetch",
                                                         carriers = {
                                                                 Carrier.FILE, Carrier.STREAM, Carrier.BYTES, Carrier.TEXT }),
                                          @BodyType.When(option = "operation", value = "textClassificationUploadFile",
                                                         carriers = {
                                                                 Carrier.FILE, Carrier.STREAM, Carrier.BYTES, Carrier.TEXT }),
                                          @BodyType.When(option = "operation", value = "runTool",
                                                         carriers = { Carrier.TEXT, Carrier.MAP }) },
                                  description = "The prompt or text for textGeneration, tokenize, detect, detectContext (the input),"
                                                + " detectGenerated (the generated text) and deploymentGenerate,"
                                                + " the file path, ID or file name for the text extraction and classification"
                                                + " operations that do not upload, read as text unless a header such as"
                                                + " CamelIBMWatsonxAiInput gives it: a Map or a POJO fails, as Camel reads"
                                                + " it only as its toString()."
                                                + " chat, chatStreaming and deploymentChat take the user message, read as text the"
                                                + " same way, a ChatMessage, or the conversation as a List of ChatMessage in which a"
                                                + " String is a user message (any other element fails); embedding and rerank take"
                                                + " one text, read as text the same way, or a List of Strings. The upload"
                                                + " operations take a file, a stream or bytes, with the CamelIBMWatsonxAiFileName"
                                                + " header unless it is a file. runTool takes a Map (structured) or a String;"
                                                + " forecast a TimeSeriesRequest unless the CamelIBMWatsonxAiForecastInputSchema and"
                                                + " CamelIBMWatsonxAiForecastData headers give it"),
           producerOut = @BodyType(value = Carrier.TEXT, javaType = "java.lang.String",
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "embedding", carriers = Carrier.LIST),
                                           @BodyType.When(option = "operation", value = "rerank", carriers = Carrier.LIST),
                                           @BodyType.When(option = "operation", value = "tokenize", carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Integer"),
                                           @BodyType.When(option = "operation", value = "detect", carriers = Carrier.LIST),
                                           @BodyType.When(option = "operation", value = "detectContext",
                                                          carriers = Carrier.LIST),
                                           @BodyType.When(option = "operation", value = "detectGenerated",
                                                          carriers = Carrier.LIST),
                                           @BodyType.When(option = "operation", value = "forecast", carriers = Carrier.LIST),
                                           @BodyType.When(option = "operation", value = "deploymentForecast",
                                                          carriers = Carrier.LIST),
                                           @BodyType.When(option = "operation", value = "listModels", carriers = Carrier.LIST),
                                           @BodyType.When(option = "operation", value = "listTasks", carriers = Carrier.LIST),
                                           @BodyType.When(option = "operation", value = "listTools", carriers = Carrier.LIST),
                                           @BodyType.When(option = "operation", value = "textExtraction",
                                                          carriers = Carrier.POJO,
                                                          javaType = "com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionResponse"),
                                           @BodyType.When(option = "operation", value = "textExtractionFetch",
                                                          carriers = Carrier.POJO,
                                                          javaType = "com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionResponse"),
                                           @BodyType.When(option = "operation", value = "textExtractionUpload",
                                                          carriers = Carrier.POJO,
                                                          javaType = "com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionResponse"),
                                           @BodyType.When(option = "operation", value = "textExtractionUploadFile",
                                                          carriers = Carrier.VALUE, javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "textExtractionDeleteFile",
                                                          carriers = Carrier.VALUE, javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "textExtractionDeleteRequest",
                                                          carriers = Carrier.VALUE, javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "textClassification",
                                                          carriers = Carrier.POJO,
                                                          javaType = "com.ibm.watsonx.ai.textprocessing.textclassification.TextClassificationResponse"),
                                           @BodyType.When(option = "operation", value = "textClassificationFetch",
                                                          carriers = Carrier.POJO,
                                                          javaType = "com.ibm.watsonx.ai.textprocessing.textclassification.TextClassificationResponse"),
                                           @BodyType.When(option = "operation", value = "textClassificationUpload",
                                                          carriers = Carrier.POJO,
                                                          javaType = "com.ibm.watsonx.ai.textprocessing.textclassification.TextClassificationResponse"),
                                           @BodyType.When(option = "operation", value = "textClassificationUploadAndFetch",
                                                          carriers = Carrier.POJO,
                                                          javaType = "com.ibm.watsonx.ai.textprocessing.textclassification.TextClassificationResponse$ClassificationResult"),
                                           @BodyType.When(option = "operation", value = "textClassificationAndFetch",
                                                          carriers = Carrier.POJO,
                                                          javaType = "com.ibm.watsonx.ai.textprocessing.textclassification.TextClassificationResponse$ClassificationResult"),
                                           @BodyType.When(option = "operation", value = "textClassificationUploadFile",
                                                          carriers = Carrier.VALUE, javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "textClassificationDeleteFile",
                                                          carriers = Carrier.VALUE, javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "textClassificationDeleteRequest",
                                                          carriers = Carrier.VALUE, javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "deploymentInfo",
                                                          carriers = Carrier.POJO,
                                                          javaType = "com.ibm.watsonx.ai.deployment.DeploymentResource"),
                                           @BodyType.When(option = "operation", value = "processToolCalls",
                                                          carriers = Carrier.UNCHANGED) },
                                   description = "The generated text for textGeneration, chat, deploymentGenerate, deploymentChat"
                                                 + " and the streaming operations, the extracted text for"
                                                 + " textExtractionUploadAndFetch and textExtractionReadFile, the tool output for"
                                                 + " runTool, with token counts and the stop reason in headers; textExtractionAndFetch gives the extracted text. embedding gives a"
                                                 + " List with one List of Floats per input, even for one input; rerank, detect,"
                                                 + " forecast and the list operations a List of results of the watsonx.ai SDK;"
                                                 + " tokenize the token count; the uploadFile and delete operations a Boolean."
                                                 + " detectContext and detectGenerated give a List of results of the SDK."
                                                 + " processToolCalls leaves the body and puts the messages in the"
                                                 + " CamelIBMWatsonxAiMessages header"))
public class WatsonxAiEndpoint extends DefaultEndpoint implements EndpointServiceLocation {

    @UriPath(description = "Logical name for the endpoint")
    @Metadata(required = true)
    private String label;

    @UriParam
    private WatsonxAiConfiguration configuration;

    // Shared by the services of this endpoint, so that they share one access token
    private Authenticator authenticator;
    // Sends the chunks of the streaming operations to the streamEndpoint
    private ProducerTemplate streamTemplate;

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
    public org.apache.camel.Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer not supported for IBM watsonx.ai");
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        validateConfiguration();
        authenticator = WatsonxAiServiceFactory.createAuthenticator(configuration, getCamelContext());
        if (authenticator instanceof OAuthProfileAuthenticator) {
            // fail at startup rather than at the first exchange when the OAuth profile cannot give a token
            authenticator.token();
        }
    }

    @Override
    protected synchronized void doStop() throws Exception {
        super.doStop();
        authenticator = null;
        if (streamTemplate != null) {
            streamTemplate.stop();
            streamTemplate = null;
        }
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

        if ((configuration.getApiKey() == null || configuration.getApiKey().isEmpty())
                && (configuration.getOauthProfile() == null || configuration.getOauthProfile().isEmpty())
                && configuration.getAuthenticator() == null) {
            throw new IllegalArgumentException("API key is required (or set oauthProfile or authenticator)");
        }
        if (configuration.getBaseUrl() == null || configuration.getBaseUrl().isEmpty()) {
            throw new IllegalArgumentException("Base URL is required");
        }
    }

    /**
     * Gets or creates the TextGenerationService instance.
     */
    public synchronized TextGenerationService getTextGenerationService() {
        if (textGenerationService == null) {
            textGenerationService = WatsonxAiServiceFactory.createTextGenerationService(configuration, authenticator);
        }
        return textGenerationService;
    }

    /**
     * Gets or creates the ChatService instance.
     */
    public synchronized ChatService getChatService() {
        if (chatService == null) {
            chatService = WatsonxAiServiceFactory.createChatService(configuration, authenticator);
        }
        return chatService;
    }

    /**
     * Gets or creates the EmbeddingService instance.
     */
    public synchronized EmbeddingService getEmbeddingService() {
        if (embeddingService == null) {
            embeddingService = WatsonxAiServiceFactory.createEmbeddingService(configuration, authenticator);
        }
        return embeddingService;
    }

    /**
     * Gets or creates the RerankService instance.
     */
    public synchronized RerankService getRerankService() {
        if (rerankService == null) {
            rerankService = WatsonxAiServiceFactory.createRerankService(configuration, authenticator);
        }
        return rerankService;
    }

    /**
     * Gets or creates the TokenizationService instance.
     */
    public synchronized TokenizationService getTokenizationService() {
        if (tokenizationService == null) {
            tokenizationService = WatsonxAiServiceFactory.createTokenizationService(configuration, authenticator);
        }
        return tokenizationService;
    }

    /**
     * Gets or creates the DetectionService instance.
     */
    public synchronized DetectionService getDetectionService() {
        if (detectionService == null) {
            detectionService = WatsonxAiServiceFactory.createDetectionService(configuration, authenticator);
        }
        return detectionService;
    }

    /**
     * Gets or creates the TextExtractionService instance.
     */
    public synchronized TextExtractionService getTextExtractionService() {
        if (textExtractionService == null) {
            textExtractionService = WatsonxAiServiceFactory.createTextExtractionService(configuration, authenticator);
        }
        return textExtractionService;
    }

    /**
     * Gets or creates the TextClassificationService instance.
     */
    public synchronized TextClassificationService getTextClassificationService() {
        if (textClassificationService == null) {
            textClassificationService = WatsonxAiServiceFactory.createTextClassificationService(configuration, authenticator);
        }
        return textClassificationService;
    }

    /**
     * Gets or creates the TimeSeriesService instance.
     */
    public synchronized TimeSeriesService getTimeSeriesService() {
        if (timeSeriesService == null) {
            timeSeriesService = WatsonxAiServiceFactory.createTimeSeriesService(configuration, authenticator);
        }
        return timeSeriesService;
    }

    /**
     * Gets or creates the FoundationModelService instance.
     */
    public synchronized FoundationModelService getFoundationModelService() {
        if (foundationModelService == null) {
            foundationModelService = WatsonxAiServiceFactory.createFoundationModelService(configuration);
        }
        return foundationModelService;
    }

    /**
     * Gets or creates the DeploymentService instance.
     */
    public synchronized DeploymentService getDeploymentService() {
        if (deploymentService == null) {
            deploymentService = WatsonxAiServiceFactory.createDeploymentService(configuration, authenticator);
        }
        return deploymentService;
    }

    /**
     * Gets or creates the ToolService instance.
     */
    public synchronized ToolService getToolService() {
        if (toolService == null) {
            toolService = WatsonxAiServiceFactory.createToolService(configuration, authenticator);
        }
        return toolService;
    }

    /**
     * The consumer of the chunks of the streaming operations that sends each chunk as the body of a message to the
     * streamEndpoint, or {@code null} when the option is not set.
     */
    public synchronized Consumer<String> getStreamEndpointConsumer() {
        String uri = configuration.getStreamEndpoint();
        if (uri == null || uri.isBlank()) {
            return null;
        }
        if (streamTemplate == null) {
            streamTemplate = getCamelContext().createProducerTemplate();
        }
        ProducerTemplate template = streamTemplate;
        return chunk -> template.sendBody(uri, chunk);
    }

    /**
     * The authenticator that the services of this endpoint share, created when the endpoint starts.
     */
    public Authenticator getAuthenticator() {
        return authenticator;
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
