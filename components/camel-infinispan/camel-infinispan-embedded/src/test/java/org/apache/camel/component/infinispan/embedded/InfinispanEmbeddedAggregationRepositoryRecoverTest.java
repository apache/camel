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
package org.apache.camel.component.infinispan.embedded;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A completed aggregation whose processing fails is recovered with all its messages, including the one that completed
 * the group (which is never added to the repository).
 */
public class InfinispanEmbeddedAggregationRepositoryRecoverTest extends InfinispanEmbeddedTestSupport {

    private final AtomicInteger failures = new AtomicInteger();

    @Test
    public void testRecoveredExchangeContainsTheLastMessage() throws Exception {
        InfinispanEmbeddedConfiguration configuration = new InfinispanEmbeddedConfiguration();
        configuration.setCacheContainer(cacheContainer);

        InfinispanEmbeddedAggregationRepository repository = new InfinispanEmbeddedAggregationRepository(getCacheName());
        repository.setConfiguration(configuration);
        repository.setRecoveryInterval(100);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .aggregate(header("id"), (oldExchange, newExchange) -> {
                            if (oldExchange == null) {
                                return newExchange;
                            }
                            String body = oldExchange.getIn().getBody(String.class) + "+"
                                          + newExchange.getIn().getBody(String.class);
                            oldExchange.getIn().setBody(body);
                            return oldExchange;
                        })
                        .aggregationRepository(repository)
                        .completionSize(3)
                        .to("mock:aggregated")
                        .process(exchange -> {
                            if (failures.getAndIncrement() == 0) {
                                throw new IllegalStateException("Forced failure after the aggregation");
                            }
                        });
            }
        });

        MockEndpoint mock = getMockEndpoint("mock:aggregated");
        // the completed aggregation fails once and is then recovered with all three messages
        mock.expectedBodiesReceived("a+b+c", "a+b+c");

        template.sendBodyAndHeader("direct:start", "a", "id", "group");
        template.sendBodyAndHeader("direct:start", "b", "id", "group");
        template.sendBodyAndHeader("direct:start", "c", "id", "group");

        mock.assertIsSatisfied();
        assertNull(mock.getReceivedExchanges().get(0).getIn().getHeader(Exchange.REDELIVERED));
        assertEquals(Boolean.TRUE, mock.getReceivedExchanges().get(1).getIn().getHeader(Exchange.REDELIVERED));
        assertTrue(repository.getKeys().isEmpty());
    }
}
