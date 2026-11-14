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
package org.apache.camel.component.nats.dst;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamManagement;
import io.nats.client.JetStreamStatusException;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.PullSubscribeOptions;
import io.nats.client.api.AckPolicy;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.ConsumerInfo;
import io.nats.client.api.DeliverPolicy;
import io.nats.client.api.ServerInfo;
import io.nats.client.api.StreamInfo;
import io.nats.client.impl.NatsJetStreamMetaData;
import io.nats.client.support.Status;
import org.apache.camel.test.dst.Dst;
import org.apache.camel.test.dst.DstLedger;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A fake NATS server with one JetStream stream and its pull consumers, and the connection camel-nats gets through its
 * {@code connection} option (dynamic proxies of the jnats interfaces; Mockito mocks of the jnats classes ServerInfo,
 * StreamInfo, ConsumerInfo and NatsJetStreamMetaData). Written from the NATS documentation, not from the Camel
 * consumer:
 * <ul>
 * <li>[C] Consumers (https://docs.nats.io/nats-concepts/jetstream/consumers): with AckExplicit a delivered message that
 * is not acknowledged within AckWait is delivered again; "MaxDeliver: the maximum number of times a specific message
 * delivery will be attempted", after which it is not delivered again; an ephemeral consumer is removed by the server
 * after its inactive threshold, for example while the client is disconnected or after a server restart; a new ephemeral
 * consumer starts with no delivery state, so a message delivered before to another consumer and not acknowledged (also
 * one nak'd there with a delay) is delivered to it at once (simplified: the fake keeps one state and one delivery count
 * per message for all consumers, see subscribe).</li>
 * <li>[A] Acknowledgements
 * (https://docs.nats.io/using-nats/developer/develop_jetstream/consumers#acknowledging-messages): Ack, Nak (redeliver
 * now) and Nak with a delay; an ack is a fire-and-forget publish, so it can be lost (assumed here: the message is then
 * delivered again after AckWait).</li>
 * <li>[P] Pull (jnats JetStreamSubscription.fetch): waits up to the timeout for up to batch messages and returns what
 * it got; after the subscription was unsubscribed it throws IllegalStateException; when the server deletes the consumer
 * while the pull waits it throws JetStreamStatusException with status 409 "Consumer Deleted", and a later pull to the
 * missing consumer gets status 503, which ends the fetch with no message; getConsumerInfo answers null for a consumer
 * the server does not have.</li>
 * <li>[F] flush (jnats Connection.flush): throws TimeoutException when the server does not answer within the
 * timeout.</li>
 * </ul>
 * Not modelled: push consumers and dispatchers, core NATS subjects, AckAll and AckNone, MaxAckPending, the stream
 * limits and retention, the reconnect of the connection itself (the connection stays CONNECTED; a server restart is
 * modelled as the deletion of the ephemeral consumer).
 * <p>
 * Timing (one-stack rule): a fetch waits first and takes its messages at the end of its wait; acks and naks take effect
 * at once; flush blocks for its round trip.
 */
final class FakeJetStream {

    static final String STREAM = "dst";
    static final String TOPIC = "dst.orders";

    enum State {
        AVAILABLE,
        PENDING,
        ACKED,
        GIVEN_UP
    }

    static final class Msg {
        final long seq;
        final String id;
        State state = State.AVAILABLE;
        int deliveries;
        long redeliverAt;
        boolean ackLost;

        Msg(long seq, String id) {
            this.seq = seq;
            this.id = id;
        }
    }

    /** A pull consumer of the stream (ephemeral unless durable), and the subscription of the client on it. */
    final class Sub implements InvocationHandler {
        final String name;
        final long startSeq;
        boolean active = true;
        boolean deleted;
        boolean durable;
        private Object proxySelf;

        Sub(String name, long startSeq) {
            this.name = name;
            this.startSeq = startSeq;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Exception {
            switch (method.getName()) {
                case "toString":
                    return "FakeSubscription[" + name + "]";
                case "hashCode":
                    return System.identityHashCode(this);
                case "equals":
                    return proxy == args[0];
                case "isActive":
                    return active;
                case "getSubject":
                    return TOPIC;
                case "getQueueName":
                    return null;
                case "getConsumerName":
                    return name;
                case "unsubscribe":
                    if (!active) {
                        throw new IllegalStateException("Subscription is inactive");
                    }
                    active = false;
                    dst.trace("fake: " + name + " unsubscribe");
                    return null;
                case "getConsumerInfo":
                    dst.block(dst.between(1, 30), name + " consumer info");
                    return deleted ? null : mock(ConsumerInfo.class);
                case "fetch":
                    return fetch((Integer) args[0],
                            args[1] instanceof Duration d ? d.toMillis() : (Long) args[1]);
                default:
                    violations.add(name + " called " + method.getName() + " (not modelled)");
                    throw new UnsupportedOperationException(method.getName());
            }
        }

        /** [P] [C] waits up to the timeout, then takes the messages due at the end of its wait. */
        private List<Message> fetch(int batch, long timeout) throws InterruptedException {
            if (!active) {
                throw new IllegalStateException("Subscription is inactive");
            }
            if (deleted) {
                // [P] a pull to a consumer the server does not have is answered 503 (no responders), which jnats takes
                // as the end of the fetch: it returns what it has, nothing
                dst.block(dst.between(1, 20), name + " fetch");
                return new ArrayList<>();
            }
            if (faults && dst.chance(fetchFailRate)) {
                dst.block(dst.between(1, 50), name + " fetch fails");
                ledger.record("fetchFault", "s", name);
                throw Dst.injected(new IllegalArgumentException("dst: fetch failed"));
            }
            dst.awaitUntil(() -> !active || deleted || available(startSeq), Math.max(1, timeout), name + " fetch");
            if (!active) {
                throw new IllegalStateException("Subscription is inactive");
            }
            dst.block(dst.between(1, 20), name + " fetch answer");
            if (deleted) {
                throw new JetStreamStatusException(new Status(409, Status.CONSUMER_DELETED));
            }
            redeliver();
            List<Message> answer = new ArrayList<>();
            for (Msg m : messages.values()) {
                if (answer.size() >= batch) {
                    break;
                }
                // an AVAILABLE message with a later redeliverAt was nak'd with a delay
                if (m.seq < startSeq || m.state != State.AVAILABLE || m.redeliverAt > dst.now()) {
                    continue;
                }
                m.state = State.PENDING;
                m.deliveries++;
                m.redeliverAt = dst.now() + ackWaitMillis;
                String delivery = m.id + "#" + m.deliveries;
                ledger.record("delivered", "s", delivery);
                onDelivery.accept(delivery);
                answer.add(message(m, delivery, (JetStreamSubscription) proxySelf));
            }
            if (!answer.isEmpty()) {
                dst.trace("fake: " + name + " fetch returns " + answer.size() + " messages");
            }
            return answer;
        }
    }

    private final Dst dst;
    private final DstLedger ledger;
    final Map<Long, Msg> messages = new LinkedHashMap<>();
    final List<Sub> subscriptions = new ArrayList<>();
    final List<String> violations = new ArrayList<>();
    long ackWaitMillis = 30_000;
    int maxDeliver = -1;
    double fetchFailRate;
    double ackLossRate;
    double flushFailRate;
    boolean faults = true;
    Consumer<String> onDelivery = d -> {
    };
    Consumer<String> onAck = d -> {
    };
    Consumer<String> onNak = d -> {
    };
    private final ZonedDateTime streamCreated;
    private long nextSeq = 1;
    private int subs;

    FakeJetStream(Dst dst, DstLedger ledger) {
        this.dst = dst;
        this.ledger = ledger;
        this.streamCreated = ZonedDateTime.ofInstant(Instant.ofEpochMilli(Dst.EPOCH_MILLIS), ZoneOffset.UTC);
    }

    void publish(String id) {
        Msg m = new Msg(nextSeq++, id);
        messages.put(m.seq, m);
        ledger.record("published", "s", id);
    }

    /** [C] a server restart: the ephemeral consumers are gone. */
    void restartServer() {
        dst.trace("fake: server restart, the ephemeral consumers are deleted");
        for (Sub s : subscriptions) {
            if (!s.durable) {
                s.deleted = true;
            }
        }
    }

    /** [C] AckWait expired: delivered again, unless MaxDeliver was reached. */
    void redeliver() {
        for (Msg m : messages.values()) {
            if (m.state == State.PENDING && dst.now() >= m.redeliverAt) {
                giveUpOrRelease(m, "ack wait expired");
            }
        }
    }

    private void giveUpOrRelease(Msg m, String why) {
        if (maxDeliver > 0 && m.deliveries >= maxDeliver) {
            m.state = State.GIVEN_UP;
            ledger.record("maxDeliver", "s", m.id);
        } else {
            m.state = State.AVAILABLE;
        }
        dst.trace("fake: " + m.id + " " + why + " -> " + m.state);
    }

    boolean available(long from) {
        redeliver();
        for (Msg m : messages.values()) {
            if (m.seq >= from && m.state == State.AVAILABLE && m.redeliverAt <= dst.now()) {
                return true;
            }
        }
        return false;
    }

    boolean settled() {
        redeliver();
        return messages.values().stream().allMatch(this::settled);
    }

    /**
     * Acknowledged, or given up after MaxDeliver; or skipped by a consumer created again after a server restart, when
     * its ack was lost: the client counts it as acknowledged and the new consumer starts after it (two faults
     * together).
     */
    private boolean settled(Msg m) {
        if (m.state == State.ACKED || m.state == State.GIVEN_UP) {
            return true;
        }
        return m.ackLost && subscriptions.stream().filter(s -> s.active && !s.deleted).allMatch(s -> s.startSeq > m.seq)
                && subscriptions.stream().anyMatch(s -> s.active && !s.deleted);
    }

    String unsettled() {
        return messages.values().stream().filter(m -> !settled(m))
                .map(m -> m.id + "=" + m.state + "/d" + m.deliveries).toList().toString();
    }

    long activeSubscriptions() {
        return subscriptions.stream().filter(s -> s.active).count();
    }

    private Message message(Msg m, String delivery, JetStreamSubscription subscription) {
        NatsJetStreamMetaData meta = mock(NatsJetStreamMetaData.class);
        when(meta.streamSequence()).thenReturn(m.seq);
        when(meta.deliveredCount()).thenReturn((long) m.deliveries);
        int number = m.deliveries;
        return (Message) Proxy.newProxyInstance(Message.class.getClassLoader(), new Class<?>[] { Message.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "toString" -> "FakeMessage[" + delivery + "]";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "getData" -> m.id.getBytes();
                    case "getSubject" -> TOPIC;
                    case "getReplyTo" -> "$JS.ACK." + STREAM + "." + delivery;
                    case "getSID" -> "1";
                    case "getSubscription" -> subscription;
                    case "getConnection" -> null;
                    case "isStatusMessage" -> false;
                    case "getStatus" -> null;
                    case "hasHeaders" -> false;
                    case "getHeaders" -> null;
                    case "isJetStream" -> true;
                    case "metaData" -> meta;
                    case "ack" -> {
                        ack(m, number, delivery);
                        yield null;
                    }
                    case "nak" -> {
                        nak(m, number, delivery, 0);
                        yield null;
                    }
                    case "nakWithDelay" -> {
                        nak(m, number, delivery, args[0] instanceof Duration d ? d.toMillis() : (Long) args[0]);
                        yield null;
                    }
                    default -> {
                        violations.add("message " + delivery + ": " + method.getName() + " (not modelled)");
                        throw new UnsupportedOperationException(method.getName());
                    }
                });
    }

    /** [A] an ack of a delivery: fire-and-forget, so it may be lost; an ack of an older delivery is accepted. */
    private void ack(Msg m, int number, String delivery) {
        onAck.accept(delivery);
        if (faults && dst.chance(ackLossRate)) {
            ledger.record("ackLost", "s", delivery);
            m.ackLost = true;
            return;
        }
        if (m.state == State.PENDING || m.state == State.AVAILABLE) {
            m.state = State.ACKED;
            ledger.record("acked", "s", delivery);
        }
    }

    private void nak(Msg m, int number, String delivery, long delay) {
        onNak.accept(delivery);
        if (m.state == State.PENDING && m.deliveries == number) {
            m.redeliverAt = dst.now() + delay;
            giveUpOrRelease(m, "nak" + (delay > 0 ? " with a delay of " + delay + " ms" : ""));
        }
    }

    /** The connection, given to the consumer with the connection option (shared: the consumer must not close it). */
    Connection connection() {
        ServerInfo serverInfo = mock(ServerInfo.class);
        when(serverInfo.isJetStreamAvailable()).thenReturn(true);
        StreamInfo streamInfo = mock(StreamInfo.class);
        when(streamInfo.getCreateTime()).thenReturn(streamCreated);
        JetStreamManagement jsm = (JetStreamManagement) Proxy.newProxyInstance(
                JetStreamManagement.class.getClassLoader(), new Class<?>[] { JetStreamManagement.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "getStreamInfo" -> {
                        dst.block(dst.between(1, 30), "stream info");
                        yield streamInfo;
                    }
                    case "toString" -> "FakeJetStreamManagement";
                    default -> {
                        violations.add("JetStreamManagement." + method.getName() + " (not modelled)");
                        throw new UnsupportedOperationException(method.getName());
                    }
                });
        JetStream js = (JetStream) Proxy.newProxyInstance(JetStream.class.getClassLoader(),
                new Class<?>[] { JetStream.class },
                (proxy, method, args) -> {
                    if ("subscribe".equals(method.getName()) && args.length == 2
                            && args[1] instanceof PullSubscribeOptions options) {
                        return subscribe((String) args[0], options);
                    }
                    if ("toString".equals(method.getName())) {
                        return "FakeJetStream";
                    }
                    violations.add("JetStream." + method.getName() + " (not modelled)");
                    throw new UnsupportedOperationException(method.getName());
                });
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] { Connection.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "toString" -> "FakeConnection";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "getServerInfo" -> serverInfo;
                    case "jetStreamManagement" -> jsm;
                    case "jetStream" -> js;
                    case "getStatus" -> Connection.Status.CONNECTED;
                    case "flush" -> {
                        flush((Duration) args[0]);
                        yield null;
                    }
                    case "close" -> {
                        violations.add("the consumer closed the shared connection");
                        yield null;
                    }
                    default -> {
                        violations.add("Connection." + method.getName() + " (not modelled)");
                        throw new UnsupportedOperationException(method.getName());
                    }
                });
    }

    /** [F] */
    private void flush(Duration timeout) throws TimeoutException {
        if (faults && dst.chance(flushFailRate)) {
            ledger.record("flushFault", "c", "");
            dst.block(timeout.toMillis(), "flush times out");
            throw Dst.injected(new TimeoutException("dst: Timeout or no response waiting for NATS server"));
        }
        dst.block(dst.between(1, 30), "flush");
    }

    private JetStreamSubscription subscribe(String topic, PullSubscribeOptions options) {
        if (!TOPIC.equals(topic)) {
            violations.add("subscribe to " + topic);
        }
        dst.block(dst.between(1, 50), "subscribe");
        ConsumerConfiguration cc = options.getConsumerConfiguration();
        long start = cc != null && cc.getDeliverPolicy() == DeliverPolicy.ByStartSequence ? cc.getStartSequence() : 1;
        Sub sub = new Sub("sub-" + subs++, start);
        JetStreamSubscription proxy = (JetStreamSubscription) Proxy.newProxyInstance(
                JetStreamSubscription.class.getClassLoader(), new Class<?>[] { JetStreamSubscription.class }, sub);
        sub.proxySelf = proxy;
        sub.durable = cc != null && cc.getDurable() != null;
        // the consumer must pass the configured ack policy, AckWait and MaxDeliver to the server
        if (cc == null || cc.getAckPolicy() != AckPolicy.Explicit || cc.getAckWait() == null
                || cc.getAckWait().toMillis() != ackWaitMillis
                || (maxDeliver > 0 ? cc.getMaxDeliver() != maxDeliver : cc.getMaxDeliver() > 0)) {
            violations.add(sub.name + " subscribed with " + (cc == null
                    ? "no consumer configuration"
                    : "ackPolicy=" + cc.getAckPolicy() + " ackWait=" + cc.getAckWait() + " maxDeliver=" + cc.getMaxDeliver()));
        }
        if (!sub.durable) {
            // [C] a new ephemeral consumer has no delivery state of its own: a message delivered to an earlier
            // consumer and not acknowledged, or nak'd there with a delay, is new to it. Simplified: the fake keeps one
            // state per message for all consumers, so an acknowledged message, or one given up after MaxDeliver, is
            // not delivered again (the real server would deliver it again), and the delivery count goes on (it names
            // the deliveries, which must stay unique)
            for (Msg m : messages.values()) {
                if (m.seq >= start && (m.state == State.PENDING || m.state == State.AVAILABLE)) {
                    m.state = State.AVAILABLE;
                    m.redeliverAt = dst.now();
                }
            }
        }
        subscriptions.add(sub);
        dst.trace("fake: " + sub.name + " subscribed from stream sequence " + start);
        return proxy;
    }
}
