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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
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
    private static final String TOPIC_TRANSACTION = "transaction";
    private static final String TOPIC_CONCURRENCY_TRANSACTION = "concurrency_transaction";
    private static KafkaConsumer<String, String> stringsConsumerConn;
    private static final int THREAD_NUM = 5;

    @EndpointInject("kafka:" + TOPIC_TRANSACTION + "?requestRequiredAcks=-1"
                    + "&additional-properties[transactional.id]=1234"
                    + "&additional-properties[enable.idempotence]=true"
                    + "&additional-properties[retries]=5")
    private Endpoint toTransaction;

    @EndpointInject("kafka:" + TOPIC_CONCURRENCY_TRANSACTION + "?requestRequiredAcks=-1&synchronous=true"
                    + "&additional-properties[transactional.id]=5678"
                    + "&additional-properties[enable.idempotence]=true"
                    + "&additional-properties[retries]=5")
    private Endpoint toConcurrencyTransaction;

    @EndpointInject("mock:kafkaAck")
    private MockEndpoint mockEndpoint;

    @Produce("direct:startTransaction")
    private ProducerTemplate testTransaction;

    @Produce("seda:startTransaction")
    private ProducerTemplate testConcurrencyTransaction;

    public KafkaTransactionIT() {

    }

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
            public void configure() throws Exception {
                from("direct:startTransaction").to(toTransaction)
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                String body = exchange.getIn().getBody(String.class);
                                if (body.contains("fail")) {
                                    throw new RuntimeException("fail process message " + body);
                                }
                            }
                        }).to(mockEndpoint);

                from("seda:startTransaction").to(toConcurrencyTransaction);
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
                    sendMessagesInRoute(messageInTopic, testConcurrencyTransaction, "IT test concurrency transaction message",
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

        sendMessagesInRoute(messageInTopic, testTransaction, "IT test transaction message", KafkaConstants.PARTITION_KEY, "0");
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                sendMessagesInRoute(messageInTopic, testTransaction, "IT test transaction fail message",
                        KafkaConstants.PARTITION_KEY,
                        "0");
            }
        });

        createKafkaMessageConsumer(stringsConsumerConn, TOPIC_TRANSACTION, messagesLatch);

        boolean allMessagesReceived = messagesLatch.await(200, TimeUnit.MILLISECONDS);

        assertTrue(allMessagesReceived,
                "Not all messages were published to the kafka topics. Not received: " + messagesLatch.getCount());

        List<Exchange> exchangeList = mockEndpoint.getExchanges();
        assertEquals(10, exchangeList.size(), "Ten Exchanges are expected");
        for (Exchange exchange : exchangeList) {
            @SuppressWarnings("unchecked")
            List<RecordMetadata> recordMetaData1
                    = (List<RecordMetadata>) (exchange.getIn().getHeader(KafkaConstants.KAFKA_RECORDMETA));
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
