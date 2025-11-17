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
package org.apache.camel.component.azure.eventgrid;

import java.util.Map;

import com.azure.identity.DefaultAzureCredential;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Azure EventGrid component
 */
@Component("azure-eventgrid")
public class EventGridComponent extends DefaultComponent {

    private static final Logger LOG = LoggerFactory.getLogger(EventGridComponent.class);

    @Metadata
    private EventGridConfiguration configuration = new EventGridConfiguration();

    public EventGridComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        final EventGridConfiguration configuration = this.configuration.copy();

        // Set the topic endpoint from the remaining part if not using a custom client
        if (configuration.getPublisherClient() == null && ObjectHelper.isNotEmpty(remaining)) {
            configuration.setTopicEndpoint(remaining);
        }

        final EventGridEndpoint endpoint = new EventGridEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);

        // Ensure we use default credential type if not configured
        if (endpoint.getConfiguration().getTokenCredential() == null
                && endpoint.getConfiguration().getAzureKeyCredential() == null
                && endpoint.getConfiguration().getAccessKey() == null) {
            if (endpoint.getConfiguration().getCredentialType() == null) {
                endpoint.getConfiguration().setCredentialType(CredentialType.AZURE_IDENTITY);
            }
        } else if (endpoint.getConfiguration().getTokenCredential() != null) {
            boolean azure = endpoint.getConfiguration().getTokenCredential() instanceof DefaultAzureCredential;
            endpoint.getConfiguration()
                    .setCredentialType(azure ? CredentialType.AZURE_IDENTITY : CredentialType.TOKEN_CREDENTIAL);
        } else if (endpoint.getConfiguration().getAzureKeyCredential() != null
                || endpoint.getConfiguration().getAccessKey() != null) {
            endpoint.getConfiguration().setCredentialType(CredentialType.ACCESS_KEY);
        }

        validateConfigurations(configuration);

        return endpoint;
    }

    /**
     * The component configurations
     */
    public EventGridConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(EventGridConfiguration configuration) {
        this.configuration = configuration;
    }

    private void validateConfigurations(final EventGridConfiguration configuration) {
        if (configuration.getPublisherClient() == null) {
            if (ObjectHelper.isEmpty(configuration.getTopicEndpoint())) {
                throw new IllegalArgumentException("Topic endpoint must be specified.");
            }

            if (!isAccessKeySet(configuration) && !isTokenCredentialSet(configuration)
                    && !isAzureIdentitySet(configuration)) {
                throw new IllegalArgumentException(
                        "Azure EventGrid AccessKey, AzureKeyCredential, TokenCredential or Azure Identity must be specified.");
            }
        }
    }

    private boolean isAccessKeySet(final EventGridConfiguration configuration) {
        return ObjectHelper.isNotEmpty(configuration.getAccessKey())
                || ObjectHelper.isNotEmpty(configuration.getAzureKeyCredential());
    }

    private boolean isTokenCredentialSet(final EventGridConfiguration configuration) {
        return ObjectHelper.isNotEmpty(configuration.getTokenCredential());
    }

    private boolean isAzureIdentitySet(final EventGridConfiguration configuration) {
        return ObjectHelper.isNotEmpty(configuration.getCredentialType())
                && configuration.getCredentialType().equals(CredentialType.AZURE_IDENTITY);
    }
}
