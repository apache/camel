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

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;

import org.apache.camel.Exchange;
import org.apache.camel.spi.AsyncProcessorAwaitManager;
import org.apache.camel.support.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultAsyncProcessorAwaitManager extends ServiceSupport implements AsyncProcessorAwaitManager {

    // TODO: capture message history of the exchange when it was interrupted
    // TODO: capture route id, node id where thread is blocked
    // TODO: rename to AsyncInflightRepository?

    private static final Logger LOG = LoggerFactory.getLogger(DefaultAsyncProcessorAwaitManager.class);

    private final Map<Exchange, AwaitThread> inflight = new ConcurrentHashMap<Exchange, AwaitThread>();

    private boolean interruptThreadsWhileStopping = true;

    @Override
    public void await(Exchange exchange, CountDownLatch latch) {
        LOG.trace("Waiting for asynchronous callback before continuing for exchangeId: {} -> {}",
                exchange.getExchangeId(), exchange);
        try {
            inflight.put(exchange, new AwaitThreadEntry(Thread.currentThread(), exchange, latch));
            latch.await();
            LOG.trace("Asynchronous callback received, will continue routing exchangeId: {} -> {}",
                    exchange.getExchangeId(), exchange);

        } catch (InterruptedException e) {
            LOG.trace("Interrupted while waiting for callback, will continue routing exchangeId: {} -> {}",
                    exchange.getExchangeId(), exchange);
            exchange.setException(e);
        } finally {
            inflight.remove(exchange);
        }
    }

    @Override
    public void countDown(Exchange exchange, CountDownLatch latch) {
        LOG.trace("Asynchronous callback received for exchangeId: {}", exchange.getExchangeId());
        latch.countDown();
    }

    @Override
    public int size() {
        return inflight.size();
    }

    @Override
    public Collection<AwaitThread> browse() {
        return Collections.unmodifiableCollection(inflight.values());
    }

    @Override
    public void interrupt(Exchange exchange) {
        AwaitThreadEntry latch = (AwaitThreadEntry) inflight.get(exchange);
        if (latch != null) {
            LOG.warn("Interrupted while waiting for asynchronous callback, will continue routing exchangeId: {} -> {}",
                    exchange.getExchangeId(), exchange);
            exchange.setException(new RejectedExecutionException("Interrupted while waiting for asynchronous callback"));
            latch.getLatch().countDown();
        }
    }

    public boolean isInterruptThreadsWhileStopping() {
        return interruptThreadsWhileStopping;
    }

    public void setInterruptThreadsWhileStopping(boolean interruptThreadsWhileStopping) {
        this.interruptThreadsWhileStopping = interruptThreadsWhileStopping;
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        Collection<AwaitThread> threads = browse();
        int count = threads.size();
        if (count > 0) {
            LOG.warn("Shutting down while there are still " + count + " inflight threads currently blocked.");

            StringBuilder sb = new StringBuilder();
            for (AwaitThread entry : threads) {
                sb.append("\tBlocked thread: ").append(entry.getBlockedThread().getName())
                        .append(", exchangeId=").append(entry.getExchange().getExchangeId())
                        .append(", duration=").append(entry.getWaitDuration()).append(" msec.");
            }

            if (isInterruptThreadsWhileStopping()) {
                LOG.warn("The following threads are blocked and will be interrupted so the threads are released:\n" + sb.toString());
                for (AwaitThread entry : threads) {
                    try {
                        interrupt(entry.getExchange());
                    } catch (Throwable e) {
                        LOG.warn("Error while interrupting thread: " + entry.getBlockedThread().getName() + ". This exception is ignored.", e);
                    }
                }
            } else {
                LOG.warn("The following threads are blocked, and may reside in the JVM:\n" + sb.toString());
            }
        } else {
            LOG.debug("Shutting down with no inflight threads.");
        }

        inflight.clear();
    }

    private static final class AwaitThreadEntry implements AwaitThread {
        private final Thread thread;
        private final Exchange exchange;
        private final CountDownLatch latch;
        private final long start;

        private AwaitThreadEntry(Thread thread, Exchange exchange, CountDownLatch latch) {
            this.thread = thread;
            this.exchange = exchange;
            this.latch = latch;
            this.start = System.currentTimeMillis();
        }

        @Override
        public Thread getBlockedThread() {
            return thread;
        }

        @Override
        public Exchange getExchange() {
            return exchange;
        }

        @Override
        public long getWaitDuration() {
            return System.currentTimeMillis() - start;
        }

        public CountDownLatch getLatch() {
            return latch;
        }
    }

}
