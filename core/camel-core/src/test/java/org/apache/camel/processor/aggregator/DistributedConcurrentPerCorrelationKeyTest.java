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

import static org.junit.jupiter.api.Assertions.assertEquals;

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
import org.junit.jupiter.api.Test;

public class DistributedConcurrentPerCorrelationKeyTest extends AbstractDistributedTest {

    private final MemoryAggregationRepository sharedAggregationRepository = new MemoryAggregationRepository(true);

    @Test
    public void testAggregateConcurrentPerCorrelationKey() throws Exception {
        ExecutorService service = Executors.newFixedThreadPool(50);
        final List<Callable<Object>> tasks = createTasks();

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

    private List<Callable<Object>> createTasks() {
        List<Callable<Object>> tasks = new ArrayList<>();
        int size = 200;
        for (int i = 0; i < size; i++) {
            final int id = i % 25;
            final int choice = i % 2;
            final int count = i;
            tasks.add(() -> sendTask(choice, count, id));
        }
        return tasks;
    }

    private Object sendTask(int choice, int count, int id) {
        String uri = "direct:start";
        if (choice == 0) {
            template.sendBodyAndHeader(uri, Integer.toString(count), "id", id);
        } else {
            template2.sendBodyAndHeader(uri, Integer.toString(count), "id", id);
        }
        return null;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .aggregate(header("id"), new BodyInAggregatingStrategy())
                        .aggregationRepository(sharedAggregationRepository)
                        .optimisticLocking()
                        .completionSize(8)
                        .to("mock:result");
            }
        };
    }
}
