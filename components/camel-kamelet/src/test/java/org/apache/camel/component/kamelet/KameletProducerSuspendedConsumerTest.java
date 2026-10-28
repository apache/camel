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
package org.apache.camel.component.kamelet;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * While one exchange waits for the consumer of a suspended or stopped kamelet route, other exchanges sent with the same
 * producer must wait too, and not be sent to the suspended or stopped consumer.
 */
@Timeout(30)
public class KameletProducerSuspendedConsumerTest extends CamelTestSupport {

    // counted down by each exchange that looks up the consumer of the kamelet route
    private final AtomicReference<CountDownLatch> lookups = new AtomicReference<>(new CountDownLatch(0));

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.addComponent("kamelet", new KameletComponent() {
            @Override
            protected KameletConsumer getConsumer(String key, boolean block, long timeout) throws InterruptedException {
                lookups.get().countDown();
                return super.getConsumer(key, block, timeout);
            }
        });
        return context;
    }

    @Test
    public void testExchangesWaitForSuspendedConsumer() throws Exception {
        testExchangesWaitForConsumer(() -> context.getRouteController().suspendRoute("echo"),
                () -> context.getRouteController().resumeRoute("echo"));
    }

    @Test
    public void testExchangesWaitForStoppedConsumer() throws Exception {
        testExchangesWaitForConsumer(() -> context.getRouteController().stopRoute("echo"),
                () -> context.getRouteController().startRoute("echo"));
    }

    private void testExchangesWaitForConsumer(RouteAction removeConsumer, RouteAction addConsumer) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:kamelet");
        mock.expectedBodiesReceived("warm");
        // the producer of the kamelet now caches the consumer of the kamelet route
        template.sendBody("direct:start", "warm");
        mock.assertIsSatisfied();

        removeConsumer.run();

        CountDownLatch firstWaiting = new CountDownLatch(1);
        lookups.set(firstWaiting);
        CompletableFuture<Object> first = template.asyncSendBody("direct:start", "first");
        // the first exchange found that the consumer changed and waits for the consumer
        assertThat(firstWaiting.await(10, TimeUnit.SECONDS)).isTrue();

        CountDownLatch secondWaiting = new CountDownLatch(1);
        lookups.set(secondWaiting);
        CompletableFuture<Object> second = template.asyncSendBody("direct:start", "second");

        // the second exchange must wait for the consumer too, and not reach the kamelet route while its consumer is gone
        await().atMost(10, TimeUnit.SECONDS).until(() -> secondWaiting.getCount() == 0 || second.isDone());
        assertThat(mock.getReceivedCounter()).as("No exchange should reach the kamelet route while its consumer is gone")
                .isEqualTo(1);
        assertThat(second).as("The second exchange should wait for the consumer of the kamelet route").isNotDone();

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
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("echo")
                        .from("kamelet:source")
                        .to("mock:kamelet");

                from("direct:start")
                        .to("kamelet:echo/echo?timeout=20000");
            }
        };
    }
}
