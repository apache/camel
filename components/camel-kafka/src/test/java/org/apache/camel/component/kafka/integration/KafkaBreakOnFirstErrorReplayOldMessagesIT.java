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
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.MockConsumerInterceptor;
import org.apache.camel.component.kafka.consumer.KafkaManualCommit;
import org.apache.camel.component.kafka.integration.common.KafkaAdminUtil;
import org.apache.camel.component.kafka.testutil.CamelKafkaUtil;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * this will test breakOnFirstError functionality and the issue that was surfaced in CAMEL-20044 regarding incorrectly
 * handling the offset commit resulting in replaying messages
 *
 * mimics the reproduction of the problem in https://github.com/CodeSmell/CamelKafkaOffset
 */
@Tags({ @Tag("breakOnFirstError") })
@EnabledOnOs(value = { OS.LINUX, OS.MAC, OS.FREEBSD, OS.OPENBSD, OS.WINDOWS },
             architectures = { "amd64", "aarch64", "s390x" },
             disabledReason = "This test does not run reliably on ppc64le")
class KafkaBreakOnFirstErrorReplayOldMessagesIT extends BaseEmbeddedKafkaTestSupport {

    public static final String ROUTE_ID = "breakOnFirstError-20044";
    public static final String TOPIC = "breakOnFirstError-20044";

    private static final Logger LOG = LoggerFactory.getLogger(KafkaBreakOnFirstErrorReplayOldMessagesIT.class);

    @EndpointInject("mock:result")
    private MockEndpoint to;

    private org.apache.kafka.clients.producer.KafkaProducer<String, String> producer;

    @BeforeAll
    public static void setupTopic() {
        if (kafkaAdminClient == null) {
            kafkaAdminClient = KafkaAdminUtil.createAdminClient(service);
        }

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
    void testCamel20044TestFix() throws Exception {
        to.reset();
        to.expectedMessageCount(13);
        to.expectedBodiesReceivedInAnyOrder("1", "2", "3", "4", "5", "ERROR",
                "6", "7", "ERROR", "8", "9", "10", "11");

        contextExtension.getContext().getRouteController().stopRoute(ROUTE_ID);

        this.publishMessagesToKafka();

        contextExtension.getContext().getRouteController().startRoute(ROUTE_ID);

        // let test run for awhile
        Awaitility.await()
                .timeout(10, TimeUnit.SECONDS)
                .pollDelay(8, TimeUnit.SECONDS)
                .untilAsserted(() -> assertTrue(true));

        to.assertIsSatisfied();
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

                from("kafka:" + TOPIC
                     + "?groupId=" + ROUTE_ID
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
