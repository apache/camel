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
package org.apache.camel.component.hazelcast.queue;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import com.hazelcast.collection.IQueue;
import com.hazelcast.core.HazelcastInstance;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.hazelcast.HazelcastDefaultConsumer;
import org.apache.camel.component.hazelcast.listener.CamelItemListener;

public class HazelcastQueueConsumer extends HazelcastDefaultConsumer {

    // the minimum delay before polling again after a poll error, so a pollingTimeout of 0 does not spin
    private static final long MIN_POLL_ERROR_DELAY = 1000L;

    private final Processor processor;
    private ExecutorService executor;
    private HazelcastQueueConfiguration config;
    private IQueue<Object> queue;
    private UUID listener;
    // counted down on stop, to end the delay after a poll error without waiting for it
    private CountDownLatch stopLatch;

    public HazelcastQueueConsumer(HazelcastInstance hazelcastInstance, Endpoint endpoint, Processor processor, String cacheName,
                                  final HazelcastQueueConfiguration configuration) {
        super(hazelcastInstance, endpoint, processor, cacheName);
        this.processor = processor;
        this.config = configuration;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        queue = hazelcastInstance.getQueue(cacheName);

        if (config.getQueueConsumerMode() == HazelcastQueueConsumerMode.LISTEN) {
            // register the listener here, so that doStop can remove it (CAMEL-15899)
            listener = queue.addItemListener(new CamelItemListener(this, cacheName), true);
        } else if (config.getQueueConsumerMode() == HazelcastQueueConsumerMode.POLL) {
            stopLatch = new CountDownLatch(1);
            executor = ((HazelcastQueueEndpoint) getEndpoint()).createExecutor(this);
            executor.submit(new QueueConsumerTask(queue, stopLatch));
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (listener != null) {
            queue.removeItemListener(listener);
            listener = null;
        }

        super.doStop();

        if (stopLatch != null) {
            stopLatch.countDown();
        }
        if (executor != null) {
            if (getEndpoint() != null && getEndpoint().getCamelContext() != null) {
                getEndpoint().getCamelContext().getExecutorServiceManager().shutdownNow(executor);
            } else {
                executor.shutdownNow();
            }
        }
        executor = null;
    }

    class QueueConsumerTask implements Runnable {

        private final IQueue<Object> queue;
        private final CountDownLatch stopLatch;

        QueueConsumerTask(IQueue<Object> queue, CountDownLatch stopLatch) {
            this.queue = queue;
            this.stopLatch = stopLatch;
        }

        @Override
        public void run() {
            while (isRunAllowed()) {
                final Object body;
                try {
                    body = queue.poll(config.getPollingTimeout(), TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    // only doStop interrupts this thread
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    // keep polling after an error (such as the client being disconnected from the cluster)
                    if (isRunAllowed()) {
                        getExceptionHandler().handleException("Error polling from the queue " + cacheName, e);
                        if (!waitBeforeNextPoll()) {
                            return;
                        }
                    }
                    continue;
                }
                // CAMEL-16035 - If the polling timeout is exceeded with nothing to poll from the queue, the queue.poll() method return NULL
                if (body != null) {
                    Exchange exchange = createExchange(false);
                    exchange.getIn().setBody(body);
                    try {
                        processor.process(exchange);
                    } catch (Exception e) {
                        getExceptionHandler().handleException("Error during processing", exchange, e);
                    } finally {
                        releaseExchange(exchange, false);
                    }
                }
            }
        }

        /**
         * Waits before the next poll after a poll error, and ends early when the consumer stops.
         *
         * @return false if the thread was interrupted
         */
        private boolean waitBeforeNextPoll() {
            try {
                stopLatch.await(Math.max(config.getPollingTimeout(), MIN_POLL_ERROR_DELAY), TimeUnit.MILLISECONDS);
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

}
