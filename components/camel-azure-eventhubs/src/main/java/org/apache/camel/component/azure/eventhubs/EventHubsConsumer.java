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

import com.azure.messaging.eventhubs.EventProcessorClient;
import com.azure.messaging.eventhubs.models.ErrorContext;
import com.azure.messaging.eventhubs.models.EventContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedExchange;
import org.apache.camel.Processor;
import org.apache.camel.component.azure.eventhubs.client.EventHubsClientFactory;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventHubsConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(EventHubsConsumer.class);

    // we use the EventProcessorClient as recommended by Azure docs to consume from all partitions
    private EventProcessorClient processorClient;

    public EventHubsConsumer(final EventHubsEndpoint endpoint, final Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // create the client
        processorClient = EventHubsClientFactory.createEventProcessorClient(getConfiguration(),
                this::onEventListener, this::onErrorListener);

        // start the client but we will rely on the Azure Client Scheduler for thread management
        processorClient.start();
    }

    @Override
    protected void doStop() throws Exception {
        if (processorClient != null) {
            // shutdown the client
            processorClient.stop();
        }

        // shutdown camel consumer
        super.doStop();
    }

    public EventHubsConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public EventHubsEndpoint getEndpoint() {
        return (EventHubsEndpoint) super.getEndpoint();
    }

    private void onEventListener(final EventContext eventContext) {
        final Exchange exchange = getEndpoint().createAzureEventHubExchange(eventContext);

        // add exchange callback
        exchange.adapt(ExtendedExchange.class).addOnCompletion(new Synchronization() {
            @Override
            public void onComplete(Exchange exchange) {
                // we update the consumer offsets
                processCommit(exchange, eventContext);
            }

            @Override
            public void onFailure(Exchange exchange) {
                // we do nothing here
                processRollback(exchange);
            }
        });
        // send message to next processor in the route
        getAsyncProcessor().process(exchange, doneSync -> LOG.trace("Processing exchange [{}] done.", exchange));
    }

    private void onErrorListener(final ErrorContext errorContext) {
        final Exchange exchange = getEndpoint().createAzureEventHubExchange(errorContext);

        // log exception if an exception occurred and was not handled
        if (exchange.getException() != null) {
            getExceptionHandler().handleException("Error processing exchange", exchange,
                    exchange.getException());
        }
    }

    /**
     * Strategy to commit the offset after message being processed successfully.
     *
     * @param exchange the exchange
     */
    private void processCommit(final Exchange exchange, final EventContext eventContext) {
        try {
            eventContext.updateCheckpoint();
        } catch (Exception ex) {
            getExceptionHandler().handleException("Error occurred during updating the checkpoint. This exception is ignored.",
                    exchange, ex);
        }
    }

    /**
     * Strategy when processing the exchange failed.
     *
     * @param exchange the exchange
     */
    private void processRollback(Exchange exchange) {
        final Exception cause = exchange.getException();
        if (cause != null) {
            getExceptionHandler().handleException("Error during processing exchange.", exchange, cause);
        }
    }
}
