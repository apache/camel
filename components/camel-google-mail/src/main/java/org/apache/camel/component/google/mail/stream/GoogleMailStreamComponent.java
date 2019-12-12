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
package org.apache.camel.component.google.mail.stream;

import java.util.Map;

import com.google.api.services.gmail.Gmail;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.google.mail.BatchGoogleMailClientFactory;
import org.apache.camel.component.google.mail.GoogleMailClientFactory;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

/**
 * Represents the component that manages {@link GoogleMailStreamEndpoint}.
 */
@Component("google-mail-stream")
public class GoogleMailStreamComponent extends DefaultComponent {

    @Metadata(label = "advanced")
    private Gmail client;
    @Metadata(label = "advanced")
    private GoogleMailClientFactory clientFactory;
    @Metadata(label = "advanced")
    private GoogleMailStreamConfiguration configuration;

    public GoogleMailStreamComponent() {
        this(null);
    }

    public GoogleMailStreamComponent(CamelContext context) {
        super(context);
        registerExtension(new GoogleMailStreamComponentVerifierExtension());
        this.configuration = new GoogleMailStreamConfiguration();
    }

    public Gmail getClient(GoogleMailStreamConfiguration googleMailConfiguration) {
        if (client == null) {
            client = getClientFactory().makeClient(googleMailConfiguration.getClientId(), googleMailConfiguration.getClientSecret(),
                                                   googleMailConfiguration.getApplicationName(), googleMailConfiguration.getRefreshToken(),
                                                   googleMailConfiguration.getAccessToken());
        }
        return client;
    }

    /**
     * The client Factory
     */
    public GoogleMailClientFactory getClientFactory() {
        if (clientFactory == null) {
            clientFactory = new BatchGoogleMailClientFactory();
        }
        return clientFactory;
    }

    public GoogleMailStreamConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * The configuration
     */
    public void setConfiguration(GoogleMailStreamConfiguration configuration) {
        this.configuration = configuration;
    }

    public void setClientFactory(GoogleMailClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        final GoogleMailStreamConfiguration configuration = this.configuration.copy();
        GoogleMailStreamEndpoint endpoint = new GoogleMailStreamEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);
        return endpoint;
    }
}
