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
package org.apache.camel.component.direct;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * While one exchange waits for the consumer of a suspended or stopped direct route, other exchanges sent with the same
 * producer must wait too, and not be sent to the suspended or stopped consumer.
 */
@Timeout(30)
public class DirectProducerSuspendedConsumerTest extends ContextTestSupport {

    // counted down by each exchange that looks up the consumer of the suspended route
    private final AtomicReference<CountDownLatch> lookups = new AtomicReference<>(new CountDownLatch(0));

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.addComponent("direct", new DirectComponent() {
            @Override
            protected DirectConsumer getConsumer(String key, boolean block, long timeout) throws InterruptedException {
                lookups.get().countDown();
                return super.getConsumer(key, block, timeout);
            }
        });
        return context;
    }

    @Test
    public void testExchangesWaitForSuspendedConsumer() throws Exception {
        testExchangesWaitForConsumer(() -> context.getRouteController().suspendRoute("b"),
                () -> context.getRouteController().resumeRoute("b"));
    }

    @Test
    public void testExchangesWaitForStoppedConsumer() throws Exception {
        testExchangesWaitForConsumer(() -> context.getRouteController().stopRoute("b"),
                () -> context.getRouteController().startRoute("b"));
    }

    private void testExchangesWaitForConsumer(RouteAction removeConsumer, RouteAction addConsumer) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:b");
        mock.expectedBodiesReceived("warm");
        // the producer of direct:b now caches the consumer of route b
        template.sendBody("direct:start", "warm");
        mock.assertIsSatisfied();

        removeConsumer.run();

        CountDownLatch firstWaiting = new CountDownLatch(1);
        lookups.set(firstWaiting);
        CompletableFuture<Object> first = template.asyncSendBody("direct:start", "first");
        // the first exchange found that the consumer changed and waits for the consumer
        assertTrue(firstWaiting.await(10, TimeUnit.SECONDS));

        CountDownLatch secondWaiting = new CountDownLatch(1);
        lookups.set(secondWaiting);
        CompletableFuture<Object> second = template.asyncSendBody("direct:start", "second");

        // the second exchange must wait for the consumer too, and not reach route b while its consumer is gone
        await().atMost(10, TimeUnit.SECONDS).until(() -> secondWaiting.getCount() == 0 || second.isDone());
        assertEquals(1, mock.getReceivedCounter(), "No exchange should reach route b while its consumer is gone");
        assertFalse(second.isDone(), "The second exchange should wait for the consumer of route b");

        mock.reset();
        mock.expectedBodiesReceivedInAnyOrder("first", "second");
        addConsumer.run();

        first.get(10, TimeUnit.SECONDS);
        second.get(10, TimeUnit.SECONDS);
        mock.assertIsSatisfied();
    }

    @FunctionalInterface
    private interface RouteAction {
        void run() throws Exception;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").to("direct:b?timeout=20000");

                from("direct:b").routeId("b").to("mock:b");
            }
        };
    }
}
