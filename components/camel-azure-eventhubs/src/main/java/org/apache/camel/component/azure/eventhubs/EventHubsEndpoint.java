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

import com.azure.messaging.eventhubs.models.ErrorContext;
import com.azure.messaging.eventhubs.models.EventContext;
import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;

/**
 * The azure-eventhubs component that integrates Azure Event Hubs using AMQP protocol. Azure EventHubs is a highly
 * scalable publish-subscribe service that can ingest millions of events per second and stream them to multiple
 * consumers.
 */
@UriEndpoint(firstVersion = "3.5.0", scheme = "azure-eventhubs", title = "Azure Event Hubs",
             syntax = "azure-eventhubs:namespace/eventHubName", category = {
                     Category.CLOUD, Category.MESSAGING })
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

    public Exchange createAzureEventHubExchange(final EventContext eventContext) {
        final Exchange exchange = createExchange();
        final Message message = exchange.getIn();

        // set body as byte[] and let camel typeConverters do the job to convert
        message.setBody(eventContext.getEventData().getBody());
        // set headers
        message.setHeader(EventHubsConstants.PARTITION_ID, eventContext.getPartitionContext().getPartitionId());
        message.setHeader(EventHubsConstants.PARTITION_KEY, eventContext.getEventData().getPartitionKey());
        message.setHeader(EventHubsConstants.OFFSET, eventContext.getEventData().getOffset());
        message.setHeader(EventHubsConstants.ENQUEUED_TIME, eventContext.getEventData().getEnqueuedTime());
        message.setHeader(EventHubsConstants.SEQUENCE_NUMBER, eventContext.getEventData().getSequenceNumber());

        return exchange;
    }

    public Exchange createAzureEventHubExchange(final ErrorContext errorContext) {
        final Exchange exchange = createExchange();
        final Message message = exchange.getIn();

        // set headers
        message.setHeader(EventHubsConstants.PARTITION_ID, errorContext.getPartitionContext().getPartitionId());

        // set exception
        exchange.setException(errorContext.getThrowable());

        return exchange;
    }
}
