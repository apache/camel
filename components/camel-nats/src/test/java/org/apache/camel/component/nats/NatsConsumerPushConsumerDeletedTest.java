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
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.nats.client.Connection;
import io.nats.client.ConnectionListener;
import io.nats.client.Dispatcher;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import io.nats.client.PushSubscribeOptions;
import io.nats.client.api.AckPolicy;
import io.nats.client.api.ApiResponse;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.ConsumerInfo;
import io.nats.client.api.DeliverPolicy;
import io.nats.client.api.Error;
import io.nats.client.api.RetentionPolicy;
import io.nats.client.api.ServerInfo;
import io.nats.client.api.StreamInfo;
import io.nats.client.impl.AckType;
import io.nats.client.impl.NatsJetStreamMetaData;
import io.nats.client.impl.NatsMessage;
import io.nats.client.support.DateTimeUtils;
import io.nats.client.support.JsonParser;
import io.nats.client.support.Status;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With pullSubscription=false the JetStream consumer uses a push subscription, with an ephemeral consumer when no
 * durableName is set. The NATS server deletes an ephemeral push consumer when nobody is subscribed to its deliver
 * subject for its inactive threshold (5 seconds by default), for example while the client was disconnected for a few
 * seconds, and a server restart loses it. jnats subscribes the deliver subject again after the reconnect but does not
 * create the consumer again. The consumer must then subscribe again, starting at the first message it did not
 * acknowledge, instead of waiting for ever on a deliver subject nobody delivers to. A durable consumer that is gone is
 * not created again (that would replay the stream from its deliver policy).
 */
class NatsConsumerPushConsumerDeletedTest extends CamelTestSupport {

    private final FakeServer server = new FakeServer();
    private final AtomicInteger attempts = new AtomicInteger();
    private final List<Throwable> handled = new CopyOnWriteArrayList<>();

    @Override
    protected RouteBuilder createRouteBuilder() {
        context.getRegistry().bind("conn", server.connection());
        context.getRegistry().bind("handler", new ExceptionHandler() {
            @Override
            public void handleException(Throwable exception) {
                handled.add(exception);
            }

            @Override
            public void handleException(String message, Throwable exception) {
                handled.add(exception);
            }

            @Override
            public void handleException(String message, Exchange exchange, Throwable exception) {
                handled.add(exception);
            }
        });
        // an ephemeral consumer that starts at a time: the server rejects a start time next to a start sequence
        context.getRegistry().bind("byStartTime", ConsumerConfiguration.builder()
                .deliverPolicy(DeliverPolicy.ByStartTime).startTime(ZonedDateTime.now().minusDays(1)).build());
        // an ephemeral consumer of the messages published after the subscribe
        context.getRegistry().bind("deliverNew", ConsumerConfiguration.builder().deliverPolicy(DeliverPolicy.New).build());
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("nats:orders?connection=#conn&jetstreamEnabled=true&jetstreamName=ORDERS&pullSubscription=false")
                        .convertBodyTo(String.class)
                        .process(e -> {
                            if ("fail-once".equals(e.getMessage().getBody()) && attempts.incrementAndGet() == 1) {
                                // the client is disconnected while the message is processed, for longer than the
                                // inactive threshold: the server deletes the consumer, the nak is lost
                                server.disconnect();
                                throw new IllegalStateException("processing failed");
                            }
                        })
                        .to("mock:result");
            }
        };
    }

    @Test
    void consumesAgainAfterTheServerDeletedTheEphemeralConsumer() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("order-1");
        server.publish("order-1");
        mock.assertIsSatisfied();

        // disconnected for longer than the inactive threshold: the server deletes the consumer
        server.disconnect();
        server.publish("order-2");
        server.publish("order-3");
        server.reconnect();

        mock.reset();
        mock.expectedBodiesReceived("order-2", "order-3");
        mock.setResultWaitTime(20_000);
        mock.assertIsSatisfied();
        assertEquals(2, server.consumersCreated, "one consumer created again");
    }

    @Test
    void redeliversTheMessageWhoseNakWasLostWithTheConsumer() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("order-1");
        server.publish("order-1");
        mock.assertIsSatisfied();

        server.publish("fail-once");
        server.awaitDisconnected();
        server.publish("order-3");
        server.reconnect();

        mock.reset();
        mock.expectedBodiesReceived("fail-once", "order-3");
        mock.setResultWaitTime(20_000);
        mock.assertIsSatisfied();
    }

    @Test
    void endsTheSubscriptionItMadeWhileTheRouteStopped() throws Exception {
        consumeWith("exceptionHandler=#handler");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("order-1");
        server.publish("order-1");
        mock.assertIsSatisfied();

        // the route is stopped while the consumer subscribes again: the stop ends the subscription of the deleted
        // consumer only, so the consumer must end the one it makes
        String routeId = context.getRoutes().get(0).getId();
        server.beforeSubscribe = () -> {
            server.beforeSubscribe = null;
            Thread stopper = new Thread(() -> {
                try {
                    context.getRouteController().stopRoute(routeId);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }, "route-stopper");
            stopper.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (stopper.isAlive() && System.nanoTime() < deadline) {
                try {
                    stopper.join(1000);
                } catch (InterruptedException e) {
                    // the stop interrupts the executor of the consumer, which runs this
                }
            }
        };
        int before;
        synchronized (server) {
            before = server.consumers.size();
        }
        server.disconnect();
        server.reconnect();

        // the new subscription is ended, or (when the stop closes the dispatcher) jnats refuses to subscribe on the
        // closed dispatcher; either way no subscription is left and nothing is reported during the normal stop
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            synchronized (server) {
                FakeServer.FakeConsumer last = server.consumers.get(server.consumers.size() - 1);
                assertTrue(server.consumers.size() == before + 1 && last.unsubscribed || server.refusedSubscribes > 0,
                        "the subscription made while the route stopped is ended");
            }
        });
        assertEquals(List.of(), handled, "a subscribe that fails as the route stops is not reported");
    }

    /**
     * Replaces the route of the test by one consuming with the given options.
     */
    private void consumeWith(String options) throws Exception {
        String routeId = context.getRoutes().get(0).getId();
        context.getRouteController().stopRoute(routeId);
        context.removeRoute(routeId);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("nats:orders?connection=#conn&jetstreamEnabled=true&jetstreamName=ORDERS&pullSubscription=false&"
                     + options)
                        .convertBodyTo(String.class)
                        .process(e -> {
                            if ("poison".equals(e.getMessage().getBody())) {
                                throw new IllegalStateException("cannot process the poison message");
                            }
                        })
                        .to("mock:result");
            }
        });
    }

    @Test
    void doesNotCreateAGoneDurableConsumerAgain() throws Exception {
        consumeWith("durableName=orders");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("order-1");
        server.publish("order-1");
        mock.assertIsSatisfied();
        int created;
        synchronized (server) {
            created = server.consumersCreated;
            assertEquals("orders-durable", server.configurations.get(server.configurations.size() - 1).getDurable());
        }

        // the durable consumer is deleted (as by an administrator) while the client is disconnected
        server.disconnect();
        server.reconnect();

        // creating it again would replay the stream from its deliver policy (DeliverPolicy.All: order-1 again)
        await().during(3, TimeUnit.SECONDS).atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            synchronized (server) {
                assertEquals(created, server.consumersCreated, "the durable consumer must not be created again");
            }
        });
        assertEquals(1, mock.getReceivedCounter());
    }

    @Test
    void reportsAFailedSubscribeAndTriesAgain() throws Exception {
        consumeWith("exceptionHandler=#handler");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("order-1");
        server.publish("order-1");
        mock.assertIsSatisfied();

        // jnats reports some subscribe failures as IllegalStateException (SUB-90007 when the stream is gone)
        server.beforeSubscribe = () -> {
            server.beforeSubscribe = null;
            throw new IllegalStateException("[SUB-90007] No matching streams for subject.");
        };
        server.disconnect();
        server.publish("order-2");
        server.reconnect();

        mock.reset();
        mock.expectedBodiesReceived("order-2");
        mock.setResultWaitTime(20_000);
        mock.assertIsSatisfied();
        assertEquals(1, handled.size(), "the failed subscribe goes to the exception handler");
        assertInstanceOf(IllegalStateException.class, handled.get(0));
    }

    @Test
    void resumesAnEphemeralConsumerThatStartedAtATime() throws Exception {
        consumeWith("consumerConfiguration=#byStartTime");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("order-1");
        server.publish("order-1");
        mock.assertIsSatisfied();

        server.disconnect();
        server.publish("order-2");
        server.reconnect();

        mock.reset();
        mock.expectedBodiesReceived("order-2");
        mock.setResultWaitTime(20_000);
        mock.assertIsSatisfied();
    }

    @Test
    void workQueueStreamIsSubscribedAgainWithDeliverPolicyAll() throws Exception {
        server.retention(RetentionPolicy.WorkQueue);
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("order-1");
        server.publish("order-1");
        mock.assertIsSatisfied();
        // the route acknowledges order-1 once the exchange is complete: the stream removes it
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            synchronized (server) {
                assertTrue(server.removed.contains(1L), "order-1 is acknowledged and removed");
            }
        });

        server.disconnect();
        server.publish("order-2");
        server.reconnect();

        // a work queue stream refuses a consumer whose deliver policy is not All (10101); order-1 was acknowledged
        // and is removed, so the consumer configuration replays nothing
        mock.reset();
        mock.expectedBodiesReceived("order-2");
        mock.setResultWaitTime(20_000);
        mock.assertIsSatisfied();
        synchronized (server) {
            assertEquals(2, server.consumersCreated, "one consumer created again");
            assertEquals(DeliverPolicy.All, server.configurations.get(1).getDeliverPolicy(),
                    "a work queue stream accepts only the deliver policy All");
        }
    }

    @Test
    void consumesAStreamCreatedAgainFromItsStart() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("order-1", "order-2");
        server.publish("order-1");
        server.publish("order-2");
        mock.assertIsSatisfied();

        // a memory stream after a server restart: the consumer is gone, and order-3 is sequence 1 of the new stream
        server.disconnect();
        server.createStreamAgain();
        server.publish("order-3");
        server.reconnect();

        mock.reset();
        mock.expectedBodiesReceived("order-3");
        mock.setResultWaitTime(20_000);
        mock.assertIsSatisfied();
        synchronized (server) {
            assertEquals(DeliverPolicy.All, server.configurations.get(1).getDeliverPolicy(),
                    "the consumer configuration, not a start sequence of the old stream");
        }
    }

    @Test
    void ackPolicyNoneResumesAfterTheHighestDelivered() throws Exception {
        // the route never calls ack: with ackPolicy=None the delivery is the acknowledgement
        consumeWith("manualAck=true&ackPolicy=None");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("order-1", "order-2");
        server.publish("order-1");
        server.publish("order-2");
        mock.assertIsSatisfied();

        server.disconnect();
        server.publish("order-3");
        server.reconnect();

        mock.reset();
        mock.expectedBodiesReceived("order-3");
        mock.setResultWaitTime(20_000);
        mock.assertIsSatisfied();
        synchronized (server) {
            ConsumerConfiguration again = server.configurations.get(server.configurations.size() - 1);
            assertEquals(AckPolicy.None, again.getAckPolicy());
            assertEquals(DeliverPolicy.ByStartSequence, again.getDeliverPolicy());
            assertEquals(3, again.getStartSequence(), "starts after the highest delivered sequence");
        }
    }

    @Test
    void aMessageDeliveredMaxDeliverTimesDoesNotHoldBackTheResume() throws Exception {
        consumeWith("maxDeliver=3");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("order-2", "order-3");
        // the route fails the poison message: it is nak'ed until the server stops delivering it (maxDeliver)
        server.publish("poison");
        server.publish("order-2");
        server.publish("order-3");
        mock.assertIsSatisfied();
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            synchronized (server) {
                assertEquals(3, server.consumers.get(server.consumers.size() - 1).deliveries.get(1L),
                        "the poison message is delivered maxDeliver times");
            }
        });

        server.disconnect();
        server.publish("order-4");
        server.reconnect();

        // the server never delivers the poison message again, so the new consumer starts after the highest delivered
        mock.reset();
        mock.expectedBodiesReceived("order-4");
        mock.setResultWaitTime(20_000);
        mock.assertIsSatisfied();
        synchronized (server) {
            ConsumerConfiguration again = server.configurations.get(server.configurations.size() - 1);
            assertEquals(4, again.getStartSequence(), "starts after the highest delivered sequence");
        }
    }

    @Test
    void keepsCheckingUntilTheStreamIsCreatedAgain() throws Exception {
        consumeWith("exceptionHandler=#handler");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("order-1");
        server.publish("order-1");
        mock.assertIsSatisfied();

        // a memory stream lost on a server restart, and created again only later
        server.disconnect();
        server.deleteStream();
        server.reconnect();
        // more checks than the first attempts one second apart
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            synchronized (server) {
                assertTrue(server.streamNotFound >= 6, "the consumer keeps checking, " + server.streamNotFound);
            }
        });
        server.createStreamAgain();
        server.publish("order-2");

        mock.reset();
        mock.expectedBodiesReceived("order-2");
        mock.setResultWaitTime(40_000);
        mock.assertIsSatisfied();
        assertEquals(1, handled.size(), "the first failure goes to the exception handler, the next ones are logged");
    }

    @Test
    void deliverPolicyNewWithNothingDeliveredResumesAfterTheLastMessageAtTheSubscribe() throws Exception {
        consumeWith("consumerConfiguration=#deliverNew");
        // the route subscribes on the executor of the consumer, and then listens to the connection
        await().atMost(10, TimeUnit.SECONDS).until(() -> !server.listeners.isEmpty());

        // nothing was delivered when the consumer is deleted: the messages published meanwhile must not be skipped
        server.disconnect();
        server.publish("order-1");
        server.reconnect();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("order-1");
        mock.setResultWaitTime(20_000);
        mock.assertIsSatisfied();
        synchronized (server) {
            ConsumerConfiguration again = server.configurations.get(server.configurations.size() - 1);
            assertEquals(DeliverPolicy.ByStartSequence, again.getDeliverPolicy());
            assertEquals(1, again.getStartSequence(), "starts after the last message of the stream at the subscribe");
        }
    }

    /**
     * A JetStream server with one stream, faked at the level of the jnats interfaces the consumer calls. A push
     * consumer delivers from its start sequence on its own thread (like the dispatcher of jnats), one message at a
     * time; a nak'ed message is delivered again. While the client is disconnected nothing is delivered and the server
     * deletes the ephemeral consumers (their inactive threshold passes); acknowledgements to a deleted consumer are
     * lost. A reconnect fires RESUBSCRIBED like jnats once it subscribed the deliver subjects again. getConsumerInfo
     * answers null for a deleted consumer, as jnats does when the server answers consumer not found (10014), and the
     * info of a live one. A stream with WorkQueue retention removes the acknowledged messages and refuses a consumer
     * whose deliver policy is not All (400, 10101), and a stream created again starts its sequences at 1 again with a
     * new create time. A message nak'ed is not delivered again once it was delivered maxDeliver times (the delivered
     * count is in the reply subject, as on the server). While the stream does not exist, getStreamInfo and
     * getConsumerInfo throw 404 stream not found (10059) and subscribe throws IllegalStateException (SUB-90007), as
     * jnats does. A closed dispatcher (closeDispatcher) refuses a subscribe with IllegalStateException, as jnats does.
     */
    static final class FakeServer {
        final List<byte[]> stream = new ArrayList<>();
        final Set<Long> removed = new HashSet<>();
        RetentionPolicy retention = RetentionPolicy.Limits;
        ZonedDateTime created = DateTimeUtils.gmtNow().minusHours(1);
        final List<FakeConsumer> consumers = new ArrayList<>();
        final List<ConsumerConfiguration> configurations = new ArrayList<>();
        final List<ConnectionListener> listeners = new CopyOnWriteArrayList<>();
        boolean connected = true;
        boolean streamExists = true;
        // the consumer lookups answered with stream not found
        int streamNotFound;
        // the subscribes refused as their dispatcher was closed
        int refusedSubscribes;
        final Set<Dispatcher> closedDispatchers = new HashSet<>();
        int consumersCreated;
        // runs on the thread that subscribes, before the subscription is made
        volatile Runnable beforeSubscribe;
        Connection connection;

        final class FakeConsumer {
            long maxDeliver;
            final Map<Long, Integer> deliveries = new HashMap<>();
            long next;
            boolean deleted;
            boolean unsubscribed;
            final List<Long> redeliver = new ArrayList<>();
        }

        synchronized void retention(RetentionPolicy retention) {
            this.retention = retention;
        }

        /**
         * The stream is created again, for example a memory stream after a server restart: its sequences start again at
         * 1.
         */
        synchronized void createStreamAgain() {
            streamExists = true;
            stream.clear();
            removed.clear();
            created = created.plusMinutes(1);
            for (FakeConsumer c : consumers) {
                c.deleted = true;
            }
            notifyAll();
        }

        /**
         * The stream is deleted, for example a memory stream lost on a server restart.
         */
        synchronized void deleteStream() {
            streamExists = false;
            stream.clear();
            removed.clear();
            for (FakeConsumer c : consumers) {
                c.deleted = true;
            }
            notifyAll();
        }

        synchronized void publish(String body) {
            stream.add(body.getBytes(StandardCharsets.UTF_8));
            notifyAll();
        }

        synchronized void disconnect() {
            connected = false;
            for (FakeConsumer c : consumers) {
                c.deleted = true;
            }
            notifyAll();
        }

        synchronized void awaitDisconnected() throws InterruptedException {
            long deadline = System.currentTimeMillis() + 20_000;
            while (connected && System.currentTimeMillis() < deadline) {
                wait(100);
            }
        }

        void reconnect() {
            synchronized (this) {
                connected = true;
                notifyAll();
            }
            for (ConnectionListener l : listeners) {
                l.connectionEvent(connection, ConnectionListener.Events.RECONNECTED);
                l.connectionEvent(connection, ConnectionListener.Events.RESUBSCRIBED);
            }
        }

        Connection connection() {
            JetStreamManagement jsm = proxy(JetStreamManagement.class, (name, args) -> switch (name) {
                case "getStreamInfo", "addStream" -> streamInfo();
                default -> null;
            });
            JetStream js = proxy(JetStream.class, (name, args) -> {
                if ("subscribe".equals(name) && args.length == 6 && args[5] instanceof PushSubscribeOptions o) {
                    Runnable hook = beforeSubscribe;
                    if (hook != null) {
                        hook.run();
                    }
                    synchronized (this) {
                        if (closedDispatchers.contains((Dispatcher) args[2])) {
                            // as jnats NatsDispatcher.subscribeImplJetStream (checkBeforeSubImpl)
                            refusedSubscribes++;
                            throw new IllegalStateException("Dispatcher is closed");
                        }
                    }
                    return subscribe(o.getConsumerConfiguration(), (MessageHandler) args[3]);
                }
                throw new UnsupportedOperationException(name);
            });
            ServerInfo info = new ServerInfo("INFO {\"server_id\":\"fake\",\"jetstream\":true}");
            connection = proxy(Connection.class, (name, args) -> switch (name) {
                case "getServerInfo" -> info;
                case "jetStreamManagement" -> jsm;
                case "jetStream" -> js;
                case "createDispatcher" -> dispatcher();
                case "addConnectionListener" -> listeners.add((ConnectionListener) args[0]);
                case "closeDispatcher" -> {
                    synchronized (this) {
                        closedDispatchers.add((Dispatcher) args[0]);
                    }
                    yield null;
                }
                case "removeConnectionListener" -> listeners.remove((ConnectionListener) args[0]);
                case "getStatus" -> Connection.Status.CONNECTED;
                default -> null;
            });
            return connection;
        }

        /**
         * A new dispatcher, as jnats creates one per createDispatcher call.
         */
        private static Dispatcher dispatcher() {
            return proxy(Dispatcher.class, (name, args) -> {
                if ("unsubscribe".equals(name) && args.length == 1 && args[0] instanceof JetStreamSubscription s) {
                    s.unsubscribe();
                }
                return null;
            });
        }

        private synchronized void requireStream() throws JetStreamApiException {
            if (!streamExists) {
                throw new JetStreamApiException(
                        new ApiResponse<Void>(
                                JsonParser.parseUnchecked(
                                        "{\"error\":{\"code\":404,\"err_code\":10059,\"description\":\"stream not found\"}}")) {
                        });
            }
        }

        private synchronized StreamInfo streamInfo() throws JetStreamApiException {
            requireStream();
            return new StreamInfo(
                    JsonParser.parseUnchecked("{\"config\":{\"name\":\"ORDERS\",\"subjects\":[\"orders\"],"
                                              + "\"retention\":\"" + retention + "\"},\"created\":\""
                                              + DateTimeUtils.toRfc3339(created) + "\",\"state\":{\"last_seq\":" + stream.size()
                                              + "}}"));
        }

        private synchronized JetStreamSubscription subscribe(ConsumerConfiguration cc, MessageHandler handler)
                throws JetStreamApiException {
            if (!streamExists) {
                throw new IllegalStateException("[SUB-90007] No matching streams for subject.");
            }
            if (retention == RetentionPolicy.WorkQueue && cc.getDeliverPolicy() != DeliverPolicy.All) {
                // the server's answer as jnats reads it (ApiResponse is abstract)
                throw new JetStreamApiException(
                        new ApiResponse<Void>(
                                JsonParser.parseUnchecked(
                                        "{\"error\":{\"code\":400,\"err_code\":10101,"
                                                          + "\"description\":\"consumer must be deliver all on workqueue stream\"}}")) {
                        });
            }
            if (cc != null && cc.getDeliverPolicy() == DeliverPolicy.ByStartSequence && cc.getStartTime() != null) {
                // as nats-server: a start time is not allowed with DeliverPolicy.ByStartSequence
                throw new JetStreamApiException(
                        Error.convert(new Status(
                                400, "consumer delivery policy is deliver by"
                                     + " start sequence, but optional start time is also set")));
            }
            configurations.add(cc);
            FakeConsumer c = new FakeConsumer();
            DeliverPolicy policy = cc != null ? cc.getDeliverPolicy() : DeliverPolicy.All;
            if (policy == DeliverPolicy.ByStartSequence) {
                c.next = cc.getStartSequence();
            } else if (policy == DeliverPolicy.New) {
                c.next = stream.size() + 1;
            } else if (policy == DeliverPolicy.Last) {
                c.next = Math.max(stream.size(), 1);
            } else {
                c.next = 1;
            }
            c.maxDeliver = cc != null ? cc.getMaxDeliver() : -1;
            consumers.add(c);
            consumersCreated++;
            JetStreamSubscription sub = proxy(JetStreamSubscription.class, (name, args) -> switch (name) {
                case "getConsumerInfo" -> consumerInfo(c);
                case "unsubscribe" -> {
                    synchronized (this) {
                        c.unsubscribed = true;
                        notifyAll();
                    }
                    yield null;
                }
                case "isActive" -> true;
                default -> null;
            });
            Thread t = new Thread(() -> deliver(c, sub, handler), "fake-push-" + consumersCreated);
            t.setDaemon(true);
            t.start();
            return sub;
        }

        private synchronized ConsumerInfo consumerInfo(FakeConsumer c) throws JetStreamApiException {
            // jnats lookupConsumerInfo maps only consumer not found to null: stream not found is thrown
            if (!streamExists) {
                // counts the checks of a consumer while the stream is gone
                streamNotFound++;
            }
            requireStream();
            if (c.deleted) {
                // jnats lookupConsumerInfo maps consumer not found (10014) to null
                return null;
            }
            return new ConsumerInfo(JsonParser.parseUnchecked("{\"stream_name\":\"ORDERS\",\"name\":\"c\",\"config\":{}}"));
        }

        private void deliver(FakeConsumer c, JetStreamSubscription sub, MessageHandler handler) {
            while (true) {
                long seq;
                int delivered;
                synchronized (this) {
                    while (!c.deleted && !c.unsubscribed
                            && (!connected || (c.redeliver.isEmpty() && c.next > stream.size()))) {
                        try {
                            wait();
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                    if (c.deleted || c.unsubscribed) {
                        return;
                    }
                    seq = c.redeliver.isEmpty() ? c.next++ : c.redeliver.remove(0);
                    if (removed.contains(seq)) {
                        continue;
                    }
                    delivered = c.deliveries.merge(seq, 1, Integer::sum);
                }
                try {
                    handler.onMessage(message(c, sub, seq, delivered));
                } catch (InterruptedException e) {
                    return;
                }
            }
        }

        private Message message(FakeConsumer c, JetStreamSubscription sub, long seq, int delivered) {
            byte[] data;
            synchronized (this) {
                data = stream.get((int) seq - 1);
            }
            // a JetStream message carries its metadata in the reply subject of the acknowledgement
            NatsJetStreamMetaData meta = new NatsJetStreamMetaData(
                    new NatsMessage("orders", "$JS.ACK.ORDERS.c." + delivered + "." + seq + "." + seq + ".0.0", data) {
                        @Override
                        public boolean isJetStream() {
                            return true;
                        }
                    });
            AckType[] lastAck = new AckType[1];
            return proxy(Message.class, (name, args) -> switch (name) {
                case "getData" -> data;
                case "getSubject" -> "orders";
                case "getSID" -> "1";
                case "getSubscription" -> sub;
                case "isJetStream" -> true;
                case "isStatusMessage" -> false;
                case "metaData" -> meta;
                case "lastAck" -> lastAck[0];
                case "ack" -> {
                    lastAck[0] = AckType.AckAck;
                    removeFromWorkQueue(c, seq);
                    yield null;
                }
                case "term" -> {
                    lastAck[0] = AckType.AckTerm;
                    removeFromWorkQueue(c, seq);
                    yield null;
                }
                case "nak", "nakWithDelay" -> {
                    lastAck[0] = AckType.AckNak;
                    synchronized (this) {
                        // the server stops delivering a message delivered maxDeliver times
                        if (!c.deleted && (c.maxDeliver <= 0 || delivered < c.maxDeliver)) {
                            c.redeliver.add(seq);
                            notifyAll();
                        }
                    }
                    yield null;
                }
                default -> null;
            });
        }

        private synchronized void removeFromWorkQueue(FakeConsumer c, long seq) {
            if (retention == RetentionPolicy.WorkQueue && !c.deleted) {
                removed.add(seq);
            }
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
