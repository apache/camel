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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.integration.common.KafkaTestUtil;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.kafka.clients.admin.MemberDescription;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.Uuid;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Static membership with more than one consumer: each consumer joins the group as its own static member, so the
 * consumers do not fence each other, and the records of every partition are consumed.
 */
class KafkaConsumerGroupInstanceIdIT extends BaseKafkaTestSupport {

    private static final String TOPIC = "group-instance-id-" + Uuid.randomUuid();
    private static final String GROUP = TOPIC + "-group";
    private static final int PARTITIONS = 3;

    @Override
    protected RouteBuilder createRouteBuilder() {
        // the route is added by the test, once the topic has its partitions
        return new RouteBuilder() {
            @Override
            public void configure() {
                // no routes
            }
        };
    }

    @Test
    void eachConsumerJoinsAsItsOwnStaticMember() throws Exception {
        kafkaAdminClient.createTopics(List.of(new NewTopic(TOPIC, PARTITIONS, (short) 1))).all().get(30, TimeUnit.SECONDS);

        CamelContext context = contextExtension.getContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("kafka:" + TOPIC + "?groupId=" + GROUP + "&groupInstanceId=camel&consumersCount=" + PARTITIONS
                     + "&autoOffsetReset=earliest&pollTimeoutMs=1000")
                        .routeId("static-members")
                        .to(KafkaTestUtil.MOCK_RESULT);
            }
        });

        await().atMost(60, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(Set.of("camel-0", "camel-1", "camel-2"), groupInstanceIds()));

        MockEndpoint result = contextExtension.getMockEndpoint(KafkaTestUtil.MOCK_RESULT);
        result.expectedBodiesReceivedInAnyOrder("record-0", "record-1", "record-2");
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(getDefaultProperties())) {
            for (int partition = 0; partition < PARTITIONS; partition++) {
                producer.send(new ProducerRecord<>(TOPIC, partition, null, "record-" + partition)).get(30, TimeUnit.SECONDS);
            }
        }
        MockEndpoint.assertIsSatisfied(context, 30, TimeUnit.SECONDS);
    }

    private Set<String> groupInstanceIds() throws Exception {
        return kafkaAdminClient.describeConsumerGroups(List.of(GROUP)).describedGroups().get(GROUP)
                .get(10, TimeUnit.SECONDS)
                .members().stream()
                .map(MemberDescription::groupInstanceId)
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());
    }
}
