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
package org.apache.camel.component.google.sheets.stream;

import java.util.Map;

import com.google.api.services.sheets.v4.Sheets;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.google.sheets.BatchGoogleSheetsClientFactory;
import org.apache.camel.component.google.sheets.GoogleSheetsClientFactory;
import org.apache.camel.component.google.sheets.GoogleSheetsVerifierExtension;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

/**
 * Represents the component that manages {@link GoogleSheetsStreamEndpoint}.
 */
@Metadata(label = "verifiers", enums = "parameters,connectivity")
@Component("google-sheets-stream")
public class GoogleSheetsStreamComponent extends DefaultComponent {

    @Metadata(label = "advanced")
    private Sheets client;
    @Metadata(label = "advanced")
    private GoogleSheetsClientFactory clientFactory;
    @Metadata
    private GoogleSheetsStreamConfiguration configuration;

    public GoogleSheetsStreamComponent() {
        this(null);
    }

    public GoogleSheetsStreamComponent(CamelContext context) {
        super(context);
        registerExtension(new GoogleSheetsVerifierExtension("google-sheets-stream", context));
        this.configuration = new GoogleSheetsStreamConfiguration();
    }

    public Sheets getClient(GoogleSheetsStreamConfiguration endpointConfiguration) {
        if (client == null) {
            client = getClientFactory().makeClient(endpointConfiguration.getClientId(),
                                                endpointConfiguration.getClientSecret(),
                                                endpointConfiguration.getApplicationName(),
                                                endpointConfiguration.getRefreshToken(),
                                                endpointConfiguration.getAccessToken());
        }
        return client;
    }

    public GoogleSheetsClientFactory getClientFactory() {
        if (clientFactory == null) {
            clientFactory = new BatchGoogleSheetsClientFactory();
        }
        return clientFactory;
    }

    public GoogleSheetsStreamConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * To use the shared configuration
     */
    public void setConfiguration(GoogleSheetsStreamConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * To use the GoogleSheetsClientFactory as factory for creating the client.
     * Will by default use {@link BatchGoogleSheetsClientFactory}
     */
    public void setClientFactory(GoogleSheetsClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        final GoogleSheetsStreamConfiguration configuration = this.configuration.copy();
        GoogleSheetsStreamEndpoint endpoint = new GoogleSheetsStreamEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);
        return endpoint;
    }
}
