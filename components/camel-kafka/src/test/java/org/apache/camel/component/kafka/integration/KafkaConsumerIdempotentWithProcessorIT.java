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

import java.math.BigInteger;
import java.util.Arrays;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.idempotent.kafka.KafkaIdempotentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class KafkaConsumerIdempotentWithProcessorIT extends KafkaConsumerIdempotentTestSupport {
    public static final String TOPIC = "testidemp3";

    @BindToRegistry("kafkaIdempotentRepository")
    private KafkaIdempotentRepository kafkaIdempotentRepository
            = new KafkaIdempotentRepository("TEST_IDEMPOTENT", getBootstrapServers());

    @EndpointInject("kafka:" + TOPIC
                    + "?groupId=group2&autoOffsetReset=earliest"
                    + "&keyDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
                    + "&valueDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
                    + "&autoCommitIntervalMs=1000&pollTimeoutMs=1000&autoCommitEnable=true"
                    + "&interceptorClasses=org.apache.camel.component.kafka.MockConsumerInterceptor")
    private Endpoint from;

    @EndpointInject("mock:resulti")
    private MockEndpoint to;

    private int size = 200;

    @BeforeEach
    public void before() {
        kafkaIdempotentRepository.clear();
        kafkaAdminClient.deleteTopics(Arrays.asList(TOPIC, "TEST_IDEMPOTENT")).all();
        doSend(size, TOPIC);
    }

    @AfterEach
    public void after() {
        // clean all test topics
        kafkaAdminClient.deleteTopics(Arrays.asList(TOPIC, "TEST_IDEMPOTENT")).all();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {

        return new RouteBuilder() {

            @Override
            public void configure() {
                from(from).routeId("idemp-with-prop")
                        .process(exchange -> {
                            byte[] id = exchange.getIn().getHeader("id", byte[].class);

                            BigInteger bi = new BigInteger(id);

                            exchange.getIn().setHeader("id", String.valueOf(bi.longValue()));
                        })
                        .idempotentConsumer(header("id"))
                        .idempotentRepository("kafkaIdempotentRepository")
                        .to(to);
            }
        };
    }

    @Test
    public void kafkaMessageIsConsumedByCamel() throws InterruptedException {
        doRun(to, size);
    }
}
