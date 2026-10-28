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

import java.util.Map;

import com.google.cloud.aiplatform.v1.PredictionServiceClient;
import com.google.genai.Client;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interact with Google Cloud Vertex AI generative models.
 *
 * Google Vertex AI Endpoint definition represents a model endpoint and contains configuration to customize the behavior
 * of the Producer.
 */
@UriEndpoint(firstVersion = "4.17.0", scheme = "google-vertexai", title = "Google Vertex AI",
             syntax = "google-vertexai:projectId:location:modelId",
             category = { Category.AI, Category.CLOUD }, headersClass = GoogleVertexAIConstants.class,
             producerOnly = true)
@BodyTypes(producerIn = @BodyType(value = { Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE },
                                  byOption = {
                                          @BodyType.When(option = "operation", value = "generateEmbeddings",
                                                         carriers = {
                                                                 Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE,
                                                                 Carrier.LIST }),
                                          @BodyType.When(option = "operation", value = "generateMultimodal",
                                                         carriers = { Carrier.TEXT, Carrier.BYTES }),
                                          @BodyType.When(option = "operation", value = "rawPredict",
                                                         carriers = { Carrier.TEXT, Carrier.MAP }),
                                          @BodyType.When(option = "operation", value = "streamRawPredict",
                                                         carriers = { Carrier.TEXT, Carrier.MAP }) },
                                  description = "The prompt for generateText, the default, and the other generate operations,"
                                                + " unless the CamelGoogleVertexAIPrompt header has it: bytes, a stream or a"
                                                + " file are read as text, a Map or a POJO is sent as its toString(), and no"
                                                + " body fails. generateEmbeddings also takes a List of texts. generateMultimodal"
                                                + " takes the text, or the bytes of an image with the"
                                                + " CamelGoogleVertexAIMediaMimeType header, only when the"
                                                + " CamelGoogleVertexAIMediaData and CamelGoogleVertexAIMediaGcsUri headers are"
                                                + " not set: a stream or a file is not used. rawPredict and streamRawPredict take"
                                                + " the JSON request as a String or a Map, or a plain text prompt: bytes, a"
                                                + " stream or a file fail, so convert them to a String first"),
           producerOut = @BodyType(value = Carrier.TEXT,
                                   byOption = {
                                           @BodyType.When(option = "streamOutputMode", value = "chunks",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<String>"),
                                           @BodyType.When(option = "operation", value = "generateImage",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<com.google.genai.types.Image>"),
                                           @BodyType.When(option = "operation", value = "generateEmbeddings",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<java.util.List<Float>>") },
                                   description = "The text the model answers for generateText, the default, and the other"
                                                 + " generate operations, JSON with jsonMode=true, with the token counts and the"
                                                 + " finish reason in headers. generateChatStreaming joins the chunks, or gives"
                                                 + " the List of them with streamOutputMode=chunks. The List of the generated"
                                                 + " images for generateImage, and one vector of Floats per input text for"
                                                 + " generateEmbeddings. rawPredict and streamRawPredict give the JSON the model"
                                                 + " answers, or for publisher anthropic the text of Claude with the JSON in the"
                                                 + " CamelGoogleVertexAIRawResponse header"))
public class GoogleVertexAIEndpoint extends ScheduledPollEndpoint implements EndpointServiceLocation {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleVertexAIEndpoint.class);

    @UriParam
    private GoogleVertexAIConfiguration configuration;

    private Client client;
    private boolean clientCreatedHere;

    private PredictionServiceClient predictionServiceClient;
    private boolean predictionClientCreatedHere;

    public GoogleVertexAIEndpoint(String uri, GoogleVertexAIComponent component,
                                  GoogleVertexAIConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new GoogleVertexAIProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer is not supported for this component");
    }

    @Override
    public GoogleVertexAIComponent getComponent() {
        return (GoogleVertexAIComponent) super.getComponent();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // Initialize GenAI client for Gemini models
        this.client = configuration.getClient();
        if (this.client == null) {
            this.client = GoogleVertexAIConnectionFactory.create(this.getCamelContext(), configuration);
            this.clientCreatedHere = true;
        }

        // Initialize PredictionServiceClient for rawPredict operations (partner models)
        this.predictionServiceClient = configuration.getPredictionServiceClient();
        if (this.predictionServiceClient == null) {
            this.predictionServiceClient = PredictionServiceClientFactory.create(this.getCamelContext(), configuration);
            this.predictionClientCreatedHere = true;
        }

        LOG.debug("Vertex AI endpoint started for project: {}, location: {}, model: {}",
                configuration.getProjectId(), configuration.getLocation(), configuration.getModelId());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (this.client != null && this.clientCreatedHere) {
            this.client.close();
            this.client = null;
        }

        if (this.predictionServiceClient != null && this.predictionClientCreatedHere) {
            this.predictionServiceClient.close();
            this.predictionServiceClient = null;
        }
    }

    public GoogleVertexAIConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Setup configuration
     */
    public void setConfiguration(GoogleVertexAIConfiguration configuration) {
        this.configuration = configuration;
    }

    public Client getClient() {
        return client;
    }

    public PredictionServiceClient getPredictionServiceClient() {
        return predictionServiceClient;
    }

    @Override
    public String getServiceUrl() {
        if (ObjectHelper.isNotEmpty(configuration.getProjectId())
                && ObjectHelper.isNotEmpty(configuration.getLocation())
                && ObjectHelper.isNotEmpty(configuration.getModelId())) {
            return getServiceProtocol() + ":" + configuration.getProjectId() + ":"
                   + configuration.getLocation() + ":" + configuration.getModelId();
        }
        return null;
    }

    @Override
    public String getServiceProtocol() {
        return "vertexai";
    }

    @Override
    public Map<String, String> getServiceMetadata() {
        if (configuration.getModelId() != null) {
            return Map.of("modelId", configuration.getModelId());
        }
        return null;
    }
}
