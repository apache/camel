/**
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
package org.apache.camel.impl;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.PollingConsumerPollingStrategy;
import org.apache.camel.Processor;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A default implementation of the {@link org.apache.camel.PollingConsumer} which uses the normal
 * asynchronous consumer mechanism along with a {@link BlockingQueue} to allow
 * the caller to pull messages on demand.
 *
 * @version 
 */
public class EventDrivenPollingConsumer extends PollingConsumerSupport implements Processor {
    private static final transient Logger LOG = LoggerFactory.getLogger(EventDrivenPollingConsumer.class);
    private final BlockingQueue<Exchange> queue;
    private ExceptionHandler interruptedExceptionHandler = new LoggingExceptionHandler(EventDrivenPollingConsumer.class);
    private Consumer consumer;

    public EventDrivenPollingConsumer(Endpoint endpoint) {
        this(endpoint, new ArrayBlockingQueue<Exchange>(1000));
    }

    public EventDrivenPollingConsumer(Endpoint endpoint, BlockingQueue<Exchange> queue) {
        super(endpoint);
        this.queue = queue;
    }

    public Exchange receiveNoWait() {
        return receive(0);
    }

    public Exchange receive() {
        // must be started
        if (!isRunAllowed() || !isStarted()) {
            throw new RejectedExecutionException(this + " is not started, but in state: " + getStatus().name());
        }

        while (isRunAllowed()) {
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
        LOG.trace("Consumer is not running, so returning null");
        return null;
    }

    public Exchange receive(long timeout) {
        // must be started
        if (!isRunAllowed() || !isStarted()) {
            throw new RejectedExecutionException(this + " is not started, but in state: " + getStatus().name());
        }

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

    public void process(Exchange exchange) throws Exception {
        queue.offer(exchange);
    }

    public ExceptionHandler getInterruptedExceptionHandler() {
        return interruptedExceptionHandler;
    }

    public void setInterruptedExceptionHandler(ExceptionHandler interruptedExceptionHandler) {
        this.interruptedExceptionHandler = interruptedExceptionHandler;
    }

    protected void handleInterruptedException(InterruptedException e) {
        getInterruptedExceptionHandler().handleException(e);
    }

    protected long beforePoll(long timeout) {
        if (consumer instanceof PollingConsumerPollingStrategy) {
            PollingConsumerPollingStrategy strategy = (PollingConsumerPollingStrategy) consumer;
            try {
                timeout = strategy.beforePoll(timeout);
            } catch (Exception e) {
                LOG.debug("Error occurred before polling " + consumer + ". This exception will be ignored.", e);
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
                LOG.debug("Error occurred after polling " + consumer + ". This exception will be ignored.", e);
            }
        }
    }

    protected void doStart() throws Exception {
        // lets add ourselves as a consumer
        consumer = getEndpoint().createConsumer(this);

        // if the consumer has a polling strategy then invoke that
        if (consumer instanceof PollingConsumerPollingStrategy) {
            PollingConsumerPollingStrategy strategy = (PollingConsumerPollingStrategy) consumer;
            strategy.onInit();
        } else {
            // for regular consumers start it
            ServiceHelper.startService(consumer);
        }
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopService(consumer);
    }

    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownService(consumer);
    }
}
