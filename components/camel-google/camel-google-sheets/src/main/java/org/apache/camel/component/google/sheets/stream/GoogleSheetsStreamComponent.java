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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.api.services.sheets.v4.Sheets;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.google.sheets.BatchGoogleSheetsClientFactory;
import org.apache.camel.component.google.sheets.GoogleSheetsClientFactory;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.HealthCheckComponent;

@Metadata(label = "verifiers", enums = "parameters,connectivity")
@Component("google-sheets-stream")
public class GoogleSheetsStreamComponent extends HealthCheckComponent {

    @Metadata
    private GoogleSheetsStreamConfiguration configuration;
    @Metadata(label = "advanced")
    private GoogleSheetsClientFactory clientFactory;
    private final Map<List<Object>, Sheets> clients = new ConcurrentHashMap<>();

    public GoogleSheetsStreamComponent() {
        this(null);
    }

    public GoogleSheetsStreamComponent(CamelContext context) {
        super(context);
        this.configuration = new GoogleSheetsStreamConfiguration();
    }

    /**
     * Gets the client for the given configuration. One client is kept per distinct set of the options the client is
     * created from, so endpoints with the same options share a client and endpoints with different options get their
     * own.
     */
    public Sheets getClient(GoogleSheetsStreamConfiguration config) {
        return clients.computeIfAbsent(clientKey(config), key -> createClient(config));
    }

    private Sheets createClient(GoogleSheetsStreamConfiguration config) {
        if (config.getClientId() != null && config.getClientSecret() != null) {
            return getClientFactory().makeClient(config.getClientId(),
                    config.getClientSecret(), config.getScopesAsList(),
                    config.getApplicationName(), config.getRefreshToken(), config.getAccessToken());
        }
        // Use service account key or fall back to Application Default Credentials
        return getClientFactory().makeClient(getCamelContext(), config.getServiceAccountKey(),
                config.getScopesAsList(), config.getApplicationName(), config.getDelegate());
    }

    private static List<Object> clientKey(GoogleSheetsStreamConfiguration config) {
        return Arrays.asList(config.getClientId(), config.getClientSecret(), config.getRefreshToken(),
                config.getAccessToken(), config.getServiceAccountKey(), config.getDelegate(), config.getScopes(),
                config.getApplicationName());
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
     * To use the GoogleSheetsClientFactory as factory for creating the client. Will by default use
     * {@link BatchGoogleSheetsClientFactory}
     */
    public void setClientFactory(GoogleSheetsClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        final GoogleSheetsStreamConfiguration configuration = this.configuration.copy();
        configuration.setSpreadsheetId(remaining);
        GoogleSheetsStreamEndpoint endpoint = new GoogleSheetsStreamEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    @Override
    protected void doStop() throws Exception {
        clients.clear();
        super.doStop();
    }
}
