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

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Azure ServiceBus component
 */
@Component("azure-servicebus")
public class ServiceBusComponent extends DefaultComponent {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceBusComponent.class);

    @Metadata
    private ServiceBusConfiguration configuration = new ServiceBusConfiguration();

    public ServiceBusComponent() {
    }

    public ServiceBusComponent(final CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        if (remaining == null || remaining.trim().length() == 0) {
            throw new IllegalArgumentException("A queue or topic name must be specified.");
        }

        final ServiceBusConfiguration configuration
                = this.configuration != null ? this.configuration.copy() : new ServiceBusConfiguration();

        // set account or topic name
        configuration.setTopicOrQueueName(remaining);

        final ServiceBusEndpoint endpoint = new ServiceBusEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);
        validateConfigurations(configuration);

        return endpoint;
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
            if (ObjectHelper.isEmpty(configuration.getConnectionString())) {
                throw new IllegalArgumentException("Azure ServiceBus ConnectionString must be specified.");
            }
        }
    }
}
