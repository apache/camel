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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreams;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsumerStopTest extends BaseReactiveTest {

    private final CountDownLatch gateReached = new CountDownLatch(1);
    private final CountDownLatch gate = new CountDownLatch(1);
    private final CountDownLatch policyStopped = new CountDownLatch(1);
    private final List<Integer> received = new CopyOnWriteArrayList<>();

    @Test
    void testStopProcessesTheExchangesTakenFromTheStream() throws Exception {
        startWithQueuedExchanges("queued", 1);

        stopWhileTheFirstExchangeWaits("queued");

        assertEquals(IntStream.rangeClosed(1, 10).boxed().toList(), received,
                "The exchanges taken from the stream before the stop must be processed");
    }

    @Test
    void testConsumesAgainAfterRestart() throws Exception {
        startWithQueuedExchanges("queued", 1);
        stopWhileTheFirstExchangeWaits("queued");

        context.getRouteController().startRoute("queued");

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                () -> assertTrue(received.contains(11), "The route must consume again after its restart"));
    }

    @Test
    void testStopFromTheRouteDoesNotWaitForItself() throws Exception {
        // a stop that waited for the pool from one of its threads would wait for this timeout
        context.getExecutorServiceManager().setShutdownAwaitTermination(60000);
        startWithQueuedExchanges("policy", 1);

        gate.countDown();

        assertTrue(policyStopped.await(20, TimeUnit.SECONDS), "The route policy must be able to stop the consumer");
        // the exchanges queued behind the one that stopped the consumer still complete
        ReactiveStreamsCamelSubscriber subscriber
                = (ReactiveStreamsCamelSubscriber) CamelReactiveStreams.get(context).streamSubscriber("policy");
        await().atMost(10, TimeUnit.SECONDS).until(() -> subscriber.getInflightCount() == 0);

        ServiceHelper.startService(context.getRoute("policy").getConsumer());

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                () -> assertTrue(received.contains(11), "The consumer must consume again after its restart"));
    }

    private void startWithQueuedExchanges(String stream, int waiting) throws Exception {
        Flux.range(1, 1000).subscribe(CamelReactiveStreams.get(context).streamSubscriber(stream, Integer.class));

        // the publisher sent the 10 requested numbers: one waits in the route, the others are queued or done
        assertTrue(gateReached.await(10, TimeUnit.SECONDS));
        ReactiveStreamsCamelSubscriber subscriber
                = (ReactiveStreamsCamelSubscriber) CamelReactiveStreams.get(context).streamSubscriber(stream);
        await().atMost(10, TimeUnit.SECONDS).until(() -> subscriber.getInflightCount() == 11 - waiting);
    }

    private void stopWhileTheFirstExchangeWaits(String routeId) throws Exception {
        CompletableFuture<Void> stop = CompletableFuture.runAsync(() -> {
            try {
                context.getRouteController().stopRoute(routeId);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        ServiceSupport consumer = (ServiceSupport) context.getRoute(routeId).getConsumer();
        // the graceful stop suspends the consumer first, and stops it once the exchange in flight is done
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> consumer.isSuspended() || consumer.isStopping() || consumer.isStopped());
        gate.countDown();
        stop.get(20, TimeUnit.SECONDS);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("reactive-streams:queued?maxInflightExchanges=10").routeId("queued")
                        .process(e -> {
                            if (e.getMessage().getBody(Integer.class) == 1) {
                                gateReached.countDown();
                                gate.await();
                            }
                            received.add(e.getMessage().getBody(Integer.class));
                        });

                // a route policy that stops the consumer when the first exchange is done, on the thread of the consumer
                from("reactive-streams:policy?maxInflightExchanges=10").routeId("policy")
                        .routePolicy(new RoutePolicySupport() {
                            @Override
                            public void onExchangeDone(Route route, Exchange exchange) {
                                if (exchange.getMessage().getBody(Integer.class) == 1) {
                                    try {
                                        stopConsumer(route.getConsumer());
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                    policyStopped.countDown();
                                }
                            }
                        })
                        .process(e -> {
                            if (e.getMessage().getBody(Integer.class) == 1) {
                                gateReached.countDown();
                                gate.await();
                            }
                            received.add(e.getMessage().getBody(Integer.class));
                        });

            }
        };
    }
}
