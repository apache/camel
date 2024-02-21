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

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.MockConsumerInterceptor;
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
 * this will test breakOnFirstError functionality and the issue that was surfaced in CAMEL-19894 regarding failure to
 * correctly commit the offset in a batch using the Synch Commit Manager
 *
 * mimics the reproduction of the problem in https://github.com/Krivda/camel-bug-reproduction
 */
@Tags({ @Tag("breakOnFirstError") })
@EnabledOnOs(value = { OS.LINUX, OS.MAC, OS.FREEBSD, OS.OPENBSD, OS.WINDOWS },
             architectures = { "amd64", "aarch64", "s390x" },
             disabledReason = "This test does not run reliably on ppc64le")
class KafkaBreakOnFirstErrorSeekIssueIT extends BaseEmbeddedKafkaTestSupport {

    public static final String ROUTE_ID = "breakOnFirstError-19894";
    public static final String TOPIC = "breakOnFirstError-19894";

    private static final Logger LOG = LoggerFactory.getLogger(KafkaBreakOnFirstErrorSeekIssueIT.class);

    @EndpointInject("mock:result")
    private MockEndpoint to;

    private org.apache.kafka.clients.producer.KafkaProducer<String, String> producer;

    @BeforeAll
    public static void setupTopic() {
        if (kafkaAdminClient == null) {
            kafkaAdminClient = KafkaAdminUtil.createAdminClient(service);
        }

        // create the topic w/ 2 partitions
        final NewTopic mytopic = new NewTopic(TOPIC, 2, (short) 1);
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
    void testCamel19894TestFix() throws Exception {
        to.reset();
        // will consume the payloads from partition 0
        // and will continually retry the payload with "5"
        to.expectedMessageCount(4);
        to.expectedBodiesReceived("1", "2", "3", "4");

        contextExtension.getContext().getRouteController().stopRoute(ROUTE_ID);

        this.publishMessagesToKafka();

        contextExtension.getContext().getRouteController().startRoute(ROUTE_ID);

        // let test run for awhile
        Awaitility.await()
                .timeout(10, TimeUnit.SECONDS)
                .pollDelay(8, TimeUnit.SECONDS)
                .untilAsserted(() -> assertTrue(true));

        // the replaying of the message with an error
        // will prevent other paylods from being
        // processed
        to.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                from("kafka:" + TOPIC
                     + "?groupId=" + ROUTE_ID
                     + "&autoOffsetReset=earliest"
                     + "&autoCommitEnable=false"
                     + "&allowManualCommit=true"
                     + "&breakOnFirstError=true"
                     + "&maxPollRecords=8"
                     + "&pollTimeoutMs=1000"
                     + "&keyDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
                     + "&valueDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
                // Synch Commit Manager
                     + "&kafkaManualCommitFactory=#class:org.apache.camel.component.kafka.consumer.DefaultKafkaManualCommitFactory"
                     + "&interceptorClasses=org.apache.camel.component.kafka.MockConsumerInterceptor")
                        .routeId(ROUTE_ID)
                        .autoStartup(false)
                        .process(exchange -> {
                            LOG.debug(CamelKafkaUtil.buildKafkaLogMessage("Consuming", exchange, true));
                        })
                        .process(exchange -> {
                            ifIsFifthRecordThrowException(exchange);
                        })
                        .to(to)
                        .end();
            }
        };
    }

    private void ifIsFifthRecordThrowException(Exchange e) {
        if (e.getMessage().getBody().equals("5")) {
            throw new RuntimeException("ERROR_TRIGGERED_BY_TEST");
        }
    }

    private void publishMessagesToKafka() {
        final List<String> producedRecordsPartition1 = List.of("5", "6", "7", "8", "9", "10", "11");
        final List<String> producedRecordsPartition0 = List.of("1", "2", "3", "4");

        producedRecordsPartition1.forEach(v -> {
            ProducerRecord<String, String> data = new ProducerRecord<>(TOPIC, 1, null, null, v);
            producer.send(data);
        });

        producedRecordsPartition0.forEach(v -> {
            ProducerRecord<String, String> data = new ProducerRecord<>(TOPIC, 0, null, null, v);
            producer.send(data);
        });
    }

}
