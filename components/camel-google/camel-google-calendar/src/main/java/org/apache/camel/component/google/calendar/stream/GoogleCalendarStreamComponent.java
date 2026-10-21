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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.api.services.calendar.Calendar;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.google.calendar.BatchGoogleCalendarClientFactory;
import org.apache.camel.component.google.calendar.GoogleCalendarClientFactory;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.HealthCheckComponent;

@Component("google-calendar-stream")
public class GoogleCalendarStreamComponent extends HealthCheckComponent {

    @Metadata(label = "advanced")
    private GoogleCalendarClientFactory clientFactory;
    @Metadata
    private GoogleCalendarStreamConfiguration configuration;
    private final Map<List<Object>, Calendar> clients = new ConcurrentHashMap<>();

    public GoogleCalendarStreamComponent() {
        this(null);
    }

    public GoogleCalendarStreamComponent(CamelContext context) {
        super(context);

        this.configuration = new GoogleCalendarStreamConfiguration();
    }

    /**
     * Gets the client for the given configuration. One client is kept per distinct set of the options the client is
     * created from, so endpoints with the same options share a client and endpoints with different options get their
     * own.
     */
    public Calendar getClient(GoogleCalendarStreamConfiguration config) {
        return clients.computeIfAbsent(clientKey(config), key -> createClient(config));
    }

    private Calendar createClient(GoogleCalendarStreamConfiguration config) {
        if (config.getClientId() != null && config.getClientSecret() != null) {
            return getClientFactory().makeClient(config.getClientId(), config.getClientSecret(), config.getScopesAsList(),
                    config.getApplicationName(), config.getRefreshToken(),
                    config.getAccessToken(), config.getEmailAddress(), config.getP12FileName(), config.getUser());
        } else if (config.getServiceAccountKey() != null) {
            return getClientFactory().makeClient(getCamelContext(), config.getServiceAccountKey(),
                    config.getScopesAsList(),
                    config.getApplicationName(), config.getDelegate());
        } else {
            throw new IllegalArgumentException(
                    "(clientId and clientSecret) or serviceAccountKey are required to create Google Calendar client");
        }
    }

    private static List<Object> clientKey(GoogleCalendarStreamConfiguration config) {
        return Arrays.asList(config.getClientId(), config.getClientSecret(), config.getRefreshToken(),
                config.getAccessToken(), config.getServiceAccountKey(), config.getDelegate(), config.getScopes(),
                config.getApplicationName(), config.getEmailAddress(), config.getP12FileName(), config.getUser());
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

    @Override
    protected void doStop() throws Exception {
        clients.clear();
        super.doStop();
    }
}
