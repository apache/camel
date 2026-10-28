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
package org.apache.camel.component.reactive.streams;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreams;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A suspended consumer (suspended route, route policies, graceful shutdown) routes no new items and does not wait for
 * the items it already took from the stream; they are routed when it is resumed or stopped.
 */
class ConsumerSuspendTest extends BaseReactiveTest {

    private final CountDownLatch gateReached = new CountDownLatch(1);
    private final CountDownLatch gate = new CountDownLatch(1);
    private final CountDownLatch policySuspended = new CountDownLatch(1);

    private final RoutePolicySupport suspendingPolicy = new RoutePolicySupport() {
        @Override
        public void onExchangeDone(Route route, Exchange exchange) {
            // on the thread of the consumer, as ThrottlingInflightRoutePolicy does
            if (exchange.getMessage().getBody(Integer.class) == 3) {
                try {
                    suspendOrStopConsumer(route.getConsumer());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                policySuspended.countDown();
            }
        }
    };

    @AfterEach
    void openGate() {
        // a suspend that waits for the exchange behind the gate must not block the context stop
        gate.countDown();
    }

    @Test
    void testSuspendDoesNotWaitForTheQueuedExchanges() throws Exception {
        // a suspend that drained the queued exchanges would wait for the gate up to this timeout
        context.getExecutorServiceManager().setShutdownAwaitTermination(60000);
        Flux.range(1, 1000).subscribe(CamelReactiveStreams.get(context).streamSubscriber("queued", Integer.class));
        // the publisher sent the 10 requested numbers: the first one waits in the route, the others are queued
        assertTrue(gateReached.await(10, TimeUnit.SECONDS));
        ReactiveStreamsCamelSubscriber subscriber
                = (ReactiveStreamsCamelSubscriber) CamelReactiveStreams.get(context).streamSubscriber("queued");
        await().atMost(10, TimeUnit.SECONDS).until(() -> subscriber.getInflightCount() == 10);

        Consumer consumer = context.getRoute("queued").getConsumer();
        CompletableFuture.runAsync(() -> ServiceHelper.suspendService(consumer)).get(10, TimeUnit.SECONDS);
        assertTrue(((ServiceSupport) consumer).isSuspended(), "The consumer must be suspended");

        // the exchange in flight completes, the queued ones are not routed while the consumer is suspended
        MockEndpoint mock = getMockEndpoint("mock:queued");
        mock.expectedBodiesReceived(1);
        mock.setAssertPeriod(500);
        gate.countDown();
        mock.assertIsSatisfied();
        // and no more items are requested from the stream (the refill watermark would request one per exchange done)
        assertEquals(9, subscriber.getInflightCount(), "The queued items must be held");
        assertEquals(0, subscriber.getRequested(), "A suspended consumer must not request items");

        // a stop routes the items the suspended consumer took from the stream
        mock.reset();
        mock.expectedBodiesReceived(IntStream.rangeClosed(2, 10).boxed().toList());
        context.getRouteController().stopRoute("queued");
        mock.assertIsSatisfied();
        assertEquals(0, subscriber.getInflightCount());
    }

    @Test
    void testSuspendAndResumeRoute() throws Exception {
        Sinks.Many<Integer> sink = Sinks.many().unicast().onBackpressureBuffer();
        sink.asFlux().subscribe(CamelReactiveStreams.get(context).streamSubscriber("controlled", Integer.class));
        MockEndpoint mock = getMockEndpoint("mock:controlled");
        mock.expectedBodiesReceived(1, 2, 3, 4, 5);
        IntStream.rangeClosed(1, 5).forEach(sink::tryEmitNext);
        mock.assertIsSatisfied();

        context.getRouteController().suspendRoute("controlled");
        assertEquals(ServiceStatus.Suspended, context.getRouteController().getRouteStatus("controlled"));
        assertTrue(((ServiceSupport) context.getRoute("controlled").getConsumer()).isSuspended(),
                "The consumer must be suspended");

        // the items sent for the outstanding demand are held, the others stay with the publisher
        mock.reset();
        mock.expectedMessageCount(0);
        mock.setAssertPeriod(500);
        IntStream.rangeClosed(6, 30).forEach(sink::tryEmitNext);
        mock.assertIsSatisfied();

        mock.reset();
        mock.setAssertPeriod(0);
        mock.expectedBodiesReceived(IntStream.rangeClosed(6, 30).boxed().toList());
        context.getRouteController().resumeRoute("controlled");
        assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("controlled"));
        mock.assertIsSatisfied();
    }

    @Test
    void testRequestToASuspendedRouteWaitsForTheResume() throws Exception {
        context.getRouteController().suspendRoute("controlled");
        MockEndpoint mock = getMockEndpoint("mock:controlled");
        mock.expectedMessageCount(0);
        mock.setAssertPeriod(500);

        CompletableFuture<Exchange> reply = Mono.from(CamelReactiveStreams.get(context).toStream("controlled", 7)).toFuture();
        mock.assertIsSatisfied();
        assertFalse(reply.isDone(), "A suspended route must not process the request");

        mock.reset();
        mock.setAssertPeriod(0);
        mock.expectedBodiesReceived(7);
        context.getRouteController().resumeRoute("controlled");
        mock.assertIsSatisfied();
        assertEquals(7, reply.get(10, TimeUnit.SECONDS).getMessage().getBody(Integer.class));
    }

    @Test
    void testRoutePolicySuspendsAndResumesTheConsumer() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:policy");
        mock.expectedBodiesReceived(1, 2, 3);
        mock.setAssertPeriod(500);
        Flux.range(1, 50).subscribe(CamelReactiveStreams.get(context).streamSubscriber("policy", Integer.class));
        assertTrue(policySuspended.await(10, TimeUnit.SECONDS), "The route policy must suspend the consumer");
        Consumer consumer = context.getRoute("policy").getConsumer();
        assertTrue(((ServiceSupport) consumer).isSuspended(), "The consumer must be suspended");
        mock.assertIsSatisfied();

        // the items taken from the stream before the suspend are routed after the resume, in order
        mock.reset();
        mock.setAssertPeriod(0);
        mock.expectedBodiesReceived(IntStream.rangeClosed(4, 50).boxed().toList());
        suspendingPolicy.resumeOrStartConsumer(consumer);
        mock.assertIsSatisfied();
    }

    @Test
    void testResumeStartsAConsumerThatWasNotStarted() throws Exception {
        Consumer consumer = context.getRoute("notStarted").getConsumer();
        Flux.range(1, 5).subscribe(CamelReactiveStreams.get(context).streamSubscriber("notStarted", Integer.class));
        MockEndpoint mock = getMockEndpoint("mock:notStarted");
        mock.expectedBodiesReceived(1, 2, 3, 4, 5);

        ServiceHelper.suspendService(consumer);
        ServiceHelper.resumeService(consumer);

        mock.assertIsSatisfied();
        await().atMost(10, TimeUnit.SECONDS).until(() -> ((ServiceSupport) consumer).isStarted());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("reactive-streams:queued?maxInflightExchanges=10&exchangesRefillLowWatermark=1").routeId("queued")
                        .process(e -> {
                            if (e.getMessage().getBody(Integer.class) == 1) {
                                gateReached.countDown();
                                gate.await();
                            }
                        })
                        .to("mock:queued");

                from("reactive-streams:controlled?maxInflightExchanges=10").routeId("controlled")
                        .to("mock:controlled");

                from("reactive-streams:policy?maxInflightExchanges=10").routeId("policy")
                        .routePolicy(suspendingPolicy)
                        .to("mock:policy");

                from("reactive-streams:notStarted").routeId("notStarted").autoStartup(false)
                        .to("mock:notStarted");
            }
        };
    }
}
