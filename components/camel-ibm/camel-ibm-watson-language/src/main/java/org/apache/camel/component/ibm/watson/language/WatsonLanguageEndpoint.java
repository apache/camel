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

package org.apache.camel.component.ibm.watson.language;

import com.ibm.cloud.sdk.core.security.IamAuthenticator;
import com.ibm.watson.natural_language_understanding.v1.NaturalLanguageUnderstanding;
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
 * Perform natural language processing using IBM Watson Natural Language Understanding
 */
@UriEndpoint(
        firstVersion = "4.16.0",
        scheme = "ibm-watson-language",
        title = "IBM Watson Language",
        syntax = "ibm-watson-language:label",
        producerOnly = true,
        category = {Category.AI, Category.CLOUD},
        headersClass = WatsonLanguageConstants.class)
public class WatsonLanguageEndpoint extends DefaultEndpoint implements EndpointServiceLocation {

    @UriPath(description = "Logical name")
    @Metadata(required = true)
    private String label;

    @UriParam
    private WatsonLanguageConfiguration configuration;

    private NaturalLanguageUnderstanding nluClient;

    public WatsonLanguageEndpoint(
            String uri, WatsonLanguageComponent component, WatsonLanguageConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new WatsonLanguageProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer not supported for IBM Watson Language");
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (configuration.getApiKey() == null) {
            throw new IllegalArgumentException("API key is required");
        }

        nluClient = createNluClient();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        nluClient = null;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public WatsonLanguageConfiguration getConfiguration() {
        return configuration;
    }

    public NaturalLanguageUnderstanding getNluClient() {
        return nluClient;
    }

    @Override
    public String getServiceUrl() {
        return configuration.getServiceUrl();
    }

    @Override
    public String getServiceProtocol() {
        return "https";
    }

    private NaturalLanguageUnderstanding createNluClient() {
        IamAuthenticator authenticator = new IamAuthenticator(configuration.getApiKey());
        NaturalLanguageUnderstanding service = new NaturalLanguageUnderstanding("2022-04-07", authenticator);

        if (configuration.getServiceUrl() != null) {
            service.setServiceUrl(configuration.getServiceUrl());
        }

        return service;
    }
}
