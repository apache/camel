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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.MockConsumerInterceptor;
import org.apache.camel.component.kafka.integration.common.KafkaAdminUtil;
import org.apache.camel.component.kafka.testutil.CamelKafkaUtil;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.Uuid;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * this will test breakOnFirstError functionality and the issue that was surfaced in CAMEL-19894 regarding failure to
 * correctly commit the offset in a batch using the Synch Commit Manager
 *
 * mimics the reproduction of the problem in https://github.com/Krivda/camel-bug-reproduction
 */
@Tags({ @Tag("breakOnFirstError") })

class KafkaBreakOnFirstErrorSeekIssueIT extends BaseKafkaTestSupport {

    public static final String ROUTE_ID = "breakOnFirstError-19894" + Uuid.randomUuid().toString();
    public static final String TOPIC = "breakOnFirstError-19894" + Uuid.randomUuid().toString();
    public static final int PARTITION_COUNT = 2;
    public static final int CONSUMERS_COUNT = 4;  // Set to more than partition count. In case first one is stuck on breakOnFirstError,
                                                // others can process the second partition
                                                // IDEALLY, 2 consumers should be sufficient, but flakiness was observed with 2

    private static final Logger LOG = LoggerFactory.getLogger(KafkaBreakOnFirstErrorSeekIssueIT.class);

    private final List<String> errorPayloads = new CopyOnWriteArrayList<>();

    @EndpointInject("mock:result")
    private MockEndpoint to;

    private org.apache.kafka.clients.producer.KafkaProducer<String, String> producer;

    @BeforeAll
    public static void setupTopic() {
        if (kafkaAdminClient == null) {
            kafkaAdminClient = KafkaAdminUtil.createAdminClient(service);
        }
    }

    @BeforeEach
    public void init() {

        // setup the producer
        Properties props = getDefaultProperties();
        producer = new org.apache.kafka.clients.producer.KafkaProducer<>(props);
        MockConsumerInterceptor.recordsCaptured.clear();
        // create the topic w/ more than 1 partitions - moved here from BeforeAll
        final NewTopic mytopic = new NewTopic(TOPIC, PARTITION_COUNT, (short) 1);
        CreateTopicsResult r = kafkaAdminClient.createTopics(Collections.singleton(mytopic));

        // This wait is necessary to ensure that required number of partitions are actually created
        Awaitility.await()
                .timeout(180, TimeUnit.SECONDS)
                .pollDelay(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertTrue(r.numPartitions(TOPIC).isDone()));

    }

    @AfterEach
    public void after() {
        if (producer != null) {
            producer.close();
        }
        // clean all test topics
        kafkaAdminClient.deleteTopics(Collections.singletonList(TOPIC)).all();
        // if more tests are added later, then also check DeleteTopicResult::all().isDone() to avoid concurrency issues
    }

    @Test
    void testCamel19894TestFix() throws Exception {
        to.reset();
        // will consume the payloads from partition 0 & 1
        // and will continually retry the payload with "8" and "3"
        // 2 messages from partition 0 and 3 from partition 1 are read before exceptions are raised.
        to.expectedMessageCount(5);

        to.expectedBodiesReceivedInAnyOrder("5", "6", "7", "1", "2");
        // Messages 1, 2 are read in-order from partition 0,
        // and 5, 6, 7 are read in-order from partition 1

        contextExtension.getContext().getRouteController().stopRoute(ROUTE_ID);

        assertEquals(PARTITION_COUNT, producer.partitionsFor(TOPIC).size());
        //Test relies on multiple partitions but expects the poller to stop reading after the errored message
        // Increase the delay in setupTopic if this assert fails too frequently

        this.publishMessagesToKafka();

        contextExtension.getContext().getRouteController().startRoute(ROUTE_ID);

        Awaitility.await()
                .atMost(180, TimeUnit.SECONDS)
                .pollDelay(5, TimeUnit.SECONDS)
                .until(() -> (errorPayloads.contains("3") && errorPayloads.contains("8") && errorPayloads.size() > 3));
        // the replaying of the message 3 and 8 with an error
        // will prevent other payloads from the same partition being
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
                     + "&consumersCount=" + CONSUMERS_COUNT
                     + "&heartbeatIntervalMs=1000"  //added for CAMEL-20722, after consumersCount
                     + "&metadataMaxAgeMs=1000"
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
        if (e.getMessage().getBody().equals("8") || e.getMessage().getBody().equals("3")) {
            //Message 3 from partition 0, and 8 from partition 1 will be retried indefinitely
            errorPayloads.add(e.getMessage().getBody(String.class));
            throw new RuntimeException("ERROR_TRIGGERED_BY_TEST");
        }
    }

    private void publishMessagesToKafka() {
        final List<String> producedRecordsPartition1 = List.of("5", "6", "7", "8", "9", "10", "11");
        final List<String> producedRecordsPartition0 = List.of("1", "2", "3", "4");

        producedRecordsPartition0.forEach(v -> {
            ProducerRecord<String, String> data = new ProducerRecord<>(TOPIC, 0, "k0", v); //CAMEL-20680: kept explicit partition 0, added key.
            producer.send(data);
        });

        producedRecordsPartition1.forEach(v -> {
            ProducerRecord<String, String> data = new ProducerRecord<>(TOPIC, 1, "k1", v);
            producer.send(data);
        });  //CAMEL-20680: restored loop that publishes to partition1, but with reduced execution time
        // See changes in setupTopic() and testCamel19894TestFix just before publishMessagesToKafka().
        // This loop is required by the original fix for CAMEL-19894

    }

}
