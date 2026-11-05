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
package org.apache.camel.component.seda;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.AggregationStrategies;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With multipleConsumers, a route that starts or stops consuming from the queue must not stop the other routes that
 * consume from it.
 */
class SedaMultipleConsumersOtherRoutesTest extends ContextTestSupport {

    private static final String URI = "seda:x?multipleConsumers=true";

    private final CountDownLatch blocked = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private final CountingProcessor counting = new CountingProcessor();
    private final CountDownLatch k2Done = new CountDownLatch(1);

    @Test
    void testInflightExchangeCompletesWhenAnotherRouteStarts() throws Exception {
        MockEndpoint a = getMockEndpoint("mock:a");
        a.expectedBodiesReceived("m1");

        template.sendBody(URI, "m1");
        assertTrue(blocked.await(10, TimeUnit.SECONDS));
        context.addRoutes(routeB());
        release.countDown();

        assertMockEndpointsSatisfied();
    }

    @Test
    void testOtherRoutesNotStoppedWhenAnotherRouteStartsAndStops() throws Exception {
        MockEndpoint a = getMockEndpoint("mock:a");
        // the multicast of the consumers is parallel, so the order is not kept
        a.expectedBodiesReceivedInAnyOrder("m0", "m2", "m3");
        release.countDown();

        template.sendBody(URI, "m0");
        await().atMost(10, TimeUnit.SECONDS).until(() -> a.getReceivedCounter() == 1);
        context.addRoutes(routeB());
        template.sendBody(URI, "m2");
        context.getRouteController().stopRoute("B");
        template.sendBody(URI, "m3");

        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
        assertEquals(0, counting.stops.get(), "the processor of route A was stopped");
    }

    @Test
    void testClosedCorrelationKeyKeptWhenAnotherRouteStarts() throws Exception {
        release.countDown();
        MockEndpoint agg = getMockEndpoint("mock:agg");
        MockEndpoint b = getMockEndpoint("mock:b");

        template.sendBodyAndHeader(URI, "k1", "k", "K");
        agg.expectedMessageCount(1);
        agg.assertIsSatisfied();

        context.addRoutes(routeB());
        b.expectedMessageCount(1);
        // the key is closed, so the aggregator rejects this message
        template.sendBodyAndHeader(URI, "k2", "k", "K");
        b.assertIsSatisfied();
        assertTrue(k2Done.await(10, TimeUnit.SECONDS));

        agg.expectedMessageCount(1);
        agg.assertIsSatisfied();
    }

    private RouteBuilder routeB() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(URI).routeId("B").to("mock:b");
            }
        };
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(URI).routeId("A")
                        .onCompletion()
                        .process(e -> {
                            if ("k2".equals(e.getMessage().getBody(String.class))) {
                                k2Done.countDown();
                            }
                        })
                        .end()
                        .process(e -> {
                            if ("m1".equals(e.getMessage().getBody(String.class))) {
                                blocked.countDown();
                                assertTrue(release.await(10, TimeUnit.SECONDS));
                            }
                        })
                        .process(counting)
                        .choice()
                        .when(header("k").isNotNull())
                        .aggregate(header("k"), AggregationStrategies.groupedBody()).completionSize(1)
                        .closeCorrelationKeyOnCompletion(100)
                        .to("mock:agg")
                        .end()
                        .endChoice()
                        .otherwise()
                        .to("mock:a");
            }
        };
    }

    private static final class CountingProcessor extends ServiceSupport implements Processor {
        private final AtomicInteger stops = new AtomicInteger();

        @Override
        public void process(Exchange exchange) {
            // noop
        }

        @Override
        protected void doStop() {
            stops.incrementAndGet();
        }
    }
}
