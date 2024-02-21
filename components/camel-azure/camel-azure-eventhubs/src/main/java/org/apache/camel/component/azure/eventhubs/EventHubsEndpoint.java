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
package org.apache.camel.component.azure.eventhubs;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Send and receive events to/from Azure Event Hubs using AMQP protocol.
 */
@UriEndpoint(firstVersion = "3.5.0", scheme = "azure-eventhubs", title = "Azure Event Hubs",
             syntax = "azure-eventhubs:namespace/eventHubName", category = {
                     Category.CLOUD, Category.MESSAGING },
             headersClass = EventHubsConstants.class)
public class EventHubsEndpoint extends DefaultEndpoint {

    @UriParam
    private EventHubsConfiguration configuration;

    public EventHubsEndpoint(final String uri, final Component component, final EventHubsConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new EventHubsProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        final Consumer eventHubConsumer = new EventHubsConsumer(this, processor);
        configureConsumer(eventHubConsumer);

        return eventHubConsumer;
    }

    /**
     * The component configurations
     */
    public EventHubsConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(EventHubsConfiguration configuration) {
        this.configuration = configuration;
    }

}
