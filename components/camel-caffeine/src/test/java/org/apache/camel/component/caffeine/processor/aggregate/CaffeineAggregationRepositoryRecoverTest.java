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
package org.apache.camel.component.caffeine.processor.aggregate;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The recover task of the Aggregate EIP must only recover completed exchanges that were not confirmed, and never send
 * an aggregation that is still in progress.
 */
public class CaffeineAggregationRepositoryRecoverTest extends CamelTestSupport {

    private final AtomicInteger scans = new AtomicInteger();
    private final AtomicInteger failures = new AtomicInteger();
    private CaffeineAggregationRepository repository;

    @Test
    void testRecoverCompletedExchangeOnly() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:aggregated");
        // the completed aggregation fails once and is then recovered, the aggregation in progress is not sent
        mock.expectedBodiesReceived("a+b", "a+b");

        template.sendBodyAndHeader("direct:start", "c", "id", "inProgress");
        template.sendBodyAndHeader("direct:start", "a", "id", "completed");
        template.sendBodyAndHeader("direct:start", "b", "id", "completed");

        mock.assertIsSatisfied();
        assertNull(mock.getReceivedExchanges().get(0).getIn().getHeader(Exchange.REDELIVERED));
        assertEquals(Boolean.TRUE, mock.getReceivedExchanges().get(1).getIn().getHeader(Exchange.REDELIVERED));

        // let the recover task run a few more times: nothing else is sent, and the recovered exchange was confirmed
        int scanned = scans.get();
        await().atMost(10, TimeUnit.SECONDS).until(() -> scans.get() >= scanned + 3);
        assertEquals(2, mock.getReceivedCounter());
        assertTrue(repository.scan(context).isEmpty());
        assertEquals(Set.of("inProgress"), repository.getKeys());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        repository = new CaffeineAggregationRepository() {
            @Override
            public Set<String> scan(CamelContext camelContext) {
                scans.incrementAndGet();
                return super.scan(camelContext);
            }
        };
        repository.setRecoveryInterval(100);

        return new RouteBuilder() {
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
                        .completionSize(2)
                        .to("mock:aggregated")
                        .process(exchange -> {
                            if (failures.getAndIncrement() == 0) {
                                throw new IllegalStateException("Forced failure after the aggregation");
                            }
                        });
            }
        };
    }
}
