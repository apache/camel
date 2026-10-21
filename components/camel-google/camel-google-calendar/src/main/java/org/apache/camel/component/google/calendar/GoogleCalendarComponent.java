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
package org.apache.camel.component.google.calendar;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.api.services.calendar.Calendar;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.google.calendar.internal.GoogleCalendarApiCollection;
import org.apache.camel.component.google.calendar.internal.GoogleCalendarApiName;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.component.AbstractApiComponent;

@Component("google-calendar")
public class GoogleCalendarComponent
        extends AbstractApiComponent<GoogleCalendarApiName, GoogleCalendarConfiguration, GoogleCalendarApiCollection> {

    @Metadata
    GoogleCalendarConfiguration configuration;
    @Metadata(label = "advanced")
    private GoogleCalendarClientFactory clientFactory;
    private final Map<List<Object>, Calendar> clients = new ConcurrentHashMap<>();

    public GoogleCalendarComponent() {
        super(GoogleCalendarApiName.class, GoogleCalendarApiCollection.getCollection());
    }

    public GoogleCalendarComponent(CamelContext context) {
        super(context, GoogleCalendarApiName.class, GoogleCalendarApiCollection.getCollection());
    }

    @Override
    protected GoogleCalendarApiName getApiName(String apiNameStr) {
        return getCamelContext().getTypeConverter().convertTo(GoogleCalendarApiName.class, apiNameStr);
    }

    /**
     * Gets the client for the given configuration. One client is kept per distinct set of the options the client is
     * created from, so endpoints with the same options share a client and endpoints with different options get their
     * own.
     */
    public Calendar getClient(GoogleCalendarConfiguration config) {
        return clients.computeIfAbsent(clientKey(config), key -> createClient(config));
    }

    private Calendar createClient(GoogleCalendarConfiguration config) {
        if (config.getClientId() != null && !config.getClientId().isBlank()
                && config.getClientSecret() != null && !config.getClientSecret().isBlank()) {
            return getClientFactory().makeClient(config.getClientId(), config.getClientSecret(), config.getScopesAsList(),
                    config.getApplicationName(), config.getRefreshToken(),
                    config.getAccessToken(), config.getEmailAddress(), config.getP12FileName(), config.getUser());
        } else if (config.getServiceAccountKey() != null && !config.getServiceAccountKey().isBlank()) {
            return getClientFactory().makeClient(getCamelContext(), config.getServiceAccountKey(),
                    config.getScopesAsList(),
                    config.getApplicationName(), config.getDelegate());
        } else {
            throw new IllegalArgumentException(
                    "(clientId and clientSecret) or serviceAccountKey are required to create Google Calendar client");
        }
    }

    private static List<Object> clientKey(GoogleCalendarConfiguration config) {
        return Arrays.asList(config.getClientId(), config.getClientSecret(), config.getRefreshToken(),
                config.getAccessToken(), config.getServiceAccountKey(), config.getDelegate(), config.getScopes(),
                config.getApplicationName(), config.getEmailAddress(), config.getP12FileName(), config.getUser());
    }

    public GoogleCalendarClientFactory getClientFactory() {
        if (clientFactory == null) {
            clientFactory = new BatchGoogleCalendarClientFactory();
        }
        return clientFactory;
    }

    @Override
    public GoogleCalendarConfiguration getConfiguration() {
        if (configuration == null) {
            configuration = new GoogleCalendarConfiguration();
        }
        return super.getConfiguration();
    }

    /**
     * To use the shared configuration
     */
    @Override
    public void setConfiguration(GoogleCalendarConfiguration configuration) {
        super.setConfiguration(configuration);
    }

    /**
     * To use the GoogleCalendarClientFactory as factory for creating the client. Will by default use
     * {@link BatchGoogleCalendarClientFactory}
     */
    public void setClientFactory(GoogleCalendarClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    @Override
    protected Endpoint createEndpoint(
            String uri, String methodName, GoogleCalendarApiName apiName,
            GoogleCalendarConfiguration endpointConfiguration) {
        endpointConfiguration.setApiName(apiName);
        endpointConfiguration.setMethodName(methodName);
        return new GoogleCalendarEndpoint(uri, this, apiName, methodName, endpointConfiguration);
    }

    @Override
    protected void doStop() throws Exception {
        clients.clear();
        super.doStop();
    }
}
