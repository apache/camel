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

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.MemoryAggregationRepository;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.apache.camel.spi.AggregationRepository;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.jupiter.api.Test;

/**
 * Test that the completion interval task survives an exception thrown by the aggregation repository. Without the
 * try/catch guard in AggregationIntervalTask.run(), a single RuntimeException from getKeys() would permanently cancel
 * the scheduled task.
 */
public class AggregateIntervalTaskExceptionTest extends ContextTestSupport {

    @Test
    public void testIntervalTaskSurvivesException() throws Exception {
        getMockEndpoint("mock:result").expectedMinimumMessageCount(1);

        // send messages that will be aggregated
        template.sendBodyAndHeader("direct:start", "A", "id", "1");
        template.sendBodyAndHeader("direct:start", "B", "id", "1");

        // the first interval tick with data will hit the exception from getKeys(),
        // but the scheduler should survive and complete on a subsequent tick
        MockEndpoint.assertIsSatisfied(context, 15, TimeUnit.SECONDS);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .aggregate(header("id"), new UseLatestAggregationStrategy())
                        .aggregationRepository(new FailOnceRepository())
                        .completionInterval(500)
                        .to("mock:result");
            }
        };
    }

    private static class FailOnceRepository extends ServiceSupport implements AggregationRepository {

        private final MemoryAggregationRepository delegate = new MemoryAggregationRepository();
        private final AtomicInteger getKeysCount = new AtomicInteger();

        @Override
        public Exchange add(CamelContext camelContext, String key, Exchange exchange) {
            return delegate.add(camelContext, key, exchange);
        }

        @Override
        public Exchange get(CamelContext camelContext, String key) {
            return delegate.get(camelContext, key);
        }

        @Override
        public void remove(CamelContext camelContext, String key, Exchange exchange) {
            delegate.remove(camelContext, key, exchange);
        }

        @Override
        public void confirm(CamelContext camelContext, String exchangeId) {
            delegate.confirm(camelContext, exchangeId);
        }

        @Override
        public Set<String> getKeys() {
            Set<String> keys = delegate.getKeys();
            if (keys != null && !keys.isEmpty() && getKeysCount.incrementAndGet() == 1) {
                throw new RuntimeException("Simulated repository failure");
            }
            return keys;
        }

        @Override
        protected void doStart() throws Exception {
            delegate.start();
        }

        @Override
        protected void doStop() throws Exception {
            delegate.stop();
        }
    }
}
