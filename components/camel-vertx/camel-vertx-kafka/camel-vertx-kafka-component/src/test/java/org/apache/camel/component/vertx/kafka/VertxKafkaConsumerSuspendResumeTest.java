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
package org.apache.camel.component.vertx.kafka;

import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class VertxKafkaConsumerSuspendResumeTest extends BaseEmbeddedKafkaTest {

    public static final String TOPIC = "test";

    @EndpointInject("vertx-kafka:" + TOPIC
                    + "?groupId=group1&autoOffsetReset=earliest&keyDeserializer=org.apache.kafka.common.serialization.StringDeserializer&"
                    + "valueDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
                    + "&autoCommitIntervalMs=1000&sessionTimeoutMs=30000")
    private Endpoint from;

    @EndpointInject("mock:result")
    private MockEndpoint to;

    private org.apache.kafka.clients.producer.KafkaProducer<String, String> producer;

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

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                from(from).routeId("foo").to(to);
            }
        };
    }

    @Test
    public void kafkaSuspendResume() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(3);

        for (int k = 0; k < 3; k++) {
            String msg = "message-" + k;
            ProducerRecord<String, String> data = new ProducerRecord<>(TOPIC, "1", msg);
            producer.send(data);
        }

        MockEndpoint.assertIsSatisfied(context);

        // suspend
        context.getRouteController().suspendRoute("foo");

        resetMocks();
        getMockEndpoint("mock:result").expectedMessageCount(0);

        for (int k = 3; k < 10; k++) {
            String msg = "message-" + k;
            ProducerRecord<String, String> data = new ProducerRecord<>(TOPIC, "1", msg);
            producer.send(data);
        }

        getMockEndpoint("mock:result").setResultMinimumWaitTime(1000);
        assertMockEndpointsSatisfied(5, TimeUnit.SECONDS);

        resetMocks();
        getMockEndpoint("mock:result").expectedMessageCount(7);

        // resume
        context.getRouteController().resumeRoute("foo");

        MockEndpoint.assertIsSatisfied(context);
    }

}
