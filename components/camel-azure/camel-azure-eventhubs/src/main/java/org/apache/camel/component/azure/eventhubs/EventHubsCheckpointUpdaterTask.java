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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.azure.messaging.eventhubs.models.EventContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A task for updating Azure Event Hubs checkpoints using ScheduledExecutorService.
 */
public class EventHubsCheckpointUpdaterTask implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(EventHubsCheckpointUpdaterTask.class);

    private volatile EventContext eventContext;
    private final AtomicInteger processedEvents;
    private volatile long scheduledTime;
    // the lock of the consumer, which counts the events under it
    private final Lock lock;
    // set under the lock when the task has taken the count of its window, or was cancelled
    private volatile boolean done;

    public EventHubsCheckpointUpdaterTask(EventContext eventContext, AtomicInteger processedEvents) {
        this(eventContext, processedEvents, new ReentrantLock());
    }

    public EventHubsCheckpointUpdaterTask(EventContext eventContext, AtomicInteger processedEvents, Lock lock) {
        this.eventContext = eventContext;
        this.processedEvents = processedEvents;
        this.lock = lock;
    }

    @Override
    public void run() {
        // take the count when the checkpoint is started: an event that completes while it is being stored is counted
        // for the next window from 0, and a checkpoint by batch size in the meantime cannot be confused with this one.
        // This is done under the lock of the consumer, so an event is either counted (and its context set) before, and
        // checkpointed here, or after, and then it sees that this task is done and starts a new window.
        final int processed;
        final EventContext context;
        lock.lock();
        try {
            if (done) {
                LOG.debug("skip checkpointing offset: the window was already checkpointed by batch size");
                return;
            }
            done = true;
            processed = processedEvents.getAndSet(0);
            context = eventContext;
        } finally {
            lock.unlock();
        }
        if (processed > 0) {
            LOG.debug("checkpointing offset after reaching timeout, with a batch of {}", processed);
            context.updateCheckpointAsync()
                    .subscribe(unused -> LOG.debug("Processed one event..."),
                            error -> {
                                LOG.warn("Error when updating Checkpoint: {}", error.getMessage(), error);
                                // these events are not checkpointed: count them again for the next checkpoint
                                processedEvents.addAndGet(processed);
                            },
                            () -> LOG.debug("Checkpoint updated."));
        } else {
            LOG.debug("skip checkpointing offset even if timeout is reached. No events processed");
        }
    }

    public void setEventContext(EventContext eventContext) {
        this.eventContext = eventContext;
    }

    public void setScheduledTime(long scheduledTime) {
        this.scheduledTime = scheduledTime;
    }

    public long getScheduledTime() {
        return scheduledTime;
    }

    /**
     * Whether this task has taken the count of its window (or was cancelled), so a new event must start a new window.
     */
    public boolean isDone() {
        return done;
    }

    /**
     * Cancels the task under the lock of the consumer: if it is already running, it takes nothing.
     */
    public void cancel() {
        lock.lock();
        try {
            done = true;
        } finally {
            lock.unlock();
        }
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > scheduledTime;
    }
}
