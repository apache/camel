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
package org.apache.camel.component.google.calendar.stream;

import java.util.Map;

import com.google.api.services.calendar.Calendar;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.google.calendar.BatchGoogleCalendarClientFactory;
import org.apache.camel.component.google.calendar.GoogleCalendarClientFactory;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

/**
 * Represents the component that manages {@link GoogleCalendarStreamEndpoint}.
 */
@Component("google-calendar-stream")
public class GoogleCalendarStreamComponent extends DefaultComponent {

    @Metadata(label = "advanced")
    private Calendar client;
    @Metadata(label = "advanced")
    private GoogleCalendarClientFactory clientFactory;
    @Metadata(label = "advanced")
    private GoogleCalendarStreamConfiguration configuration;

    public GoogleCalendarStreamComponent() {
        this(null);
    }

    public GoogleCalendarStreamComponent(CamelContext context) {
        super(context);

        this.configuration = new GoogleCalendarStreamConfiguration();
    }

    public Calendar getClient(GoogleCalendarStreamConfiguration endpointConfiguration) {
        if (client == null) {
            client = getClientFactory().makeClient(endpointConfiguration.getClientId(), endpointConfiguration.getClientSecret(), endpointConfiguration.getScopes(),
                                                    endpointConfiguration.getApplicationName(), endpointConfiguration.getRefreshToken(),
                                                    endpointConfiguration.getAccessToken(), null, null, "me");
        }
        return client;
    }

    /**
     * The client Factory
     */
    public GoogleCalendarClientFactory getClientFactory() {
        if (clientFactory == null) {
            clientFactory = new BatchGoogleCalendarClientFactory();
        }
        return clientFactory;
    }

    public GoogleCalendarStreamConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * The configuration
     */
    public void setConfiguration(GoogleCalendarStreamConfiguration configuration) {
        this.configuration = configuration;
    }

    public void setClientFactory(GoogleCalendarClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        final GoogleCalendarStreamConfiguration configuration = this.configuration.copy();
        GoogleCalendarStreamEndpoint endpoint = new GoogleCalendarStreamEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);
        return endpoint;
    }
}
