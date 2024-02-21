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

import java.util.Arrays;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.integration.common.KafkaTestUtil;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.idempotent.kafka.KafkaIdempotentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.apache.camel.component.kafka.serde.KafkaSerdeHelper.numericHeader;

@DisabledIfSystemProperty(named = "enable.kafka.consumer.idempotency.tests", matches = "false")
@Timeout(60)
@Tags({ @Tag("idempotent") })
public class KafkaConsumerIdempotentIT extends KafkaConsumerIdempotentTestSupport {

    public static final String TOPIC = "idempt";

    private final int size = 200;

    @BindToRegistry("kafkaIdempotentRepository")
    private final KafkaIdempotentRepository testIdempotent
            = new KafkaIdempotentRepository("TEST_IDEMPOTENT", getBootstrapServers());

    @BeforeEach
    public void before() {
        kafkaAdminClient.deleteTopics(Arrays.asList(TOPIC, "TEST_IDEMPOTENT")).all();
        doSend(size, TOPIC);
    }

    @AfterEach
    public void after() {
        kafkaAdminClient.deleteTopics(Arrays.asList(TOPIC, "TEST_IDEMPOTENT")).all();
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                from("kafka:" + TOPIC
                     + "?groupId=KafkaConsumerIdempotentIT&autoOffsetReset=earliest"
                     + "&keyDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
                     + "&valueDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
                     + "&autoCommitIntervalMs=1000&pollTimeoutMs=1000&autoCommitEnable=true"
                     + "&interceptorClasses=org.apache.camel.component.kafka.MockConsumerInterceptor").routeId("foo")
                        .idempotentConsumer(numericHeader("id"))
                        .idempotentRepository("kafkaIdempotentRepository")
                        .to(KafkaTestUtil.MOCK_RESULT);
            }
        };
    }

    @Test
    @DisplayName("Numeric headers is consumable when using idempotent (CAMEL-16914)")
    void kafkaIdempotentMessageIsConsumedByCamel() {
        MockEndpoint to = contextExtension.getMockEndpoint(KafkaTestUtil.MOCK_RESULT);

        doRun(to, size);
    }
}
