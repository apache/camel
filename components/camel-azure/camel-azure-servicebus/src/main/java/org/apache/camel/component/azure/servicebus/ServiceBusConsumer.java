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

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import com.azure.messaging.servicebus.ServiceBusErrorContext;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.azure.messaging.servicebus.models.DeadLetterOptions;
import com.azure.messaging.servicebus.models.ServiceBusReceiveMode;
import com.azure.messaging.servicebus.models.SubQueue;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServiceBusConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceBusConsumer.class);
    private ServiceBusProcessorClient client;

    public ServiceBusConsumer(final ServiceBusEndpoint endpoint, final Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        LOG.debug("Creating connection to Azure ServiceBus");

        client = getConfiguration().getProcessorClient();
        if (client == null) {
            // create client as per sessions
            if (Boolean.FALSE.equals(getConfiguration().isSessionEnabled())) {
                client = getEndpoint().getServiceBusClientFactory().createServiceBusProcessorClient(getConfiguration(),
                        this::processMessage, this::processError);
            } else {
                client = getEndpoint().getServiceBusClientFactory().createServiceBusSessionProcessorClient(getConfiguration(),
                        this::processMessage, this::processError);
            }
        }

        client.start();
    }

    private void processMessage(ServiceBusReceivedMessageContext messageContext) {
        final ServiceBusReceivedMessage message = messageContext.getMessage();
        final Exchange exchange = createServiceBusExchange(message);
        final ConsumerOnCompletion onCompletion = new ConsumerOnCompletion(messageContext);
        // add exchange callback
        exchange.getExchangeExtension().addOnCompletion(onCompletion);
        // use default consumer callback
        AsyncCallback cb = defaultConsumerCallback(exchange, true);
        getAsyncProcessor().process(exchange, cb);
    }

    private void processError(ServiceBusErrorContext errorContext) {
        LOG.error("Error from Service Bus client: {}", errorContext.getErrorSource(), errorContext.getException());
    }

    @Override
    protected void doStop() throws Exception {
        if (client != null) {
            // shutdown the client
            client.close();
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

        // propagate headers
        final HeaderFilterStrategy headerFilterStrategy = getConfiguration().getHeaderFilterStrategy();
        message.getHeaders().putAll(receivedMessage.getApplicationProperties().entrySet().stream()
                .filter(entry -> !headerFilterStrategy.applyFilterToExternalHeaders(entry.getKey(), entry.getValue(), exchange))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));

        return exchange;
    }

    private class ConsumerOnCompletion extends SynchronizationAdapter {
        private final ServiceBusReceivedMessageContext messageContext;

        private ConsumerOnCompletion(ServiceBusReceivedMessageContext messageContext) {
            this.messageContext = messageContext;
        }

        @Override
        public void onComplete(Exchange exchange) {
            super.onComplete(exchange);
            if (getConfiguration().getServiceBusReceiveMode() == ServiceBusReceiveMode.PEEK_LOCK) {
                messageContext.complete();
            }
        }

        @Override
        public void onFailure(Exchange exchange) {
            final Exception cause = exchange.getException();
            if (cause != null) {
                getExceptionHandler().handleException("Error during processing exchange.", exchange, cause);
            }

            if (getConfiguration().getServiceBusReceiveMode() == ServiceBusReceiveMode.PEEK_LOCK) {
                if (getConfiguration().isEnableDeadLettering() && (ObjectHelper.isEmpty(getConfiguration().getSubQueue())
                        || ObjectHelper.equal(getConfiguration().getSubQueue(), SubQueue.NONE))) {
                    DeadLetterOptions deadLetterOptions = new DeadLetterOptions();
                    if (cause != null) {
                        deadLetterOptions
                                .setDeadLetterReason(String.format("%s: %s", cause.getClass().getName(), cause.getMessage()));
                        deadLetterOptions.setDeadLetterErrorDescription(Arrays.stream(cause.getStackTrace())
                                .map(StackTraceElement::toString)
                                .collect(Collectors.joining("\n")));
                        messageContext.deadLetter(deadLetterOptions);
                    } else {
                        messageContext.deadLetter();
                    }
                } else {
                    messageContext.abandon();
                }
            }
        }
    }
}
