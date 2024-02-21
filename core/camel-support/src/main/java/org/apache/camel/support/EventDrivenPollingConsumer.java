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
package org.apache.camel.support;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.IsSingleton;
import org.apache.camel.PollingConsumerPollingStrategy;
import org.apache.camel.PooledExchange;
import org.apache.camel.Processor;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.support.service.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A default implementation of the {@link org.apache.camel.PollingConsumer} which uses the normal asynchronous consumer
 * mechanism along with a {@link BlockingQueue} to allow the caller to pull messages on demand.
 */
public class EventDrivenPollingConsumer extends PollingConsumerSupport implements Processor, IsSingleton {

    private static final Logger LOG = LoggerFactory.getLogger(EventDrivenPollingConsumer.class);

    private final BlockingQueue<Exchange> queue;
    private ExceptionHandler interruptedExceptionHandler;
    private Consumer consumer;
    private boolean blockWhenFull = true;
    private long blockTimeout;
    private final int queueCapacity;
    private boolean copy;

    public EventDrivenPollingConsumer(Endpoint endpoint) {
        this(endpoint, 1000);
    }

    public EventDrivenPollingConsumer(Endpoint endpoint, int queueSize) {
        super(endpoint);
        this.queueCapacity = queueSize;
        if (queueSize <= 0) {
            this.queue = new LinkedBlockingQueue<>();
        } else {
            this.queue = new ArrayBlockingQueue<>(queueSize);
        }
        this.interruptedExceptionHandler
                = new LoggingExceptionHandler(endpoint.getCamelContext(), EventDrivenPollingConsumer.class);
    }

    public EventDrivenPollingConsumer(Endpoint endpoint, BlockingQueue<Exchange> queue) {
        super(endpoint);
        this.queue = queue;
        this.queueCapacity = queue.remainingCapacity();
        this.interruptedExceptionHandler
                = new LoggingExceptionHandler(endpoint.getCamelContext(), EventDrivenPollingConsumer.class);
    }

    @Override
    public Processor getProcessor() {
        return this;
    }

    public boolean isBlockWhenFull() {
        return blockWhenFull;
    }

    public void setBlockWhenFull(boolean blockWhenFull) {
        this.blockWhenFull = blockWhenFull;
    }

    public long getBlockTimeout() {
        return blockTimeout;
    }

    public void setBlockTimeout(long blockTimeout) {
        this.blockTimeout = blockTimeout;
    }

    public boolean isCopy() {
        return copy;
    }

    public void setCopy(boolean copy) {
        this.copy = copy;
    }

    /**
     * Gets the queue capacity.
     */
    public int getQueueCapacity() {
        return queueCapacity;
    }

    /**
     * Gets the current queue size (no of elements in the queue).
     */
    public int getQueueSize() {
        return queue.size();
    }

    @Override
    public Exchange receiveNoWait() {
        return receive(0);
    }

    @Override
    public Exchange receive() {
        // must be started
        if (!isRunAllowed() || !isStarted()) {
            throw new RejectedExecutionException(this + " is not started, but in state: " + getStatus().name());
        }

        while (isRunAllowed()) {
            // synchronizing the ordering of beforePoll, poll and afterPoll as an atomic activity
            synchronized (this) {
                try {
                    beforePoll(0);
                    // take will block waiting for message
                    return queue.take();
                } catch (InterruptedException e) {
                    handleInterruptedException(e);
                } finally {
                    afterPoll();
                }
            }
        }
        LOG.trace("Consumer is not running, so returning null");
        return null;
    }

    @Override
    public Exchange receive(long timeout) {
        // must be started
        if (!isRunAllowed() || !isStarted()) {
            throw new RejectedExecutionException(this + " is not started, but in state: " + getStatus().name());
        }

        // synchronizing the ordering of beforePoll, poll and afterPoll as an atomic activity
        synchronized (this) {
            try {
                // use the timeout value returned from beforePoll
                timeout = beforePoll(timeout);
                return queue.poll(timeout, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                handleInterruptedException(e);
                return null;
            } finally {
                afterPoll();
            }
        }
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        // we must make a copy as the exchange put on queue is consumed by another part
        // and it would not reset and return the pooled exchange to the pool
        boolean pooled = exchange instanceof PooledExchange;

        if (isCopy() || pooled) {
            // if we copy then we handover completion
            exchange = prepareCopy(exchange, true);
        }

        if (isBlockWhenFull()) {
            try {
                if (getBlockTimeout() <= 0) {
                    queue.put(exchange);
                } else {
                    boolean added = queue.offer(exchange, getBlockTimeout(), TimeUnit.MILLISECONDS);
                    if (!added) {
                        throw new ExchangeTimedOutException(exchange, getBlockTimeout());
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.debug("Put interrupted, are we stopping? {}", isStopping() || isStopped());
            }
        } else {
            queue.add(exchange);
        }
    }

    protected Exchange prepareCopy(Exchange exchange, boolean handover) {
        // use a new copy of the exchange to route async (and use same message id)

        // if handover we need to do special handover to avoid handing over
        // RestBindingMarshalOnCompletion as it should not be handed over
        Exchange copy = ExchangeHelper.createCorrelatedCopy(exchange, handover, true);

        // we want the copy to have an uow
        UnitOfWork uow = PluginHelper.getUnitOfWorkFactory(getEndpoint().getCamelContext()).createUnitOfWork(copy);
        copy.getExchangeExtension().setUnitOfWork(uow);

        return copy;
    }

    public ExceptionHandler getInterruptedExceptionHandler() {
        return interruptedExceptionHandler;
    }

    public void setInterruptedExceptionHandler(ExceptionHandler interruptedExceptionHandler) {
        this.interruptedExceptionHandler = interruptedExceptionHandler;
    }

    public Consumer getDelegateConsumer() {
        return consumer;
    }

    protected void handleInterruptedException(InterruptedException e) {
        Thread.currentThread().interrupt();
        getInterruptedExceptionHandler().handleException(e);
    }

    protected long beforePoll(long timeout) {
        if (consumer instanceof PollingConsumerPollingStrategy) {
            PollingConsumerPollingStrategy strategy = (PollingConsumerPollingStrategy) consumer;
            try {
                timeout = strategy.beforePoll(timeout);
            } catch (Exception e) {
                LOG.debug("Error occurred before polling {}. This exception will be ignored.", consumer, e);
            }
        }
        return timeout;
    }

    protected void afterPoll() {
        if (consumer instanceof PollingConsumerPollingStrategy) {
            PollingConsumerPollingStrategy strategy = (PollingConsumerPollingStrategy) consumer;
            try {
                strategy.afterPoll();
            } catch (Exception e) {
                LOG.debug("Error occurred after polling {}. This exception will be ignored.", consumer, e);
            }
        }
    }

    protected Consumer getConsumer() {
        return consumer;
    }

    protected Consumer createConsumer() throws Exception {
        return getEndpoint().createConsumer(this);
    }

    @Override
    protected void doBuild() throws Exception {
        super.doBuild();
        // lets add ourselves as a consumer (only create do not build)
        consumer = createConsumer();
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        ServiceHelper.initService(consumer);
    }

    @Override
    protected void doStart() throws Exception {
        // if the consumer has a polling strategy then invoke that
        if (consumer instanceof PollingConsumerPollingStrategy) {
            PollingConsumerPollingStrategy strategy = (PollingConsumerPollingStrategy) consumer;
            strategy.onInit();
        } else {
            ServiceHelper.startService(consumer);
        }
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(consumer);
    }

    @Override
    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownService(consumer);
        queue.clear();
    }

    @Override
    // As the consumer could take the messages at once, so we cannot release the consumer
    public boolean isSingleton() {
        return true;
    }
}
