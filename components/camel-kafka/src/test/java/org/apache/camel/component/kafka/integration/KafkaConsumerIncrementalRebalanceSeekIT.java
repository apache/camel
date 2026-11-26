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

import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.CooperativeStickyAssignor;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * With a cooperative assignor or the consumer group protocol, a rebalance assigns only the partitions it adds to a
 * consumer, and the consumer keeps reading the other partitions from where it is. seekTo must only move the added
 * partitions: before CAMEL-27551 it moved the kept partitions back to the beginning too, and their records were read
 * again.
 */
class KafkaConsumerIncrementalRebalanceSeekIT extends BaseKafkaTestSupport {

    private static final int RECORDS_PER_PARTITION = 5;
    private static final String MARKER = "marker";

    private String topic;
    private KafkaProducer<String, String> producer;

    @Override
    protected RouteBuilder createRouteBuilder() {
        // the routes are added by each test
        return new RouteBuilder() {
            @Override
            public void configure() {
            }
        };
    }

    @AfterEach
    void after() {
        if (producer != null) {
            producer.close();
        }
        if (topic != null) {
            kafkaAdminClient.deleteTopics(List.of(topic));
        }
    }

    @Test
    void cooperativeAssignorKeepsThePositionOfTheKeptPartition() throws Exception {
        runScenario("partitionAssignor=" + CooperativeStickyAssignor.class.getName());
    }

    @Test
    void consumerGroupProtocolKeepsThePositionOfTheKeptPartition() throws Exception {
        runScenario("groupProtocol=consumer");
    }

    private void runScenario(String options) throws Exception {
        String id = UUID.randomUUID().toString();
        topic = "incremental-rebalance-" + id;
        kafkaAdminClient.createTopics(List.of(new NewTopic(topic, 2, (short) 1))).all().get(30, TimeUnit.SECONDS);
        producer = new KafkaProducer<>(getDefaultProperties());

        for (int partition = 0; partition < 2; partition++) {
            for (int i = 0; i < RECORDS_PER_PARTITION; i++) {
                producer.send(new ProducerRecord<>(topic, partition, null, "p" + partition + "-" + i)).get();
            }
        }

        CamelContext context = contextExtension.getContext();
        String uri = "kafka:" + topic + "?groupId=group-" + id + "&seekTo=BEGINNING&" + options;
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(uri).routeId("a-" + id).autoStartup(false).to("mock:a-" + id);
                from(uri).routeId("b-" + id).autoStartup(false).to("mock:b-" + id);
            }
        });
        MockEndpoint a = context.getEndpoint("mock:a-" + id, MockEndpoint.class);
        MockEndpoint b = context.getEndpoint("mock:b-" + id, MockEndpoint.class);

        // consumer a owns both partitions and reads all the records
        context.getRouteController().startRoute("a-" + id);
        await().atMost(30, TimeUnit.SECONDS).until(() -> a.getReceivedCounter() >= 2 * RECORDS_PER_PARTITION);

        // consumer b joins: the rebalance moves one partition to b, a keeps the other one
        context.getRouteController().startRoute("b-" + id);
        await().atMost(60, TimeUnit.SECONDS).until(() -> b.getReceivedCounter() >= RECORDS_PER_PARTITION);

        // a marker at the end of each partition: once both are read, a has read anything it was going to read again
        for (int partition = 0; partition < 2; partition++) {
            producer.send(new ProducerRecord<>(topic, partition, null, MARKER)).get();
        }
        await().atMost(30, TimeUnit.SECONDS)
                .until(() -> bodies(a).contains(MARKER) && bodies(b).contains(MARKER));

        List<String> received = bodies(a);
        assertEquals(2 * RECORDS_PER_PARTITION + 1, received.size(),
                "consumer a must read each record of its kept partition once, but received " + received);
        assertEquals(received.size(), new HashSet<>(received).size(), "duplicates in " + received);
    }

    private static List<String> bodies(MockEndpoint mock) {
        return mock.getReceivedExchanges().stream().map(e -> e.getMessage().getBody(String.class)).toList();
    }
}
