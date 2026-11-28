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
package org.apache.camel.component.azure.eventhubs;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;

import com.azure.messaging.eventhubs.CheckpointStore;
import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventProcessorClient;
import com.azure.messaging.eventhubs.models.Checkpoint;
import com.azure.messaging.eventhubs.models.CloseContext;
import com.azure.messaging.eventhubs.models.CloseReason;
import com.azure.messaging.eventhubs.models.EventContext;
import com.azure.messaging.eventhubs.models.PartitionContext;
import com.azure.messaging.eventhubs.models.PartitionOwnership;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.azure.eventhubs.client.EventHubsClientFactory;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.AsyncProcessorSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The checkpoint of a partition when the exchanges complete in another order than their events were delivered (any
 * asynchronous step in the route), and when checkpoint writes answer late. A real route and consumer, a mocked event
 * processor client, and a checkpoint store that behaves like the blob checkpoint store: the write that lands last wins,
 * whatever its sequence number. The checkpoint timer is run by the test (the batch timeout is long), so nothing depends
 * on timing.
 */
class EventHubsConsumerCheckpointOrderTest {

    private static final String PARTITION = "0";

    /** The stored checkpoint (sequence number) and every write in the order it landed. */
    private volatile long stored = -1;
    private final List<Long> landed = new CopyOnWriteArrayList<>();
    /** The writes of these sequence numbers are held until the test releases them. */
    private final Set<Long> hold = ConcurrentHashMap.newKeySet();
    private final Map<Long, Runnable> held = new ConcurrentHashMap<>();

    private final Map<Long, AsyncCallback> callbacks = new ConcurrentHashMap<>();
    private final Map<Long, Exchange> exchanges = new ConcurrentHashMap<>();
    private DefaultCamelContext context;
    private MockedStatic<EventHubsClientFactory> factory;
    private volatile Consumer<EventContext> processEvent;
    private volatile Consumer<CloseContext> processPartitionClose;

    private void start(int batchSize, boolean async, long failingSequenceNumber) throws Exception {
        EventProcessorClient processorClient = Mockito.mock(EventProcessorClient.class);
        factory = Mockito.mockStatic(EventHubsClientFactory.class, invocation -> {
            if ("createEventProcessorClient".equals(invocation.getMethod().getName())) {
                processEvent = invocation.getArgument(1);
                if (invocation.getArguments().length > 3) {
                    processPartitionClose = invocation.getArgument(3);
                }
                return processorClient;
            }
            return null;
        });
        context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                var route = from("azure-eventhubs:ns/hub?sharedAccessName=x&sharedAccessKey=y&checkpointBatchSize="
                                 + batchSize + "&checkpointBatchTimeout=600000")
                        .routeId("eh");
                if (async) {
                    // a non-blocking downstream call: the test completes each exchange when it wants
                    route.process(new AsyncProcessorSupport() {
                        @Override
                        public boolean process(Exchange exchange, AsyncCallback callback) {
                            long seq = exchange.getMessage().getHeader(EventHubsConstants.SEQUENCE_NUMBER, Long.class);
                            exchanges.put(seq, exchange);
                            callbacks.put(seq, callback);
                            return false;
                        }
                    });
                } else {
                    route.process(e -> {
                        if (e.getMessage().getHeader(EventHubsConstants.SEQUENCE_NUMBER, Long.class)
                            == failingSequenceNumber) {
                            throw new IllegalStateException("downstream failed for e" + failingSequenceNumber);
                        }
                    });
                }
            }
        });
        context.start();
    }

    @AfterEach
    void tearDown() {
        // a test that failed may leave exchanges in flight: complete them, so the stop does not wait for them
        callbacks.values().forEach(callback -> callback.done(false));
        if (context != null) {
            context.stop();
        }
        if (factory != null) {
            factory.close();
        }
    }

    @Test
    void checkpointDoesNotPassAnEventStillInFlight() throws Exception {
        start(2, true, -1);
        deliver(1);
        deliver(2);
        deliver(3);
        complete(1);
        complete(3);

        // e2 is in flight: a checkpoint at e3 would make the next owner of the partition resume at e4, and e2 would be
        // lost if this consumer stopped now
        assertTrue(callbacks.containsKey(2L), "e2 is still in flight");
        assertEquals(1, stored, "the checkpoint stops before e2");

        complete(2);
        assertEquals(3, stored);
        assertEquals(List.of(1L, 3L), landed);
    }

    @Test
    void storedCheckpointDoesNotGoBackWithReorderedCompletions() throws Exception {
        start(2, true, -1);
        deliver(1);
        deliver(2);
        deliver(3);
        complete(1);
        complete(3);
        complete(2);
        runWindowTimer();

        assertEquals(List.of(1L, 3L), landed, "the checkpoint only moves forward");
        assertEquals(3, stored);
    }

    @Test
    void storedCheckpointDoesNotGoBackWhenATimerWriteLandsLate() throws Exception {
        start(2, false, -1);
        hold.add(1L);
        deliver(1);
        // the timer checkpoints e1, and the store answers it late
        runWindowTimer();
        assertTrue(held.containsKey(1L));

        // meanwhile e2 and e3 reach the batch size
        deliver(2);
        deliver(3);
        held.remove(1L).run();

        assertEquals(List.of(1L, 3L), landed, "the checkpoint at e3 is written after the late write of e1 answered");
        assertEquals(3, stored);
    }

    @Test
    void failedEventIsPassedByTheNextCheckpoint() throws Exception {
        // unchanged: a failed exchange is not delivered again, the next checkpoint passes it
        start(2, false, 1);
        deliver(1);
        deliver(2);
        deliver(3);

        assertEquals(3, stored);
        assertEquals(List.of(3L), landed);
    }

    @Test
    void failedEventDoesNotHoldBackTheEventsCompletedAfterIt() throws Exception {
        start(2, true, -1);
        deliver(1);
        deliver(2);
        deliver(3);
        complete(1);
        complete(3);
        fail(2);
        runWindowTimer();

        assertEquals(3, stored);
    }

    @Test
    void exchangesInFlightWhenThePartitionIsLostDoNotCheckpoint() throws Exception {
        start(2, true, -1);
        deliver(1);
        deliver(2);
        complete(1);
        processPartitionClose.accept(new CloseContext(partitionContext(), CloseReason.LOST_PARTITION_OWNERSHIP));
        // the new owner resumes after the stored checkpoint, and delivers e1 and e2 again
        complete(2);
        runWindowTimer();

        assertEquals(List.of(), landed);
    }

    @Test
    void exchangesInFlightWhenTheProcessorStopsStillCheckpoint() throws Exception {
        start(1, true, -1);
        deliver(1);
        processPartitionClose.accept(new CloseContext(partitionContext(), CloseReason.EVENT_PROCESSOR_SHUTDOWN));
        complete(1);

        assertEquals(1, stored);
    }

    @Test
    void exchangesOfEarlierDeliveriesDoNotCheckpointWhenThePartitionIsDeliveredAgain() throws Exception {
        start(1, true, -1);
        deliver(1);
        deliver(2);
        AsyncCallback earlierE1 = callbacks.remove(1L);
        // the partition is claimed again without being reported as lost: the SDK delivers it again from the stored
        // checkpoint (none yet), while the exchanges of the earlier deliveries are still in flight
        deliver(1);
        complete(2);
        assertEquals(List.of(), landed, "the earlier e2 must not pass the e1 delivered again");

        complete(1);
        earlierE1.done(false);
        assertEquals(List.of(1L), landed);
    }

    @Test
    void eventsCompletedAfterAnEventInFlightAreKeptAsOneEntry() throws Exception {
        start(2, true, -1);
        for (long seq = 1; seq <= 50; seq++) {
            deliver(seq);
        }
        for (long seq = 2; seq <= 50; seq += 2) {
            complete(seq);
        }
        fail(25);
        for (long seq = 3; seq <= 49; seq += 2) {
            if (seq != 25) {
                complete(seq);
            }
        }

        // e1 is in flight (an exchange that never completes would hold it so for ever): the 49 events completed after
        // it are one entry, which keeps the context of e50 only, not one entry (and event) each
        assertEquals(List.of(), landed);
        assertEquals(2, deliveredEntries(), "e1 in flight, and e2 to e50 completed");

        complete(1);
        assertEquals(List.of(50L), landed);
        assertEquals(0, deliveredEntries());
    }

    /** The SDK partition pump delivers event e{seq} (one at a time, as the event processor client does). */
    private void deliver(long seq) {
        EventData data = Mockito.spy(new EventData("e" + seq));
        Mockito.doReturn(seq).when(data).getSequenceNumber();
        Mockito.doReturn(seq * 100).when(data).getOffset();
        processEvent.accept(new EventContext(partitionContext(), data, new Store(seq), null));
    }

    private static PartitionContext partitionContext() {
        return new PartitionContext("ns.servicebus.windows.net", "hub", "$Default", PARTITION);
    }

    private void complete(long seq) {
        callbacks.remove(seq).done(false);
    }

    private void fail(long seq) {
        exchanges.get(seq).setException(new IllegalStateException("downstream failed for e" + seq));
        callbacks.remove(seq).done(false);
    }

    /**
     * Runs the timer of the current checkpoint window of the partition now, if there is one, instead of the scheduler
     * (which would run it after the batch timeout).
     */
    private void runWindowTimer() throws Exception {
        Object consumer = context.getRoute("eh").getConsumer();
        ScheduledFuture<?> scheduled = this.<ScheduledFuture<?>> consumerMap(consumer, "scheduledTasksByPartition")
                .get(PARTITION);
        if (scheduled != null) {
            scheduled.cancel(false);
        }
        EventHubsCheckpointUpdaterTask task
                = this.<EventHubsCheckpointUpdaterTask> consumerMap(consumer, "checkpointTasksByPartition").get(PARTITION);
        if (task != null) {
            task.run();
        }
    }

    private int deliveredEntries() throws Exception {
        Object checkpoints = consumerMap(context.getRoute("eh").getConsumer(), "checkpointsByPartition").get(PARTITION);
        Field field = EventHubsPartitionCheckpoints.class.getDeclaredField("delivered");
        field.setAccessible(true);
        return ((Map<?, ?>) field.get(checkpoints)).size();
    }

    @SuppressWarnings("unchecked")
    private <T> Map<String, T> consumerMap(Object consumer, String name) throws Exception {
        Field field = EventHubsConsumer.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<String, T>) field.get(consumer);
    }

    /** The checkpoint store as seen by the event e{seq}: a write stores that event's sequence number. */
    private final class Store implements CheckpointStore {
        private final long seq;

        Store(long seq) {
            this.seq = seq;
        }

        @Override
        public Mono<Void> updateCheckpoint(Checkpoint checkpoint) {
            return Mono.create(sink -> {
                Runnable landing = () -> {
                    stored = seq;
                    landed.add(seq);
                    sink.success();
                };
                if (hold.remove(seq)) {
                    held.put(seq, landing);
                } else {
                    landing.run();
                }
            });
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
            return Flux.empty();
        }
    }
}
