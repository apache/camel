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
package org.apache.camel.component.vertx.kafka;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.vertx.kafka.client.producer.RecordMetadata;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Headers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VertxKafkaProducerFullTest extends BaseEmbeddedKafkaTest {

    private static final String TOPIC_STRINGS = "test";
    private static final String TOPIC_INTERCEPTED = "test";
    private static final String TOPIC_STRINGS_IN_HEADER = "testHeader";
    private static final String TOPIC_BYTES = "testBytes";
    private static final String TOPIC_BYTES_IN_HEADER = "testBytesHeader";
    private static final String GROUP_BYTES = "groupStrings";
    private static final String TOPIC_PROPAGATED_HEADERS = "testPropagatedHeaders";
    private static final String TOPIC_NO_RECORD_SPECIFIC_HEADERS = "noRecordSpecificHeaders";

    private static KafkaConsumer<String, String> stringsConsumerConn;
    private static KafkaConsumer<byte[], byte[]> bytesConsumerConn;

    @EndpointInject("vertx-kafka:" + TOPIC_STRINGS + "?acks=-1")
    private Endpoint toStrings;

    @EndpointInject("vertx-kafka:" + TOPIC_STRINGS + "?acks=-1&partitionId=0")
    private Endpoint toStrings2;

    @EndpointInject("mock:kafkaAck")
    private MockEndpoint mockEndpoint;

    @EndpointInject("vertx-kafka:" + TOPIC_BYTES + "?acks=-1"
                    + "&valueSerializer=org.apache.kafka.common.serialization.ByteArraySerializer&"
                    + "keySerializer=org.apache.kafka.common.serialization.ByteArraySerializer")
    private Endpoint toBytes;

    @EndpointInject("vertx-kafka:" + TOPIC_PROPAGATED_HEADERS + "?acks=-1")
    private Endpoint toPropagatedHeaders;

    @EndpointInject("vertx-kafka:" + TOPIC_NO_RECORD_SPECIFIC_HEADERS + "?acks=-1")
    private Endpoint toNoRecordSpecificHeaders;

    @Produce("direct:startStrings")
    private ProducerTemplate stringsTemplate;

    @Produce("direct:startStrings2")
    private ProducerTemplate stringsTemplate2;

    @Produce("direct:startBytes")
    private ProducerTemplate bytesTemplate;

    @Produce("direct:startTraced")
    private ProducerTemplate interceptedTemplate;

    @Produce("direct:propagatedHeaders")
    private ProducerTemplate propagatedHeadersTemplate;

    @Produce("direct:noRecordSpecificHeaders")
    private ProducerTemplate noRecordSpecificHeadersTemplate;

    @BeforeAll
    public static void before() {
        stringsConsumerConn = createStringKafkaConsumer("DemoConsumer");
        bytesConsumerConn = createByteKafkaConsumer(GROUP_BYTES);
    }

    @AfterAll
    public static void after() {
        // clean all test topics
        final List<String> topics = new ArrayList<>();
        topics.add(TOPIC_BYTES);
        topics.add(TOPIC_INTERCEPTED);
        topics.add(TOPIC_PROPAGATED_HEADERS);
        topics.add(TOPIC_STRINGS);
        topics.add(TOPIC_NO_RECORD_SPECIFIC_HEADERS);

        kafkaAdminClient.deleteTopics(topics);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:startStrings").to(toStrings).to(mockEndpoint);

                from("direct:startStrings2").to(toStrings2).to(mockEndpoint);

                from("direct:startBytes").to(toBytes).to(mockEndpoint);

                from("direct:propagatedHeaders").to(toPropagatedHeaders).to(mockEndpoint);

                from("direct:noRecordSpecificHeaders").to(toNoRecordSpecificHeaders).to(mockEndpoint);
            }
        };
    }

    @Test
    public void producedStringMessageIsReceivedByKafka() throws InterruptedException {
        int messageInTopic = 10;
        int messageInOtherTopic = 5;

        CountDownLatch messagesLatch = new CountDownLatch(messageInTopic + messageInOtherTopic);

        sendMessagesInRoute(messageInTopic, stringsTemplate, "IT test message", VertxKafkaConstants.PARTITION_ID, "0");
        sendMessagesInRoute(messageInOtherTopic, stringsTemplate, "IT test message in other topic",
                VertxKafkaConstants.PARTITION_ID, "0", VertxKafkaConstants.TOPIC,
                TOPIC_STRINGS_IN_HEADER);

        createKafkaMessageConsumer(stringsConsumerConn, TOPIC_STRINGS, TOPIC_STRINGS_IN_HEADER, messagesLatch);

        boolean allMessagesReceived = messagesLatch.await(200, TimeUnit.MILLISECONDS);

        assertTrue(allMessagesReceived,
                "Not all messages were published to the kafka topics. Not received: " + messagesLatch.getCount());

        List<Exchange> exchangeList = mockEndpoint.getExchanges();
        assertEquals(15, exchangeList.size(), "Fifteen Exchanges are expected");
        for (Exchange exchange : exchangeList) {
            List<RecordMetadata> recordMetaData1
                    = getRecordMetadata(exchange);
            assertEquals(1, recordMetaData1.size(), "One RecordMetadata is expected.");
            assertTrue(recordMetaData1.get(0).getOffset() >= 0, "Offset is positive");
            assertTrue(recordMetaData1.get(0).getTopic().startsWith("test"), "Topic Name start with 'test'");
        }
    }

    @Test
    public void producedString2MessageIsReceivedByKafka() throws InterruptedException {
        int messageInTopic = 10;
        int messageInOtherTopic = 5;

        CountDownLatch messagesLatch = new CountDownLatch(messageInTopic + messageInOtherTopic);

        sendMessagesInRoute(messageInTopic, stringsTemplate2, "IT test message", (String[]) null);
        sendMessagesInRoute(messageInOtherTopic, stringsTemplate2, "IT test message in other topic",
                VertxKafkaConstants.PARTITION_ID, "0", VertxKafkaConstants.TOPIC,
                TOPIC_STRINGS_IN_HEADER);

        createKafkaMessageConsumer(stringsConsumerConn, TOPIC_STRINGS, TOPIC_STRINGS_IN_HEADER, messagesLatch);

        boolean allMessagesReceived = messagesLatch.await(200, TimeUnit.MILLISECONDS);

        assertTrue(allMessagesReceived,
                "Not all messages were published to the kafka topics. Not received: " + messagesLatch.getCount());

        List<Exchange> exchangeList = mockEndpoint.getExchanges();
        assertEquals(15, exchangeList.size(), "Fifteen Exchanges are expected");
        for (Exchange exchange : exchangeList) {
            List<RecordMetadata> recordMetaData1
                    = getRecordMetadata(exchange);
            assertEquals(1, recordMetaData1.size(), "One RecordMetadata is expected.");
            assertTrue(recordMetaData1.get(0).getOffset() >= 0, "Offset is positive");
            assertTrue(recordMetaData1.get(0).getTopic().startsWith("test"), "Topic Name start with 'test'");
        }
    }

    @Test
    public void producedStringCollectionMessageIsReceivedByKafka() throws InterruptedException {
        int messageInTopic = 10;
        int messageInOtherTopic = 5;

        CountDownLatch messagesLatch = new CountDownLatch(messageInTopic + messageInOtherTopic);

        List<String> msgs = new ArrayList<>();
        for (int x = 0; x < messageInTopic; x++) {
            msgs.add("Message " + x);
        }

        sendMessagesInRoute(1, stringsTemplate, msgs, VertxKafkaConstants.PARTITION_ID, "0");
        msgs = new ArrayList<>();
        for (int x = 0; x < messageInOtherTopic; x++) {
            msgs.add("Other Message " + x);
        }
        sendMessagesInRoute(1, stringsTemplate, msgs, VertxKafkaConstants.PARTITION_ID, "0", VertxKafkaConstants.TOPIC,
                TOPIC_STRINGS_IN_HEADER);

        createKafkaMessageConsumer(stringsConsumerConn, TOPIC_STRINGS, TOPIC_STRINGS_IN_HEADER, messagesLatch);

        boolean allMessagesReceived = messagesLatch.await(200, TimeUnit.MILLISECONDS);

        assertTrue(allMessagesReceived,
                "Not all messages were published to the kafka topics. Not received: " + messagesLatch.getCount());
        List<Exchange> exchangeList = mockEndpoint.getExchanges();
        assertEquals(2, exchangeList.size(), "Two Exchanges are expected");
        Exchange e1 = exchangeList.get(0);
        List<RecordMetadata> recordMetaData1 = getRecordMetadata(e1);
        assertEquals(10, recordMetaData1.size(), "Ten RecordMetadata is expected.");
        for (RecordMetadata recordMeta : recordMetaData1) {
            assertTrue(recordMeta.getOffset() >= 0, "Offset is positive");
            assertTrue(recordMeta.getTopic().startsWith("test"), "Topic Name start with 'test'");
        }
        Exchange e2 = exchangeList.get(1);
        List<RecordMetadata> recordMetaData2 = getRecordMetadata(e2);
        assertEquals(5, recordMetaData2.size(), "Five RecordMetadata is expected.");
        for (RecordMetadata recordMeta : recordMetaData2) {
            assertTrue(recordMeta.getOffset() >= 0, "Offset is positive");
            assertTrue(recordMeta.getTopic().startsWith("test"), "Topic Name start with 'test'");
        }
    }

    @Test
    public void producedBytesMessageIsReceivedByKafka() throws InterruptedException {
        int messageInTopic = 10;
        int messageInOtherTopic = 5;

        CountDownLatch messagesLatch = new CountDownLatch(messageInTopic + messageInOtherTopic);

        Map<String, Object> inTopicHeaders = new HashMap<>();
        inTopicHeaders.put(VertxKafkaConstants.PARTITION_ID, "0".getBytes());
        sendMessagesInRoute(messageInTopic, bytesTemplate, "IT test message".getBytes(), inTopicHeaders);

        Map<String, Object> otherTopicHeaders = new HashMap<>();
        otherTopicHeaders.put(VertxKafkaConstants.PARTITION_ID, "0".getBytes());
        otherTopicHeaders.put(VertxKafkaConstants.TOPIC, TOPIC_BYTES_IN_HEADER);
        sendMessagesInRoute(messageInOtherTopic, bytesTemplate, "IT test message in other topic".getBytes(), otherTopicHeaders);

        createKafkaBytesMessageConsumer(bytesConsumerConn, TOPIC_BYTES, TOPIC_BYTES_IN_HEADER, messagesLatch);

        boolean allMessagesReceived = messagesLatch.await(200, TimeUnit.MILLISECONDS);

        assertTrue(allMessagesReceived,
                "Not all messages were published to the kafka topics. Not received: " + messagesLatch.getCount());

        List<Exchange> exchangeList = mockEndpoint.getExchanges();
        assertEquals(15, exchangeList.size(), "Fifteen Exchanges are expected");
        for (Exchange exchange : exchangeList) {
            List<RecordMetadata> recordMetaData1
                    = getRecordMetadata(exchange);
            assertEquals(1, recordMetaData1.size(), "One RecordMetadata is expected.");
            assertTrue(recordMetaData1.get(0).getOffset() >= 0, "Offset is positive");
            assertTrue(recordMetaData1.get(0).getTopic().startsWith("test"), "Topic Name start with 'test'");
        }
    }

    @Test
    public void propagatedHeaderIsReceivedByKafka() throws Exception {
        String propagatedStringHeaderKey = "PROPAGATED_STRING_HEADER";
        String propagatedStringHeaderValue = "propagated string header value";

        String propagatedIntegerHeaderKey = "PROPAGATED_INTEGER_HEADER";
        Integer propagatedIntegerHeaderValue = 54545;

        String propagatedLongHeaderKey = "PROPAGATED_LONG_HEADER";
        Long propagatedLongHeaderValue = 5454545454545L;

        String propagatedDoubleHeaderKey = "PROPAGATED_DOUBLE_HEADER";
        Double propagatedDoubleHeaderValue = 43434.545D;

        String propagatedBytesHeaderKey = "PROPAGATED_BYTES_HEADER";
        byte[] propagatedBytesHeaderValue = new byte[] { 121, 34, 34, 54, 5, 3, 54, -34 };

        String propagatedBooleanHeaderKey = "PROPAGATED_BOOLEAN_HEADER";
        Boolean propagatedBooleanHeaderValue = Boolean.TRUE;

        Map<String, Object> camelHeaders = new HashMap<>();
        camelHeaders.put(propagatedStringHeaderKey, propagatedStringHeaderValue);
        camelHeaders.put(propagatedIntegerHeaderKey, propagatedIntegerHeaderValue);
        camelHeaders.put(propagatedLongHeaderKey, propagatedLongHeaderValue);
        camelHeaders.put(propagatedDoubleHeaderKey, propagatedDoubleHeaderValue);
        camelHeaders.put(propagatedBytesHeaderKey, propagatedBytesHeaderValue);
        camelHeaders.put(propagatedBooleanHeaderKey, propagatedBooleanHeaderValue);

        camelHeaders.put("CustomObjectHeader", new Object());
        camelHeaders.put("CustomNullObjectHeader", null);
        camelHeaders.put("CamelFilteredHeader", "CamelFilteredHeader value");

        CountDownLatch messagesLatch = new CountDownLatch(1);
        propagatedHeadersTemplate.sendBodyAndHeaders("Some test message", camelHeaders);

        List<ConsumerRecord<String, String>> records = pollForRecords(createStringKafkaConsumer("propagatedHeaderConsumer"),
                TOPIC_PROPAGATED_HEADERS, messagesLatch);
        boolean allMessagesReceived = messagesLatch.await(10_000, TimeUnit.MILLISECONDS);

        assertTrue(allMessagesReceived,
                "Not all messages were published to the kafka topics. Not received: " + messagesLatch.getCount());

        ConsumerRecord<String, String> record = records.get(0);
        Headers headers = record.headers();
        assertNotNull(headers, "Kafka Headers should not be null.");
        // we have 6 headers
        assertEquals(6, headers.toArray().length, "6 propagated header is expected.");
        assertEquals(propagatedStringHeaderValue, new String(getHeaderValue(propagatedStringHeaderKey, headers)),
                "Propagated string value received");
        assertEquals(propagatedIntegerHeaderValue,
                Integer.valueOf(ByteBuffer.wrap(getHeaderValue(propagatedIntegerHeaderKey, headers)).getInt()),
                "Propagated integer value received");
        assertEquals(propagatedLongHeaderValue,
                Long.valueOf(ByteBuffer.wrap(getHeaderValue(propagatedLongHeaderKey, headers)).getLong()),
                "Propagated long value received");
        assertEquals(propagatedDoubleHeaderValue,
                Double.valueOf(ByteBuffer.wrap(getHeaderValue(propagatedDoubleHeaderKey, headers)).getDouble()),
                "Propagated double value received");
        assertArrayEquals(propagatedBytesHeaderValue, getHeaderValue(propagatedBytesHeaderKey, headers),
                "Propagated byte array value received");
        assertEquals(propagatedBooleanHeaderValue,
                Boolean.valueOf(new String(getHeaderValue(propagatedBooleanHeaderKey, headers))),
                "Propagated boolean value received");
    }

    @Test
    public void recordSpecificHeaderIsNotReceivedByKafka() throws Exception {
        String propagatedStringHeaderKey = VertxKafkaConstants.TIMESTAMP;
        String propagatedStringHeaderValue = "dummy timestamp";

        Map<String, Object> camelHeaders = new HashMap<>();
        camelHeaders.put(propagatedStringHeaderKey, propagatedStringHeaderValue);

        CountDownLatch messagesLatch = new CountDownLatch(1);
        noRecordSpecificHeadersTemplate.sendBodyAndHeaders("Some test message", camelHeaders);

        List<ConsumerRecord<String, String>> records = pollForRecords(
                createStringKafkaConsumer("noRecordSpecificHeadersConsumer"), TOPIC_NO_RECORD_SPECIFIC_HEADERS, messagesLatch);
        boolean allMessagesReceived = messagesLatch.await(10_000, TimeUnit.MILLISECONDS);

        assertTrue(allMessagesReceived,
                "Not all messages were published to the kafka topics. Not received: " + messagesLatch.getCount());

        ConsumerRecord<String, String> record = records.get(0);
        Headers headers = record.headers();
        assertNotNull(headers, "Kafka Headers should not be null.");
        // we have 0 headers
        assertEquals(0, headers.toArray().length, "0 propagated headers are expected");
    }

    private byte[] getHeaderValue(String headerKey, Headers headers) {
        return VertxKafkaTestUtils.getHeaderValue(headerKey, headers);
    }

    @SuppressWarnings("unchecked")
    private List<RecordMetadata> getRecordMetadata(final Exchange exchange) {
        return (List<RecordMetadata>) (exchange.getIn().getHeader(VertxKafkaConstants.RECORD_METADATA));
    }

    private static KafkaConsumer<String, String> createStringKafkaConsumer(final String groupId) {
        Properties stringsProps = new Properties();

        stringsProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                service.getBootstrapServers());
        stringsProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG, groupId);
        stringsProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        stringsProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        stringsProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "30000");
        stringsProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringDeserializer");
        stringsProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringDeserializer");
        stringsProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        return new KafkaConsumer<>(stringsProps);
    }

    private static KafkaConsumer<byte[], byte[]> createByteKafkaConsumer(final String groupId) {
        Properties stringsProps = new Properties();

        stringsProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                service.getBootstrapServers());
        stringsProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG, groupId);
        stringsProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        stringsProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        stringsProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "30000");
        stringsProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        stringsProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        stringsProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        return new KafkaConsumer<>(stringsProps);
    }

    private List<ConsumerRecord<String, String>> pollForRecords(
            KafkaConsumer<String, String> consumerConn, String topic, CountDownLatch messagesLatch) {

        List<ConsumerRecord<String, String>> consumedRecords = new ArrayList<>();
        consumerConn.subscribe(Collections.singletonList(topic));

        new Thread(() -> {
            while (messagesLatch.getCount() != 0) {
                for (ConsumerRecord<String, String> record : consumerConn.poll(Duration.ofMillis(100))) {
                    consumedRecords.add(record);
                    messagesLatch.countDown();
                }
            }
        }).start();

        return consumedRecords;
    }

    private void createKafkaMessageConsumer(
            KafkaConsumer<String, String> consumerConn, String topic, String topicInHeader, CountDownLatch messagesLatch) {

        consumerConn.subscribe(Arrays.asList(topic, topicInHeader));
        boolean run = true;

        while (run) {
            ConsumerRecords<String, String> records = consumerConn.poll(Duration.ofMillis(100));
            for (int i = 0; i < records.count(); i++) {
                messagesLatch.countDown();
                if (messagesLatch.getCount() == 0) {
                    run = false;
                }
            }
        }

    }

    private void createKafkaBytesMessageConsumer(
            KafkaConsumer<byte[], byte[]> consumerConn, String topic, String topicInHeader, CountDownLatch messagesLatch) {

        consumerConn.subscribe(Arrays.asList(topic, topicInHeader));
        boolean run = true;

        while (run) {
            ConsumerRecords<byte[], byte[]> records = consumerConn.poll(Duration.ofMillis(100));
            for (int i = 0; i < records.count(); i++) {
                messagesLatch.countDown();
                if (messagesLatch.getCount() == 0) {
                    run = false;
                }
            }
        }

    }

    private void sendMessagesInRoute(int messages, ProducerTemplate template, Object bodyOther, String... headersWithValue) {
        Map<String, Object> headerMap = new HashMap<>();
        if (headersWithValue != null) {
            for (int i = 0; i < headersWithValue.length; i = i + 2) {
                headerMap.put(headersWithValue[i], headersWithValue[i + 1]);
            }
        }
        sendMessagesInRoute(messages, template, bodyOther, headerMap);
    }

    private void sendMessagesInRoute(int messages, ProducerTemplate template, Object bodyOther, Map<String, Object> headerMap) {
        for (int k = 0; k < messages; k++) {
            template.sendBodyAndHeaders(bodyOther, headerMap);
        }
    }
}
