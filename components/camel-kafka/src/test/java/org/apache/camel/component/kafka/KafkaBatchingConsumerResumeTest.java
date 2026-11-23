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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
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
import org.apache.kafka.clients.consumer.RetriableCommitFailedException;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A resume of a paused batching consumer seeks each partition to its committed offset, from where the consumer reads
 * the records again. The records of the partition that the batch still holds must then not stay in the batch: they
 * would be processed twice, and the commit of the batch that holds them could move the committed offset back. Nor must
 * the offset recorded before the resume (its commit failed): committing it before the records read again commit their
 * own offsets would move the committed offset back too. Uses a {@link MockConsumer} instead of a broker.
 */
class KafkaBatchingConsumerResumeTest extends CamelTestSupport {

    private static final String TOPIC = "batching-resume";
    private static final TopicPartition PARTITION = new TopicPartition(TOPIC, 0);

    private final FailingCommitMockConsumer mockConsumer = new FailingCommitMockConsumer();
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
                // a batch of 2 records, committed by Camel; the poll timeout is long enough that an incomplete batch
                // is never dispatched during the test
                from("kafka:" + TOPIC + "?brokers=localhost:9092&kafkaClientFactory=#mockFactory&groupId=test"
                     + "&batching=true&maxPollRecords=2&pollTimeoutMs=60000&autoCommitEnable=false")
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
        mockConsumer.updateBeginningOffsets(Map.of(PARTITION, 0L));
        mockConsumer.schedulePollTask(() -> mockConsumer.rebalance(List.of(PARTITION)));
    }

    @Test
    void testResumeDoesNotKeepHeldRecordsTwice() throws Exception {
        addRecords(0, 2);
        await().atMost(20, TimeUnit.SECONDS).until(() -> Long.valueOf(2).equals(mockConsumer.lastCommitted()));

        // record 2 is polled and held for the next batch
        addRecords(2, 1);
        await().atMost(20, TimeUnit.SECONDS).until(() -> mockConsumer.position(PARTITION) == 3);

        // the resume seeks the partition to the committed offset 2: the consumer reads record 2 again
        pauseAndResume();
        addRecords(2, 2);
        await().atMost(20, TimeUnit.SECONDS).until(() -> batches.size() == 2);

        assertEquals(List.of(List.of("a0", "a1"), List.of("a2", "a3")), batches, "each record must be processed once");
        assertEquals(4L, mockConsumer.lastCommitted());
    }

    @Test
    void testResumeAfterFailedCommitDoesNotMoveCommittedOffsetBack() throws Exception {
        addRecords(0, 2);
        await().atMost(20, TimeUnit.SECONDS).until(() -> Long.valueOf(2).equals(mockConsumer.lastCommitted()));

        // the commits of the next two batches fail: the committed offset stays at 2
        mockConsumer.failCommits = true;
        addRecords(2, 4);
        await().atMost(20, TimeUnit.SECONDS).until(() -> batches.size() == 3);
        // record 6 is polled and held for the next batch
        addRecords(6, 1);
        await().atMost(20, TimeUnit.SECONDS).until(() -> mockConsumer.position(PARTITION) == 7);

        // the commits work again; the resume seeks the partition to the committed offset 2, and the consumer reads the
        // records from 2 again
        pauseAndResume();
        mockConsumer.failCommits = false;
        // the first poll returns record 2 alone, fewer than a batch: it is held, and the commit after that poll must
        // not commit the offset recorded before the resume
        addRecords(2, 1);
        await().atMost(20, TimeUnit.SECONDS).until(() -> mockConsumer.position(PARTITION) == 3);
        addRecords(3, 5);
        await().atMost(20, TimeUnit.SECONDS).until(() -> Long.valueOf(8).equals(mockConsumer.lastCommitted()));

        assertEquals(List.of(2L, 4L, 6L, 8L), mockConsumer.commits, "the committed offset must not move back");
        assertEquals(List.of(List.of("a2", "a3"), List.of("a4", "a5"), List.of("a6", "a7")),
                batches.subList(3, batches.size()), "the records read again must be processed in order, once");
    }

    private void pauseAndResume() throws Exception {
        context.getRouteController().suspendRoute("kafka");
        await().atMost(20, TimeUnit.SECONDS).until(() -> mockConsumer.paused().contains(PARTITION));
        context.getRouteController().resumeRoute("kafka");
        await().atMost(20, TimeUnit.SECONDS).until(() -> mockConsumer.paused().isEmpty());
        // the records are added once the consumer has seeked, as the mock returns only records at or after its position
        // and forgets the records it returned
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> mockConsumer.position(PARTITION) == mockConsumer.lastCommitted());
    }

    private void addRecords(long firstOffset, int count) {
        await().atMost(20, TimeUnit.SECONDS).until(() -> mockConsumer.assignment().contains(PARTITION));
        for (int i = 0; i < count; i++) {
            long offset = firstOffset + i;
            mockConsumer.addRecord(new ConsumerRecord<>(TOPIC, PARTITION.partition(), offset, null, "a" + offset));
        }
    }

    private static final class FailingCommitMockConsumer extends MockConsumer<Object, Object> {
        // the offsets committed for the partition, in order
        final List<Long> commits = new CopyOnWriteArrayList<>();
        volatile boolean failCommits;

        FailingCommitMockConsumer() {
            super("earliest");
        }

        Long lastCommitted() {
            return commits.isEmpty() ? null : commits.get(commits.size() - 1);
        }

        @Override
        public synchronized void commitAsync(Map<TopicPartition, OffsetAndMetadata> offsets, OffsetCommitCallback callback) {
            if (failCommits) {
                // the Kafka client calls the callback of a failed commit in a later poll, with the exception; the
                // AsyncCommitManager keeps the offset in its cache either way
                callback.onComplete(offsets, new RetriableCommitFailedException("commit failed in the test"));
                return;
            }
            OffsetAndMetadata offset = offsets.get(PARTITION);
            // the commit after each poll commits the same offset again: record each committed value once
            if (offset != null && !Long.valueOf(offset.offset()).equals(lastCommitted())) {
                commits.add(offset.offset());
            }
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
