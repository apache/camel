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
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.camel.Exchange;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;

/**
 * Immutable snapshot of the source consumer group metadata and offsets for one Kafka transaction.
 */
public final class KafkaExactlyOnceMetadata {

    /** Exchange property used to retain all offsets when a batch body is transformed downstream. */
    public static final String EXCHANGE_PROPERTY = KafkaExactlyOnceMetadata.class.getName();

    private final Map<TopicPartition, OffsetAndMetadata> offsetsToCommit;
    private final ConsumerGroupMetadata consumerGroupMetadata;

    private KafkaExactlyOnceMetadata(
                                     Map<TopicPartition, OffsetAndMetadata> offsetsToCommit,
                                     ConsumerGroupMetadata consumerGroupMetadata) {
        this.offsetsToCommit = Map.copyOf(offsetsToCommit);
        this.consumerGroupMetadata = consumerGroupMetadata;
    }

    /** Build metadata from the individual exchanges in a Kafka batch. */
    public static KafkaExactlyOnceMetadata fromExchanges(List<Exchange> exchanges) {
        if (exchanges == null || exchanges.isEmpty()) {
            throw new IllegalStateException("exactlyOnce requires a non-empty Kafka source batch");
        }
        return fromManualCommits(exchanges.stream()
                .map(exchange -> exchange.getMessage().getHeader(KafkaConstants.MANUAL_COMMIT, KafkaManualCommit.class))
                .toList());
    }

    /** Build metadata from one or more manual-commit tokens created by the same Kafka consumer group generation. */
    public static KafkaExactlyOnceMetadata fromManualCommits(Collection<? extends KafkaManualCommit> manualCommits) {
        if (manualCommits == null || manualCommits.isEmpty()) {
            throw new IllegalStateException("exactlyOnce requires a Kafka manual-commit token");
        }

        Map<TopicPartition, OffsetAndMetadata> offsets = new HashMap<>();
        ConsumerGroupMetadata groupMetadata = null;
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

            TopicPartition partition = defaultCommit.getPartition();
            OffsetAndMetadata nextOffset = new OffsetAndMetadata(
                    defaultCommit.getRecordOffset() + 1, defaultCommit.getLeaderEpoch(), "");
            offsets.merge(partition, nextOffset,
                    (previous, next) -> previous.offset() >= next.offset() ? previous : next);
        }

        return new KafkaExactlyOnceMetadata(offsets, groupMetadata);
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
}
