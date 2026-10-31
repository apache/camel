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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.Exchange;
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

    /** Preserve the first send failure only for an exchange carrying exactly-once source metadata. */
    public static void recordSendFailure(Exchange exchange, Throwable failure) {
        if (exchange != null && failure != null
                && exchange.getProperty(EXCHANGE_PROPERTY, KafkaExactlyOnceMetadata.class) != null
                && exchange.getProperty(SEND_FAILURE_PROPERTY) == null) {
            exchange.setProperty(SEND_FAILURE_PROPERTY, failure);
        }
    }

    private final Map<TopicPartition, OffsetAndMetadata> offsetsToCommit;
    private final ConsumerGroupMetadata consumerGroupMetadata;
    private final Consumer<?, ?> consumer;
    private final String sourceExchangeId;
    private final AtomicBoolean claimed = new AtomicBoolean();

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
    public void claim(String exchangeId) {
        if (sourceExchangeId != null && !sourceExchangeId.equals(exchangeId)) {
            throw new IllegalStateException(
                    "exactlyOnce source metadata cannot be used by a split, wireTap or copied exchange");
        }
        if (!claimed.compareAndSet(false, true)) {
            throw new IllegalStateException("exactlyOnce source offsets have already been claimed by another producer");
        }
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
