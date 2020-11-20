package org.apache.camel.component.vertx.kafka.operations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Vertx;
import io.vertx.kafka.client.producer.KafkaProducer;
import org.apache.camel.Exchange;
import org.apache.camel.component.vertx.kafka.VertxKafkaConstants;
import org.apache.camel.component.vertx.kafka.configuration.VertxKafkaConfiguration;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.internals.DefaultPartitioner;
import org.apache.kafka.common.Cluster;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VertxKafkaProducerOperationsTest extends CamelTestSupport {

    private final VertxKafkaConfiguration configuration = new VertxKafkaConfiguration();

    @BeforeAll
    void prepare() {
        configuration.setKeySerializer(StringSerializer.class.getName());
        configuration.setValueSerializer(StringSerializer.class.getName());
        configuration.setKeyDeserializer(StringDeserializer.class.getName());
        configuration.setValueDeserializer(StringDeserializer.class.getName());
    }

    @Test
    void testSendSimpleEventsAsListAndString() {
        final MockProducer<Object, Object> mockProducer = new MockProducer<>();
        final KafkaProducer<Object, Object> producer = KafkaProducer.create(Vertx.vertx(), mockProducer);

        configuration.setTopic("testSimpleEventsTopic");

        final VertxKafkaProducerOperations operations = new VertxKafkaProducerOperations(producer, configuration);

        final Exchange exchange = new DefaultExchange(context);
        final List<String> messages = new LinkedList<>();
        messages.add("test message 1");
        messages.add("test message 2");
        messages.add("test message 3");
        messages.add("test message 4");
        messages.add("test message 5");

        exchange.getIn().setBody(messages);

        operations.sendEvents(exchange, doneSync -> {
        });

        final Exchange exchange2 = new DefaultExchange(context);

        exchange2.getIn().setHeader(VertxKafkaConstants.MESSAGE_KEY, "6");
        exchange2.getIn().setBody("test message 6");

        operations.sendEvents(exchange2, doneSync -> {
        });

        Awaitility
                .await()
                .atMost(2, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> {
                    if (!mockProducer.history().isEmpty()) {
                        final List<ProducerRecord<Object, Object>> records = mockProducer.history();
                        // assert the size
                        assertEquals(records.size(), 6);

                        // assert the content
                        final ProducerRecord<Object, Object> record1 = records.get(0);
                        final ProducerRecord<Object, Object> record2 = records.get(1);
                        final ProducerRecord<Object, Object> record3 = records.get(2);
                        final ProducerRecord<Object, Object> record4 = records.get(3);
                        final ProducerRecord<Object, Object> record5 = records.get(4);
                        final ProducerRecord<Object, Object> record6 = records.get(5);

                        assertEquals("test message 1", record1.value().toString());
                        assertEquals("test message 2", record2.value().toString());
                        assertEquals("test message 3", record3.value().toString());
                        assertEquals("test message 4", record4.value().toString());
                        assertEquals("test message 5", record5.value().toString());
                        assertEquals("test message 6", record6.value().toString());

                        assertNull(record1.key());
                        assertNull(record2.key());
                        assertNull(record3.key());
                        assertNull(record4.key());
                        assertNull(record5.key());
                        assertEquals("6", record6.key().toString());

                        records.forEach(record -> {
                            // assert is the correct topic
                            assertEquals("testSimpleEventsTopic", record.topic());
                            assertNull(record.partition());
                        });

                        return true;
                    }
                    return false;
                });
    }

    @Test
    void testSendEventWithMultiplePartitions() {
        final MockProducer<Object, Object> mockProducer = createMockProducerWithCluster();
        final KafkaProducer<Object, Object> producer = KafkaProducer.create(Vertx.vertx(), mockProducer);

        configuration.setTopic("test_topic_with_partitions");

        final VertxKafkaProducerOperations operations = new VertxKafkaProducerOperations(producer, configuration);

        final Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("message 1");
        exchange.getIn().setHeader(VertxKafkaConstants.PARTITION_ID, 0);

        final CompletableFuture<Integer> resultPartition1 = new CompletableFuture<>();

        operations.sendEvents(exchange, recordMetadata -> resultPartition1.complete(recordMetadata.get(0).getPartition()),
                doneSync -> {
                });

        final Exchange exchange1 = new DefaultExchange(context);
        exchange1.getIn().setBody("message 2");
        exchange1.getIn().setHeader(VertxKafkaConstants.PARTITION_ID, 1);

        final CompletableFuture<Integer> resultPartition2 = new CompletableFuture<>();

        operations.sendEvents(exchange1, recordMetadata -> resultPartition2.complete(recordMetadata.get(0).getPartition()),
                doneSync -> {
                });

        final Exchange exchange2 = new DefaultExchange(context);
        exchange2.getIn().setBody("message 3");
        exchange2.getIn().setHeader(VertxKafkaConstants.MESSAGE_KEY, "2");

        final CompletableFuture<Integer> resultPartition3 = new CompletableFuture<>();

        operations.sendEvents(exchange2, recordMetadata -> resultPartition3.complete(recordMetadata.get(0).getPartition()),
                doneSync -> {
                });

        final Exchange exchange3 = new DefaultExchange(context);
        exchange3.getIn().setBody("message 4");
        exchange3.getIn().setHeader(VertxKafkaConstants.MESSAGE_KEY, "3");

        final CompletableFuture<Integer> resultPartition4 = new CompletableFuture<>();

        operations.sendEvents(exchange3, recordMetadata -> resultPartition4.complete(recordMetadata.get(0).getPartition()),
                doneSync -> {
                });

        Awaitility
                .await()
                .atMost(2, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> {
                    if (!mockProducer.history().isEmpty()) {
                        final List<ProducerRecord<Object, Object>> records = mockProducer.history();
                        // assert the size
                        assertEquals(4, records.size());

                        // assert the content
                        final ProducerRecord<Object, Object> record1 = records.get(0);
                        final ProducerRecord<Object, Object> record2 = records.get(1);
                        final ProducerRecord<Object, Object> record3 = records.get(2);
                        final ProducerRecord<Object, Object> record4 = records.get(3);

                        assertEquals("message 1", record1.value().toString());
                        assertEquals("message 2", record2.value().toString());
                        assertEquals("message 3", record3.value().toString());
                        assertEquals("message 4", record4.value().toString());

                        // assert partition id
                        assertEquals(0, resultPartition1.get());
                        assertEquals(1, resultPartition2.get());
                        assertEquals(0, resultPartition3.get());
                        assertEquals(1, resultPartition4.get());

                        return true;
                    }
                    return false;
                });
    }

    private MockProducer<Object, Object> createMockProducerWithCluster() {
        final List<PartitionInfo> partitionInfos = new ArrayList<>();
        partitionInfos.add(new PartitionInfo("test_topic_with_partitions", 0, null, null, null));
        partitionInfos.add(new PartitionInfo("test_topic_with_partitions", 1, null, null, null));

        final Cluster cluster = new Cluster(
                "kafka_cluster", new ArrayList<>(), partitionInfos, Collections.emptySet(), Collections.emptySet());

        return new MockProducer<>(
                cluster, true, new EvenOddPartitioner(), new StringObjectSerializer(), new StringObjectSerializer());
    }

    public static class EvenOddPartitioner extends DefaultPartitioner {
        @Override
        public int partition(
                String topic, Object key, byte[] keyBytes, Object value,
                byte[] valueBytes, Cluster cluster) {
            if (Integer.parseInt(key.toString()) % 2 == 0) {
                return 0;
            }
            return 1;
        }
    }

    public static class StringObjectSerializer implements Serializer<Object> {

        final StringSerializer stringSerializer = new StringSerializer();

        @Override
        public void configure(Map<String, ?> configs, boolean isKey) {
            stringSerializer.configure(configs, isKey);
        }

        @Override
        public byte[] serialize(String topic, Object data) {
            return stringSerializer.serialize(topic, data.toString());
        }
    }
}
