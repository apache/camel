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

import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConsumer;
import org.apache.camel.component.kafka.KafkaFetchRecords;
import org.apache.camel.component.kafka.MockConsumerInterceptor;
import org.apache.camel.component.kafka.integration.common.KafkaTestUtil;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.platform.commons.function.Try;
import org.junit.platform.commons.util.ReflectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This IT is based on {@link KafkaConsumerFullIT}
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class KafkaConsumerStopIT extends BaseEmbeddedKafkaTestSupport {

    public static final String TOPIC = "test-full";

    private static final Logger LOG = LoggerFactory.getLogger(KafkaConsumerStopIT.class);

    private org.apache.kafka.clients.producer.KafkaProducer<String, String> producer;

    @BeforeEach
    public void before() {
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

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                from("kafka:" + TOPIC
                     + "?groupId=KafkaConsumerFullIT&autoOffsetReset=earliest&keyDeserializer=org.apache.kafka.common.serialization.StringDeserializer&"
                     + "valueDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
                     + "&autoCommitIntervalMs=1000&pollTimeoutMs=1000&autoCommitEnable=true&interceptorClasses=org.apache.camel.component.kafka.MockConsumerInterceptor")
                        .process(exchange -> LOG.trace("Captured on the processor: {}",
                                exchange.getMessage().getBody()))
                        .routeId("full-it").to(KafkaTestUtil.MOCK_RESULT);
            }
        };
    }

    @Test
    public void kafkaClientConsumerClosedWhenKafkaRouteStopped() throws Exception {
        MockEndpoint to = contextExtension.getMockEndpoint(KafkaTestUtil.MOCK_RESULT);

        // given: kafka consumer route
        to.expectedBodiesReceivedInAnyOrder("message");
        ProducerRecord<String, String> data = new ProducerRecord<>(TOPIC, "1", "message");
        producer.send(data);
        to.assertIsSatisfied(3000);

        // and: org.apache.kafka.clients.consumer.KafkaConsumer
        org.apache.kafka.clients.consumer.KafkaConsumer kafkaClientConsumer = getKafkaClientConsumer();

        // when: kafka consumer route stopped
        contextExtension.getContext().getRouteController().stopRoute("full-it");

        // then: org.apache.kafka.clients.consumer.KafkaConsumer closed
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertTrue(kafkaClientConsumerClosed(kafkaClientConsumer),
                        "org.apache.kafka.clients.consumer.KafkaConsumer should be closed"));
    }

    private org.apache.kafka.clients.consumer.KafkaConsumer getKafkaClientConsumer() throws Exception {
        KafkaConsumer kafkaConsumer = (KafkaConsumer) contextExtension.getContext().getRoute("full-it").getConsumer();
        Try<Object> tasksTry = ReflectionUtils.tryToReadFieldValue(KafkaConsumer.class, "tasks", kafkaConsumer);
        KafkaFetchRecords kafkaFetchRecords = ((List<KafkaFetchRecords>) tasksTry.get()).get(0);
        Try<Object> kafkaClientConsumerTry
                = ReflectionUtils.tryToReadFieldValue(KafkaFetchRecords.class, "consumer", kafkaFetchRecords);
        return (org.apache.kafka.clients.consumer.KafkaConsumer) kafkaClientConsumerTry.get();
    }

    private static boolean kafkaClientConsumerClosed(org.apache.kafka.clients.consumer.KafkaConsumer kafkaClientConsumer)
            throws Exception {
        Try<Object> closedTry = ReflectionUtils.tryToReadFieldValue(org.apache.kafka.clients.consumer.KafkaConsumer.class,
                "closed", kafkaClientConsumer);
        return (boolean) closedTry.get();
    }

}
