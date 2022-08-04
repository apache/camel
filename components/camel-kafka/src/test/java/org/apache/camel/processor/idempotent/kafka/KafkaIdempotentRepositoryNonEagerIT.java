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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.EndpointInject;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.integration.BaseEmbeddedKafkaTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test for non-eager idempotentRepository usage.
 */
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
public class KafkaIdempotentRepositoryNonEagerIT extends BaseEmbeddedKafkaTestSupport {

    private KafkaIdempotentRepository kafkaIdempotentRepository;

    @EndpointInject("mock:out")
    private MockEndpoint mockOut;

    @EndpointInject("mock:before")
    private MockEndpoint mockBefore;

    @Override
    protected RoutesBuilder createRouteBuilder() {
        // Every instance of the repository must use a different topic to guarantee isolation between tests
        kafkaIdempotentRepository = new KafkaIdempotentRepository("TEST_NON_EAGER_" + UUID.randomUUID(), getBootstrapServers());
        context.getRegistry().bind("kafkaIdempotentRepositoryNonEager", kafkaIdempotentRepository);

        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:in").to("mock:before").idempotentConsumer(header("id"))
                        .idempotentRepository("kafkaIdempotentRepositoryNonEager").eager(false).to("mock:out").end();
            }
        };
    }

    @Test
    public void testRemovesDuplicates() {
        for (int i = 0; i < 10; i++) {
            template.sendBodyAndHeader("direct:in", "Test message", "id", i % 5);
        }

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(5, mockOut.getReceivedCounter()));

        assertEquals(10, mockBefore.getReceivedCounter());
    }

    @Test
    public void testRollsBackOnException() {
        mockOut.whenAnyExchangeReceived(exchange -> {
            int id = exchange.getIn().getHeader("id", Integer.class);
            if (id == 0) {
                throw new IllegalArgumentException("Boom!");
            }
        });

        for (int i = 0; i < 10; i++) {
            try {
                template.sendBodyAndHeader("direct:in", "Test message", "id", i % 5);
            } catch (CamelExecutionException cex) {
                // no-op; expected
            }
        }

        assertEquals(6, mockOut.getReceivedCounter()); // id{0} goes through the
                                                      // idempotency check
                                                      // twice
        assertEquals(10, mockBefore.getReceivedCounter());
    }

}
