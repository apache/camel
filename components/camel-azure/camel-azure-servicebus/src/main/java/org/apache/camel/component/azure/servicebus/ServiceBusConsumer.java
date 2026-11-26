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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.azure.messaging.servicebus.ServiceBusErrorContext;
import com.azure.messaging.servicebus.ServiceBusException;
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
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.ShutdownAware;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServiceBusConsumer extends DefaultConsumer implements ShutdownAware {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceBusConsumer.class);
    // the lock renewer runs on a thread of its own, LOCK_RENEW_INTERVAL_SECONDS after the end of its previous run, and
    // renews a lock that expires within LOCK_RENEW_BEFORE_EXPIRY_SECONDS, so its first renewal comes with about 15
    // seconds left, and a transient failure of it is retried by the next run with about 10 seconds left
    private static final int LOCK_RENEW_INTERVAL_SECONDS = 5;
    private static final int LOCK_RENEW_BEFORE_EXPIRY_SECONDS = 20;
    // a run does not renew a lock while a renewal of it is in flight, unless that renewal has not answered for this
    // long (two runs): the SDK ends a request only after its tryTimeout (60 seconds by default), which can be after the
    // lock expired, so a renewal that hangs gets one more attempt. With a lock duration of 30 seconds or more (the
    // default is 60) it comes with about 5 to 10 seconds left; with a shorter lock it comes later in the lock (with 15
    // seconds, 0 to 5 seconds left, 5 seconds later than without this rule)
    private static final int LOCK_RENEW_IN_FLIGHT_SECONDS = 2 * LOCK_RENEW_INTERVAL_SECONDS;
    // Azure recommends putting the exception type in the dead-letter reason and its stack trace in the description,
    // but both are length-limited: the .NET and JavaScript SDKs cap them at 4096 (MaxDeadLetterReasonLength, applied
    // to the description as well), and Azure separately warns that a stack trace here can push a message past the
    // 256 KB quota of the Standard tier. The Java SDK does not check, so an over-long value only fails at the broker -
    // which leaves the message locked. Cap it here instead: the head of a stack trace carries the cause, the tail is
    // error-handler frames.
    private static final int MAX_DEAD_LETTER_TEXT_LENGTH = 4096;
    // so that whoever reads the dead-letter queue can see the text was cut rather than the trace having ended
    private static final String TRUNCATED_SUFFIX = "...";

    private ServiceBusProcessorClient client;
    private boolean clientCloseable;
    private ServiceBusReceiverAsyncClient renewalClient;
    private volatile LockRenewer lockRenewer;
    private ScheduledExecutorService lockRenewerExecutor;
    private final AtomicInteger pendingExchanges = new AtomicInteger();
    // Set by deferShutdown in peek-lock mode: a graceful stop waits for the exchanges in flight, which settle their
    // message through the processor client, so the client stays open and the messages it delivers meanwhile are held
    // (not routed) until the consumer stops
    private volatile boolean deferredStop;
    private volatile CountDownLatch stopped = new CountDownLatch(1);

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

        deferredStop = false;
        stopped = new CountDownLatch(1);

        LOG.debug("Creating connection to Azure ServiceBus");

        // close any previously created client (e.g. on route restart after doStop)
        if (clientCloseable && client != null) {
            client.close();
            client = null;
        }

        client = getConfiguration().getProcessorClient();
        if (client == null) {
            clientCloseable = true;
            // create client as per sessions
            if (Boolean.FALSE.equals(getConfiguration().isSessionEnabled())) {
                client = getEndpoint().getServiceBusClientFactory().createServiceBusProcessorClient(getConfiguration(),
                        this::processMessage, this::processError);
            } else {
                client = getEndpoint().getServiceBusClientFactory().createServiceBusSessionProcessorClient(getConfiguration(),
                        this::processMessage, this::processError);
            }
        } else {
            clientCloseable = false;
        }

        // set up Camel-managed lock renewal for PEEK_LOCK mode
        // the Azure SDK's built-in lock renewal is tied to the processMessage callback duration,
        // which returns immediately for async routes, making maxAutoLockRenewDuration ineffective.
        // Set up before the client starts, so a message it delivers right away is tracked too; if the start fails,
        // BaseService.start calls doStop, which shuts it down
        if (getConfiguration().getProcessorClient() == null
                && getConfiguration().getServiceBusReceiveMode() == ServiceBusReceiveMode.PEEK_LOCK
                && getConfiguration().getMaxAutoLockRenewDuration() > 0
                && !getConfiguration().isSessionEnabled()) {
            renewalClient = getEndpoint().getServiceBusClientFactory()
                    .createServiceBusReceiverAsyncClient(getConfiguration());

            long maxRenewMs = getConfiguration().getMaxAutoLockRenewDuration();
            lockRenewer = new LockRenewer(renewalClient, Duration.ofMillis(maxRenewMs));

            // a thread of its own, not the shared timer of the PeriodTaskScheduler: there the renewer waits behind every
            // other periodic task of the context (a slow one lets the locks expire), and a task cannot be removed, so
            // every start would leave one more renewer registered. A run only subscribes renewals, it never blocks
            lockRenewerExecutor = getEndpoint().getCamelContext().getExecutorServiceManager()
                    .newSingleThreadScheduledExecutor(this,
                            "ServiceBusLockRenewer[" + getConfiguration().getTopicOrQueueName() + "]");
            lockRenewerExecutor.scheduleWithFixedDelay(lockRenewer, LOCK_RENEW_INTERVAL_SECONDS * 1000L,
                    LOCK_RENEW_INTERVAL_SECONDS * 1000L, TimeUnit.MILLISECONDS);
        }

        client.start();
    }

    private void processMessage(ServiceBusReceivedMessageContext messageContext) {
        // the latch of this run of the consumer, taken before the check, so a stop and start meanwhile cannot leave the
        // message waiting for a later stop
        CountDownLatch stop = stopped;
        pendingExchanges.incrementAndGet();
        // counted before the check: a graceful stop that does not see this exchange as pending makes it be held here
        if (deferredStop) {
            pendingExchanges.decrementAndGet();
            holdUntilStopped(stop);
            return;
        }
        try {
            final ServiceBusReceivedMessage message = messageContext.getMessage();
            final Exchange exchange = createServiceBusExchange(message);
            final ConsumerOnCompletion onCompletion = new ConsumerOnCompletion(messageContext);
            // add exchange callback
            exchange.getExchangeExtension().addOnCompletion(onCompletion);
            // track for Camel-managed lock renewal
            final LockRenewer renewer = lockRenewer;
            if (renewer != null) {
                renewer.add(exchange, message);
            }
            // use default consumer callback
            AsyncCallback cb = defaultConsumerCallback(exchange, true);
            getAsyncProcessor().process(exchange, cb);
        } catch (Exception e) {
            pendingExchanges.decrementAndGet();
            if (getConfiguration().getServiceBusReceiveMode() == ServiceBusReceiveMode.PEEK_LOCK) {
                try {
                    messageContext.abandon();
                } catch (Exception abandonEx) {
                    // ignore
                }
            }
            getExceptionHandler().handleException("Error processing Service Bus message", e);
        }
    }

    /**
     * Holds a message delivered during a graceful stop, without routing or settling it: the processor client delivers
     * the next message only when processMessage returns, so at most maxConcurrentCalls messages are held (per session
     * with sessions). The SDK renews their lock while they are held, up to maxAutoLockRenewDuration: with 0 the lock of
     * a held message can expire during the graceful wait, and Service Bus then delivers it again to a free call, where
     * it is held again (one more delivery count each time). They are released when the stop has closed the client:
     * Service Bus delivers a held message again once its lock expires.
     */
    private static void holdUntilStopped(CountDownLatch stop) {
        try {
            stop.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
        stopLockRenewer();
        if (renewalClient != null) {
            renewalClient.close();
            renewalClient = null;
        }
        try {
            if (client != null) {
                if (clientCloseable) {
                    client.close();
                    client = null;
                } else {
                    // user-provided client: stop accepting messages but don't close
                    client.stop();
                }
            }
        } finally {
            // the client is closed or stopped: release the messages held during a graceful stop
            stopped.countDown();
        }

        // shutdown camel consumer
        super.doStop();
    }

    @Override
    protected void doShutdown() throws Exception {
        // defensive cleanup in case doStop() was not called
        stopLockRenewer();
        if (renewalClient != null) {
            renewalClient.close();
            renewalClient = null;
        }
        if (clientCloseable && client != null) {
            client.close();
            client = null;
        }
        stopped.countDown();

        super.doShutdown();
    }

    /**
     * Stops the lock renewer and its thread. shutdownNow: there is nothing to wait for, as a run only subscribes
     * renewals (their callbacks run on threads of the SDK), and the renewal client is closed right after.
     */
    private void stopLockRenewer() {
        if (lockRenewer != null) {
            lockRenewer.cancel();
            lockRenewer = null;
        }
        if (lockRenewerExecutor != null) {
            getEndpoint().getCamelContext().getExecutorServiceManager().shutdownNow(lockRenewerExecutor);
            lockRenewerExecutor = null;
        }
    }

    /**
     * For tests: one run of the lock renewer, as its thread runs it at the given time.
     */
    void runLockRenewer(Instant now) {
        lockRenewer.renewDueLocks(now);
    }

    /**
     * For tests: the lock expiry the lock renewer holds for the exchange, or null when it does not renew its lock.
     */
    OffsetDateTime lockRenewerLockedUntil(String exchangeId) {
        LockRenewer.LockRenewerEntry entry = lockRenewer.entries.get(exchangeId);
        return entry != null ? entry.lockedUntil : null;
    }

    @Override
    public boolean deferShutdown(ShutdownRunningTask shutdownRunningTask) {
        if (clientCloseable && getConfiguration().getServiceBusReceiveMode() == ServiceBusReceiveMode.PEEK_LOCK) {
            // stop routing new messages but keep the client open, so that in-flight exchanges can still
            // complete/abandon their message: the stop() of the processor client closes its receiver (the default
            // V2 stack of the SDK), and a message cannot be settled through a closed receiver. If a stop with
            // abortAfterTimeout gives up, the consumer stays in this state until the route is stopped and started
            deferredStop = true;
        } else if (client != null) {
            // receive and delete mode: nothing to settle; a processorClient given by the user: its messages do not go
            // through processMessage. Stop receiving
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
        message.setHeader(ServiceBusConstants.TOPIC_OR_QUEUE_NAME, getConfiguration().getTopicOrQueueName());
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
        // changed only through the atomic operations of the map: add, the run (on the renewer thread), the renewal
        // callbacks (on threads of the SDK) and the completion of the exchange
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
                    new LockRenewerEntry(message, message.getLockedUntil(), Instant.now(), null));
        }

        public void cancel() {
            run.set(false);
        }

        @Override
        public void run() {
            try {
                renewDueLocks(Instant.now());
            } catch (VirtualMachineError e) {
                // as the TimerListenerManager: a JVM-level failure is thrown on, and ends the task
                throw e;
            } catch (Exception | Error e) {
                // keep the scheduled task alive: anything thrown would end it, and no lock would be renewed any more
                LOG.warn("Error in the lock renewer, the next run renews the locks again: {}", e.toString());
                LOG.debug("Error in the lock renewer", e);
            }
        }

        void renewDueLocks(Instant now) {
            if (!run.get()) {
                return;
            }

            for (String exchangeId : entries.keySet()) {
                LockRenewerEntry[] started = new LockRenewerEntry[1];
                boolean[] exceeded = new boolean[1];
                // decided and marked in one atomic step, so a callback or the completion of the exchange in between
                // cannot be lost; the renewal is subscribed after it (its callback can run at once, on this thread)
                entries.computeIfPresent(exchangeId, (k, entry) -> {
                    // skip if max renewal duration exceeded
                    if (Duration.between(entry.startTime, now).compareTo(maxRenewDuration) >= 0) {
                        exceeded[0] = true;
                        return null;
                    }
                    // renew if the lock expires within LOCK_RENEW_BEFORE_EXPIRY_SECONDS (several runs before it
                    // expires), and no renewal of it is in flight
                    if (!now.plusSeconds(LOCK_RENEW_BEFORE_EXPIRY_SECONDS).isAfter(entry.lockedUntil.toInstant())
                            || entry.renewal != null && entry.renewal.awaited(now)) {
                        return entry;
                    }
                    started[0] = new LockRenewerEntry(entry.message, entry.lockedUntil, entry.startTime, new Renewal(now));
                    return started[0];
                });
                if (exceeded[0]) {
                    LOG.debug("Max lock renewal duration exceeded for exchangeId {}, stopping renewal", exchangeId);
                } else if (started[0] != null) {
                    renew(exchangeId, started[0].message, started[0].renewal);
                }
            }
        }

        private void renew(String exchangeId, ServiceBusReceivedMessage message, Renewal renewal) {
            try {
                client.renewMessageLock(message)
                        .subscribe(
                                newLockedUntil -> {
                                    LOG.trace("Renewed lock for exchangeId {}, new expiry: {}", exchangeId, newLockedUntil);
                                    // the answer of an earlier renewal can come after that of a later one: keep the
                                    // later expiry. computeIfPresent: a late answer does not add a completed exchange
                                    entries.computeIfPresent(exchangeId,
                                            (k, e) -> new LockRenewerEntry(
                                                    e.message,
                                                    newLockedUntil.isAfter(e.lockedUntil) ? newLockedUntil : e.lockedUntil,
                                                    e.startTime, e.renewal == renewal ? null : e.renewal));
                                },
                                error -> {
                                    if (isTransient(error)) {
                                        // the SDK makes one attempt: keep the entry, so the next run renews
                                        // the lock again while it is still held
                                        LOG.warn("Failed to renew lock for exchangeId {}, will try again: {}",
                                                exchangeId, error.getMessage());
                                        answered(exchangeId, renewal);
                                    } else {
                                        LOG.warn("Failed to renew lock for exchangeId {}: {}", exchangeId,
                                                error.getMessage());
                                        entries.remove(exchangeId);
                                    }
                                });
            } catch (Exception e) {
                // as a transient error: the next run tries again
                LOG.warn("Failed to renew lock for exchangeId {}, will try again: {}", exchangeId, e.toString());
                answered(exchangeId, renewal);
            }
        }

        /** The renewal is no longer in flight: the next run may renew the lock again. */
        private void answered(String exchangeId, Renewal renewal) {
            entries.computeIfPresent(exchangeId,
                    (k, e) -> e.renewal == renewal ? new LockRenewerEntry(e.message, e.lockedUntil, e.startTime, null) : e);
        }

        /**
         * A transient error (such as a busy server or a connection that was lost) can be retried, as the processor of
         * the Service Bus .NET SDK does for its own lock renewal; another error (such as a lost lock) ends the renewal.
         */
        private static boolean isTransient(Throwable error) {
            return error instanceof ServiceBusException sbe && sbe.isTransient();
        }

        /** The renewal of a lock in flight, compared by identity. */
        private static final class Renewal {
            private final Instant sentAt;

            private Renewal(Instant sentAt) {
                this.sentAt = sentAt;
            }

            /** Whether a run at this time waits for its answer: it was sent less than two runs ago. */
            boolean awaited(Instant now) {
                return now.isBefore(sentAt.plusSeconds(LOCK_RENEW_IN_FLIGHT_SECONDS));
            }
        }

        private record LockRenewerEntry(
                ServiceBusReceivedMessage message, OffsetDateTime lockedUntil, Instant startTime, Renewal renewal) {
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
                        if (cause != null) {
                            DeadLetterOptions options = deadLetterOptions(cause);
                            settle(exchange, "dead-letter", () -> messageContext.deadLetter(options));
                        } else {
                            settle(exchange, "dead-letter", messageContext::deadLetter);
                        }
                    } else {
                        settle(exchange, "abandon", messageContext::abandon);
                    }
                }
            } finally {
                pendingExchanges.decrementAndGet();
            }
        }

        /**
         * Describes the failure for the dead-letter queue, bounded so that the diagnostics cannot be what makes the
         * broker reject the message.
         */
        private DeadLetterOptions deadLetterOptions(Exception cause) {
            String stackTrace = Arrays.stream(cause.getStackTrace())
                    .map(StackTraceElement::toString)
                    .collect(Collectors.joining("\n"));

            return new DeadLetterOptions()
                    .setDeadLetterReason(
                            limitToDeadLetterLength(String.format("%s: %s", cause.getClass().getName(),
                                    cause.getMessage())))
                    .setDeadLetterErrorDescription(limitToDeadLetterLength(stackTrace));
        }

        /**
         * Caps a dead-letter field, leaving room for the suffix so the result still fits.
         */
        private String limitToDeadLetterLength(String value) {
            return StringHelper.limitLength(value, MAX_DEAD_LETTER_TEXT_LENGTH - TRUNCATED_SUFFIX.length(),
                    TRUNCATED_SUFFIX);
        }

        /**
         * Settles the message, reporting a rejected request through the consumer's {@link ExceptionHandler}.
         * <p/>
         * Without this the exception only reaches {@code UnitOfWorkHelper.doneSynchronization}, which logs it as
         * "Exception occurred during onCompletion. This exception will be ignored." - so the operator sees a generic
         * core warning rather than a message from this consumer, and nothing routable. Either way the message is not
         * settled: it stays locked until the lock expires, Service Bus redelivers it, and it ends up dead-lettered with
         * {@code MaxDeliveryCountExceeded} instead of the reason collected here.
         */
        private void settle(Exchange exchange, String operation, Runnable settlement) {
            try {
                settlement.run();
            } catch (Exception e) {
                getExceptionHandler().handleException(
                        "Cannot " + operation + " the message. It stays locked and will be redelivered.", exchange, e);
            }
        }
    }
}
