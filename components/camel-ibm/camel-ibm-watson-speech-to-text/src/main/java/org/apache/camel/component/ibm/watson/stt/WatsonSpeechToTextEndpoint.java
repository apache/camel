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

package org.apache.camel.component.ibm.watson.stt;

import com.ibm.cloud.sdk.core.security.IamAuthenticator;
import com.ibm.watson.speech_to_text.v1.SpeechToText;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Convert speech audio to text using IBM Watson Speech to Text
 */
@UriEndpoint(
        firstVersion = "4.17.0",
        scheme = "ibm-watson-speech-to-text",
        title = "IBM Watson Speech to Text",
        syntax = "ibm-watson-speech-to-text:label",
        producerOnly = true,
        category = {Category.AI, Category.CLOUD},
        headersClass = WatsonSpeechToTextConstants.class)
public class WatsonSpeechToTextEndpoint extends DefaultEndpoint implements EndpointServiceLocation {

    @UriPath(description = "Logical name")
    @Metadata(required = true)
    private String label;

    @UriParam
    private WatsonSpeechToTextConfiguration configuration;

    private SpeechToText sttClient;

    public WatsonSpeechToTextEndpoint(
            String uri, WatsonSpeechToTextComponent component, WatsonSpeechToTextConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new WatsonSpeechToTextProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer not supported for IBM Watson Speech to Text");
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (configuration.getApiKey() == null) {
            throw new IllegalArgumentException("API key is required");
        }

        sttClient = createSttClient();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        sttClient = null;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public WatsonSpeechToTextConfiguration getConfiguration() {
        return configuration;
    }

    public SpeechToText getSttClient() {
        return sttClient;
    }

    @Override
    public String getServiceUrl() {
        return configuration.getServiceUrl();
    }

    @Override
    public String getServiceProtocol() {
        return "https";
    }

    private SpeechToText createSttClient() {
        IamAuthenticator authenticator = new IamAuthenticator(configuration.getApiKey());
        SpeechToText service = new SpeechToText(authenticator);

        if (configuration.getServiceUrl() != null) {
            service.setServiceUrl(configuration.getServiceUrl());
        }

        return service;
    }
}
