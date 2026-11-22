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
package org.apache.camel.component.kafka;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.OffsetCommitCallback;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A batching consumer holds the records of earlier polls until its batch is complete. When a partition is revoked while
 * its records are held, the batch must not process them and commit their offsets: the new owner of the partition reads
 * them from the committed offset, and the commit of the old owner would move back the offset that the new owner
 * committed. Uses a {@link MockConsumer} instead of a broker: like the Kafka client, it calls the rebalance listener
 * inside poll, and it accepts a commit for a partition that is not assigned (the group coordinator checks the
 * generation of the member, not which partitions it owns).
 */
class KafkaBatchingConsumerRevokeTest extends CamelTestSupport {

    private static final String TOPIC = "batching-revoke";
    private static final TopicPartition PARTITION_0 = new TopicPartition(TOPIC, 0);
    private static final TopicPartition PARTITION_1 = new TopicPartition(TOPIC, 1);

    private final SlowPollMockConsumer mockConsumer = new SlowPollMockConsumer();
    private final List<List<String>> batches = new CopyOnWriteArrayList<>();

    @BindToRegistry("mockFactory")
    private final KafkaClientFactory factory = new KafkaClientFactory() {
        @Override
        public Producer getProducer(Properties kafkaProps) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Consumer getConsumer(Properties kafkaProps) {
            return mockConsumer;
        }

        @Override
        public String getBrokers(KafkaConfiguration configuration) {
            return "localhost:9092";
        }
    };

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // a batch of 10 records, committed by Camel; the poll timeout is long enough that an incomplete batch
                // is never dispatched during the test
                from("kafka:" + TOPIC + "?brokers=localhost:9092&kafkaClientFactory=#mockFactory&groupId=test"
                     + "&batching=true&maxPollRecords=10&pollTimeoutMs=60000&autoCommitEnable=false")
                        .routeId("kafka")
                        .process(exchange -> {
                            List<String> batch = new ArrayList<>();
                            for (Object record : exchange.getMessage().getBody(List.class)) {
                                batch.add(((Exchange) record).getMessage().getBody(String.class));
                            }
                            batches.add(batch);
                        });
            }
        };
    }

    @Override
    protected void setupResources() {
        mockConsumer.updateBeginningOffsets(Map.of(PARTITION_0, 0L, PARTITION_1, 0L));
        mockConsumer.schedulePollTask(() -> mockConsumer.rebalance(List.of(PARTITION_0, PARTITION_1)));
    }

    @Test
    void testRevokedPartitionOfIncompleteBatchIsNotCommitted() {
        addRecords(PARTITION_0, "a", 0, 3);
        // the three records are polled and held for the batch of 10
        await().atMost(20, TimeUnit.SECONDS).until(() -> mockConsumer.position(PARTITION_0) == 3);

        // a rebalance in the next poll gives partition 0 to another consumer, which reads it from the start and
        // commits offset 10
        AtomicBoolean revoked = new AtomicBoolean();
        mockConsumer.schedulePollTask(() -> {
            mockConsumer.rebalance(List.of(PARTITION_1));
            mockConsumer.commitSync(Map.of(PARTITION_0, new OffsetAndMetadata(10L)));
            revoked.set(true);
        });
        await().atMost(20, TimeUnit.SECONDS).untilTrue(revoked);

        addRecords(PARTITION_1, "b", 0, 10);
        await().atMost(20, TimeUnit.SECONDS).until(() -> committedOffset(PARTITION_1) != null);

        assertEquals(10L, committedOffset(PARTITION_0), "the offset committed by the new owner must be kept");
        assertEquals(List.of(List.of("b0", "b1", "b2", "b3", "b4", "b5", "b6", "b7", "b8", "b9")), batches,
                "the batch must not contain the records of the revoked partition");
        assertEquals(10L, committedOffset(PARTITION_1));
    }

    @Test
    void testPartitionRevokedAndAssignedAgainIsNotInTheBatchTwice() {
        addRecords(PARTITION_0, "a", 0, 3);
        await().atMost(20, TimeUnit.SECONDS).until(() -> mockConsumer.position(PARTITION_0) == 3);

        // an eager rebalance revokes every partition and assigns them again: the consumer reads partition 0 again from
        // the committed offset (none, so from the beginning)
        AtomicBoolean reassigned = new AtomicBoolean();
        mockConsumer.schedulePollTask(() -> {
            mockConsumer.rebalance(List.of());
            mockConsumer.rebalance(List.of(PARTITION_0, PARTITION_1));
            // the mock forgets the records it had on a rebalance: these are the records the broker still has
            addAssignedRecords(PARTITION_0, "a", 0, 3);
            reassigned.set(true);
        });
        await().atMost(20, TimeUnit.SECONDS).untilTrue(reassigned);

        addRecords(PARTITION_0, "a", 3, 7);
        await().atMost(20, TimeUnit.SECONDS).until(() -> !batches.isEmpty());

        assertEquals(List.of("a0", "a1", "a2", "a3", "a4", "a5", "a6", "a7", "a8", "a9"), batches.get(0),
                "the batch must hold each record once");
        assertEquals(10L, committedOffset(PARTITION_0));
    }

    @Test
    void testRebalanceThatRevokesNothingKeepsTheIncompleteBatch() {
        addRecords(PARTITION_0, "a", 0, 3);
        await().atMost(20, TimeUnit.SECONDS).until(() -> mockConsumer.position(PARTITION_0) == 3);

        // partition 1 is revoked, partition 0 stays assigned
        AtomicBoolean rebalanced = new AtomicBoolean();
        mockConsumer.schedulePollTask(() -> {
            mockConsumer.rebalance(List.of(PARTITION_0));
            rebalanced.set(true);
        });
        await().atMost(20, TimeUnit.SECONDS).untilTrue(rebalanced);

        addRecords(PARTITION_0, "a", 3, 7);
        await().atMost(20, TimeUnit.SECONDS).until(() -> !batches.isEmpty());

        assertEquals(List.of("a0", "a1", "a2", "a3", "a4", "a5", "a6", "a7", "a8", "a9"), batches.get(0));
        assertEquals(10L, committedOffset(PARTITION_0));
    }

    private void addRecords(TopicPartition partition, String prefix, long firstOffset, int count) {
        await().atMost(20, TimeUnit.SECONDS).until(() -> mockConsumer.assignment().contains(partition));
        addAssignedRecords(partition, prefix, firstOffset, count);
    }

    private void addAssignedRecords(TopicPartition partition, String prefix, long firstOffset, int count) {
        for (int i = 0; i < count; i++) {
            long offset = firstOffset + i;
            mockConsumer.addRecord(new ConsumerRecord<>(TOPIC, partition.partition(), offset, null, prefix + offset));
        }
    }

    private Long committedOffset(TopicPartition partition) {
        return mockConsumer.groupOffsets.get(partition);
    }

    private static final class SlowPollMockConsumer extends MockConsumer<Object, Object> {
        // the offsets committed for the group: MockConsumer.committed returns them only for the assigned partitions
        final Map<TopicPartition, Long> groupOffsets = new ConcurrentHashMap<>();

        SlowPollMockConsumer() {
            super("earliest");
        }

        @Override
        public synchronized void commitAsync(Map<TopicPartition, OffsetAndMetadata> offsets, OffsetCommitCallback callback) {
            offsets.forEach((partition, offset) -> groupOffsets.put(partition, offset.offset()));
            super.commitAsync(offsets, callback);
        }

        @Override
        public synchronized ConsumerRecords<Object, Object> poll(Duration timeout) {
            ConsumerRecords<Object, Object> records = super.poll(timeout);
            if (records.isEmpty()) {
                // a real consumer waits up to the poll timeout when there is nothing to fetch
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
            }
            return records;
        }

        @Override
        public synchronized void close() {
            // keep the mock usable: a stopped route closes the consumer
        }
    }
}
