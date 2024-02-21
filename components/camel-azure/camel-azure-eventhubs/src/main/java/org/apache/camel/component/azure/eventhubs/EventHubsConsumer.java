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

import java.util.Timer;
import java.util.concurrent.atomic.AtomicInteger;

import com.azure.messaging.eventhubs.EventProcessorClient;
import com.azure.messaging.eventhubs.models.ErrorContext;
import com.azure.messaging.eventhubs.models.EventContext;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.azure.eventhubs.client.EventHubsClientFactory;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.azure.eventhubs.EventHubsConstants.COMPLETED_BY_SIZE;
import static org.apache.camel.component.azure.eventhubs.EventHubsConstants.COMPLETED_BY_TIMEOUT;
import static org.apache.camel.component.azure.eventhubs.EventHubsConstants.UNCOMPLETED;

public class EventHubsConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(EventHubsConsumer.class);

    // we use the EventProcessorClient as recommended by Azure docs to consume from all partitions
    private EventProcessorClient processorClient;

    private final AtomicInteger processedEvents;
    private final Timer timer;

    private EventHubsCheckpointUpdaterTimerTask lastTask;

    public EventHubsConsumer(final EventHubsEndpoint endpoint, final Processor processor) {
        super(endpoint, processor);

        this.processedEvents = new AtomicInteger();
        this.timer = new Timer();
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
            processorClient = null;
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

    private Exchange createAzureEventHubExchange(final EventContext eventContext) {
        final Exchange exchange = createExchange(true);
        final Message message = exchange.getIn();

        // set body as byte[] and let camel typeConverters do the job to convert
        message.setBody(eventContext.getEventData().getBody());
        // set headers
        message.setHeader(EventHubsConstants.PARTITION_ID, eventContext.getPartitionContext().getPartitionId());
        message.setHeader(EventHubsConstants.PARTITION_KEY, eventContext.getEventData().getPartitionKey());
        message.setHeader(EventHubsConstants.OFFSET, eventContext.getEventData().getOffset());
        message.setHeader(EventHubsConstants.ENQUEUED_TIME, eventContext.getEventData().getEnqueuedTime());
        message.setHeader(EventHubsConstants.SEQUENCE_NUMBER, eventContext.getEventData().getSequenceNumber());
        if (eventContext.getEventData().getEnqueuedTime() != null) {
            long ts = eventContext.getEventData().getEnqueuedTime().getEpochSecond() * 1000;
            message.setHeader(EventHubsConstants.MESSAGE_TIMESTAMP, ts);
        }
        message.setHeader(EventHubsConstants.METADATA, eventContext.getEventData().getProperties());

        return exchange;
    }

    private Exchange createAzureEventHubExchange(final ErrorContext errorContext) {
        final Exchange exchange = createExchange(true);
        final Message message = exchange.getIn();

        // set headers
        message.setHeader(EventHubsConstants.PARTITION_ID, errorContext.getPartitionContext().getPartitionId());

        // set exception
        exchange.setException(errorContext.getThrowable());

        return exchange;
    }

    private void onEventListener(final EventContext eventContext) {
        final Exchange exchange = createAzureEventHubExchange(eventContext);

        // add exchange callback
        exchange.getExchangeExtension().addOnCompletion(new Synchronization() {
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
        // use default consumer callback
        AsyncCallback cb = defaultConsumerCallback(exchange, true);
        getAsyncProcessor().process(exchange, cb);
    }

    private void onErrorListener(final ErrorContext errorContext) {
        final Exchange exchange = createAzureEventHubExchange(errorContext);

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
        if (lastTask == null || System.currentTimeMillis() > lastTask.scheduledExecutionTime()) {
            lastTask = new EventHubsCheckpointUpdaterTimerTask(eventContext, processedEvents);
            // delegate the checkpoint update to a dedicated Thread
            timer.schedule(lastTask, getConfiguration().getCheckpointBatchTimeout());
        } else {
            // updates the eventContext to use for the offset to be the most accurate
            lastTask.setEventContext(eventContext);
        }

        try {
            var completionCondition = processCheckpoint(exchange);
            if (completionCondition.equals(COMPLETED_BY_SIZE)) {
                eventContext.updateCheckpointAsync()
                        .subscribe(unused -> LOG.debug("Processed one event..."),
                                error -> LOG.debug("Error when updating Checkpoint: {}", error.getMessage()),
                                () -> {
                                    processedEvents.set(0);
                                    LOG.debug("Checkpoint updated.");
                                });

            } else if (!completionCondition.equals(COMPLETED_BY_TIMEOUT)) {
                processedEvents.incrementAndGet();
            }
            // we assume that the timer task has done the update by its side

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

    /**
     * Checks either the batch size or the batch timeout is reached
     *
     * @param  exchange the exchange
     * @return          the completion condition (batch size or batch timeout) if one of them is reached, else the
     *                  'uncompleted' state adds a header {@value EventHubsConstants#CHECKPOINT_UPDATED_BY} with the
     *                  completion condition (
     */
    private String processCheckpoint(Exchange exchange) {
        // Check if the batch size is reached
        if (processedEvents.get() % getConfiguration().getCheckpointBatchSize() == 0) {
            exchange.getIn().setHeader(EventHubsConstants.CHECKPOINT_UPDATED_BY, COMPLETED_BY_SIZE);
            LOG.debug("eventhub consumer batch size of reached");
            // no need to run task if the batch size already did the checkpointing
            if (lastTask != null) {
                lastTask.cancel();
            }

            return COMPLETED_BY_SIZE;
        } else {
            LOG.debug("eventhub consumer batch size of {}/{} not reached yet", processedEvents.get(),
                    getConfiguration().getCheckpointBatchSize());
        }

        // Check if the batch timeout is reached
        if (System.currentTimeMillis() >= lastTask.scheduledExecutionTime()) {
            exchange.getIn().setHeader(EventHubsConstants.CHECKPOINT_UPDATED_BY, COMPLETED_BY_TIMEOUT);
            LOG.debug("eventhub consumer batch timeout reached");

            return COMPLETED_BY_TIMEOUT;
        }

        return UNCOMPLETED;
    }
}
