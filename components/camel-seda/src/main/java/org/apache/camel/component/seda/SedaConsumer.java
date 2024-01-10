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

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.Suspendable;
import org.apache.camel.spi.ShutdownAware;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.EmptyAsyncCallback;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.UnitOfWorkHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Consumer for the SEDA component.
 * <p/>
 * In this implementation there is a little <i>slack period</i> when you suspend/stop the consumer, by which the
 * consumer may pickup a newly arrived messages and process it. That period is up till 1 second.
 */
public class SedaConsumer extends DefaultConsumer implements Runnable, ShutdownAware, Suspendable {

    private static final Logger LOG = LoggerFactory.getLogger(SedaConsumer.class);

    private final AtomicInteger taskCount = new AtomicInteger();
    private volatile CountDownLatch latch;
    private volatile boolean shutdownPending;
    private volatile boolean forceShutdown;
    private ExecutorService executor;
    private final int pollTimeout;

    public SedaConsumer(SedaEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.pollTimeout = endpoint.getPollTimeout();
    }

    @Override
    public SedaEndpoint getEndpoint() {
        return (SedaEndpoint) super.getEndpoint();
    }

    @Override
    public boolean deferShutdown(ShutdownRunningTask shutdownRunningTask) {
        // deny stopping on shutdown as we want seda consumers to run in case some other queues
        // depend on this consumer to run, so it can complete its exchanges
        return true;
    }

    @Override
    public int getPendingExchangesSize() {
        // the route is shutting down, so either we should purge the queue,
        // or return how many exchanges are still on the queue
        if (getEndpoint().isPurgeWhenStopping()) {
            getEndpoint().purgeQueue();
        }
        return getEndpoint().getQueue().size();
    }

    @Override
    public void prepareShutdown(boolean suspendOnly, boolean forced) {
        // if we are suspending then we want to keep the thread running but just not route the exchange
        // this logic is only when we stop or shutdown the consumer
        if (suspendOnly) {
            LOG.debug("Skip preparing to shutdown as consumer is being suspended");
            return;
        }

        // signal we want to shutdown
        shutdownPending = true;
        forceShutdown = forced;

        if (latch != null) {
            LOG.debug("Preparing to shutdown, waiting for {} consumer threads to complete.", latch.getCount());

            // wait for all threads to end
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public boolean isRunAllowed() {
        // if we force shutdown then do not allow running anymore
        if (forceShutdown) {
            return false;
        }

        if (isSuspending() || isSuspended()) {
            // allow to run even if we are suspended as we want to
            // keep the thread task running
            return true;
        }
        return super.isRunAllowed();
    }

    @Override
    public void run() {
        taskCount.incrementAndGet();
        try {
            doRun();
        } finally {
            taskCount.decrementAndGet();
            latch.countDown();
            LOG.debug("Ending this polling consumer thread, there are still {} consumer threads left.", latch.getCount());
        }
    }

    protected void doRun() {
        BlockingQueue<Exchange> queue = getEndpoint().getQueue();
        // loop while we are allowed, or if we are stopping loop until the queue is empty
        while (queue != null && isRunAllowed()) {

            // do not poll during CamelContext is starting, as we should only poll when CamelContext is fully started
            if (getEndpoint().getCamelContext().getStatus().isStarting()) {
                LOG.trace("CamelContext is starting so skip polling");
                try {
                    // sleep at most 1 sec
                    Thread.sleep(Math.min(pollTimeout, 1000));
                } catch (InterruptedException e) {
                    LOG.debug("Sleep interrupted, are we stopping? {}", isStopping() || isStopped());
                    Thread.currentThread().interrupt();
                }
                continue;
            }

            // do not poll if we are suspended or starting again after resuming
            if (isSuspending() || isSuspended() || isStarting()) {
                if (shutdownPending && queue.isEmpty()) {
                    LOG.trace(
                            "Consumer is suspended and shutdown is pending, so this consumer thread is breaking out because the task queue is empty.");
                    // we want to shutdown so break out if there queue is empty
                    break;
                } else {
                    LOG.trace("Consumer is suspended so skip polling");
                    try {
                        // sleep at most 1 sec
                        Thread.sleep(Math.min(pollTimeout, 1000));
                    } catch (InterruptedException e) {
                        LOG.debug("Sleep interrupted, are we stopping? {}", isStopping() || isStopped());
                        Thread.currentThread().interrupt();
                    }
                    continue;
                }
            }

            Exchange exchange = null;
            try {
                // use the end user configured poll timeout
                exchange = queue.poll(pollTimeout, TimeUnit.MILLISECONDS);
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Polled queue {} with timeout {} ms. -> {}", ObjectHelper.getIdentityHashCode(queue), pollTimeout,
                            exchange);
                }
                if (exchange != null) {
                    try {
                        // prepare the exchange before sending to consumer
                        Exchange newExchange = prepareExchange(exchange);
                        // process the exchange
                        sendToConsumers(newExchange);
                        // copy result back
                        ExchangeHelper.copyResults(exchange, newExchange);
                        // log exception if an exception occurred and was not handled
                        if (exchange.getException() != null) {
                            getExceptionHandler().handleException("Error processing exchange", exchange,
                                    exchange.getException());
                        }
                    } catch (Exception e) {
                        getExceptionHandler().handleException("Error processing exchange", exchange, e);
                    }
                } else if (shutdownPending && queue.isEmpty()) {
                    LOG.trace("Shutdown is pending, so this consumer thread is breaking out because the task queue is empty.");
                    // we want to shutdown so break out if there queue is empty
                    break;
                }
            } catch (InterruptedException e) {
                LOG.debug("Sleep interrupted, are we stopping? {}", isStopping() || isStopped());
                Thread.currentThread().interrupt();
                continue;
            } catch (Exception e) {
                if (exchange != null) {
                    getExceptionHandler().handleException("Error processing exchange", exchange, e);
                } else {
                    getExceptionHandler().handleException(e);
                }
            }
        }
    }

    /**
     * Strategy to prepare exchange for being processed by this consumer
     *
     * @param  exchange the exchange
     * @return          the exchange to process by this consumer.
     */
    protected Exchange prepareExchange(Exchange exchange) {
        // this consumer grabbed the exchange so mark its from this route/endpoint
        exchange.getExchangeExtension().setFromEndpoint(getEndpoint());
        exchange.getExchangeExtension().setFromRouteId(getRouteId());
        return exchange;
    }

    /**
     * Send the given {@link Exchange} to the consumer(s).
     * <p/>
     * If multiple consumers then they will each receive a copy of the Exchange. A multicast processor will send the
     * exchange in parallel to the multiple consumers.
     * <p/>
     * If there is only a single consumer then its dispatched directly to it using same thread.
     *
     * @param  exchange  the exchange
     * @throws Exception can be thrown if processing of the exchange failed
     */
    protected void sendToConsumers(final Exchange exchange) throws Exception {
        // validate multiple consumers has been enabled
        int size = getEndpoint().getConsumers().size();
        if (size > 1 && !getEndpoint().isMultipleConsumersSupported()) {
            throw new IllegalStateException("Multiple consumers for the same endpoint is not allowed: " + getEndpoint());
        }

        // if there are multiple consumers then multicast to them
        if (getEndpoint().isMultipleConsumersSupported()) {

            if (LOG.isTraceEnabled()) {
                LOG.trace("Multicasting to {} consumers for Exchange: {}", size, exchange);
            }

            // handover completions, as we need to done this when the multicast is done
            final List<Synchronization> completions = exchange.getExchangeExtension().handoverCompletions();

            // use a multicast processor to process it
            AsyncProcessor mp = getEndpoint().getConsumerMulticastProcessor();
            ObjectHelper.notNull(mp, "ConsumerMulticastProcessor", this);

            // and use the asynchronous routing engine to support it
            mp.process(exchange, doneSync -> {
                // done the uow on the completions
                UnitOfWorkHelper.doneSynchronizations(exchange, completions);
            });
        } else {
            // use the regular processor and use the asynchronous routing engine to support it
            getAsyncProcessor().process(exchange, EmptyAsyncCallback.get());
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        latch = new CountDownLatch(getEndpoint().getConcurrentConsumers());
        shutdownPending = false;
        forceShutdown = false;

        setupTasks();
        getEndpoint().onStarted(this);
    }

    @Override
    protected void doSuspend() throws Exception {
        getEndpoint().onStopped(this);
    }

    @Override
    protected void doResume() throws Exception {
        getEndpoint().onStarted(this);
    }

    @Override
    protected void doStop() throws Exception {
        // ensure queue is purged if we stop the consumer
        if (getEndpoint().isPurgeWhenStopping()) {
            getEndpoint().purgeQueue();
        }

        getEndpoint().onStopped(this);

        shutdownExecutor();

        super.doStop();
    }

    @Override
    protected void doShutdown() throws Exception {
        shutdownExecutor();
    }

    private void shutdownExecutor() {
        if (executor != null) {
            getEndpoint().getCamelContext().getExecutorServiceManager().shutdownNow(executor);
            executor = null;
        }
    }

    /**
     * Setup the thread pool and ensures tasks gets executed (if needed)
     */
    private void setupTasks() {
        int poolSize = getEndpoint().getConcurrentConsumers();

        // create thread pool if needed
        if (executor == null) {
            executor = getEndpoint().getCamelContext().getExecutorServiceManager().newFixedThreadPool(this,
                    getEndpoint().getEndpointUri(), poolSize);
        }

        // submit needed number of tasks
        int tasks = poolSize - taskCount.get();
        LOG.debug("Creating {} consumer tasks with poll timeout {} ms.", tasks, pollTimeout);
        for (int i = 0; i < tasks; i++) {
            executor.execute(this);
        }
    }

}
