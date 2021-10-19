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

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.AggregationStrategies;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.DefaultKafkaManualAsyncCommitFactory;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.KafkaEndpoint;
import org.apache.camel.component.kafka.KafkaManualCommit;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class KafkaConsumerAsyncManualCommitIT extends BaseEmbeddedKafkaTestSupport {

    public static final String TOPIC = "testManualCommitTest";

    @EndpointInject("kafka:" + TOPIC
                    + "?groupId=group1&sessionTimeoutMs=30000&autoCommitEnable=false"
                    + "&allowManualCommit=true&autoOffsetReset=earliest")
    private Endpoint from;

    @EndpointInject("mock:result")
    private MockEndpoint to;

    @EndpointInject("mock:resultBar")
    private MockEndpoint toBar;

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
    protected RouteBuilder createRouteBuilder() throws Exception {
        ((KafkaEndpoint) from).getComponent().setKafkaManualCommitFactory(new DefaultKafkaManualAsyncCommitFactory());
        return new RouteBuilder() {

            @Override
            public void configure() {
                from(from).routeId("foo").to("direct:aggregate");
                // With sync manual commit, this would throw a concurrent modification exception
                // It can be usesd in aggregator with completion timeout/interval for instance
                // WARN: records from one partition must be processed by one unique thread
                from("direct:aggregate").routeId("aggregate").to(to)
                        .aggregate()
                        .constant(true)
                        .completionTimeout(1)
                        .aggregationStrategy(AggregationStrategies.groupedExchange())
                        .split().body()
                        .process(e -> {
                            KafkaManualCommit manual = e.getMessage().getBody(Exchange.class)
                                    .getMessage().getHeader(KafkaConstants.MANUAL_COMMIT, KafkaManualCommit.class);
                            assertNotNull(manual);
                            manual.commit();
                        });
                from(from).routeId("bar").autoStartup(false).to(toBar);
            }
        };
    }

    @RepeatedTest(4)
    public void kafkaManualCommit() throws Exception {
        to.expectedMessageCount(5);
        to.expectedBodiesReceivedInAnyOrder("message-0", "message-1", "message-2", "message-3", "message-4");
        // The LAST_RECORD_BEFORE_COMMIT header should include a value as we use
        // manual commit
        to.allMessages().header(KafkaConstants.LAST_RECORD_BEFORE_COMMIT).isNotNull();

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
        to.expectedBodiesReceivedInAnyOrder("message-5", "message-6", "message-7");

        to.assertIsSatisfied(3000);
    }

}
