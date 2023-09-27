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
package org.apache.camel.component.google.mail;

import com.google.api.services.gmail.Gmail;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.google.mail.internal.GoogleMailApiCollection;
import org.apache.camel.component.google.mail.internal.GoogleMailApiName;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.component.AbstractApiComponent;

@Component("google-mail")
public class GoogleMailComponent
        extends AbstractApiComponent<GoogleMailApiName, GoogleMailConfiguration, GoogleMailApiCollection> {

    @Metadata
    GoogleMailConfiguration configuration;
    @Metadata(label = "advanced")
    private Gmail client;
    @Metadata(label = "advanced")
    private GoogleMailClientFactory clientFactory;

    public GoogleMailComponent() {
        super(GoogleMailApiName.class, GoogleMailApiCollection.getCollection());
        registerExtension(new GoogleMailComponentVerifierExtension());
    }

    public GoogleMailComponent(CamelContext context) {
        super(context, GoogleMailApiName.class, GoogleMailApiCollection.getCollection());
        registerExtension(new GoogleMailComponentVerifierExtension());
    }

    @Override
    protected GoogleMailApiName getApiName(String apiNameStr) {
        return getCamelContext().getTypeConverter().convertTo(GoogleMailApiName.class, apiNameStr);
    }

    public Gmail getClient(GoogleMailConfiguration config) {
        if (client == null) {
            if (config.getClientId() != null && !config.getClientId().isBlank()
                    && config.getClientSecret() != null && !config.getClientSecret().isBlank()) {
                client = getClientFactory().makeClient(config.getClientId(),
                        config.getClientSecret(), config.getScopes(),
                        config.getApplicationName(), config.getRefreshToken(), config.getAccessToken());
            } else if (config.getServiceAccountKey() != null && !config.getServiceAccountKey().isBlank()) {
                client = getClientFactory().makeClient(getCamelContext(), config.getServiceAccountKey(),
                        config.getScopes(), config.getApplicationName(), config.getDelegate());
            } else {
                throw new IllegalArgumentException(
                        "(clientId and clientSecret) or serviceAccountKey are required to create Gmail client");
            }
        }
        return client;
    }

    public GoogleMailClientFactory getClientFactory() {
        if (clientFactory == null) {
            clientFactory = new BatchGoogleMailClientFactory();
        }
        return clientFactory;
    }

    /**
     * To use the shared configuration
     */
    @Override
    public void setConfiguration(GoogleMailConfiguration configuration) {
        super.setConfiguration(configuration);
    }

    @Override
    public GoogleMailConfiguration getConfiguration() {
        if (configuration == null) {
            configuration = new GoogleMailConfiguration();
        }
        return super.getConfiguration();
    }

    /**
     * To use the GoogleCalendarClientFactory as factory for creating the client. Will by default use
     * {@link BatchGoogleMailClientFactory}
     */
    public void setClientFactory(GoogleMailClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    @Override
    protected Endpoint createEndpoint(
            String uri, String methodName, GoogleMailApiName apiName, GoogleMailConfiguration endpointConfiguration) {
        endpointConfiguration.setApiName(apiName);
        endpointConfiguration.setMethodName(methodName);
        return new GoogleMailEndpoint(uri, this, apiName, methodName, endpointConfiguration);
    }
}
