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
package org.apache.camel.component.kafka.integration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.integration.common.KafkaTestUtil;
import org.apache.camel.component.kafka.integration.common.TestProducerUtil;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KafkaTransactionIT extends BaseEmbeddedKafkaTestSupport {
    public static final String SEQUENTIAL_TRANSACTION_URI = "direct:startTransaction";
    public static final String CONCURRENT_TRANSACTION_URI = "seda:startTransaction";

    private static final String TOPIC_TRANSACTION = "transaction";
    private static final String TOPIC_CONCURRENCY_TRANSACTION = "concurrency_transaction";
    private static final int THREAD_NUM = 5;
    private static KafkaConsumer<String, String> stringsConsumerConn;

    @BeforeAll
    public static void before() {
        stringsConsumerConn = createStringKafkaConsumer("DemoTransaction");
    }

    @AfterAll
    public static void after() {
        // clean all test topics
        final List<String> topics = new ArrayList<>();
        topics.add(TOPIC_TRANSACTION);
        topics.add(TOPIC_CONCURRENCY_TRANSACTION);
        kafkaAdminClient.deleteTopics(topics);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(SEQUENTIAL_TRANSACTION_URI).to("kafka:" + TOPIC_TRANSACTION + "?requestRequiredAcks=-1"
                                                    + "&additional-properties[transactional.id]=1234"
                                                    + "&additional-properties[enable.idempotence]=true"
                                                    + "&additional-properties[retries]=5")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) {
                                String body = exchange.getIn().getBody(String.class);
                                if (body.contains("fail")) {
                                    throw new RuntimeException("fail process message " + body);
                                }
                            }
                        }).to(KafkaTestUtil.MOCK_RESULT);

                from(CONCURRENT_TRANSACTION_URI)
                        .to("kafka:" + TOPIC_CONCURRENCY_TRANSACTION + "?requestRequiredAcks=-1&synchronous=true"
                            + "&additional-properties[transactional.id]=5678"
                            + "&additional-properties[enable.idempotence]=true"
                            + "&additional-properties[retries]=5");
            }
        };
    }

    @Test
    public void concurrencyProducedTransactionMessage() throws InterruptedException {
        Thread[] threads = new Thread[THREAD_NUM];
        int messageInTopic = 5;

        CountDownLatch messagesLatch = new CountDownLatch(messageInTopic * THREAD_NUM);

        for (int i = 0; i < THREAD_NUM; i++) {
            threads[i] = new Thread(new Runnable() {
                @Override
                public void run() {
                    ProducerTemplate testConcurrencyTransaction = contextExtension.getProducerTemplate();

                    TestProducerUtil.sendMessagesInRoute(CONCURRENT_TRANSACTION_URI, messageInTopic, testConcurrencyTransaction,
                            "IT test concurrency transaction message",
                            KafkaConstants.PARTITION_KEY,
                            "0");
                }
            });
            threads[i].start();
        }

        for (int i = 0; i < THREAD_NUM; i++) {
            threads[i].join();
        }

        createKafkaMessageConsumer(stringsConsumerConn, TOPIC_CONCURRENCY_TRANSACTION, messagesLatch);

        boolean allMessagesReceived = messagesLatch.await(200, TimeUnit.MILLISECONDS);

        assertTrue(allMessagesReceived,
                "Not all messages were published to the kafka topics. Not received: " + messagesLatch.getCount());
    }

    @Test
    public void producedTransactionMassageIsReceivedByKafka() throws InterruptedException {
        int messageInTopic = 10;

        CountDownLatch messagesLatch = new CountDownLatch(messageInTopic);

        ProducerTemplate testTransaction = contextExtension.getProducerTemplate();
        TestProducerUtil.sendMessagesInRoute(SEQUENTIAL_TRANSACTION_URI, messageInTopic, testTransaction,
                "IT test transaction message",
                KafkaConstants.PARTITION_KEY, "0");
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                TestProducerUtil.sendMessagesInRoute(SEQUENTIAL_TRANSACTION_URI, messageInTopic, testTransaction,
                        "IT test transaction fail message",
                        KafkaConstants.PARTITION_KEY,
                        "0");
            }
        });

        createKafkaMessageConsumer(stringsConsumerConn, TOPIC_TRANSACTION, messagesLatch);

        boolean allMessagesReceived = messagesLatch.await(200, TimeUnit.MILLISECONDS);

        assertTrue(allMessagesReceived,
                "Not all messages were published to the kafka topics. Not received: " + messagesLatch.getCount());

        MockEndpoint mockEndpoint = contextExtension.getMockEndpoint(KafkaTestUtil.MOCK_RESULT);

        List<Exchange> exchangeList = mockEndpoint.getExchanges();
        assertEquals(10, exchangeList.size(), "Ten Exchanges are expected");
        for (Exchange exchange : exchangeList) {
            @SuppressWarnings("unchecked")
            List<RecordMetadata> recordMetaData1
                    = (List<RecordMetadata>) (exchange.getIn().getHeader(KafkaConstants.KAFKA_RECORD_META));
            assertEquals(1, recordMetaData1.size(), "One RecordMetadata is expected.");
            assertTrue(recordMetaData1.get(0).offset() >= 0, "Offset is positive");
            assertTrue(recordMetaData1.get(0).topic().startsWith("transaction"), "Topic Name start with 'transaction'");
        }

    }

    private static KafkaConsumer<String, String> createStringKafkaConsumer(final String groupId) {
        Properties stringsProps = new Properties();

        stringsProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, getBootstrapServers());
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

    private void createKafkaMessageConsumer(
            KafkaConsumer<String, String> consumerConn, String topic, CountDownLatch messagesLatch) {

        consumerConn.subscribe(Arrays.asList(topic));
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

}
