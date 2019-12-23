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
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.junit.Test;

public class AggregatorLockingTest extends ContextTestSupport {

    private final CountDownLatch latch = new CountDownLatch(2);

    @Test
    public void testAggregationWithoutParallelNorOptimisticShouldNotLockDownstreamProcessors() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("a", "b");

        template.sendBodyAndHeader("seda:a", "a", "myId", 1);
        template.sendBodyAndHeader("seda:a", "b", "myId", 2);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:a?concurrentConsumers=2").aggregate(header("myId"), new UseLatestAggregationStrategy()).completionSize(1)
                    // N.B. *no* parallelProcessing() nor optimisticLocking() !
                    // each thread releases 1 permit and then blocks waiting for
                    // other threads.
                    // if there are <THREAD_COUNT> threads running in parallel,
                    // then all N threads will release
                    // and we will proceed. If the threads are prevented from
                    // running simultaneously due to the
                    // lock in AggregateProcessor.doProcess() then only 1 thread
                    // will run and will not release
                    // the current thread, causing the test to time out.
                    .log("Before await with thread: ${threadName} and body: ${body}").process(new Processor() {
                        @Override
                        public void process(Exchange exchange) throws Exception {
                            latch.countDown();
                            // block until the other thread counts down as well
                            if (!latch.await(5, TimeUnit.SECONDS)) {
                                throw new RuntimeException("Took too long; assume threads are blocked and fail test");
                            }
                        }
                    }).log("After await with thread: ${threadName} and body: ${body}").to("mock:result");
            }
        };
    }

}
