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
package org.apache.camel.processor.aggregator;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.BodyInAggregatingStrategy;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.MemoryKeyValueRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every aggregator without a repository auto-discovers the single {@code KeyValueRepository} of the registry. An
 * aggregator must only recover the exchanges it completed itself, not an exchange that another aggregator completed and
 * is still processing.
 */
class AggregateAutoDiscoveredKeyValueRepositorySharedTest extends ContextTestSupport {

    private final MemoryKeyValueRepository kvRepo = new MemoryKeyValueRepository();
    private final CountDownLatch release = new CountDownLatch(1);

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry registry = super.createCamelRegistry();
        registry.bind("kvRepo", kvRepo);
        return registry;
    }

    @AfterEach
    void releaseHeldExchange() {
        // also when the test fails before, so that the held exchange does not keep the shutdown waiting
        release.countDown();
    }

    @Test
    void testOtherAggregatorDoesNotRecoverExchangeInProgress() throws Exception {
        MockEndpoint orders = getMockEndpoint("mock:orders");
        orders.expectedBodiesReceived("A+B");
        MockEndpoint invoices = getMockEndpoint("mock:invoices");
        invoices.expectedMessageCount(0);
        // the recover checkers run 1 second after start and then every 5 seconds
        invoices.setAssertPeriod(7000);

        template.sendBodyAndHeader("direct:orders", "A", "id", 1);
        // the group completes, and the aggregated exchange waits in the orders route
        Future<Object> completing = template.asyncRequestBodyAndHeader("direct:orders", "B", "id", 1);
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertTrue(kvRepo.keys().stream().anyMatch(k -> k.startsWith("completed"))));

        // the aggregator of the invoices route must not send the exchange of the orders route
        invoices.assertIsSatisfied();

        release.countDown();
        completing.get(5, TimeUnit.SECONDS);
        orders.assertIsSatisfied();
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertTrue(kvRepo.keys().isEmpty()));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // no aggregationRepository: both aggregators auto-discover the KeyValueRepository of the registry
                from("direct:orders").routeId("orders")
                        .aggregate(header("id"), new BodyInAggregatingStrategy())
                        .completionSize(2)
                        .process(e -> release.await(20, TimeUnit.SECONDS))
                        .to("mock:orders");

                from("direct:invoices").routeId("invoices")
                        .aggregate(header("id"), new BodyInAggregatingStrategy())
                        .completionSize(2)
                        .to("mock:invoices");
            }
        };
    }
}
