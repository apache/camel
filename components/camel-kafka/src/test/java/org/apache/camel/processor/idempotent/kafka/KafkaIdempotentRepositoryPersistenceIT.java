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
package org.apache.camel.processor.idempotent.kafka;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.integration.BaseEmbeddedKafkaTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.annotations.ContextFixture;
import org.apache.camel.test.infra.core.api.ConfigurableContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test whether the KafkaIdempotentRepository successfully recreates its cache from pre-existing topics. This guarantees
 * that the de-duplication state survives application instance restarts.
 *
 * This test requires running in a certain order (which isn't great for unit testing), hence the ordering-related
 * annotations.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class KafkaIdempotentRepositoryPersistenceIT extends BaseEmbeddedKafkaTestSupport implements ConfigurableContext {

    private KafkaIdempotentRepository kafkaIdempotentRepository;

    void clearTopics() {
        kafkaAdminClient.deleteTopics(Arrays.asList("TEST_PERSISTENCE")).all();
    }

    @Override
    @ContextFixture
    public void configureContext(CamelContext context) {
        kafkaIdempotentRepository = new KafkaIdempotentRepository("TEST_PERSISTENCE", getBootstrapServers());
        context.getRegistry().bind("kafkaIdempotentRepositoryPersistence", kafkaIdempotentRepository);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:in").to("mock:before").idempotentConsumer(header("id"))
                        .idempotentRepository("kafkaIdempotentRepositoryPersistence").to("mock:out").end();
            }
        };
    }

    private void sendMessages(long count) {
        ProducerTemplate template = contextExtension.getProducerTemplate();

        for (int i = 0; i < count; i++) {
            template.sendBodyAndHeader("direct:in", "Test message", "id", i % 5);
        }
    }

    @Order(1)
    @Test
    @DisplayName("Checks that half of the messages pass and duplicates are blocked")
    public void testFirstPassFiltersAsExpected() {
        int count = 10;
        sendMessages(count);

        // all records sent initially
        MockEndpoint mockBefore = contextExtension.getMockEndpoint("mock:before");
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(count, mockBefore.getReceivedCounter()));

        // only first 5 records are received, the rest are filtered
        MockEndpoint mockOut = contextExtension.getMockEndpoint("mock:out");
        assertEquals(5, mockOut.getReceivedCounter());
    }

    @Order(2)
    @RepeatedTest(3)
    @DisabledIfSystemProperty(named = "kafka.instance.type", matches = "remote",
                              disabledReason = "Remote may not allow deleting the topic, may contain data, etc")
    @DisplayName("Checks that resending the same messages causes no duplicate messages")
    public void testSecondPassFiltersEverything() {
        int count = 10;
        sendMessages(count);

        // all records sent initially
        MockEndpoint mockBefore = contextExtension.getMockEndpoint("mock:before");
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(count, mockBefore.getReceivedCounter()));

        // nothing pass the idempotent consumer this time
        MockEndpoint mockOut = contextExtension.getMockEndpoint("mock:out");
        assertEquals(0, mockOut.getReceivedCounter());
    }

    @Order(3)
    @DisabledIfSystemProperty(named = "kafka.instance.type", matches = "remote",
                              disabledReason = "Remote may not allow deleting the topic, may contain data, etc")
    @ParameterizedTest
    @MethodSource("multiplePassesProvider")
    @DisplayName("Checks that multiple passes in different ways yield the same result: no duplicate messages")
    public void testThirdPassFiltersEverything(long count, long passes) {
        for (int i = 0; i < passes; i++) {
            sendMessages(count);
        }

        // all records sent initially
        MockEndpoint mockBefore = contextExtension.getMockEndpoint("mock:before");
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(count * passes, mockBefore.getReceivedCounter()));

        // nothing gets passed the idempotent consumer this time
        MockEndpoint mockOut = contextExtension.getMockEndpoint("mock:out");
        assertEquals(0, mockOut.getReceivedCounter());
    }

    private static Stream<Arguments> multiplePassesProvider() {
        return Stream.of(Arguments.of(10, 2),
                Arguments.of(ThreadLocalRandom.current().nextInt(11, 27), 2),
                Arguments.of(ThreadLocalRandom.current().nextInt(1, 9), 4));
    }

    @Order(4)
    @DisabledIfSystemProperty(named = "kafka.instance.type", matches = "remote",
                              disabledReason = "Remote may not allow deleting the topic, may contain data, etc")
    @Test
    @DisplayName("Checks that the remaining messages can finally go through")
    public void testFourthPass() {
        ProducerTemplate template = contextExtension.getProducerTemplate();
        int count = 5;
        for (int i = 5; i < 10; i++) {
            template.sendBodyAndHeader("direct:in", "Test message", "id", i);
        }

        // all records sent initially
        MockEndpoint mockBefore = contextExtension.getMockEndpoint("mock:before");
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(count, mockBefore.getReceivedCounter()));

        // there are no duplicate messages on this run so all of them should pass
        MockEndpoint mockOut = contextExtension.getMockEndpoint("mock:out");
        assertEquals(count, mockOut.getReceivedCounter());
    }

    @Order(5)
    @Test
    @DisplayName("Checks that can be cleared after use")
    public void testClear() {
        assertDoesNotThrow(kafkaIdempotentRepository::clear,
                "Clearing the idempotent repository should not throw exceptions");

        clearTopics();
    }
}
