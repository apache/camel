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
package org.apache.camel.test.dst.demo.eventhubs;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.azure.messaging.eventhubs.CheckpointStore;
import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventProcessorClient;
import com.azure.messaging.eventhubs.models.Checkpoint;
import com.azure.messaging.eventhubs.models.EventContext;
import com.azure.messaging.eventhubs.models.PartitionContext;
import com.azure.messaging.eventhubs.models.PartitionOwnership;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.azure.eventhubs.EventHubsConsumer;
import org.apache.camel.component.azure.eventhubs.client.EventHubsClientFactory;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.test.dst.Dst;
import org.apache.camel.test.dst.DstCamel;
import org.apache.camel.test.dst.DstExecutor;
import org.apache.camel.test.dst.DstLedger;
import org.apache.camel.test.dst.Invariant;
import org.apache.camel.test.dst.Invariants;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;

/**
 * The world around a camel-azure-eventhubs consumer: a fake Event Hubs SDK that delivers events per partition (bursts
 * and quiet periods chosen by the seed), an asynchronous downstream step with latency and failures, and a fake
 * checkpoint store whose answers are delayed, slow or failing. The consumer, the route and Camel's scheduler are real;
 * every thread pool runs on the DST loop.
 */
final class EventHubsWorld {

    static final String ROUTE = "eh";

    /** Probe counters over all seeds of the JVM (davsclaus' corner case on #27922 and the other causes). */
    static final Map<String, Integer> PROBES = new TreeMap<>();
    static final Map<String, Set<Long>> PROBE_SEEDS = new TreeMap<>();

    private final Dst dst;
    private final DstLedger ledger;
    private final Set<String> probes = new TreeSet<>();
    private final int batchSize;
    private final long timeout;
    private final int partitions;
    private final long maxStoreLatency;
    private Consumer<EventContext> processEvent;
    private EventHubsConsumer consumer;
    private int inflight;
    private int arrivalsLeft;
    private final Map<String, Integer> sizeCheckpoints = new TreeMap<>();
    private final DstExecutor store;

    private final boolean countProbes;

    EventHubsWorld(Dst dst) {
        this(dst, true);
    }

    EventHubsWorld(Dst dst, boolean countProbes) {
        this.dst = dst;
        this.countProbes = countProbes;
        this.ledger = new DstLedger(dst);
        this.batchSize = dst.pick(List.of(2, 3, 5, 10));
        this.timeout = dst.pick(List.of(200L, 500L, 1000L));
        this.partitions = dst.pick(List.of(1, 2));
        this.maxStoreLatency = 3 * timeout;
        this.store = dst.executor("checkpoint-store", Integer.MAX_VALUE);
        dst.trace("world: checkpointBatchSize=" + batchSize + " checkpointBatchTimeout=" + timeout + " partitions="
                  + partitions);
    }

    void run() throws Exception {
        DefaultCamelContext context = DstCamel.newContext(dst);
        DstExecutor downstream = dst.executor("downstream", Integer.MAX_VALUE);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("azure-eventhubs:dstns/dsthub?sharedAccessName=dst&sharedAccessKey=dst"
                     + "&checkpointBatchSize=" + batchSize + "&checkpointBatchTimeout=" + timeout)
                        .routeId(ROUTE)
                        .process(new AsyncProcessorSupport() {
                            @Override
                            public boolean process(Exchange exchange, AsyncCallback callback) {
                                // a non-blocking downstream call: answers after a latency chosen by the seed
                                String id = exchange.getMessage().getBody(String.class);
                                String partition = partitionOf(id);
                                long latency = dst.chance(0.1)
                                        ? dst.between(timeout / 2, 2 * timeout) : dst.between(0, timeout / 2);
                                downstream.schedule("downstream answer " + id, latency, () -> {
                                    if (dst.chance(0.05)) {
                                        exchange.setException(new IllegalStateException("downstream failed for " + id));
                                        ledger.record("failed", partition, id);
                                    } else {
                                        // completed: the consumer's onComplete (processCommit) runs within done()
                                        ledger.record("completed", partition, id);
                                    }
                                    callback.done(false);
                                    afterExchange(partition);
                                });
                                return false;
                            }
                        });
            }
        });
        try (MockedStatic<EventHubsClientFactory> factory = Mockito.mockStatic(EventHubsClientFactory.class)) {
            factory.when(() -> EventHubsClientFactory.createEventProcessorClient(any(), any(), any()))
                    .thenAnswer(inv -> {
                        processEvent = inv.getArgument(1);
                        return Mockito.mock(EventProcessorClient.class);
                    });
            context.start();
            consumer = (EventHubsConsumer) context.getRoute(ROUTE).getConsumer();
            try {
                planArrivals();
                dst.runUntil(() -> arrivalsLeft == 0 && inflight == 0, "all events delivered and completed");
                // the partitions are quiet now: one batch timeout plus the slowest checkpoint store answer later, every
                // completed event must have been checkpointed
                dst.trace("quiet");
                dst.runFor(timeout + maxStoreLatency + 100);
                dst.check(withProbes(Invariants.lastCompletedAcknowledgedWhenQuiet(ledger, "completed", "checkpoint")));
            } finally {
                dst.dropPending();
                context.stop();
            }
        }
    }

    private Invariant withProbes(Invariant invariant) {
        return Invariants.of(invariant.name(), () -> {
            String v = invariant.violation();
            return v == null ? null : v + " | probes " + probes;
        });
    }

    private static String partitionOf(String id) {
        return id.substring(1, id.indexOf('-'));
    }

    private void planArrivals() {
        for (int p = 0; p < partitions; p++) {
            String partition = String.valueOf(p);
            DstExecutor sdk = dst.executor("eventhubs-sdk-p" + p, 1);
            long t = dst.between(0, 50);
            int n = 0;
            int bursts = (int) dst.between(1, 4);
            for (int b = 0; b < bursts; b++) {
                int size = (int) dst.between(1, 2L * batchSize + 1);
                for (int i = 0; i < size; i++) {
                    String id = "p" + partition + "-e" + n++;
                    arrivalsLeft++;
                    sdk.schedule("deliver " + id, t, () -> deliver(partition, id));
                    t += dst.between(0, 20);
                }
                t += dst.between(0, 3 * timeout);
            }
        }
    }

    private void deliver(String partition, String id) {
        arrivalsLeft--;
        inflight++;
        PartitionContext pc = new PartitionContext("dstns.servicebus.windows.net", "dsthub", "$Default", partition);
        EventContext ctx = new EventContext(pc, new EventData(id), new FakeCheckpointStore(partition, id), null);
        ledger.record("delivered", partition, id);
        processEvent.accept(ctx);
    }

    /** Called by the downstream step's answer: the exchange is done (completed or failed). */
    private void afterExchange(String partition) {
        inflight--;
        probeTimerState(partition);
    }

    private void probeTimerState(String partition) {
        try {
            ScheduledFuture<?> timer = this.<Map<String, ScheduledFuture<?>>> field("scheduledTasksByPartition").get(partition);
            AtomicInteger count = this.<Map<String, AtomicInteger>> field("processedEventsByPartition").get(partition);
            if (timer != null && count != null && count.get() > 0) {
                if (timer.isCancelled()) {
                    probe("A: counted for a window whose timer was cancelled by a size checkpoint");
                } else if (timer.isDone()) {
                    probe("C: counted for a window whose timer already ran (not yet expired)");
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T field(String name) throws ReflectiveOperationException {
        Field f = EventHubsConsumer.class.getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(consumer);
    }

    private void probe(String what) {
        dst.trace("probe " + what);
        if (probes.add(what) && countProbes) {
            synchronized (PROBES) {
                PROBES.merge(what, 1, Integer::sum);
                PROBE_SEEDS.computeIfAbsent(what, k -> new TreeSet<>()).add(dst.seed());
            }
        }
    }

    /** A checkpoint store fake per event, so the trace says which event's offset is checkpointed. */
    private final class FakeCheckpointStore implements CheckpointStore {
        private final String partition;
        private final String id;

        FakeCheckpointStore(String partition, String id) {
            this.partition = partition;
            this.id = id;
        }

        @Override
        public Mono<Void> updateCheckpoint(Checkpoint checkpoint) {
            boolean byTimer = dst.currentExecutor().contains("EventHubsCheckpoint");
            ledger.record("checkpoint", partition, id + (byTimer ? " (timer)" : " (size)"));
            if (!byTimer) {
                sizeCheckpoints.merge(partition, 1, Integer::sum);
            }
            final int sizeAtIssue = sizeCheckpoints.getOrDefault(partition, 0);
            final int countAtIssue = count();
            return Mono.create(sink -> {
                boolean slow = dst.chance(0.15);
                long latency = slow ? dst.between(timeout / 2, maxStoreLatency) : dst.between(1, 50);
                boolean fail = dst.chance(0.05);
                store.schedule(
                        "checkpoint store answer " + id + (fail ? " (error)" : ""), latency, () -> {
                            int before = count();
                            if (fail) {
                                sink.error(new IllegalStateException("checkpoint store unavailable"));
                            } else {
                                sink.success();
                            }
                            int after = count();
                            if (byTimer && before > 0 && after == 0) {
                                boolean sizeSince = sizeCheckpoints.getOrDefault(partition, 0) > sizeAtIssue;
                                if (before != countAtIssue) {
                                    probe("B: timer checkpoint answer reset the count of events completed meanwhile");
                                } else if (sizeSince) {
                                    probe("D: timer checkpoint answer (compareAndSet) wiped a later window"
                                          + " (count climbed back to the checkpointed value)");
                                }
                            }
                        });
            });
        }

        private int count() {
            try {
                AtomicInteger c = EventHubsWorld.this.<Map<String, AtomicInteger>> field("processedEventsByPartition")
                        .get(partition);
                return c == null ? 0 : c.get();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public Flux<PartitionOwnership> listOwnership(String ns, String hub, String group) {
            return Flux.empty();
        }

        @Override
        public Flux<PartitionOwnership> claimOwnership(List<PartitionOwnership> requested) {
            return Flux.empty();
        }

        @Override
        public Flux<Checkpoint> listCheckpoints(String ns, String hub, String group) {
            return Flux.fromIterable(new ArrayList<>());
        }
    }
}
