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
package org.apache.camel.component.kafka.share.integration;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.share.KafkaShareConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.kafka.services.KafkaService;
import org.apache.camel.test.infra.kafka.services.KafkaServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.camel.builder.Builder.body;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The kafka-share consumer against a Kafka broker with share groups. Only the Apache Kafka container, the default, is
 * configured for share groups.
 */
@DisabledIfSystemProperty(named = "kafka.instance.type", matches = "(?!local-kafka-container$).+",
                          disabledReason = "Share groups are only configured on the Apache Kafka container")
class KafkaShareConsumerIT extends CamelTestSupport {

    private static final String WORK_TOPIC = "share-work";
    private static final String RETRY_TOPIC = "share-retry";
    private static final String REJECT_TOPIC = "share-reject";
    private static final Map<String, String> GROUPS = Map.of(
            WORK_TOPIC, "share-work-group", RETRY_TOPIC, "share-retry-group", REJECT_TOPIC, "share-reject-group");

    @RegisterExtension
    static KafkaService service = KafkaServiceFactory.createSingletonService();

    private static Admin admin;

    private final Set<String> workThreads = ConcurrentHashMap.newKeySet();

    @BeforeAll
    static void createTopicsAndGroups() throws Exception {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, service.getBootstrapServers());
        admin = Admin.create(props);

        // a single partition: the consumers of a share group share it
        admin.createTopics(GROUPS.keySet().stream().map(topic -> new NewTopic(topic, 1, (short) 1)).toList())
                .all().get(30, TimeUnit.SECONDS);

        // a new share group starts from the latest records by default, make it read the records produced before
        Map<ConfigResource, Collection<AlterConfigOp>> configs = new HashMap<>();
        for (String group : GROUPS.values()) {
            configs.put(new ConfigResource(ConfigResource.Type.GROUP, group), List.of(new AlterConfigOp(
                    new ConfigEntry("share.auto.offset.reset", "earliest"), AlterConfigOp.OpType.SET)));
        }
        admin.incrementalAlterConfigs(configs).all().get(30, TimeUnit.SECONDS);
    }

    @AfterAll
    static void closeAdmin() {
        if (admin != null) {
            admin.close();
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        String brokers = service.getBootstrapServers();
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("kafka-share:" + WORK_TOPIC + "?brokers=" + brokers + "&groupId=" + GROUPS.get(WORK_TOPIC)
                     + "&consumersCount=3&maxPollRecords=5&acquireMode=record_limit")
                        .routeId("work")
                        .process(exchange -> workThreads.add(Thread.currentThread().getName()))
                        .to("mock:work");

                // fails the first delivery: the record is released and delivered again
                from("kafka-share:" + RETRY_TOPIC + "?brokers=" + brokers + "&groupId=" + GROUPS.get(RETRY_TOPIC))
                        .routeId("retry")
                        .errorHandler(noErrorHandler())
                        .to("mock:deliveries")
                        .filter(header(KafkaShareConstants.DELIVERY_COUNT).isEqualTo(1))
                        .throwException(new IllegalStateException("First delivery fails"))
                        .end()
                        .to("mock:retried");

                // the route rejects the record: it is not delivered again
                from("kafka-share:" + REJECT_TOPIC + "?brokers=" + brokers + "&groupId=" + GROUPS.get(REJECT_TOPIC))
                        .routeId("reject")
                        .setHeader(KafkaShareConstants.ACKNOWLEDGE, constant("REJECT"))
                        .to("mock:rejected");
            }
        };
    }

    @Test
    void consumersOfAShareGroupShareASinglePartition() throws Exception {
        MockEndpoint work = getMockEndpoint("mock:work");
        work.expectedMessageCount(60);
        work.expectsNoDuplicates(body());

        produce(WORK_TOPIC, 60);

        work.assertIsSatisfied(60000);
        assertThat(work.getReceivedExchanges()).extracting(e -> e.getMessage().getHeader(KafkaShareConstants.PARTITION))
                .containsOnly(0);
    }

    @Test
    void releasedRecordIsDeliveredAgain() throws Exception {
        MockEndpoint deliveries = getMockEndpoint("mock:deliveries");
        deliveries.expectedMessageCount(2);
        MockEndpoint retried = getMockEndpoint("mock:retried");
        retried.expectedBodiesReceived("message-0");
        retried.expectedHeaderReceived(KafkaShareConstants.DELIVERY_COUNT, (short) 2);

        produce(RETRY_TOPIC, 1);

        MockEndpoint.assertIsSatisfied(60, TimeUnit.SECONDS, deliveries, retried);
    }

    @Test
    void rejectedRecordIsNotDeliveredAgain() throws Exception {
        MockEndpoint rejected = getMockEndpoint("mock:rejected");
        rejected.expectedBodiesReceived("message-0");
        rejected.setResultWaitTime(60000);
        // a delivery count of 1, and no other delivery within the assert period
        rejected.expectedHeaderReceived(KafkaShareConstants.DELIVERY_COUNT, (short) 1);
        rejected.setAssertPeriod(5000);

        produce(REJECT_TOPIC, 1);

        rejected.assertIsSatisfied();
        Exchange exchange = rejected.getReceivedExchanges().get(0);
        assertThat(exchange.getMessage().getHeader(KafkaShareConstants.TOPIC)).isEqualTo(REJECT_TOPIC);
    }

    private static void produce(String topic, int count) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, service.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            for (int i = 0; i < count; i++) {
                producer.send(new ProducerRecord<>(topic, "key-" + i, "message-" + i));
            }
            producer.flush();
        }
    }
}
