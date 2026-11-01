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
package org.apache.camel.component.kafka.consumer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntSupplier;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;

/**
 * Snapshot of source consumer group metadata and offsets for one Kafka transaction, with atomic producer-claim
 * tracking.
 */
public final class KafkaExactlyOnceMetadata {

    /** Exchange property that preserves source offsets even when the message and its headers are replaced. */
    public static final String EXCHANGE_PROPERTY = KafkaConstants.EXACTLY_ONCE_METADATA_PROPERTY;
    /** Set by the transaction synchronization when a source transaction was aborted. */
    public static final String TRANSACTION_ABORTED_PROPERTY = KafkaConstants.EXACTLY_ONCE_TRANSACTION_ABORTED_PROPERTY;
    /** Sticky marker for a Kafka send failure, even when a Camel error handler later handles the exception. */
    public static final String SEND_FAILURE_PROPERTY = KafkaConstants.EXACTLY_ONCE_SEND_FAILURE_PROPERTY;
    /** A later Kafka send failed while an earlier send failure was being handled (for example, a Kafka DLQ send). */
    public static final String SECONDARY_SEND_FAILURE_PROPERTY
            = KafkaConstants.EXACTLY_ONCE_SECONDARY_SEND_FAILURE_PROPERTY;
    /** Suppress the marker for the synthetic exception used to reject an in-transaction redelivery. */
    public static final String SUPPRESS_NEXT_SEND_FAILURE_PROPERTY
            = KafkaConstants.EXACTLY_ONCE_SUPPRESS_NEXT_SEND_FAILURE_PROPERTY;
    /** Marks the rollback-only flag set when an in-transaction Kafka redelivery is rejected. */
    public static final String RETRY_ROLLBACK_PROPERTY
            = KafkaConstants.EXACTLY_ONCE_RETRY_ROLLBACK_PROPERTY;
    /** Identifies a copied exchange created by an exactly-once source WireTap. */
    public static final String WIRE_TAP_COPY_PROPERTY = "CamelKafkaExactlyOnceWireTapCopy";

    /** Preserve Kafka send failures on shared metadata so failures on an exchange copy reach the source UoW. */
    public static void recordSendFailure(Exchange exchange, Throwable failure) {
        if (exchange == null || failure == null || failure instanceof ClaimRejectedException) {
            return;
        }
        KafkaExactlyOnceMetadata metadata = exchange.getProperty(EXCHANGE_PROPERTY, KafkaExactlyOnceMetadata.class);
        if (metadata == null) {
            return;
        }
        if (Boolean.TRUE.equals(exchange.getProperty(SUPPRESS_NEXT_SEND_FAILURE_PROPERTY))) {
            exchange.removeProperty(SUPPRESS_NEXT_SEND_FAILURE_PROPERTY);
            return;
        }

        metadata.recordSendFailure(failure);
        Throwable firstFailure = metadata.getSendFailure();
        if (exchange.getProperty(SEND_FAILURE_PROPERTY, Throwable.class) != firstFailure) {
            exchange.setProperty(SEND_FAILURE_PROPERTY, firstFailure);
        }
        Throwable secondaryFailure = metadata.getSecondarySendFailure();
        if (secondaryFailure != null
                && exchange.getProperty(SECONDARY_SEND_FAILURE_PROPERTY, Throwable.class) != secondaryFailure) {
            exchange.setProperty(SECONDARY_SEND_FAILURE_PROPERTY, secondaryFailure);
        }
        // A Kafka send attempted while an error handler is delivering the failed exchange is a DLQ failure.
        // Keep this marker even if deadLetterHandleNewException later clears the exchange exception.
        if (exchange.getProperty(ExchangePropertyKey.EXCEPTION_CAUGHT, Throwable.class) != null) {
            metadata.markDeadLetterSendFailed();
        }
    }

    /** A rejected source-offset claim is not a Kafka send failure and must not poison the owning exchange. */
    public static final class ClaimRejectedException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        public ClaimRejectedException(String message) {
            super(message);
        }
    }

    private final Map<TopicPartition, OffsetAndMetadata> offsetsToCommit;
    private final ConsumerGroupMetadata consumerGroupMetadata;
    private final Consumer<?, ?> consumer;
    private final String sourceExchangeId;
    private final AtomicBoolean claimed = new AtomicBoolean();
    private final AtomicReference<String> claimRejection = new AtomicReference<>();
    private final AtomicReference<Throwable> sendFailure = new AtomicReference<>();
    private final AtomicReference<Throwable> secondarySendFailure = new AtomicReference<>();
    private final AtomicBoolean deadLetterSendFailed = new AtomicBoolean();
    private final AtomicBoolean asynchronousKafkaHandoff = new AtomicBoolean();
    private final Object asynchronousWireTapMonitor = new Object();
    private final List<IntSupplier> asynchronousWireTapPendingCounts = new ArrayList<>();

    private void recordSendFailure(Throwable failure) {
        Throwable firstFailure = sendFailure.get();
        if (firstFailure == null) {
            if (sendFailure.compareAndSet(null, failure)) {
                return;
            }
            firstFailure = sendFailure.get();
        }
        if (firstFailure != failure) {
            secondarySendFailure.compareAndSet(null, failure);
        }
    }

    /** The first send failure observed by any exchange carrying this source metadata. */
    public Throwable getSendFailure() {
        return sendFailure.get();
    }

    /** A later, distinct send failure (for example, a Kafka DLQ failure) observed by an exchange copy. */
    public Throwable getSecondarySendFailure() {
        return secondarySendFailure.get();
    }

    /** Mark a Kafka send made by a failure handler as failed; it must never be treated as a successful DLQ delivery. */
    public void markDeadLetterSendFailed() {
        deadLetterSendFailed.set(true);
    }

    /** Whether a Kafka dead-letter send failed, even if its error handler later handled the exception. */
    public boolean isDeadLetterSendFailed() {
        return deadLetterSendFailed.get();
    }

    /** Mark that an executed WireTap copy actually invoked a Kafka producer. */
    public void markAsynchronousKafkaHandoff() {
        asynchronousKafkaHandoff.set(true);
    }

    /** Whether an executed WireTap copy actually invoked a Kafka producer. */
    public boolean hasAsynchronousKafkaHandoff() {
        return asynchronousKafkaHandoff.get();
    }

    /** Register the pending-task counter of a potentially Kafka-producing WireTap processor. */
    public void registerAsynchronousWireTap(IntSupplier pendingCount) {
        synchronized (asynchronousWireTapMonitor) {
            asynchronousWireTapPendingCounts.add(pendingCount);
        }
    }

    /**
     * Wait until potentially Kafka-producing WireTap copies have completed, so their selected route branches are known
     * before the source consumer commits offsets directly. WireTap increments its task counter before submitting the
     * asynchronous task and decrements it from the task completion callback.
     */
    public boolean awaitAsynchronousWireTaps(long timeoutMillis) throws InterruptedException {
        long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(0L, timeoutMillis));
        long deadline = System.nanoTime() + timeoutNanos;
        while (true) {
            List<IntSupplier> pendingCounters;
            synchronized (asynchronousWireTapMonitor) {
                pendingCounters = List.copyOf(asynchronousWireTapPendingCounts);
            }
            try {
                boolean pending = pendingCounters.stream().anyMatch(counter -> counter.getAsInt() > 0);
                if (!pending) {
                    return true;
                }
            } catch (RuntimeException e) {
                // If completion state cannot be read safely, do not commit the source offset.
                return false;
            }
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0L) {
                return false;
            }
            TimeUnit.NANOSECONDS.sleep(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(10L)));
        }
    }

    /** Clear failures that occurred before a new transaction synchronization took ownership of the offsets. */
    public void clearSendFailures() {
        sendFailure.set(null);
        secondarySendFailure.set(null);
        deadLetterSendFailed.set(false);
    }

    private KafkaExactlyOnceMetadata(
                                     Map<TopicPartition, OffsetAndMetadata> offsetsToCommit,
                                     ConsumerGroupMetadata consumerGroupMetadata,
                                     Consumer<?, ?> consumer,
                                     String sourceExchangeId) {
        this.offsetsToCommit = Map.copyOf(offsetsToCommit);
        this.consumerGroupMetadata = consumerGroupMetadata;
        this.consumer = consumer;
        this.sourceExchangeId = sourceExchangeId;
    }

    /** Build metadata from one or more manual-commit tokens created by the same Kafka consumer group generation. */
    public static KafkaExactlyOnceMetadata fromManualCommits(Collection<? extends KafkaManualCommit> manualCommits) {
        return fromManualCommits(manualCommits, null);
    }

    /** Build metadata and bind it to the original source exchange. */
    public static KafkaExactlyOnceMetadata fromManualCommits(
            Collection<? extends KafkaManualCommit> manualCommits, String sourceExchangeId) {
        if (manualCommits == null || manualCommits.isEmpty()) {
            throw new IllegalStateException("exactlyOnce requires a Kafka manual-commit token");
        }

        Map<TopicPartition, OffsetAndMetadata> offsets = new HashMap<>();
        ConsumerGroupMetadata groupMetadata = null;
        Consumer<?, ?> consumer = null;
        boolean consumerSeen = false;
        for (KafkaManualCommit manualCommit : manualCommits) {
            if (!(manualCommit instanceof DefaultKafkaManualCommit defaultCommit)) {
                String type = manualCommit == null ? "null" : manualCommit.getClass().getName();
                throw new IllegalStateException(
                        "exactlyOnce requires the default KafkaManualCommit implementation; found " + type);
            }

            ConsumerGroupMetadata current = defaultCommit.getConsumerGroupMetadata();
            if (current == null || current.groupId() == null || current.groupId().isBlank()) {
                throw new IllegalStateException("exactlyOnce requires a Kafka consumer configured with groupId");
            }
            if (groupMetadata == null) {
                groupMetadata = current;
            } else if (!sameGroupGeneration(groupMetadata, current)) {
                throw new IllegalStateException(
                        "exactlyOnce cannot combine records from different Kafka consumer group generations");
            }

            var payload = defaultCommit.getCamelExchangePayload();
            if (payload != null) {
                if (!consumerSeen) {
                    consumer = payload.consumer;
                    consumerSeen = true;
                } else if (consumer != payload.consumer) {
                    throw new IllegalStateException("exactlyOnce metadata must come from one Kafka consumer instance");
                }
            }

            TopicPartition partition = defaultCommit.getPartition();
            OffsetAndMetadata nextOffset = new OffsetAndMetadata(
                    defaultCommit.getRecordOffset() + 1, defaultCommit.getLeaderEpoch(), "");
            offsets.merge(partition, nextOffset,
                    (previous, next) -> previous.offset() >= next.offset() ? previous : next);
        }

        return new KafkaExactlyOnceMetadata(offsets, groupMetadata, consumer, sourceExchangeId);
    }

    private static boolean sameGroupGeneration(ConsumerGroupMetadata left, ConsumerGroupMetadata right) {
        return Objects.equals(left.groupId(), right.groupId())
                && left.generationId() == right.generationId()
                && Objects.equals(left.memberId(), right.memberId())
                && Objects.equals(left.groupInstanceId(), right.groupInstanceId());
    }

    public Map<TopicPartition, OffsetAndMetadata> getOffsetsToCommit() {
        return offsetsToCommit;
    }

    public ConsumerGroupMetadata getConsumerGroupMetadata() {
        return consumerGroupMetadata;
    }

    /** Claim these source offsets for exactly one transaction on the original source exchange. */
    public synchronized void claim(String exchangeId) {
        if (sourceExchangeId != null && !sourceExchangeId.equals(exchangeId)) {
            String reason = "exactlyOnce source metadata cannot be used by a split, wireTap or copied exchange";
            rejectCopyClaim(reason);
            throw new ClaimRejectedException(reason);
        }
        if (!claimed.compareAndSet(false, true)) {
            String reason = "exactlyOnce source offsets have already been claimed by another producer";
            rejectClaim(reason);
            throw new ClaimRejectedException(reason);
        }

        // A copied exchange may reach its producer before the original route reaches its synchronous producer.
        // The source exchange owns the offsets, so its successful claim supersedes that rejected copy attempt.
        claimRejection.set(null);
    }

    /** Mark a rejected copy only while the original exchange has not claimed the source offsets. */
    public synchronized void rejectCopyClaim(String reason) {
        if (!claimed.get()) {
            claimRejection.compareAndSet(null, reason);
        }
    }

    /** Mark a rejected copy or competing producer claim on the metadata shared with the source exchange. */
    public void rejectClaim(String reason) {
        claimRejection.compareAndSet(null, reason);
    }

    /** Whether another exchange or producer attempted to claim these source offsets. */
    public boolean isClaimRejected() {
        return claimRejection.get() != null;
    }

    /** Describes why a copied or competing exchange was not allowed to claim the source offsets. */
    public String getClaimRejectionReason() {
        return claimRejection.get();
    }

    /** Release a claim only when transaction initialization failed before its synchronization was registered. */
    public void releaseClaim() {
        claimed.set(false);
    }

    public boolean isClaimed() {
        return claimed.get();
    }

    /**
     * Commit the source offsets directly on the polling consumer when a successful route did not use an exactly-once
     * Kafka producer (for example, when a content-based filter drops the record). This method must be called by the
     * consumer polling thread.
     */
    public void commitOffsets() {
        if (consumer == null) {
            throw new IllegalStateException("exactlyOnce source consumer is not available for an offset-only commit");
        }
        consumer.commitSync(offsetsToCommit);
    }
}
