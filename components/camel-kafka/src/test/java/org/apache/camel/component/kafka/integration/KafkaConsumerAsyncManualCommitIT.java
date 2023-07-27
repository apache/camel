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

import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.AggregationStrategies;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.consumer.DefaultKafkaManualAsyncCommitFactory;
import org.apache.camel.component.kafka.consumer.KafkaManualCommit;
import org.apache.camel.component.kafka.consumer.KafkaManualCommitFactory;
import org.apache.camel.component.kafka.integration.common.KafkaTestUtil;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class KafkaConsumerAsyncManualCommitIT extends BaseEmbeddedKafkaTestSupport {
    public static final String TOPIC = "testManualCommitTest";

    private static final Logger LOG = LoggerFactory.getLogger(KafkaConsumerAsyncManualCommitIT.class);

    @BindToRegistry("testFactory")
    private final KafkaManualCommitFactory manualCommitFactory = new DefaultKafkaManualAsyncCommitFactory();

    private final CamelContext context = contextExtension.getContext();

    private org.apache.kafka.clients.producer.KafkaProducer<String, String> producer;

    private volatile int failCount;

    @BeforeEach
    public void before() {
        Properties props = KafkaTestUtil.getDefaultProperties(service);
        producer = new org.apache.kafka.clients.producer.KafkaProducer<>(props);
    }

    @AfterEach
    public void after() {
        if (producer != null) {
            producer.close();
        }
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                String uri = "kafka:" + TOPIC + "?brokers=" + service.getBootstrapServers()
                             + "&groupId=KafkaConsumerAsyncManualCommitIT&pollTimeoutMs=1000&autoCommitEnable=false"
                             + "&allowManualCommit=true&autoOffsetReset=earliest&kafkaManualCommitFactory=#testFactory";

                from(uri).routeId("foo").to("direct:aggregate");
                // With sync manual commit, this would throw a concurrent modification exception
                // It can be used in aggregator with completion timeout/interval for instance
                // WARN: records from one partition must be processed by one unique thread
                from("direct:aggregate").routeId("aggregate").to(KafkaTestUtil.MOCK_RESULT)
                        .aggregate()
                        .constant(true)
                        .completionTimeout(1)
                        .aggregationStrategy(AggregationStrategies.groupedExchange())
                        .split().body()
                        .process(e -> {
                            KafkaManualCommit manual = e.getMessage().getBody(Exchange.class)
                                    .getMessage().getHeader(KafkaConstants.MANUAL_COMMIT, KafkaManualCommit.class);
                            assertNotNull(manual);

                            try {
                                manual.commit();
                            } catch (Exception commitException) {
                                LOG.error("Failed to commit: {}", commitException.getMessage(), commitException);
                                failCount++;
                            }
                        });
                from(uri).routeId("bar").autoStartup(false).to(KafkaTestUtil.MOCK_RESULT_BAR);
            }
        };
    }

    @DisplayName("Tests that LAST_RECORD_BEFORE_COMMIT header includes a value")
    @Order(1)
    @Test
    void testLastRecordBeforeCommitHeader() {
        MockEndpoint to = contextExtension.getMockEndpoint(KafkaTestUtil.MOCK_RESULT);

        to.expectedMessageCount(5);
        to.expectedBodiesReceivedInAnyOrder("message-0", "message-1", "message-2", "message-3", "message-4");

        to.allMessages().header(KafkaConstants.LAST_RECORD_BEFORE_COMMIT).isNotNull();

        for (int k = 0; k < 5; k++) {
            String msg = "message-" + k;
            ProducerRecord<String, String> data = new ProducerRecord<>(TOPIC, "1", msg);
            producer.send(data);
        }

        Awaitility.await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> to.assertIsSatisfied());

        List<Exchange> exchangeList = to.getExchanges();
        assertEquals(5, exchangeList.size());
        assertEquals(true, exchangeList.get(4).getMessage().getHeader(KafkaConstants.LAST_RECORD_BEFORE_COMMIT, Boolean.class));
    }

    @Order(2)
    @Test
    void kafkaManualCommit() throws Exception {
        MockEndpoint to = contextExtension.getMockEndpoint(KafkaTestUtil.MOCK_RESULT);

        // Second step: We shut down our route, we expect nothing will be recovered by our route
        context.getRouteController().stopRoute("foo");
        to.expectedMessageCount(0);

        // Third step: While our route is stopped, we send 3 records more to Kafka test topic
        for (int k = 5; k < 8; k++) {
            String msg = "message-" + k;
            ProducerRecord<String, String> data = new ProducerRecord<>(TOPIC, "1", msg);
            producer.send(data);
        }

        to.assertIsSatisfied(3000);
    }

    @Order(3)
    @Test
    void testResumeFromTheRightPoint() throws Exception {
        MockEndpoint to = contextExtension.getMockEndpoint(KafkaTestUtil.MOCK_RESULT);

        // Fourth step: We start again our route, since we have been committing the offsets from the first step,
        // we will expect to consume from the latest committed offset (i.e., from offset 5)
        context.getRouteController().startRoute("foo");

        to.expectedMessageCount(3);
        to.expectedBodiesReceivedInAnyOrder("message-5", "message-6", "message-7");

        Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> to.assertIsSatisfied());

        assertEquals(0, failCount, "There should have been 0 commit failures");
    }

}
