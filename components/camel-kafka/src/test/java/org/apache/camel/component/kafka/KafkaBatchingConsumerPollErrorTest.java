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
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.RecordDeserializationException;
import org.apache.kafka.common.errors.RecordDeserializationException.DeserializationExceptionOrigin;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A batching consumer holds the records of earlier polls until its batch is complete. A failed poll (handled by the
 * default pollOnError=ERROR_HANDLER) must not drop them: the consumer does not return them again, and the next batch
 * commits past them. Uses a {@link MockConsumer} instead of a broker.
 */
class KafkaBatchingConsumerPollErrorTest extends CamelTestSupport {

    private static final String TOPIC = "batching-poll-error";
    private static final TopicPartition PARTITION = new TopicPartition(TOPIC, 0);

    private final PollCountingMockConsumer mockConsumer = new PollCountingMockConsumer();
    private final List<String> routed = new CopyOnWriteArrayList<>();
    private long offset;

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
                // a batch of 10 records; the poll timeout is long enough that an incomplete batch is never dispatched
                // during the test
                from("kafka:" + TOPIC + "?brokers=localhost:9092&kafkaClientFactory=#mockFactory&groupId=test"
                     + "&batching=true&maxPollRecords=10&pollTimeoutMs=60000")
                        .routeId("kafka")
                        .process(exchange -> {
                            for (Object record : exchange.getMessage().getBody(List.class)) {
                                routed.add(((Exchange) record).getMessage().getBody(String.class));
                            }
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
    void testFailedPollKeepsTheRecordsOfTheIncompleteBatch() {
        addRecords("a", 3);
        // the three records are polled and held for the batch of 10
        await().atMost(20, TimeUnit.SECONDS).until(() -> mockConsumer.position(PARTITION) == 3);

        mockConsumer.setPollException(new KafkaException("Simulated poll failure"));
        await().atMost(20, TimeUnit.SECONDS).until(() -> mockConsumer.pollErrors.get() == 1);

        addRecords("b", 10);
        await().atMost(20, TimeUnit.SECONDS).until(() -> routed.contains("b6"));

        assertEquals(List.of("a0", "a1", "a2", "b0", "b1", "b2", "b3", "b4", "b5", "b6"), routed);
        assertEquals(10L, committedOffset());
    }

    @Test
    void testPoisonRecordKeepsTheRecordsOfTheIncompleteBatch() {
        addRecords("a", 3);
        await().atMost(20, TimeUnit.SECONDS).until(() -> mockConsumer.position(PARTITION) == 3);

        // the record at offset 3 cannot be deserialized (the poll fails on it): the error handler seeks past it
        long poison = offset++;
        mockConsumer.setPollException(
                new RecordDeserializationException(
                        DeserializationExceptionOrigin.VALUE, PARTITION, poison, 0L, TimestampType.CREATE_TIME, null, null,
                        new RecordHeaders(), "Simulated deserialization failure", null));
        await().atMost(20, TimeUnit.SECONDS).until(() -> mockConsumer.pollErrors.get() == 1);

        addRecords("b", 10);
        await().atMost(20, TimeUnit.SECONDS).until(() -> routed.contains("b6"));

        assertEquals(List.of("a0", "a1", "a2", "b0", "b1", "b2", "b3", "b4", "b5", "b6"), routed);
        assertEquals(11L, committedOffset());
    }

    private void addRecords(String prefix, int count) {
        await().atMost(20, TimeUnit.SECONDS).until(() -> mockConsumer.assignment().contains(PARTITION));
        List<ConsumerRecord<Object, Object>> records = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            records.add(new ConsumerRecord<>(TOPIC, 0, offset++, null, prefix + i));
        }
        records.forEach(mockConsumer::addRecord);
    }

    private long committedOffset() {
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> mockConsumer.committed(Set.of(PARTITION)).get(PARTITION) != null);
        OffsetAndMetadata committed = mockConsumer.committed(Set.of(PARTITION)).get(PARTITION);
        return committed.offset();
    }

    private static final class PollCountingMockConsumer extends MockConsumer<Object, Object> {
        final AtomicInteger pollErrors = new AtomicInteger();

        PollCountingMockConsumer() {
            super("earliest");
        }

        @Override
        public synchronized ConsumerRecords<Object, Object> poll(Duration timeout) {
            ConsumerRecords<Object, Object> records;
            try {
                records = super.poll(timeout);
            } catch (KafkaException e) {
                pollErrors.incrementAndGet();
                throw e;
            }
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
