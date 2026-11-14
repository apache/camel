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
package org.apache.camel.component.azure.servicebus.dst;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.azure.core.amqp.exception.AmqpErrorCondition;
import com.azure.core.amqp.exception.AmqpException;
import com.azure.core.util.BinaryData;
import com.azure.messaging.servicebus.ServiceBusErrorContext;
import com.azure.messaging.servicebus.ServiceBusErrorSource;
import com.azure.messaging.servicebus.ServiceBusException;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.azure.messaging.servicebus.ServiceBusReceiverAsyncClient;
import org.apache.camel.test.dst.Dst;
import org.apache.camel.test.dst.DstExecutor;
import org.apache.camel.test.dst.DstLedger;
import org.apache.camel.test.dst.DstTask;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A fake Azure Service Bus queue in peek-lock mode, and the clients camel-azure-servicebus gets from its
 * ServiceBusClientFactory (Mockito mocks of the final SDK classes, whose behaviour is the queue below). Written from
 * the Service Bus documentation, not from the Camel consumer:
 * <ul>
 * <li>[L] Peek-lock (https://learn.microsoft.com/azure/service-bus-messaging/message-transfers-locks-settlement): a
 * received message is locked for the lock duration of the queue; "When the lock on a message is explicitly released or
 * when the lock expires, the message goes back to the front of the retrieval order for redelivery"; "Whenever a message
 * is delivered under a peek-lock, but is either explicitly abandoned or the lock has expired, the delivery count on the
 * message is incremented. When the delivery count exceeds the limit, the message is moved to the DLQ"
 * (https://learn.microsoft.com/azure/service-bus-messaging/service-bus-dead-letter-queues).</li>
 * <li>[S] Settlement: complete removes the message, abandon releases the lock (the message is available again),
 * deadLetter moves it to the dead-letter queue; "The Complete, DeadLetter, or RenewLock operations might fail due to
 * network issues, if the held lock has expired" (assumed: the Java SDK reports it as a ServiceBusException with reason
 * MESSAGE_LOCK_LOST). "Settling a message after you close the receiver throws an exception, and the message isn't
 * settled" (dead-letter page): in azure-messaging-servicebus 7.17 the processor of a queue runs the V2 stack by
 * default, where stop() closes the processor and its receiver, so a settlement after stop() or close() throws
 * IllegalStateException. A settlement of a delivery that was settled already is a misuse (the SDK rejects it).</li>
 * <li>[R] RenewMessageLock: "The client that owns the lock can extend it" (assumed: also the receiver client the
 * consumer creates for its renewals, which is what the consumer relies on); the call takes a while, and the lock is
 * extended by the lock duration from when the server handles it, which fails when the lock was lost by then. A renewal
 * through a closed receiver client is a misuse.</li>
 * <li>[P] The processor client (ServiceBusProcessorClient, auto-complete disabled by the factory): it receives messages
 * and calls processMessage with up to maxConcurrentCalls calls at a time, and receives the next message for a call only
 * when the call returned; stop and close end the receiving (and close the receiver, see [S]).</li>
 * <li>[A] The lock renewal of the processor client while processMessage runs
 * (https://learn.microsoft.com/java/api/com.azure.messaging.servicebus.servicebusclientbuilder.servicebusprocessorclientbuilder):
 * "By default, a ServiceBusProcessorClient is configured with auto-completion and auto-lock renewal capabilities";
 * maxAutoLockRenewDuration "Sets the amount of time to continue auto-renewing the lock. Setting Duration#ZERO or null
 * disables auto-renewal"; "To keep the message locked, the client will have to continuously renew the message lock
 * before its expiration. maxAutoLockRenewDuration controls how long the background renewal task runs" (the factory
 * passes the maxAutoLockRenewDuration of the endpoint). In azure-messaging-servicebus 7.17 (javap of 7.17.16) the V2
 * MessagePump begins the renewal of a message before it calls processMessage and disposes it when the call returns;
 * LockRenewalOperation renews when the lock has min(half of the time left, 10 seconds) left, until
 * maxAutoLockRenewDuration has passed, and ends at the first failed renewal. The renewal goes through the receiver of
 * the processor, so it ends when stop() or close() disposed it.</li>
 * </ul>
 * Not modelled: sessions, prefetch, deferral, scheduled messages, time to live, duplicate detection, topics and
 * subscriptions (a queue only), faults and latency of the renewals of [A] (the faults are injected on the renewals of
 * the consumer), connection loss and the processError callback.
 * <p>
 * Timing (one-stack rule): a receive takes the message at the end of its latency; settlement takes effect at once (it
 * is made from completion callbacks); a renewal does not block (the lock renewer of the consumer and the renewal of [A]
 * are background tasks), the latency of a renewal of the consumer is drawn when it is called and decides whether it
 * comes in time.
 */
final class FakeServiceBus {

    static final String QUEUE = "dst";
    /** [A] LockRenewalOperation renews when the lock has min(half of the time left, this) left. */
    static final long MAX_RENEWAL_BUFFER_MILLIS = 10_000;

    enum State {
        AVAILABLE,
        LOCKED,
        COMPLETED,
        DEAD_LETTERED
    }

    static final class Msg {
        final String id;
        State state = State.AVAILABLE;
        int deliveryCount;
        String lockToken;
        long lockedUntil;

        Msg(String id) {
            this.id = id;
        }
    }

    private final Dst dst;
    private final DstLedger ledger;
    final Map<String, Msg> messages = new LinkedHashMap<>();
    final List<String> violations = new ArrayList<>();
    long lockDurationMillis = 30_000;
    int maxDeliveryCount = 10;
    double renewFailRate;
    double settleFailRate;
    /** The longest latency of a renewal while the faults are on (decided when the renewal is called). */
    long renewLatencyMaxMillis;
    boolean faults = true;
    /** Called with the delivery ("id#n") when a message is delivered. */
    Consumer<String> onDelivery = d -> {
    };
    /** Called with the delivery when an injected renewal fault hits it. */
    Consumer<String> onRenewFault = d -> {
    };
    /** Called with "delivery STATE" when the consumer settles a delivery, before the outcome is known. */
    Consumer<String> onSettle = d -> {
    };
    /**
     * Called with "delivery outcome" after it: COMPLETED, ABANDONED, DEAD_LETTERED, fault, lockLost, closedByStop
     * (after stop() and before close()) or closed (after close()).
     */
    Consumer<String> onSettled = d -> {
    };
    /** Called when a processor client is stopped or closed. */
    Runnable onProcessorClosed = () -> {
    };
    /** Called with the delivery when its lock expires. */
    Consumer<String> onLockExpired = d -> {
    };
    private final Set<String> settledDeliveries = new HashSet<>();
    private int tokens;
    private int receivers;

    FakeServiceBus(Dst dst, DstLedger ledger) {
        this.dst = dst;
        this.ledger = ledger;
    }

    void send(String id) {
        messages.put(id, new Msg(id));
        ledger.record("sent", "q", id);
    }

    /** [L] locks that expired. */
    void expire() {
        for (Msg m : messages.values()) {
            if (m.state == State.LOCKED && dst.now() >= m.lockedUntil) {
                ledger.record("lockExpired", "q", m.id + "#" + m.deliveryCount);
                onLockExpired.accept(m.id + "#" + m.deliveryCount);
                available(m, "lock expired");
            }
        }
    }

    private void available(Msg m, String why) {
        m.lockToken = null;
        if (m.deliveryCount >= maxDeliveryCount) {
            m.state = State.DEAD_LETTERED;
            ledger.record("deadLettered", "q", m.id + " (MaxDeliveryCountExceeded)");
        } else {
            m.state = State.AVAILABLE;
        }
        dst.trace("fake: " + m.id + " " + why + " -> " + m.state);
    }

    boolean settled() {
        expire();
        return messages.values().stream()
                .allMatch(m -> m.state == State.COMPLETED || m.state == State.DEAD_LETTERED);
    }

    String unsettled() {
        return messages.values().stream().filter(m -> m.state != State.COMPLETED && m.state != State.DEAD_LETTERED)
                .map(m -> m.id + "=" + m.state + "/d" + m.deliveryCount).toList().toString();
    }

    private static ServiceBusException lockLost(String token, ServiceBusErrorSource source) {
        return new ServiceBusException(
                new AmqpException(
                        false, AmqpErrorCondition.MESSAGE_LOCK_LOST,
                        "dst: the lock " + token + " is lost or expired", null),
                source);
    }

    private static ServiceBusException transientFailure(ServiceBusErrorSource source) {
        return new ServiceBusException(
                new AmqpException(true, AmqpErrorCondition.SERVER_BUSY_ERROR, "dst: the server is busy", null), source);
    }

    private Msg locked(String token) {
        expire();
        for (Msg m : messages.values()) {
            if (m.state == State.LOCKED && token.equals(m.lockToken)) {
                return m;
            }
        }
        return null;
    }

    /** ServiceBusClientFactory.createServiceBusProcessorClient: [P] a processor client on this queue. */
    ServiceBusProcessorClient newProcessorClient(
            int maxConcurrentCalls, long maxAutoLockRenewMillis,
            Consumer<ServiceBusReceivedMessageContext> processMessage, Consumer<ServiceBusErrorContext> processError) {
        ServiceBusProcessorClient client = mock(ServiceBusProcessorClient.class);
        String name = "processor-" + receivers++;
        DstExecutor calls = dst.executor(name + "-calls", maxConcurrentCalls);
        DstExecutor receiver = dst.executor(name + "-receive", 1);
        DstExecutor lockRenewal = dst.executor(name + "-lock-renewal", Integer.MAX_VALUE);
        boolean[] running = new boolean[1];
        // 0: open, 1: stopped (stop() closed the receiver), 2: closed
        int[] closed = new int[1];
        int[] inCalls = new int[1];
        Runnable[] receive = new Runnable[1];
        receive[0] = () -> {
            if (!running[0]) {
                return;
            }
            expire();
            Msg next = inCalls[0] < maxConcurrentCalls
                    ? messages.values().stream().filter(m -> m.state == State.AVAILABLE).findFirst().orElse(null)
                    : null;
            if (next == null) {
                receiver.schedule(name + " receive", 100, receive[0]);
                return;
            }
            // reads take effect at the end of their latency: the receive takes the message after it
            dst.block(dst.between(5, 50), name + " receive");
            expire();
            if (!running[0] || next.state != State.AVAILABLE) {
                receiver.schedule(name + " receive", 0, receive[0]);
                return;
            }
            next.state = State.LOCKED;
            next.deliveryCount++;
            next.lockToken = "lock-" + tokens++;
            next.lockedUntil = dst.now() + lockDurationMillis;
            String delivery = next.id + "#" + next.deliveryCount;
            ledger.record("delivered", "q", delivery);
            onDelivery.accept(delivery);
            ServiceBusReceivedMessageContext context = context(next, next.lockToken, delivery, closed);
            inCalls[0]++;
            String token = next.lockToken;
            calls.schedule(name + " processMessage " + delivery, 0, () -> {
                // [A] the renewal of the lock while the call runs, disposed when it returns
                DstTask<?>[] renewal = new DstTask<?>[1];
                if (maxAutoLockRenewMillis > 0) {
                    Msg m = locked(token);
                    if (m != null) {
                        renewLockWhileProcessed(lockRenewal, renewal, token, delivery, m.lockedUntil,
                                dst.now() + maxAutoLockRenewMillis, closed);
                    }
                }
                try {
                    processMessage.accept(context);
                } finally {
                    if (renewal[0] != null) {
                        renewal[0].cancel(false);
                    }
                    inCalls[0]--;
                }
            });
            receiver.schedule(name + " receive", 0, receive[0]);
        };
        doAnswer(i -> {
            if (closed[0] != 0) {
                violations.add(name + " started after it was closed");
                return null;
            }
            if (!running[0]) {
                running[0] = true;
                dst.trace("fake: " + name + " start");
                receiver.schedule(name + " receive", 0, receive[0]);
            }
            return null;
        }).when(client).start();
        doAnswer(i -> {
            // [P] [S] V2 stack: stop() closes the processor and its receiver
            running[0] = false;
            closed[0] = Math.max(closed[0], 1);
            dst.trace("fake: " + name + " stop (closes the receiver)");
            onProcessorClosed.run();
            return null;
        }).when(client).stop();
        doAnswer(i -> {
            running[0] = false;
            closed[0] = 2;
            dst.trace("fake: " + name + " close");
            onProcessorClosed.run();
            return null;
        }).when(client).close();
        when(client.isRunning()).thenAnswer(i -> running[0]);
        return client;
    }

    /**
     * [A] The next renewal of the processor client for a message whose processMessage call runs: when the lock it knows
     * of has min(half of the time left, 10 seconds) left; it ends once maxAutoLockRenewDuration has passed, when the
     * receiver of the processor is closed, or at the first failed renewal (the lock was lost).
     */
    private void renewLockWhileProcessed(
            DstExecutor lockRenewal, DstTask<?>[] renewal, String token, String delivery, long lockedUntil, long until,
            int[] closed) {
        long left = lockedUntil - dst.now();
        long delay = left < 400 ? 0 : left - Math.min(left / 2, MAX_RENEWAL_BUFFER_MILLIS);
        renewal[0] = lockRenewal.schedule("processor lock renewal of " + delivery, delay, () -> {
            renewal[0] = null;
            if (closed[0] != 0 || dst.now() >= until) {
                return;
            }
            Msg m = locked(token);
            if (m == null) {
                ledger.record("processorRenewLockLost", "q", delivery);
                return;
            }
            m.lockedUntil = dst.now() + lockDurationMillis;
            ledger.record("processorRenewed", "q", delivery);
            renewLockWhileProcessed(lockRenewal, renewal, token, delivery, m.lockedUntil, until, closed);
        });
    }

    private ServiceBusReceivedMessageContext context(Msg m, String token, String delivery, int[] closed) {
        ServiceBusReceivedMessage message = mock(ServiceBusReceivedMessage.class);
        when(message.getBody()).thenReturn(BinaryData.fromString(m.id));
        when(message.getMessageId()).thenReturn(m.id);
        when(message.getLockToken()).thenReturn(token);
        when(message.getDeliveryCount()).thenReturn((long) m.deliveryCount);
        when(message.getLockedUntil())
                .thenReturn(OffsetDateTime.ofInstant(Instant.ofEpochMilli(Dst.EPOCH_MILLIS + m.lockedUntil), ZoneOffset.UTC));
        when(message.getApplicationProperties()).thenReturn(Map.of());
        ServiceBusReceivedMessageContext context = mock(ServiceBusReceivedMessageContext.class);
        when(context.getMessage()).thenReturn(message);
        doAnswer(i -> {
            settle(token, delivery, State.COMPLETED, ServiceBusErrorSource.COMPLETE, closed[0]);
            return null;
        }).when(context).complete();
        doAnswer(i -> {
            settle(token, delivery, State.AVAILABLE, ServiceBusErrorSource.ABANDON, closed[0]);
            return null;
        }).when(context).abandon();
        doAnswer(i -> {
            settle(token, delivery, State.DEAD_LETTERED, ServiceBusErrorSource.UNKNOWN, closed[0]);
            return null;
        }).when(context).deadLetter();
        doAnswer(i -> {
            settle(token, delivery, State.DEAD_LETTERED, ServiceBusErrorSource.UNKNOWN, closed[0]);
            return null;
        }).when(context).deadLetter(any());
        return context;
    }

    /** [S] a settlement with the lock token of a delivery. */
    private void settle(String token, String delivery, State to, ServiceBusErrorSource source, int closed) {
        onSettle.accept(delivery + " " + to);
        // the SDK checks a disposed receiver first, and marks a message settled only once a disposition succeeded
        if (closed != 0) {
            ledger.record("settleAfterClose", "q", delivery + " " + to);
            onSettled.accept(delivery + (closed == 1 ? " closedByStop" : " closed"));
            throw new IllegalStateException("Cannot perform operation on a disposed receiver.");
        }
        if (settledDeliveries.contains(delivery)) {
            violations.add(delivery + " settled again (" + to + ")");
            throw new IllegalArgumentException("The message has either been deleted or already settled.");
        }
        if (faults && dst.chance(settleFailRate)) {
            ledger.record("settleFault", "q", delivery);
            onSettled.accept(delivery + " fault");
            throw Dst.injected(transientFailure(source));
        }
        Msg m = locked(token);
        if (m == null) {
            ledger.record("lockLost", "q", delivery + " " + to);
            onSettled.accept(delivery + " lockLost");
            throw lockLost(token, source);
        }
        ledger.record(to.name().toLowerCase(), "q", delivery);
        settledDeliveries.add(delivery);
        onSettled.accept(delivery + " " + (to == State.AVAILABLE ? "ABANDONED" : to.name()));
        if (to == State.AVAILABLE) {
            available(m, "abandoned");
        } else {
            m.state = to;
            m.lockToken = null;
        }
    }

    /** ServiceBusClientFactory.createServiceBusReceiverAsyncClient: [R] the lock renewals of the consumer. */
    ServiceBusReceiverAsyncClient newRenewalClient() {
        ServiceBusReceiverAsyncClient client = mock(ServiceBusReceiverAsyncClient.class);
        boolean[] closed = new boolean[1];
        doAnswer(i -> {
            closed[0] = true;
            return null;
        }).when(client).close();
        when(client.renewMessageLock(any(ServiceBusReceivedMessage.class))).thenAnswer(i -> {
            ServiceBusReceivedMessage message = i.getArgument(0);
            String token = message.getLockToken();
            String delivery = message.getMessageId() + "#" + message.getDeliveryCount();
            if (closed[0]) {
                violations.add("a renewal of " + delivery + " through a closed receiver client");
                return Mono.error(new IllegalStateException("Cannot perform operation on a disposed receiver."));
            }
            if (settledDeliveries.contains(delivery)) {
                violations.add("a renewal of " + delivery + " after it was settled");
            }
            if (faults && dst.chance(renewFailRate)) {
                ledger.record("renewFault", "q", delivery);
                onRenewFault.accept(delivery);
                return Mono.error(Dst.injected(transientFailure(ServiceBusErrorSource.RENEW_LOCK)));
            }
            // the server handles the renewal after its latency: a lock that expires before that is lost
            long latency = faults && renewLatencyMaxMillis > 0 ? dst.between(1, renewLatencyMaxMillis) : 1;
            Msg m = locked(token);
            if (m == null || dst.now() + latency >= m.lockedUntil) {
                ledger.record("renewLockLost", "q", delivery);
                return Mono.error(lockLost(token, ServiceBusErrorSource.RENEW_LOCK));
            }
            m.lockedUntil = dst.now() + latency + lockDurationMillis;
            ledger.record("renewed", "q", delivery);
            return Mono.just(
                    OffsetDateTime.ofInstant(Instant.ofEpochMilli(Dst.EPOCH_MILLIS + m.lockedUntil), ZoneOffset.UTC));
        });
        return client;
    }
}
