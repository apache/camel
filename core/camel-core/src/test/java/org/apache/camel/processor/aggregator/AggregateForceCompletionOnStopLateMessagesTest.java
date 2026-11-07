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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.AggregationStrategies;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * forceCompletionOnStop completes the groups when the route stops, before the graceful shutdown waits for the inflight
 * and pending exchanges. The consumer (seda here) keeps consuming during that wait, and the messages it routes then
 * must be completed too, and not be lost.
 */
public class AggregateForceCompletionOnStopLateMessagesTest extends ContextTestSupport {

    private final CountDownLatch blocked = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);

    @Test
    public void testMessagesConsumedWhileStoppingAreCompleted() throws Exception {
        doTestMessagesConsumedWhileStopping(false);
    }

    @Test
    public void testMessagesConsumedWhileCamelContextStopsAreCompleted() throws Exception {
        doTestMessagesConsumedWhileStopping(true);
    }

    private void doTestMessagesConsumedWhileStopping(boolean stopContext) throws Exception {
        MockEndpoint out = getMockEndpoint("mock:out");

        NotifyBuilder first = new NotifyBuilder(context).whenDone(1).create();
        template.sendBody("seda:in", "m0");
        assertTrue(first.matches(10, TimeUnit.SECONDS));

        // m1 is in flight (blocked before the aggregator), and m2 and m3 wait in the seda queue
        template.sendBody("seda:in", "m1");
        assertTrue(blocked.await(10, TimeUnit.SECONDS));
        template.sendBody("seda:in", "m2");
        template.sendBody("seda:in", "m3");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> stop = executor.submit(() -> {
                if (stopContext) {
                    context.stop();
                } else {
                    context.getRouteController().stopRoute("agg");
                }
                return null;
            });
            // the stop completes the group of m0 first, while m1 is still in flight
            await().atMost(10, TimeUnit.SECONDS).until(() -> out.getReceivedCounter() >= 1);
            release.countDown();
            stop.get(20, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }

        List<String> bodies = new ArrayList<>();
        for (Exchange e : out.getReceivedExchanges()) {
            for (Object o : e.getMessage().getBody(List.class)) {
                bodies.add((String) o);
            }
        }
        bodies.sort(null);
        assertEquals(List.of("m0", "m1", "m2", "m3"), bodies);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:in").routeId("agg")
                        .process(e -> {
                            if ("m1".equals(e.getMessage().getBody(String.class))) {
                                blocked.countDown();
                                assertTrue(release.await(10, TimeUnit.SECONDS));
                            }
                        })
                        .aggregate(constant(true), AggregationStrategies.groupedBody()).completionSize(100)
                        .forceCompletionOnStop()
                        .to("mock:out");
            }
        };
    }
}
