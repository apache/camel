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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import com.azure.messaging.eventhubs.EventProcessorClient;
import com.azure.messaging.eventhubs.models.CloseContext;
import com.azure.messaging.eventhubs.models.CloseReason;
import com.azure.messaging.eventhubs.models.ErrorContext;
import com.azure.messaging.eventhubs.models.EventContext;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.component.azure.eventhubs.client.EventHubsClientFactory;
import org.apache.camel.spi.ShutdownAware;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.azure.eventhubs.EventHubsConstants.COMPLETED_BY_SIZE;
import static org.apache.camel.component.azure.eventhubs.EventHubsConstants.COMPLETED_BY_TIMEOUT;

public class EventHubsConsumer extends DefaultConsumer implements ShutdownAware {

    private static final Logger LOG = LoggerFactory.getLogger(EventHubsConsumer.class);

    // we use the EventProcessorClient as recommended by Azure docs to consume from all partitions
    private EventProcessorClient processorClient;

    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicInteger pendingExchanges = new AtomicInteger();
    private final Map<String, AtomicInteger> processedEventsByPartition = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> scheduledTasksByPartition = new ConcurrentHashMap<>();
    private final Map<String, EventHubsCheckpointUpdaterTask> checkpointTasksByPartition = new ConcurrentHashMap<>();
    private final Map<String, EventHubsPartitionCheckpoints> checkpointsByPartition = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduledExecutorService;

    public EventHubsConsumer(final EventHubsEndpoint endpoint, final Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // create scheduled executor for checkpoint updates
        scheduledExecutorService = getEndpoint().getCamelContext().getExecutorServiceManager()
                .newScheduledThreadPool(this, "EventHubsCheckpoint", 1);

        // create the client
        processorClient = EventHubsClientFactory.createEventProcessorClient(getConfiguration(),
                this::onEventListener, this::onErrorListener, this::onPartitionClose);

        // start the client but we will rely on the Azure Client Scheduler for thread management
        processorClient.start();
    }

    @Override
    protected void doStop() throws Exception {
        if (processorClient != null) {
            // stop accepting new messages but keep the connection open
            // so that in-flight exchanges can still complete
            processorClient.stop();
        }

        processedEventsByPartition.clear();
        scheduledTasksByPartition.clear();
        checkpointTasksByPartition.clear();
        // the exchanges still in flight no longer move the checkpoint: their events are delivered again after a start
        checkpointsByPartition.clear();

        // shutdown camel consumer
        super.doStop();
    }

    @Override
    protected void doShutdown() throws Exception {
        processorClient = null;

        // shutdown scheduled executor after all in-flight exchanges have completed
        if (scheduledExecutorService != null) {
            getEndpoint().getCamelContext().getExecutorServiceManager().shutdownGraceful(scheduledExecutorService);
            scheduledExecutorService = null;
        }

        super.doShutdown();
    }

    @Override
    public boolean deferShutdown(ShutdownRunningTask shutdownRunningTask) {
        if (processorClient != null) {
            processorClient.stop();
        }
        return true;
    }

    @Override
    public int getPendingExchangesSize() {
        return pendingExchanges.get();
    }

    @Override
    public void prepareShutdown(boolean suspendOnly, boolean forced) {
        // noop
    }

    public EventHubsConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public EventHubsEndpoint getEndpoint() {
        return (EventHubsEndpoint) super.getEndpoint();
    }

    Exchange createAzureEventHubExchange(final EventContext eventContext) {
        final Exchange exchange = createExchange(true);
        final Message message = exchange.getIn();

        // set body as byte[] and let camel typeConverters do the job to convert
        message.setBody(eventContext.getEventData().getBody());
        // set headers, and record the partition of the received event so an azure-eventhubs producer later in the
        // route does not take these headers as a partition chosen by the route
        final String partitionId = eventContext.getPartitionContext().getPartitionId();
        final String partitionKey = eventContext.getEventData().getPartitionKey();
        message.setHeader(EventHubsConstants.PARTITION_ID, partitionId);
        exchange.setProperty(EventHubsConstants.RECEIVED_PARTITION_ID, partitionId);
        if (partitionKey != null) {
            message.setHeader(EventHubsConstants.PARTITION_KEY, partitionKey);
            exchange.setProperty(EventHubsConstants.RECEIVED_PARTITION_KEY, partitionKey);
        }
        message.setHeader(EventHubsConstants.OFFSET, eventContext.getEventData().getOffset());
        message.setHeader(EventHubsConstants.ENQUEUED_TIME, eventContext.getEventData().getEnqueuedTime());
        message.setHeader(EventHubsConstants.SEQUENCE_NUMBER, eventContext.getEventData().getSequenceNumber());
        if (eventContext.getEventData().getEnqueuedTime() != null) {
            long ts = eventContext.getEventData().getEnqueuedTime().getEpochSecond() * 1000;
            message.setHeader(EventHubsConstants.MESSAGE_TIMESTAMP, ts);
        }
        message.setHeader(EventHubsConstants.METADATA, eventContext.getEventData().getProperties());

        return exchange;
    }

    private Exchange createAzureEventHubExchange(final ErrorContext errorContext) {
        final Exchange exchange = createExchange(true);
        final Message message = exchange.getIn();

        // set headers
        final String partitionId = errorContext.getPartitionContext().getPartitionId();
        message.setHeader(EventHubsConstants.PARTITION_ID, partitionId);
        exchange.setProperty(EventHubsConstants.RECEIVED_PARTITION_ID, partitionId);

        // set exception
        exchange.setException(errorContext.getThrowable());

        return exchange;
    }

    private void onEventListener(final EventContext eventContext) {
        pendingExchanges.incrementAndGet();

        final Exchange exchange = createAzureEventHubExchange(eventContext);
        processDelivered(eventContext);

        // add exchange callback
        exchange.getExchangeExtension().addOnCompletion(new Synchronization() {
            @Override
            public void onComplete(Exchange exchange) {
                try {
                    // we update the consumer offsets
                    processCommit(exchange, eventContext);
                } finally {
                    pendingExchanges.decrementAndGet();
                }
            }

            @Override
            public void onFailure(Exchange exchange) {
                try {
                    // the event is not delivered again, but it no longer holds back the checkpoint
                    processRollback(exchange, eventContext);
                } finally {
                    pendingExchanges.decrementAndGet();
                }
            }
        });
        // use default consumer callback
        AsyncCallback cb = defaultConsumerCallback(exchange, true);
        getAsyncProcessor().process(exchange, cb);
    }

    private void onPartitionClose(final CloseContext closeContext) {
        // when the processor client stops, the exchanges in flight still complete and checkpoint
        if (closeContext.getCloseReason() != CloseReason.LOST_PARTITION_OWNERSHIP) {
            return;
        }
        final String partitionId = closeContext.getPartitionContext().getPartitionId();
        lock.lock();
        try {
            // the new owner resumes after the stored checkpoint: the exchanges in flight here must not move it any
            // more, as their events are delivered again there
            if (dropPartition(partitionId)) {
                LOG.info("Partition {} was lost: the exchanges of its events still in flight are not checkpointed",
                        partitionId);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Drops the checkpoint state of the partition, and cancels the timer of its window. Under the lock.
     *
     * @return whether the partition had a checkpoint state
     */
    private boolean dropPartition(String partitionId) {
        final EventHubsPartitionCheckpoints checkpoints = checkpointsByPartition.remove(partitionId);
        if (checkpoints != null) {
            checkpoints.close();
        }
        final EventHubsCheckpointUpdaterTask checkpointTask = checkpointTasksByPartition.remove(partitionId);
        if (checkpointTask != null) {
            checkpointTask.cancel();
        }
        final ScheduledFuture<?> scheduledTask = scheduledTasksByPartition.remove(partitionId);
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
        }
        processedEventsByPartition.remove(partitionId);
        return checkpoints != null;
    }

    private void onErrorListener(final ErrorContext errorContext) {
        final Exchange exchange = createAzureEventHubExchange(errorContext);

        // log exception if an exception occurred and was not handled
        if (exchange.getException() != null) {
            getExceptionHandler().handleException("Error processing exchange", exchange,
                    exchange.getException());
        }
    }

    /**
     * The event is delivered to the route: until its exchange is done, no checkpoint of its partition passes it.
     *
     * @param eventContext the event context of the event
     */
    void processDelivered(final EventContext eventContext) {
        lock.lock();
        try {
            final String partitionId = eventContext.getPartitionContext().getPartitionId();
            final EventHubsPartitionCheckpoints checkpoints = checkpointsByPartition.computeIfAbsent(partitionId,
                    ignored -> new EventHubsPartitionCheckpoints(lock));
            if (!checkpoints.delivered(eventContext)) {
                // the partition was claimed again (closed without being reported as lost) and the SDK delivers it again
                // from the stored checkpoint: the exchanges of the earlier deliveries no longer move the checkpoint
                LOG.info("Partition {} is delivered again from sequence number {}: the exchanges of its earlier deliveries"
                         + " still in flight are not checkpointed",
                        partitionId, eventContext.getEventData().getSequenceNumber());
                dropPartition(partitionId);
                final EventHubsPartitionCheckpoints again = new EventHubsPartitionCheckpoints(lock);
                again.delivered(eventContext);
                checkpointsByPartition.put(partitionId, again);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Strategy to commit the offset after message being processed successfully.
     *
     * @param exchange     the exchange
     * @param eventContext the event context of the event, used to update the checkpoint
     */
    void processCommit(final Exchange exchange, final EventContext eventContext) {
        lock.lock();
        try {
            final String partitionId = eventContext.getPartitionContext().getPartitionId();
            final EventHubsPartitionCheckpoints checkpoints = checkpointsByPartition.get(partitionId);
            if (checkpoints == null || !checkpoints.completed(eventContext, true)) {
                LOG.debug("event of partition {} was delivered before the consumer was stopped or lost the partition:"
                          + " it does not move the checkpoint",
                        partitionId);
                return;
            }
            final AtomicInteger processedEvents = processedEventsByPartition.computeIfAbsent(partitionId,
                    ignored -> new AtomicInteger());
            EventHubsCheckpointUpdaterTask checkpointTask = checkpointTasksByPartition.get(partitionId);
            ScheduledFuture<?> scheduledTask = scheduledTasksByPartition.get(partitionId);

            // a new window starts when the timer of the current one has run, not when the wall clock passed its time: the
            // timer can run in the millisecond of its scheduled time, or later, and the event must be counted in the
            // window whose timer has not taken the count yet
            if (checkpointTask == null || checkpointTask.isDone()) {
                checkpointTask = startWindow(partitionId, checkpoints, processedEvents);
                scheduledTask = scheduledTasksByPartition.get(partitionId);
            }

            try {
                var cnt = processedEvents.incrementAndGet();
                // >= and not ==: a failed timeout checkpoint adds its count back, so the count can pass the batch size;
                // and the checkpoint waits while every event completed since the last one is after an event in flight
                if (cnt >= getConfiguration().getCheckpointBatchSize()
                        && checkpoint(checkpoints, processedEvents, false)) {
                    exchange.getIn().setHeader(EventHubsConstants.CHECKPOINT_UPDATED_BY, COMPLETED_BY_SIZE);
                    LOG.debug("eventhub consumer batch size of reached for partition {}", partitionId);
                    // a timer that has already started takes nothing (it waits for the lock)
                    checkpointTask.cancel();
                    if (scheduledTask != null) {
                        scheduledTask.cancel(false);
                    }
                    // the next event starts a new timeout window: the cancelled task must not be reused until its
                    // scheduled time, else the events of the rest of that window are not checkpointed by the timeout
                    checkpointTasksByPartition.remove(partitionId);
                    scheduledTasksByPartition.remove(partitionId);
                } else if (checkpointTask.isExpired()) {
                    exchange.getIn().setHeader(EventHubsConstants.CHECKPOINT_UPDATED_BY, COMPLETED_BY_TIMEOUT);
                    LOG.debug("eventhub consumer batch timeout reached for partition {}", partitionId);
                } else {
                    LOG.debug("neither eventhub consumer batch size of {}/{} nor batch timeout reached yet for partition {}",
                            cnt, getConfiguration().getCheckpointBatchSize(), partitionId);
                }
                // we assume that the scheduled task has done the update by its side
            } catch (Exception ex) {
                getExceptionHandler().handleException(
                        "Error occurred during updating the checkpoint. This exception is ignored.",
                        exchange, ex);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Strategy when processing the exchange failed: the event is not delivered again, and is passed by the next
     * checkpoint (it is not counted, nor checkpointed by itself).
     *
     * @param exchange     the exchange
     * @param eventContext the event context of the event
     */
    void processRollback(final Exchange exchange, final EventContext eventContext) {
        final Exception cause = exchange.getException();
        if (cause != null) {
            getExceptionHandler().handleException("Error during processing exchange.", exchange, cause);
        }
        lock.lock();
        try {
            final String partitionId = eventContext.getPartitionContext().getPartitionId();
            final EventHubsPartitionCheckpoints checkpoints = checkpointsByPartition.get(partitionId);
            if (checkpoints == null || !checkpoints.completed(eventContext, false)) {
                return;
            }
            // events completed after this one waited for it: a window must checkpoint them
            final AtomicInteger processedEvents = processedEventsByPartition.get(partitionId);
            final EventHubsCheckpointUpdaterTask checkpointTask = checkpointTasksByPartition.get(partitionId);
            if (processedEvents != null && processedEvents.get() > 0
                    && (checkpointTask == null || checkpointTask.isDone())) {
                startWindow(partitionId, checkpoints, processedEvents);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Starts a checkpoint window of the partition, whose timer checkpoints it after the batch timeout. Under the lock.
     */
    private EventHubsCheckpointUpdaterTask startWindow(
            String partitionId, EventHubsPartitionCheckpoints checkpoints, AtomicInteger processedEvents) {
        final EventHubsCheckpointUpdaterTask checkpointTask = new EventHubsCheckpointUpdaterTask(
                lock, () -> checkpointByTimeout(checkpoints, processedEvents));
        // delegate the checkpoint update to a dedicated Thread
        final long timeout = getConfiguration().getCheckpointBatchTimeout();
        checkpointTask.setScheduledTime(System.currentTimeMillis() + timeout);
        final ScheduledFuture<?> scheduledTask
                = scheduledExecutorService.schedule(checkpointTask, timeout, TimeUnit.MILLISECONDS);
        checkpointTasksByPartition.put(partitionId, checkpointTask);
        scheduledTasksByPartition.put(partitionId, scheduledTask);
        return checkpointTask;
    }

    /**
     * The timer of a window: checkpoints the partition if events were processed since the last checkpoint. Under the
     * lock.
     */
    private void checkpointByTimeout(EventHubsPartitionCheckpoints checkpoints, AtomicInteger processedEvents) {
        if (processedEvents.get() == 0) {
            LOG.debug("skip checkpointing offset even if timeout is reached. No events processed");
        } else if (checkpoint(checkpoints, processedEvents, true)) {
            LOG.debug("checkpointing offset after reaching timeout");
        } else {
            LOG.debug("skip checkpointing offset after reaching timeout: the events processed since the last checkpoint"
                      + " are after an event still in flight");
        }
    }

    /**
     * Checkpoints the partition up to the last event below which every delivered event has completed, if that is after
     * the last checkpoint. The events completed after an event still in flight stay counted for the next checkpoint.
     * Under the lock.
     *
     * @return false if there is nothing to checkpoint
     */
    private boolean checkpoint(
            EventHubsPartitionCheckpoints checkpoints, AtomicInteger processedEvents, boolean byTimeout) {
        if (!checkpoints.canCheckpoint()) {
            return false;
        }
        final int ahead = checkpoints.succeededAhead();
        final int covered = Math.max(0, processedEvents.getAndSet(ahead) - ahead);
        // a failed checkpoint by timeout counts its events again for the next checkpoint
        return checkpoints.checkpoint(byTimeout ? () -> processedEvents.addAndGet(covered) : null);
    }
}
