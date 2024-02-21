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

import java.util.EnumMap;
import java.util.Map;

import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceiverAsyncClient;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.azure.servicebus.client.ServiceBusClientFactory;
import org.apache.camel.component.azure.servicebus.client.ServiceBusReceiverAsyncClientWrapper;
import org.apache.camel.component.azure.servicebus.operations.ServiceBusReceiverOperations;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.scheduler.Schedulers;

public class ServiceBusConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceBusConsumer.class);

    private ServiceBusReceiverAsyncClientWrapper clientWrapper;
    private ServiceBusReceiverOperations operations;

    private final Map<ServiceBusConsumerOperationDefinition, Runnable> operationsToExecute
            = new EnumMap<>(ServiceBusConsumerOperationDefinition.class);

    {
        bind(ServiceBusConsumerOperationDefinition.peekMessages, this::peekMessages);
        bind(ServiceBusConsumerOperationDefinition.receiveMessages, this::receiveMessages);
    }

    public ServiceBusConsumer(final ServiceBusEndpoint endpoint, final Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        createAndConnectClient();
    }

    protected void createAndConnectClient() {
        LOG.debug("Creating connection to Azure ServiceBus");

        // create the client
        final ServiceBusReceiverAsyncClient client = getConfiguration().getReceiverAsyncClient() != null
                ? getConfiguration().getReceiverAsyncClient()
                : ServiceBusClientFactory.createServiceBusReceiverAsyncClient(getConfiguration());

        // create the wrapper
        clientWrapper = new ServiceBusReceiverAsyncClientWrapper(client);

        // create the operations
        operations = new ServiceBusReceiverOperations(clientWrapper);

        // get the operation that we want to invoke
        final ServiceBusConsumerOperationDefinition chosenOperation = getConfiguration().getConsumerOperation();

        // invoke the operation and run it
        invokeOperation(chosenOperation);
    }

    @Override
    protected void doStop() throws Exception {
        if (clientWrapper != null) {
            // shutdown the client
            clientWrapper.close();
        }

        // shutdown camel consumer
        super.doStop();
    }

    public ServiceBusConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public ServiceBusEndpoint getEndpoint() {
        return (ServiceBusEndpoint) super.getEndpoint();
    }

    private void bind(final ServiceBusConsumerOperationDefinition operation, Runnable fn) {
        operationsToExecute.put(operation, fn);
    }

    /**
     * Entry method that selects the appropriate operation and executes it
     */
    private void invokeOperation(final ServiceBusConsumerOperationDefinition operation) {
        final ServiceBusConsumerOperationDefinition operationsToInvoke;

        if (ObjectHelper.isEmpty(operation)) {
            operationsToInvoke = ServiceBusConsumerOperationDefinition.receiveMessages;
        } else {
            operationsToInvoke = operation;
        }

        final Runnable fnToInvoke = operationsToExecute.get(operationsToInvoke);

        if (fnToInvoke != null) {
            fnToInvoke.run();
        } else {
            throw new RuntimeCamelException("Operation not supported. Value: " + operationsToInvoke);
        }
    }

    private void receiveMessages() {
        operations.receiveMessages()
                .subscribe(this::onEventListener, this::onErrorListener, () -> {
                });
    }

    private void peekMessages() {
        operations.peekMessages(getConfiguration().getPeekNumMaxMessages())
                .subscribe(this::onEventListener, this::onErrorListener, () -> {
                });
    }

    private void onEventListener(final ServiceBusReceivedMessage message) {
        final Exchange exchange = createServiceBusExchange(message);
        final ConsumerOnCompletion onCompletion = new ConsumerOnCompletion(message);
        // add exchange callback
        exchange.getExchangeExtension().addOnCompletion(onCompletion);
        // use default consumer callback
        AsyncCallback cb = defaultConsumerCallback(exchange, true);
        getAsyncProcessor().process(exchange, cb);
    }

    private Exchange createServiceBusExchange(final ServiceBusReceivedMessage receivedMessage) {
        final Exchange exchange = createExchange(true);
        final Message message = exchange.getIn();

        // set body
        message.setBody(receivedMessage.getBody());

        // set headers
        message.setHeader(ServiceBusConstants.APPLICATION_PROPERTIES, receivedMessage.getApplicationProperties());
        message.setHeader(ServiceBusConstants.CONTENT_TYPE, receivedMessage.getContentType());
        message.setHeader(ServiceBusConstants.MESSAGE_ID, receivedMessage.getMessageId());
        message.setHeader(ServiceBusConstants.CORRELATION_ID, receivedMessage.getCorrelationId());
        message.setHeader(ServiceBusConstants.DEAD_LETTER_ERROR_DESCRIPTION, receivedMessage.getDeadLetterErrorDescription());
        message.setHeader(ServiceBusConstants.DEAD_LETTER_REASON, receivedMessage.getDeadLetterReason());
        message.setHeader(ServiceBusConstants.DEAD_LETTER_SOURCE, receivedMessage.getDeadLetterSource());
        message.setHeader(ServiceBusConstants.DELIVERY_COUNT, receivedMessage.getDeliveryCount());
        message.setHeader(ServiceBusConstants.SCHEDULED_ENQUEUE_TIME, receivedMessage.getScheduledEnqueueTime());
        message.setHeader(ServiceBusConstants.ENQUEUED_SEQUENCE_NUMBER, receivedMessage.getEnqueuedSequenceNumber());
        message.setHeader(ServiceBusConstants.ENQUEUED_TIME, receivedMessage.getEnqueuedTime());
        message.setHeader(ServiceBusConstants.EXPIRES_AT, receivedMessage.getExpiresAt());
        message.setHeader(ServiceBusConstants.LOCK_TOKEN, receivedMessage.getLockToken());
        message.setHeader(ServiceBusConstants.LOCKED_UNTIL, receivedMessage.getLockedUntil());
        message.setHeader(ServiceBusConstants.PARTITION_KEY, receivedMessage.getPartitionKey());
        message.setHeader(ServiceBusConstants.RAW_AMQP_MESSAGE, receivedMessage.getRawAmqpMessage());
        message.setHeader(ServiceBusConstants.REPLY_TO, receivedMessage.getReplyTo());
        message.setHeader(ServiceBusConstants.REPLY_TO_SESSION_ID, receivedMessage.getReplyToSessionId());
        message.setHeader(ServiceBusConstants.SEQUENCE_NUMBER, receivedMessage.getSequenceNumber());
        message.setHeader(ServiceBusConstants.SESSION_ID, receivedMessage.getSessionId());
        message.setHeader(ServiceBusConstants.SUBJECT, receivedMessage.getSubject());
        message.setHeader(ServiceBusConstants.TIME_TO_LIVE, receivedMessage.getTimeToLive());
        message.setHeader(ServiceBusConstants.TO, receivedMessage.getTo());

        return exchange;
    }

    private void onErrorListener(final Throwable errorContext) {
        LOG.warn("Error from Azure ServiceBus due to {} - Reconnecting in {} seconds", errorContext.getMessage(),
                getConfiguration().getReconnectDelay());
        if (LOG.isDebugEnabled()) {
            LOG.warn("Error from Azure ServiceBus (incl stacktrace)", errorContext);
        }
        if (getConfiguration().getReconnectDelay() > 0) {
            try {
                Thread.sleep(getConfiguration().getReconnectDelay());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        try {
            clientWrapper.close();
        } catch (Exception e) {
            // ignore
        }
        createAndConnectClient();
    }

    private class ConsumerOnCompletion extends SynchronizationAdapter {
        private final ServiceBusReceivedMessage message;

        public ConsumerOnCompletion(ServiceBusReceivedMessage message) {
            this.message = message;
        }

        @Override
        public void onComplete(Exchange exchange) {
            super.onComplete(exchange);
            if (!getConfiguration().isDisableAutoComplete()) {
                clientWrapper.complete(message).subscribeOn(Schedulers.boundedElastic()).subscribe();
            }
        }

        @Override
        public void onFailure(Exchange exchange) {
            final Exception cause = exchange.getException();
            if (cause != null) {
                getExceptionHandler().handleException("Error during processing exchange.", exchange, cause);
            }
            if (!getConfiguration().isDisableAutoComplete()) {
                clientWrapper.abandon(message).subscribeOn(Schedulers.boundedElastic()).subscribe();
            }
        }
    }
}
