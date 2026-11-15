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
package org.apache.camel.component.nats;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamManagement;
import io.nats.client.JetStreamSubscription;
import io.nats.client.PullSubscribeOptions;
import io.nats.client.api.ServerInfo;
import io.nats.client.api.StreamInfo;
import io.nats.client.support.DateTimeUtils;
import io.nats.client.support.JsonParser;
import org.apache.camel.Consumer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.nats.NatsConsumerPullConsumerDeletedTest.FakeServer.proxy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A NATS consumer that is stopped and started again while the consuming task of the first start is still in its
 * subscribe: when that subscribe returns, the task of the first start must end what it made and leave the second start
 * alone, and the next stop must end what the second start made.
 * <p>
 * A route policy stops the consumer of its route and starts the same consumer again (the throttling policies suspend or
 * stop it and resume or start it, and the NATS consumer is not suspendable). A route that is stopped and started again
 * (or suspended and resumed) gets a new consumer instead.
 */
class NatsConsumerRestartTest extends CamelTestSupport {

    NatsConsumerRestartTest() {
        testConfiguration().withUseRouteBuilder(false);
    }

    @Test
    void pullSubscriptionOfTheStoppedStartIsEndedAndTheNewOneIsKept() throws Exception {
        FakePullSubscription first = new FakePullSubscription();
        FakePullSubscription second = new FakePullSubscription();
        CountDownLatch firstSubscribing = new CountDownLatch(1);
        CountDownLatch firstSubscribeDone = new CountDownLatch(1);
        context.getRegistry().bind("conn", pullConnection(first, second, firstSubscribing, firstSubscribeDone));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("nats:orders?connection=#conn&jetstreamEnabled=true&jetstreamName=ORDERS&flushConnection=false")
                        .routeId("orders").to("mock:result");
            }
        });
        context.start();
        assertTrue(firstSubscribing.await(10, TimeUnit.SECONDS));

        // a quick stop and start of the consumer, as a route policy does, while the subscribe of the first start is in
        // flight
        restartConsumer();
        await().atMost(10, TimeUnit.SECONDS).until(() -> second.fetches.get() > 0);

        // the subscribe of the first start returns now: the consumer runs again, but not for that start
        firstSubscribeDone.countDown();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(1, first.unsubscribes.get(),
                "the subscription made for the stopped start must be ended"));
        assertFalse(first.unsubscribedOnInterruptedThread.get(), "the unsubscribe must not run on an interrupted thread");
        assertEquals(0, first.fetches.get(), "the task of the stopped start must not fetch");
        assertTrue(second.active.get(), "the subscription of the new start must be kept");

        context.getRouteController().stopRoute("orders");

        assertEquals(1, second.unsubscribes.get(), "the stop must end the subscription of the new start");
        assertEquals(1, first.unsubscribes.get());
    }

    @Test
    void dispatcherOfTheStoppedStartIsClosedAndTheNewOneIsKept() throws Exception {
        NatsConsumerJetStreamPushStopTest.FakeServer server = new NatsConsumerJetStreamPushStopTest.FakeServer();
        CountDownLatch firstCreating = new CountDownLatch(1);
        CountDownLatch firstCreateDone = new CountDownLatch(1);
        AtomicInteger creates = new AtomicInteger();
        server.afterCreateDispatcher = () -> {
            if (creates.incrementAndGet() == 1) {
                // the task of the first start gets its dispatcher only after the stop and the next start
                firstCreating.countDown();
                awaitUninterruptibly(firstCreateDone);
            }
        };
        context.getRegistry().bind("conn", server.connection());
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("nats:orders?connection=#conn&jetstreamEnabled=true&jetstreamName=ORDERS&pullSubscription=false")
                        .routeId("orders").to("mock:result");
            }
        });
        context.start();
        assertTrue(firstCreating.await(10, TimeUnit.SECONDS));

        // a quick stop and start of the consumer, as a route policy does, while the first start is creating its
        // dispatcher
        restartConsumer();
        await().atMost(10, TimeUnit.SECONDS).until(() -> server.activeSubscriptions() == 1);

        // the task of the first start gets its dispatcher now: the consumer runs again, but not for that start
        firstCreateDone.countDown();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(1, server.closedDispatchers(),
                "the dispatcher made for the stopped start must be closed"));
        assertEquals(1, server.activeSubscriptions(), "the subscription of the new start must be kept");

        // the new start still consumes
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        server.publish("a");
        mock.assertIsSatisfied();

        context.getRouteController().stopRoute("orders");

        // the stop ends the subscription of the new start and closes its dispatcher
        assertEquals(0, server.activeSubscriptions());
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(2, server.closedDispatchers(),
                "the stop must close the dispatcher of the new start"));
    }

    private void restartConsumer() throws Exception {
        Consumer consumer = context.getRoute("orders").getConsumer();
        ServiceHelper.stopService(consumer);
        ServiceHelper.startService(consumer);
    }

    private static Connection pullConnection(
            FakePullSubscription first, FakePullSubscription second, CountDownLatch firstSubscribing,
            CountDownLatch firstSubscribeDone) {
        StreamInfo streamInfo = new StreamInfo(
                JsonParser.parseUnchecked("{\"config\":{\"name\":\"ORDERS\",\"subjects\":[\"orders\"]},\"created\":\""
                                          + DateTimeUtils.toRfc3339(ZonedDateTime.now()) + "\",\"state\":{}}"));
        JetStreamManagement jsm = proxy(JetStreamManagement.class, (name, args) -> switch (name) {
            case "getStreamInfo", "addStream" -> streamInfo;
            default -> null;
        });
        AtomicInteger subscribes = new AtomicInteger();
        JetStream js = proxy(JetStream.class, (name, args) -> {
            if ("subscribe".equals(name) && args.length == 2 && args[1] instanceof PullSubscribeOptions) {
                if (subscribes.incrementAndGet() > 1) {
                    return second.subscription;
                }
                firstSubscribing.countDown();
                // the subscribe completes only after the stop and the next start (the interrupt of the stop does not
                // end it)
                awaitUninterruptibly(firstSubscribeDone);
                return first.subscription;
            }
            throw new UnsupportedOperationException(name);
        });
        ServerInfo info = new ServerInfo("INFO {\"server_id\":\"fake\",\"jetstream\":true}");
        return proxy(Connection.class, (name, args) -> switch (name) {
            case "getServerInfo" -> info;
            case "jetStreamManagement" -> jsm;
            case "jetStream" -> js;
            case "getStatus" -> Connection.Status.CONNECTED;
            default -> null;
        });
    }

    /**
     * Waits for the latch and keeps the interrupt of the thread for afterwards, as a subscribe that the interrupt does
     * not end.
     */
    private static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                assertTrue(latch.await(20, TimeUnit.SECONDS));
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class FakePullSubscription {
        final AtomicBoolean active = new AtomicBoolean(true);
        final AtomicInteger unsubscribes = new AtomicInteger();
        final AtomicInteger fetches = new AtomicInteger();
        final AtomicBoolean unsubscribedOnInterruptedThread = new AtomicBoolean();
        final JetStreamSubscription subscription = proxy(JetStreamSubscription.class, (name, args) -> switch (name) {
            case "isActive" -> active.get();
            case "unsubscribe" -> {
                // jnats drops the UNSUB that it queues on an interrupted thread
                unsubscribedOnInterruptedThread.compareAndSet(false, Thread.currentThread().isInterrupted());
                unsubscribes.incrementAndGet();
                active.set(false);
                yield null;
            }
            case "fetch" -> {
                fetches.incrementAndGet();
                // an idle consumer: the fetch ends empty at its timeout (an interrupt ends it early, and is kept)
                try {
                    new CountDownLatch(1).await(((Duration) args[1]).toMillis(), TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                yield List.of();
            }
            default -> null;
        });
    }
}
