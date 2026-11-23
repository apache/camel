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

import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.locks.Lock;

import com.azure.messaging.eventhubs.models.EventContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * The checkpoint state of one partition owned by an {@link EventHubsConsumer}: the events delivered to the route and
 * not yet passed by the checkpoint, and the checkpoint writes of the partition.
 * <p>
 * The exchanges of a partition can complete in any order (any asynchronous step in the route), while the checkpoint
 * store keeps one position per partition and the next owner of the partition resumes after it. So a checkpoint is
 * written only for an event below which every delivered event has completed (successfully or not), only when it is
 * after the last checkpoint written, and only when the write before it has answered: the stored checkpoint never passes
 * an event still in flight and never goes back.
 * <p>
 * Used under the lock of the consumer only (the answers of the writes take it).
 */
final class EventHubsPartitionCheckpoints {

    private static final Logger LOG = LoggerFactory.getLogger(EventHubsPartitionCheckpoints.class);

    private final Lock lock;
    // the delivered events by sequence number, until every event before them has completed; the events completed one
    // after the other are one entry, under the last of them, so an event that never completes holds one entry per run
    // of events completed after it, not one per event
    private final NavigableMap<Long, Delivered> delivered = new TreeMap<>();
    private long lastDelivered = -1;
    // the events in delivered that completed successfully: they wait for an earlier event still in flight
    private int succeededAhead;
    // the last event below which every delivered event has completed: the furthest a checkpoint may go
    private EventContext completedUpTo;
    // the sequence number of the last checkpoint written (or waiting to be written), and of the last one stored
    private long written = -1;
    private long stored = -1;
    // a write is in flight; the next checkpoint waits for its answer
    private boolean writing;
    private EventContext waiting;
    private Runnable waitingOnError;
    // the partition was lost: its checkpoints belong to its new owner
    private boolean closed;

    EventHubsPartitionCheckpoints(Lock lock) {
        this.lock = lock;
    }

    /**
     * The event is delivered to the route. The SDK delivers the events of a partition one at a time, in order.
     *
     * @return false if the event is not after the last one delivered: the SDK delivers the partition again from the
     *         stored checkpoint, so this state no longer applies
     */
    boolean delivered(EventContext eventContext) {
        final long sequenceNumber = sequenceNumber(eventContext);
        if (sequenceNumber <= lastDelivered) {
            return false;
        }
        lastDelivered = sequenceNumber;
        delivered.put(sequenceNumber, new Delivered(eventContext));
        return true;
    }

    /**
     * The exchange of the event is done, successfully or not.
     *
     * @return false if the event was not delivered by this owner of the partition (it was delivered before the consumer
     *         was stopped or lost the partition), so it must not move the checkpoint
     */
    boolean completed(EventContext eventContext, boolean succeeded) {
        final long sequenceNumber = sequenceNumber(eventContext);
        final Delivered event = delivered.get(sequenceNumber);
        if (event == null || event.eventContext != eventContext || event.completed) {
            return false;
        }
        event.completed = true;
        if (succeeded) {
            event.succeeded = 1;
            succeededAhead++;
        }
        // join the completed events next to it: a checkpoint can only use the last event of such a run
        final Map.Entry<Long, Delivered> before = delivered.lowerEntry(sequenceNumber);
        if (before != null && before.getValue().completed) {
            event.succeeded += before.getValue().succeeded;
            delivered.remove(before.getKey());
        }
        final Map.Entry<Long, Delivered> after = delivered.higherEntry(sequenceNumber);
        if (after != null && after.getValue().completed) {
            after.getValue().succeeded += event.succeeded;
            delivered.remove(sequenceNumber);
        }
        Map.Entry<Long, Delivered> first;
        while ((first = delivered.firstEntry()) != null && first.getValue().completed) {
            delivered.pollFirstEntry();
            completedUpTo = first.getValue().eventContext;
            succeededAhead -= first.getValue().succeeded;
        }
        return true;
    }

    /**
     * The number of events that completed successfully after an event still in flight: a checkpoint now does not cover
     * them.
     */
    int succeededAhead() {
        return succeededAhead;
    }

    /**
     * Whether a checkpoint now would move the checkpoint of the partition forward.
     */
    boolean canCheckpoint() {
        return !closed && completedUpTo != null && sequenceNumber(completedUpTo) > written;
    }

    /**
     * Checkpoints the partition up to the last event below which every delivered event has completed, if that is after
     * the last checkpoint written. When a write is in flight, the checkpoint is written when it answers (a later
     * checkpoint replaces a waiting one), so the writes land in order.
     *
     * @param  onError called if the write of this checkpoint fails
     * @return         false if there is nothing to checkpoint
     */
    boolean checkpoint(Runnable onError) {
        if (!canCheckpoint()) {
            return false;
        }
        written = sequenceNumber(completedUpTo);
        if (writing) {
            waiting = completedUpTo;
            waitingOnError = onError;
        } else {
            write(completedUpTo, onError);
        }
        return true;
    }

    /**
     * The partition was lost, or is delivered again: the checkpoints waiting to be written are dropped, and nothing
     * more is written.
     */
    void close() {
        closed = true;
        waiting = null;
        waitingOnError = null;
        delivered.clear();
    }

    private void write(EventContext eventContext, Runnable onError) {
        final long sequenceNumber = sequenceNumber(eventContext);
        final Mono<Void> update;
        try {
            update = eventContext.updateCheckpointAsync();
        } catch (RuntimeException e) {
            written = stored;
            throw e;
        }
        writing = true;
        update.subscribe(unused -> LOG.debug("Processed one event..."),
                error -> {
                    LOG.warn("Error when updating Checkpoint: {}", error.getMessage(), error);
                    answered(sequenceNumber, false);
                    if (onError != null) {
                        onError.run();
                    }
                },
                () -> {
                    LOG.debug("Checkpoint updated.");
                    answered(sequenceNumber, true);
                });
    }

    private void answered(long sequenceNumber, boolean ok) {
        lock.lock();
        try {
            writing = false;
            if (ok) {
                stored = sequenceNumber;
            } else if (waiting == null) {
                // a later checkpoint can write the same position again
                written = stored;
            }
            if (waiting != null) {
                EventContext next = waiting;
                Runnable nextOnError = waitingOnError;
                waiting = null;
                waitingOnError = null;
                write(next, nextOnError);
            }
        } finally {
            lock.unlock();
        }
    }

    private static long sequenceNumber(EventContext eventContext) {
        return eventContext.getEventData().getSequenceNumber();
    }

    private static final class Delivered {
        private final EventContext eventContext;
        private boolean completed;
        // the events of this entry that completed successfully
        private int succeeded;

        private Delivered(EventContext eventContext) {
            this.eventContext = eventContext;
        }
    }
}
