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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledIfSystemProperty(named = "camel.threads.virtual.enabled", matches = "true",
                          disabledReason = "In case of Virtual Threads, the threads cannot be counted this way")
public class AggregateTimeoutWithNoExecutorServiceTest extends ContextTestSupport {

    @Test
    public void testThreadUsedForEveryAggregatorWhenDefaultExecutorServiceUsed() throws Exception {
        assertTrue(AggregateTimeoutWithExecutorServiceTest.aggregateThreadsCount(context.getName())
                   >= AggregateTimeoutWithExecutorServiceTest.NUM_AGGREGATORS,
                "There should be a thread for every aggregator when using defaults");

        // sanity check to make sure were testing routes that work
        for (int i = 0; i < AggregateTimeoutWithExecutorServiceTest.NUM_AGGREGATORS; ++i) {
            MockEndpoint result = getMockEndpoint("mock:result" + i);
            // by default the use latest aggregation strategy is used so we get
            // message 4
            result.expectedBodiesReceived("Message 4");
        }
        for (int i = 0; i < AggregateTimeoutWithExecutorServiceTest.NUM_AGGREGATORS; ++i) {
            for (int j = 0; j < 5; j++) {
                template.sendBodyAndHeader("direct:start" + i, "Message " + j, "id", "1");
            }
        }
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                for (int i = 0; i < AggregateTimeoutWithExecutorServiceTest.NUM_AGGREGATORS; ++i) {
                    from("direct:start" + i)
                            // aggregate timeout after 0.1 second
                            .aggregate(header("id"), new UseLatestAggregationStrategy()).completionTimeout(100)
                            .completionTimeoutCheckerInterval(10).to("mock:result" + i);
                }
            }
        };
    }
}
