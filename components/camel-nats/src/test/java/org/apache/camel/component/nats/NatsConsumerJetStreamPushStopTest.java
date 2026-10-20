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

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.JetStream;
import io.nats.client.JetStreamManagement;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import io.nats.client.PushSubscribeOptions;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.DeliverPolicy;
import io.nats.client.api.ServerInfo;
import io.nats.client.impl.NatsJetStreamMetaData;
import io.nats.client.impl.NatsMessage;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A JetStream push subscription belongs to the dispatcher of the consumer, and jnats refuses to unsubscribe it directly
 * ("Subscriptions that belong to a dispatcher cannot respond to unsubscribe directly"). When the connection is shared
 * (connection option) it stays open when the route stops, so the consumer must unsubscribe through its dispatcher;
 * otherwise the stopped route keeps getting messages, and after a restart every message is routed twice.
 */
class NatsConsumerJetStreamPushStopTest extends CamelTestSupport {

    private final FakeServer server = new FakeServer();
    private final CountDownLatch slowEntered = new CountDownLatch(1);
    private final CountDownLatch releaseSlow = new CountDownLatch(1);
    private final AtomicBoolean slowInterrupted = new AtomicBoolean();
    private final AtomicBoolean slowDone = new AtomicBoolean();

    @Override
    protected RouteBuilder createRouteBuilder() {
        context.getRegistry().bind("conn", server.connection());
        context.getRegistry().bind("cc", ConsumerConfiguration.builder().deliverPolicy(DeliverPolicy.New).build());
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("nats:orders?connection=#conn&jetstreamEnabled=true&jetstreamName=ORDERS&pullSubscription=false"
                     + "&consumerConfiguration=#cc")
                        .routeId("orders")
                        .convertBodyTo(String.class)
                        .process(e -> {
                            if ("slow".equals(e.getMessage().getBody())) {
                                // an exchange in flight while the route stops, such as a call that waits on I/O
                                slowEntered.countDown();
                                try {
                                    releaseSlow.await(20, TimeUnit.SECONDS);
                                } catch (InterruptedException ie) {
                                    slowInterrupted.set(true);
                                    Thread.currentThread().interrupt();
                                    throw ie;
                                }
                                slowDone.set(true);
                            }
                        })
                        .to("mock:result");
            }
        };
    }

    @Test
    void stoppedRouteGetsNoMessagesAndRestartedRouteGetsEachMessageOnce() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("order-1");
        server.publish("order-1");
        mock.assertIsSatisfied();

        context.getRouteController().stopRoute("orders");
        int afterStop = server.activeSubscriptions();

        // the route is started again: one subscription, each message routed once
        context.getRouteController().startRoute("orders");
        await().atMost(10, TimeUnit.SECONDS).until(() -> server.activeSubscriptions() > 0);
        mock.reset();
        mock.expectedBodiesReceived("order-2");
        // and no second copy shortly after
        mock.setAssertPeriod(500);
        server.publish("order-2");
        mock.assertIsSatisfied();
        assertEquals(0, afterStop, "no subscription left once the route stopped");
        assertEquals(1, server.activeSubscriptions(), "one subscription once the route started again");
        assertEquals(1, server.closedDispatchers(), "the dispatcher of the first run is closed");
    }

    @Test
    void stoppingTheRouteDoesNotInterruptTheExchangeInFlight() throws Exception {
        server.publish("slow");
        assertTrue(slowEntered.await(10, TimeUnit.SECONDS), "the exchange is in flight");

        // the shutdown strategy stops the consumer (it is not Suspendable) and then waits for the exchange in flight
        Thread stopper = new Thread(() -> {
            try {
                context.getRouteController().stopRoute("orders");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }, "route-stopper");
        stopper.start();
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> ServiceHelper.isStopped(context.getRoute("orders").getConsumer()));
        assertEquals(0, server.activeSubscriptions(), "no subscription left once the consumer stopped");
        releaseSlow.countDown();
        stopper.join(TimeUnit.SECONDS.toMillis(20));

        assertFalse(slowInterrupted.get(), "the step in flight must not be interrupted by the stop");
        assertTrue(slowDone.get(), "the step in flight completes");
        // the dispatcher (and its thread) is closed once the message is done
        await().atMost(10, TimeUnit.SECONDS).until(() -> server.closedDispatchers() == 1);
    }

    /**
     * A JetStream server with one stream, faked at the level of the jnats interfaces the consumer calls. Each push
     * subscription has its own consumer (DeliverPolicy.New starts after the last published message) which delivers on
     * its own thread while the subscription is active. Like jnats, a subscription that belongs to a dispatcher cannot
     * unsubscribe directly, the dispatcher unsubscribes it by subscription (not by the topic: a push subscription is on
     * its deliver subject), and closing the dispatcher unsubscribes all its subscriptions and interrupts the thread
     * that delivers its messages (jnats NatsDispatcher.stop cancels its thread with Future.cancel(true)). A closed
     * dispatcher refuses to subscribe or unsubscribe ("Dispatcher is closed").
     */
    static final class FakeServer {
        final List<byte[]> stream = new ArrayList<>();
        final List<FakeSubscription> subscriptions = new ArrayList<>();
        final Map<JetStreamSubscription, FakeSubscription> bySubscription = new IdentityHashMap<>();
        final Set<Dispatcher> closed = Collections.newSetFromMap(new IdentityHashMap<>());

        final class FakeSubscription {
            final Dispatcher dispatcher;
            Thread thread;
            long next;
            boolean active = true;
            final List<Long> redeliver = new ArrayList<>();
            final Map<Long, Integer> naks = new HashMap<>();

            FakeSubscription(Dispatcher dispatcher) {
                this.dispatcher = dispatcher;
            }
        }

        synchronized void publish(String body) {
            stream.add(body.getBytes(StandardCharsets.UTF_8));
            notifyAll();
        }

        synchronized int closedDispatchers() {
            return closed.size();
        }

        synchronized int activeSubscriptions() {
            return (int) subscriptions.stream().filter(s -> s.active).count();
        }

        Connection connection() {
            JetStreamManagement jsm = proxy(JetStreamManagement.class, (name, args) -> null);
            JetStream js = proxy(JetStream.class, (name, args) -> {
                if ("subscribe".equals(name) && args.length == 6 && args[5] instanceof PushSubscribeOptions o) {
                    return subscribe(o.getConsumerConfiguration(), (Dispatcher) args[2], (MessageHandler) args[3]);
                }
                throw new UnsupportedOperationException(name);
            });
            ServerInfo info = new ServerInfo("INFO {\"server_id\":\"fake\",\"jetstream\":true}");
            return proxy(Connection.class, (name, args) -> switch (name) {
                case "getServerInfo" -> info;
                case "jetStreamManagement" -> jsm;
                case "jetStream" -> js;
                case "createDispatcher" -> createDispatcher();
                case "closeDispatcher" -> {
                    closeDispatcher((Dispatcher) args[0]);
                    yield null;
                }
                case "getStatus" -> Connection.Status.CONNECTED;
                default -> null;
            });
        }

        private Dispatcher createDispatcher() {
            Dispatcher[] self = new Dispatcher[1];
            self[0] = proxy(Dispatcher.class, (name, args) -> {
                synchronized (this) {
                    if (closed.contains(self[0])) {
                        throw new IllegalStateException("Dispatcher is closed");
                    }
                }
                if ("unsubscribe".equals(name) && args[0] instanceof JetStreamSubscription s) {
                    unsubscribe(s);
                }
                // unsubscribe by subject: a push subscription is on its deliver subject, not on the topic
                return self[0];
            });
            return self[0];
        }

        private synchronized void unsubscribe(JetStreamSubscription s) {
            FakeSubscription f = bySubscription.get(s);
            if (f != null) {
                f.active = false;
                notifyAll();
            }
        }

        private synchronized void closeDispatcher(Dispatcher d) {
            if (!closed.add(d)) {
                throw new IllegalArgumentException("Dispatcher is already closed.");
            }
            for (FakeSubscription f : subscriptions) {
                if (f.dispatcher == d) {
                    f.active = false;
                    // the thread of the dispatcher is cancelled with an interrupt
                    f.thread.interrupt();
                }
            }
            notifyAll();
        }

        private synchronized JetStreamSubscription subscribe(
                ConsumerConfiguration cc, Dispatcher dispatcher, MessageHandler handler) {
            if (closed.contains(dispatcher)) {
                throw new IllegalStateException("Dispatcher is closed");
            }
            FakeSubscription f = new FakeSubscription(dispatcher);
            f.next = cc != null && cc.getDeliverPolicy() == DeliverPolicy.New ? stream.size() + 1 : 1;
            subscriptions.add(f);
            JetStreamSubscription sub = proxy(JetStreamSubscription.class, (name, args) -> switch (name) {
                case "unsubscribe" -> throw new IllegalStateException(
                        "Subscriptions that belong to a dispatcher cannot respond to unsubscribe directly.");
                case "isActive" -> {
                    synchronized (this) {
                        yield f.active;
                    }
                }
                case "getDispatcher" -> f.dispatcher;
                default -> null;
            });
            bySubscription.put(sub, f);
            Thread t = new Thread(() -> deliver(f, sub, handler), "fake-push-" + subscriptions.size());
            t.setDaemon(true);
            f.thread = t;
            t.start();
            return sub;
        }

        private void deliver(FakeSubscription f, JetStreamSubscription sub, MessageHandler handler) {
            while (true) {
                long seq;
                synchronized (this) {
                    while (f.active && f.redeliver.isEmpty() && f.next > stream.size()) {
                        try {
                            wait();
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                    if (!f.active) {
                        return;
                    }
                    seq = f.redeliver.isEmpty() ? f.next++ : f.redeliver.remove(0);
                }
                try {
                    handler.onMessage(message(f, sub, seq));
                } catch (InterruptedException e) {
                    return;
                }
            }
        }

        private Message message(FakeSubscription f, JetStreamSubscription sub, long seq) {
            byte[] data;
            synchronized (this) {
                data = stream.get((int) seq - 1);
            }
            // a JetStream message carries its metadata in the reply subject of the acknowledgement
            NatsJetStreamMetaData meta = new NatsJetStreamMetaData(
                    new NatsMessage("orders", "$JS.ACK.ORDERS.c.1." + seq + "." + seq + ".0.0", data) {
                        @Override
                        public boolean isJetStream() {
                            return true;
                        }
                    });
            return proxy(Message.class, (name, args) -> switch (name) {
                case "getData" -> data;
                case "getSubject" -> "orders";
                case "getSID" -> "1";
                case "getSubscription" -> sub;
                case "isJetStream" -> true;
                case "isStatusMessage" -> false;
                case "metaData" -> meta;
                case "nak", "nakWithDelay" -> {
                    synchronized (this) {
                        // like maxDeliver=3: a message nak'ed three times is not delivered again
                        if (f.naks.merge(seq, 1, Integer::sum) < 3 && f.active) {
                            f.redeliver.add(seq);
                            notifyAll();
                        }
                    }
                    yield null;
                }
                default -> null;
            });
        }

        @FunctionalInterface
        interface Handler {
            Object handle(String method, Object[] args) throws Exception;
        }

        @SuppressWarnings("unchecked")
        static <T> T proxy(Class<T> type, Handler handler) {
            return (T) Proxy.newProxyInstance(FakeServer.class.getClassLoader(), new Class<?>[] { type },
                    (p, m, a) -> {
                        if (m.getDeclaringClass() == Object.class) {
                            return switch (m.getName()) {
                                case "hashCode" -> System.identityHashCode(p);
                                case "equals" -> p == a[0];
                                default -> type.getSimpleName() + "@fake";
                            };
                        }
                        Object r = handler.handle(m.getName(), a == null ? new Object[0] : a);
                        if (r == null && m.getReturnType() == boolean.class) {
                            return false;
                        }
                        return r;
                    });
        }
    }
}
