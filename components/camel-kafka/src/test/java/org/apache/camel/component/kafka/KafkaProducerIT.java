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
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * The Producer IT tests require a Kafka broker running on 9092 and a zookeeper instance running on 2181.
 * The broker must have a topic called test created.
 */
public class KafkaProducerIT extends CamelTestSupport {
    
    public static final String TOPIC = "test";
    public static final String TOPIC_IN_HEADER = "testHeader";

    private static final Logger LOG = LoggerFactory.getLogger(KafkaProducerIT.class);

    @EndpointInject(uri = "kafka:localhost:9092?topic=" + TOPIC 
        + "&partitioner=org.apache.camel.component.kafka.SimplePartitioner&serializerClass=kafka.serializer.StringEncoder&requestRequiredAcks=1")
    private Endpoint to;

    @Produce(uri = "direct:start")
    private ProducerTemplate template;

    private ConsumerConnector kafkaConsumer;

    @Before
    public void before() {
        Properties props = new Properties();
        props.put("zookeeper.connect", "localhost:2181");
        props.put("group.id", KafkaConstants.DEFAULT_GROUP);
        props.put("zookeeper.session.timeout.ms", "400");
        props.put("zookeeper.sync.time.ms", "200");
        props.put("auto.commit.interval.ms", "1000");

        kafkaConsumer = kafka.consumer.Consumer.createJavaConsumerConnector(new ConsumerConfig(props));
    }

    @After
    public void after() {
        kafkaConsumer.shutdown();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                from("direct:start").to(to);
            }
        };
    }

    @Test
    public void producedMessageIsReceivedByKafka() throws InterruptedException, IOException {
        int messageInTopic = 10;
        int messageInOtherTopic = 5;

        CountDownLatch messagesLatch = new CountDownLatch(messageInTopic + messageInOtherTopic);

        Map<String, Integer> topicCountMap = new HashMap<String, Integer>();
        topicCountMap.put(TOPIC, 5);
        topicCountMap.put(TOPIC_IN_HEADER, 5);
        createKafkaMessageConsumer(messagesLatch, topicCountMap);

        sendMessagesInRoute(messageInTopic, "IT test message", KafkaConstants.PARTITION_KEY, "1");
        sendMessagesInRoute(messageInOtherTopic, "IT test message in other topic", KafkaConstants.PARTITION_KEY, "1", KafkaConstants.TOPIC, TOPIC_IN_HEADER);

        boolean allMessagesReceived = messagesLatch.await(200, TimeUnit.MILLISECONDS);

        assertTrue("Not all messages were published to the kafka topics", allMessagesReceived);
    }

    private void createKafkaMessageConsumer(CountDownLatch messagesLatch, Map<String, Integer> topicCountMap) {
        Map<String, List<KafkaStream<byte[], byte[]>>> consumerMap = kafkaConsumer.createMessageStreams(topicCountMap);

        ExecutorService executor = Executors.newFixedThreadPool(10);
        for (final KafkaStream<byte[], byte[]> stream : consumerMap.get(TOPIC)) {
            executor.submit(new KakfaTopicConsumer(stream, messagesLatch));
        }
        for (final KafkaStream<byte[], byte[]> stream : consumerMap.get(TOPIC_IN_HEADER)) {
            executor.submit(new KakfaTopicConsumer(stream, messagesLatch));
        }
    }

    private void sendMessagesInRoute(int messageInOtherTopic, String bodyOther, String... headersWithValue) {
        Map<String, Object> headerMap = new HashMap<String, Object>();
        for (int i = 0; i < headersWithValue.length; i = i + 2) {
            headerMap.put(headersWithValue[i], headersWithValue[i + 1]);
        }

        for (int k = 0; k < messageInOtherTopic; k++) {
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
