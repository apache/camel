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
package org.apache.camel.processor;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeExchangeException;
import org.apache.camel.Traceable;
import org.apache.camel.spi.IdAware;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.LRUCache;
import org.apache.camel.util.LRUCacheFactory;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A <a href="http://camel.apache.org/throttler.html">Throttler</a>
 * will set a limit on the maximum number of message exchanges which can be sent
 * to a processor within a specific time period. <p/> This pattern can be
 * extremely useful if you have some external system which meters access; such
 * as only allowing 100 requests per second; or if huge load can cause a
 * particular system to malfunction or to reduce its throughput you might want
 * to introduce some throttling.
 *
 * This throttle implementation is thread-safe and is therefore safe to be used
 * by multiple concurrent threads in a single route.
 *
 * The throttling mechanism is a DelayQueue with maxRequestsPerPeriod permits on
 * it. Each permit is set to be delayed by timePeriodMillis (except when the
 * throttler is initialized or the throttle rate increased, then there is no delay
 * for those permits). Callers trying to acquire a permit from the DelayQueue will
 * block if necessary. The end result is a rolling window of time. Where from the
 * callers point of view in the last timePeriodMillis no more than
 * maxRequestsPerPeriod have been allowed to be acquired.
 *
 * @version
 */
public class Throttler extends DelegateAsyncProcessor implements Traceable, IdAware {

    private static final String PROPERTY_EXCHANGE_QUEUED_TIMESTAMP = "CamelThrottlerExchangeQueuedTimestamp";
    private static final String PROPERTY_EXCHANGE_STATE = "CamelThrottlerExchangeState";

    private enum State { SYNC, ASYNC, ASYNC_REJECTED }

    private final Logger log = LoggerFactory.getLogger(Throttler.class);
    private final CamelContext camelContext;
    private final ExecutorService asyncExecutor;
    private final boolean shutdownAsyncExecutor;

    private volatile long timePeriodMillis;
    private String id;
    private volatile Integer throttleRate = new Integer(0);
    private Expression maxRequestsPerPeriodExpression;
    private boolean rejectExecution;
    private boolean asyncDelayed;
    private boolean callerRunsWhenRejected = true;
    private final DelayQueue<ThrottlePermit> delayQueue = new DelayQueue<>();
    // below 3 fields added for (throttling grouping)
    private Expression correlationExpression;
    private Map<Integer, DelayQueue<ThrottlePermit>> delayQueueCache;
    private Map<Integer, Integer> throttleRatesMap = new HashMap<>();    

    public Throttler(final CamelContext camelContext, final Processor processor, final Expression maxRequestsPerPeriodExpression, final long timePeriodMillis,
                     final ExecutorService asyncExecutor, final boolean shutdownAsyncExecutor, final boolean rejectExecution, Expression correlation) {
        super(processor);
        this.camelContext = camelContext;
        this.rejectExecution = rejectExecution;
        this.shutdownAsyncExecutor = shutdownAsyncExecutor;

        ObjectHelper.notNull(maxRequestsPerPeriodExpression, "maxRequestsPerPeriodExpression");
        this.maxRequestsPerPeriodExpression = maxRequestsPerPeriodExpression;

        if (timePeriodMillis <= 0) {
            throw new IllegalArgumentException("TimePeriodMillis should be a positive number, was: " + timePeriodMillis);
        }
        this.timePeriodMillis = timePeriodMillis;
        this.asyncExecutor = asyncExecutor;
        this.correlationExpression = correlation;
    }

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        long queuedStart = 0;
        if (log.isTraceEnabled()) {
            queuedStart = exchange.getProperty(PROPERTY_EXCHANGE_QUEUED_TIMESTAMP, 0L, Long.class);
            exchange.removeProperty(PROPERTY_EXCHANGE_QUEUED_TIMESTAMP);
        }
        State state = exchange.getProperty(PROPERTY_EXCHANGE_STATE, State.SYNC, State.class);
        exchange.removeProperty(PROPERTY_EXCHANGE_STATE);
        boolean doneSync = state == State.SYNC || state == State.ASYNC_REJECTED;

        try {
            if (!isRunAllowed()) {
                throw new RejectedExecutionException("Run is not allowed");
            }

            DelayQueue<ThrottlePermit> delayQ = null;
            Integer key = null;
            if (correlationExpression != null) {
                key = correlationExpression.evaluate(exchange, Integer.class);
                delayQ = locateDelayQueue(key, doneSync);
                calculateAndSetMaxRequestsPerPeriod(delayQ, exchange, key);
            } else {
                delayQ = delayQueue;
                calculateAndSetMaxRequestsPerPeriod(exchange);
            }

            ThrottlePermit permit = delayQ.poll();

            if (permit == null) {
                if (isRejectExecution()) {
                    throw new ThrottlerRejectedExecutionException("Exceeded the max throttle rate of "
                            + ((correlationExpression != null) ? throttleRatesMap.get(key) : throttleRate) + " within " + timePeriodMillis + "ms");
                } else {
                    // delegate to async pool
                    if (isAsyncDelayed() && !exchange.isTransacted() && state == State.SYNC) {
                        log.debug("Throttle rate exceeded but AsyncDelayed enabled, so queueing for async processing, exchangeId: {}", exchange.getExchangeId());
                        return processAsynchronously(exchange, callback);
                    }

                    // block waiting for a permit
                    long start = 0;
                    long elapsed = 0;
                    if (log.isTraceEnabled()) {
                        start = System.currentTimeMillis();
                    }
                    permit = delayQ.take();
                    if (log.isTraceEnabled()) {
                        elapsed = System.currentTimeMillis() - start;
                    }
                    enqueuePermit(permit, exchange, delayQ);

                    if (state == State.ASYNC) {
                        if (log.isTraceEnabled()) {
                            long queuedTime = start - queuedStart;
                            log.trace("Queued for {}ms, Throttled for {}ms, exchangeId: {}", queuedTime, elapsed, exchange.getExchangeId());
                        }
                    } else {
                        log.trace("Throttled for {}ms, exchangeId: {}", elapsed, exchange.getExchangeId());
                    }
                }
            } else {
                enqueuePermit(permit, exchange, delayQ);

                if (state == State.ASYNC) {
                    if (log.isTraceEnabled()) {
                        long queuedTime = System.currentTimeMillis() - queuedStart;
                        log.trace("Queued for {}ms, No throttling applied (throttle cleared while queued), for exchangeId: {}", queuedTime, exchange.getExchangeId());
                    }
                } else {
                    log.trace("No throttling applied to exchangeId: {}", exchange.getExchangeId());
                }
            }

            if (processor != null) {
                if (doneSync) {
                    return processor.process(exchange, callback);
                } else {
                    // if we are executing async, then we have to call the nested processor synchronously, and we
                    // must not share our AsyncCallback, because the nested processing has no way of knowing that
                    // we are already executing asynchronously.
                    AsyncProcessorHelper.process(processor, exchange);
                }
            }

            callback.done(doneSync);
            return doneSync;

        } catch (final InterruptedException e) {
            // determine if we can still run, or the camel context is forcing a shutdown
            boolean forceShutdown = exchange.getContext().getShutdownStrategy().forceShutdown(this);
            if (forceShutdown) {
                String msg = "Run not allowed as ShutdownStrategy is forcing shutting down, will reject executing exchange: " + exchange;
                log.debug(msg);
                exchange.setException(new RejectedExecutionException(msg, e));
            } else {
                exchange.setException(e);
            }
            callback.done(doneSync);
            return doneSync;
        } catch (final Throwable t) {
            exchange.setException(t);
            callback.done(doneSync);
            return doneSync;
        }
    }

    /**
     * 
     * Finds the right Delay Queue to put a permit into with the exchanges time arrival timestamp +  timePeriodInMillis 
     * In case of asynchronous routing there may be cases where we create new group whose correlationExpression 
     * might first hit after long series of exchanges with a different correlationExpression and are to be on hold in 
     * their delayQueue so we need to find delay queue to add new ones while we create a new empty delay
     * queue for the new group hit for the first time. that's why locating delay queues for those frequently
     * hitting exchanges for the group during asynchronous routing would be better be asynchronous with asyncExecutor 
     * 
     * @param key is evaluated value of correlationExpression
     * @param doneSync is a flag indicating if the exchange is routed asynchronously or not
     * @return DelayQueue in which the exchange with permit expiry to be put into
     */
    private DelayQueue<ThrottlePermit> locateDelayQueue(final Integer key, final boolean doneSync) throws InterruptedException, ExecutionException {        
        CompletableFuture<DelayQueue<ThrottlePermit>> futureDelayQueue = new CompletableFuture<>();

        if (!doneSync) {
            asyncExecutor.submit(() -> {
                futureDelayQueue.complete(findDelayQueue(key));
            });
        }
        DelayQueue<ThrottlePermit> currentQueue = (!doneSync) ? futureDelayQueue.get() : findDelayQueue(key);   
        return currentQueue;
    }

    private DelayQueue<ThrottlePermit> findDelayQueue(Integer key) {
        DelayQueue<ThrottlePermit> currentDelayQueue = delayQueueCache.get(key);
        if (currentDelayQueue == null) {
            currentDelayQueue = new DelayQueue<>();
            throttleRatesMap.put(key, 0);
            delayQueueCache.put(key, currentDelayQueue);
        }
        return currentDelayQueue;
    }

    /**
     * Delegate blocking on the DelayQueue to an asyncExecutor. Except if the executor rejects the submission
     * and isCallerRunsWhenRejected() is enabled, then this method will delegate back to process(), but not
     * before changing the exchange state to stop any recursion.
     */
    protected boolean processAsynchronously(final Exchange exchange, final AsyncCallback callback) {
        try {
            if (log.isTraceEnabled()) {
                exchange.setProperty(PROPERTY_EXCHANGE_QUEUED_TIMESTAMP, System.currentTimeMillis());
            }
            exchange.setProperty(PROPERTY_EXCHANGE_STATE, State.ASYNC);
            asyncExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    process(exchange, callback);
                }
            });
            return false;
        } catch (final RejectedExecutionException e) {
            if (isCallerRunsWhenRejected()) {
                log.debug("AsyncExecutor is full, rejected exchange will run in the current thread, exchangeId: {}", exchange.getExchangeId());
                exchange.setProperty(PROPERTY_EXCHANGE_STATE, State.ASYNC_REJECTED);
                return process(exchange, callback);
            }
            throw e;
        }
    }

    /**
     * Returns a permit to the DelayQueue, first resetting it's delay to be relative to now.
     * @throws ExecutionException 
     * @throws InterruptedException 
     */
    protected void enqueuePermit(final ThrottlePermit permit, final Exchange exchange, DelayQueue<ThrottlePermit> delayQueue) throws InterruptedException, ExecutionException {
        permit.setDelayMs(getTimePeriodMillis());
        delayQueue.put(permit);
        // try and incur the least amount of overhead while releasing permits back to the queue
        if (log.isTraceEnabled()) {
            log.trace("Permit released, for exchangeId: {}", exchange.getExchangeId());
        }
    }

    /**
     * Evaluates the maxRequestsPerPeriodExpression and adjusts the throttle rate up or down.
     */
    protected void calculateAndSetMaxRequestsPerPeriod(final Exchange exchange) throws Exception {
        Integer newThrottle = maxRequestsPerPeriodExpression.evaluate(exchange, Integer.class);

        if (newThrottle != null && newThrottle < 0) {
            throw new IllegalStateException("The maximumRequestsPerPeriod must be a positive number, was: " + newThrottle);
        }

        synchronized (this) {
            if (newThrottle == null && throttleRate == 0) {
                throw new RuntimeExchangeException("The maxRequestsPerPeriodExpression was evaluated as null: " + maxRequestsPerPeriodExpression, exchange);
            }

            if (newThrottle != null) {
                if (newThrottle != throttleRate) {
                    // get the queue from the cache
                    // decrease
                    if (throttleRate > newThrottle) {
                        int delta = throttleRate - newThrottle;

                        // discard any permits that are needed to decrease throttling
                        while (delta > 0) {
                            delayQueue.take();
                            delta--;
                            log.trace("Permit discarded due to throttling rate decrease, triggered by ExchangeId: {}", exchange.getExchangeId());
                        }
                        log.debug("Throttle rate decreased from {} to {}, triggered by ExchangeId: {}", throttleRate, newThrottle, exchange.getExchangeId());

                    // increase
                    } else if (newThrottle > throttleRate) {
                        int delta = newThrottle - throttleRate;
                        for (int i = 0; i < delta; i++) {
                            delayQueue.put(new ThrottlePermit(-1));
                        }
                        if (throttleRate == 0) {
                            log.debug("Initial throttle rate set to {}, triggered by ExchangeId: {}", newThrottle, exchange.getExchangeId());
                        } else {
                            log.debug("Throttle rate increase from {} to {}, triggered by ExchangeId: {}", throttleRate, newThrottle, exchange.getExchangeId());
                        }
                    }
                    throttleRate = newThrottle;
                }
            }
        }
    }
    
    /**
     * Evaluates the maxRequestsPerPeriodExpression and adjusts the throttle rate up or down.
     */
    protected void calculateAndSetMaxRequestsPerPeriod(DelayQueue<ThrottlePermit> delayQueue, final Exchange exchange, final Integer key) throws Exception {
        Integer newThrottle = maxRequestsPerPeriodExpression.evaluate(exchange, Integer.class);

        if (newThrottle != null && newThrottle < 0) {
            throw new IllegalStateException("The maximumRequestsPerPeriod must be a positive number, was: " + newThrottle);
        }

        synchronized (key) {
            if (newThrottle == null && throttleRatesMap.get(key) == 0) {
                throw new RuntimeExchangeException("The maxRequestsPerPeriodExpression was evaluated as null: " + maxRequestsPerPeriodExpression, exchange);
            }

            if (newThrottle != null) {
                if (newThrottle != throttleRatesMap.get(key)) {
                    // get the queue from the cache
                    // decrease
                    if (throttleRatesMap.get(key) > newThrottle) {
                        int delta = throttleRatesMap.get(key) - newThrottle;

                        // discard any permits that are needed to decrease throttling
                        while (delta > 0) {
                            delayQueue.take();
                            delta--;
                            log.trace("Permit discarded due to throttling rate decrease, triggered by ExchangeId: {}", exchange.getExchangeId());
                        }
                        log.debug("Throttle rate decreased from {} to {}, triggered by ExchangeId: {}", throttleRatesMap.get(key), newThrottle, exchange.getExchangeId());

                    // increase
                    } else if (newThrottle > throttleRatesMap.get(key)) {
                        int delta = newThrottle - throttleRatesMap.get(key);
                        for (int i = 0; i < delta; i++) {
                            delayQueue.put(new ThrottlePermit(-1));
                        }
                        if (throttleRatesMap.get(key) == 0) {
                            log.debug("Initial throttle rate set to {}, triggered by ExchangeId: {}", newThrottle, exchange.getExchangeId());
                        } else {
                            log.debug("Throttle rate increase from {} to {}, triggered by ExchangeId: {}", throttleRatesMap.get(key), newThrottle, exchange.getExchangeId());
                        }
                    }
                    throttleRatesMap.put(key, newThrottle);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void doStart() throws Exception {
        if (isAsyncDelayed()) {
            ObjectHelper.notNull(asyncExecutor, "executorService", this);
        }
        if (correlationExpression != null) {
            if (camelContext != null) {
                int maxSize = CamelContextHelper.getMaximumSimpleCacheSize(camelContext);
                if (maxSize > 0) {
                    delayQueueCache = LRUCacheFactory.newLRUCache(16, maxSize, false);
                    log.debug("DelayQueues cache size: {}", maxSize);
                } else {
                    delayQueueCache = LRUCacheFactory.newLRUCache(100);
                    log.debug("Defaulting DelayQueues cache size: {}", 100);
                }
            }
            if (delayQueueCache != null) {
                ServiceHelper.startService(delayQueueCache);
            }
        }
        super.doStart();
    }

    @SuppressWarnings("rawtypes")
    @Override
    protected void doShutdown() throws Exception {
        if (shutdownAsyncExecutor && asyncExecutor != null) {
            camelContext.getExecutorServiceManager().shutdownNow(asyncExecutor);
        }
        if (correlationExpression != null) {
            if (delayQueueCache != null) {
                ServiceHelper.stopService(delayQueueCache);
                if (log.isDebugEnabled()) {
                    if (delayQueueCache instanceof LRUCache) {
                        log.debug("Clearing deleay queues cache[size={}, hits={}, misses={}, evicted={}]",
                                delayQueueCache.size(), ((LRUCache) delayQueueCache).getHits(), ((LRUCache) delayQueueCache).getMisses(), ((LRUCache) delayQueueCache).getEvicted());
                    }
                }
                delayQueueCache.clear();
            }
            if (throttleRatesMap != null && throttleRatesMap.size() > 0) {
                throttleRatesMap.clear();
            }
        }
        super.doShutdown();
    }

    /**
     * Permit that implements the Delayed interface needed by DelayQueue.
     */
    private class ThrottlePermit implements Delayed {
        private volatile long scheduledTime;

        ThrottlePermit(final long delayMs) {
            setDelayMs(delayMs);
        }

        public void setDelayMs(final long delayMs) {
            this.scheduledTime = System.currentTimeMillis() + delayMs;
        }

        @Override
        public long getDelay(final TimeUnit unit) {
            return unit.convert(scheduledTime - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(final Delayed o) {
            return (int)(getDelay(TimeUnit.MILLISECONDS) - o.getDelay(TimeUnit.MILLISECONDS));
        }
    }

    public boolean isRejectExecution() {
        return rejectExecution;
    }

    public void setRejectExecution(boolean rejectExecution) {
        this.rejectExecution = rejectExecution;
    }

    public boolean isAsyncDelayed() {
        return asyncDelayed;
    }

    public void setAsyncDelayed(boolean asyncDelayed) {
        this.asyncDelayed = asyncDelayed;
    }

    public boolean isCallerRunsWhenRejected() {
        return callerRunsWhenRejected;
    }

    public void setCallerRunsWhenRejected(boolean callerRunsWhenRejected) {
        this.callerRunsWhenRejected = callerRunsWhenRejected;
    }

    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    /**
     * Sets the maximum number of requests per time period expression
     */
    public void setMaximumRequestsPerPeriodExpression(Expression maxRequestsPerPeriodExpression) {
        this.maxRequestsPerPeriodExpression = maxRequestsPerPeriodExpression;
    }

    public Expression getMaximumRequestsPerPeriodExpression() {
        return maxRequestsPerPeriodExpression;
    }

    /**
     * Gets the current maximum request per period value.
     * If it is grouped throttling applied with correlationExpression 
     * than the max per period within the group will return
     */
    public int getCurrentMaximumRequestsPerPeriod() {
        if (correlationExpression == null) {
            return throttleRate;
        }
        return Collections.max(throttleRatesMap.entrySet(), (entry1, entry2) -> entry1.getValue() - entry2.getValue()).getValue();
    }

    /**
     * Sets the time period during which the maximum number of requests apply
     */
    public void setTimePeriodMillis(final long timePeriodMillis) {
        this.timePeriodMillis = timePeriodMillis;
    }

    public long getTimePeriodMillis() {
        return timePeriodMillis;
    }

    public String getTraceLabel() {
        return "throttle[" + maxRequestsPerPeriodExpression + " per: " + timePeriodMillis + "]";
    }

    @Override
    public String toString() {
        return "Throttler[requests: " + maxRequestsPerPeriodExpression + " per: " + timePeriodMillis + " (ms) to: "
                + getProcessor() + "]";
    }
}
