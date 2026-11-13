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
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;

/**
 * The timeout of the aggregation strategy throws an Error (not a VirtualMachineError) when a group completes by its
 * completion timeout: the other groups still complete by their timeout.
 */
class AggregateTimeoutStrategyThrowsErrorTest extends ContextTestSupport {

    private final AtomicInteger timeouts = new AtomicInteger();

    @Test
    void testOtherGroupsStillCompleteByTimeout() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        // A is not delivered, as for an exception of timeout (the group was removed from the repository before)
        mock.expectedBodiesReceived("B");

        template.sendBodyAndHeader("direct:start", "A", "id", "A");
        // the timeout of A has thrown the Error
        await().atMost(5, TimeUnit.SECONDS).until(() -> timeouts.get() >= 1);
        template.sendBodyAndHeader("direct:start", "B", "id", "B");

        // the timeout checker still runs: B completes by its timeout
        MockEndpoint.assertIsSatisfied(10, TimeUnit.SECONDS, mock);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .aggregate(header("id"), new AggregationStrategy() {
                            @Override
                            public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
                                return oldExchange == null ? newExchange : oldExchange;
                            }

                            @Override
                            public void timeout(Exchange exchange, int index, int total, long timeout) {
                                if (timeouts.incrementAndGet() == 1) {
                                    throw new NoClassDefFoundError("Simulated missing class");
                                }
                            }
                        })
                        .completionTimeout(50).completionTimeoutCheckerInterval(10)
                        .to("mock:result");
            }
        };
    }
}
