/**
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
package org.apache.camel.component.google.sheets;

import com.google.api.services.sheets.v4.Sheets;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.google.sheets.internal.GoogleSheetsApiCollection;
import org.apache.camel.component.google.sheets.internal.GoogleSheetsApiName;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.component.AbstractApiComponent;

/**
 * Represents the component that manages {@link GoogleSheetsEndpoint}.
 */
@Metadata(label = "verifiers", enums = "parameters,connectivity")
public class GoogleSheetsComponent extends AbstractApiComponent<GoogleSheetsApiName, GoogleSheetsConfiguration, GoogleSheetsApiCollection> {

    @Metadata(label = "advanced")
    private Sheets client;
    @Metadata(label = "advanced")
    private GoogleSheetsClientFactory clientFactory;

    public GoogleSheetsComponent() {
        super(GoogleSheetsEndpoint.class, GoogleSheetsApiName.class, GoogleSheetsApiCollection.getCollection());
        registerExtension(new GoogleSheetsVerifierExtension("google-sheets"));
    }

    public GoogleSheetsComponent(CamelContext context) {
        super(context, GoogleSheetsEndpoint.class, GoogleSheetsApiName.class, GoogleSheetsApiCollection.getCollection());
        registerExtension(new GoogleSheetsVerifierExtension("google-sheets", context));
    }

    @Override
    protected GoogleSheetsApiName getApiName(String apiNameStr) throws IllegalArgumentException {
        return GoogleSheetsApiName.fromValue(apiNameStr);
    }

    public Sheets getClient(GoogleSheetsConfiguration config) {
        if (client == null) {
            client = getClientFactory().makeClient(config.getClientId(),
                    config.getClientSecret(),
                    config.getApplicationName(),
                    config.getRefreshToken(),
                    config.getAccessToken());
        }
        return client;
    }

    public GoogleSheetsClientFactory getClientFactory() {
        if (clientFactory == null) {
            clientFactory = new BatchGoogleSheetsClientFactory();
        }
        return clientFactory;
    }

    /**
     * To use the shared configuration
     */
    @Override
    public void setConfiguration(GoogleSheetsConfiguration configuration) {
        super.setConfiguration(configuration);
    }

    @Override
    public GoogleSheetsConfiguration getConfiguration() {
        if (configuration == null) {
            configuration = new GoogleSheetsConfiguration();
        }
        return super.getConfiguration();
    }

    /**
     * To use the GoogleSheetsClientFactory as factory for creating the client.
     * Will by default use {@link BatchGoogleSheetsClientFactory}
     */
    public void setClientFactory(GoogleSheetsClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String methodName, GoogleSheetsApiName apiName,
                                      GoogleSheetsConfiguration endpointConfiguration) {
        endpointConfiguration.setApiName(apiName);
        endpointConfiguration.setMethodName(methodName);
        return new GoogleSheetsEndpoint(uri, this, apiName, methodName, endpointConfiguration);
    }
}
