/**
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

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import kafka.consumer.ConsumerConfig;
import kafka.consumer.ConsumerIterator;
import kafka.consumer.KafkaStream;
import kafka.javaapi.consumer.ConsumerConnector;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaProducerFullTest extends BaseEmbeddedKafkaTest {
    
    private static final String TOPIC_STRINGS = "test";
    private static final String TOPIC_STRINGS_IN_HEADER = "testHeader";
    private static final String TOPIC_BYTES = "testBytes";
    private static final String TOPIC_BYTES_IN_HEADER = "testBytesHeader";
    private static final String GROUP_STRINGS = "groupStrings";
    private static final String GROUP_BYTES = "groupStrings";

    private static final Logger LOG = LoggerFactory.getLogger(KafkaProducerFullTest.class);

    private static ConsumerConnector stringsConsumerConn;
    private static ConsumerConnector bytesConsumerConn;

    @EndpointInject(uri = "kafka:localhost:{{karfkaPort}}?topic=" + TOPIC_STRINGS
        + "&partitioner=org.apache.camel.component.kafka.SimplePartitioner&serializerClass=kafka.serializer.StringEncoder"
        + "&requestRequiredAcks=-1")
    private Endpoint toStrings;

    @EndpointInject(uri = "kafka:localhost:{{karfkaPort}}?topic=" + TOPIC_BYTES + "&requestRequiredAcks=-1")
    private Endpoint toBytes;

    @Produce(uri = "direct:startStrings")
    private ProducerTemplate stringsTemplate;

    @Produce(uri = "direct:startBytes")
    private ProducerTemplate bytesTemplate;


    @BeforeClass
    public static void before() {
        Properties stringsProps = new Properties();
       
        stringsProps.put("zookeeper.connect", "localhost:" + getZookeeperPort());
        stringsProps.put("group.id", GROUP_STRINGS);
        stringsProps.put("zookeeper.session.timeout.ms", "6000");
        stringsProps.put("zookeeper.connectiontimeout.ms", "12000");
        stringsProps.put("zookeeper.sync.time.ms", "200");
        stringsProps.put("auto.commit.interval.ms", "1000");
        stringsProps.put("auto.offset.reset", "smallest");
        stringsConsumerConn = kafka.consumer.Consumer.createJavaConsumerConnector(new ConsumerConfig(stringsProps));

        Properties bytesProps = new Properties();
        bytesProps.putAll(stringsProps);
        bytesProps.put("group.id", GROUP_BYTES);
        bytesConsumerConn = kafka.consumer.Consumer.createJavaConsumerConnector(new ConsumerConfig(bytesProps));
    }

    @AfterClass
    public static void after() {
        stringsConsumerConn.shutdown();
        bytesConsumerConn.shutdown();
    }

    @Override
    protected RouteBuilder[] createRouteBuilders() throws Exception {
        return new RouteBuilder[] {
            new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("direct:startStrings").to(toStrings);
                }
            },
            new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("direct:startBytes").to(toBytes);
                }
            }
        };
    }

    @Test
    public void producedStringMessageIsReceivedByKafka() throws InterruptedException, IOException {
        int messageInTopic = 10;
        int messageInOtherTopic = 5;

        CountDownLatch messagesLatch = new CountDownLatch(messageInTopic + messageInOtherTopic);

        Map<String, Integer> topicCountMap = new HashMap<String, Integer>();
        topicCountMap.put(TOPIC_STRINGS, 5);
        topicCountMap.put(TOPIC_STRINGS_IN_HEADER, 5);
        createKafkaMessageConsumer(stringsConsumerConn, TOPIC_STRINGS, TOPIC_STRINGS_IN_HEADER, messagesLatch, topicCountMap);

        sendMessagesInRoute(messageInTopic, stringsTemplate, "IT test message", KafkaConstants.PARTITION_KEY, "1");
        sendMessagesInRoute(messageInOtherTopic, stringsTemplate, "IT test message in other topic", KafkaConstants.PARTITION_KEY, "1", KafkaConstants.TOPIC, TOPIC_STRINGS_IN_HEADER);

        boolean allMessagesReceived = messagesLatch.await(200, TimeUnit.MILLISECONDS);

        assertTrue("Not all messages were published to the kafka topics. Not received: " + messagesLatch.getCount(), allMessagesReceived);
    }

    @Test
    public void producedBytesMessageIsReceivedByKafka() throws InterruptedException, IOException {
        int messageInTopic = 10;
        int messageInOtherTopic = 5;

        CountDownLatch messagesLatch = new CountDownLatch(messageInTopic + messageInOtherTopic);

        Map<String, Integer> topicCountMap = new HashMap<String, Integer>();
        topicCountMap.put(TOPIC_BYTES, 5);
        topicCountMap.put(TOPIC_BYTES_IN_HEADER, 5);
        createKafkaMessageConsumer(bytesConsumerConn, TOPIC_BYTES, TOPIC_BYTES_IN_HEADER, messagesLatch, topicCountMap);

        Map<String, Object> inTopicHeaders = new HashMap<String, Object>();
        inTopicHeaders.put(KafkaConstants.PARTITION_KEY, "1".getBytes());
        sendMessagesInRoute(messageInTopic, bytesTemplate, "IT test message".getBytes(), inTopicHeaders);

        Map<String, Object> otherTopicHeaders = new HashMap<String, Object>();
        otherTopicHeaders.put(KafkaConstants.PARTITION_KEY, "1".getBytes());
        otherTopicHeaders.put(KafkaConstants.TOPIC, TOPIC_BYTES_IN_HEADER);
        sendMessagesInRoute(messageInOtherTopic, bytesTemplate, "IT test message in other topic".getBytes(), otherTopicHeaders);

        boolean allMessagesReceived = messagesLatch.await(200, TimeUnit.MILLISECONDS);

        assertTrue("Not all messages were published to the kafka topics. Not received: " + messagesLatch.getCount(), allMessagesReceived);
    }

    private void createKafkaMessageConsumer(ConsumerConnector consumerConn, String topic, String topicInHeader,
                                            CountDownLatch messagesLatch, Map<String, Integer> topicCountMap) {
        Map<String, List<KafkaStream<byte[], byte[]>>> consumerMap = consumerConn.createMessageStreams(topicCountMap);

        ExecutorService executor = Executors.newFixedThreadPool(10);
        for (final KafkaStream<byte[], byte[]> stream : consumerMap.get(topic)) {
            executor.submit(new KakfaTopicConsumer(stream, messagesLatch));
        }
        for (final KafkaStream<byte[], byte[]> stream : consumerMap.get(topicInHeader)) {
            executor.submit(new KakfaTopicConsumer(stream, messagesLatch));
        }
    }

    private void sendMessagesInRoute(int messages, ProducerTemplate template, Object bodyOther, String... headersWithValue) {
        Map<String, Object> headerMap = new HashMap<String, Object>();
        for (int i = 0; i < headersWithValue.length; i = i + 2) {
            headerMap.put(headersWithValue[i], headersWithValue[i + 1]);
        }
        sendMessagesInRoute(messages, template, bodyOther, headerMap);
    }

    private void sendMessagesInRoute(int messages, ProducerTemplate template, Object bodyOther, Map<String, Object> headerMap) {
        for (int k = 0; k < messages; k++) {
            template.sendBodyAndHeaders(bodyOther, headerMap);
        }
    }

    private static class KakfaTopicConsumer implements Runnable {
        private final KafkaStream<byte[], byte[]> stream;
        private final CountDownLatch latch;

        public KakfaTopicConsumer(KafkaStream<byte[], byte[]> stream, CountDownLatch latch) {
            this.stream = stream;
            this.latch = latch;
        }

        @Override
        public void run() {
            ConsumerIterator<byte[], byte[]> it = stream.iterator();
            while (it.hasNext()) {
                String msg = new String(it.next().message());
                LOG.info("Get the message" + msg);
                latch.countDown();
            }
        }
    }
}
