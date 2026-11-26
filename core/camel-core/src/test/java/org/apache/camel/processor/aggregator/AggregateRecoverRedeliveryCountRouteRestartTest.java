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
import org.apache.camel.processor.BodyInAggregatingStrategy;
import org.apache.camel.support.KeyValueAggregationRepository;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The redeliveries of a recovered exchange are counted in memory by the aggregator. A route stop and start must not
 * reset that count: the exchange is processed at most 1 + maximumRedeliveries times before it is moved to the dead
 * letter channel.
 */
class AggregateRecoverRedeliveryCountRouteRestartTest extends ContextTestSupport {

    private static final int MAXIMUM_REDELIVERIES = 2;

    private final GatedRepository repository = new GatedRepository();
    private MockEndpoint aggregated;
    private MockEndpoint dead;

    /**
     * Control: without a restart, the exchange is processed 3 times (the first attempt and 2 redeliveries), and the
     * copy moved to the dead letter channel has CamelRedeliveryCounter=2.
     */
    @Test
    void testRedeliveryCountWithoutRouteRestart() throws Exception {
        expectAttemptsThenDeadLetter();

        completeGroup();
        recoverOnce(2);
        recoverOnce(3);
        recoverOnce(4);

        assertAttemptsThenDeadLetter();
    }

    /**
     * Without the fix the stop clears the count, so the recover check after the start counts the second redelivery as
     * the first one: the exchange is processed a 4th time instead of being moved to the dead letter channel.
     */
    @Test
    void testRedeliveryCountKeptWhenRouteRestarted() throws Exception {
        expectAttemptsThenDeadLetter();

        completeGroup();
        recoverOnce(2);

        context.getRouteController().stopRoute("agg");
        context.getRouteController().startRoute("agg");

        recoverOnce(3);
        recoverOnce(4);

        assertAttemptsThenDeadLetter();
    }

    /**
     * The count is no longer cleared by a stop, so the recover task drops the count of an exchange that has left the
     * repository without this aggregator (here confirmed by someone else). The test then puts the exchange back under
     * the same id: its count starts again from 1 instead of going on from the old one.
     */
    @Test
    void testRedeliveryCountDroppedWhenExchangeLeftRepository() throws Exception {
        completeGroup();
        recoverOnce(2);

        // the exchange is confirmed by someone else, and a recover check runs without it
        String exchangeId = repository.scan(context).iterator().next();
        Exchange exchange = repository.recoverNow(context, exchangeId);
        repository.confirm(context, exchangeId);
        int scans = repository.scans.get();
        await().atMost(20, TimeUnit.SECONDS).until(() -> repository.scans.get() >= scans + 2);

        // it comes back under the same id
        repository.add(context, "1", exchange);
        repository.remove(context, "1", exchange);
        recoverOnce(3);

        assertEquals(3, aggregated.getReceivedCounter());
        assertEquals(1, aggregated.getReceivedExchanges().get(2).getIn().getHeader(Exchange.REDELIVERY_COUNTER),
                "The count of the exchange that left the repository should have been dropped");
        assertEquals(0, dead.getReceivedCounter());
    }

    private void expectAttemptsThenDeadLetter() {
        aggregated.expectedMessageCount(1 + MAXIMUM_REDELIVERIES);
        aggregated.message(0).header(Exchange.REDELIVERY_COUNTER).isNull();
        aggregated.message(1).header(Exchange.REDELIVERY_COUNTER).isEqualTo(1);
        aggregated.message(2).header(Exchange.REDELIVERY_COUNTER).isEqualTo(2);
        dead.expectedMessageCount(1);
        dead.message(0).header(Exchange.REDELIVERY_COUNTER).isEqualTo(MAXIMUM_REDELIVERIES);
    }

    private void assertAttemptsThenDeadLetter() throws Exception {
        assertEquals(1 + MAXIMUM_REDELIVERIES, aggregated.getReceivedCounter(),
                "The recovered exchange should be processed 1 + maximumRedeliveries times");
        MockEndpoint.assertIsSatisfied(aggregated, dead);
        // and it is confirmed, so it is not recovered again
        await().atMost(20, TimeUnit.SECONDS).until(() -> repository.scan(context).isEmpty());
    }

    /**
     * Completes the group: the first attempt fails, so the aggregated exchange stays in the repository.
     */
    private void completeGroup() {
        template.sendBodyAndHeader("direct:start", "A", "id", 1);
        template.sendBodyAndHeader("direct:start", "B", "id", 1);
        await().atMost(20, TimeUnit.SECONDS).until(() -> aggregated.getReceivedCounter() == 1);
    }

    /**
     * Lets the recover task load the exchange once, and waits until it is processed again or moved to the dead letter
     * channel.
     */
    private void recoverOnce(int attemptsAndDeadLetters) {
        repository.permits.incrementAndGet();
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> aggregated.getReceivedCounter() + dead.getReceivedCounter() >= attemptsAndDeadLetters);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        aggregated = getMockEndpoint("mock:aggregated");
        dead = getMockEndpoint("mock:dead");
        return new RouteBuilder() {
            @Override
            public void configure() {
                repository.setRecoveryInterval(100);
                repository.setMaximumRedeliveries(MAXIMUM_REDELIVERIES);
                repository.setDeadLetterUri("mock:dead");

                from("direct:start").routeId("agg")
                        .aggregate(header("id"), new BodyInAggregatingStrategy()).aggregationRepository(repository)
                        .completionSize(2)
                        .to("mock:aggregated")
                        .throwException(new IllegalArgumentException("Forced"));
            }
        };
    }

    /**
     * An in-memory recoverable repository whose recover task loads an exchange only when the test lets it, so each
     * redelivery (or the move to the dead letter channel) happens when the test expects it.
     */
    private static final class GatedRepository extends KeyValueAggregationRepository {

        private final AtomicInteger permits = new AtomicInteger();
        private final AtomicInteger scans = new AtomicInteger();

        @Override
        public Set<String> scan(CamelContext camelContext) {
            scans.incrementAndGet();
            return super.scan(camelContext);
        }

        @Override
        public Exchange recover(CamelContext camelContext, String exchangeId) {
            if (permits.getAndUpdate(p -> p > 0 ? p - 1 : 0) == 0) {
                // not now: the recover task tries again on its next check
                return null;
            }
            return super.recover(camelContext, exchangeId);
        }

        Exchange recoverNow(CamelContext camelContext, String exchangeId) {
            return super.recover(camelContext, exchangeId);
        }
    }
}
