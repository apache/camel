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
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.BodyInAggregatingStrategy;
import org.apache.camel.processor.aggregate.MemoryAggregationRepository;
import org.apache.camel.processor.aggregate.OptimisticLockRetryPolicy;
import org.apache.camel.spi.OptimisticLockingAggregationRepository;
import org.junit.Test;

public class DistributedOptimisticLockFailingTest extends AbstractDistributedTest {

    private static final class AlwaysFailingRepository extends MemoryAggregationRepository {
        @Override
        public Exchange add(CamelContext camelContext, String key, Exchange oldExchange, Exchange newExchange) {
            throw new OptimisticLockingException();
        }
    }

    private static final class EverySecondOneFailsRepository extends MemoryAggregationRepository {
        private AtomicInteger counter = new AtomicInteger();

        private EverySecondOneFailsRepository() {
            super(true);
        }

        @Override
        public Exchange add(CamelContext camelContext, String key, Exchange oldExchange, Exchange newExchange) {
            int count = counter.incrementAndGet();
            if (count % 2 == 0) {
                throw new OptimisticLockingException();
            } else {
                return super.add(camelContext, key, oldExchange, newExchange);
            }
        }
    }

    private EverySecondOneFailsRepository sharedRepository = new EverySecondOneFailsRepository();

    @Test
    public void testAlwaysFails() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);
        MockEndpoint mock2 = getMockEndpoint2("mock:result");
        mock2.expectedMessageCount(0);

        try {
            template.sendBodyAndHeader("direct:fails", "hello world", "id", 1);
            fail("Should throw CamelExecutionException");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(CamelExchangeException.class, e.getCause());
            assertIsInstanceOf(OptimisticLockingAggregationRepository.OptimisticLockingException.class, e.getCause().getCause());
        }

        try {
            template2.sendBodyAndHeader("direct:fails", "hello world", "id", 1);
            fail("Should throw CamelExecutionException");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(CamelExchangeException.class, e.getCause());
            assertIsInstanceOf(OptimisticLockingAggregationRepository.OptimisticLockingException.class, e.getCause().getCause());
        }

        mock.assertIsSatisfied();
        mock2.assertIsSatisfied();
    }

    @Test
    public void testEverySecondOneFails() throws Exception {
        int size = 200;
        ExecutorService service = Executors.newFixedThreadPool(10);
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            final int id = i % 25;
            final int choice = i % 2;
            final int count = i;
            tasks.add(new Callable<Object>() {
                public Object call() throws Exception {
                    if (choice == 0) {
                        template.sendBodyAndHeader("direct:everysecondone", "" + count, "id", id);
                    } else {
                        template2.sendBodyAndHeader("direct:everysecondone", "" + count, "id", id);
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
                from("direct:fails").aggregate(header("id"), new BodyInAggregatingStrategy()).aggregationRepository(new AlwaysFailingRepository()).optimisticLocking()
                    // do not use retry delay to speedup test
                    .optimisticLockRetryPolicy(new OptimisticLockRetryPolicy().maximumRetries(5).retryDelay(0)).completionSize(2).to("mock:result");

                from("direct:everysecondone").aggregate(header("id"), new BodyInAggregatingStrategy()).aggregationRepository(sharedRepository).optimisticLocking().completionSize(8)
                    .to("mock:result");
            }
        };
    }
}
