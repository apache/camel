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

class BatchConsumerWorker implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(BatchConsumerWorker.class);

    private static final long JMS_CONSUMER_RECEIVE_WAKE_INTERVAL_TIMEOUT = 1000L;
    private static final long JMS_CONSUMER_RECEIVE_MIN_TIMEOUT = 100L;

    private final SjmsEndpoint endpoint;
    private final BatchEndpointMessageListener batchListener;
    private final MessageConsumer consumer;
    private final Session session;
    private final AtomicBoolean running = new AtomicBoolean(true);

    BatchConsumerWorker(SjmsEndpoint endpoint, BatchEndpointMessageListener batchListener,
                        MessageConsumer consumer, Session session) {
        this.endpoint = endpoint;
        this.batchListener = batchListener;
        this.consumer = consumer;
        this.session = session;
    }

    void shutdown() {
        running.set(false);
    }

    boolean isShutdownRequested() {
        return !running.get();
    }

    private boolean isRedeliverable() {
        return endpoint.isTransacted()
                || endpoint.getAcknowledgementMode() == SessionAcknowledgementType.CLIENT_ACKNOWLEDGE;
    }

    @Override
    public void run() {
        int batchSize = endpoint.getBatchSize();
        long batchInterval = endpoint.getBatchInterval();
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
                }

                boolean sizeReached = batchSize > 0 && buffer.size() >= batchSize;
                boolean intervalElapsed = batchInterval > 0 && !buffer.isEmpty()
                        && TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - batchStartTime) >= batchInterval;

                if (sizeReached || intervalElapsed) {
                    onBatch(buffer);
                    buffer = new ArrayList<>();
                }
            }

            if (!buffer.isEmpty()) {
                onBatch(buffer); // graceful-stop drain
            }
        } catch (JMSException e) {
            if (!buffer.isEmpty()) {
                if (isRedeliverable()) {
                    LOG.error("Discarding {} buffered message(s) on {} after connection failure; "
                              + "unacknowledged/uncommitted, will be redelivered",
                            buffer.size(), endpoint.getEndpointUri());
                } else {
                    onBatch(buffer);
                    LOG.warn("Connection failed on {} with {} already-acknowledged message(s) buffered; "
                             + "attempting best-effort dispatch since they cannot be redelivered",
                            endpoint.getEndpointUri(), buffer.size());
                }
            }
            throw new BatchConsumerWorkerException(e);
        }
    }

    private void onBatch(List<Message> batch) {
        try {
            doOnBatch(batch);
        } catch (Exception e) {
            if (e instanceof JMSException jmsException) {
                if (endpoint.getExceptionListener() != null) {
                    endpoint.getExceptionListener().onException(jmsException);
                }
            } else {
                LOG.warn("Execution of JMS message listener failed. This exception is ignored.", e);
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
