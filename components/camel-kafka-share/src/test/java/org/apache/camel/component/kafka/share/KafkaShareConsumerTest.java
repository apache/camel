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
package org.apache.camel.component.kafka.share;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.kafka.clients.consumer.AcknowledgeType;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ShareConsumer;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.kafka.share.RecordingShareConsumer.record;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The acknowledgement of each record, derived from the outcome of its exchange, with a share consumer that records the
 * acknowledgements instead of a broker.
 */
class KafkaShareConsumerTest extends CamelTestSupport {

    private static final String TOPIC = "orders";

    private final Map<String, RecordingShareConsumer> consumers = new ConcurrentHashMap<>();

    @BindToRegistry("recordingFactory")
    private final KafkaShareClientFactory factory = new KafkaShareClientFactory() {
        @Override
        public ShareConsumer<Object, Object> getShareConsumer(Properties kafkaProps) {
            return consumers.computeIfAbsent(kafkaProps.getProperty(ConsumerConfig.GROUP_ID_CONFIG),
                    groupId -> new RecordingShareConsumer());
        }

        @Override
        public String getBrokers(KafkaShareConfiguration configuration) {
            return "localhost:9092";
        }
    };

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                onException(IllegalStateException.class).handled(true).to("mock:handled");

                from("kafka-share:" + TOPIC + "?brokers=localhost:9092&groupId=outcome&pollTimeoutMs=100").routeId("outcome")
                        .choice()
                        .when(body().isEqualTo("fail")).throwException(new IllegalArgumentException("Forced"))
                        .when(body().isEqualTo("handled")).throwException(new IllegalStateException("Handled"))
                        .when(body().isEqualTo("reject")).setHeader(KafkaShareConstants.ACKNOWLEDGE, constant("reject"))
                        .when(body().isEqualTo("invalid")).setHeader(KafkaShareConstants.ACKNOWLEDGE, constant("MAYBE"))
                        .end()
                        .to("mock:result");

                from("kafka-share:" + TOPIC
                     + "?brokers=localhost:9092&groupId=reject-on-failure&pollTimeoutMs=100&onFailure=REJECT")
                        .routeId("rejectOnFailure")
                        .throwException(new IllegalArgumentException("Forced"));
            }
        };
    }

    @Test
    void acknowledgesEachRecordFromTheOutcomeOfItsExchange() throws Exception {
        RecordingShareConsumer consumer = subscribedConsumer("outcome");
        List<String> bodies = List.of("ok", "fail", "handled", "reject", "invalid");
        for (int i = 0; i < bodies.size(); i++) {
            consumer.addRecord(record(TOPIC, i, null, bodies.get(i), (short) 1));
        }

        await().atMost(10, TimeUnit.SECONDS).until(() -> consumer.getAcknowledgements().size() == bodies.size());

        assertThat(consumer.getAcknowledgements()).extracting(RecordingShareConsumer.Acknowledgement::type)
                .containsExactly(AcknowledgeType.ACCEPT, AcknowledgeType.RELEASE, AcknowledgeType.ACCEPT,
                        AcknowledgeType.REJECT, AcknowledgeType.ACCEPT);
        assertThat(consumer.getCommits()).isPositive();
    }

    @Test
    void failedExchangeIsAcknowledgedWithOnFailure() {
        RecordingShareConsumer consumer = subscribedConsumer("reject-on-failure");
        consumer.addRecord(record(TOPIC, 0, null, "fail", (short) 1));

        await().atMost(10, TimeUnit.SECONDS).until(() -> !consumer.getAcknowledgements().isEmpty());

        assertThat(consumer.getAcknowledgements()).extracting(RecordingShareConsumer.Acknowledgement::type)
                .containsExactly(AcknowledgeType.REJECT);
    }

    @Test
    void recordIsMappedToTheMessage() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("hello");
        result.expectedHeaderReceived(KafkaShareConstants.TOPIC, TOPIC);
        result.expectedHeaderReceived(KafkaShareConstants.PARTITION, 0);
        result.expectedHeaderReceived(KafkaShareConstants.OFFSET, 7L);
        result.expectedHeaderReceived(KafkaShareConstants.KEY, "my-key");
        result.expectedHeaderReceived(KafkaShareConstants.DELIVERY_COUNT, (short) 3);

        subscribedConsumer("outcome").addRecord(record(TOPIC, 7, "my-key", "hello", (short) 3));

        result.assertIsSatisfied();
        assertThat(KafkaShareConstants.TOPIC).isEqualTo(KafkaConstants.TOPIC);
    }

    private RecordingShareConsumer subscribedConsumer(String groupId) {
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> consumers.containsKey(groupId) && consumers.get(groupId).subscription().contains(TOPIC));
        return consumers.get(groupId);
    }
}
