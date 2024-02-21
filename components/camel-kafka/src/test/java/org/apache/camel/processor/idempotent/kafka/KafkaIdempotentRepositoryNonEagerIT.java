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

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.annotations.ContextFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test for non-eager idempotentRepository usage.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class KafkaIdempotentRepositoryNonEagerIT extends SimpleIdempotentTest {

    @BindToRegistry("kafkaIdempotentRepositoryNonEager")
    private final KafkaIdempotentRepository kafkaIdempotentRepository
            = new KafkaIdempotentRepository("TEST_NON_EAGER_" + UUID.randomUUID(), service.getBootstrapServers());

    @ContextFixture
    public void configureKafka(CamelContext context) {
        context.getPropertiesComponent().setLocation("ref:prop");

        KafkaComponent kafka = new KafkaComponent(context);
        kafka.init();
        kafka.getConfiguration().setBrokers(service.getBootstrapServers());
        context.addComponent("kafka", kafka);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:in").to("mock:before")
                        .idempotentConsumer(header("id")).idempotentRepository("kafkaIdempotentRepositoryNonEager").eager(false)
                        .to("mock:out").end();
            }
        };
    }

    @Order(1)
    @Test
    @DisplayName("Tests that duplicated messages do not go through")
    public void testRemovesDuplicates() {
        ProducerTemplate template = contextExtension.getProducerTemplate();
        for (int i = 0; i < 10; i++) {
            template.sendBodyAndHeader("direct:in", "Test message", "id", i % 5);
        }

        MockEndpoint mockOut = contextExtension.getMockEndpoint("mock:out");
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(5, mockOut.getReceivedCounter()));

        MockEndpoint mockBefore = contextExtension.getMockEndpoint("mock:before");
        assertEquals(10, mockBefore.getReceivedCounter());
    }

    @Order(2)
    @Test
    @DisplayName("Tests that processing exceptions cause the message to be rolled back")
    public void testRollsBackOnException() {
        MockEndpoint mockOut = contextExtension.getMockEndpoint("mock:out");
        mockOut.whenAnyExchangeReceived(exchange -> {
            int id = exchange.getIn().getHeader("id", Integer.class);
            if (id == 0) {
                throw new IllegalArgumentException("Boom!");
            }
        });

        ProducerTemplate template = contextExtension.getProducerTemplate();
        for (int i = 0; i < 10; i++) {
            try {
                template.sendBodyAndHeader("direct:in", "Test message", "id", i % 5);
            } catch (CamelExecutionException cex) {
                // no-op; expected
            }
        }

        assertEquals(5, mockOut.getReceivedCounter(),
                "Only the 5 messages from the previous test should have been received ");
        MockEndpoint mockBefore = contextExtension.getMockEndpoint("mock:before");

        assertEquals(20, mockBefore.getReceivedCounter(),
                "Test should have received 20 messages in total from all the tests");
    }

}
