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
package org.apache.camel.component.azure.servicebus;

import java.util.Map;
import java.util.Set;

import com.azure.core.credential.TokenCredential;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;

/**
 * Azure ServiceBus component
 */
@Component("azure-servicebus")
public class ServiceBusComponent extends DefaultComponent {
    @Metadata
    private ServiceBusConfiguration configuration = new ServiceBusConfiguration();

    public ServiceBusComponent() {
    }

    public ServiceBusComponent(final CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        if (remaining == null || remaining.isBlank()) {
            throw new IllegalArgumentException("A queue or topic name must be specified.");
        }

        final ServiceBusConfiguration configuration
                = this.configuration != null ? this.configuration.copy() : new ServiceBusConfiguration();

        // set account or topic name
        configuration.setTopicOrQueueName(remaining);

        final ServiceBusEndpoint endpoint = new ServiceBusEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);
        setCredentials(configuration);
        validateConfigurations(configuration);

        return endpoint;
    }

    private void setCredentials(final ServiceBusConfiguration configuration) {
        if (ObjectHelper.isNotEmpty(configuration.getFullyQualifiedNamespace()) &&
                ObjectHelper.isEmpty(configuration.getTokenCredential())) {
            final Set<TokenCredential> tokenCredentialFromRegistry
                    = getCamelContext().getRegistry().findByType(TokenCredential.class);

            // Find exactly one from the registry or create one
            if (tokenCredentialFromRegistry.size() == 1) {
                configuration.setTokenCredential(tokenCredentialFromRegistry.stream().findFirst().get());
            }
        }
    }

    /**
     * The component configurations
     */
    public ServiceBusConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(ServiceBusConfiguration configuration) {
        this.configuration = configuration;
    }

    private void validateConfigurations(final ServiceBusConfiguration configuration) {
        if (configuration.getReceiverAsyncClient() == null || configuration.getSenderAsyncClient() == null) {
            if (ObjectHelper.isEmpty(configuration.getConnectionString()) &&
                    ObjectHelper.isEmpty(configuration.getFullyQualifiedNamespace())) {
                throw new IllegalArgumentException(
                        "Azure ServiceBus ConnectionString or FQNS must be specified.");
            }
        }
    }
}
