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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.AggregationStrategies;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.concurrent.SynchronousExecutorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.camel.Exchange.SPLIT_COMPLETE;
import static org.apache.camel.Exchange.SPLIT_INDEX;

/**
 * Reproducer for CAMEL-23281: split/aggregator deadlock when the aggregate uses SynchronousExecutorService with
 * completionSize + completionPredicate(SPLIT_COMPLETE) and the exchange is transacted.
 *
 * Root cause: when a transacted exchange triggers aggregate completion, AggregateProcessor.onSubmitCompletion() queues
 * the completion task via reactiveExecutor.scheduleQueue(). This task is later drained by
 * DefaultAsyncProcessorAwaitManager.await() via executeFromQueue(). The drained task re-enters
 * CamelInternalProcessor.processTransacted() which calls processor.process(exchange) (sync version), triggering another
 * DefaultAsyncProcessorAwaitManager.process() → await() → executeFromQueue() cycle. When the reactive queue is
 * exhausted, the innermost await() blocks on CountDownLatch.await() forever — deadlock.
 */
public class SplitAggregateInChoiceSynchronousExecutorTest extends ContextTestSupport {

    @Test
    public void testSplitAggregateInChoiceNonTransacted() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("HEADER\n");
        for (int i = 1; i <= 11; i++) {
            sb.append("Line ").append(i).append("\n");
        }

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(3);

        template.sendBody("direct:start", sb.toString());

        MockEndpoint.assertIsSatisfied(10, SECONDS, result);
    }

    @Test
    @Timeout(30)
    public void testSplitAggregateTransactedDeadlock() throws Exception {
        // Transacted split + aggregate: deadlocks due to recursive processTransacted → await → executeFromQueue cycle
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 11; i++) {
            sb.append("Line ").append(i).append("\n");
        }

        MockEndpoint result = getMockEndpoint("mock:aggregated");
        result.expectedMessageCount(3);

        template.request("direct:transacted-simple", e -> {
            e.getIn().setBody(sb.toString());
        });

        MockEndpoint.assertIsSatisfied(10, SECONDS, result);
    }

    @Test
    @Timeout(30)
    public void testSplitAggregateTransactedInChoiceDeadlock() throws Exception {
        // Same as the JIRA reproducer: transacted split with aggregate inside choice/when
        StringBuilder sb = new StringBuilder();
        sb.append("HEADER\n");
        for (int i = 1; i <= 11; i++) {
            sb.append("Line ").append(i).append("\n");
        }

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(3);

        template.request("direct:transacted", e -> {
            e.getIn().setBody(sb.toString());
        });

        MockEndpoint.assertIsSatisfied(10, SECONDS, result);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Non-transacted: split CSV, skip header, aggregate inside choice/when — works fine
                from("direct:start")
                        .split(body().tokenize("\n")).streaming().stopOnException()
                        .choice()
                        .when(exchangeProperty(SPLIT_INDEX).isGreaterThan(0))
                        .aggregate(constant(true), AggregationStrategies.groupedBody())
                        .eagerCheckCompletion()
                        .executorService(new SynchronousExecutorService())
                        .completionSize(5)
                        .completionPredicate(exchangeProperty(SPLIT_COMPLETE))
                        .to("mock:result");

                // Transacted split + aggregate (no choice/when) — deadlocks
                from("direct:transacted-simple")
                        .process(e -> e.getExchangeExtension().setTransacted(true))
                        .split(body().tokenize("\n")).streaming().stopOnException()
                        .aggregate(constant(true), AggregationStrategies.groupedBody())
                        .eagerCheckCompletion()
                        .executorService(new SynchronousExecutorService())
                        .completionSize(5)
                        .completionPredicate(exchangeProperty(SPLIT_COMPLETE))
                        .to("mock:aggregated");

                // Transacted split + aggregate inside choice/when (CAMEL-23281 pattern) — deadlocks
                from("direct:transacted")
                        .process(e -> e.getExchangeExtension().setTransacted(true))
                        .split(body().tokenize("\n")).streaming().stopOnException()
                        .choice()
                        .when(exchangeProperty(SPLIT_INDEX).isGreaterThan(0))
                        .aggregate(constant(true), AggregationStrategies.groupedBody())
                        .eagerCheckCompletion()
                        .executorService(new SynchronousExecutorService())
                        .completionSize(5)
                        .completionPredicate(exchangeProperty(SPLIT_COMPLETE))
                        .to("mock:result");
            }
        };
    }
}
