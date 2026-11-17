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
package org.apache.camel.component.google.pubsub.dst;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import com.google.api.core.ApiFuture;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.StatusCode;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.api.gax.rpc.UnavailableException;
import com.google.cloud.pubsub.v1.stub.SubscriberStub;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;
import com.google.pubsub.v1.AcknowledgeRequest;
import com.google.pubsub.v1.ModifyAckDeadlineRequest;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.PullRequest;
import com.google.pubsub.v1.PullResponse;
import com.google.pubsub.v1.ReceivedMessage;
import org.apache.camel.test.dst.Dst;
import org.apache.camel.test.dst.DstExecutor;
import org.apache.camel.test.dst.DstLedger;

/**
 * A fake Google Cloud Pub/Sub subscription behind the {@link SubscriberStub} of the synchronous pull, written from the
 * Pub/Sub documentation, not from the Camel consumer:
 * <ul>
 * <li>[P] Pull (https://cloud.google.com/pubsub/docs/reference/rpc/google.pubsub.v1#google.pubsub.v1.Subscriber.Pull):
 * returns at most max_messages, "The server may return fewer than max_messages"; with return_immediately=false it waits
 * for messages, and may still answer with none. The delivery_attempt of a received message is set only when the
 * subscription has a dead letter policy, and is 1 for the first delivery.</li>
 * <li>[D] The ack deadline (https://cloud.google.com/pubsub/docs/lease-management): a message that is not acknowledged
 * before its deadline is delivered again, with a new ack id.</li>
 * <li>[A] Acknowledge: "Acknowledging a message whose ack deadline has expired may succeed, but such a message may be
 * redelivered later. Acknowledging a message more than once will not result in an error." (Here: an ack of the ack id
 * of the current delivery takes effect, also after its deadline, as long as the message was not delivered again; an ack
 * of an older ack id does nothing, which is assumed.)</li>
 * <li>[M] ModifyAckDeadline with ack_deadline_seconds 0: "the message is immediately made available for another
 * delivery".</li>
 * <li>[L] Dead lettering (https://cloud.google.com/pubsub/docs/handling-failures): once a message was delivered
 * max_delivery_attempts times without an acknowledgement, the next nack or expired deadline forwards it to the dead
 * letter topic instead of delivering it again.</li>
 * <li>[E] A failed call: UNAVAILABLE (retryable), as the client library reports it.</li>
 * </ul>
 * Not modelled: the streaming pull of the asynchronous subscriber, message ordering, exactly-once delivery, filters,
 * retention and seek, the retry policy delays of the subscription, flow control.
 * <p>
 * Timing (one-stack rule): a pull returns a future at once (as the gRPC client does) that completes after its latency
 * with the messages available then; acknowledge and modifyAckDeadline take effect at the start of their latency.
 */
final class FakePubsub {

    static final String PROJECT = "dst-project";
    static final String SUBSCRIPTION = "dst-subscription";

    enum State {
        AVAILABLE,
        OUTSTANDING,
        ACKED,
        DEAD
    }

    static final class Msg {
        final String id;
        State state = State.AVAILABLE;
        int deliveries;
        String ackId;
        long deadline;

        Msg(String id) {
            this.id = id;
        }
    }

    private final Dst dst;
    private final DstLedger ledger;
    private final DstExecutor server;
    final Map<String, Msg> messages = new LinkedHashMap<>();
    private final Map<String, Msg> byAckId = new LinkedHashMap<>();
    final List<String> violations = new ArrayList<>();
    int ackDeadlineSeconds = 10;
    int maxDeliveryAttempts;
    double pullFailRate;
    double ackFailRate;
    boolean faults = true;
    /** Whether the route is stopped: a pull issued then is a violation (see the world). */
    boolean routeStopped;
    final List<String> pullsWhileStopped = new ArrayList<>();
    int pullsInFlight;
    int maxPullsInFlight;
    /** Called with the delivery ("id#n") of each ack and nack the subscription gets, for the checks of the world. */
    Consumer<String> onAck = d -> {
    };
    Consumer<String> onNack = d -> {
    };
    private int calls;

    FakePubsub(Dst dst, DstLedger ledger) {
        this.dst = dst;
        this.ledger = ledger;
        this.server = dst.executor("pubsub", Integer.MAX_VALUE);
    }

    void publish(String id) {
        messages.put(id, new Msg(id));
        ledger.record("published", "s", id);
    }

    /** [D] [L] deadlines that expired. */
    void expire() {
        for (Msg m : messages.values()) {
            if (m.state == State.OUTSTANDING && dst.now() >= m.deadline) {
                ledger.record("deadlineExpired", "s", m.id);
                release(m, "ack deadline expired");
            }
        }
    }

    private void release(Msg m, String why) {
        if (maxDeliveryAttempts > 0 && m.deliveries >= maxDeliveryAttempts) {
            m.state = State.DEAD;
            ledger.record("deadLettered", "s", m.id);
        } else {
            m.state = State.AVAILABLE;
        }
        dst.trace("fake: " + m.id + " " + why + " -> " + m.state);
    }

    boolean available() {
        expire();
        return messages.values().stream().anyMatch(m -> m.state == State.AVAILABLE);
    }

    boolean settled() {
        expire();
        return messages.values().stream().allMatch(m -> m.state == State.ACKED || m.state == State.DEAD);
    }

    String stateOf(String id) {
        Msg m = messages.get(id);
        return m == null ? "not published" : m.state + "/d" + m.deliveries;
    }

    String unsettled() {
        return messages.values().stream().filter(m -> m.state != State.ACKED && m.state != State.DEAD)
                .map(m -> m.id + "=" + m.state + "/d" + m.deliveries).toList().toString();
    }

    private static StatusCode unavailable() {
        return new StatusCode() {
            @Override
            public Code getCode() {
                return Code.UNAVAILABLE;
            }

            @Override
            public Object getTransportCode() {
                return "UNAVAILABLE";
            }
        };
    }

    private long latency() {
        return dst.chance(0.1) ? dst.between(200, 2000) : dst.between(5, 80);
    }

    /** GooglePubsubComponent.getSubscriberStub: a stub on this subscription. */
    SubscriberStub newStub() {
        return new SubscriberStub() {
            @Override
            public UnaryCallable<PullRequest, PullResponse> pullCallable() {
                return new UnaryCallable<>() {
                    @Override
                    public ApiFuture<PullResponse> futureCall(PullRequest request, ApiCallContext context) {
                        return pull(request);
                    }
                };
            }

            @Override
            public UnaryCallable<AcknowledgeRequest, Empty> acknowledgeCallable() {
                return new UnaryCallable<>() {
                    @Override
                    public ApiFuture<Empty> futureCall(AcknowledgeRequest request, ApiCallContext context) {
                        throw new UnsupportedOperationException("the consumer calls call()");
                    }

                    @Override
                    public Empty call(AcknowledgeRequest request) {
                        acknowledge(request);
                        return Empty.getDefaultInstance();
                    }
                };
            }

            @Override
            public UnaryCallable<ModifyAckDeadlineRequest, Empty> modifyAckDeadlineCallable() {
                return new UnaryCallable<>() {
                    @Override
                    public ApiFuture<Empty> futureCall(ModifyAckDeadlineRequest request, ApiCallContext context) {
                        throw new UnsupportedOperationException("the consumer calls call()");
                    }

                    @Override
                    public Empty call(ModifyAckDeadlineRequest request) {
                        modifyAckDeadline(request);
                        return Empty.getDefaultInstance();
                    }
                };
            }

            @Override
            public void close() {
                // nothing to release
            }

            @Override
            public void shutdown() {
                // nothing to release
            }

            @Override
            public boolean isShutdown() {
                return false;
            }

            @Override
            public boolean isTerminated() {
                return false;
            }

            @Override
            public void shutdownNow() {
                // nothing to release
            }

            @Override
            public boolean awaitTermination(long duration, TimeUnit unit) {
                return true;
            }
        };
    }

    private void checkSubscription(String subscription) {
        String expected = "projects/" + PROJECT + "/subscriptions/" + SUBSCRIPTION;
        if (!expected.equals(subscription)) {
            violations.add("a call for the subscription " + subscription);
        }
    }

    /** [P] a pull: answered after its latency, or a server wait, with the messages available then. */
    private ApiFuture<PullResponse> pull(PullRequest request) {
        checkSubscription(request.getSubscription());
        if (request.getMaxMessages() < 1) {
            violations.add("pull with max_messages " + request.getMaxMessages());
        }
        String name = "pull-" + calls++;
        if (routeStopped) {
            pullsWhileStopped.add(name + " at t=" + dst.now());
        }
        DstFuture<PullResponse> future = new DstFuture<>(name);
        pullsInFlight++;
        maxPullsInFlight = Math.max(maxPullsInFlight, pullsInFlight);
        long serverWait = dst.between(1000, 10_000);
        long start = dst.now();
        Runnable[] answer = new Runnable[1];
        answer[0] = () -> {
            if (future.isDone()) {
                return;
            }
            if (faults && dst.chance(pullFailRate)) {
                ledger.record("pullFault", "s", name);
                pullsInFlight--;
                future.fail(new ExecutionException(
                        Dst.injected(
                                new UnavailableException("dst: pull failed", null, unavailable(), true))));
                return;
            }
            if (!available() && dst.now() - start < serverWait) {
                // [P] the pull waits for messages
                server.schedule(name + " waits", 1000, answer[0]);
                return;
            }
            pullsInFlight--;
            future.complete(deliver(request.getMaxMessages(), name));
        };
        server.schedule(name, latency(), answer[0]);
        return future;
    }

    private PullResponse deliver(int max, String pull) {
        expire();
        int budget = max;
        if (budget > 1 && dst.chance(0.3)) {
            budget = (int) dst.between(1, budget - 1);
        }
        PullResponse.Builder answer = PullResponse.newBuilder();
        for (Msg m : messages.values()) {
            if (budget == 0) {
                break;
            }
            if (m.state != State.AVAILABLE) {
                continue;
            }
            m.state = State.OUTSTANDING;
            m.deliveries++;
            m.ackId = "ack-" + m.id + "#" + m.deliveries;
            m.deadline = dst.now() + ackDeadlineSeconds * 1000L;
            byAckId.put(m.ackId, m);
            ReceivedMessage.Builder received = ReceivedMessage.newBuilder().setAckId(m.ackId)
                    .setMessage(PubsubMessage.newBuilder().setMessageId(m.id).setData(ByteString.copyFromUtf8(m.id))
                            .setPublishTime(Timestamp.newBuilder().setSeconds((Dst.EPOCH_MILLIS + dst.now()) / 1000)));
            if (maxDeliveryAttempts > 0) {
                received.setDeliveryAttempt(m.deliveries);
            }
            answer.addReceivedMessages(received);
            ledger.record("delivered", "s", m.id + "#" + m.deliveries);
            budget--;
        }
        if (answer.getReceivedMessagesCount() > 0) {
            dst.trace("fake: " + pull + " returns " + answer.getReceivedMessagesCount() + " messages");
        }
        return answer.build();
    }

    private void acknowledge(AcknowledgeRequest request) {
        checkSubscription(request.getSubscription());
        if (request.getAckIdsCount() == 0) {
            violations.add("acknowledge without ack ids");
        }
        if (faults && dst.chance(ackFailRate)) {
            ledger.record("ackFault", "s", request.getAckIdsList().toString());
            dst.block(latency(), "acknowledge fails");
            throw Dst.injected(new UnavailableException("dst: acknowledge failed", null, unavailable(), true));
        }
        // writes take effect at the start of their latency
        for (String ackId : request.getAckIdsList()) {
            Msg m = byAckId.get(ackId);
            if (m == null) {
                violations.add("acknowledge of an unknown ack id " + ackId);
                continue;
            }
            onAck.accept(ackId.substring(4));
            expire();
            if (ackId.equals(m.ackId) && m.state == State.OUTSTANDING) {
                m.state = State.ACKED;
                ledger.record("acked", "s", ackId.substring(4));
            } else if (ackId.equals(m.ackId) && m.state == State.AVAILABLE && dst.chance(0.5)) {
                // [A] expired, not delivered again yet: the ack may still succeed
                m.state = State.ACKED;
                ledger.record("acked", "s", ackId.substring(4) + " (after its deadline)");
            } else {
                ledger.record("ackIgnored", "s", ackId.substring(4));
            }
        }
        dst.block(latency(), "acknowledge");
    }

    private void modifyAckDeadline(ModifyAckDeadlineRequest request) {
        checkSubscription(request.getSubscription());
        if (request.getAckDeadlineSeconds() != 0) {
            violations.add("modifyAckDeadline to " + request.getAckDeadlineSeconds() + " s (only nacks are modelled)");
        }
        if (faults && dst.chance(ackFailRate)) {
            ledger.record("nackFault", "s", request.getAckIdsList().toString());
            dst.block(latency(), "modifyAckDeadline fails");
            throw Dst.injected(new UnavailableException("dst: modifyAckDeadline failed", null, unavailable(), true));
        }
        for (String ackId : request.getAckIdsList()) {
            Msg m = byAckId.get(ackId);
            if (m == null) {
                violations.add("modifyAckDeadline of an unknown ack id " + ackId);
                continue;
            }
            onNack.accept(ackId.substring(4));
            expire();
            if (ackId.equals(m.ackId) && m.state == State.OUTSTANDING) {
                // [M] [L]
                ledger.record("nacked", "s", ackId.substring(4));
                release(m, "nacked");
            }
        }
        dst.block(latency(), "modifyAckDeadline");
    }

    /**
     * A future of the run: the consumer waits for it with {@code get()} on the loop thread (an ApiFuture is not one of
     * the futures the harness rewrites), so its wait runs the other tasks of the run; a cancel ends the wait.
     */
    final class DstFuture<T> implements ApiFuture<T> {
        private final String name;
        private T value;
        private ExecutionException failure;
        private boolean done;
        private boolean cancelled;

        DstFuture(String name) {
            this.name = name;
        }

        void complete(T value) {
            if (!done) {
                this.value = value;
                done = true;
            }
        }

        void fail(ExecutionException failure) {
            if (!done) {
                this.failure = failure;
                done = true;
            }
        }

        @Override
        public void addListener(Runnable listener, Executor executor) {
            throw new UnsupportedOperationException("not used by the synchronous pull");
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            if (done) {
                return false;
            }
            dst.trace("fake: " + name + " cancelled");
            cancelled = true;
            done = true;
            pullsInFlight--;
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return done;
        }

        @Override
        public T get() throws ExecutionException {
            dst.awaitCondition(this::isDone, name);
            if (cancelled) {
                throw new CancellationException(name + " cancelled");
            }
            if (failure != null) {
                throw failure;
            }
            return value;
        }

        @Override
        public T get(long timeout, TimeUnit unit) throws ExecutionException, TimeoutException {
            if (!dst.awaitUntil(this::isDone, unit.toMillis(timeout), name)) {
                throw new TimeoutException(name);
            }
            return get();
        }
    }
}
