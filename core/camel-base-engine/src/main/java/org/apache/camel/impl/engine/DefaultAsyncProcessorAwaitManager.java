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
package org.apache.camel.impl.engine;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.AsyncProcessorAwaitManager;
import org.apache.camel.spi.ExchangeFormatter;
import org.apache.camel.spi.ReactiveExecutor;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.support.processor.DefaultExchangeFormatter;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultAsyncProcessorAwaitManager extends ServiceSupport implements AsyncProcessorAwaitManager {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultAsyncProcessorAwaitManager.class);
    private static final CountDownLatch DONE = new CountDownLatch(0);

    private final AsyncProcessorAwaitManager.Statistics statistics = new UtilizationStatistics();
    private final AtomicLong blockedCounter = new AtomicLong();
    private final AtomicLong interruptedCounter = new AtomicLong();
    private final AtomicLong totalDuration = new AtomicLong();
    private final AtomicLong minDuration = new AtomicLong();
    private final AtomicLong maxDuration = new AtomicLong();
    private final AtomicLong meanDuration = new AtomicLong();

    // one entry per waiting thread: two threads can wait for the same exchange (a thread that waits for an exchange while
    // the exchange goes on, on another thread, through a step that waits for it too), and each must stay listed
    private final Set<AwaitThreadEntry> inflight = ConcurrentHashMap.newKeySet();
    private final ExchangeFormatter exchangeFormatter;
    private boolean interruptThreadsWhileStopping = true;

    public DefaultAsyncProcessorAwaitManager() {
        // setup exchange formatter to be used for message history dump
        DefaultExchangeFormatter formatter = new DefaultExchangeFormatter();
        formatter.setShowExchangeId(true);
        formatter.setMultiline(true);
        formatter.setShowHeaders(true);
        formatter.setStyle(DefaultExchangeFormatter.OutputStyle.Fixed);
        this.exchangeFormatter = formatter;
    }

    /**
     * Calls the async version of the processor's process method and waits for it to complete before returning. This can
     * be used by {@link AsyncProcessor} objects to implement their sync version of the process method.
     * <p>
     * <b>Important:</b> This method is discouraged to be used, as it is better to invoke the asynchronous
     * {@link AsyncProcessor#process(org.apache.camel.Exchange, org.apache.camel.AsyncCallback)} method, whenever
     * possible.
     *
     * @param processor the processor
     * @param exchange  the exchange
     */
    @Override
    public void process(final AsyncProcessor processor, final Exchange exchange) {
        AwaitCallback callback = new AwaitCallback(exchange);
        processor.process(exchange, callback);
        // the callback has usually fired by now, as the reactive executor runs the work in this thread
        if (callback.get() == null) {
            CountDownLatch latch = new CountDownLatch(1);
            if (callback.compareAndSet(null, latch)) {
                await(exchange, latch);
            }
        }
    }

    public void await(Exchange exchange, CountDownLatch latch) {
        ReactiveExecutor reactiveExecutor = exchange.getContext().getCamelContextExtension().getReactiveExecutor();
        // Early exit for pending reactive queued work
        do {
            if (latch.getCount() <= 0) {
                return;
            }
        } while (reactiveExecutor.executeFromQueue());

        if (LOG.isTraceEnabled()) {
            LOG.trace("Waiting for asynchronous callback before continuing for exchangeId: {} -> {}",
                    exchange.getExchangeId(), exchange);
        }
        AwaitThreadEntry entry = new AwaitThreadEntry(Thread.currentThread(), exchange, latch);
        try {
            if (statistics.isStatisticsEnabled()) {
                blockedCounter.incrementAndGet();
            }
            inflight.add(entry);
            latch.await();
            if (LOG.isTraceEnabled()) {
                LOG.trace("Asynchronous callback received, will continue routing exchangeId: {} -> {}",
                        exchange.getExchangeId(), exchange);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            if (LOG.isTraceEnabled()) {
                LOG.trace("Interrupted while waiting for callback, will continue routing exchangeId: {} -> {}",
                        exchange.getExchangeId(), exchange);
            }
            exchange.setException(e);
        } finally {
            // from here on the exchange is the caller's: an interrupt that comes later leaves it alone
            entry.finish();
            boolean removed = inflight.remove(entry);

            if (statistics.isStatisticsEnabled() && removed) {
                long time = entry.getWaitDuration();
                long total = totalDuration.get() + time;
                totalDuration.set(total);

                if (time < minDuration.get()) {
                    minDuration.set(time);
                } else if (time > maxDuration.get()) {
                    maxDuration.set(time);
                }

                // update mean
                long count = blockedCounter.get();
                long mean = count > 0 ? total / count : 0;
                meanDuration.set(mean);
            }
        }
    }

    /**
     * The value is null until either the callback fires, which sets {@link #DONE}, or the waiting thread gives it the
     * latch to count down. Most calls complete without waiting, so this avoids creating the latch for them.
     */
    private final class AwaitCallback extends AtomicReference<CountDownLatch> implements AsyncCallback {
        private final transient Exchange exchange;

        private AwaitCallback(Exchange exchange) {
            this.exchange = exchange;
        }

        @Override
        public void done(boolean doneSync) {
            CountDownLatch latch = getAndSet(DONE);
            if (latch != null) {
                countDown(exchange, latch);
            }
        }
    }

    public void countDown(Exchange exchange, CountDownLatch latch) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Asynchronous callback received for exchangeId: {}", exchange.getExchangeId());
        }
        latch.countDown();
    }

    @Override
    public int size() {
        return inflight.size();
    }

    @Override
    public Collection<AwaitThread> browse() {
        return Collections.unmodifiableCollection(inflight);
    }

    @Override
    public void interrupt(String exchangeId) {
        // need to find the exchange with the given exchange id
        Exchange found = null;
        for (AsyncProcessorAwaitManager.AwaitThread entry : browse()) {
            Exchange exchange = entry.getExchange();
            if (exchangeId.equals(exchange.getExchangeId())) {
                found = exchange;
                break;
            }
        }

        if (found != null) {
            interrupt(found);
        }
    }

    @Override
    public void interrupt(Exchange exchange) {
        // every thread that waits for the exchange
        for (AwaitThreadEntry entry : inflight) {
            if (entry.getExchange() == exchange) {
                interrupt(entry);
            }
        }
    }

    private void interrupt(AwaitThreadEntry entry) {
        Exchange exchange = entry.getExchange();
        String dump = null;
        RuntimeException failure = null;
        try {
            StringBuilder sb = new StringBuilder(512);
            sb.append(
                    "Interrupted while waiting for asynchronous callback, will release the following blocked thread which was waiting for exchange to finish processing with exchangeId: ");
            sb.append(exchange.getExchangeId());
            sb.append("\n");

            sb.append(dumpBlockedThread(entry));

            // dump a route stack trace of the exchange
            String routeStackTrace = MessageHelper.dumpMessageHistoryStacktrace(exchange, exchangeFormatter, false);
            sb.append(routeStackTrace);
            dump = sb.toString();
        } catch (Exception e) {
            failure = RuntimeCamelException.wrapRuntimeCamelException(e);
        }
        // the callback may have fired while the dump was made, and the waiting thread may have returned the exchange
        // to its caller as done: the exchange is then left alone
        if (entry.interrupt()) {
            if (statistics.isStatisticsEnabled()) {
                interruptedCounter.incrementAndGet();
            }
            if (dump != null) {
                LOG.warn(dump);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public boolean isInterruptThreadsWhileStopping() {
        return interruptThreadsWhileStopping;
    }

    @Override
    public void setInterruptThreadsWhileStopping(boolean interruptThreadsWhileStopping) {
        this.interruptThreadsWhileStopping = interruptThreadsWhileStopping;
    }

    @Override
    public Statistics getStatistics() {
        return statistics;
    }

    @Override
    protected void doStop() throws Exception {
        Collection<AwaitThread> threads = browse();
        int count = threads.size();
        if (count > 0) {
            LOG.warn("Shutting down while there are still {} inflight threads currently blocked.", count);

            StringBuilder sb = new StringBuilder(1024);
            for (AwaitThread entry : threads) {
                sb.append(dumpBlockedThread(entry));
            }

            if (isInterruptThreadsWhileStopping()) {
                LOG.warn("The following threads are blocked and will be interrupted so the threads are released:\n{}", sb);
                for (AwaitThread entry : threads) {
                    try {
                        interrupt((AwaitThreadEntry) entry);
                    } catch (Exception e) {
                        LOG.warn("Error while interrupting thread: {}. This exception is ignored.",
                                entry.getBlockedThread().getName(),
                                e);
                    }
                }
            } else {
                LOG.warn("The following threads are blocked, and may reside in the JVM:\n{}", sb);
            }
        } else {
            LOG.debug("Shutting down with no inflight threads.");
        }
        // the entries are not cleared: each thread removes its own once it returns, so a thread that is still blocked
        // (not interrupted, or blocked since the threads were listed above) stays listed, and can be interrupted
    }

    private static String dumpBlockedThread(AwaitThread entry) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("\n");
        sb.append("Blocked Thread\n");
        sb.append(
                "---------------------------------------------------------------------------------------------------------------------------------------\n");

        sb.append(style("Id:")).append(entry.getBlockedThread().threadId()).append("\n");
        sb.append(style("Name:")).append(entry.getBlockedThread().getName()).append("\n");
        sb.append(style("RouteId:")).append(safeNull(entry.getRouteId())).append("\n");
        sb.append(style("NodeId:")).append(safeNull(entry.getNodeId())).append("\n");
        if (entry.getNodeSource() != null) {
            sb.append(style("Source:")).append(entry.getNodeSource()).append("\n");
        }
        sb.append(style("Duration:")).append(entry.getWaitDuration()).append(" msec.\n");
        return sb.toString();
    }

    private static String style(String label) {
        return String.format("\t%-20s", label);
    }

    private static String safeNull(Object value) {
        return value != null ? value.toString() : "";
    }

    private static final class AwaitThreadEntry implements AwaitThread {
        private final Thread thread;
        private final Exchange exchange;
        private final CountDownLatch latch;
        private final StopWatch watch = new StopWatch();
        // guarded by this: the waiting thread returned (finish), or interrupt released it
        private boolean finished;

        private AwaitThreadEntry(Thread thread, Exchange exchange, CountDownLatch latch) {
            this.thread = thread;
            this.exchange = exchange;
            this.latch = latch;
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
            return watch.taken();
        }

        @Override
        public String getRouteId() {
            return ExchangeHelper.getAtRouteId(exchange);
        }

        @Override
        public String getNodeId() {
            return exchange.getExchangeExtension().getHistoryNodeId();
        }

        @Override
        public String getNodeSource() {
            return exchange.getExchangeExtension().getHistoryNodeSource();
        }

        public CountDownLatch getLatch() {
            return latch;
        }

        /**
         * Fails the exchange as interrupted and releases the waiting thread, unless the callback already fired or the
         * thread returned. The waiting thread sees the exception, as it returns only after it called {@link #finish()}.
         *
         * @return whether the exchange was failed and the thread released
         */
        synchronized boolean interrupt() {
            if (finished || latch.getCount() == 0) {
                return false;
            }
            finished = true;
            exchange.setException(new RejectedExecutionException(
                    "Interrupted while waiting for asynchronous callback for exchangeId: " + exchange.getExchangeId()));
            exchange.getExchangeExtension().setInterrupted(true);
            latch.countDown();
            return true;
        }

        /** Called by the waiting thread before it returns: an interrupt after this leaves the exchange alone. */
        synchronized void finish() {
            finished = true;
        }

        @Override
        public String toString() {
            return "AwaitThreadEntry[name=" + thread.getName() + ", exchangeId=" + exchange.getExchangeId() + "]";
        }
    }

    /**
     * Represents utilization statistics
     */
    private final class UtilizationStatistics implements AsyncProcessorAwaitManager.Statistics {

        private boolean statisticsEnabled;

        @Override
        public long getThreadsBlocked() {
            return blockedCounter.get();
        }

        @Override
        public long getThreadsInterrupted() {
            return interruptedCounter.get();
        }

        @Override
        public long getTotalDuration() {
            return totalDuration.get();
        }

        @Override
        public long getMinDuration() {
            return minDuration.get();
        }

        @Override
        public long getMaxDuration() {
            return maxDuration.get();
        }

        @Override
        public long getMeanDuration() {
            return meanDuration.get();
        }

        @Override
        public void reset() {
            blockedCounter.set(0);
            interruptedCounter.set(0);
            totalDuration.set(0);
            minDuration.set(0);
            maxDuration.set(0);
            meanDuration.set(0);
        }

        @Override
        public boolean isStatisticsEnabled() {
            return statisticsEnabled;
        }

        @Override
        public void setStatisticsEnabled(boolean statisticsEnabled) {
            this.statisticsEnabled = statisticsEnabled;
        }

        @Override
        public String toString() {
            return String.format(
                    "AsyncProcessAwaitManager utilization[blocked=%s, interrupted=%s, total=%s min=%s, max=%s, mean=%s]",
                    getThreadsBlocked(), getThreadsInterrupted(), getTotalDuration(), getMinDuration(), getMaxDuration(),
                    getMeanDuration());
        }
    }

}
