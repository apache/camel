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

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.vertx.kafka.offset.VertxKafkaManualCommit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class VertxKafkaConsumerManualCommitTest extends BaseEmbeddedKafkaTest {

    public static final String TOPIC = "test";

    @EndpointInject("vertx-kafka:" + TOPIC
                    + "?groupId=group1&sessionTimeoutMs=30000&enableAutoCommit=false&autoOffsetReset=earliest&"
                    + "allowManualCommit=true")
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
                from(from).routeId("foo").to(to).process(e -> {
                    VertxKafkaManualCommit manual
                            = e.getIn().getHeader(VertxKafkaConstants.MANUAL_COMMIT, VertxKafkaManualCommit.class);
                    assertNotNull(manual);
                    manual.commit();
                });
            }
        };
    }

    @Test
    public void kafkaManualCommit() throws Exception {
        // We disable the default autoCommit and we take control on committing the offsets
        // First step: We send first 5 records to Kafka, we expect our consumer to receive them and commit the offsets after consuming the records
        // through manual.commit();
        to.expectedMessageCount(5);

        for (int k = 0; k < 5; k++) {
            String msg = "message-" + k;
            ProducerRecord<String, String> data = new ProducerRecord<>(TOPIC, "1", msg);
            producer.send(data);
        }

        to.assertIsSatisfied(3000);

        to.reset();

        // Second step: We shut down our route, we expect nothing will be recovered by our route
        context.getRouteController().stopRoute("foo");
        to.expectedMessageCount(0);

        // Third step: While our route is stopped, we send 3 records more to Kafka test topic
        for (int k = 5; k < 8; k++) {
            String msg = "message-" + k;
            ProducerRecord<String, String> data = new ProducerRecord<>(TOPIC, "1", msg);
            producer.send(data);
        }

        to.assertIsSatisfied(3000);

        to.reset();

        // Fourth step: We start again our route, since we have been committing the offsets from the first step,
        // we will expect to consume from the latest committed offset e.g from offset 5
        context.getRouteController().startRoute("foo");
        to.expectedMessageCount(3);

        // give some time for the route to start again
        synchronized (this) {
            Thread.sleep(1000);
        }

        to.assertIsSatisfied(3000);
    }
}
