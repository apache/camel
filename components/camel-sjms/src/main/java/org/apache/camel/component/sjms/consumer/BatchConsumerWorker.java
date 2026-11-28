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
package org.apache.camel.component.sjms.consumer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.Session;

import org.apache.camel.component.sjms.SjmsEndpoint;
import org.apache.camel.component.sjms.jms.SessionAcknowledgementType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.sjms.SjmsHelper.commitIfNeeded;
import static org.apache.camel.component.sjms.SjmsHelper.rollbackIfNeeded;

public class BatchConsumerWorker implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(BatchConsumerWorker.class);

    private static final long JMS_CONSUMER_RECEIVE_WAKE_INTERVAL_TIMEOUT = 1000L;
    private static final long JMS_CONSUMER_RECEIVE_MIN_TIMEOUT = 100L;

    private final SjmsEndpoint endpoint;
    private final BatchEndpointMessageListener batchListener;
    private final MessageConsumer consumer;
    private final Session session;
    private final BooleanSupplier suspended;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private volatile boolean jmsUnhealthy;
    // the messages that this worker still routes before it ends: the batch being routed, and the buffered messages
    // of an AUTO_ACKNOWLEDGE session (acknowledged on receipt, so they are routed when suspended or stopped). Written
    // by the worker only
    private volatile int pendingMessages;

    BatchConsumerWorker(SjmsEndpoint endpoint, BatchEndpointMessageListener batchListener,
                        Session session, MessageConsumer consumer, BooleanSupplier suspended) {
        this.endpoint = endpoint;
        this.batchListener = batchListener;
        this.session = session;
        this.consumer = consumer;
        this.suspended = suspended;
    }

    /**
     * The number of messages that this worker still routes before it ends, for the graceful shutdown to wait for: the
     * batch being routed, and the buffered messages of an AUTO_ACKNOWLEDGE session. The buffered messages of a
     * transacted or CLIENT_ACKNOWLEDGE session are not counted, as they are redelivered when the consumer stops.
     */
    int getPendingMessages() {
        return pendingMessages;
    }

    void shutdown() {
        LOG.debug("Shutdown requested");
        running.set(false);
    }

    boolean isShutdownRequested() {
        return !running.get();
    }

    void invalidate() {
        jmsUnhealthy = true;
        shutdown();
    }

    private boolean isRedeliverable() {
        return endpoint.isTransacted()
                || endpoint.getAcknowledgementMode() == SessionAcknowledgementType.CLIENT_ACKNOWLEDGE;
    }

    @Override
    public void run() {
        LOG.trace("run START");
        int batchSize = endpoint.getBatchSize();
        long batchInterval = endpoint.getBatchInterval();
        boolean redeliverable = isRedeliverable();
        List<Message> buffer = new ArrayList<>();
        long batchStartTime = 0L;

        try {
            while (running.get()) {
                long waitMillis
                        = computeWaitMillis(buffer.isEmpty(), batchStartTime, batchInterval);

                Message msg = consumer.receive(waitMillis);

                if (msg != null) {
                    if (buffer.isEmpty()) {
                        batchStartTime = System.nanoTime();
                    }
                    buffer.add(msg);
                    if (!redeliverable) {
                        pendingMessages = buffer.size();
                    }
                }

                boolean sizeReached = batchSize > 0 && buffer.size() >= batchSize;
                boolean intervalElapsed = batchInterval > 0 && !buffer.isEmpty()
                        && TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - batchStartTime) >= batchInterval;
                boolean due = sizeReached || intervalElapsed;
                if (due && redeliverable) {
                    // counted before the consumer is found not suspended, so that a graceful shutdown that suspends
                    // the consumer meanwhile sees this batch when it counts the pending messages
                    pendingMessages = buffer.size();
                }

                boolean dispatch;
                if (suspended.getAsBoolean()) {
                    // the consumer is suspended, such as by a graceful shutdown that waits for the pending messages.
                    // The buffered messages of an AUTO_ACKNOWLEDGE session are acknowledged already, so they are
                    // routed now, as no more messages may come to complete the batch. A transacted or
                    // CLIENT_ACKNOWLEDGE session starts no batch while suspended: its buffered messages are routed
                    // after a resume, or redelivered after a stop. So a stop does not close the session of a batch
                    // that started after the graceful shutdown stopped waiting (that batch would be routed again
                    // when redelivered)
                    dispatch = !redeliverable && !buffer.isEmpty();
                } else {
                    dispatch = due;
                }

                if (dispatch) {
                    dispatchBatch(buffer);
                    buffer = new ArrayList<>();
                } else if (redeliverable) {
                    pendingMessages = 0;
                }
            }

            if (!buffer.isEmpty()) {
                dispatchBatch(buffer); // graceful-stop drain
            }
        } catch (JMSException e) {
            jmsUnhealthy = true;
            if (!buffer.isEmpty()) {
                dispatchBatch(buffer);
            }
            throw new BatchConsumerWorkerException(e);
        } finally {
            pendingMessages = 0;
        }
        LOG.trace("run STOP");
    }

    private void dispatchBatch(List<Message> batch) {
        pendingMessages = batch.size();
        try {
            dispatchOrDiscard(batch);
        } finally {
            pendingMessages = 0;
        }
    }

    private void dispatchOrDiscard(List<Message> batch) {
        if (jmsUnhealthy) {
            if (isRedeliverable()) {
                LOG.warn("JMS is unhealthy on {}: discarding {} buffered message(s); "
                         + "unacknowledged/uncommitted, will be redelivered",
                        endpoint.getEndpointUri(), batch.size());
                return;
            }
            LOG.warn("JMS is unhealthy on {}: attempting best-effort dispatch of {} already-acknowledged "
                     + "message(s) since they cannot be redelivered",
                    endpoint.getEndpointUri(), batch.size());
        }
        if (isShutdownRequested() && isRedeliverable()) {
            LOG.debug("Shutting down {}: discarding {} buffered message(s); "
                      + "unacknowledged/uncommitted, will be redelivered",
                    endpoint.getEndpointUri(), batch.size());
            return;
        }
        onBatch(batch);
    }

    private void onBatch(List<Message> batch) {
        try {
            doOnBatch(batch);
        } catch (Exception e) {
            if (e instanceof JMSException jmsException) {
                if (endpoint.getExceptionListener() != null) {
                    endpoint.getExceptionListener().onException(jmsException);
                } else {
                    LOG.warn("JMS exception on {} (no ExceptionListener configured)",
                            endpoint.getEndpointUri(), jmsException);
                }
            } else {
                batchListener.handleException(
                        "Execution of batch JMS message listener failed.",
                        e);
            }
        }
    }

    private void doOnBatch(List<Message> batch) throws Exception {
        try {
            batchListener.onBatch(batch, session);
        } catch (Exception e) {
            // unexpected error so rollback
            rollbackIfNeeded(session);
            throw e;
        }
        // success then commit if we need to
        Message lastMessage = batch.get(batch.size() - 1);
        commitIfNeeded(session, lastMessage);
    }

    private long computeWaitMillis(
            boolean isBufferEmpty, long batchStartNanos,
            long batchIntervalMillis) {
        if (isBufferEmpty || batchIntervalMillis <= 0) {
            return JMS_CONSUMER_RECEIVE_WAKE_INTERVAL_TIMEOUT;
        }

        long elapsedNanos = System.nanoTime() - batchStartNanos;
        long remainingMillis = batchIntervalMillis - TimeUnit.NANOSECONDS.toMillis(elapsedNanos);

        return Math.max(JMS_CONSUMER_RECEIVE_MIN_TIMEOUT,
                Math.min(remainingMillis, JMS_CONSUMER_RECEIVE_WAKE_INTERVAL_TIMEOUT));
    }
}
