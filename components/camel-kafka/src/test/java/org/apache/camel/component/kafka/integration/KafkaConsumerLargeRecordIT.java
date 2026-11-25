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
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.integration.common.KafkaTestUtil;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.errors.TopicExistsException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A record larger than max.partition.fetch.bytes (1 MB by default) must not stall the consumer (CAMEL-25021).
 */
public class KafkaConsumerLargeRecordIT extends BaseKafkaTestSupport {
    private static final String TOPIC = "large-record-" + UUID.randomUUID();
    private static final int LARGE_SIZE = 3 * 1024 * 1024;

    private KafkaProducer<String, String> producer;

    @BeforeEach
    public void before() throws Exception {
        // create the topic with a limit above the large record; the consumer of the route may have created it
        // already (auto creation), then raise the limit of that topic instead
        String maxMessageBytes = String.valueOf(10 * 1024 * 1024);
        NewTopic topic = new NewTopic(TOPIC, 1, (short) 1)
                .configs(Map.of(TopicConfig.MAX_MESSAGE_BYTES_CONFIG, maxMessageBytes));
        try {
            kafkaAdminClient.createTopics(List.of(topic)).all().get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (!(e.getCause() instanceof TopicExistsException)) {
                throw e;
            }
            ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, TOPIC);
            AlterConfigOp op = new AlterConfigOp(
                    new ConfigEntry(TopicConfig.MAX_MESSAGE_BYTES_CONFIG, maxMessageBytes), AlterConfigOp.OpType.SET);
            kafkaAdminClient.incrementalAlterConfigs(Map.of(resource, List.of(op))).all().get(30, TimeUnit.SECONDS);
        }

        Properties props = getDefaultProperties();
        props.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, 10 * 1024 * 1024);
        producer = new KafkaProducer<>(props);
    }

    @AfterEach
    public void after() {
        if (producer != null) {
            producer.close();
        }
        kafkaAdminClient.deleteTopics(Collections.singletonList(TOPIC));
    }

    @Test
    public void shouldConsumeRecordLargerThanMaxPartitionFetchBytes() throws Exception {
        MockEndpoint result = contextExtension.getMockEndpoint(KafkaTestUtil.MOCK_RESULT);
        result.expectedMessageCount(3);

        producer.send(new ProducerRecord<>(TOPIC, "1", "small-before")).get(30, TimeUnit.SECONDS);
        producer.send(new ProducerRecord<>(TOPIC, "1", "x".repeat(LARGE_SIZE))).get(30, TimeUnit.SECONDS);
        producer.send(new ProducerRecord<>(TOPIC, "1", "small-after")).get(30, TimeUnit.SECONDS);

        MockEndpoint.assertIsSatisfied(30, TimeUnit.SECONDS, result);
        assertEquals("small-before", result.getExchanges().get(0).getMessage().getBody(String.class));
        assertEquals(LARGE_SIZE, result.getExchanges().get(1).getMessage().getBody(String.class).length());
        assertEquals("small-after", result.getExchanges().get(2).getMessage().getBody(String.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("kafka:" + TOPIC + "?groupId=" + UUID.randomUUID() + "&autoOffsetReset=earliest")
                        .to(KafkaTestUtil.MOCK_RESULT);
            }
        };
    }
}
