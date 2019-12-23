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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.BodyInAggregatingStrategy;
import org.apache.camel.processor.aggregate.MemoryAggregationRepository;
import org.junit.Test;

public class DistributedConcurrentPerCorrelationKeyTest extends AbstractDistributedTest {

    private MemoryAggregationRepository sharedAggregationRepository = new MemoryAggregationRepository(true);

    private int size = 200;
    private final String uri = "direct:start";

    @Test
    public void testAggregateConcurrentPerCorrelationKey() throws Exception {
        ExecutorService service = Executors.newFixedThreadPool(50);
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            final int id = i % 25;
            final int choice = i % 2;
            final int count = i;
            tasks.add(new Callable<Object>() {
                public Object call() throws Exception {
                    if (choice == 0) {
                        template.sendBodyAndHeader(uri, "" + count, "id", id);
                    } else {
                        template2.sendBodyAndHeader(uri, "" + count, "id", id);
                    }
                    return null;
                }
            });
        }

        MockEndpoint mock = getMockEndpoint("mock:result");
        MockEndpoint mock2 = getMockEndpoint2("mock:result");

        // submit all tasks
        service.invokeAll(tasks);
        service.shutdown();
        service.awaitTermination(10, TimeUnit.SECONDS);

        int contextCount = mock.getReceivedCounter();
        int context2Count = mock2.getReceivedCounter();

        assertEquals(25, contextCount + context2Count);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").aggregate(header("id"), new BodyInAggregatingStrategy()).aggregationRepository(sharedAggregationRepository).optimisticLocking()
                    .completionSize(8).to("mock:result");
            }
        };
    }
}
