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

import java.time.ZonedDateTime;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import io.nats.client.Connection;
import io.nats.client.ConnectionListener;
import io.nats.client.Dispatcher;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import io.nats.client.PushSubscribeOptions;
import io.nats.client.api.AckPolicy;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.DeliverPolicy;
import io.nats.client.api.RetentionPolicy;
import io.nats.client.api.StreamInfo;
import io.nats.client.impl.AckType;
import org.apache.camel.spi.ExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps a JetStream push subscription consuming when the server deleted its consumer.
 * <p>
 * The server deletes an ephemeral push consumer (no durable name) when nobody is subscribed to its deliver subject for
 * its inactive threshold (5 seconds by default), for example while the client was disconnected for a few seconds, and a
 * server restart loses it. jnats subscribes the deliver subject again after the reconnect, but it does not create the
 * consumer again, so the subscription would never receive anything again. After every reconnect this asks the server
 * for the consumer and, when an ephemeral consumer is gone, subscribes again: the new consumer starts at the first
 * message that was delivered to the route and not acknowledged (or after the last one delivered), so that nothing is
 * skipped and the stream is not replayed from the deliver policy. A durable consumer is not created again: it is gone
 * only when an administrator deleted it or after its inactive threshold, and creating it again from its configuration
 * would replay the stream from its deliver policy. A WARN says so, and the subscription is kept.
 * <p>
 * jnats answers the consumer lookup with null (not an exception) when the server has no such consumer (api error
 * 10014). On a stream with WorkQueue retention, which accepts only the deliver policy All and has already removed the
 * acknowledged messages, and on a stream that was created again since the subscribe (its sequences started again at 1),
 * the new consumer uses the consumer configuration as it is.
 * <p>
 * When the consumer cannot be checked or created again (JetStream not ready yet after a server restart, the stream is
 * gone), this keeps trying while the route runs: a few times one second apart, then with a growing delay up to 30
 * seconds, with a WARN after the first attempts.
 */
final class NatsJetStreamPushConsumerKeeper implements MessageHandler, ConnectionListener {

    private static final Logger LOG = LoggerFactory.getLogger(NatsJetStreamPushConsumerKeeper.class);
    // the attempts one second apart, before the delay grows up to the maximum
    private static final int CHECK_ATTEMPTS = 5;
    private static final long CHECK_RETRY_DELAY_MILLIS = 1000;
    private static final long MAX_CHECK_RETRY_DELAY_MILLIS = 30000;

    private final NatsConsumer consumer;
    private final Connection connection;
    private final String streamName;
    private final String topic;
    private final String queueName;
    private final Dispatcher dispatcher;
    private final MessageHandler handler;
    private final ConsumerConfiguration configuration;
    private final ExecutorService executor;
    private final Consumer<JetStreamSubscription> onSubscribed;
    private final Lock lock = new ReentrantLock();
    // whether a check is submitted and has not started yet, so that reconnects do not queue several checks
    private final AtomicBoolean checkQueued = new AtomicBoolean();
    // the JetStream messages delivered to the route that may still need a delivery, by stream sequence
    private final ConcurrentSkipListMap<Long, Message> unacknowledged = new ConcurrentSkipListMap<>();
    private final AtomicLong highestDelivered = new AtomicLong();
    // with AckPolicy.None the delivery is the acknowledgement: no delivered message waits for an ack
    private final boolean trackUnacknowledged;
    // the server delivers a message at most maxDeliver times (when set): a message delivered that often is settled
    private final long maxDeliver;
    private volatile JetStreamSubscription subscription;
    // when the stream was created, as seen at the subscribe: a different time later means that the stream was created
    // again and its sequences started again at 1
    private volatile ZonedDateTime streamCreated;
    // the last sequence of the stream at the subscribe (-1 if not known), where a consumer that starts at the new (or
    // last) messages started
    private volatile long streamLastSequence = -1;
    // why the consumer counts as gone, for the log
    private volatile String goneReason;

    NatsJetStreamPushConsumerKeeper(NatsConsumer consumer, Connection connection, String streamName, String topic,
                                    String queueName, Dispatcher dispatcher, MessageHandler handler,
                                    ConsumerConfiguration configuration, ExecutorService executor,
                                    Consumer<JetStreamSubscription> onSubscribed) {
        this.consumer = consumer;
        this.connection = connection;
        this.streamName = streamName;
        this.topic = topic;
        this.queueName = queueName;
        this.dispatcher = dispatcher;
        this.handler = handler;
        this.configuration = configuration;
        this.executor = executor;
        this.onSubscribed = onSubscribed;
        this.trackUnacknowledged = configuration.getAckPolicy() != AckPolicy.None;
        this.maxDeliver = configuration.getMaxDeliver();
    }

    /**
     * Starts watching the connection for reconnects, once the first subscription is made.
     *
     * @param streamCreated      when the stream was created, as seen before the subscribe (null if not known)
     * @param streamLastSequence the last sequence of the stream, as seen before the subscribe (-1 if not known)
     */
    void start(JetStreamSubscription subscription, ZonedDateTime streamCreated, long streamLastSequence) {
        this.subscription = subscription;
        this.streamCreated = streamCreated;
        this.streamLastSequence = streamLastSequence;
        connection.addConnectionListener(this);
    }

    void stop() {
        connection.removeConnectionListener(this);
    }

    @Override
    public void onMessage(Message msg) throws InterruptedException {
        if (msg.isJetStream()) {
            long seq = msg.metaData().streamSequence();
            if (trackUnacknowledged) {
                unacknowledged.values().removeIf(this::settled);
                unacknowledged.put(seq, msg);
            }
            highestDelivered.accumulateAndGet(seq, Math::max);
        }
        handler.onMessage(msg);
    }

    @Override
    public void connectionEvent(Connection conn, Events type) {
        // jnats has subscribed the deliver subject again: is the consumer still there?
        if (type == Events.RESUBSCRIBED && consumer.isRunAllowed() && checkQueued.compareAndSet(false, true)) {
            try {
                executor.submit(this::checkConsumer);
            } catch (Exception e) {
                checkQueued.set(false);
                LOG.debug("Cannot check the JetStream consumer of the push subscription on topic {}", topic, e);
            }
        }
    }

    void checkConsumer() {
        lock.lock();
        try {
            // a reconnect from now on queues the next check
            checkQueued.set(false);
            long delay = CHECK_RETRY_DELAY_MILLIS;
            boolean reported = false;
            for (int attempt = 1; consumer.isRunAllowed(); attempt++) {
                Boolean gone = consumerGone();
                if (Boolean.FALSE.equals(gone)) {
                    if (attempt > CHECK_ATTEMPTS) {
                        LOG.info("The JetStream consumer of the push subscription on topic {} is there again", topic);
                    }
                    return;
                }
                if (Boolean.TRUE.equals(gone)) {
                    if (consumerDeleted(!reported)) {
                        return;
                    }
                    // the failure went to the exception handler once, the next ones are logged at DEBUG
                    reported = true;
                }
                // could not check (not connected, JetStream not ready, timeout), or could not subscribe again: try
                // again while the route runs, as nothing else makes this subscription receive again
                if (attempt == CHECK_ATTEMPTS) {
                    LOG.warn("Cannot check, or create again, the JetStream consumer of the push subscription on topic {}"
                             + " after {} attempts; the route receives nothing until it succeeds, trying again every"
                             + " {} seconds at most",
                            topic, attempt, MAX_CHECK_RETRY_DELAY_MILLIS / 1000);
                }
                if (attempt >= CHECK_ATTEMPTS) {
                    delay = Math.min(delay * 2, MAX_CHECK_RETRY_DELAY_MILLIS);
                }
                Thread.sleep(delay);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return whether the server no longer has the consumer, or null if that could not be checked
     */
    private Boolean consumerGone() {
        try {
            // jnats answers null when the server has no such consumer (api error 10014), and throws for any other
            // error, for example 404 stream not found (10059)
            if (subscription.getConsumerInfo() == null) {
                goneReason = "consumer not found";
                return true;
            }
            return false;
        } catch (JetStreamApiException e) {
            if (e.getErrorCode() == 404) {
                // jnats answers consumer not found with null, so this is another 404, such as stream not found
                goneReason = e.getMessage();
                return true;
            }
            LOG.debug("Cannot check the JetStream consumer of the push subscription on topic {}", topic, e);
        } catch (Exception e) {
            LOG.debug("Cannot check the JetStream consumer of the push subscription on topic {}", topic, e);
        }
        return null;
    }

    /**
     * The server no longer has the consumer: subscribes again for an ephemeral consumer, and only logs a WARN for a
     * durable one.
     *
     * @param  report whether a failure goes to the exception handler (else it is logged at DEBUG)
     * @return        false if subscribing again failed, to try again
     */
    private boolean consumerDeleted(boolean report) {
        if (configuration.getDurable() != null) {
            LOG.warn("The durable JetStream consumer {} of the push subscription on topic {} is gone ({}); it is not"
                     + " created again, and messages arrive again when it is created again with the same deliver"
                     + " subject",
                    configuration.getDurable(), topic, goneReason);
            return true;
        }
        if (!consumer.isRunAllowed()) {
            return true;
        }
        try {
            subscribeAgain();
            return true;
        } catch (Exception e) {
            if (!consumer.isRunAllowed()) {
                // the consumer stopped meanwhile (its stop can close the dispatcher): nothing to report
                LOG.debug("Not subscribing again as the consumer of topic {} is stopping", topic, e);
                return true;
            }
            if (report) {
                // jnats reports some subscribe failures as IllegalStateException, such as SUB-90007 when the stream
                // is gone
                exceptionHandler().handleException(
                        "Error subscribing again after the JetStream consumer of the push subscription was deleted", e);
            } else {
                LOG.debug("Error subscribing again after the JetStream consumer of the push subscription on topic {}"
                          + " was deleted",
                        topic, e);
            }
            return false;
        }
    }

    /**
     * Subscribes again after the server deleted the ephemeral consumer, starting at the first message delivered to the
     * route and not acknowledged.
     * <p>
     * A message that is not acknowledged stays in the unacknowledged map until this resubscribe, and the new consumer
     * starts at its sequence, so the route sees the messages after it again (at-least-once). A message delivered
     * maxDeliver times (when set) is settled, as the server does not deliver it again. With ackPolicy=None no message
     * is tracked: the new consumer starts after the highest one delivered. When nothing was delivered yet, a consumer
     * with the deliver policy New (or Last) starts after (or at) the last message of the stream at the subscribe, so
     * that the messages published meanwhile are not skipped.
     * <p>
     * The consumer configuration is used as it is (no start sequence) for a stream with WorkQueue retention, which
     * accepts only the deliver policy All and has already removed the acknowledged messages, and for a stream that was
     * created again since the subscribe (its sequences started again at 1).
     */
    private void subscribeAgain() throws Exception {
        StreamInfo stream = connection.jetStreamManagement().getStreamInfo(streamName);
        ZonedDateTime created = stream != null ? stream.getCreateTime() : null;
        if (created != null && streamCreated != null && !created.isEqual(streamCreated)) {
            LOG.warn("The JetStream stream {} was created again since the subscribe; consuming it from its deliver"
                     + " policy (from its first message for the deliver policy New or Last)",
                    streamName);
            unacknowledged.clear();
            highestDelivered.set(0);
            // every message of the new stream came after the subscribe
            streamLastSequence = 0;
        }
        if (created != null) {
            streamCreated = created;
        }
        boolean workQueue = stream != null && stream.getConfiguration() != null
                && stream.getConfiguration().getRetentionPolicy() == RetentionPolicy.WorkQueue;
        ConsumerConfiguration again = configuration;
        unacknowledged.values().removeIf(this::settled);
        long highest = highestDelivered.get();
        DeliverPolicy policy = configuration.getDeliverPolicy();
        long lastAtSubscribe = streamLastSequence;
        // the stream sequence to start at, 0 to use the consumer configuration as it is
        long start = 0;
        if (workQueue) {
            LOG.debug("The JetStream stream {} has WorkQueue retention: subscribing with the consumer configuration",
                    streamName);
        } else if (highest > 0) {
            start = unacknowledged.isEmpty() ? highest + 1 : unacknowledged.firstKey();
        } else if (policy == DeliverPolicy.New && lastAtSubscribe >= 0) {
            start = lastAtSubscribe + 1;
        } else if (policy == DeliverPolicy.Last && lastAtSubscribe >= 0) {
            start = Math.max(lastAtSubscribe, 1);
        }
        boolean resume = start > 0;
        if (resume) {
            // a start time (DeliverPolicy.ByStartTime) together with a start sequence is rejected by the server
            again = ConsumerConfiguration.builder(configuration)
                    .deliverPolicy(DeliverPolicy.ByStartSequence)
                    .startSequence(start)
                    .startTime(null)
                    .build();
        }
        LOG.warn("The JetStream consumer of the push subscription on topic {} is gone ({}); subscribing again{}",
                topic, goneReason, resume ? " from stream sequence " + start : "");
        PushSubscribeOptions options = PushSubscribeOptions.builder()
                .configuration(again)
                .deliverGroup(queueName)
                .build();
        JetStreamSubscription old = subscription;
        subscription = connection.jetStream().subscribe(topic, queueName, dispatcher, this, false, options);
        // the new consumer delivers those again; a message it delivered meanwhile stays tracked
        unacknowledged.values().removeIf(m -> m.getSubscription() == old);
        onSubscribed.accept(subscription);
        try {
            dispatcher.unsubscribe(old);
        } catch (Exception e) {
            LOG.debug("Error unsubscribing the push subscription of the deleted consumer", e);
        }
        if (!consumer.isRunAllowed()) {
            // the consumer was stopped while subscribing: its stop may have ended the previous subscription only
            try {
                dispatcher.unsubscribe(subscription);
            } catch (Exception e) {
                LOG.debug("Error unsubscribing the push subscription made while the consumer stopped", e);
            }
        }
    }

    private ExceptionHandler exceptionHandler() {
        return consumer.getExceptionHandler();
    }

    /**
     * Whether the server does not deliver the message again: it is acknowledged or terminated, or it was delivered
     * maxDeliver times.
     */
    private boolean settled(Message msg) {
        AckType ack = msg.lastAck();
        if (ack == AckType.AckAck || ack == AckType.AckTerm) {
            return true;
        }
        return maxDeliver > 0 && msg.metaData().deliveredCount() >= maxDeliver;
    }
}
