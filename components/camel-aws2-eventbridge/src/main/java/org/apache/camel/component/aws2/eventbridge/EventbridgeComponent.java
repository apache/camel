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
package org.apache.camel.component.aws2.eventbridge;

import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;

/**
 * For working with Amazon Eventbridge SDK v2.
 */
@Component("aws2-eventbridge")
public class EventbridgeComponent extends DefaultComponent {

    private static final Logger LOG = LoggerFactory.getLogger(EventbridgeComponent.class);

    @Metadata
    private EventbridgeConfiguration configuration = new EventbridgeConfiguration();

    public EventbridgeComponent() {
        this(null);
    }

    public EventbridgeComponent(CamelContext context) {
        super(context);

        registerExtension(new EventbridgeComponentVerifierExtension());
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        if (remaining == null || remaining.trim().length() == 0) {
            throw new IllegalArgumentException("Event bus name must be specified.");
        }
        EventbridgeConfiguration configuration
                = this.configuration != null ? this.configuration.copy() : new EventbridgeConfiguration();
        configuration.setEventbusName(remaining);
        EventbridgeEndpoint endpoint = new EventbridgeEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);
        if (endpoint.getConfiguration().isAutoDiscoverClient()) {
            checkAndSetRegistryClient(configuration, endpoint);
        }
        if (configuration.getEventbridgeClient() == null
                && (configuration.getAccessKey() == null || configuration.getSecretKey() == null)) {
            throw new IllegalArgumentException("Amazon Eventbridge client or accessKey and secretKey must be specified");
        }

        return endpoint;
    }

    public EventbridgeConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Component configuration
     */
    public void setConfiguration(EventbridgeConfiguration configuration) {
        this.configuration = configuration;
    }

    private void checkAndSetRegistryClient(EventbridgeConfiguration configuration, EventbridgeEndpoint endpoint) {
        if (ObjectHelper.isEmpty(endpoint.getConfiguration().getEventbridgeClient())) {
            LOG.debug("Looking for an EventBridgeClient instance in the registry");
            Set<EventBridgeClient> clients = getCamelContext().getRegistry().findByType(EventBridgeClient.class);
            if (clients.size() == 1) {
                LOG.debug("Found exactly one EventBridgeClient instance in the registry");
                configuration.setEventbridgeClient(clients.stream().findFirst().get());
            } else {
                LOG.debug("No EventbridgeClient instance in the registry");
            }
        } else {
            LOG.debug("EventbridgeClient instance is already set at endpoint level: skipping the check in the registry");
        }
    }
}
