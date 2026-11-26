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
package org.apache.camel.component.google.pubsub;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.google.api.core.AbstractApiService;
import com.google.api.core.ApiFuture;
import com.google.api.core.ApiService;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.stub.SubscriberStub;
import com.google.common.base.Strings;
import com.google.pubsub.v1.DeadLetterPolicy;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.PullRequest;
import com.google.pubsub.v1.PullResponse;
import com.google.pubsub.v1.ReceivedMessage;
import com.google.pubsub.v1.Subscription;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.component.google.pubsub.consumer.AcknowledgeCompletion;
import org.apache.camel.component.google.pubsub.consumer.AcknowledgeSync;
import org.apache.camel.component.google.pubsub.consumer.CamelMessageReceiver;
import org.apache.camel.component.google.pubsub.consumer.GooglePubsubAcknowledge;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.ShutdownAware;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.UnitOfWorkHelper;
import org.apache.camel.support.task.BackgroundTask;
import org.apache.camel.support.task.Tasks;
import org.apache.camel.support.task.budget.Budgets;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GooglePubsubConsumer extends DefaultConsumer implements ShutdownAware {

    private final Logger localLog;

    private final GooglePubsubEndpoint endpoint;
    private final Processor processor;
    private final AtomicInteger pendingExchanges = new AtomicInteger();
    /**
     * The subscriber loops started by the current start of this consumer. Incremented when the consumer starts, and
     * when it stops pulling (deferShutdown, doStop): a loop of an earlier generation ends instead of subscribing or
     * pulling again, also when the consumer has been started again meanwhile.
     */
    private final AtomicLong generation = new AtomicLong();
    /**
     * How long a subscriber loop waits before it subscribes or pulls again after a failure.
     */
    private Duration retryDelay = Duration.ofSeconds(5);
    /**
     * The retry delays the subscriber loops wait in now: a stop ends them at once, as the loops do not pull again.
     */
    private final Set<BackgroundTask> retryWaits = ConcurrentHashMap.newKeySet();
    /**
     * The subscriber loops of the current start of this consumer, for the health check.
     */
    private volatile List<SubscriberWrapper> loops = List.of();
    private ExecutorService executor;
    private ScheduledExecutorService taskExecutor;
    private final List<Subscriber> subscribers;
    private final Set<ApiFuture<PullResponse>> pendingSynchronousPullResponses;
    private final HeaderFilterStrategy headerFilterStrategy;
    private int resolvedMaxDeliveryAttempts;

    GooglePubsubConsumer(GooglePubsubEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.processor = processor;
        this.subscribers = new CopyOnWriteArrayList<>();
        this.pendingSynchronousPullResponses = ConcurrentHashMap.newKeySet();
        String loggerId = endpoint.getLoggerId();

        if (Strings.isNullOrEmpty(loggerId)) {
            loggerId = this.getClass().getName();
        }

        localLog = LoggerFactory.getLogger(loggerId);
        headerFilterStrategy = endpoint.getHeaderFilterStrategy();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        localLog.info("Starting Google PubSub consumer for {}/{}", endpoint.getProjectId(), endpoint.getDestinationName());

        resolvedMaxDeliveryAttempts = resolveMaxDeliveryAttempts();
        if (resolvedMaxDeliveryAttempts > 0) {
            localLog.info("Max delivery attempts enforcement enabled: {} for subscription {}",
                    resolvedMaxDeliveryAttempts, endpoint.getDestinationName());
        }

        // started again after a stop: the executor of the previous start is otherwise only shut down in doShutdown.
        // Its subscriber loops end on their own (generation), so a regular shutdown lets them finish and then
        // releases the threads
        if (executor != null) {
            endpoint.getCamelContext().getExecutorServiceManager().shutdown(executor);
        }
        executor = endpoint.createExecutor(this);
        // the scheduler of the retry delay is kept until doShutdown: a loop of the previous start that waits in that
        // delay must still be woken up, and then ends on the generation check
        if (taskExecutor == null) {
            taskExecutor = endpoint.getCamelContext().getExecutorServiceManager()
                    .newSingleThreadScheduledExecutor(this, "PubSubReconnectTask");
        }
        if (getHealthCheck() == null) {
            // readiness: DOWN while a subscriber loop cannot get messages (used when the consumer health checks of
            // Camel are enabled)
            setHealthCheck(new GooglePubsubConsumerHealthCheck(this, getRouteId()));
        }
        long current = generation.incrementAndGet();
        List<SubscriberWrapper> started = new ArrayList<>();
        for (int i = 0; i < endpoint.getConcurrentConsumers(); i++) {
            started.add(new SubscriberWrapper(current));
        }
        loops = List.copyOf(started);
        for (SubscriberWrapper loop : started) {
            executor.submit(loop);
        }
    }

    @Override
    protected void doStop() throws Exception {
        localLog.info("Stopping Google PubSub consumer for {}/{}", endpoint.getProjectId(), endpoint.getDestinationName());

        generation.incrementAndGet();
        cancelRetryWaits();
        if (!subscribers.isEmpty()) {
            localLog.info("Stopping subscribers for {}/{}", endpoint.getProjectId(), endpoint.getDestinationName());
            subscribers.forEach(AbstractApiService::stopAsync);
        }

        safeCancelSynchronousPullResponses();

        super.doStop();
    }

    @Override
    protected void doShutdown() throws Exception {
        if (taskExecutor != null) {
            getEndpoint().getCamelContext().getExecutorServiceManager().shutdown(taskExecutor);
            taskExecutor = null;
        }
        if (executor != null) {
            if (getEndpoint() != null && getEndpoint().getCamelContext() != null) {
                getEndpoint().getCamelContext().getExecutorServiceManager().shutdownGraceful(executor);
            } else {
                executor.shutdownNow();
            }
        }
        executor = null;

        super.doShutdown();
    }

    @Override
    public boolean deferShutdown(ShutdownRunningTask shutdownRunningTask) {
        // stop pulling: the consumer stays started until the pending exchanges are done, so the subscriber loops must
        // not subscribe or pull again when their subscriber terminates or their pull is cancelled.
        // Note: when a stopRoute with abortAfterTimeout=true times out, the route is started again while this consumer
        // is still started, so doStart does not run and no loop pulls again until the route is stopped and started
        generation.incrementAndGet();
        cancelRetryWaits();
        if (!subscribers.isEmpty()) {
            subscribers.forEach(AbstractApiService::stopAsync);
        }
        safeCancelSynchronousPullResponses();
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

    @Override
    protected boolean isStopOnFatalError() {
        // the consumer pulls messages in loops: it stops after a VirtualMachineError (CAMEL-27515)
        return true;
    }

    @Override
    public boolean handleFatalError(Throwable error) {
        if (!super.handleFatalError(error)) {
            return false;
        }
        // the loops no longer pull or subscribe again (isRunAllowed() is false): end their subscribers, pulls and
        // retry delays now, as a stop does, until the route is restarted
        cancelRetryWaits();
        subscribers.forEach(AbstractApiService::stopAsync);
        safeCancelSynchronousPullResponses();
        return true;
    }

    /**
     * The failure of a subscriber loop of the current start of this consumer that waits to subscribe or pull again, or
     * that has ended for good, or null while every loop gets messages (or tries to for the first time).
     */
    Throwable getLoopFailure() {
        for (SubscriberWrapper loop : loops) {
            Throwable failure = loop.failure;
            if (failure != null) {
                return failure;
            }
        }
        return null;
    }

    /**
     * The number of subscriber loops of the current start of this consumer that cannot get messages.
     */
    int getFailingLoops() {
        int failing = 0;
        for (SubscriberWrapper loop : loops) {
            if (loop.failure != null) {
                failing++;
            }
        }
        return failing;
    }

    /**
     * The highest number of failed attempts in a row of the subscriber loops of the current start of this consumer.
     */
    int getFailedAttempts() {
        int failed = 0;
        for (SubscriberWrapper loop : loops) {
            failed = Math.max(failed, loop.failuresInARow.get());
        }
        return failed;
    }

    /**
     * The retry delay of the subscriber loops.
     */
    Duration getRetryDelay() {
        return retryDelay;
    }

    private void cancelRetryWaits() {
        // a loop that waits does not pull again after a stop: end the wait now instead of after the retry delay (in
        // doShutdown the scheduler of the wait is shut down, which would leave the loop waiting until its thread is
        // interrupted)
        retryWaits.forEach(wait -> wait.cancel(false));
    }

    private void safeCancelSynchronousPullResponses() {
        for (ApiFuture<PullResponse> pullResponseApiFuture : pendingSynchronousPullResponses) {
            try {
                pullResponseApiFuture.cancel(true);
            } catch (Exception e) {
                localLog.warn("Exception while cancelling pending synchronous pull response", e);
            }
        }
        pendingSynchronousPullResponses.clear();
    }

    private int resolveMaxDeliveryAttempts() {
        if (endpoint.isMaxDeliveryAttemptsExplicitlySet()) {
            localLog.debug("Using explicitly configured maxDeliveryAttempts: {}", endpoint.getMaxDeliveryAttempts());
            return endpoint.getMaxDeliveryAttempts();
        }

        String subscriptionName = ProjectSubscriptionName.format(endpoint.getProjectId(), endpoint.getDestinationName());
        try (SubscriptionAdminClient adminClient = endpoint.getComponent().getSubscriptionAdminClient(endpoint)) {
            Subscription subscription = adminClient.getSubscription(subscriptionName);
            if (subscription.hasDeadLetterPolicy()) {
                DeadLetterPolicy dlp = subscription.getDeadLetterPolicy();
                int maxAttempts = dlp.getMaxDeliveryAttempts();
                if (maxAttempts > 0) {
                    localLog.info("Auto-fetched maxDeliveryAttempts={} from subscription dead-letter policy for {}",
                            maxAttempts, endpoint.getDestinationName());
                    return maxAttempts;
                }
            }
            localLog.debug("No dead-letter policy found on subscription {}, maxDeliveryAttempts enforcement disabled",
                    endpoint.getDestinationName());
        } catch (Exception e) {
            localLog.warn("Failed to auto-fetch maxDeliveryAttempts from subscription {}: {}. "
                          + "Max delivery attempts enforcement will be disabled. "
                          + "Set the maxDeliveryAttempts endpoint option explicitly to enable enforcement.",
                    endpoint.getDestinationName(), e.getMessage());
            localLog.debug("Auto-fetch failure details", e);
        }
        return 0;
    }

    public int getResolvedMaxDeliveryAttempts() {
        return resolvedMaxDeliveryAttempts;
    }

    /**
     * For tests: the retry delay of the subscriber loops (5 seconds by default), set before the consumer starts.
     */
    void setRetryDelay(Duration retryDelay) {
        this.retryDelay = retryDelay;
    }

    public void incrementPendingExchanges() {
        pendingExchanges.incrementAndGet();
    }

    public void decrementPendingExchanges() {
        pendingExchanges.decrementAndGet();
    }

    private class SubscriberWrapper implements Runnable {

        private final String subscriptionName;
        private final long wrapperGeneration;
        // failed attempts since the last pull or subscription that worked; written by the thread of this loop only, read
        // by the health check
        private final AtomicInteger failuresInARow = new AtomicInteger();
        // the last failure while the loop waits to subscribe or pull again, or the failure that ended it for good; null
        // while it gets messages (for the health check)
        private volatile Throwable failure;

        SubscriberWrapper(long wrapperGeneration) {
            this.wrapperGeneration = wrapperGeneration;
            subscriptionName = ProjectSubscriptionName.format(endpoint.getProjectId(), endpoint.getDestinationName());
        }

        /**
         * Whether this loop may subscribe or pull (again): the consumer runs and has not stopped pulling or been
         * started again since this loop was started.
         */
        private boolean isPullAllowed() {
            return isRunAllowed() && !isSuspendingOrSuspended() && wrapperGeneration == generation.get();
        }

        @Override
        public void run() {
            try {
                if (localLog.isDebugEnabled()) {
                    localLog.debug("Subscribing to {}", subscriptionName);
                }

                if (endpoint.isSynchronousPull()) {
                    synchronousPull(subscriptionName);
                } else {
                    asynchronousPull(subscriptionName);
                }

                localLog.debug("Exit run for subscription {}", subscriptionName);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ended("Interrupted while getting messages from PubSub " + subscriptionName, e);
            } catch (VirtualMachineError e) {
                // a JVM-level failure: the consumer stops consuming until the route is restarted, and its health check
                // is DOWN with the error (CAMEL-27515). It is not thrown on, as nothing reads the future of this loop. A
                // loop of an earlier start only logs it: it does not stop the consumer started again since
                localLog.error("Fatal error getting messages from PubSub {}. The consumer stops consuming until the route"
                               + " is restarted.",
                        subscriptionName, e);
                if (wrapperGeneration == generation.get()) {
                    handleFatalError(e);
                }
            } catch (Exception | Error e) {
                // not expected, as the loops retry after a failure (such as an exception thrown by the exception
                // handler)
                ended("Failure getting messages from PubSub " + subscriptionName, e);
            }
        }

        /**
         * This loop ends while it may still pull: it reports the failure until the consumer is started again.
         */
        private void ended(String message, Throwable e) {
            if (isPullAllowed()) {
                localLog.error("{}. The subscriber loop ends until the route is restarted.", message, e);
                failure = e;
            } else {
                localLog.debug(message, e);
            }
        }

        private void asynchronousPull(String subscriptionName) throws InterruptedException {
            while (isPullAllowed()) {
                long attemptStarted = System.nanoTime();
                Subscriber subscriber = null;
                boolean subscriberAdded = false;
                Throwable attemptFailure = null;
                try {
                    MessageReceiver messageReceiver
                            = new CamelMessageReceiver(GooglePubsubConsumer.this, endpoint, processor);
                    subscriber = endpoint.getComponent().getSubscriber(subscriptionName, messageReceiver, endpoint);
                    subscriber.startAsync().awaitRunning();
                    // Only add to list after successful startup
                    subscribers.add(subscriber);
                    subscriberAdded = true;
                    // a stop while the subscriber was starting missed it; stop it here or awaitTerminated never returns
                    if (!isPullAllowed()) {
                        subscriber.stopAsync();
                    }
                    awaitTerminated(subscriber);
                } catch (VirtualMachineError e) {
                    throw e;
                } catch (Exception | Error e) {
                    // the failure of the subscriber (such as PERMISSION_DENIED or NOT_FOUND) instead of the
                    // IllegalStateException of awaitRunning or awaitTerminated that wraps it
                    attemptFailure = subscriber != null && subscriber.state() == ApiService.State.FAILED
                            ? subscriber.failureCause() : e;
                } finally {
                    if (subscriber != null) {
                        localLog.debug("Stopping async subscriber {}", subscriptionName);
                        subscriber.stopAsync();
                    }
                    // Ensure cleanup from list
                    if (subscriberAdded) {
                        subscribers.remove(subscriber);
                    }
                }
                if (attemptFailure != null) {
                    retryAfterFailure("Failure getting messages from PubSub " + subscriptionName + ". Subscribing again",
                            attemptFailure, attemptStarted);
                }
            }
        }

        /**
         * Waits until the subscriber terminates. After failed attempts, the subscriber only counts as working once it
         * has run for the retry delay: it is running before its streaming pull is answered, so a missing subscription
         * or permission fails it right after the start.
         */
        private void awaitTerminated(Subscriber subscriber) {
            if (failuresInARow.get() > 0) {
                try {
                    subscriber.awaitTerminated(retryDelay.toMillis(), TimeUnit.MILLISECONDS);
                    return;
                } catch (TimeoutException e) {
                    pulled();
                }
            }
            subscriber.awaitTerminated();
        }

        private void synchronousPull(String subscriptionName) throws InterruptedException {
            while (isPullAllowed()) {
                long attemptStarted = System.nanoTime();
                ApiFuture<PullResponse> synchronousPullResponseFuture = null;
                Throwable attemptFailure = null;
                try (SubscriberStub subscriber = endpoint.getComponent().getSubscriberStub(endpoint)) {

                    PullRequest pullRequest = PullRequest.newBuilder()
                            .setMaxMessages(endpoint.getMaxMessagesPerPoll())
                            .setReturnImmediately(false)
                            .setSubscription(subscriptionName)
                            .build();

                    synchronousPullResponseFuture = subscriber.pullCallable().futureCall(pullRequest);
                    pendingSynchronousPullResponses.add(synchronousPullResponseFuture);
                    // a stop while the pull was being issued missed this future; cancel it here or get() blocks
                    if (!isPullAllowed()) {
                        synchronousPullResponseFuture.cancel(true);
                    }
                    PullResponse pullResponse = synchronousPullResponseFuture.get();
                    pulled();
                    for (ReceivedMessage message : pullResponse.getReceivedMessagesList()) {
                        PubsubMessage pubsubMessage = message.getMessage();
                        Exchange exchange = createExchange(true);
                        exchange.getIn().setBody(pubsubMessage.getData().toByteArray());

                        // Standard headers
                        exchange.getIn().setHeader(GooglePubsubConstants.ACK_ID, message.getAckId());
                        exchange.getIn().setHeader(GooglePubsubConstants.MESSAGE_ID, pubsubMessage.getMessageId());
                        exchange.getIn().setHeader(GooglePubsubConstants.SUBSCRIPTION_NAME, endpoint.getDestinationName());
                        exchange.getIn().setHeader(GooglePubsubConstants.PUBLISH_TIME, pubsubMessage.getPublishTime());
                        // Deprecated:  replaced by headerFilterStrategy
                        exchange.getIn().setHeader(GooglePubsubConstants.ATTRIBUTES, pubsubMessage.getAttributesMap());

                        // Delivery attempt (requires dead-letter policy on subscription)
                        int deliveryAttempt = message.getDeliveryAttempt();
                        if (deliveryAttempt > 0) {
                            exchange.getIn().setHeader(GooglePubsubConstants.DELIVERY_ATTEMPT, deliveryAttempt);
                        }

                        // Enforce maxDeliveryAttempts: nack without processing if limit reached
                        if (resolvedMaxDeliveryAttempts > 0 && deliveryAttempt >= resolvedMaxDeliveryAttempts) {
                            localLog.info(
                                    "Message {} has reached max delivery attempts ({}/{}), nacking to route to dead-letter topic",
                                    pubsubMessage.getMessageId(), deliveryAttempt, resolvedMaxDeliveryAttempts);
                            GooglePubsubAcknowledge ack = new AcknowledgeSync(
                                    () -> endpoint.getComponent().getSubscriberStub(endpoint), subscriptionName,
                                    message.getAckId());
                            try {
                                ack.nack(exchange);
                            } catch (Exception e) {
                                // keep pulling: the message is delivered again when its ack deadline expires and is
                                // nacked then, while an exception here would end the pull loop of this consumer
                                getExceptionHandler().handleException(
                                        "Error nacking message " + pubsubMessage.getMessageId()
                                                                      + " that reached max delivery attempts",
                                        exchange, e);
                            }
                            continue;
                        }

                        //existing subscriber can not be propagated, because it will be closed at the end of this block
                        //subscriber will be created at the moment of use
                        // (see  https://issues.apache.org/jira/browse/CAMEL-18447)
                        if (endpoint.getAckMode() != GooglePubsubConstants.AckMode.NONE) {
                            // acks or nacks the message received, with the ack id it was received with: the route
                            // may change or remove the ack id header, or replace the message, before it completes
                            GooglePubsubAcknowledge acknowledge = new AcknowledgeSync(
                                    () -> endpoint.getComponent().getSubscriberStub(endpoint), subscriptionName,
                                    message.getAckId());
                            exchange.getExchangeExtension().addOnCompletion(new AcknowledgeCompletion(acknowledge));
                        } else {
                            // the route acks or nacks with the ack ids of the exchange it gives, which may be an
                            // aggregated exchange of several messages
                            GooglePubsubAcknowledge acknowledge = new AcknowledgeSync(
                                    () -> endpoint.getComponent().getSubscriberStub(endpoint), subscriptionName);
                            exchange.getIn().setHeader(GooglePubsubConstants.GOOGLE_PUBSUB_ACKNOWLEDGE, acknowledge);
                        }

                        // Inherit the rest of headers
                        for (String pubSubHeader : pubsubMessage.getAttributesMap().keySet()) {
                            String value = pubsubMessage.getAttributesMap().get(pubSubHeader);
                            if (headerFilterStrategy != null
                                    && headerFilterStrategy.applyFilterToExternalHeaders(pubSubHeader, value, exchange)) {
                                continue;
                            }
                            exchange.getIn().setHeader(pubSubHeader, value);
                        }

                        pendingExchanges.incrementAndGet();
                        try {
                            try {
                                processor.process(exchange);
                            } catch (Exception e) {
                                exchange.setException(e);
                            }

                            // Handle exception if one occurred
                            if (exchange.getException() != null) {
                                getExceptionHandler().handleException("Error processing exchange", exchange,
                                        exchange.getException());
                            }

                            // Execute synchronization callbacks (ACK/NACK) based on exchange status
                            // This is required because we are directly calling processor.process() outside of the normal
                            // Camel routing engine, so we must manually trigger the OnCompletion callbacks
                            if (endpoint.getAckMode() != GooglePubsubConstants.AckMode.NONE) {
                                List<Synchronization> synchronizations
                                        = exchange.getExchangeExtension().handoverCompletions();
                                UnitOfWorkHelper.doneSynchronizations(exchange, synchronizations);
                            }
                        } finally {
                            pendingExchanges.decrementAndGet();
                        }
                    }
                } catch (CancellationException e) {
                    localLog.debug("PubSub synchronous pull request cancelled", e);
                } catch (InterruptedException | VirtualMachineError e) {
                    throw e;
                } catch (ExecutionException e) {
                    // the failure of the pull, retryable or not (such as PERMISSION_DENIED while an IAM binding
                    // is changed, or NOT_FOUND while the subscription is created again)
                    attemptFailure = e.getCause() != null ? e.getCause() : e;
                } catch (Exception | Error e) {
                    // such as credentials that cannot be loaded (IOException), or a client library that does not
                    // match (NoClassDefFoundError): an attempt at once would fail the same way
                    attemptFailure = e;
                } finally {
                    if (synchronousPullResponseFuture != null) {
                        pendingSynchronousPullResponses.remove(synchronousPullResponseFuture);
                    }
                }
                if (attemptFailure != null) {
                    retryAfterFailure("Failure getting messages from PubSub " + subscriptionName + ". Pulling again",
                            attemptFailure, attemptStarted);
                }
            }
        }

        /**
         * A failed attempt (Exception or Error) ends the subscription or the pull, but not the loop: it is logged,
         * given to the exception handler with bridgeErrorHandler, and the loop subscribes or pulls again after the
         * retry delay, until the cause is gone. A VirtualMachineError is thrown on.
         */
        private void retryAfterFailure(String message, Throwable e, long attemptStarted) throws InterruptedException {
            VirtualMachineError fatal = ObjectHelper.getVirtualMachineError(e);
            if (fatal != null) {
                throw fatal;
            }
            if (!isPullAllowed()) {
                // the consumer stops: a failure of the subscription or pull it ends
                localLog.debug(message, e);
                return;
            }
            failuresInARow.incrementAndGet();
            // after the count: a failure the health check sees comes with its count
            failure = e;
            logFailure(message + " within " + TimeUtils.printDuration(retryDelay) + '.', e);
            // allow camel error handler to be aware
            if (endpoint.isBridgeErrorHandler()) {
                getExceptionHandler().handleException(e);
            }
            waitBeforeRetry(attemptStarted);
        }

        /**
         * Logs a failed attempt: the first one in a row with its stack trace, the next ones in one line (the stack
         * trace at DEBUG), as the loop retries until the cause is gone.
         */
        private void logFailure(String message, Throwable e) {
            int failures = failuresInARow.get();
            if (failures <= 1) {
                localLog.error(message, e);
            } else {
                localLog.warn("{} ({} failed attempts in a row: {})", message, failures, e.toString());
                localLog.debug(message, e);
            }
        }

        /**
         * A pull or a subscription worked: logs once that the loop recovered from failed attempts.
         */
        private void pulled() {
            int failures = failuresInARow.get();
            if (failures > 0) {
                localLog.info("Getting messages from PubSub {} again after {} failed attempts", subscriptionName,
                        failures);
                // the failure before the count, as the health check reads them in the other order
                failure = null;
                failuresInARow.set(0);
            }
        }

        /**
         * Waits after a failed attempt until the retry delay has passed since the attempt started, so a loop makes at
         * most one failed attempt per retry delay. An attempt that already took that long (a retryable error is only
         * returned after the client library retried it for up to a minute) is followed by the next one at once. A stop
         * of the consumer ends the wait.
         */
        private void waitBeforeRetry(long attemptStarted) throws InterruptedException {
            long remaining = retryDelay.toMillis() - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - attemptStarted);
            ScheduledExecutorService scheduler = taskExecutor;
            if (remaining <= 0 || scheduler == null || !isPullAllowed()) {
                return;
            }
            BackgroundTask wait = Tasks.backgroundTask()
                    .withBudget(Budgets.iterationTimeBudget()
                            .withMaxIterations(1)
                            .withInitialDelay(Duration.ofMillis(remaining))
                            .withInterval(Duration.ofMillis(1))
                            .withUnlimitedDuration()
                            .build())
                    .withScheduledExecutor(scheduler)
                    .withName("PubSubRetryDelay")
                    .build();
            retryWaits.add(wait);
            try {
                // a stop cancels the waits in retryWaits after it stopped the pulling: a stop that came before this
                // wait was added is seen here, a later one cancels it (also before it runs, then it returns at once)
                if (isPullAllowed()) {
                    wait.run(getEndpoint().getCamelContext(), () -> true);
                }
            } finally {
                retryWaits.remove(wait);
            }
            // the wait returns at once when the thread is interrupted (shutdown): end the loop instead of retrying
            if (Thread.interrupted()) {
                throw new InterruptedException("Interrupted while waiting to get messages from PubSub again");
            }
        }
    }
}
