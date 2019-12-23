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

import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.AggregateProcessor;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.junit.Test;

/**
 * Unit test to verify that aggregate by timeout only also works.
 */
public class AggregateTimeoutWithExecutorServiceTest extends ContextTestSupport {

    public static final int NUM_AGGREGATORS = 20;

    @Test
    public void testThreadNotUsedForEveryAggregatorWithCustomExecutorService() throws Exception {
        assertTrue("There should not be a thread for every aggregator when using a shared thread pool", aggregateThreadsCount() < NUM_AGGREGATORS);

        // sanity check to make sure were testing routes that work
        for (int i = 0; i < NUM_AGGREGATORS; ++i) {
            MockEndpoint result = getMockEndpoint("mock:result" + i);
            // by default the use latest aggregation strategy is used so we get
            // message 4
            result.expectedBodiesReceived("Message 4");
        }
        for (int i = 0; i < NUM_AGGREGATORS; ++i) {
            for (int j = 0; j < 5; j++) {
                template.sendBodyAndHeader("direct:start" + i, "Message " + j, "id", "1");
            }
        }
        assertMockEndpointsSatisfied();
    }

    public static int aggregateThreadsCount() {
        int count = 0;
        ThreadGroup threadGroup = Thread.currentThread().getThreadGroup();
        Thread[] threads = new Thread[threadGroup.activeCount()];
        threadGroup.enumerate(threads);
        for (Thread thread : threads) {
            if (thread != null && thread.getName().contains(AggregateProcessor.AGGREGATE_TIMEOUT_CHECKER)) {
                ++count;
            }
        }
        return count;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // share 8 threads among the 20 routes
                ScheduledExecutorService threadPool = context.getExecutorServiceManager().newScheduledThreadPool(this, "MyThreadPool", 8);
                for (int i = 0; i < NUM_AGGREGATORS; ++i) {
                    from("direct:start" + i)
                        // aggregate timeout after 0.1 second
                        .aggregate(header("id"), new UseLatestAggregationStrategy()).completionTimeout(100).timeoutCheckerExecutorService(threadPool)
                        .completionTimeoutCheckerInterval(10).to("mock:result" + i);
                }
            }
        };
    }
}
