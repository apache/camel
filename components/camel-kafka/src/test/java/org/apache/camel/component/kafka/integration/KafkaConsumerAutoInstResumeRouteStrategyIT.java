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

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.consumer.support.resume.KafkaResumable;
import org.apache.camel.component.kafka.integration.common.KafkaTestUtil;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.resume.TransientResumeStrategy;
import org.apache.camel.processor.resume.kafka.KafkaResumeStrategyConfigurationBuilder;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class KafkaConsumerAutoInstResumeRouteStrategyIT extends BaseEmbeddedKafkaTestSupport {
    private static final String TOPIC = "resumable-route-auto";

    public static KafkaResumeStrategyConfigurationBuilder getDefaultKafkaResumeStrategyConfigurationBuilder() {
        return KafkaResumeStrategyConfigurationBuilder.newBuilder()
                .withBootstrapServers(service.getBootstrapServers())
                .withTopic("resumable-route-auto-offsets")
                .withResumeCache(TransientResumeStrategy.createSimpleCache())
                .withProducerProperty("max.block.ms", "10000")
                .withMaxInitializationDuration(Duration.ofSeconds(5))
                .withProducerProperty("delivery.timeout.ms", "30000")
                .withProducerProperty("session.timeout.ms", "15000")
                .withProducerProperty("request.timeout.ms", "15000")
                .withConsumerProperty("session.timeout.ms", "20000");
    }

    @BeforeEach
    public void before() {
        Properties props = KafkaTestUtil.getDefaultProperties(service);
        KafkaProducer<Object, Object> producer = new KafkaProducer<>(props);

        for (int i = 0; i < 10; i++) {
            producer.send(new ProducerRecord<>(TOPIC, String.valueOf(i)));
        }
    }

    @Test
    @Timeout(value = 30)
    public void testOffsetIsBeingChecked() throws InterruptedException {
        MockEndpoint mock = contextExtension.getMockEndpoint(KafkaTestUtil.MOCK_RESULT);

        mock.expectedMessageCount(10);
        mock.assertIsSatisfied();
    }

    @AfterEach
    public void after() {
        kafkaAdminClient.deleteTopics(Collections.singletonList(TOPIC));
    }

    private void process(Exchange exchange) {
        exchange.getMessage().setHeader(Exchange.OFFSET, KafkaResumable.of(exchange));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("kafka:%s?groupId=%s_GROUP&autoCommitIntervalMs=1000"
                      + "&autoOffsetReset=earliest&consumersCount=1", TOPIC, TOPIC)
                        .resumable().configuration(getDefaultKafkaResumeStrategyConfigurationBuilder())
                        .process(e -> process(e))
                        .routeId("resume-strategy-auto-route")
                        // Note: this is for manually testing the ResumableCompletion onFailure exception logging. Uncomment it for testing it
                        // .process(e -> e.setException(new RuntimeCamelException("Mock error in test")))
                        .to("mock:sentMessages");

                fromF("kafka:%s?groupId=%s_GROUP&autoCommitIntervalMs=1000", "resumable-route-auto-offsets",
                        "resumable-route-auto-offsets")
                        .to(KafkaTestUtil.MOCK_RESULT);
            }
        };
    }
}
