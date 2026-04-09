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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.AggregationStrategies;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.concurrent.SynchronousExecutorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.camel.Exchange.SPLIT_COMPLETE;
import static org.apache.camel.Exchange.SPLIT_INDEX;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Reproducer for CAMEL-23281 regression: split + aggregate + transacted deadlocks when the aggregated exchange is
 * routed to a separate route via direct:.
 *
 * The CAMEL-23281 fix processes the aggregate completion inline for SynchronousExecutorService, which avoids the
 * deadlock when the destination is a simple endpoint (e.g. mock:). However, when the aggregated exchange is sent to a
 * direct: endpoint that starts a new route, the inline processing re-enters Pipeline.process() which goes through
 * CamelInternalProcessor.processTransacted() and DefaultAsyncProcessorAwaitManager.await(). The nested await() loops on
 * executeFromQueue(), which does NOT restore from the reactive executor's back stack, so the callback latch never fires
 * and the thread blocks forever.
 */
public class SplitAggregateDirectRouteTransactedDeadlockTest extends ContextTestSupport {

    private final Map<String, Object> txData = new ConcurrentHashMap<>();

    @Test
    @Timeout(30)
    public void testSplitAggregateTransactedDirectRoute() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 11; i++) {
            sb.append("Line ").append(i).append("\n");
        }

        MockEndpoint result = getMockEndpoint("mock:aggregated");
        result.expectedMessageCount(3);

        template.request("direct:transacted", e -> {
            e.getIn().setBody(sb.toString());
        });

        MockEndpoint.assertIsSatisfied(10, SECONDS, result);

        // verify TRANSACTION_CONTEXT_DATA was propagated to the aggregated exchanges
        for (Exchange received : result.getReceivedExchanges()) {
            Map<?, ?> ctx = received.getProperty(Exchange.TRANSACTION_CONTEXT_DATA, Map.class);
            assertNotNull(ctx, "TRANSACTION_CONTEXT_DATA should be propagated");
            assertSame(txData, ctx, "TRANSACTION_CONTEXT_DATA should reference the same map");
        }
    }

    @Test
    @Timeout(30)
    public void testSplitAggregateTransactedDirectRouteInChoice() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("HEADER\n");
        for (int i = 1; i <= 11; i++) {
            sb.append("Line ").append(i).append("\n");
        }

        MockEndpoint result = getMockEndpoint("mock:aggregated");
        result.expectedMessageCount(3);

        template.request("direct:transacted-choice", e -> {
            e.getIn().setBody(sb.toString());
        });

        MockEndpoint.assertIsSatisfied(10, SECONDS, result);

        for (Exchange received : result.getReceivedExchanges()) {
            Map<?, ?> ctx = received.getProperty(Exchange.TRANSACTION_CONTEXT_DATA, Map.class);
            assertNotNull(ctx, "TRANSACTION_CONTEXT_DATA should be propagated");
            assertSame(txData, ctx, "TRANSACTION_CONTEXT_DATA should reference the same map");
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Transacted split + aggregate sending to direct: route — deadlocks
                from("direct:transacted")
                        .process(e -> {
                            e.getExchangeExtension().setTransacted(true);
                            e.setProperty(Exchange.TRANSACTION_CONTEXT_DATA, txData);
                        })
                        .split(body().tokenize("\n")).streaming().stopOnException()
                        .aggregate(constant(true), AggregationStrategies.groupedBody())
                        .eagerCheckCompletion()
                        .executorService(new SynchronousExecutorService())
                        .completionSize(5)
                        .completionPredicate(exchangeProperty(SPLIT_COMPLETE))
                        .to("direct:process");

                // Transacted split + aggregate inside choice/when sending to direct: route — deadlocks
                from("direct:transacted-choice")
                        .process(e -> {
                            e.getExchangeExtension().setTransacted(true);
                            e.setProperty(Exchange.TRANSACTION_CONTEXT_DATA, txData);
                        })
                        .split(body().tokenize("\n")).streaming().stopOnException()
                        .choice()
                        .when(exchangeProperty(SPLIT_INDEX).isGreaterThan(0))
                        .aggregate(constant(true), AggregationStrategies.groupedBody())
                        .eagerCheckCompletion()
                        .executorService(new SynchronousExecutorService())
                        .completionSize(5)
                        .completionPredicate(exchangeProperty(SPLIT_COMPLETE))
                        .to("direct:process");

                // Separate route that processes the aggregated exchange
                from("direct:process")
                        .log("Aggregated batch: ${body}")
                        .to("mock:aggregated");
            }
        };
    }
}
