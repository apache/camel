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
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.JetStream;
import io.nats.client.JetStreamManagement;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.PushSubscribeOptions;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.ConsumerInfo;
import io.nats.client.api.DeliverPolicy;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.api.StreamInfo;
import io.nats.client.impl.AckType;
import io.nats.client.impl.NatsJetStreamMetaData;
import io.nats.client.impl.NatsMessage;
import io.nats.client.support.DateTimeUtils;
import io.nats.client.support.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where the push consumer keeper resumes after the server deleted the ephemeral consumer, when messages of an earlier
 * subscription are still dispatched (jnats keeps the queue of the dispatcher across a reconnect) or were not delivered
 * again yet by the new consumer.
 * <p>
 * The keeper runs against fakes of the jnats interfaces it calls, which answer as jnats 2.26.4 does: the dispatcher
 * passes a queued message to the handler only while its subscription is active ({@code NatsDispatcher.run}, a
 * subscription is no longer active once {@code dispatcher.unsubscribe} invalidated it) and does not clear its queue on
 * a reconnect ({@code NatsConnection.reconnectImpl}); {@code subscribe} makes the subscription before it creates the
 * consumer ({@code NatsJetStream.createSubscription}), so the new consumer can deliver before the call returns;
 * {@code getConsumerInfo} answers null for a deleted consumer (api error 10014); {@code lastAck} is set after the ack
 * is published and a terminal ack is never replaced ({@code NatsJetStreamMessage.ackReply}); the metadata come from the
 * reply subject. The test thread plays the dispatcher thread (it calls {@code onMessage} as jnats does), the route
 * (ack, nak) and the server; {@code checkConsumer} is what the keeper runs after RESUBSCRIBED.
 */
class NatsJetStreamPushConsumerKeeperResumeTest {

    private final FakeJetStream server = new FakeJetStream();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void anOldStreamMessageDispatchedWhileSubscribingAgainDoesNotMoveTheNextResume() throws Exception {
        NatsJetStreamPushConsumerKeeper keeper = server.keeper(executor);
        JetStreamSubscription sub1 = server.subscription();
        server.lastSeq = 5;
        keeper.start(sub1, server.created, 5);
        for (long seq = 1; seq <= 4; seq++) {
            server.dispatch(keeper, server.message(sub1, seq)).ack();
        }
        // message 5 of the old stream waits in the queue of the dispatcher

        // a server restart: the memory stream is created again (its sequences start at 1 again), the consumer is gone
        server.restartWithNewStream(2);
        // jnats dispatches the old message 5 while the keeper waits for the new consumer
        server.duringSubscribe = sub -> server.dispatch(keeper, server.message(sub1, 5)).ack();
        keeper.checkConsumer();
        assertEquals(0, server.startOf(1), "the consumer configuration (deliver policy All) on the new stream");
        JetStreamSubscription sub2 = server.lastSubscription();
        server.dispatch(keeper, server.message(sub2, 1)).ack();
        server.dispatch(keeper, server.message(sub2, 2)).ack();

        // the connection drops again for longer than the inactive threshold, while 3 more messages are published
        server.lastSeq = 5;
        server.deleteConsumers();
        keeper.checkConsumer();
        assertEquals(3, server.startOf(2), "resumes after the messages 1 and 2 of the new stream");
    }

    @Test
    void anOldStreamMessageDispatchedWhileTheResumeIsWorkedOutIsNotTracked() throws Exception {
        NatsJetStreamPushConsumerKeeper keeper = server.keeper(executor);
        JetStreamSubscription sub1 = server.subscription();
        server.lastSeq = 5;
        keeper.start(sub1, server.created, 5);
        for (long seq = 1; seq <= 4; seq++) {
            server.dispatch(keeper, server.message(sub1, seq)).ack();
        }

        server.restartWithNewStream(3);
        // jnats dispatches the old message 5 on its own thread after the keeper found the stream created again and
        // before it works out where to start
        server.duringStreamConfiguration = () -> server.dispatchOnOtherThread(keeper, server.message(sub1, 5));
        keeper.checkConsumer();
        assertEquals(0, server.startOf(1), "the consumer configuration (deliver policy All) on the new stream");
    }

    @Test
    void anOldStreamMessageTakenBeforeAnEarlierResubscribeIsNotTracked() throws Exception {
        NatsJetStreamPushConsumerKeeper keeper = server.keeper(executor);
        JetStreamSubscription sub1 = server.subscription();
        server.lastSeq = 3;
        keeper.start(sub1, server.created, 3);
        server.dispatch(keeper, server.message(sub1, 1)).ack();
        server.dispatch(keeper, server.message(sub1, 2)).ack();
        // the dispatcher thread took message 3 from its queue and has not handed it over yet
        Message taken = server.message(sub1, 3);

        server.deleteConsumers();
        keeper.checkConsumer();
        assertEquals(3, server.startOf(1));
        // the connection drops again and the server restarts: the stream is created again
        server.restartWithNewStream(1);
        // the dispatcher thread hands over the message it took (subscription 1 is unsubscribed meanwhile)
        server.duringStreamConfiguration = () -> server.handOverOnOtherThread(keeper, taken);
        keeper.checkConsumer();
        assertEquals(0, server.startOf(2), "the consumer configuration (deliver policy All) on the new stream");
    }

    @Test
    void aMessageTheNewConsumerDeliversWhileSubscribingIsTracked() throws Exception {
        NatsJetStreamPushConsumerKeeper keeper = server.keeper(executor);
        JetStreamSubscription sub1 = server.subscription();
        server.lastSeq = 2;
        keeper.start(sub1, server.created, 2);
        server.dispatch(keeper, server.message(sub1, 1)).ack();
        server.dispatch(keeper, server.message(sub1, 2)).ack();

        server.restartWithNewStream(2);
        Message[] first = new Message[1];
        // the new consumer delivers message 1 of the new stream before subscribe returns; its processing fails later
        server.duringSubscribe = sub -> first[0] = server.dispatch(keeper, server.message(sub, 1));
        keeper.checkConsumer();
        JetStreamSubscription sub2 = server.lastSubscription();
        server.dispatch(keeper, server.message(sub2, 2)).ack();

        server.deleteConsumers();
        // the nak goes to the deleted consumer
        first[0].nak();
        keeper.checkConsumer();
        assertEquals(1, server.startOf(2), "resumes at the message of the new stream that was not acknowledged");
    }

    @Test
    void anUnsettledMessageStaysTrackedUntilTheNewConsumerDeliversItAgain() throws Exception {
        NatsJetStreamPushConsumerKeeper keeper = server.keeper(executor);
        JetStreamSubscription sub1 = server.subscription();
        server.lastSeq = 2;
        keeper.start(sub1, server.created, 2);
        server.dispatch(keeper, server.message(sub1, 1)).ack();
        // the route is still processing message 2 (a slow downstream call)
        Message m2 = server.dispatch(keeper, server.message(sub1, 2));

        // disconnected for longer than the inactive threshold: the consumer is gone
        server.deleteConsumers();
        keeper.checkConsumer();
        assertEquals(2, server.startOf(1), "message 2 is not acknowledged");
        JetStreamSubscription sub2 = server.lastSubscription();

        // the new consumer pushed message 2 again, but its copy still waits in the queue of the dispatcher when the
        // connection drops again for longer than the inactive threshold
        server.message(sub2, 2);
        server.deleteConsumers();
        // the processing of message 2 fails: its nak goes to the first consumer, which is gone
        m2.nak();
        keeper.checkConsumer();
        assertTrue(server.unsubscribed.contains(sub2), "the queued copy of message 2 is dropped with subscription 2");
        assertEquals(2, server.startOf(2), "message 2 was never processed");
    }

    @Test
    void anUnsettledMessageNoLongerInTheStreamDoesNotHoldTheResumeBack() throws Exception {
        NatsJetStreamPushConsumerKeeper keeper = server.keeper(executor);
        JetStreamSubscription sub1 = server.subscription();
        server.lastSeq = 3;
        keeper.start(sub1, server.created, 3);
        server.dispatch(keeper, server.message(sub1, 1)).ack();
        server.dispatch(keeper, server.message(sub1, 2)).nak();

        server.deleteConsumers();
        keeper.checkConsumer();
        assertEquals(2, server.startOf(1));
        // message 2 is gone from the stream meanwhile (a limit of the stream): the new consumer delivers 3
        JetStreamSubscription sub2 = server.lastSubscription();
        server.dispatch(keeper, server.message(sub2, 3)).ack();

        server.deleteConsumers();
        keeper.checkConsumer();
        assertEquals(4, server.startOf(2), "the new consumer went past message 2");
    }

    /**
     * The server and the jnats client, faked at the level of the jnats interfaces the keeper calls.
     */
    static final class FakeJetStream {
        volatile ZonedDateTime created = DateTimeUtils.gmtNow().minusHours(1);
        volatile long lastSeq;
        final Set<JetStreamSubscription> deleted = ConcurrentHashMap.newKeySet();
        final Set<JetStreamSubscription> unsubscribed = ConcurrentHashMap.newKeySet();
        final List<JetStreamSubscription> subscriptions = new CopyOnWriteArrayList<>();
        final List<ConsumerConfiguration> consumers = new CopyOnWriteArrayList<>();
        // runs on the thread that subscribes, after the subscription is made and before the call returns
        volatile SubscriptionHook duringSubscribe;
        // runs when the keeper reads the configuration of the stream it looked up
        volatile Runnable duringStreamConfiguration;

        @FunctionalInterface
        interface SubscriptionHook {
            void run(JetStreamSubscription subscription) throws Exception;
        }

        NatsJetStreamPushConsumerKeeper keeper(ExecutorService executor) {
            Dispatcher dispatcher = proxy(Dispatcher.class, (name, args) -> {
                if ("unsubscribe".equals(name) && args.length == 1 && args[0] instanceof JetStreamSubscription s) {
                    unsubscribed.add(s);
                }
                return null;
            });
            return new NatsJetStreamPushConsumerKeeper(
                    null, connection(), "ORDERS", "orders", null, dispatcher, msg -> {
                    }, ConsumerConfiguration.builder().build(), executor, () -> true, sub -> {
                    });
        }

        void restartWithNewStream(long messages) {
            deleteConsumers();
            created = created.plusMinutes(10);
            lastSeq = messages;
        }

        void deleteConsumers() {
            deleted.addAll(subscriptions);
        }

        long startOf(int consumer) {
            ConsumerConfiguration cc = consumers.get(consumer - 1);
            return cc.getDeliverPolicy() == DeliverPolicy.ByStartSequence ? cc.getStartSequence() : 0;
        }

        JetStreamSubscription lastSubscription() {
            return subscriptions.get(subscriptions.size() - 1);
        }

        /**
         * The dispatcher thread passes the message on, as jnats does, only while its subscription is active.
         */
        Message dispatch(NatsJetStreamPushConsumerKeeper keeper, Message msg) throws InterruptedException {
            if (!unsubscribed.contains((JetStreamSubscription) msg.getSubscription())) {
                keeper.onMessage(msg);
            }
            return msg;
        }

        void dispatchOnOtherThread(NatsJetStreamPushConsumerKeeper keeper, Message msg) {
            runOnOtherThread(() -> dispatch(keeper, msg));
        }

        /**
         * The dispatcher thread hands over a message it took from its queue while the subscription was active.
         */
        void handOverOnOtherThread(NatsJetStreamPushConsumerKeeper keeper, Message msg) {
            runOnOtherThread(() -> keeper.onMessage(msg));
        }

        @FunctionalInterface
        interface Dispatch {
            void run() throws InterruptedException;
        }

        private static void runOnOtherThread(Dispatch dispatch) {
            Thread t = new Thread(() -> {
                try {
                    dispatch.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "fake-dispatcher");
            t.start();
            try {
                t.join(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        Connection connection() {
            JetStreamManagement jsm = proxy(JetStreamManagement.class,
                    (name, args) -> "getStreamInfo".equals(name) ? streamInfo() : null);
            JetStream js = proxy(JetStream.class, (name, args) -> {
                if ("subscribe".equals(name) && args.length == 6 && args[5] instanceof PushSubscribeOptions o) {
                    consumers.add(o.getConsumerConfiguration());
                    JetStreamSubscription sub = subscription();
                    SubscriptionHook hook = duringSubscribe;
                    if (hook != null) {
                        duringSubscribe = null;
                        hook.run(sub);
                    }
                    return sub;
                }
                throw new UnsupportedOperationException(name);
            });
            return proxy(Connection.class, (name, args) -> switch (name) {
                case "jetStreamManagement" -> jsm;
                case "jetStream" -> js;
                default -> null;
            });
        }

        JetStreamSubscription subscription() {
            JetStreamSubscription[] self = new JetStreamSubscription[1];
            self[0] = proxy(JetStreamSubscription.class, (name, args) -> switch (name) {
                // jnats maps consumer not found (10014) to null
                case "getConsumerInfo" -> deleted.contains(self[0])
                        ? null
                        : new ConsumerInfo(
                                JsonParser.parseUnchecked("{\"stream_name\":\"ORDERS\",\"name\":\"c\",\"config\":{}}"));
                case "isActive" -> !unsubscribed.contains(self[0]);
                default -> null;
            });
            subscriptions.add(self[0]);
            return self[0];
        }

        StreamInfo streamInfo() {
            String json = "{\"config\":{\"name\":\"ORDERS\",\"subjects\":[\"orders\"],\"retention\":\"limits\"},"
                          + "\"created\":\"" + DateTimeUtils.toRfc3339(created) + "\",\"state\":{\"last_seq\":" + lastSeq
                          + "}}";
            return new StreamInfo(JsonParser.parseUnchecked(json)) {
                @Override
                public StreamConfiguration getConfiguration() {
                    Runnable hook = duringStreamConfiguration;
                    if (hook != null) {
                        duringStreamConfiguration = null;
                        hook.run();
                    }
                    return super.getConfiguration();
                }
            };
        }

        /**
         * A JetStream message as a push subscription receives it.
         */
        Message message(JetStreamSubscription sub, long seq) {
            NatsJetStreamMetaData meta = new NatsJetStreamMetaData(
                    new NatsMessage("orders", "$JS.ACK.ORDERS.c.1." + seq + "." + seq + ".0.0", new byte[0]) {
                        @Override
                        public boolean isJetStream() {
                            return true;
                        }
                    });
            AckType[] lastAck = new AckType[1];
            return proxy(Message.class, (name, args) -> switch (name) {
                case "isJetStream" -> true;
                case "metaData" -> meta;
                case "getSubscription" -> sub;
                case "lastAck" -> lastAck[0];
                case "ack", "nak" -> {
                    // as NatsJetStreamMessage.ackReply: set after the publish, and a terminal ack is never replaced
                    if (lastAck[0] == null || !lastAck[0].terminal) {
                        lastAck[0] = "ack".equals(name) ? AckType.AckAck : AckType.AckNak;
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
            return (T) Proxy.newProxyInstance(FakeJetStream.class.getClassLoader(), new Class<?>[] { type },
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
