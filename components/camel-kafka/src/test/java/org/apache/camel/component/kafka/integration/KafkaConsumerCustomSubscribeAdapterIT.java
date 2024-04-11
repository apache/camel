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
import java.util.Properties;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.consumer.support.subcription.DefaultSubscribeAdapter;
import org.apache.camel.component.kafka.consumer.support.subcription.TopicInfo;
import org.apache.camel.component.kafka.integration.common.KafkaTestUtil;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class KafkaConsumerCustomSubscribeAdapterIT extends BaseKafkaTestSupport {

    public static final String TOPIC = "test-subscribe-adapter";

    private org.apache.kafka.clients.producer.KafkaProducer<String, String> producer;

    private static class TestSubscribeAdapter extends DefaultSubscribeAdapter {
        private volatile boolean subscribeCalled = false;

        @Override
        public void subscribe(Consumer<?, ?> consumer, ConsumerRebalanceListener reBalanceListener, TopicInfo topicInfo) {
            try {
                super.subscribe(consumer, reBalanceListener, topicInfo);
            } finally {
                subscribeCalled = true;
            }
        }

        public boolean isSubscribeCalled() {
            return subscribeCalled;
        }
    }

    @BindToRegistry(KafkaConstants.KAFKA_SUBSCRIBE_ADAPTER)
    private TestSubscribeAdapter testSubscribeAdapter = new TestSubscribeAdapter();

    @BeforeEach
    public void before() {
        Properties props = getDefaultProperties();
        producer = new org.apache.kafka.clients.producer.KafkaProducer<>(props);
    }

    @AfterEach
    public void after() {
        if (producer != null) {
            producer.close();
        }
        // clean all test topics
        kafkaAdminClient.deleteTopics(Collections.singletonList(TOPIC));
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("kafka:%s?brokers=%s&autoOffsetReset=earliest&consumersCount=1",
                        TOPIC, service.getBootstrapServers())
                        .routeId("subadapter").to(KafkaTestUtil.MOCK_RESULT);
            }
        };
    }

    @Test
    public void kafkaMessagesIsConsumedByCamel() throws Exception {
        MockEndpoint to = contextExtension.getMockEndpoint(KafkaTestUtil.MOCK_RESULT);

        to.expectedBodiesReceivedInAnyOrder("m1", "m2");
        for (int k = 1; k <= 2; k++) {
            String msg = "m" + k;
            ProducerRecord<String, String> data = new ProducerRecord<>(TOPIC, "1", msg);
            producer.send(data);
        }

        to.assertIsSatisfied();

        assertTrue(testSubscribeAdapter.isSubscribeCalled());
    }
}
