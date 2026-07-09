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

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.azure.messaging.servicebus.ServiceBusErrorContext;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.azure.messaging.servicebus.ServiceBusReceiverAsyncClient;
import com.azure.messaging.servicebus.models.DeadLetterOptions;
import com.azure.messaging.servicebus.models.ServiceBusReceiveMode;
import com.azure.messaging.servicebus.models.SubQueue;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.PeriodTaskScheduler;
import org.apache.camel.spi.ShutdownAware;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServiceBusConsumer extends DefaultConsumer implements ShutdownAware {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceBusConsumer.class);
    private static final int LOCK_RENEW_INTERVAL_SECONDS = 10;

    private ServiceBusProcessorClient client;
    private ServiceBusReceiverAsyncClient renewalClient;
    private LockRenewer lockRenewer;
    private final AtomicInteger pendingExchanges = new AtomicInteger();

    public ServiceBusConsumer(final ServiceBusEndpoint endpoint, final Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        ServiceBusUtils.validateConfiguration(getConfiguration(), true);
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

        // set up Camel-managed lock renewal for PEEK_LOCK mode
        // the Azure SDK's built-in lock renewal is tied to the processMessage callback duration,
        // which returns immediately for async routes, making maxAutoLockRenewDuration ineffective
        if (getConfiguration().getProcessorClient() == null
                && getConfiguration().getServiceBusReceiveMode() == ServiceBusReceiveMode.PEEK_LOCK
                && getConfiguration().getMaxAutoLockRenewDuration() > 0
                && !getConfiguration().isSessionEnabled()) {
            renewalClient = getEndpoint().getServiceBusClientFactory()
                    .createServiceBusReceiverAsyncClient(getConfiguration());

            long maxRenewMs = getConfiguration().getMaxAutoLockRenewDuration();
            lockRenewer = new LockRenewer(renewalClient, Duration.ofMillis(maxRenewMs));

            PeriodTaskScheduler scheduler = PluginHelper.getPeriodTaskScheduler(getEndpoint().getCamelContext());
            scheduler.schedulePeriodTask(lockRenewer, LOCK_RENEW_INTERVAL_SECONDS * 1000L);
        }
    }

    private void processMessage(ServiceBusReceivedMessageContext messageContext) {
        pendingExchanges.incrementAndGet();
        final ServiceBusReceivedMessage message = messageContext.getMessage();
        final Exchange exchange = createServiceBusExchange(message);
        final ConsumerOnCompletion onCompletion = new ConsumerOnCompletion(messageContext);
        // add exchange callback
        exchange.getExchangeExtension().addOnCompletion(onCompletion);
        // track for Camel-managed lock renewal
        if (lockRenewer != null) {
            lockRenewer.add(exchange, message);
        }
        // use default consumer callback
        AsyncCallback cb = defaultConsumerCallback(exchange, true);
        getAsyncProcessor().process(exchange, cb);
    }

    private void processError(ServiceBusErrorContext errorContext) {
        final Exchange exchange = createServiceBusExchange(errorContext);

        // log exception if an exception occurred and was not handled
        if (exchange.getException() != null) {
            getExceptionHandler().handleException("Error from Service Bus: " + errorContext.getErrorSource(), exchange,
                    exchange.getException());
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (lockRenewer != null) {
            lockRenewer.cancel();
            lockRenewer = null;
        }
        if (renewalClient != null) {
            renewalClient.close();
            renewalClient = null;
        }
        if (client != null) {
            // stop accepting new messages but keep the connection open
            // so that in-flight exchanges can still complete/abandon messages
            client.stop();
        }

        // shutdown camel consumer
        super.doStop();
    }

    @Override
    protected void doShutdown() throws Exception {
        // defensive cleanup in case doStop() was not called
        if (lockRenewer != null) {
            lockRenewer.cancel();
            lockRenewer = null;
        }
        if (renewalClient != null) {
            renewalClient.close();
            renewalClient = null;
        }
        if (client != null) {
            // close the client after all in-flight exchanges have completed
            client.close();
        }

        super.doShutdown();
    }

    @Override
    public boolean deferShutdown(ShutdownRunningTask shutdownRunningTask) {
        // stop accepting new messages but keep the connection open
        // so that in-flight exchanges can still complete/abandon messages
        if (client != null) {
            client.stop();
        }
        return true;
    }

    @Override
    public int getPendingExchangesSize() {
        return pendingExchanges.get();
    }

    @Override
    public void prepareShutdown(boolean suspendOnly, boolean forced) {
        // noop
    }

    public ServiceBusConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public ServiceBusEndpoint getEndpoint() {
        return (ServiceBusEndpoint) super.getEndpoint();
    }

    private Exchange createServiceBusExchange(final ServiceBusErrorContext errorContext) {
        final Exchange exchange = createExchange(true);
        exchange.setException(errorContext.getException());
        return exchange;
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

    private class LockRenewer implements Runnable {

        private final ServiceBusReceiverAsyncClient client;
        private final Duration maxRenewDuration;
        private final AtomicBoolean run = new AtomicBoolean(true);
        private final Map<String, LockRenewerEntry> entries = new ConcurrentHashMap<>();

        LockRenewer(ServiceBusReceiverAsyncClient client, Duration maxRenewDuration) {
            this.client = client;
            this.maxRenewDuration = maxRenewDuration;
        }

        public void add(Exchange exchange, ServiceBusReceivedMessage message) {
            exchange.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
                @Override
                public void onComplete(Exchange exchange) {
                    remove(exchange);
                }

                @Override
                public void onFailure(Exchange exchange) {
                    remove(exchange);
                }

                private void remove(Exchange exchange) {
                    LOG.trace("Removing exchangeId {} from the LockRenewer, processing done", exchange.getExchangeId());
                    entries.remove(exchange.getExchangeId());
                }
            });

            entries.put(exchange.getExchangeId(),
                    new LockRenewerEntry(message, message.getLockedUntil(), Instant.now()));
        }

        public void cancel() {
            run.set(false);
        }

        @Override
        public void run() {
            if (!run.get()) {
                return;
            }

            Instant now = Instant.now();
            for (Map.Entry<String, LockRenewerEntry> mapEntry : entries.entrySet()) {
                String exchangeId = mapEntry.getKey();
                LockRenewerEntry entry = mapEntry.getValue();

                // skip if max renewal duration exceeded
                Duration elapsed = Duration.between(entry.startTime, now);
                if (elapsed.compareTo(maxRenewDuration) >= 0) {
                    LOG.debug("Max lock renewal duration exceeded for exchangeId {}, stopping renewal", exchangeId);
                    entries.remove(exchangeId);
                    continue;
                }

                // renew if lock is approaching expiry
                Instant lockExpiry = entry.lockedUntil.toInstant();
                if (now.plusSeconds(LOCK_RENEW_INTERVAL_SECONDS).isAfter(lockExpiry)) {
                    client.renewMessageLock(entry.message)
                            .subscribe(
                                    newLockedUntil -> {
                                        LOG.trace("Renewed lock for exchangeId {}, new expiry: {}", exchangeId,
                                                newLockedUntil);
                                        entries.computeIfPresent(exchangeId,
                                                (k, e) -> new LockRenewerEntry(e.message, newLockedUntil, e.startTime));
                                    },
                                    error -> {
                                        LOG.warn("Failed to renew lock for exchangeId {}: {}", exchangeId,
                                                error.getMessage());
                                        entries.remove(exchangeId);
                                    });
                }
            }
        }

        private record LockRenewerEntry(ServiceBusReceivedMessage message, OffsetDateTime lockedUntil, Instant startTime) {
        }
    }

    private class ConsumerOnCompletion extends SynchronizationAdapter {
        private final ServiceBusReceivedMessageContext messageContext;

        private ConsumerOnCompletion(ServiceBusReceivedMessageContext messageContext) {
            this.messageContext = messageContext;
        }

        @Override
        public void onComplete(Exchange exchange) {
            try {
                super.onComplete(exchange);
                if (getConfiguration().getServiceBusReceiveMode() == ServiceBusReceiveMode.PEEK_LOCK) {
                    messageContext.complete();
                }
            } finally {
                pendingExchanges.decrementAndGet();
            }
        }

        @Override
        public void onFailure(Exchange exchange) {
            try {
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
                                    .setDeadLetterReason(
                                            String.format("%s: %s", cause.getClass().getName(), cause.getMessage()));
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
            } finally {
                pendingExchanges.decrementAndGet();
            }
        }
    }
}
