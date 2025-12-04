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
import org.apache.camel.processor.aggregate.GroupedBodyAggregationStrategy;
import org.junit.jupiter.api.Test;

/**
 * This test verifies that completeAllOnStop() properly completes aggregations on shutdown when using
 * completionInterval.
 */
public class AggregateCompleteAllOnStopWithIntervalTest extends ContextTestSupport {

    @Test
    public void testCompleteAllOnStopWithCompletionIntervalOnly() throws Exception {
        // Set shutdown timeout to 5x the completion interval (1 second)
        context.getShutdownStrategy().setTimeout(5);

        MockEndpoint mock = getMockEndpoint("mock:aggregated");
        // We expect the incomplete aggregation to be completed on shutdown
        // The aggregation should contain 3 messages: A, B, C
        mock.expectedMessageCount(1);

        MockEndpoint input = getMockEndpoint("mock:input");
        input.expectedMessageCount(3);

        // Send 3 messages with the same correlation key
        // completionSize is 10, so this won't trigger size-based completion
        template.sendBodyAndHeader("direct:start", "A", "aggregateKey", "group1");
        template.sendBodyAndHeader("direct:start", "B", "aggregateKey", "group1");
        template.sendBodyAndHeader("direct:start", "C", "aggregateKey", "group1");

        input.assertIsSatisfied();

        // Stop the route immediately without waiting for completionInterval
        // With completeAllOnStop(), we expect the aggregation to be completed
        context.stop();

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("mock:input")
                        .aggregate(new GroupedBodyAggregationStrategy())
                        .simple("${in.header.aggregateKey}")
                        .completionSize(10)
                        .completionInterval(1000)
                        .completeAllOnStop()
                        .to("mock:aggregated");
            }
        };
    }
}
