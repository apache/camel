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
package org.apache.camel.component.nats;

import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.NavigableSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.JetStreamStatusException;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import io.nats.client.PullSubscribeOptions;
import io.nats.client.PushSubscribeOptions;
import io.nats.client.api.AckPolicy;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.DeliverPolicy;
import io.nats.client.api.RetentionPolicy;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.api.StreamInfo;
import io.nats.client.support.Status;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.trait.message.MessageTrait;
import org.apache.camel.trait.message.RedeliveryTraitPayload;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NatsConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(NatsConsumer.class);
    // an idle JetStream pull loop asks the server for its consumer at most this often
    private static final long CONSUMER_CHECK_INTERVAL_NANOS = 5_000_000_000L;

    private final Processor processor;
    private ExecutorService executor;
    private Connection connection;
    private Dispatcher dispatcher;
    private boolean active;
    // set by the consuming task, and again by the push consumer keeper, and read when the consumer stops
    private volatile JetStreamSubscription jetStreamSubscription;
    private NatsJetStreamPushConsumerKeeper pushConsumerKeeper;

    public NatsConsumer(NatsEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.processor = processor;
    }

    @Override
    public NatsEndpoint getEndpoint() {
        return (NatsEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        LOG.debug("Starting Nats Consumer");
        this.executor = this.getEndpoint().createExecutor(this);

        LOG.debug("Getting Nats Connection");
        this.connection = this.getEndpoint().getConfiguration().getConnection() != null
                ? this.getEndpoint().getConfiguration().getConnection()
                : this.getEndpoint().getConnection();

        NatsConfiguration config = this.getEndpoint().getConfiguration();
        if (config.isManualAck() && !config.isJetstreamEnabled()) {
            LOG.warn("manualAck=true has no effect without jetstreamEnabled=true; standard NATS has no acknowledgment");
        }
        if (config.isManualAck() && config.getAckPolicy() == AckPolicy.None) {
            LOG.warn(
                    "manualAck=true with ackPolicy=None: the server acknowledges automatically on delivery, manual ack/nak calls will have no effect");
        }
        this.executor.submit(new NatsConsumingTask(this.connection, config));
    }

    @Override
    protected void doStop() throws Exception {
        final NatsConfiguration configuration = this.getEndpoint().getConfiguration();

        if (this.pushConsumerKeeper != null) {
            this.pushConsumerKeeper.stop();
            this.pushConsumerKeeper = null;
        }

        if (configuration.isFlushConnection() && ObjectHelper.isNotEmpty(this.connection)) {
            LOG.debug("Flushing Messages before stopping");
            this.connection.flush(Duration.ofMillis(configuration.getFlushTimeout()));
        }

        if (ObjectHelper.isNotEmpty(this.dispatcher)) {
            try {
                this.dispatcher.unsubscribe(configuration.getTopic());
            } catch (final Exception e) {
                this.getExceptionHandler().handleException("Error during unsubscribing", e);
            }
        }

        if (ObjectHelper.isNotEmpty(this.jetStreamSubscription) && this.jetStreamSubscription.isActive()) {
            try {
                this.jetStreamSubscription.unsubscribe();
            } catch (final Exception e) {
                this.getExceptionHandler().handleException("Error during unsubscribing", e);
            }
        }

        LOG.debug("Stopping Nats Consumer");
        if (this.executor != null) {
            if (this.getEndpoint() != null && this.getEndpoint().getCamelContext() != null) {
                this.getEndpoint().getCamelContext().getExecutorServiceManager().shutdownNow(this.executor);
            } else {
                this.executor.shutdownNow();
            }
        }
        this.executor = null;

        if (ObjectHelper.isEmpty(configuration.getConnection()) && ObjectHelper.isNotEmpty(this.connection)) {
            LOG.debug("Closing Nats Connection");
            if (!this.connection.getStatus().equals(Connection.Status.CLOSED)) {
                this.connection.close();
            }
        }
        super.doStop();
    }

    public boolean isActive() {
        return this.active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    class NatsConsumingTask implements Runnable {

        private final Connection connection;
        private final NatsConfiguration configuration;
        // stream sequences of the JetStream messages fetched by the pull loop and not acknowledged yet, and the highest
        // fetched: where a new ephemeral consumer must start when the server deleted the one this consumer used
        private final NavigableSet<Long> unacknowledged = new ConcurrentSkipListSet<>();
        private final AtomicLong highestFetched = new AtomicLong();
        // with AckPolicy.None the delivery is the acknowledgement: no fetched sequence waits for an ack
        private boolean trackUnacknowledged = true;
        // when the stream was created, as seen at the subscribe: a different time later means that the stream was
        // created again and its sequences started again at 1
        private ZonedDateTime streamCreated;
        private long lastConsumerCheck = System.nanoTime();
        // whether the WARN that the durable consumer is gone was logged, so that it is logged once until it is back
        private boolean durableGoneReported;

        NatsConsumingTask(Connection connection, NatsConfiguration configuration) {
            this.connection = connection;
            this.configuration = configuration;
        }

        @Override
        public void run() {
            try {
                NatsConfiguration config = getEndpoint().getConfiguration();
                String topic = config.getTopic();
                String queueName = config.getQueueName();
                String maxMessagesStr = config.getMaxMessages();
                Integer maxMessages = null;
                if (ObjectHelper.isNotEmpty(maxMessagesStr)) {
                    maxMessages = Integer.parseInt(maxMessagesStr);
                }

                if (config.isJetstreamEnabled() && connection.getServerInfo().isJetStreamAvailable()) {
                    setupJetStreamConsumer(topic, queueName);
                } else {
                    setupStandardNatsConsumer(topic, queueName, maxMessages);
                }
            } catch (final Exception e) {
                NatsConsumer.this.getExceptionHandler().handleException("Error during processing", e);
            }

        }

        private void setupJetStreamConsumer(String topic, String queueName) throws IOException, JetStreamApiException {
            String streamName = this.configuration.getJetstreamName();
            String durableName = this.configuration.getDurableName();

            String subscriptionType = this.configuration.isPullSubscription() ? "PULL" : "PUSH";
            LOG.debug("Setting up JetStream {}/{} consumer for stream: '{}', topic: {}",
                    subscriptionType,
                    ObjectHelper.isNotEmpty(durableName)
                            ? String.format("DURABLE, durableName: '%s'", durableName) : "EPHEMERAL",
                    streamName,
                    this.configuration.getTopic());

            JetStreamManagement jsm = connection.jetStreamManagement();
            StreamInfo streamInfo = lookupStream(jsm, streamName);
            if (streamInfo != null) {
                LOG.debug("JetStream stream '{}' already exists. Skipping creation.", streamName);
            } else {
                LOG.debug("Creating JetStream {}", streamName);
                StreamConfiguration streamConfig = StreamConfiguration.builder()
                        .name(streamName)
                        .subjects(topic)
                        .build();
                streamInfo = jsm.addStream(streamConfig);
                LOG.info("JetStream stream '{}' created successfully.", streamName);
            }
            streamCreated = streamInfo != null ? streamInfo.getCreateTime() : null;

            ConsumerConfiguration cc = configuration.getConsumerConfiguration();
            if (cc == null) {
                ConsumerConfiguration.Builder ccBuilder = ConsumerConfiguration.builder();
                ccBuilder.deliverSubject(null);
                if (durableName != null) {
                    ccBuilder.durable(durableName + "-durable");
                }
                if (configuration.getAckPolicy() != null) {
                    ccBuilder.ackPolicy(configuration.getAckPolicy());
                }
                if (configuration.getAckWait() > 0) {
                    ccBuilder.ackWait(configuration.getAckWait());
                }
                if (configuration.getMaxDeliver() > 0) {
                    ccBuilder.maxDeliver(configuration.getMaxDeliver());
                }
                cc = ccBuilder.build();
            }
            trackUnacknowledged = cc.getAckPolicy() != AckPolicy.None;

            CamelNatsMessageHandler messageHandler = new CamelNatsMessageHandler();

            if (this.configuration.isPullSubscription()) {
                PullSubscribeOptions pullOptions = PullSubscribeOptions.builder()
                        .configuration(cc)
                        .build();

                LOG.info(
                        "Subscribing topic: {} using AckPolicy: {} (ackWait:{} nackWait:{} pullBatchSize:{} pullFetchTimeout:{})",
                        configuration.getTopic(), configuration.getAckPolicy(), configuration.getAckWait(),
                        configuration.getNackWait(), configuration.getPullBatchSize(), configuration.getPullFetchTimeout());

                NatsConsumer.this.jetStreamSubscription = this.connection.jetStream().subscribe(
                        NatsConsumer.this.getEndpoint().getConfiguration().getTopic(),
                        pullOptions);

                NatsConsumer.this.setActive(true);
                runPullFetchLoop(NatsConsumer.this.jetStreamSubscription, messageHandler, cc);
            } else {
                NatsConsumer.this.dispatcher = this.connection.createDispatcher(messageHandler);

                PushSubscribeOptions pushOptions = PushSubscribeOptions.builder()
                        .configuration(cc)
                        .deliverGroup(queueName)
                        .build();

                boolean autoAck = false; // ACK is handled by Camel

                LOG.info("Subscribing topic: {} queue:{} using AckPolicy: {} (ackWait:{} nackWait:{})",
                        configuration.getTopic(), queueName, configuration.getAckPolicy(), configuration.getAckWait(),
                        configuration.getNackWait());

                // the keeper subscribes again when the server deleted the ephemeral consumer (after a disconnect of
                // more than its inactive threshold, server restart); a durable consumer is not created again
                NatsJetStreamPushConsumerKeeper keeper = new NatsJetStreamPushConsumerKeeper(
                        NatsConsumer.this, this.connection, streamName, configuration.getTopic(), queueName,
                        dispatcher, messageHandler, cc, NatsConsumer.this.executor,
                        sub -> NatsConsumer.this.jetStreamSubscription = sub);
                NatsConsumer.this.jetStreamSubscription = this.connection.jetStream().subscribe(
                        NatsConsumer.this.getEndpoint().getConfiguration().getTopic(),
                        queueName,
                        dispatcher,
                        keeper,
                        autoAck,
                        pushOptions);
                NatsConsumer.this.pushConsumerKeeper = keeper;
                keeper.start(NatsConsumer.this.jetStreamSubscription,
                        streamInfo != null ? streamInfo.getCreateTime() : null,
                        streamInfo != null && streamInfo.getStreamState() != null
                                ? streamInfo.getStreamState().getLastSequence() : -1);

                NatsConsumer.this.setActive(true);
            }
        }

        private void runPullFetchLoop(
                JetStreamSubscription subscription, CamelNatsMessageHandler messageHandler, ConsumerConfiguration cc) {
            JetStreamSubscription sub = subscription;
            int batchSize = configuration.getPullBatchSize();
            Duration fetchTimeout = Duration.ofMillis(configuration.getPullFetchTimeout());
            while (NatsConsumer.this.isRunAllowed() && !Thread.currentThread().isInterrupted()) {
                long fetchStarted = System.nanoTime();
                try {
                    List<Message> messages = sub.fetch(batchSize, fetchTimeout);
                    if (messages.isEmpty()) {
                        // jnats ends a fetch at once when its pull request has no responder (status 503), for example
                        // while the consumer is gone, whereas an idle healthy consumer ends it at its expiry, shortly
                        // before the fetch timeout: only an empty fetch that ended early asks the server for the
                        // consumer, so that an idle healthy consumer does not send that request every few seconds
                        boolean endedEarly = System.nanoTime() - fetchStarted < fetchTimeout.toNanos() / 2;
                        if (endedEarly && consumerCheckDue() && consumerGone(sub)) {
                            sub = consumerDeleted(sub, cc);
                            continue;
                        }
                        // wait for the rest of the fetch timeout, so that the loop does not send pull requests back to
                        // back
                        waitRestOfFetch(fetchStarted, fetchTimeout);
                        continue;
                    }
                    durableGoneReported = false;
                    for (Message msg : messages) {
                        if (!NatsConsumer.this.isRunAllowed()) {
                            break;
                        }
                        try {
                            track(msg);
                            messageHandler.onMessage(msg);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                } catch (JetStreamStatusException e) {
                    // Synchronous pull calls may throw JetStreamStatusException for statuses such as 409. Report it and
                    // continue; for 409 Consumer Deleted an ephemeral consumer is created again (see consumerDeleted).
                    NatsConsumer.this.getExceptionHandler().handleException("JetStream status during pull fetch", e);
                    Status status = e.getStatus();
                    if (status != null && Status.CONSUMER_DELETED.equals(status.getMessage())) {
                        sub = consumerDeleted(sub, cc);
                    } else {
                        // for example 409 Consumer is push based: do not send pull requests back to back
                        try {
                            waitRestOfFetch(fetchStarted, fetchTimeout);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                } catch (IllegalStateException e) {
                    // Subscription was closed (e.g. consumer is stopping), exit the loop
                    LOG.debug("Pull subscription closed, stopping fetch loop", e);
                    return;
                } catch (InterruptedException e) {
                    // the consumer is stopping
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    NatsConsumer.this.getExceptionHandler().handleException("Error during JetStream pull fetch", e);
                    // as for the other error statuses: do not send the next pull request at once
                    try {
                        waitRestOfFetch(fetchStarted, fetchTimeout);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }

        private void waitRestOfFetch(long fetchStarted, Duration fetchTimeout) throws InterruptedException {
            long left = fetchTimeout.toNanos() - (System.nanoTime() - fetchStarted);
            if (left > 0) {
                TimeUnit.NANOSECONDS.sleep(left);
            }
        }

        /**
         * Whether the server no longer has the consumer of this pull subscription. The server deletes an ephemeral
         * consumer (no durable name) that saw no pull request for its inactive threshold (5 seconds by default), for
         * example while the connection was down for a few seconds, and a server restart loses it. jnats then answers
         * every fetch with an empty list (the pull request has no responder), and the subscription never receives
         * anything again.
         */
        private boolean consumerCheckDue() {
            long now = System.nanoTime();
            if (now - lastConsumerCheck < CONSUMER_CHECK_INTERVAL_NANOS) {
                return false;
            }
            lastConsumerCheck = now;
            return true;
        }

        private boolean consumerGone(JetStreamSubscription sub) {
            try {
                // jnats answers null when the server has no such consumer (api error 10014), and throws for any other
                // error, for example 404 stream not found (10059)
                if (sub.getConsumerInfo() == null) {
                    return true;
                }
                durableGoneReported = false;
                return false;
            } catch (JetStreamApiException e) {
                return e.getErrorCode() == 404;
            } catch (Exception e) {
                // not connected, timeout: the consumer may well still be there, the next fetch tells
                LOG.debug("Cannot check the JetStream consumer of the pull subscription", e);
                return false;
            }
        }

        /**
         * The server no longer has the consumer of the pull subscription. An ephemeral consumer is created again (see
         * {@link #subscribeAgain}); when that fails, the failure goes to the exception handler and the next consumer
         * check tries again. A durable consumer is not created again: it is gone only when an administrator deleted it
         * or after its inactive threshold, and creating it again from its configuration would replay the stream from
         * its deliver policy. Its subscription is kept, so the fetches work again when the durable is created again
         * with the same name.
         *
         * @return the subscription to fetch from
         */
        private JetStreamSubscription consumerDeleted(JetStreamSubscription sub, ConsumerConfiguration cc) {
            if (cc.getDurable() != null) {
                if (!durableGoneReported) {
                    durableGoneReported = true;
                    LOG.warn("The durable JetStream consumer {} of the pull subscription on topic {} is gone; it is not"
                             + " created again, and the fetches resume when it is created again with the same name",
                            cc.getDurable(), configuration.getTopic());
                }
                return sub;
            }
            try {
                return subscribeAgain(sub, cc);
            } catch (Exception e) {
                NatsConsumer.this.getExceptionHandler().handleException(
                        "Error subscribing again after the JetStream consumer was deleted", e);
                return sub;
            }
        }

        /**
         * Subscribes again after the server deleted the ephemeral consumer, starting at the first message this consumer
         * has not acknowledged, so that nothing is skipped and the stream is not replayed from its deliver policy.
         * <p>
         * A sequence that is never acknowledged stays in the unacknowledged set until this resubscribe, for example a
         * message nak'd until maxDeliver is used up. The new consumer then starts at that old sequence, and the route
         * sees the messages after it again (at-least-once). With ackPolicy=None no sequence is tracked: the new
         * consumer starts after the highest one fetched.
         * <p>
         * The consumer configuration is used as it is (no start sequence) for a stream with WorkQueue retention, which
         * accepts only the deliver policy All and has already removed the acknowledged messages, and for a stream that
         * was created again since the subscribe (its sequences started again at 1).
         */
        private JetStreamSubscription subscribeAgain(JetStreamSubscription old, ConsumerConfiguration cc)
                throws IOException, JetStreamApiException {
            StreamInfo stream = this.connection.jetStreamManagement().getStreamInfo(configuration.getJetstreamName());
            ZonedDateTime created = stream.getCreateTime();
            if (created != null && streamCreated != null && !created.isEqual(streamCreated)) {
                LOG.warn("The JetStream stream {} was created again since the subscribe; consuming it from its deliver"
                         + " policy",
                        configuration.getJetstreamName());
                unacknowledged.clear();
                highestFetched.set(0);
            }
            if (created != null) {
                streamCreated = created;
            }
            boolean workQueue = stream.getConfiguration() != null
                    && stream.getConfiguration().getRetentionPolicy() == RetentionPolicy.WorkQueue;
            ConsumerConfiguration again = cc;
            long start = unacknowledged.isEmpty() ? highestFetched.get() + 1 : unacknowledged.first();
            boolean resume = !workQueue && highestFetched.get() > 0;
            if (resume) {
                // a start time (DeliverPolicy.ByStartTime) together with a start sequence is rejected by the server
                again = ConsumerConfiguration.builder(cc)
                        .deliverPolicy(DeliverPolicy.ByStartSequence)
                        .startSequence(start)
                        .startTime(null)
                        .build();
            }
            LOG.warn("The JetStream consumer of the pull subscription on topic {} is gone; subscribing again{}",
                    configuration.getTopic(), resume ? " from stream sequence " + start : "");
            JetStreamSubscription sub = this.connection.jetStream().subscribe(configuration.getTopic(),
                    PullSubscribeOptions.builder().configuration(again).build());
            unacknowledged.clear();
            NatsConsumer.this.jetStreamSubscription = sub;
            if (!NatsConsumer.this.isRunAllowed()) {
                // stopping: doStop may have unsubscribed the old subscription before this one was assigned
                try {
                    sub.unsubscribe();
                } catch (Exception e) {
                    LOG.debug("Error unsubscribing the new pull subscription while stopping", e);
                }
            }
            try {
                old.unsubscribe();
            } catch (Exception e) {
                LOG.debug("Error unsubscribing the pull subscription of the deleted consumer", e);
            }
            return sub;
        }

        private void track(Message msg) {
            if (msg.isJetStream()) {
                long seq = msg.metaData().streamSequence();
                if (trackUnacknowledged) {
                    unacknowledged.add(seq);
                }
                highestFetched.accumulateAndGet(seq, Math::max);
            }
        }

        private void acknowledged(Message msg) {
            if (msg.isJetStream()) {
                unacknowledged.remove(msg.metaData().streamSequence());
            }
        }

        private void setupStandardNatsConsumer(String topic, String queueName, Integer maxMessages) {
            NatsConsumer.this.dispatcher = connection.createDispatcher(new CamelNatsMessageHandler());
            if (ObjectHelper.isNotEmpty(queueName)) {
                NatsConsumer.this.dispatcher = NatsConsumer.this.dispatcher.subscribe(topic, queueName);
            } else {
                NatsConsumer.this.dispatcher = NatsConsumer.this.dispatcher.subscribe(topic);
            }
            if (maxMessages != null) {
                NatsConsumer.this.dispatcher.unsubscribe(topic, maxMessages);
            }
            if (NatsConsumer.this.dispatcher.isActive()) {
                NatsConsumer.this.setActive(true);
            }
        }

        class CamelNatsMessageHandler implements MessageHandler {

            final boolean ackPolicyNone = configuration.getAckPolicy() == AckPolicy.None;
            final boolean manualAckEnabled = configuration.isManualAck() && configuration.isJetstreamEnabled();

            @Override
            public void onMessage(Message msg) throws InterruptedException {
                LOG.debug("Received Message: {}", msg);
                final Exchange exchange = NatsConsumer.this.createExchange(false);

                if (!manualAckEnabled) {
                    exchange.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
                        @Override
                        public void onComplete(Exchange exchange) {
                            LOG.debug("ACK");
                            msg.ack();
                            acknowledged(msg);
                        }

                        @Override
                        public void onFailure(Exchange exchange) {
                            if (ackPolicyNone) {
                                // ACK policy is none which means that we should auto ACK even if the message processed failed in Camel
                                LOG.debug("ACK");
                                msg.ack();
                                acknowledged(msg);
                            } else {
                                LOG.debug("NACK (delay:{})", configuration.getNackWait());
                                if (configuration.getNackWait() <= 0) {
                                    msg.nak();
                                } else {
                                    msg.nakWithDelay(configuration.getNackWait());
                                }
                            }
                        }
                    });
                }
                try {
                    exchange.getIn().setBody(msg.getData());
                    exchange.getIn().setHeader(NatsConstants.NATS_REPLY_TO, msg.getReplyTo());
                    exchange.getIn().setHeader(NatsConstants.NATS_SID, msg.getSID());
                    exchange.getIn().setHeader(NatsConstants.NATS_SUBJECT, msg.getSubject());
                    exchange.getIn().setHeader(NatsConstants.NATS_QUEUE_NAME, msg.getSubscription().getQueueName());
                    exchange.getIn().setHeader(NatsConstants.NATS_MESSAGE_TIMESTAMP, System.currentTimeMillis());
                    if (msg.isStatusMessage() && msg.getStatus() != null) {
                        exchange.getIn().setHeader(NatsConstants.NATS_STATUS_CODE, msg.getStatus().getCode());
                        exchange.getIn().setHeader(NatsConstants.NATS_STATUS_ERROR, msg.getStatus().getMessage());
                    }
                    if (msg.getHeaders() != null) {
                        final HeaderFilterStrategy strategy = NatsConsumer.this.getEndpoint()
                                .getConfiguration()
                                .getHeaderFilterStrategy();
                        msg.getHeaders().entrySet().forEach(entry -> {
                            if (!strategy.applyFilterToExternalHeaders(entry.getKey(), entry.getValue(), exchange)) {
                                if (entry.getValue().size() == 1) {
                                    // going from camel to nats add all headers in lists, so we extract them in the opposite
                                    // way if it contains a single value
                                    exchange.getIn().setHeader(entry.getKey(), entry.getValue().get(0));
                                } else {
                                    exchange.getIn().setHeader(entry.getKey(), entry.getValue());
                                }
                            } else {
                                LOG.debug("Excluding header {} as per strategy", entry.getKey());
                            }
                        });
                    }

                    // redelivery information
                    exchange.getMessage().setPayloadForTrait(MessageTrait.REDELIVERY,
                            evalRedeliveryMessageTrait(msg, exchange));

                    if (manualAckEnabled) {
                        exchange.getIn().setHeader(NatsConstants.NATS_MANUAL_ACK,
                                new DefaultNatsManualAck(msg, () -> acknowledged(msg)));
                    }

                    NatsConsumer.this.processor.process(exchange);

                    // is there a reply? only send reply if exchange pattern supports output (InOut)
                    // NATS has no error reply, so a failed exchange is not answered (the requester times out)
                    // instead of answering with the body of the failed exchange as if it succeeded
                    if (!NatsConsumingTask.this.configuration.isReplyToDisabled()
                            && msg.getReplyTo() != null && msg.getConnection() != null
                            && exchange.getPattern().isOutCapable()
                            && !exchange.isFailed() && !exchange.isRollbackOnly()) {
                        final Connection con = msg.getConnection();
                        final byte[] data = exchange.getMessage().getBody(byte[].class);
                        if (data != null) {
                            LOG.debug("Publishing replyTo: {} message", msg.getReplyTo());
                            con.publish(msg.getReplyTo(), data);
                        }
                    }
                } catch (final Exception e) {
                    NatsConsumer.this.getExceptionHandler().handleException("Error during processing", exchange, e);
                } finally {
                    NatsConsumer.this.releaseExchange(exchange, false);
                }
            }
        }
    }

    /**
     * @return the stream, or null when the server does not have it
     */
    private static StreamInfo lookupStream(JetStreamManagement jsm, String streamName)
            throws IOException, JetStreamApiException {
        try {
            return jsm.getStreamInfo(streamName);
        } catch (JetStreamApiException jsae) {
            if (jsae.getErrorCode() == 404) {
                return null;
            }
            throw jsae;
        }
    }

    private static RedeliveryTraitPayload evalRedeliveryMessageTrait(Message msg, Exchange exchange) {
        long counter = 1;
        if (msg.isJetStream()) {
            counter = msg.metaData().deliveredCount();
        }
        exchange.getIn().setHeader(NatsConstants.NATS_DELIVERY_COUNTER, counter);

        if (counter > 1) {
            return RedeliveryTraitPayload.IS_REDELIVERY;
        }
        return RedeliveryTraitPayload.NON_REDELIVERY;
    }

}
