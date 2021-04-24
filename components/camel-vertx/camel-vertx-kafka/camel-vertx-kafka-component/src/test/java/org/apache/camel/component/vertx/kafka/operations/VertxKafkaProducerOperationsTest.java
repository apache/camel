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

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import io.vertx.core.Vertx;
import io.vertx.kafka.client.producer.KafkaProducer;
import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.vertx.kafka.VertxKafkaConstants;
import org.apache.camel.component.vertx.kafka.VertxKafkaTestUtils;
import org.apache.camel.component.vertx.kafka.configuration.VertxKafkaConfiguration;
import org.apache.camel.processor.aggregate.GroupedExchangeAggregationStrategy;
import org.apache.camel.processor.aggregate.GroupedMessageAggregationStrategy;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.internals.DefaultPartitioner;
import org.apache.kafka.common.Cluster;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Serializer;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VertxKafkaProducerOperationsTest extends CamelTestSupport {

    private static final String TOPIC_NAME_WITH_PARTITIONS = "test_topic_with_partitions";

    private static final Logger LOG = LoggerFactory.getLogger(VertxKafkaProducerOperationsTest.class);

    private final VertxKafkaConfiguration configuration = new VertxKafkaConfiguration();
    private final MockProducer<Object, Object> mockProducer = createMockProducerWithCluster();
    private final KafkaProducer<Object, Object> producer = KafkaProducer.create(Vertx.vertx(), mockProducer);
    private final VertxKafkaProducerOperations operations = new VertxKafkaProducerOperations(producer, configuration);

    @BeforeAll
    void prepare() {
        configuration.setKeySerializer(StringSerializer.class.getName());
        configuration.setValueSerializer(StringSerializer.class.getName());
        configuration.setKeyDeserializer(StringDeserializer.class.getName());
        configuration.setValueDeserializer(StringDeserializer.class.getName());
    }

    @AfterEach
    void clearRecords() {
        // clear any outstanding records
        mockProducer.clear();
    }

    @AfterAll
    void closeProducer() {
        mockProducer.close();
    }

    @Test
    void testSendSimpleEventsAsListAndString() {
        configuration.setTopic("testSimpleEventsTopic");

        final Message message1 = createMessage();
        final List<String> messages = new LinkedList<>();
        messages.add("test message 1");
        messages.add("test message 2");
        messages.add("test message 3");
        messages.add("test message 4");
        messages.add("test message 5");

        message1.setBody(messages);
        sendEvent(message1);

        final Message message2 = createMessage();

        message2.setHeader(VertxKafkaConstants.MESSAGE_KEY, "6");
        message2.setBody("test message 6");

        sendEvent(message2);

        assertProducedMessages(records -> {
            // assert the size
            assertEquals(6, records.size());

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
        });
    }

    @Test
    void testSendEventWithMultiplePartitions() {
        configuration.setTopic(TOPIC_NAME_WITH_PARTITIONS);

        final Message message1 = createMessage();
        message1.setBody("message 1");
        message1.setHeader(VertxKafkaConstants.PARTITION_ID, 0);

        final CompletableFuture<Integer> resultPartition1 = new CompletableFuture<>();

        operations.sendEvents(message1, recordMetadata -> resultPartition1.complete(recordMetadata.get(0).getPartition()),
                doneSync -> {
                });

        final Message message2 = createMessage();
        message2.setBody("message 2");
        message2.setHeader(VertxKafkaConstants.PARTITION_ID, 1);

        final CompletableFuture<Integer> resultPartition2 = new CompletableFuture<>();

        operations.sendEvents(message2, recordMetadata -> resultPartition2.complete(recordMetadata.get(0).getPartition()),
                doneSync -> {
                });

        final Message message3 = createMessage();
        message3.setBody("message 3");
        message3.setHeader(VertxKafkaConstants.MESSAGE_KEY, "2");

        final CompletableFuture<Integer> resultPartition3 = new CompletableFuture<>();

        operations.sendEvents(message3, recordMetadata -> resultPartition3.complete(recordMetadata.get(0).getPartition()),
                doneSync -> {
                });

        final Message message4 = createMessage();
        message4.setBody("message 4");
        message4.setHeader(VertxKafkaConstants.MESSAGE_KEY, "3");

        final CompletableFuture<Integer> resultPartition4 = new CompletableFuture<>();

        operations.sendEvents(message4, recordMetadata -> resultPartition4.complete(recordMetadata.get(0).getPartition()),
                doneSync -> {
                });

        assertProducedMessages(records -> {
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
            try {
                assertEquals(0, resultPartition1.get());
                assertEquals(1, resultPartition2.get());
                assertEquals(0, resultPartition3.get());
                assertEquals(1, resultPartition4.get());
            } catch (InterruptedException | ExecutionException e) {
                LOG.error(e.getMessage(), e);
            }

            records.forEach(record -> {
                assertEquals(TOPIC_NAME_WITH_PARTITIONS, record.topic());
            });
        });
    }

    @Test
    void testSendEventsWithTopicHeaderAndNoTopicInConfig() {
        configuration.setTopic(null);

        final Message message = createMessage();
        message.setBody("test message");
        message.setHeader(VertxKafkaConstants.TOPIC, "test_topic");

        sendEvent(message);

        verifySendMessage("test_topic", null, "test message");
    }

    @Test
    void testSendEventWithTopicHeaderAndConfig() {
        configuration.setTopic("sometopic");

        final Message message = createMessage();

        message.setHeader(VertxKafkaConstants.TOPIC, "anotherTopic");
        message.setHeader(VertxKafkaConstants.MESSAGE_KEY, "someKey");
        message.setBody("test");

        sendEvent(message);

        // header is preserved
        assertNotNull(message.getHeader(VertxKafkaConstants.TOPIC));

        verifySendMessage("anotherTopic", "someKey", "test");
    }

    @Test
    void testSendEventWithOverrideTopicHeaderAndConfig() {
        configuration.setTopic("sometopic");

        final Message message = createMessage();

        message.setHeader(VertxKafkaConstants.OVERRIDE_TOPIC, "anotherTopic");
        message.setHeader(VertxKafkaConstants.MESSAGE_KEY, "someKey");
        message.setBody("test");

        sendEvent(message);

        // the header is now removed
        assertNull(message.getHeader(VertxKafkaConstants.OVERRIDE_TOPIC));

        verifySendMessage("anotherTopic", "someKey", "test");
    }

    @Test
    void testSendEventWithOverrideTopicHeaderAndTimestamp() {
        configuration.setTopic("sometopic");
        Long timestamp = LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        final Message message = createMessage();

        message.setHeader(VertxKafkaConstants.OVERRIDE_TOPIC, "anotherTopic");
        message.setHeader(VertxKafkaConstants.MESSAGE_KEY, "someKey");
        message.setHeader(VertxKafkaConstants.OVERRIDE_TIMESTAMP, timestamp);
        message.setBody("test");

        sendEvent(message);

        // the header is now removed
        assertNull(message.getHeader(VertxKafkaConstants.OVERRIDE_TOPIC));
        assertNull(message.getHeader(VertxKafkaConstants.OVERRIDE_TIMESTAMP));

        verifySendMessage("anotherTopic", "someKey", timestamp, "test");
    }

    @Test
    void testSendEventWithNoTopicSet() {
        configuration.setTopic(null);

        final Message message = createMessage();

        message.setHeader(VertxKafkaConstants.MESSAGE_KEY, "someKey");
        message.setBody("test");

        assertThrows(IllegalArgumentException.class, () -> sendEvent(message));
    }

    @Test
    void testSendEventRequiresTopicInConfiguration() {
        configuration.setTopic("configTopic");

        final Message message = createMessage();

        message.setHeader(VertxKafkaConstants.MESSAGE_KEY, "someKey");

        sendEvent(message);

        verifySendMessage("configTopic", "someKey");
    }

    @Test
    void testSendsEventWithListOfExchangesWithOverrideTopicHeaderOnEveryExchange() {
        configuration.setTopic("someTopic");

        final Message message = createMessage();

        // we set the initial topic
        message.setHeader(VertxKafkaConstants.OVERRIDE_TOPIC, "anotherTopic");
        message.setHeader(VertxKafkaConstants.MESSAGE_KEY, "someKey");

        // we add our exchanges in order to aggregate
        final List<Exchange> nestedExchanges
                = createListOfExchanges(Arrays.asList("overridenTopic1", "overridenTopic2", "overridenTopic3"),
                        VertxKafkaConstants.OVERRIDE_TOPIC);

        // aggregate
        final Exchange finalAggregatedExchange = aggregateExchanges(nestedExchanges, new GroupedExchangeAggregationStrategy());

        message.setBody(finalAggregatedExchange.getIn().getBody());
        message.setHeaders(finalAggregatedExchange.getIn().getHeaders());

        sendEvent(message);

        assertProducedMessages(records -> {
            assertEquals(3, records.size());

            // assert topics
            assertEquals("overridenTopic1", records.get(0).topic());
            assertEquals("overridenTopic2", records.get(1).topic());
            assertEquals("overridenTopic3", records.get(2).topic());
        });
    }

    @Test
    void testSendsEventWithListOfExchangesWithTopicHeaderOnEveryExchange() {
        configuration.setTopic("someTopic");

        final Message message = createMessage();

        // we set the initial topic
        message.setHeader(VertxKafkaConstants.OVERRIDE_TOPIC, "anotherTopic");
        message.setHeader(VertxKafkaConstants.MESSAGE_KEY, "someKey");

        // we add our exchanges in order to aggregate
        final List<Exchange> nestedExchanges
                = createListOfExchanges(Arrays.asList("topic1", "topic2", "topic3"),
                        VertxKafkaConstants.TOPIC);

        // aggregate
        final Exchange finalAggregatedExchange = aggregateExchanges(nestedExchanges, new GroupedExchangeAggregationStrategy());

        message.setBody(finalAggregatedExchange.getIn().getBody());
        message.setHeaders(finalAggregatedExchange.getIn().getHeaders());

        sendEvent(message);

        assertProducedMessages(records -> {
            assertEquals(3, records.size());

            // assert topics
            assertEquals("topic1", records.get(0).topic());
            assertEquals("topic2", records.get(1).topic());
            assertEquals("topic3", records.get(2).topic());
        });
    }

    @Test
    void testSendsEventWithListOfExchangesWithTopicOnConfig() {
        configuration.setTopic("someTopic");

        final Message message = createMessage();

        message.setHeader(VertxKafkaConstants.MESSAGE_KEY, "someKey");

        // we add our exchanges in order to aggregate
        final List<Exchange> nestedExchanges
                = createListOfExchanges(Arrays.asList("key1", "key2", "key3"), VertxKafkaConstants.MESSAGE_KEY);

        // aggregate
        final Exchange finalAggregatedExchange = aggregateExchanges(nestedExchanges, new GroupedExchangeAggregationStrategy());

        message.setBody(finalAggregatedExchange.getIn().getBody());
        message.setHeaders(finalAggregatedExchange.getIn().getHeaders());

        sendEvent(message);

        assertProducedMessages(records -> {
            assertEquals(3, records.size());

            // assert keys
            assertEquals("key1", records.get(0).key());
            assertEquals("key2", records.get(1).key());
            assertEquals("key3", records.get(2).key());

            records.forEach(record -> assertEquals("someTopic", record.topic()));
        });
    }

    @Test
    void testSendsEventWithListOfExchangesWithOverrideTopicOnHeaders() {
        configuration.setTopic("someTopic");

        final Message message = createMessage();

        message.setHeader(VertxKafkaConstants.MESSAGE_KEY, "someKey");
        message.setHeader(VertxKafkaConstants.OVERRIDE_TOPIC, "anotherTopic");

        // we add our exchanges in order to aggregate
        final List<Exchange> nestedExchanges
                = createListOfExchanges(Arrays.asList("key1", "key2", "key3"), VertxKafkaConstants.MESSAGE_KEY);

        // aggregate
        final Exchange finalAggregatedExchange = aggregateExchanges(nestedExchanges, new GroupedExchangeAggregationStrategy());

        message.setBody(finalAggregatedExchange.getIn().getBody());

        sendEvent(message);

        // the header is now removed
        assertNull(message.getHeader(VertxKafkaConstants.OVERRIDE_TOPIC));

        assertProducedMessages(records -> {
            assertEquals(3, records.size());

            // assert keys
            assertEquals("key1", records.get(0).key());
            assertEquals("key2", records.get(1).key());
            assertEquals("key3", records.get(2).key());

            records.forEach(record -> assertEquals("anotherTopic", record.topic()));
        });
    }

    @Test
    void testSendsEventWithListOfMessagesWithOverrideTopicHeaderOnEveryExchange() {
        configuration.setTopic("someTopic");

        final Message message = createMessage();

        // we set the initial topic
        message.setHeader(VertxKafkaConstants.OVERRIDE_TOPIC, "anotherTopic");
        message.setHeader(VertxKafkaConstants.MESSAGE_KEY, "someKey");

        // we add our exchanges in order to aggregate
        final List<Exchange> nestedExchanges
                = createListOfExchanges(Arrays.asList("overridenTopic1", "overridenTopic2", "overridenTopic3"),
                        VertxKafkaConstants.OVERRIDE_TOPIC);

        // aggregate
        final Exchange finalAggregatedExchange = aggregateExchanges(nestedExchanges, new GroupedMessageAggregationStrategy());

        message.setBody(finalAggregatedExchange.getIn().getBody());
        message.setHeaders(finalAggregatedExchange.getIn().getHeaders());

        sendEvent(message);

        assertProducedMessages(records -> {
            assertEquals(3, records.size());

            // assert topics
            assertEquals("overridenTopic1", records.get(0).topic());
            assertEquals("overridenTopic2", records.get(1).topic());
            assertEquals("overridenTopic3", records.get(2).topic());
        });
    }

    @Test
    void testIfPropagateHeadersFromCamelMessage() {
        configuration.setTopic("someTopic");

        final Message message = createMessage();

        // set some headers
        message.setHeader("CamelDummy", "test-camel-dummy-header");
        message.setHeader(VertxKafkaConstants.TOPIC, "someTopic");
        message.setHeader("MyHeader", "test-value-my-header");
        message.setBody("message");

        sendEvent(message);

        assertProducedMessages(records -> {
            assertEquals(1, records.size());
            // we expect only one header since they are filtered out
            assertEquals(1, records.get(0).headers().toArray().length);
            assertEquals("test-value-my-header", new String(getHeaderValue("MyHeader", records.get(0).headers())));
        });
    }

    private Message createMessage() {
        return new DefaultExchange(context).getIn();
    }

    private List<Exchange> createListOfExchanges(final List<String> properties, final String headerKey) {
        final List<Exchange> resultLists = new LinkedList<>();

        properties.forEach(property -> {
            final Exchange innerExchange = new DefaultExchange(context);
            innerExchange.getIn().setHeader(headerKey, property);
            resultLists.add(innerExchange);
        });

        return resultLists;
    }

    private Exchange aggregateExchanges(final List<Exchange> exchangesToAggregate, final AggregationStrategy strategy) {
        Exchange exchangeHolder = new DefaultExchange(context);

        for (final Exchange innerExchange : exchangesToAggregate) {
            exchangeHolder = strategy.aggregate(exchangeHolder, innerExchange);
        }

        strategy.onCompletion(exchangeHolder);

        return exchangeHolder;
    }

    private MockProducer<Object, Object> createMockProducerWithCluster() {
        final List<PartitionInfo> partitionInfos = new ArrayList<>();
        partitionInfos.add(new PartitionInfo(TOPIC_NAME_WITH_PARTITIONS, 0, null, null, null));
        partitionInfos.add(new PartitionInfo(TOPIC_NAME_WITH_PARTITIONS, 1, null, null, null));

        final Cluster cluster = new Cluster(
                "kafka_cluster", new ArrayList<>(), partitionInfos, Collections.emptySet(), Collections.emptySet());

        return new MockProducer<>(
                cluster, true, new EvenOddPartitioner(), new StringObjectSerializer(), new StringObjectSerializer());
    }

    private void verifySendMessage(final String topic, final Object messageKey) {
        verifySendMessage(topic, messageKey, null);
    }

    private void verifySendMessage(final String topic, final Object messageKey, final Object messageBody) {
        assertProducedMessages(records -> {
            assertEquals(1, records.size());
            assertEquals(topic, records.get(0).topic());
            assertEquals(messageKey, records.get(0).key());
            assertEquals(messageBody, records.get(0).value());
        });
    }

    private void verifySendMessage(
            final String topic, final Object messageKey, final Long timestamp, final Object messageBody) {
        assertProducedMessages(records -> {
            assertEquals(1, records.size());
            assertEquals(topic, records.get(0).topic());
            assertEquals(messageKey, records.get(0).key());
            assertEquals(messageBody, records.get(0).value());
            assertEquals(timestamp, records.get(0).timestamp());

        });
    }

    private void assertProducedMessages(final Consumer<List<ProducerRecord<Object, Object>>> recordsFn) {
        Awaitility
                .await()
                .atMost(2, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> {
                    if (!mockProducer.history().isEmpty()) {
                        recordsFn.accept(mockProducer.history());

                        return true;
                    }
                    return false;
                });
    }

    private void sendEvent(final Message message) {
        operations.sendEvents(message, doneSync -> {
        });
    }

    private byte[] getHeaderValue(String headerKey, Headers headers) {
        return VertxKafkaTestUtils.getHeaderValue(headerKey, headers);
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
            return stringSerializer.serialize(topic, data != null ? data.toString() : null);
        }
    }
}
