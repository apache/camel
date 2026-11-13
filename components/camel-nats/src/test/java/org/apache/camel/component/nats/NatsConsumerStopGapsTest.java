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

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamManagement;
import io.nats.client.JetStreamStatusException;
import io.nats.client.JetStreamSubscription;
import io.nats.client.PullSubscribeOptions;
import io.nats.client.api.ServerInfo;
import io.nats.client.api.StreamInfo;
import io.nats.client.support.DateTimeUtils;
import io.nats.client.support.JsonParser;
import io.nats.client.support.Status;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.nats.NatsConsumerPullConsumerDeletedTest.FakeServer.proxy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stop of a NATS consumer when the flush of the connection fails, and when the consumer stops while its JetStream
 * pull subscribe, or its subscribe again after the server deleted the consumer, is in flight.
 */
class NatsConsumerStopGapsTest extends CamelTestSupport {

    private final NatsConsumerJetStreamPushStopTest.FakeServer server = new NatsConsumerJetStreamPushStopTest.FakeServer();

    // the pull subscribe of the second test
    private final CountDownLatch subscribing = new CountDownLatch(1);
    private final CountDownLatch subscribeDone = new CountDownLatch(1);
    private final AtomicInteger pullUnsubscribes = new AtomicInteger();
    private final AtomicBoolean pullActive = new AtomicBoolean(true);
    private final AtomicBoolean unsubscribedOnInterruptedThread = new AtomicBoolean();

    // the pull subscribe again of the third test
    private final CountDownLatch resubscribing = new CountDownLatch(1);
    private final CountDownLatch resubscribeDone = new CountDownLatch(1);
    private final AtomicInteger newPullUnsubscribes = new AtomicInteger();
    private final AtomicBoolean newPullActive = new AtomicBoolean(true);
    private final AtomicBoolean newUnsubscribedOnInterruptedThread = new AtomicBoolean();

    NatsConsumerStopGapsTest() {
        testConfiguration().withUseRouteBuilder(false);
    }

    @Test
    void stopWhenTheFlushFailsEndsTheSubscription() throws Exception {
        context.getRegistry().bind("conn", server.connection());
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("nats:orders?connection=#conn&jetstreamEnabled=true&jetstreamName=ORDERS&pullSubscription=false")
                        .routeId("orders").to("mock:result");
            }
        });
        context.start();
        await().atMost(10, TimeUnit.SECONDS).until(() -> server.activeSubscriptions() == 1);

        // jnats fails the flush of a connection that is not connected (the server is down)
        server.onFlush = () -> sneakyThrow(new TimeoutException("Timeout out waiting for connection before flush."));

        assertDoesNotThrow(() -> context.getRouteController().stopRoute("orders"));

        // the stop still ends the subscription and closes the dispatcher
        assertEquals(0, server.activeSubscriptions());
        await().atMost(10, TimeUnit.SECONDS).until(() -> server.closedDispatchers() == 1);
        assertTrue(context.getRouteController().getRouteStatus("orders").isStopped());
    }

    @Test
    void pullSubscriptionMadeWhileStoppingIsEnded() throws Exception {
        context.getRegistry().bind("conn", pullConnection());
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("nats:orders?connection=#conn&jetstreamEnabled=true&jetstreamName=ORDERS&flushConnection=false")
                        .routeId("orders").to("mock:result");
            }
        });
        context.start();
        assertTrue(subscribing.await(10, TimeUnit.SECONDS));

        // the subscribe completes just before the stop interrupts the task, and the task stores the subscription after
        // doStop looked for it
        context.getRouteController().stopRoute("orders");
        subscribeDone.countDown();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(1, pullUnsubscribes.get(),
                "the subscription made for the stopped consumer must be ended"));
        assertFalse(unsubscribedOnInterruptedThread.get(), "the unsubscribe must not run on an interrupted thread");
    }

    @Test
    void pullSubscriptionMadeAgainWhileStoppingIsEnded() throws Exception {
        context.getRegistry().bind("conn", resubscribeConnection());
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("nats:orders?connection=#conn&jetstreamEnabled=true&jetstreamName=ORDERS&flushConnection=false")
                        .routeId("orders").to("mock:result");
            }
        });
        context.start();
        // the first fetch tells that the server deleted the ephemeral consumer, and the task subscribes again
        assertTrue(resubscribing.await(10, TimeUnit.SECONDS));

        // the subscribe again completes just before the stop interrupts the task, and the task stores the new
        // subscription after doStop looked for it
        context.getRouteController().stopRoute("orders");
        resubscribeDone.countDown();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(1, newPullUnsubscribes.get(),
                "the subscription made again for the stopped consumer must be ended"));
        assertFalse(newUnsubscribedOnInterruptedThread.get(), "the unsubscribe must not run on an interrupted thread");
    }

    @Test
    void producerStopWhenTheFlushFailsClosesItsConnection() throws Exception {
        AtomicInteger closes = new AtomicInteger();
        Connection connection = proxy(Connection.class, (name, args) -> switch (name) {
            case "flush" -> throw new TimeoutException("Timeout out waiting for connection before flush.");
            case "close" -> {
                closes.incrementAndGet();
                yield null;
            }
            case "getStatus" -> Connection.Status.DISCONNECTED;
            default -> null;
        });
        context.start();
        NatsComponent component = context.getComponent("nats", NatsComponent.class);
        NatsConfiguration configuration = new NatsConfiguration();
        configuration.setTopic("orders");
        // the producer creates its own connection (no connection option), which the stop must close
        NatsEndpoint endpoint = new NatsEndpoint("nats:orders", component, configuration) {
            @Override
            public Connection getConnection() {
                return connection;
            }
        };
        endpoint.setCamelContext(context);
        Producer producer = endpoint.createProducer();
        producer.start();

        assertDoesNotThrow(producer::stop);

        assertEquals(1, closes.get(), "the connection of the producer must be closed");
        assertTrue(((NatsProducer) producer).isStopped());
    }

    private Connection pullConnection() {
        StreamInfo streamInfo = new StreamInfo(
                JsonParser.parseUnchecked("{\"config\":{\"name\":\"ORDERS\",\"subjects\":[\"orders\"]},\"created\":\""
                                          + DateTimeUtils.toRfc3339(ZonedDateTime.now()) + "\",\"state\":{}}"));
        JetStreamManagement jsm = proxy(JetStreamManagement.class, (name, args) -> switch (name) {
            case "getStreamInfo", "addStream" -> streamInfo;
            default -> null;
        });
        JetStreamSubscription subscription = proxy(JetStreamSubscription.class, (name, args) -> switch (name) {
            case "isActive" -> pullActive.get();
            case "unsubscribe" -> {
                // jnats drops the UNSUB that it queues on an interrupted thread
                unsubscribedOnInterruptedThread.compareAndSet(false, Thread.currentThread().isInterrupted());
                pullUnsubscribes.incrementAndGet();
                pullActive.set(false);
                yield null;
            }
            case "fetch" -> List.of();
            default -> null;
        });
        JetStream js = proxy(JetStream.class, (name, args) -> {
            if ("subscribe".equals(name) && args.length == 2 && args[1] instanceof PullSubscribeOptions) {
                subscribing.countDown();
                // the subscribe completed before the interrupt of the stop reached it: the task gets the subscription
                // after doStop looked for it
                boolean interrupted = false;
                while (true) {
                    try {
                        assertTrue(subscribeDone.await(20, TimeUnit.SECONDS));
                        break;
                    } catch (InterruptedException e) {
                        interrupted = true;
                    }
                }
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
                return subscription;
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

    private Connection resubscribeConnection() {
        StreamInfo streamInfo = new StreamInfo(
                JsonParser.parseUnchecked("{\"config\":{\"name\":\"ORDERS\",\"subjects\":[\"orders\"]},\"created\":\""
                                          + DateTimeUtils.toRfc3339(ZonedDateTime.now()) + "\",\"state\":{}}"));
        JetStreamManagement jsm = proxy(JetStreamManagement.class, (name, args) -> switch (name) {
            case "getStreamInfo", "addStream" -> streamInfo;
            default -> null;
        });
        // the subscription of the first subscribe, whose consumer the server deleted
        AtomicBoolean firstActive = new AtomicBoolean(true);
        AtomicBoolean toldDeleted = new AtomicBoolean();
        JetStreamSubscription[] first = new JetStreamSubscription[1];
        first[0] = proxy(JetStreamSubscription.class, (name, args) -> switch (name) {
            case "isActive" -> firstActive.get();
            case "unsubscribe" -> {
                firstActive.set(false);
                yield null;
            }
            case "fetch" -> {
                if (toldDeleted.compareAndSet(false, true)) {
                    throw new JetStreamStatusException(new Status(409, Status.CONSUMER_DELETED), first[0]);
                }
                yield List.of();
            }
            default -> null;
        });
        // the subscription made again
        JetStreamSubscription again = proxy(JetStreamSubscription.class, (name, args) -> switch (name) {
            case "isActive" -> newPullActive.get();
            case "unsubscribe" -> {
                // jnats drops the UNSUB that it queues on an interrupted thread
                newUnsubscribedOnInterruptedThread.compareAndSet(false, Thread.currentThread().isInterrupted());
                newPullUnsubscribes.incrementAndGet();
                newPullActive.set(false);
                yield null;
            }
            case "fetch" -> List.of();
            default -> null;
        });
        AtomicInteger subscribes = new AtomicInteger();
        JetStream js = proxy(JetStream.class, (name, args) -> {
            if ("subscribe".equals(name) && args.length == 2 && args[1] instanceof PullSubscribeOptions) {
                if (subscribes.incrementAndGet() == 1) {
                    return first[0];
                }
                resubscribing.countDown();
                // the subscribe again completed before the interrupt of the stop reached it: the task gets the new
                // subscription after doStop looked for it
                boolean interrupted = false;
                while (true) {
                    try {
                        assertTrue(resubscribeDone.await(20, TimeUnit.SECONDS));
                        break;
                    } catch (InterruptedException e) {
                        interrupted = true;
                    }
                }
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
                return again;
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

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void sneakyThrow(Throwable e) throws E {
        throw (E) e;
    }
}
