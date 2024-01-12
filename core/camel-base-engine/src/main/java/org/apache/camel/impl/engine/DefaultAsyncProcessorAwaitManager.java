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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.StaticService;
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

public class DefaultAsyncProcessorAwaitManager extends ServiceSupport implements AsyncProcessorAwaitManager, StaticService {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultAsyncProcessorAwaitManager.class);

    private final AsyncProcessorAwaitManager.Statistics statistics = new UtilizationStatistics();
    private final AtomicLong blockedCounter = new AtomicLong();
    private final AtomicLong interruptedCounter = new AtomicLong();
    private final AtomicLong totalDuration = new AtomicLong();
    private final AtomicLong minDuration = new AtomicLong();
    private final AtomicLong maxDuration = new AtomicLong();
    private final AtomicLong meanDuration = new AtomicLong();

    private final Map<Exchange, AwaitThread> inflight = new ConcurrentHashMap<>();
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
     * <p/>
     * <b>Important:</b> This method is discouraged to be used, as its better to invoke the asynchronous
     * {@link AsyncProcessor#process(org.apache.camel.Exchange, org.apache.camel.AsyncCallback)} method, whenever
     * possible.
     *
     * @param processor the processor
     * @param exchange  the exchange
     */
    @Override
    public void process(final AsyncProcessor processor, final Exchange exchange) {
        CountDownLatch latch = new CountDownLatch(1);
        processor.process(exchange, doneSync -> countDown(exchange, latch));
        if (latch.getCount() > 0) {
            await(exchange, latch);
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
        try {
            if (statistics.isStatisticsEnabled()) {
                blockedCounter.incrementAndGet();
            }
            inflight.put(exchange, new AwaitThreadEntry(Thread.currentThread(), exchange, latch));
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
            AwaitThread thread = inflight.remove(exchange);

            if (statistics.isStatisticsEnabled() && thread != null) {
                long time = thread.getWaitDuration();
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
        return Collections.unmodifiableCollection(inflight.values());
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
        AwaitThreadEntry entry = (AwaitThreadEntry) inflight.get(exchange);
        if (entry != null) {
            try {
                StringBuilder sb = new StringBuilder();
                sb.append(
                        "Interrupted while waiting for asynchronous callback, will release the following blocked thread which was waiting for exchange to finish processing with exchangeId: ");
                sb.append(exchange.getExchangeId());
                sb.append("\n");

                sb.append(dumpBlockedThread(entry));

                // dump a route stack trace of the exchange
                String routeStackTrace = MessageHelper.dumpMessageHistoryStacktrace(exchange, exchangeFormatter, false);
                if (routeStackTrace != null) {
                    sb.append(routeStackTrace);
                }
                LOG.warn(sb.toString());

            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            } finally {
                if (statistics.isStatisticsEnabled()) {
                    interruptedCounter.incrementAndGet();
                }
                exchange.setException(new RejectedExecutionException(
                        "Interrupted while waiting for asynchronous callback for exchangeId: " + exchange.getExchangeId()));
                exchange.getExchangeExtension().setInterrupted(true);
                entry.getLatch().countDown();
            }
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

            StringBuilder sb = new StringBuilder();
            for (AwaitThread entry : threads) {
                sb.append(dumpBlockedThread(entry));
            }

            if (isInterruptThreadsWhileStopping()) {
                LOG.warn("The following threads are blocked and will be interrupted so the threads are released:\n{}", sb);
                for (AwaitThread entry : threads) {
                    try {
                        interrupt(entry.getExchange());
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

        inflight.clear();
    }

    private static String dumpBlockedThread(AwaitThread entry) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("Blocked Thread\n");
        sb.append(
                "---------------------------------------------------------------------------------------------------------------------------------------\n");

        sb.append(style("Id:")).append(entry.getBlockedThread().getId()).append("\n");
        sb.append(style("Name:")).append(entry.getBlockedThread().getName()).append("\n");
        sb.append(style("RouteId:")).append(safeNull(entry.getRouteId())).append("\n");
        sb.append(style("NodeId:")).append(safeNull(entry.getNodeId())).append("\n");
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

        public CountDownLatch getLatch() {
            return latch;
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
