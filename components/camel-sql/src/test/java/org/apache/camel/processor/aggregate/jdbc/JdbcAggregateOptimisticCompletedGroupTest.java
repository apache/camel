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
package org.apache.camel.processor.aggregate.jdbc;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Aggregate EIP with optimistic locking: a message that is aggregated on a group while the group is completed by
 * another message or the completion timeout must start a new group, and must not store the completed group again
 * (duplicates) or overwrite a new group of the same key (lost messages).
 */
public class JdbcAggregateOptimisticCompletedGroupTest extends AbstractJdbcAggregationTestSupport {

    private final AtomicBoolean hold = new AtomicBoolean();
    private volatile CountDownLatch aggregating;
    private volatile CountDownLatch release;
    private JdbcAggregationRepository repo2;

    @BeforeEach
    public void resetLatches() {
        aggregating = new CountDownLatch(1);
        release = new CountDownLatch(1);
        hold.set(true);
    }

    @Test
    public void testGroupCompletedByAnotherMessage() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:aggregated");
        mock.expectedMessageCount(2);

        template.sendBodyAndHeader("direct:predicate", "x", "id", "k1");
        Thread threadA = sendAndHold("direct:predicate", "k1");
        // the group [x] is completed while thread A aggregates on it
        sendLast("direct:predicate", "b", "k1");
        await().atMost(5, TimeUnit.SECONDS).until(() -> repo.scan(context).isEmpty());

        release.countDown();
        threadA.join(10000);
        sendLast("direct:predicate", "z", "k1");

        MockEndpoint.assertIsSatisfied(context);
        // x is sent once, a starts a new group
        assertEquals(List.of("x,b", "a,z"), bodies(mock));
    }

    @Test
    public void testGroupCompletedAndNewGroupStarted() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:aggregated");
        mock.expectedMessageCount(2);

        template.sendBodyAndHeader("direct:predicate", "x", "id", "k2");
        Thread threadA = sendAndHold("direct:predicate", "k2");
        sendLast("direct:predicate", "b", "k2");
        // a new group for the same key
        template.sendBodyAndHeader("direct:predicate", "c", "id", "k2");
        await().atMost(5, TimeUnit.SECONDS).until(() -> repo.scan(context).isEmpty());

        release.countDown();
        threadA.join(10000);
        sendLast("direct:predicate", "z", "k2");

        MockEndpoint.assertIsSatisfied(context);
        // x is sent once, and c is not lost
        assertEquals(List.of("x,b", "c,a,z"), bodies(mock));
    }

    @Test
    public void testGroupCompletedByTimeout() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:timeout");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:timeout", "x", "id", "k3");
        Thread threadA = sendAndHold("direct:timeout", "k3");
        // the group [x] is completed by the completion timeout while thread A aggregates on it
        MockEndpoint.assertIsSatisfied(context);
        await().atMost(5, TimeUnit.SECONDS).until(() -> repo2.scan(context).isEmpty());

        release.countDown();
        threadA.join(10000);

        await().atMost(5, TimeUnit.SECONDS).until(() -> mock.getReceivedCounter() == 2);
        // x is sent once, a starts a new group that completes by timeout too
        assertEquals(List.of("x", "a"), bodies(mock));
    }

    private static List<String> bodies(MockEndpoint mock) {
        return mock.getReceivedExchanges().stream().map(e -> e.getMessage().getBody(String.class)).toList();
    }

    // sends "a" on another thread and waits until the aggregation strategy got the group it read
    private Thread sendAndHold(String uri, String key) throws InterruptedException {
        Thread thread = new Thread(() -> template.sendBodyAndHeader(uri, "a", "id", key), "thread-A");
        thread.start();
        assertTrue(aggregating.await(5, TimeUnit.SECONDS), "message a should be aggregating");
        return thread;
    }

    private void sendLast(String uri, String body, String key) {
        template.send(uri, e -> {
            e.getMessage().setBody(body);
            e.getMessage().setHeader("id", key);
            e.getMessage().setHeader("last", true);
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                configureJdbcAggregationRepository();
                repo2 = applicationContext.getBean("repo2", JdbcAggregationRepository.class);

                from("direct:predicate")
                        .aggregate(header("id"), new HoldingAggregationStrategy())
                        .aggregationRepository(repo).optimisticLocking()
                        .eagerCheckCompletion().completionPredicate(header("last").isEqualTo(true))
                        .to("mock:aggregated");

                from("direct:timeout")
                        .aggregate(header("id"), new HoldingAggregationStrategy())
                        .aggregationRepository(repo2).optimisticLocking()
                        .completionTimeout(300).completionTimeoutCheckerInterval(50)
                        .to("mock:timeout");
            }
        };
    }

    private final class HoldingAggregationStrategy implements AggregationStrategy {

        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            String body = newExchange.getMessage().getBody(String.class);
            if (oldExchange != null && "a".equals(body) && hold.compareAndSet(true, false)) {
                // message a has read the group, hold it until the group has been completed
                aggregating.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (oldExchange == null) {
                return newExchange;
            }
            oldExchange.getMessage().setBody(oldExchange.getMessage().getBody(String.class) + "," + body);
            return oldExchange;
        }
    }
}
