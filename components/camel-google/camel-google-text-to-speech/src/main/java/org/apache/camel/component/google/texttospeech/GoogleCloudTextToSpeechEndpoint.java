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
package org.apache.camel.component.google.texttospeech;

import com.google.cloud.texttospeech.v1.TextToSpeechClient;
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
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * Synthesize speech from text using the Google Cloud Text-to-Speech API
 */
@UriEndpoint(firstVersion = "4.19.0", scheme = "google-text-to-speech", title = "Google Cloud Text To Speech",
             syntax = "google-text-to-speech:operation", category = {
                     Category.CLOUD, Category.AI },
             producerOnly = true, headersClass = GoogleCloudTextToSpeechConstants.class)
@BodyTypes(producerIn = @BodyType(value = Carrier.ANY,
                                  byOption = {
                                          @BodyType.When(option = "operation", value = "synthesize",
                                                         carriers = {
                                                                 Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE }),
                                          @BodyType.When(option = "operation", value = "listVoices", carriers = Carrier.ANY),
                                          @BodyType.When(option = "pojoRequest", value = "true", carriers = Carrier.POJO,
                                                         javaType = "com.google.cloud.texttospeech.v1.SynthesizeSpeechRequest") },
                                  description = "The text to speak for synthesize: bytes, a stream or a file are read as"
                                                + " text, a Map or a POJO is spoken as its toString(), so take the text out of"
                                                + " it first, and no body fails. listVoices does not use the body. With"
                                                + " pojoRequest=true a SynthesizeSpeechRequest of the Google Cloud SDK, whatever"
                                                + " the operation: another body fails"),
           producerOut = @BodyType(value = { Carrier.BYTES, Carrier.LIST },
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "synthesize", carriers = Carrier.BYTES,
                                                          javaType = "byte[]"),
                                           @BodyType.When(option = "operation", value = "listVoices", carriers = Carrier.LIST,
                                                          javaType = "java.util.List<com.google.cloud.texttospeech.v1.Voice>"),
                                           @BodyType.When(option = "pojoRequest", value = "true", carriers = Carrier.BYTES,
                                                          javaType = "byte[]") },
                                   description = "The audio of synthesize as bytes, in the audioEncoding option (MP3 by"
                                                 + " default), with the SynthesizeSpeechResponse in the"
                                                 + " CamelGoogleCloudTextToSpeechResponseObject header. The List of the voices"
                                                 + " for listVoices, of the languageCode option (en-US by default)"))
public class GoogleCloudTextToSpeechEndpoint extends DefaultEndpoint implements EndpointServiceLocation {

    @UriParam
    private GoogleCloudTextToSpeechConfiguration configuration;

    private TextToSpeechClient textToSpeechClient;

    public GoogleCloudTextToSpeechEndpoint(String uri, GoogleCloudTextToSpeechComponent component,
                                           GoogleCloudTextToSpeechConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new GoogleCloudTextToSpeechProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException(
                "Cannot consume from the google-text-to-speech endpoint: " + getEndpointUri());
    }

    public GoogleCloudTextToSpeechConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Set the endpoint configuration.
     *
     * @param configuration
     */
    public void setConfiguration(GoogleCloudTextToSpeechConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (configuration.getClient() != null) {
            textToSpeechClient = configuration.getClient();
        } else {
            textToSpeechClient = GoogleCloudTextToSpeechClientFactory.create(this.getCamelContext(), configuration);
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (configuration.getClient() == null && textToSpeechClient != null) {
            textToSpeechClient.close();
        }
    }

    public TextToSpeechClient getClient() {
        return textToSpeechClient;
    }

    @Override
    public String getServiceUrl() {
        if (ObjectHelper.isNotEmpty(configuration.getOperation())) {
            return getServiceProtocol() + ":" + configuration.getOperation();
        }
        return null;
    }

    @Override
    public String getServiceProtocol() {
        return "text-to-speech";
    }
}
