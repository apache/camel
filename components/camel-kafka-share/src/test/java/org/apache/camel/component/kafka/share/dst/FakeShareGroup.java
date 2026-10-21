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
package org.apache.camel.component.kafka.share.dst;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.apache.camel.test.dst.Dst;
import org.apache.camel.test.dst.DstLedger;
import org.apache.kafka.clients.consumer.AcknowledgeType;
import org.apache.kafka.clients.consumer.AcknowledgementCommitCallback;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.ShareConsumer;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.TopicIdPartition;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.Uuid;
import org.apache.kafka.common.errors.InvalidRecordStateException;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;

/**
 * A fake Kafka share group (KIP-932) for one topic with one partition, and fake share consumers behind the
 * {@link ShareConsumer} interface (dynamic proxies).
 * <p>
 * Rules (KIP-932 and the KafkaShareConsumer Javadoc, Kafka 4.3):
 * <ul>
 * <li>[A] "records are acquired for a consumer ... with a time-limited acquisition lock"
 * (group.share.record.lock.duration.ms). "If the lock times out, the record becomes available again". Every acquisition
 * increments the delivery count; "once the delivery count reaches group.share.delivery.count.limit the record is
 * archived" instead of becoming available.</li>
 * <li>[E] explicit acknowledgement: "each record ... must be acknowledged ... using acknowledge(ConsumerRecord,
 * AcknowledgeType) before the next call to poll", otherwise poll throws IllegalStateException ("All records must be
 * acknowledged in explicit acknowledgement mode."). A record that is not waiting for an acknowledgement throws
 * IllegalStateException ("The record cannot be acknowledged.").</li>
 * <li>[C] the acknowledgements are sent with the next poll, commitSync, commitAsync or close. commitSync returns the
 * result per partition; an acknowledgement of a record whose lock expired fails (InvalidRecordStateException).</li>
 * <li>[K] the acknowledgement commit callback gets the result of the acknowledgements that were sent, with their
 * offsets, at the start of a later poll, commitSync or commitAsync, as kafka-clients 4.3.1 ShareConsumerImpl calls it
 * from handleCompletedAcknowledgements (not from within commitAsync, and not at close).</li>
 * <li>[W] wakeup: "the thread which is blocking in an operation will throw WakeupException. If no thread is blocking
 * ... the next call will raise it" (poll and commitSync).</li>
 * <li>[X] close: the pending acknowledgements are sent; acquired records that were not acknowledged are released (the
 * delivery count is kept).</li>
 * <li>ACCEPT: done. RELEASE: available again (or archived at the delivery limit). REJECT: archived.</li>
 * </ul>
 * Not modelled: several partitions, the batch_optimized acquire mode (a poll returns at most max.poll.records), lock
 * renewal (RENEW), the heartbeat and assignment of the group.
 * <p>
 * Timing (one-stack rule): poll waits first and acquires at the end of its wait; acknowledgements take effect at once;
 * commit and close do not block (the consumer calls commit while it holds its lock).
 */
final class FakeShareGroup {

    static final String TOPIC = "dst";
    static final Uuid TOPIC_ID = new Uuid(4711L, 42L);
    static final TopicIdPartition PARTITION = new TopicIdPartition(TOPIC_ID, 0, TOPIC);

    enum State {
        AVAILABLE,
        ACQUIRED,
        ACKNOWLEDGED,
        ARCHIVED
    }

    /** The result of acknowledgements that were sent, for the acknowledgement commit callback. */
    record CompletedAcks(Map<TopicIdPartition, Set<Long>> offsets, Exception error) {
    }

    static final class Rec {
        final long offset;
        final String id;
        State state = State.AVAILABLE;
        int deliveries;
        long lockUntil;
        Fake owner;

        Rec(long offset, String id) {
            this.offset = offset;
            this.id = id;
        }
    }

    static final class Delivery {
        final Rec rec;
        final int number;
        final ConsumerRecord<Object, Object> record;
        AcknowledgeType ack;

        Delivery(Rec rec, int number, ConsumerRecord<Object, Object> record) {
            this.rec = rec;
            this.number = number;
            this.record = record;
        }
    }

    final class Fake implements InvocationHandler {
        final String name;
        final List<Delivery> lastBatch = new ArrayList<>();
        final List<Delivery> pendingAcks = new ArrayList<>();
        boolean closed;
        boolean wakeup;
        AcknowledgementCommitCallback callback;
        // [K] the results of the acknowledgements sent, for the callback at the next poll or commit
        final List<CompletedAcks> completedAcks = new ArrayList<>();

        Fake(String name) {
            this.name = name;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String m = method.getName();
            switch (m) {
                case "toString":
                    return "FakeShareConsumer[" + name + "]";
                case "hashCode":
                    return System.identityHashCode(this);
                case "equals":
                    return proxy == args[0];
                case "wakeup":
                    dst.trace("fake: " + name + " wakeup");
                    wakeup = true;
                    return null;
                case "close":
                    close();
                    return null;
                default:
                    break;
            }
            checkOpen(m);
            switch (m) {
                case "subscribe":
                    @SuppressWarnings("unchecked")
                    Collection<String> topics = (Collection<String>) args[0];
                    if (!topics.contains(TOPIC)) {
                        violations.add(name + " subscribed to " + topics);
                    }
                    return null;
                case "unsubscribe":
                    return null;
                case "subscription":
                    return Set.of(TOPIC);
                case "setAcknowledgementCommitCallback":
                    callback = (AcknowledgementCommitCallback) args[0];
                    return null;
                case "poll":
                    return poll((Duration) args[0]);
                case "acknowledge":
                    if (args.length != 2) {
                        violations.add(name + " acknowledge" + args.length);
                        throw new IllegalStateException("not modelled");
                    }
                    @SuppressWarnings("unchecked")
                    ConsumerRecord<Object, Object> record = (ConsumerRecord<Object, Object>) args[0];
                    acknowledge(record, (AcknowledgeType) args[1]);
                    return null;
                case "commitSync":
                    return commitSync();
                case "commitAsync":
                    commitAsync();
                    return null;
                case "acquisitionLockTimeoutMs":
                    return Optional.of((int) lockDurationMillis);
                case "metrics":
                    return Map.of();
                default:
                    violations.add(name + " called " + m + " (not modelled)");
                    throw new UnsupportedOperationException(m);
            }
        }

        private void checkOpen(String m) {
            if (closed) {
                violations.add(name + " used after close: " + m);
                throw new IllegalStateException("This consumer has already been closed.");
            }
        }

        /** [K] the callback gets the results of the acknowledgements sent before this call. */
        private void handleCompletedAcknowledgements() {
            List<CompletedAcks> completed = new ArrayList<>(completedAcks);
            completedAcks.clear();
            if (callback != null) {
                for (CompletedAcks c : completed) {
                    callback.onComplete(c.offsets(), c.error());
                }
            }
        }

        private ConsumerRecords<Object, Object> poll(Duration timeout) {
            handleCompletedAcknowledgements();
            if (wakeup) {
                wakeup = false;
                throw new WakeupException();
            }
            // [E]
            for (Delivery d : lastBatch) {
                if (d.ack == null) {
                    violations.add(name + " polled before acknowledging " + d.rec.id + "#" + d.number);
                    dst.block(10, "poll refused");
                    throw new IllegalStateException("All records must be acknowledged in explicit acknowledgement mode.");
                }
            }
            lastBatch.clear();
            // [C] the acknowledgements go with the poll
            send(false);
            if (faults && dst.chance(pollFailRate)) {
                dst.block(dst.between(1, 50), "poll fails");
                ledger.record("pollFault", "c", name);
                throw Dst.injected(new KafkaException("dst: poll failed"));
            }
            long waitMillis = Math.max(1, timeout.toMillis());
            boolean ready = dst.awaitUntil(() -> wakeup || closed || hasAvailable(), waitMillis, name + " poll");
            if (ready && !wakeup) {
                // reads take effect at the end of their latency
                dst.block(dst.between(1, 20), name + " fetch");
            }
            if (closed) {
                violations.add(name + " closed while polling");
                throw new IllegalStateException("This consumer has already been closed.");
            }
            if (wakeup) {
                wakeup = false;
                throw new WakeupException();
            }
            expire();
            Map<TopicPartition, List<ConsumerRecord<Object, Object>>> map = new LinkedHashMap<>();
            List<ConsumerRecord<Object, Object>> list = new ArrayList<>();
            for (Rec r : log) {
                if (list.size() >= maxPollRecords) {
                    break;
                }
                if (r.state == State.AVAILABLE) {
                    r.state = State.ACQUIRED;
                    r.deliveries++;
                    r.lockUntil = dst.now() + lockDurationMillis;
                    r.owner = this;
                    ConsumerRecord<Object, Object> cr = new ConsumerRecord<>(
                            TOPIC, 0, r.offset, Dst.EPOCH_MILLIS + dst.now(), TimestampType.CREATE_TIME, -1, -1,
                            null, r.id, new RecordHeaders(), Optional.empty(), Optional.of((short) r.deliveries));
                    lastBatch.add(new Delivery(r, r.deliveries, cr));
                    list.add(cr);
                    ledger.record("delivered", "t", r.id);
                }
            }
            if (!list.isEmpty()) {
                map.put(new TopicPartition(TOPIC, 0), list);
                dst.trace("fake: " + name + " poll returns " + list.size() + " records");
            }
            return new ConsumerRecords<>(map, Map.of());
        }

        private void acknowledge(ConsumerRecord<Object, Object> record, AcknowledgeType type) {
            for (Delivery d : lastBatch) {
                if (d.record == record || d.rec.offset == record.offset() && d.ack == null) {
                    if (d.ack != null) {
                        violations.add(name + " acknowledged " + d.rec.id + "#" + d.number + " twice");
                        throw new IllegalStateException("The record cannot be acknowledged.");
                    }
                    d.ack = type;
                    pendingAcks.add(d);
                    return;
                }
            }
            violations.add(name + " acknowledged a record not in its last poll: offset " + record.offset());
            throw new IllegalStateException("The record cannot be acknowledged.");
        }

        private Map<TopicIdPartition, Optional<KafkaException>> commitSync() {
            handleCompletedAcknowledgements();
            if (wakeup) {
                wakeup = false;
                throw new WakeupException();
            }
            KafkaException error = send(true);
            Map<TopicIdPartition, Optional<KafkaException>> result = new LinkedHashMap<>();
            result.put(PARTITION, Optional.ofNullable(error));
            return result;
        }

        private void commitAsync() {
            handleCompletedAcknowledgements();
            // the result reaches the callback at a later poll or commit
            send(true);
        }

        /** Sends the pending acknowledgements. */
        private KafkaException send(boolean mayFail) {
            if (pendingAcks.isEmpty()) {
                return null;
            }
            List<Delivery> acks = new ArrayList<>(pendingAcks);
            pendingAcks.clear();
            Set<Long> offsets = new TreeSet<>();
            for (Delivery d : acks) {
                offsets.add(d.rec.offset);
            }
            if (mayFail && faults && dst.chance(commitFailRate)) {
                for (Delivery d : acks) {
                    ledger.record("ackLost", "t", d.rec.id);
                }
                dst.trace("fake: " + name + " commit fails, " + acks.size() + " acknowledgements lost");
                KafkaException failure = Dst.injected(new KafkaException("dst: commit failed"));
                completedAcks.add(new CompletedAcks(Map.of(PARTITION, offsets), failure));
                return failure;
            }
            expire();
            KafkaException error = null;
            for (Delivery d : acks) {
                Rec r = d.rec;
                if (r.state != State.ACQUIRED || r.deliveries != d.number || r.owner != this) {
                    ledger.record("ackStale", "t", r.id);
                    error = new InvalidRecordStateException("dst: the lock of " + r.id + "#" + d.number + " expired");
                    continue;
                }
                apply(r, d.number, d.ack);
            }
            completedAcks.add(new CompletedAcks(Map.of(PARTITION, offsets), error));
            return error;
        }

        private void close() {
            if (closed) {
                return;
            }
            dst.trace("fake: " + name + " close");
            send(false);
            expire();
            // [X]
            for (Delivery d : lastBatch) {
                Rec r = d.rec;
                if (d.ack == null && r.state == State.ACQUIRED && r.deliveries == d.number && r.owner == this) {
                    release(r, "released on close");
                }
            }
            lastBatch.clear();
            closed = true;
            open.remove(this);
        }
    }

    private final Dst dst;
    private final DstLedger ledger;
    final List<Rec> log = new ArrayList<>();
    final List<String> violations = new ArrayList<>();
    final Set<Fake> open = new HashSet<>();
    final Set<String> completedDeliveries = new HashSet<>();
    long lockDurationMillis = 30000;
    int deliveryLimit = 5;
    int maxPollRecords = 500;
    double createFailRate;
    double pollFailRate;
    double commitFailRate;
    boolean faults = true;
    private int created;

    FakeShareGroup(Dst dst, DstLedger ledger) {
        this.dst = dst;
        this.ledger = ledger;
    }

    /** KafkaShareClientFactory.getShareConsumer. */
    @SuppressWarnings("unchecked")
    ShareConsumer<Object, Object> newConsumer() {
        if (faults && dst.chance(createFailRate)) {
            ledger.record("createFault", "c", "c" + created);
            throw Dst.injected(new KafkaException("dst: Failed to construct kafka share consumer"));
        }
        Fake fake = new Fake("c" + created++);
        open.add(fake);
        dst.trace("fake: created " + fake.name);
        return (ShareConsumer<Object, Object>) Proxy.newProxyInstance(
                ShareConsumer.class.getClassLoader(), new Class<?>[] { ShareConsumer.class }, fake);
    }

    void publish(String id) {
        log.add(new Rec(log.size(), id));
        ledger.record("published", "t", id);
    }

    boolean hasAvailable() {
        expire();
        for (Rec r : log) {
            if (r.state == State.AVAILABLE) {
                return true;
            }
        }
        return false;
    }

    /** [A] locks that timed out. */
    void expire() {
        long now = dst.now();
        for (Rec r : log) {
            if (r.state == State.ACQUIRED && now >= r.lockUntil) {
                ledger.record("lockExpired", "t", r.id);
                release(r, "lock expired");
            }
        }
    }

    private void apply(Rec r, int number, AcknowledgeType type) {
        if (type == AcknowledgeType.ACCEPT) {
            if (!completedDeliveries.contains(r.id + "#" + number)) {
                violations.add("accepted " + r.id + "#" + number + " but the route did not complete that delivery");
            }
            r.state = State.ACKNOWLEDGED;
            r.owner = null;
            ledger.record("accepted", "t", r.id);
        } else if (type == AcknowledgeType.REJECT) {
            r.state = State.ARCHIVED;
            r.owner = null;
            ledger.record("rejected", "t", r.id);
        } else if (type == AcknowledgeType.RELEASE) {
            release(r, "released");
        } else {
            violations.add("acknowledgement type " + type + " not modelled");
        }
    }

    private void release(Rec r, String why) {
        r.owner = null;
        if (r.deliveries >= deliveryLimit) {
            r.state = State.ARCHIVED;
            ledger.record("archived", "t", r.id);
        } else {
            r.state = State.AVAILABLE;
        }
        dst.trace("fake: " + r.id + " " + why + " -> " + r.state);
    }

    boolean settled() {
        expire();
        for (Rec r : log) {
            if (r.state != State.ACKNOWLEDGED && r.state != State.ARCHIVED) {
                return false;
            }
        }
        return true;
    }

    String unsettled() {
        StringBuilder sb = new StringBuilder();
        for (Rec r : log) {
            if (r.state != State.ACKNOWLEDGED && r.state != State.ARCHIVED) {
                sb.append(r.id).append('=').append(r.state).append("/d").append(r.deliveries)
                        .append(r.owner != null ? "@" + r.owner.name : "").append(' ');
            }
        }
        return sb.toString().trim();
    }

    String openNames() {
        List<String> names = new ArrayList<>();
        for (Fake f : open) {
            names.add(f.name);
        }
        names.sort(null);
        return names.toString();
    }
}
