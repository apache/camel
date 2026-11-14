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
package org.apache.camel.component.kafka.dst;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.camel.test.dst.Dst;
import org.apache.camel.test.dst.DstLedger;
import org.apache.kafka.clients.consumer.CommitFailedException;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.OffsetCommitCallback;
import org.apache.kafka.clients.consumer.RetriableCommitFailedException;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.InterruptException;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;

/**
 * A fake Kafka cluster for one topic with one or two partitions and one consumer group, and fake consumers behind the
 * {@link Consumer} interface (dynamic proxies), as camel-kafka gets them from its {@code KafkaClientFactory}.
 * <p>
 * Written from the Javadoc of {@code org.apache.kafka.clients.consumer.KafkaConsumer} and {@code Consumer}
 * (kafka-clients 4.3) for the classic group protocol with an eager assignor (the default {@code RangeAssignor}).
 * kafka-clients' {@code MockConsumer} is not used: its poll returns at once, its commits never fail or take time, and
 * it has no group (no rebalance of its own), so it could not drive the timing this world is about. When kafka-clients
 * is upgraded, check the rules below against the new Javadoc.
 * <p>
 * Rules (quotes from the KafkaConsumer Javadoc):
 * <ul>
 * <li>[P] poll: "fetch data for the topics or partitions specified using one of the subscribe/assign APIs"; it returns
 * when records are available or the timeout expired, at most max.poll.records records ("fewer records might be
 * returned"), from the position of each assigned partition that is not paused; the position then moves past them. "It
 * is an error to not have subscribed to any topics or partitions before polling for data" (IllegalStateException).</li>
 * <li>[G] group management: "the consumer will ... rebalance partitions among the members of the group" when a member
 * joins or leaves; the rebalance happens inside poll, where the listener is called: "onPartitionsRevoked ... will be
 * called ... during a rebalance operation when the consumer has to give up some partitions" (eager: every partition is
 * revoked, then the new assignment is given to onPartitionsAssigned). "Rebalance will not preserve the pause/resume
 * state" (pause). After an assignment the position of a partition is its committed offset, else auto.offset.reset
 * (earliest in this world).</li>
 * <li>[A] enable.auto.commit: "the committed offset will be the position", committed "with a frequency controlled by
 * the config auto.commit.interval.ms" while the consumer polls, before a rebalance revokes the partitions, and when it
 * closes. ("you must consume all data returned from each call to poll(Duration) before any subsequent calls, or before
 * closing the consumer. If you fail to do either of these, it is possible for the committed offset to get ahead of the
 * consumed position, which results in missing records.")</li>
 * <li>[S] commitSync: "This is a synchronous commit and will block until either the commit succeeds, an unrecoverable
 * error is encountered ... or the timeout ... expires". TimeoutException "if the timeout specified by
 * default.api.timeout.ms expires before successful completion of the offset commit": whether the broker applied it is
 * not known, so the seed chooses (assumed: the Javadoc does not say). A commit that takes longer than its timeout ends
 * the same way. CommitFailedException "when you are trying to commit to partitions that are no longer assigned to this
 * consumer". Until the member rejoins the group in its next poll, the group is preparing the rebalance and takes the
 * commits of the partitions the member still owns (assumed from the classic protocol: the coordinator accepts commits
 * of the current generation then). "This is a synchronous commit and will also invoke any pending offset commit
 * callbacks of async commits".</li>
 * <li>[C] commitAsync: "Offsets committed through multiple calls to this API are guaranteed to be sent in the same
 * order as the invocations. Corresponding commit callbacks are also invoked in the same order"; the callback is invoked
 * from a later poll, commitSync, commitAsync or close of the same consumer; a failed commit is not applied
 * (RetriableCommitFailedException or CommitFailedException in the callback).</li>
 * <li>[R] committed: "This call will block to do a remote call to get the latest committed offsets from the server";
 * null for a partition without a committed offset.</li>
 * <li>[W] wakeup: "The thread which is blocking in an operation will throw WakeupException. If no thread is blocking in
 * a method which can throw WakeupException, the next call to such method will raise it instead." Here: poll, commitSync
 * and committed.</li>
 * <li>[X] seek, pause and resume of a partition that is not assigned: IllegalStateException ("No current assignment for
 * partition"). unsubscribe leaves the group (revocation callback first); close commits with auto-commit, completes the
 * pending async commits, and leaves the group.</li>
 * <li>[L] an exception of the rebalance listener: the client gives up the partitions (or takes the new ones) first,
 * then poll, unsubscribe or close throws it, wrapped in a KafkaException ("User rebalance callback throws an error"); a
 * WakeupException or InterruptException is thrown at once, and the member keeps its partitions (assumed from the
 * classic client; the Javadoc says only that the exception is propagated). close turns wakeups off first, as the
 * classic client does.</li>
 * </ul>
 * A phantom member can join the group (and leave again): it takes partitions in the rebalance, but does not consume or
 * commit, so its partitions wait for the Camel consumer to get them back.
 * <p>
 * Timing (one-stack rule): poll waits first and fetches at the end of its wait; the rebalance in poll blocks for the
 * join before the listener is called; commits take effect at the start of their latency; committed blocks, then reads.
 * The other calls do not block.
 * <p>
 * Not modelled: several topics, topic patterns and assign(), max.poll.interval.ms and session timeouts (a member is
 * never removed for being slow), the cooperative rebalance protocol and group.protocol=consumer, transactions and
 * isolation levels, leader epochs, log retention and offsets out of range.
 * <p>
 * The fake checks the at-least-once contract of the consumer when the group takes a commit: a committed offset must not
 * pass a record that the route has not finished with ({@link #done(TopicPartition, long)}).
 */
final class FakeKafka {

    static final String TOPIC = "dst";
    static final String GROUP = "g";

    /** A record of the topic, and what the route did with it. */
    static final class Rec {
        final int partition;
        final long offset;
        final String id;
        int routed;
        int completed;
        int failed;

        Rec(int partition, long offset, String id) {
            this.partition = partition;
            this.offset = offset;
            this.id = id;
        }
    }

    /** An async commit waiting for its callback. */
    private record PendingCallback(Map<TopicPartition, OffsetAndMetadata> offsets, Exception error,
            OffsetCommitCallback callback) {
    }

    final class Member implements InvocationHandler {
        final String name;
        final Set<TopicPartition> owned = new TreeSet<>(FakeKafka::compare);
        final Set<TopicPartition> paused = new TreeSet<>(FakeKafka::compare);
        final Map<TopicPartition, Long> position = new TreeMap<>(FakeKafka::compare);
        final List<PendingCallback> callbacks = new ArrayList<>();
        ConsumerRebalanceListener listener;
        boolean subscribed;
        boolean closed;
        boolean wakeup;
        long generation = -1;
        long lastAutoCommit;
        // for the check of a suspended route: when the last poll started, and whether it returned normally
        long lastPollStart = -1;
        boolean lastPollReturned;

        Member(String name) {
            this.name = name;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String m = method.getName();
            switch (m) {
                case "toString":
                    return "FakeKafkaConsumer[" + name + "]";
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
                case "metrics":
                    return Map.of();
                default:
                    break;
            }
            checkOpen(m);
            switch (m) {
                case "subscribe":
                    subscribe(args);
                    return null;
                case "unsubscribe":
                    unsubscribe();
                    return null;
                case "subscription":
                    return subscribed ? Set.of(TOPIC) : Set.of();
                case "assignment":
                    return copyOf(owned);
                case "paused":
                    return copyOf(paused);
                case "poll":
                    return poll((Duration) args[0]);
                case "commitSync":
                    commitSync(args);
                    return null;
                case "commitAsync":
                    commitAsync(args);
                    return null;
                case "committed":
                    return committed(args);
                case "seek":
                    seek((TopicPartition) args[0],
                            args[1] instanceof OffsetAndMetadata o ? o.offset() : (Long) args[1]);
                    return null;
                case "pause":
                    pause(args, true);
                    return null;
                case "resume":
                    pause(args, false);
                    return null;
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

        private void checkWakeup() {
            if (wakeup) {
                wakeup = false;
                dst.trace("fake: " + name + " throws WakeupException");
                throw new WakeupException();
            }
        }

        private void subscribe(Object[] args) {
            if (!(args[0] instanceof Collection<?> topics)) {
                violations.add(name + " subscribed with " + args[0].getClass().getSimpleName() + " (not modelled)");
                throw new UnsupportedOperationException("subscribe(" + args[0] + ")");
            }
            if (!topics.equals(List.of(TOPIC)) && !topics.equals(Set.of(TOPIC))) {
                violations.add(name + " subscribed to " + topics);
            }
            listener = args.length > 1 ? (ConsumerRebalanceListener) args[1] : null;
            if (!subscribed) {
                subscribed = true;
                members.add(this);
                groupChanged(name + " joins");
            }
        }

        private void unsubscribe() {
            if (!subscribed) {
                return;
            }
            dst.trace("fake: " + name + " unsubscribe");
            leave();
        }

        /** [X] leaves the group: the revocation callback first, then the partitions are given up; [L]. */
        private void leave() {
            RuntimeException failure = null;
            if (!owned.isEmpty() && listener != null) {
                failure = revoke();
                if (interruption(failure)) {
                    // [L] thrown at once: the member keeps its partitions and stays in the group
                    throw failure;
                }
            }
            owned.clear();
            paused.clear();
            position.clear();
            subscribed = false;
            members.remove(this);
            groupChanged(name + " leaves");
            if (failure != null) {
                throw listenerFailure(failure);
            }
        }

        /** The revocation callback; its exception is returned, to be thrown once the partitions are given up [L]. */
        private RuntimeException revoke() {
            try {
                listener.onPartitionsRevoked(copyOf(owned));
                return null;
            } catch (RuntimeException e) {
                dst.trace("fake: " + name + " the revocation callback throws " + e);
                return e;
            }
        }

        private RuntimeException listenerFailure(RuntimeException e) {
            if (interruption(e)) {
                return e;
            }
            return new KafkaException("User rebalance callback throws an error", e);
        }

        private boolean interruption(RuntimeException e) {
            return e instanceof WakeupException || e instanceof InterruptException;
        }

        private ConsumerRecords<Object, Object> poll(Duration timeout) {
            invokeCallbacks();
            checkWakeup();
            if (!subscribed) {
                violations.add(name + " polled without a subscription");
                throw new IllegalStateException("Consumer is not subscribed to any topics or assigned any partitions");
            }
            checkSuspended();
            lastPollStart = dst.now();
            lastPollReturned = false;
            rebalanceIfNeeded();
            // [A] the auto-commit of the positions, after the group was joined and before the fetch
            if (autoCommit && dst.now() - lastAutoCommit >= autoCommitIntervalMs) {
                autoCommit("interval");
            }
            if (faults && dst.chance(pollFailRate)) {
                dst.block(dst.between(1, 50), name + " poll fails");
                ledger.record("pollFault", "c", name);
                throw Dst.injected(new KafkaException("dst: poll failed"));
            }
            long waitMillis = Math.max(1, timeout.toMillis());
            boolean ready = dst.awaitUntil(
                    () -> wakeup || closed || fetchable() || generation != FakeKafka.this.generation, waitMillis,
                    name + " poll");
            if (ready && !wakeup && !closed) {
                // reads take effect at the end of their latency
                dst.block(dst.between(1, 20), name + " fetch");
            }
            if (closed) {
                violations.add(name + " closed while it polls");
                throw new IllegalStateException("This consumer has already been closed.");
            }
            checkWakeup();
            rebalanceIfNeeded();
            Map<TopicPartition, List<ConsumerRecord<Object, Object>>> map = new LinkedHashMap<>();
            int budget = maxPollRecords;
            if (budget > 1 && dst.chance(0.3)) {
                // [P] "fewer records might be returned"
                budget = (int) dst.between(1, budget - 1);
            }
            for (TopicPartition tp : owned) {
                if (paused.contains(tp) || budget == 0) {
                    continue;
                }
                List<Rec> partitionLog = log.get(tp.partition());
                long pos = position.get(tp);
                List<ConsumerRecord<Object, Object>> list = new ArrayList<>();
                while (budget > 0 && pos < partitionLog.size()) {
                    Rec r = partitionLog.get((int) pos);
                    list.add(new ConsumerRecord<>(
                            TOPIC, tp.partition(), r.offset, Dst.EPOCH_MILLIS + dst.now(), TimestampType.CREATE_TIME, -1,
                            -1, null, r.id, new RecordHeaders(), Optional.empty(), Optional.empty()));
                    ledger.record("fetched", "p" + tp.partition(), r.id);
                    pos++;
                    budget--;
                }
                position.put(tp, pos);
                if (!list.isEmpty()) {
                    map.put(tp, list);
                }
            }
            if (!map.isEmpty()) {
                dst.trace("fake: " + name + " poll returns " + map.values().stream().mapToInt(List::size).sum()
                          + " records " + positions());
            }
            lastPollReturned = true;
            return new ConsumerRecords<>(map, Map.of());
        }

        /**
         * The check of a suspended route: once the consumer completed a poll that started after the route was suspended
         * (and then handled its records and its pause request), it must have paused its partitions.
         */
        private void checkSuspended() {
            if (suspendedSince >= 0 && lastPollStart >= suspendedSince && lastPollReturned) {
                Set<TopicPartition> fetching = new TreeSet<>(FakeKafka::compare);
                fetching.addAll(owned);
                fetching.removeAll(paused);
                if (!fetching.isEmpty()) {
                    suspendViolations.add(name + " still fetches " + fetching + " at t=" + dst.now()
                                          + " after the route was suspended at t=" + suspendedSince);
                }
            }
        }

        private String positions() {
            return "positions=" + position;
        }

        private boolean fetchable() {
            for (TopicPartition tp : owned) {
                if (!paused.contains(tp) && position.get(tp) < log.get(tp.partition()).size()) {
                    return true;
                }
            }
            return false;
        }

        /** [G] an eager rebalance inside poll, when the group changed since this member last joined. */
        private void rebalanceIfNeeded() {
            if (generation == FakeKafka.this.generation) {
                return;
            }
            // the join and sync round trips of the group
            dst.block(dst.between(1, 50), name + " rebalance");
            if (closed || !subscribed) {
                return;
            }
            if (autoCommit) {
                autoCommit("before revocation");
            }
            if (!owned.isEmpty() && listener != null) {
                RuntimeException failure = revoke();
                if (interruption(failure)) {
                    // [L] thrown at once: the member keeps its partitions and joins again in its next poll
                    throw failure;
                }
                if (failure != null) {
                    // [L] the partitions are given up, the member joins again in its next poll
                    owned.clear();
                    paused.clear();
                    position.clear();
                    throw listenerFailure(failure);
                }
            }
            if (closed || !subscribed) {
                return;
            }
            long gen = FakeKafka.this.generation;
            Set<TopicPartition> assigned = assignmentOf(this);
            owned.clear();
            paused.clear();
            position.clear();
            owned.addAll(assigned);
            for (TopicPartition tp : owned) {
                Long c = committed.get(tp.partition());
                position.put(tp, c != null ? c : 0L);
            }
            generation = gen;
            dst.trace("fake: " + name + " assigned " + owned + " (generation " + gen + ") " + positions());
            if (listener != null) {
                try {
                    listener.onPartitionsAssigned(copyOf(owned));
                } catch (RuntimeException e) {
                    // [L] the partitions are taken, then poll throws
                    throw listenerFailure(e);
                }
            }
        }

        /** [A] commits the positions (before the fetch of a poll, before a revocation, at close). */
        private void autoCommit(String why) {
            lastAutoCommit = dst.now();
            if (owned.isEmpty()) {
                return;
            }
            if (faults && dst.chance(commitFailRate)) {
                dst.trace("fake: " + name + " auto-commit (" + why + ") fails");
                ledger.record("commitFault", "c", name);
                return;
            }
            Map<TopicPartition, OffsetAndMetadata> offsets = new TreeMap<>(FakeKafka::compare);
            for (TopicPartition tp : owned) {
                offsets.put(tp, new OffsetAndMetadata(position.get(tp)));
            }
            apply(this, offsets, "auto-commit (" + why + ")");
        }

        @SuppressWarnings("unchecked")
        private void commitSync(Object[] args) {
            invokeCallbacks();
            checkWakeup();
            Map<TopicPartition, OffsetAndMetadata> offsets = args != null && args.length > 0 && args[0] instanceof Map
                    ? (Map<TopicPartition, OffsetAndMetadata>) args[0] : consumedPositions();
            long timeout = defaultApiTimeoutMs;
            if (args != null && args.length > 0 && args[args.length - 1] instanceof Duration d) {
                timeout = d.toMillis();
            }
            checkCommittable(offsets);
            long latency = commitLatency();
            if (faults && dst.chance(commitFailRate) || latency > timeout) {
                // [S] the timeout expires: the broker may or may not have applied the commit
                boolean applied = dst.chance(0.5);
                if (applied) {
                    apply(this, offsets, "commitSync (it times out)");
                }
                ledger.record("commitFault", "c", name);
                dst.block(timeout, name + " commitSync times out" + (applied ? " (applied)" : " (not applied)"));
                throw Dst.injected(new TimeoutException(
                        "dst: Timeout of " + timeout + "ms expired before successfully committing offsets " + offsets));
            }
            // writes take effect at the start of their latency
            apply(this, offsets, "commitSync");
            dst.block(latency, name + " commitSync");
        }

        @SuppressWarnings("unchecked")
        private void commitAsync(Object[] args) {
            invokeCallbacks();
            Map<TopicPartition, OffsetAndMetadata> offsets;
            OffsetCommitCallback callback = null;
            if (args == null || args.length == 0) {
                offsets = consumedPositions();
            } else if (args.length == 1) {
                offsets = consumedPositions();
                callback = (OffsetCommitCallback) args[0];
            } else {
                offsets = (Map<TopicPartition, OffsetAndMetadata>) args[0];
                callback = (OffsetCommitCallback) args[1];
            }
            Exception error = null;
            try {
                checkCommittable(offsets);
            } catch (CommitFailedException e) {
                error = e;
            }
            if (error == null && faults && dst.chance(commitFailRate)) {
                ledger.record("commitFault", "c", name);
                error = Dst.injected(new RetriableCommitFailedException("dst: async commit failed"));
            }
            if (error == null) {
                apply(this, offsets, "commitAsync");
            } else {
                dst.trace("fake: " + name + " commitAsync " + offsets + " fails: " + error.getMessage());
            }
            // [C] the callback runs at a later call of this consumer, in order
            callbacks.add(new PendingCallback(copyOf(offsets), error, callback));
        }

        private void invokeCallbacks() {
            while (!callbacks.isEmpty()) {
                PendingCallback c = callbacks.remove(0);
                if (c.callback() != null) {
                    c.callback().onComplete(c.offsets(), c.error());
                }
            }
        }

        private Map<TopicPartition, OffsetAndMetadata> consumedPositions() {
            Map<TopicPartition, OffsetAndMetadata> offsets = new TreeMap<>(FakeKafka::compare);
            for (TopicPartition tp : owned) {
                offsets.put(tp, new OffsetAndMetadata(position.get(tp)));
            }
            return offsets;
        }

        /** [S] the group takes a commit of a member for the partitions it owns. */
        private void checkCommittable(Map<TopicPartition, OffsetAndMetadata> offsets) {
            for (TopicPartition tp : offsets.keySet()) {
                if (!owned.contains(tp)) {
                    dst.trace("fake: " + name + " commit of " + tp + ", which it does not own " + offsets);
                    throw new CommitFailedException(
                            "dst: Commit cannot be completed since the group has already rebalanced and assigned the"
                                                    + " partitions to another member");
                }
            }
        }

        private Map<TopicPartition, OffsetAndMetadata> committed(Object[] args) {
            checkWakeup();
            @SuppressWarnings("unchecked")
            Set<TopicPartition> partitions = (Set<TopicPartition>) args[0];
            // [R] a remote call: reads take effect at the end of their latency
            dst.block(dst.chance(0.2) ? dst.between(100, 2000) : dst.between(1, 30), name + " committed");
            checkWakeup();
            Map<TopicPartition, OffsetAndMetadata> answer = new TreeMap<>(FakeKafka::compare);
            for (TopicPartition tp : partitions) {
                Long c = committed.get(tp.partition());
                answer.put(tp, c != null ? new OffsetAndMetadata(c) : null);
            }
            return answer;
        }

        private void seek(TopicPartition tp, long offset) {
            if (!owned.contains(tp)) {
                violations.add(name + " seeks " + tp + ", which is not assigned");
                throw new IllegalStateException("No current assignment for partition " + tp);
            }
            if (offset < 0) {
                violations.add(name + " seeks " + tp + " to " + offset);
                throw new IllegalArgumentException("seek offset must not be a negative number");
            }
            position.put(tp, offset);
            dst.trace("fake: " + name + " seek " + tp + " to " + offset);
        }

        @SuppressWarnings("unchecked")
        private void pause(Object[] args, boolean pause) {
            Collection<TopicPartition> partitions = (Collection<TopicPartition>) args[0];
            for (TopicPartition tp : partitions) {
                if (!owned.contains(tp)) {
                    violations.add(name + (pause ? " pauses " : " resumes ") + tp + ", which is not assigned");
                    throw new IllegalStateException("No current assignment for partition " + tp);
                }
            }
            if (pause) {
                paused.addAll(partitions);
            } else {
                paused.removeAll(partitions);
            }
            dst.trace("fake: " + name + (pause ? " pause " : " resume ") + partitions);
        }

        private void close() {
            if (closed) {
                return;
            }
            dst.trace("fake: " + name + " close");
            // [L] the classic client turns wakeups off before it closes
            wakeup = false;
            try {
                if (subscribed) {
                    if (autoCommit) {
                        autoCommit("close");
                    }
                    invokeCallbacks();
                    leave();
                } else {
                    invokeCallbacks();
                }
            } finally {
                // as KafkaConsumer.close: the consumer is closed, then the first exception (of a callback) is thrown
                closed = true;
                open.remove(this);
            }
        }
    }

    private final Dst dst;
    private final DstLedger ledger;
    final List<List<Rec>> log = new ArrayList<>();
    /** The committed offset of each partition of the group (the next record to read), absent if none. */
    final Map<Integer, Long> committed = new TreeMap<>();
    final List<String> violations = new ArrayList<>();
    final List<String> lossViolations = new ArrayList<>();
    final List<String> suspendViolations = new ArrayList<>();
    final List<Member> open = new ArrayList<>();
    final List<Member> members = new ArrayList<>();
    final Set<Integer> phantom = new TreeSet<>();
    long generation;
    boolean autoCommit;
    long autoCommitIntervalMs = 5000;
    long defaultApiTimeoutMs = 60_000;
    boolean breakOnFirstError;
    int maxPollRecords = 500;
    double createFailRate;
    double pollFailRate;
    double commitFailRate;
    boolean slowCommits;
    boolean faults = true;
    /** Since when the route is suspended (virtual time), or -1. */
    long suspendedSince = -1;
    private int created;

    FakeKafka(Dst dst, DstLedger ledger, int partitions) {
        this.dst = dst;
        this.ledger = ledger;
        for (int p = 0; p < partitions; p++) {
            log.add(new ArrayList<>());
        }
    }

    /** A copy in partition order (TopicPartition is not Comparable). */
    static Set<TopicPartition> copyOf(Collection<TopicPartition> partitions) {
        Set<TopicPartition> copy = new TreeSet<>(FakeKafka::compare);
        copy.addAll(partitions);
        return copy;
    }

    static Map<TopicPartition, OffsetAndMetadata> copyOf(Map<TopicPartition, OffsetAndMetadata> offsets) {
        Map<TopicPartition, OffsetAndMetadata> copy = new TreeMap<>(FakeKafka::compare);
        copy.putAll(offsets);
        return copy;
    }

    static int compare(TopicPartition a, TopicPartition b) {
        int c = a.topic().compareTo(b.topic());
        return c != 0 ? c : Integer.compare(a.partition(), b.partition());
    }

    /** KafkaClientFactory.getConsumer. */
    @SuppressWarnings("unchecked")
    Consumer<Object, Object> newConsumer() {
        if (faults && dst.chance(createFailRate)) {
            ledger.record("createFault", "c", "c" + created);
            throw Dst.injected(new KafkaException("dst: Failed to construct kafka consumer"));
        }
        Member member = new Member("c" + created++);
        open.add(member);
        dst.trace("fake: created " + member.name);
        return (Consumer<Object, Object>) Proxy.newProxyInstance(
                Consumer.class.getClassLoader(), new Class<?>[] { Consumer.class }, member);
    }

    void publish(int partition, String id) {
        List<Rec> partitionLog = log.get(partition);
        partitionLog.add(new Rec(partition, partitionLog.size(), id));
        ledger.record("published", "p" + partition, id);
    }

    Rec rec(int partition, long offset) {
        return log.get(partition).get((int) offset);
    }

    /**
     * Whether the route has finished with a record: it completed, or it failed and the consumer skips it by design
     * (breakOnFirstError=false: the error handler handled it). With breakOnFirstError=true a failed record must be
     * consumed again.
     */
    boolean done(Rec r) {
        return r.completed > 0 || (r.failed > 0 && !breakOnFirstError);
    }

    boolean done(TopicPartition tp, long offset) {
        return done(rec(tp.partition(), offset));
    }

    /** A member joined or left, or the phantom member did: every member rebalances at its next poll. */
    void groupChanged(String why) {
        generation++;
        dst.trace("fake: group changed (" + why + "), generation " + generation);
    }

    void phantomJoins(Set<Integer> partitions) {
        phantom.clear();
        phantom.addAll(partitions);
        groupChanged("a phantom member joins and takes " + partitions);
    }

    void phantomLeaves() {
        if (!phantom.isEmpty()) {
            phantom.clear();
            groupChanged("the phantom member leaves");
        }
    }

    /** The partitions of a member in the current generation: the phantom's first, the rest in turn. */
    private Set<TopicPartition> assignmentOf(Member member) {
        Set<TopicPartition> answer = new TreeSet<>(FakeKafka::compare);
        int index = members.indexOf(member);
        if (index < 0) {
            return answer;
        }
        int turn = 0;
        for (int p = 0; p < log.size(); p++) {
            if (phantom.contains(p)) {
                continue;
            }
            if (turn % members.size() == index) {
                answer.add(new TopicPartition(TOPIC, p));
            }
            turn++;
        }
        return answer;
    }

    /**
     * The group takes a commit. A committed offset must not pass a record the route has not finished with: that record
     * would not be consumed again by the group (at-least-once).
     */
    private void apply(Member member, Map<TopicPartition, OffsetAndMetadata> offsets, String how) {
        for (Map.Entry<TopicPartition, OffsetAndMetadata> e : offsets.entrySet()) {
            int p = e.getKey().partition();
            long offset = e.getValue().offset();
            List<Rec> partitionLog = log.get(p);
            if (offset > partitionLog.size()) {
                violations.add(member.name + " committed " + offset + " on p" + p + " past the end " + partitionLog.size());
            }
            for (long o = 0; o < Math.min(offset, partitionLog.size()); o++) {
                Rec r = partitionLog.get((int) o);
                if (!done(r)) {
                    lossViolations.add(member.name + " " + how + " committed offset " + offset + " on p" + p + " at t="
                                       + dst.now() + " but the route has not finished with " + r.id + " (offset " + o
                                       + ", routed " + r.routed + ", completed " + r.completed + ", failed "
                                       + r.failed + ")");
                    break;
                }
            }
            committed.put(p, offset);
            ledger.record("committed", "p" + p, String.valueOf(offset));
        }
        dst.trace("fake: " + member.name + " " + how + " " + offsets);
    }

    private long commitLatency() {
        return faults && slowCommits && dst.chance(0.2) ? dst.between(100, 3000) : dst.between(1, 30);
    }

    /** Every partition has its committed offset at the end of its log. */
    boolean allCommitted() {
        for (int p = 0; p < log.size(); p++) {
            long c = committed.getOrDefault(p, 0L);
            if (c < log.get(p).size()) {
                return false;
            }
        }
        return true;
    }

    String notCommitted() {
        StringBuilder sb = new StringBuilder();
        for (int p = 0; p < log.size(); p++) {
            sb.append("p").append(p).append(" committed=").append(committed.get(p)).append(" end=")
                    .append(log.get(p).size()).append(' ');
        }
        return sb.toString().trim();
    }

    String openNames() {
        List<String> names = new ArrayList<>();
        for (Member m : open) {
            names.add(m.name);
        }
        return names.toString();
    }
}
