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

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.aws2.eventbridge.client.EventbridgeClientFactory;
import org.apache.camel.spi.*;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;

/**
 * Send events to AWS Eventbridge cluster instances.
 */
@UriEndpoint(firstVersion = "3.6.0", scheme = "aws2-eventbridge", title = "AWS Eventbridge",
             syntax = "aws2-eventbridge://eventbusNameOrArn", producerOnly = true, category = {
                     Category.CLOUD,
                     Category.MANAGEMENT },
             headersClass = EventbridgeConstants.class)
public class EventbridgeEndpoint extends DefaultEndpoint implements EndpointServiceLocation {

    private EventBridgeClient eventbridgeClient;

    @UriPath(description = "Event bus name or ARN")
    @Metadata(required = true)
    private String eventbusNameOrArn; // to support component docs
    @UriParam
    private EventbridgeConfiguration configuration;

    public EventbridgeEndpoint(String uri, Component component, EventbridgeConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public EventbridgeComponent getComponent() {
        return (EventbridgeComponent) super.getComponent();
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    @Override
    public Producer createProducer() throws Exception {
        return new EventbridgeProducer(this);
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        eventbridgeClient = configuration.getEventbridgeClient() != null
                ? configuration.getEventbridgeClient()
                : EventbridgeClientFactory.getEventbridgeClient(configuration);
    }

    @Override
    public void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getEventbridgeClient())) {
            if (eventbridgeClient != null) {
                eventbridgeClient.close();
            }
        }
        super.doStop();
    }

    public EventbridgeConfiguration getConfiguration() {
        return configuration;
    }

    public EventBridgeClient getEventbridgeClient() {
        return eventbridgeClient;
    }

    @Override
    public String getServiceUrl() {
        if (!configuration.isOverrideEndpoint()) {
            if (ObjectHelper.isNotEmpty(configuration.getRegion())) {
                return configuration.getRegion();
            }
        } else if (ObjectHelper.isNotEmpty(configuration.getUriEndpointOverride())) {
            return configuration.getUriEndpointOverride();
        }
        return null;
    }

    @Override
    public String getServiceProtocol() {
        return "eventbridge";
    }

    @Override
    public Map<String, String> getServiceMetadata() {
        if (configuration.getEventbusName() != null) {
            return Map.of("eventbus", configuration.getEventbusName());
        }
        return null;
    }
}
