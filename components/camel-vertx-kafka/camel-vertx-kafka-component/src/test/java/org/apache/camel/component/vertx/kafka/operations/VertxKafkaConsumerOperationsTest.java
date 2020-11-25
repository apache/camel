package org.apache.camel.component.vertx.kafka.operations;

import java.util.Collections;

import io.vertx.core.Vertx;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import org.apache.camel.component.vertx.kafka.configuration.VertxKafkaConfiguration;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
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

    @Test
    void testConsumerSimpleRecords() throws InterruptedException {
        configuration.setTopic("test");

        TopicPartition topicPartition = new TopicPartition("test", 0);

        mockConsumer.schedulePollTask(() -> {
            // simulate a replacing for a partition
            mockConsumer.rebalance(Collections.singletonList(new TopicPartition("test", 0)));
            mockConsumer.addRecord(record(topicPartition.topic(), topicPartition.partition(), "my-key", "test-message"));
            mockConsumer.seek(topicPartition, 0L);
        });

        operations.receiveEvents(kafkaRecord -> {
            System.out.println(kafkaRecord);
        }, error -> {
            System.out.println(error);
        });

        Thread.sleep(1000);
    }

    private ConsumerRecord<Object, Object> record(String topic, int partition, Object key, Object value) {
        return new ConsumerRecord<>(topic, partition, 0, key, value);
    }
}
