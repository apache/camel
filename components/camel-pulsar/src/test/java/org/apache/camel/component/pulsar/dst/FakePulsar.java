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
package org.apache.camel.component.pulsar.dst;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.camel.test.dst.Dst;
import org.apache.camel.test.dst.DstExecutor;
import org.apache.camel.test.dst.DstLedger;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.ConsumerBuilder;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.MessageListener;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.SubscriptionInitialPosition;
import org.apache.pulsar.client.api.SubscriptionType;

/**
 * A fake Pulsar broker and client for one non-partitioned topic and one durable subscription, behind the client
 * interfaces the Camel consumer uses ({@link PulsarClient#newConsumer()}, {@link ConsumerBuilder}, {@link Consumer},
 * {@link Message}), as dynamic proxies.
 * <p>
 * Rules (Pulsar 4.x documentation, https://pulsar.apache.org/docs/next/concepts-messaging/ and the client Javadoc):
 * <ul>
 * <li>[S] "Exclusive ... only a single consumer is allowed to attach to the subscription. If multiple consumers
 * subscribe to a topic using the same subscription, an error occurs" (ConsumerBusyException). "Failover ... the broker
 * selects the master consumer ... by consumer name order" (assumed: lowest name). "Shared ... messages are delivered in
 * a round-robin distribution across consumers". Key_Shared: keys are not modelled, so it dispatches like Shared.</li>
 * <li>[Q] receiverQueueSize: "the consumer prefetches messages up to the receiver queue size"; pause(): "stop
 * requesting new messages from the broker ... messages already in the receiver queue are still delivered" (Camel docs
 * say the same).</li>
 * <li>[A] acknowledge(): the message is not delivered again; on a closed consumer it throws
 * AlreadyClosedException.</li>
 * <li>[N] negativeAcknowledge(): "the message will be redelivered after a fixed timeout"
 * (negativeAckRedeliveryDelay).</li>
 * <li>[T] ackTimeout: "messages not acknowledged within the timeout are redelivered"; 0 disables it.</li>
 * <li>[C] close(): unacknowledged messages are redelivered to the other consumers of the subscription.</li>
 * <li>[R] a broker restart / connection loss: the client reconnects by itself; the broker redelivers every
 * unacknowledged message and the client clears its receiver queue (assumed: the whole receiver queue).</li>
 * <li>[F] subscribe(): fails with a PulsarClientException when the broker cannot be reached before the operation
 * timeout (pulsar-client 4.2.4 ConsumerImpl.connectionFailed completes the subscribe future exceptionally once the
 * lookup deadline passed, or at once for a non retriable error).</li>
 * </ul>
 * Timing: subscribe blocks for its latency and takes effect at the end; acknowledgements take effect at once; delivery
 * to the application is a task of a per-consumer listener executor (one at a time, as the Pulsar listener thread). Not
 * modelled: receive() and asynchronous acknowledgements (the world uses the message listener), partitions, keys,
 * batching, chunking, dead letter topics, retention, ack grouping.
 */
final class FakePulsar {

    final Dst dst;
    private final DstLedger ledger;

    boolean faults = true;
    double subscribeFailRate;

    /** Published messages, by sequence. */
    private final List<String> log = new ArrayList<>();
    /** Messages of the subscription that may be dispatched (not acknowledged, not out with a consumer). */
    private final TreeSet<Integer> available = new TreeSet<>();
    private final TreeSet<Integer> acked = new TreeSet<>();
    private final Map<Integer, Integer> redeliveries = new LinkedHashMap<>();
    private final List<FakeConsumer> consumers = new ArrayList<>();
    private int created;
    private int rr;
    private DstExecutor timers;

    FakePulsar(Dst dst, DstLedger ledger) {
        this.dst = dst;
        this.ledger = ledger;
    }

    // ------------------------------------------------------------------------------------------------------------
    // the world side

    void publish(String id) {
        int seq = log.size();
        log.add(id);
        // the world uses subscriptionInitialPosition=Earliest: a subscription created later starts at the first message
        available.add(seq);
        ledger.record("published", "t", id);
        dispatch();
    }

    /** [R] the broker restarts: every consumer reconnects, everything not acknowledged is delivered again. */
    void brokerRestart() {
        dst.trace("fake: broker restart");
        for (FakeConsumer c : consumers) {
            if (!c.closed) {
                returnAll(c);
            }
        }
        dispatch();
    }

    boolean allAcked() {
        return acked.size() == log.size();
    }

    List<String> notAcked() {
        List<String> answer = new ArrayList<>();
        for (int i = 0; i < log.size(); i++) {
            if (!acked.contains(i)) {
                answer.add(log.get(i));
            }
        }
        return answer;
    }

    int openConsumers() {
        return (int) consumers.stream().filter(c -> !c.closed).count();
    }

    List<String> openConsumerNames() {
        return consumers.stream().filter(c -> !c.closed).map(c -> c.name).toList();
    }

    PulsarClient client() {
        return proxy(PulsarClient.class, (p, m, a) -> switch (m.getName()) {
            case "newConsumer" -> new Builder().proxy;
            case "close", "shutdown" -> null;
            case "isClosed" -> false;
            case "toString" -> "FakePulsarClient";
            case "hashCode" -> System.identityHashCode(p);
            case "equals" -> p == a[0];
            default -> throw new UnsupportedOperationException("fake: unexpected PulsarClient call " + m.getName());
        });
    }

    private DstExecutor timers() {
        if (timers == null) {
            timers = dst.executor("pulsar-client-timers", 1);
        }
        return timers;
    }

    // ------------------------------------------------------------------------------------------------------------
    // broker logic

    private void returnAll(FakeConsumer c) {
        for (Integer seq : c.prefetched) {
            backToAvailable(seq);
        }
        c.prefetched.clear();
        for (Integer seq : new ArrayList<>(c.unacked.keySet())) {
            backToAvailable(seq);
        }
        c.unacked.clear();
    }

    private void backToAvailable(int seq) {
        if (!acked.contains(seq)) {
            available.add(seq);
            redeliveries.merge(seq, 1, Integer::sum);
        }
    }

    private List<FakeConsumer> dispatchTargets() {
        List<FakeConsumer> open = consumers.stream().filter(c -> !c.closed).toList();
        if (open.isEmpty()) {
            return open;
        }
        SubscriptionType type = open.get(0).type;
        if (type == SubscriptionType.Failover) {
            // [S] the active consumer is the first by name
            FakeConsumer active = open.stream().min((x, y) -> x.name.compareTo(y.name)).get();
            return List.of(active);
        }
        return open;
    }

    void dispatch() {
        List<FakeConsumer> targets = dispatchTargets();
        if (targets.isEmpty()) {
            return;
        }
        boolean progress = true;
        while (!available.isEmpty() && progress) {
            progress = false;
            for (int i = 0; i < targets.size() && !available.isEmpty(); i++) {
                FakeConsumer c = targets.get((rr + i) % targets.size());
                if (!c.paused && c.prefetched.size() < c.queueSize) {
                    Integer seq = available.pollFirst();
                    c.prefetched.add(seq);
                    progress = true;
                }
            }
            rr++;
        }
        for (FakeConsumer c : targets) {
            c.kick();
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // client side

    private final class Builder {
        final ConsumerBuilder<byte[]> proxy;
        String consumerName = "c";
        SubscriptionType type = SubscriptionType.Exclusive;
        int queueSize = 1000;
        long ackTimeoutMillis;
        long nackDelayMillis = 60000;
        MessageListener<byte[]> listener;
        SubscriptionInitialPosition position = SubscriptionInitialPosition.Latest;

        @SuppressWarnings("unchecked")
        Builder() {
            this.proxy = proxy(ConsumerBuilder.class, (p, m, a) -> {
                switch (m.getName()) {
                    case "consumerName" -> consumerName = (String) a[0];
                    case "subscriptionType" -> type = (SubscriptionType) a[0];
                    case "receiverQueueSize" -> queueSize = (Integer) a[0];
                    case "ackTimeout" -> ackTimeoutMillis = ((TimeUnit) a[1]).toMillis((Long) a[0]);
                    case "negativeAckRedeliveryDelay" -> nackDelayMillis = ((TimeUnit) a[1]).toMillis((Long) a[0]);
                    case "messageListener" -> listener = (MessageListener<byte[]>) a[0];
                    case "subscriptionInitialPosition" -> position = (SubscriptionInitialPosition) a[0];
                    case "subscribe" -> {
                        return subscribe(this);
                    }
                    case "subscribeAsync", "clone", "loadConf" -> throw new UnsupportedOperationException(
                            "fake: unexpected ConsumerBuilder call " + m.getName());
                    default -> {
                        // topic, subscriptionName, subscriptionMode, acknowledgmentGroupTime, readCompacted, ...
                    }
                }
                return p;
            });
        }
    }

    private Consumer<byte[]> subscribe(Builder b) throws PulsarClientException {
        // [F] the lookup / subscribe round trip; it takes effect at the end
        long latency = dst.between(1, 50);
        boolean fail = faults && dst.chance(subscribeFailRate);
        if (fail) {
            latency = dst.between(1000, 30000);
        }
        dst.block(latency, "subscribe " + b.consumerName);
        if (fail) {
            ledger.record("subscribeFailed", "c", b.consumerName);
            dst.trace("fake: subscribe " + b.consumerName + " failed");
            throw Dst.injected(new PulsarClientException.TimeoutException(
                    "dst: subscribe " + b.consumerName + " timed out, the broker could not be reached"));
        }
        if (b.type == SubscriptionType.Exclusive && openConsumers() > 0) {
            throw new PulsarClientException.ConsumerBusyException("Exclusive consumer is already connected");
        }
        FakeConsumer c = new FakeConsumer(b, created++);
        consumers.add(c);
        ledger.record("subscribed", "c", c.name);
        dst.trace("fake: subscribed " + c.name + " (" + b.type + ", open " + openConsumers() + ")");
        dispatch();
        return c.proxy;
    }

    private final class FakeConsumer {
        final String name;
        final SubscriptionType type;
        final int queueSize;
        final long ackTimeoutMillis;
        final long nackDelayMillis;
        final MessageListener<byte[]> listener;
        final DstExecutor listenerExecutor;
        final Deque<Integer> prefetched = new ArrayDeque<>();
        final Map<Integer, Long> unacked = new LinkedHashMap<>();
        final Consumer<byte[]> proxy;
        boolean paused;
        boolean closed;
        boolean delivering;

        @SuppressWarnings("unchecked")
        FakeConsumer(Builder b, int n) {
            this.name = b.consumerName + "#" + n;
            this.type = b.type;
            this.queueSize = Math.max(1, b.queueSize);
            this.ackTimeoutMillis = b.ackTimeoutMillis;
            this.nackDelayMillis = b.nackDelayMillis;
            this.listener = b.listener;
            this.listenerExecutor = listener != null ? dst.executor("pulsar-listener-" + name, 1) : null;
            this.proxy = proxy(Consumer.class, (p, m, a) -> switch (m.getName()) {
                case "acknowledge" -> {
                    acknowledge(a[0]);
                    yield null;
                }
                case "negativeAcknowledge" -> {
                    negativeAcknowledge(a[0]);
                    yield null;
                }
                case "pause" -> {
                    paused = true;
                    dst.trace("fake: pause " + name);
                    yield null;
                }
                case "resume" -> {
                    paused = false;
                    dst.trace("fake: resume " + name);
                    dispatch();
                    yield null;
                }
                case "close" -> {
                    close();
                    yield null;
                }
                case "closeAsync" -> {
                    close();
                    yield CompletableFuture.completedFuture(null);
                }
                case "isConnected" -> !closed;
                case "getConsumerName" -> name;
                case "getTopic" -> "persistent://public/default/dst";
                case "getSubscription" -> "subs";
                case "toString" -> "FakeConsumer[" + name + "]";
                case "hashCode" -> System.identityHashCode(p);
                case "equals" -> p == a[0];
                default -> throw new UnsupportedOperationException("fake: unexpected Consumer call " + m.getName());
            });
        }

        /** Delivers the next prefetched message to the message listener, one at a time. */
        void kick() {
            if (listener == null || delivering || prefetched.isEmpty() || closed) {
                return;
            }
            delivering = true;
            listenerExecutor.schedule("deliver to " + name, dst.between(0, 5), () -> {
                delivering = false;
                if (closed || prefetched.isEmpty()) {
                    return;
                }
                Message<byte[]> msg = toApp(prefetched.pollFirst());
                try {
                    listener.received(proxy, msg);
                } catch (RuntimeException e) {
                    dst.trace("fake: listener of " + name + " threw " + e);
                }
                dispatch();
                kick();
            });
        }

        private Message<byte[]> toApp(int seq) {
            unacked.put(seq, dst.now());
            ledger.record("delivered", "t", log.get(seq));
            if (ackTimeoutMillis > 0) {
                long at = dst.now();
                // [T] redelivered when still not acknowledged after the ack timeout
                timers().schedule("ack timeout " + log.get(seq), ackTimeoutMillis, () -> {
                    Long since = unacked.get(seq);
                    if (!closed && since != null && since == at) {
                        unacked.remove(seq);
                        dst.trace("fake: ack timeout of " + log.get(seq) + " on " + name);
                        backToAvailable(seq);
                        dispatch();
                    }
                });
            }
            return message(seq);
        }

        private void acknowledge(Object idOrMessage) throws PulsarClientException {
            if (closed) {
                throw new PulsarClientException.AlreadyClosedException("Consumer already closed");
            }
            int seq = seqOf(idOrMessage);
            unacked.remove(seq);
            if (!acked.contains(seq)) {
                acked.add(seq);
                // [A] an acknowledged message is not delivered again
                available.remove(seq);
            }
            ledger.record("acked", "t", log.get(seq));
            dispatch();
        }

        private void negativeAcknowledge(Object idOrMessage) {
            int seq = seqOf(idOrMessage);
            if (closed) {
                return;
            }
            Long since = unacked.get(seq);
            dst.trace("fake: nack " + log.get(seq) + " on " + name);
            // [N] redelivered after the negative ack redelivery delay
            timers().schedule("nack redelivery " + log.get(seq), nackDelayMillis, () -> {
                if (!closed && since != null && Long.valueOf(since).equals(unacked.get(seq))) {
                    unacked.remove(seq);
                    backToAvailable(seq);
                    dispatch();
                }
            });
        }

        private void close() {
            if (closed) {
                return;
            }
            closed = true;
            dst.trace("fake: close " + name);
            ledger.record("closed", "c", name);
            // [C] the unacknowledged messages go to the other consumers
            returnAll(this);
            if (listenerExecutor != null) {
                listenerExecutor.shutdownNow();
            }
            dispatch();
        }
    }

    private static int seqOf(Object idOrMessage) {
        if (idOrMessage instanceof Message<?> m) {
            return ((Id) m.getMessageId()).seq;
        }
        return ((Id) idOrMessage).seq;
    }

    @SuppressWarnings("unchecked")
    private Message<byte[]> message(int seq) {
        String id = log.get(seq);
        Id messageId = new Id(seq);
        int redelivery = redeliveries.getOrDefault(seq, 0);
        return proxy(Message.class, (p, m, a) -> switch (m.getName()) {
            case "getValue", "getData" -> id.getBytes(StandardCharsets.UTF_8);
            case "getMessageId" -> messageId;
            case "getKey" -> id;
            case "getKeyBytes" -> id.getBytes(StandardCharsets.UTF_8);
            case "hasKey" -> true;
            case "getProducerName" -> "dst-producer";
            case "getTopicName" -> "persistent://public/default/dst";
            case "getSequenceId" -> (long) seq;
            case "getPublishTime", "getEventTime" -> Dst.EPOCH_MILLIS + seq;
            case "getRedeliveryCount" -> redelivery;
            case "getProperties" -> Collections.emptyMap();
            case "getProperty" -> null;
            case "hasProperty" -> false;
            case "getReaderSchema" -> Optional.empty();
            case "toString" -> "Message[" + id + "]";
            case "hashCode" -> seq;
            case "equals" -> p == a[0];
            default -> throw new UnsupportedOperationException("fake: unexpected Message call " + m.getName());
        });
    }

    static final class Id implements MessageId {
        private static final long serialVersionUID = 1L;
        final int seq;

        Id(int seq) {
            this.seq = seq;
        }

        @Override
        public byte[] toByteArray() {
            return Integer.toString(seq).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public int compareTo(MessageId o) {
            return Integer.compare(seq, ((Id) o).seq);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Id other && other.seq == seq;
        }

        @Override
        public int hashCode() {
            return seq;
        }

        @Override
        public String toString() {
            return "id-" + seq;
        }
    }

    @FunctionalInterface
    interface Handler {
        Object invoke(Object proxy, Method method, Object[] args) throws Throwable;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<?> type, Handler handler) {
        InvocationHandler h = (p, m, a) -> {
            try {
                return handler.invoke(p, m, a);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        };
        return (T) Proxy.newProxyInstance(FakePulsar.class.getClassLoader(), new Class<?>[] { type }, h);
    }
}
