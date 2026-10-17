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

import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.log.ConsumingAppender;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.AggregateProcessor;
import org.apache.camel.processor.aggregate.MemoryAggregationRepository;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.apache.camel.spi.AggregationRepository;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A group that is due for its completion timeout must still be completed when the aggregation repository fails once (a
 * database that is not reachable for a moment) while the timeout checker completes it. The group stays in the
 * repository, so it must be completed by a later check, and not be left in the repository for ever.
 */
public class AggregateTimeoutRepositoryExceptionTest extends ContextTestSupport {

    private static final String LOGGER = AggregateProcessor.class.getName();

    private final FailOnceRepository repository = new FailOnceRepository();

    @AfterEach
    public void removeAppender() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        ctx.getConfiguration().removeLogger(LOGGER);
        ctx.updateLoggers();
    }

    @Test
    public void testRepeatedFailuresOfAGroupLogOneWarn() throws Exception {
        // the tests in this module run in parallel and log through the same logger: keep the lines of this key only
        String key = "warn-once-" + context.getName();
        Queue<String> warnings = new ConcurrentLinkedQueue<>();
        ConsumingAppender.newAppender(LOGGER, "AggregateTimeoutRepositoryExceptionTest", Level.WARN, event -> {
            String message = event.getMessage().getFormattedMessage();
            if (event.getLevel() == Level.WARN && message.contains(key)) {
                warnings.add(message);
            }
        });

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("B");

        template.sendBodyAndHeader("direct:start", "A", "id", key);
        template.sendBodyAndHeader("direct:start", "B", "id", key);
        // the next three reads of the group are the ones of the timeout checker
        repository.failingGets.set(3);

        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
        assertEquals(3, repository.failures.get(), "the repository should have failed three times");
        assertEquals(1, warnings.size(), "only the first failure is logged at WARN: " + warnings);
        String warning = List.copyOf(warnings).get(0);
        assertTrue(warning.contains("java.lang.IllegalStateException: Simulated repository failure in get"),
                "the WARN names the type of the exception: " + warning);
    }

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
        private final AtomicInteger failingGets = new AtomicInteger();
        private final AtomicInteger failures = new AtomicInteger();

        @Override
        public Exchange add(CamelContext camelContext, String key, Exchange exchange) {
            return delegate.add(camelContext, key, exchange);
        }

        @Override
        public Exchange get(CamelContext camelContext, String key) {
            if (failNextGet.compareAndSet(true, false) || failingGets.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
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
