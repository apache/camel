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

import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.BodyInAggregatingStrategy;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.MemoryKeyValueRepository;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@code KeyValueRepository} auto-discovered from the registry must support recovery like an explicitly configured
 * {@code KeyValueAggregationRepository}.
 */
public class AggregateAutoDiscoveredKeyValueRepositoryRecoverTest extends ContextTestSupport {

    private final MemoryKeyValueRepository kvRepo = new MemoryKeyValueRepository();

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry registry = super.createCamelRegistry();
        registry.bind("kvRepo", kvRepo);
        return registry;
    }

    @Test
    public void testRecoverWithAutoDiscoveredRepository() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("A+B", "A+B");
        mock.message(1).header(Exchange.REDELIVERED).isEqualTo(true);
        // the first attempt fails, so the aggregated exchange is recovered
        mock.whenExchangeReceived(1, exchange -> {
            throw new IllegalArgumentException("Forced");
        });

        template.sendBodyAndHeader("direct:start", "A", "id", 1);
        template.sendBodyAndHeader("direct:start", "B", "id", 1);

        assertMockEndpointsSatisfied();
        // the recovered exchange is confirmed and removed from the repository
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertTrue(kvRepo.keys().isEmpty()));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // no aggregationRepository: the KeyValueRepository is auto-discovered from the registry
                from("direct:start")
                        .aggregate(header("id"), new BodyInAggregatingStrategy())
                        .completionSize(2)
                        .to("mock:result");
            }
        };
    }
}
