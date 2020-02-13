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

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.EndpointInject;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.BaseEmbeddedKafkaTest;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * Test for non-eager idempotentRepository usage.
 */
public class KafkaIdempotentRepositoryNonEagerTest extends BaseEmbeddedKafkaTest {
    @BindToRegistry("kafkaIdempotentRepository")
    private KafkaIdempotentRepository kafkaIdempotentRepository = new KafkaIdempotentRepository("TEST_IDEM", getBootstrapServers());

    @EndpointInject("mock:out")
    private MockEndpoint mockOut;

    @EndpointInject("mock:before")
    private MockEndpoint mockBefore;

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:in").to("mock:before").idempotentConsumer(header("id")).messageIdRepositoryRef("kafkaIdempotentRepository").eager(false).to("mock:out").end();
            }
        };
    }

    @Test
    public void testRemovesDuplicates() throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            template.sendBodyAndHeader("direct:in", "Test message", "id", i % 5);
        }

        assertEquals(5, kafkaIdempotentRepository.getDuplicateCount());

        assertEquals(5, mockOut.getReceivedCounter());
        assertEquals(10, mockBefore.getReceivedCounter());
    }

    @Test
    public void testRollsBackOnException() throws InterruptedException {
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

        assertEquals(4, kafkaIdempotentRepository.getDuplicateCount()); // id{0}
                                                                        // is
                                                                        // not a
                                                                        // duplicate

        assertEquals(6, mockOut.getReceivedCounter()); // id{0} goes through the
                                                       // idempotency check
                                                       // twice
        assertEquals(10, mockBefore.getReceivedCounter());
    }

}
