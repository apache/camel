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
package org.apache.camel.component.vertx.kafka.operations;

import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import io.vertx.core.Vertx;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import org.apache.camel.component.vertx.kafka.configuration.VertxKafkaConfiguration;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VertxKafkaConsumerOperationsTest {

    private static final Logger LOG = LoggerFactory.getLogger(VertxKafkaConsumerOperationsTest.class);

    private VertxKafkaConfiguration configuration;

    @BeforeEach
    void initConfig() {
        configuration = new VertxKafkaConfiguration();
        configuration.setKeySerializer(StringSerializer.class.getName());
        configuration.setValueSerializer(StringSerializer.class.getName());
        configuration.setKeyDeserializer(StringDeserializer.class.getName());
        configuration.setValueDeserializer(StringDeserializer.class.getName());

    }

    @Test
    void testConsumerSubscribeSingleRecords() {
        testConsumerSubscribeRecords(1, "testConsumerSubscribeSingleRecordsTopic", 1000);
    }

    @Test
    void testConsumerSubscribeBatchRecords() {
        testConsumerSubscribeRecords(50, "testConsumerSubscribeBatchRecordsTopic", 5000);
    }

    @Test
    void testConsumerSubscribeToSinglePartition() {
        final String topic = "testConsumerSubscribeToSinglePartitionTopic";

        configuration.setTopic(topic);
        configuration.setPartitionId(1);
        configuration.setSeekToOffset(0L);

        final MockConsumer<Object, Object> mockConsumer = createConsumer();

        produceRandomRecords(mockConsumer, 5, topic, false);

        assertRecords(mockConsumer, 5, 1000, (record, count) -> {
            assertEquals(topic, record.topic());
            assertEquals(1, record.partition());
        });
    }

    @Test
    void testConsumerAssignWithSeek() {
        final String topic = "testConsumerSubscribeWithSeekTopic";

        configuration.setTopic(topic);
        configuration.setPartitionId(1);
        configuration.setSeekToOffset(4L);

        final MockConsumer<Object, Object> mockConsumer = createConsumer();

        produceRandomRecords(mockConsumer, 5, topic, false);

        assertRecords(mockConsumer, 1, 1000, (record, count) -> {
            assertEquals(topic, record.topic());
            assertEquals(1, record.partition());
            assertEquals(4L, record.offset()); // offsets in kafka starts from 0, that means the last record offset in 5 records is 4
        });
    }

    @Test
    void testConsumerOnError() {
        final String topic = "testConsumerOnError";

        configuration.setTopic(topic);

        final MockConsumer<Object, Object> mockConsumer = createConsumer();

        mockConsumer.schedulePollTask(() -> mockConsumer.setPollException(new KafkaException("Ohh, an error!")));

        final VertxKafkaConsumerOperations operations = createOperations(mockConsumer, configuration);

        final Latch latch = new Latch();

        operations.receiveEvents(record -> {
        }, error -> {
            assertTrue(error instanceof KafkaException);
            latch.done();
        });

        latch.await(1000);
    }

    void testConsumerSubscribeRecords(final int numberOfRecords, final String topic, final long timeout) {
        configuration.setTopic(topic);
        configuration.setSeekToOffset(0L);

        final MockConsumer<Object, Object> mockConsumer = createConsumer();

        produceRandomRecords(mockConsumer, numberOfRecords, topic, true);

        assertRecords(mockConsumer, numberOfRecords, timeout, (kafkaRecord, val) -> {
            assertEquals(topic, kafkaRecord.topic());

            if (kafkaRecord.partition() == 0) {
                assertEquals("my-key-0-" + val, kafkaRecord.key());
                assertEquals("test-message-0-" + val, kafkaRecord.record().value());
            } else {
                assertEquals("my-key-1-" + val, kafkaRecord.key());
                assertEquals("test-message-1-" + val, kafkaRecord.record().value());
            }
        });
    }

    MockConsumer<Object, Object> createConsumer() {
        return new MockConsumer<>(OffsetResetStrategy.EARLIEST);
    }

    VertxKafkaConsumerOperations createOperations(
            final MockConsumer<Object, Object> mockConsumer, final VertxKafkaConfiguration configuration) {
        return new VertxKafkaConsumerOperations(KafkaConsumer.create(Vertx.vertx(), mockConsumer), configuration);
    }

    private void produceRandomRecords(
            final MockConsumer<Object, Object> mockConsumer, final int numOfRecords, final String topic,
            final boolean shouldRebalance) {
        final TopicPartition topicPartition0 = new TopicPartition(topic, 0);
        final TopicPartition topicPartition1 = new TopicPartition(topic, 1);

        schedulePollTaskOnPartition(mockConsumer, numOfRecords, topicPartition0, shouldRebalance);
        schedulePollTaskOnPartition(mockConsumer, numOfRecords, topicPartition1, shouldRebalance);
    }

    void schedulePollTaskOnPartition(
            final MockConsumer<Object, Object> mockConsumer, final int numOfRecords, final TopicPartition partition,
            final boolean shouldRebalance) {
        mockConsumer.schedulePollTask(() -> {
            // simulate a replacing for a partition
            if (shouldRebalance) {
                mockConsumer.rebalance(Collections.singletonList(partition));
            }
            // add our tests records
            for (int i = 0; i < numOfRecords; i++) {
                mockConsumer.addRecord(record(partition.topic(), partition.partition(), i, "my-key-0-" + i,
                        "test-message-0-" + i));
            }
            if (shouldRebalance) {
                // reset the consumer to 0 in order to subscribe from start of the offset
                mockConsumer.seek(partition, 0L);
            }
        });
    }

    private ConsumerRecord<Object, Object> record(String topic, int partition, long offset, Object key, Object value) {
        return new ConsumerRecord<>(topic, partition, offset, key, value);
    }

    private void assertRecords(
            final MockConsumer<Object, Object> mockConsumer, final int numberOfRecords, final long timeout,
            final BiConsumer<KafkaConsumerRecord<Object, Object>, Integer> fn) {
        final AtomicInteger count = new AtomicInteger();
        final Latch latch = new Latch();

        final VertxKafkaConsumerOperations operations = createOperations(mockConsumer, configuration);

        operations.receiveEvents(kafkaRecord -> {
            int val = count.getAndIncrement();

            fn.accept(kafkaRecord, val);

            if (val == numberOfRecords - 1) {
                latch.done();
            }
        }, error -> LOG.error(error.getLocalizedMessage(), error.getCause()));

        latch.await(timeout);
    }

    private static class Latch {
        private final AtomicBoolean doneFlag = new AtomicBoolean();

        public void done() {
            doneFlag.set(true);
        }

        public void await(long timeout) {
            Awaitility
                    .await()
                    .atMost(timeout, TimeUnit.MILLISECONDS)
                    .untilTrue(doneFlag);
        }
    }
}
