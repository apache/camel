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
package org.apache.camel.component.google.speechtotext;

import com.google.cloud.speech.v1.SpeechClient;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * Transcribe audio to text using Google Cloud Speech-to-Text API
 */
@UriEndpoint(firstVersion = "4.19.0", scheme = "google-speech-to-text", title = "Google Cloud Speech To Text",
             syntax = "google-speech-to-text:operation", category = {
                     Category.CLOUD, Category.AI },
             producerOnly = true, headersClass = GoogleCloudSpeechToTextConstants.class)
public class GoogleCloudSpeechToTextEndpoint extends DefaultEndpoint implements EndpointServiceLocation {

    @UriParam
    private GoogleCloudSpeechToTextConfiguration configuration;

    private SpeechClient speechClient;

    public GoogleCloudSpeechToTextEndpoint(String uri, GoogleCloudSpeechToTextComponent component,
                                           GoogleCloudSpeechToTextConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new GoogleCloudSpeechToTextProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException(
                "Cannot consume from the google-speech-to-text endpoint: " + getEndpointUri());
    }

    public GoogleCloudSpeechToTextConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Set the endpoint configuration.
     *
     * @param configuration
     */
    public void setConfiguration(GoogleCloudSpeechToTextConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (configuration.getClient() != null) {
            speechClient = configuration.getClient();
        } else {
            speechClient = GoogleCloudSpeechToTextClientFactory.create(this.getCamelContext(), configuration);
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (configuration.getClient() == null && speechClient != null) {
            speechClient.close();
        }
    }

    public SpeechClient getClient() {
        return speechClient;
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
        return "speech-to-text";
    }
}
