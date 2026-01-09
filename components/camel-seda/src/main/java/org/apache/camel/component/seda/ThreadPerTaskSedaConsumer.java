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
package org.apache.camel.component.seda;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A SEDA consumer that spawns a new thread/task for each message instead of using a fixed pool of long-running consumer
 * threads.
 * <p>
 * This consumer model is optimized for virtual threads (JDK 21+) where creating threads is very cheap, but it also
 * works with platform threads. The key differences from {@link SedaConsumer} are:
 * <ul>
 * <li>Uses a cached thread pool instead of a fixed pool</li>
 * <li>A single coordinator thread polls the queue</li>
 * <li>Each message is processed in its own task/thread</li>
 * <li>The concurrentConsumers setting becomes a concurrency limit (0 = unlimited)</li>
 * </ul>
 * <p>
 * When virtual threads are enabled via {@code camel.threads.virtual.enabled=true}, the cached thread pool will use
 * {@code Executors.newThreadPerTaskExecutor()}, providing optimal scaling for I/O-bound workloads.
 */
public class ThreadPerTaskSedaConsumer extends SedaConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(ThreadPerTaskSedaConsumer.class);

    private final int maxConcurrentTasks;
    private final LongAdder activeTasks = new LongAdder();

    private volatile ExecutorService taskExecutor;
    private volatile Semaphore concurrencyLimiter;

    public ThreadPerTaskSedaConsumer(SedaEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        // Use concurrentConsumers as the max concurrent tasks limit
        // 0 means unlimited (the most common case for virtual threads)
        this.maxConcurrentTasks = endpoint.getConcurrentConsumers();
    }

    @Override
    protected ExecutorService createExecutor(int poolSize) {
        // Create a single-thread executor for the coordinator
        // The actual work is done by taskExecutor
        return getEndpoint().getCamelContext().getExecutorServiceManager()
                .newSingleThreadExecutor(this, getEndpoint().getEndpointUri() + "-coordinator");
    }

    @Override
    protected void setupTasks() {
        // Create task executor - uses virtual threads when enabled
        taskExecutor = getEndpoint().getCamelContext().getExecutorServiceManager()
                .newCachedThreadPool(this, getEndpoint().getEndpointUri() + "-task");

        // Create concurrency limiter if max is specified and > 0
        if (maxConcurrentTasks > 0) {
            concurrencyLimiter = new Semaphore(maxConcurrentTasks);
            LOG.debug("Using concurrency limit of {} for thread-per-task consumer", maxConcurrentTasks);
        }

        // Call parent to create the coordinator executor and start it
        super.setupTasks();

        LOG.info("Started thread-per-task SEDA consumer for {} (maxConcurrent={})",
                getEndpoint().getEndpointUri(), maxConcurrentTasks > 0 ? maxConcurrentTasks : "unlimited");
    }

    @Override
    protected void shutdownExecutor() {
        super.shutdownExecutor();
        if (taskExecutor != null) {
            getEndpoint().getCamelContext().getExecutorServiceManager().shutdown(taskExecutor);
            taskExecutor = null;
        }
    }

    @Override
    protected boolean beforePoll() throws InterruptedException {
        // Acquire permit if using concurrency limiter (blocks if at limit)
        if (concurrencyLimiter != null) {
            return concurrencyLimiter.tryAcquire(pollTimeout, TimeUnit.MILLISECONDS);
        }
        return true;
    }

    @Override
    protected void afterPollEmpty() {
        // Release permit if we acquired one
        if (concurrencyLimiter != null) {
            concurrencyLimiter.release();
        }
    }

    @Override
    protected void processPolledExchange(Exchange exchange) {
        // Dispatch to task executor for processing
        taskExecutor.execute(() -> {
            activeTasks.increment();
            try {
                // Prepare the exchange
                Exchange prepared = prepareExchange(exchange);

                // Process asynchronously
                AsyncCallback callback = doneSync -> {
                    if (exchange.getException() != null) {
                        getExceptionHandler().handleException("Error processing exchange", exchange,
                                exchange.getException());
                    }
                };
                sendToConsumers(prepared, callback);
            } catch (Exception e) {
                getExceptionHandler().handleException("Error processing exchange", exchange, e);
            } finally {
                activeTasks.decrement();
                if (concurrencyLimiter != null) {
                    concurrencyLimiter.release();
                }
            }
        });
    }

    /**
     * Returns the current number of active processing tasks.
     */
    public long getActiveTaskCount() {
        return activeTasks.sum();
    }

    /**
     * Returns the maximum concurrent tasks allowed (0 means unlimited).
     */
    public int getMaxConcurrentTasks() {
        return maxConcurrentTasks;
    }
}
