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

import java.util.*;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.MockConsumerInterceptor;
import org.apache.camel.component.kafka.consumer.KafkaManualCommit;
import org.apache.camel.component.kafka.testutil.CamelKafkaUtil;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test breakOnFirstError functionality and the issue reported in CAMEL-20563 regarding leaking resources, mainly
 * heartbeat-threads, while reconnecting.
 *
 */
class KafkaBreakOnFirstErrorReleaseResourcesIT extends BaseEmbeddedKafkaTestSupport {

    public static final String ROUTE_ID = "breakOnFirstError-20563";
    public static final String TOPIC = "breakOnFirstError-20563";
    private static final int CONSUMER_COUNT = 3;

    private static final Logger LOG = LoggerFactory.getLogger(KafkaBreakOnFirstErrorReleaseResourcesIT.class);

    @EndpointInject("kafka:" + TOPIC
                    + "?groupId=KafkaBreakOnFirstErrorIT"
                    + "&autoOffsetReset=earliest"
                    + "&autoCommitEnable=false"
                    + "&allowManualCommit=true"
                    + "&breakOnFirstError=true"
                    + "&maxPollRecords=1"
                    // here multiple threads was an issue
                    + "&consumersCount=3"
                    + "&pollTimeoutMs=1000"
                    + "&keyDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
                    + "&valueDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
                    + "&interceptorClasses=org.apache.camel.component.kafka.MockConsumerInterceptor")
    private Endpoint from;

    @EndpointInject("mock:result")
    private MockEndpoint to;

    private org.apache.kafka.clients.producer.KafkaProducer<String, String> producer;

    @BeforeAll
    public static void setupTopic() {
        AdminClient kafkaAdminClient = createAdminClient(service);

        // create the topic w/ 3 partitions
        final NewTopic mytopic = new NewTopic(TOPIC, 3, (short) 1);
        kafkaAdminClient.createTopics(Collections.singleton(mytopic));
    }

    @BeforeEach
    public void init() {

        // setup the producer
        Properties props = getDefaultProperties();
        producer = new org.apache.kafka.clients.producer.KafkaProducer<>(props);
        MockConsumerInterceptor.recordsCaptured.clear();
    }

    @AfterEach
    public void after() {
        if (producer != null) {
            producer.close();
        }
        // clean all test topics
        kafkaAdminClient.deleteTopics(Collections.singletonList(TOPIC)).all();
    }

    @Test
    void testCamel20563TestFix() throws Exception {
        to.reset();
        to.expectedMessageCount(13);
        to.expectedBodiesReceivedInAnyOrder("1", "2", "3", "4", "5", "ERROR",
                "6", "7", "ERROR", "8", "9", "10", "11");

        context.getRouteController().stopRoute(ROUTE_ID);

        this.publishMessagesToKafka();

        context.getRouteController().startRoute(ROUTE_ID);

        // let test run for awhile
        Awaitility.await()
                .timeout(10, TimeUnit.SECONDS)
                .pollDelay(8, TimeUnit.SECONDS)
                .untilAsserted(() -> assertTrue(true));

        to.assertIsSatisfied();

        printAllThreads();
        int heartbeatThreadCount = countHeartbeatThreads();
        LOG.info("Number of heartbeat-threads is: {}", heartbeatThreadCount);
        assertEquals(CONSUMER_COUNT, heartbeatThreadCount, "Heartbeat-thread count should match consumer count");

    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                onException(RuntimeException.class)
                        .handled(false)
                        .process(exchange -> {
                            doCommitOffset(exchange);
                        })
                        .end();

                from(from)
                        .routeId(ROUTE_ID)
                        .autoStartup(false)
                        .process(exchange -> {
                            LOG.debug(CamelKafkaUtil.buildKafkaLogMessage("Consuming", exchange, true));
                        })
                        // capturing all of the payloads
                        .to(to)
                        .process(exchange -> {
                            ifIsPayloadWithErrorThrowException(exchange);
                        })
                        .process(exchange -> {
                            doCommitOffset(exchange);
                        })
                        .end();
            }
        };
    }

    private void ifIsPayloadWithErrorThrowException(Exchange exchange) {
        String payload = exchange.getMessage().getBody(String.class);
        if (payload.equals("ERROR")) {
            throw new RuntimeException("NON RETRY ERROR TRIGGERED BY TEST");
        }
    }

    private void publishMessagesToKafka() {
        final List<String> producedRecords = List.of("1", "2", "3", "4", "5", "ERROR",
                "6", "7", "ERROR", "8", "9", "10", "11");

        producedRecords.forEach(v -> {
            ProducerRecord<String, String> data = new ProducerRecord<>(TOPIC, null, v);
            producer.send(data);
        });

    }

    protected int countHeartbeatThreads() throws ClassNotFoundException {
        Set<Thread> threads = Thread.getAllStackTraces().keySet();
        int count = 0;

        for (Thread t : threads) {
            if (t.getName().contains("heartbeat")) {
                count++;
            }
        }
        return count;
    }

    protected void printAllThreads() {
        Set<Thread> threads = Thread.getAllStackTraces().keySet();
        LOG.info("****** Running Threads ***********");
        LOG.info("Name | State | Priority | isDaemon");
        for (Thread t : threads) {
            LOG.info("{} | {} | {} | {}", t.getName(), t.getState(), t.getPriority(), t.isDaemon());
        }
    }

    private void doCommitOffset(Exchange exchange) {
        LOG.debug(CamelKafkaUtil.buildKafkaLogMessage("Committing", exchange, true));
        KafkaManualCommit manual = exchange.getMessage()
                .getHeader(KafkaConstants.MANUAL_COMMIT, KafkaManualCommit.class);
        if (Objects.nonNull(manual)) {
            manual.commit();
        } else {
            LOG.error("KafkaManualCommit is MISSING");
        }
    }

}
