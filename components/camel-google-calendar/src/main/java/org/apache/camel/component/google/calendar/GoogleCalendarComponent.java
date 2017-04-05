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
package org.apache.camel.component.google.calendar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.api.services.calendar.Calendar;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.google.calendar.internal.GoogleCalendarApiCollection;
import org.apache.camel.component.google.calendar.internal.GoogleCalendarApiName;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.component.AbstractApiComponent;

/**
 * Represents the component that manages {@link GoogleCalendarEndpoint}.
 */
public class GoogleCalendarComponent extends AbstractApiComponent<GoogleCalendarApiName, GoogleCalendarConfiguration, GoogleCalendarApiCollection> {

    @Metadata(label = "advanced")
    private Calendar client;
    @Metadata(label = "advanced")
    private GoogleCalendarClientFactory clientFactory;

    public GoogleCalendarComponent() {
        super(GoogleCalendarEndpoint.class, GoogleCalendarApiName.class, GoogleCalendarApiCollection.getCollection());
    }

    public GoogleCalendarComponent(CamelContext context) {
        super(context, GoogleCalendarEndpoint.class, GoogleCalendarApiName.class, GoogleCalendarApiCollection.getCollection());
    }

    @Override
    protected GoogleCalendarApiName getApiName(String apiNameStr) throws IllegalArgumentException {
        return GoogleCalendarApiName.fromValue(apiNameStr);
    }

    public Calendar getClient(GoogleCalendarConfiguration config) {
        if (client == null) {

            List<String> list = null;
            if (config.getScopes() != null) {
                String[] arr = config.getScopes().split(",");
                list = Arrays.asList(arr);
            }

            client = getClientFactory().makeClient(config.getClientId(),
                    config.getClientSecret(), list,
                    config.getApplicationName(), config.getRefreshToken(),
                    config.getAccessToken(), config.getEmailAddress(),
                    config.getP12FileName(), config.getUser());
        }
        return client;
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
     * To use the GoogleCalendarClientFactory as factory for creating the client.
     * Will by default use {@link BatchGoogleCalendarClientFactory}
     */
    public void setClientFactory(GoogleCalendarClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String methodName, GoogleCalendarApiName apiName,
                                      GoogleCalendarConfiguration endpointConfiguration) {
        endpointConfiguration.setApiName(apiName);
        endpointConfiguration.setMethodName(methodName);
        return new GoogleCalendarEndpoint(uri, this, apiName, methodName, endpointConfiguration);
    }
}
