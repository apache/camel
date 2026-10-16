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
import java.util.concurrent.atomic.AtomicBoolean;
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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A group that is due for its completion timeout must still be completed when the aggregation repository fails once (a
 * database that is not reachable for a moment) while the timeout checker completes it. The group stays in the
 * repository, so it must be completed by a later check, and not be left in the repository for ever.
 */
public class AggregateTimeoutRepositoryExceptionTest extends ContextTestSupport {

    private final FailOnceRepository repository = new FailOnceRepository();

    @Test
    public void testTimeoutCompletesGroupAfterFailedGet() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("B");

        template.sendBodyAndHeader("direct:start", "A", "id", "1");
        template.sendBodyAndHeader("direct:start", "B", "id", "1");
        // the next read of the group is the one of the timeout checker
        repository.failNextGet.set(true);

        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
        assertEquals(1, repository.failures.get(), "the repository should have failed once");
    }

    @Test
    public void testTimeoutCompletesGroupAfterFailedRemove() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("B");

        template.sendBodyAndHeader("direct:start", "A", "id", "1");
        template.sendBodyAndHeader("direct:start", "B", "id", "1");
        // the next removal of the group is the one of the timeout checker
        repository.failNextRemove.set(true);

        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
        assertEquals(1, repository.failures.get(), "the repository should have failed once");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .aggregate(header("id"), new UseLatestAggregationStrategy())
                        .aggregationRepository(repository)
                        .completionTimeout(200).completionTimeoutCheckerInterval(50)
                        .to("mock:result");
            }
        };
    }

    private static class FailOnceRepository extends ServiceSupport implements AggregationRepository {

        private final MemoryAggregationRepository delegate = new MemoryAggregationRepository();
        private final AtomicBoolean failNextGet = new AtomicBoolean();
        private final AtomicBoolean failNextRemove = new AtomicBoolean();
        private final AtomicInteger failures = new AtomicInteger();

        @Override
        public Exchange add(CamelContext camelContext, String key, Exchange exchange) {
            return delegate.add(camelContext, key, exchange);
        }

        @Override
        public Exchange get(CamelContext camelContext, String key) {
            if (failNextGet.compareAndSet(true, false)) {
                failures.incrementAndGet();
                throw new IllegalStateException("Simulated repository failure in get");
            }
            return delegate.get(camelContext, key);
        }

        @Override
        public void remove(CamelContext camelContext, String key, Exchange exchange) {
            if (failNextRemove.compareAndSet(true, false)) {
                failures.incrementAndGet();
                throw new IllegalStateException("Simulated repository failure in remove");
            }
            delegate.remove(camelContext, key, exchange);
        }

        @Override
        public void confirm(CamelContext camelContext, String exchangeId) {
            delegate.confirm(camelContext, exchangeId);
        }

        @Override
        public Set<String> getKeys() {
            return delegate.getKeys();
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
