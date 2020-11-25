package org.apache.camel.component.vertx.kafka.operations;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.vertx.core.Vertx;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import org.apache.camel.component.vertx.kafka.configuration.VertxKafkaConfiguration;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VertxKafkaConsumerOperationsTest {

    private static final Logger LOG = LoggerFactory.getLogger(VertxKafkaConsumerOperationsTest.class);

    private final VertxKafkaConfiguration configuration = new VertxKafkaConfiguration();
    private final MockConsumer<Object, Object> mockConsumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
    private final KafkaConsumer<Object, Object> consumer = KafkaConsumer.create(Vertx.vertx(), mockConsumer);
    private final VertxKafkaConsumerOperations operations = new VertxKafkaConsumerOperations(consumer, configuration);

    @BeforeAll
    void prepare() {
        configuration.setKeySerializer(StringSerializer.class.getName());
        configuration.setValueSerializer(StringSerializer.class.getName());
        configuration.setKeyDeserializer(StringDeserializer.class.getName());
        configuration.setValueDeserializer(StringDeserializer.class.getName());
    }

    @AfterAll
    void closeConsumer() {
        consumer.close();
    }

    @AfterEach
    void unsubscribe() {
        mockConsumer.unsubscribe();
    }

    @Test
    void testConsumerSubscribeSingleRecords() {
        testConsumerSubscribeRecords(1, "test_single_record_test_topic", 1000);
    }

    @Test
    void testConsumerSubscribeBatchRecords() {
        testConsumerSubscribeRecords(50, "test_batch_record_test_topic", 5000);
    }

    void testConsumerSubscribeRecords(final int numberOfRecords, final String topic, final long timeout) {
        final Latch latch = new Latch();

        configuration.setTopic(topic);

        final TopicPartition topicPartition0 = new TopicPartition(topic, 0);
        final TopicPartition topicPartition1 = new TopicPartition(topic, 1);

        mockConsumer.schedulePollTask(() -> {
            // simulate a replacing for a partition
            mockConsumer.rebalance(Arrays.asList(topicPartition0, topicPartition1));
            // add our tests records
            for (int i = 0; i < numberOfRecords; i++) {
                mockConsumer.addRecord(record(topicPartition0.topic(), topicPartition0.partition(), i, "my-key-0-" + i,
                        "test-message-0-" + i));
                mockConsumer.addRecord(record(topicPartition1.topic(), topicPartition1.partition(), i, "my-key-1-" + i,
                        "test-message-1-" + i));
            }
            // reset the consumer to 0 in order to subscribe from start of the offset
            mockConsumer.seek(topicPartition0, 0L);
            mockConsumer.seek(topicPartition1, 0L);
        });

        final AtomicInteger count = new AtomicInteger();

        operations.receiveEvents(kafkaRecord -> {
            int val = count.getAndIncrement();

            assertEquals(topic, kafkaRecord.topic());

            if (kafkaRecord.partition() == 0) {
                assertEquals("my-key-0-" + val, kafkaRecord.key());
                assertEquals("test-message-0-" + val, kafkaRecord.record().value());
            } else {
                assertEquals("my-key-1-" + val, kafkaRecord.key());
                assertEquals("test-message-1-" + val, kafkaRecord.record().value());
            }

            if (val == numberOfRecords - 1) {
                latch.done();
            }
        }, error -> LOG.error(error.getLocalizedMessage(), error.getCause()));

        latch.await(timeout);
    }

    private ConsumerRecord<Object, Object> record(String topic, int partition, long offset, Object key, Object value) {
        return new ConsumerRecord<>(topic, partition, offset, key, value);
    }

    private static class Latch {
        final private AtomicBoolean doneFlag = new AtomicBoolean(false);

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
