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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeExchangeException;
import org.apache.camel.Traceable;
import org.apache.camel.spi.IdAware;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.ObjectHelper;
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

    private static final String DEFAULT_KEY = "CamelThrottlerDefaultKey";

    private static final String PROPERTY_EXCHANGE_QUEUED_TIMESTAMP = "CamelThrottlerExchangeQueuedTimestamp";
    private static final String PROPERTY_EXCHANGE_STATE = "CamelThrottlerExchangeState";

    private enum State { SYNC, ASYNC, ASYNC_REJECTED }

    private final Logger log = LoggerFactory.getLogger(Throttler.class);
    private final CamelContext camelContext;
    private final ScheduledExecutorService asyncExecutor;
    private final boolean shutdownAsyncExecutor;

    private volatile long timePeriodMillis;
    private volatile long cleanPeriodMillis;
    private String id;
    private Expression maxRequestsPerPeriodExpression;
    private boolean rejectExecution;
    private boolean asyncDelayed;
    private boolean callerRunsWhenRejected = true;
    private Expression correlationExpression;
    private Map<String, ThrottlingState> states = new ConcurrentHashMap<>();

    public Throttler(final CamelContext camelContext, final Processor processor, final Expression maxRequestsPerPeriodExpression, final long timePeriodMillis,
                     final ScheduledExecutorService asyncExecutor, final boolean shutdownAsyncExecutor, final boolean rejectExecution, Expression correlation) {
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
        this.cleanPeriodMillis = timePeriodMillis * 10;
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

            String key = DEFAULT_KEY;
            if (correlationExpression != null) {
                key = correlationExpression.evaluate(exchange, String.class);
            }
            ThrottlingState throttlingState = states.computeIfAbsent(key, ThrottlingState::new);
            throttlingState.calculateAndSetMaxRequestsPerPeriod(exchange);

            ThrottlePermit permit = throttlingState.poll();

            if (permit == null) {
                if (isRejectExecution()) {
                    throw new ThrottlerRejectedExecutionException("Exceeded the max throttle rate of "
                            + throttlingState.getThrottleRate() + " within " + timePeriodMillis + "ms");
                } else {
                    // delegate to async pool
                    if (isAsyncDelayed() && !exchange.isTransacted() && state == State.SYNC) {
                        log.debug("Throttle rate exceeded but AsyncDelayed enabled, so queueing for async processing, exchangeId: {}", exchange.getExchangeId());
                        return processAsynchronously(exchange, callback, throttlingState);
                    }

                    // block waiting for a permit
                    long start = 0;
                    long elapsed = 0;
                    if (log.isTraceEnabled()) {
                        start = System.currentTimeMillis();
                    }
                    permit = throttlingState.take();
                    if (log.isTraceEnabled()) {
                        elapsed = System.currentTimeMillis() - start;
                    }
                    throttlingState.enqueue(permit, exchange);

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
                throttlingState.enqueue(permit, exchange);

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
     * Delegate blocking on the DelayQueue to an asyncExecutor. Except if the executor rejects the submission
     * and isCallerRunsWhenRejected() is enabled, then this method will delegate back to process(), but not
     * before changing the exchange state to stop any recursion.
     */
    protected boolean processAsynchronously(final Exchange exchange, final AsyncCallback callback, ThrottlingState throttlingState) {
        try {
            if (log.isTraceEnabled()) {
                exchange.setProperty(PROPERTY_EXCHANGE_QUEUED_TIMESTAMP, System.currentTimeMillis());
            }
            exchange.setProperty(PROPERTY_EXCHANGE_STATE, State.ASYNC);
            long delay = throttlingState.peek().getDelay(TimeUnit.NANOSECONDS);
            asyncExecutor.schedule(() -> process(exchange, callback), delay, TimeUnit.NANOSECONDS);
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

    @SuppressWarnings("unchecked")
    @Override
    protected void doStart() throws Exception {
        if (isAsyncDelayed()) {
            ObjectHelper.notNull(asyncExecutor, "executorService", this);
        }
        super.doStart();
    }

    @SuppressWarnings("rawtypes")
    @Override
    protected void doShutdown() throws Exception {
        if (shutdownAsyncExecutor && asyncExecutor != null) {
            camelContext.getExecutorServiceManager().shutdownNow(asyncExecutor);
        }
        states.clear();
        super.doShutdown();
    }

    private class ThrottlingState {
        private final String key;
        private final DelayQueue<ThrottlePermit> delayQueue = new DelayQueue<>();
        private final AtomicReference<ScheduledFuture<?>> cleanFuture = new AtomicReference<>();
        private volatile int throttleRate;

        ThrottlingState(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }

        public int getThrottleRate() {
            return throttleRate;
        }

        public ThrottlePermit poll() {
            return delayQueue.poll();
        }

        public ThrottlePermit peek() {
            return delayQueue.peek();
        }

        public ThrottlePermit take() throws InterruptedException {
            return delayQueue.take();
        }

        public void clean() {
            states.remove(key);
        }

        /**
         * Returns a permit to the DelayQueue, first resetting it's delay to be relative to now.
         */
        public void enqueue(final ThrottlePermit permit, final Exchange exchange) {
            permit.setDelayMs(getTimePeriodMillis());
            delayQueue.put(permit);
            try {
                ScheduledFuture<?> next = asyncExecutor.schedule(this::clean, cleanPeriodMillis, TimeUnit.MILLISECONDS);
                ScheduledFuture<?> prev = cleanFuture.getAndSet(next);
                if (prev != null) {
                    prev.cancel(false);
                }
                // try and incur the least amount of overhead while releasing permits back to the queue
                if (log.isTraceEnabled()) {
                    log.trace("Permit released, for exchangeId: {}", exchange.getExchangeId());
                }
            } catch (RejectedExecutionException e) {
                log.debug("Throttling queue cleaning rejected", e);
            }
        }

        /**
         * Evaluates the maxRequestsPerPeriodExpression and adjusts the throttle rate up or down.
         */
        public synchronized void calculateAndSetMaxRequestsPerPeriod(final Exchange exchange) throws Exception {
            Integer newThrottle = maxRequestsPerPeriodExpression.evaluate(exchange, Integer.class);

            if (newThrottle != null && newThrottle < 0) {
                throw new IllegalStateException("The maximumRequestsPerPeriod must be a positive number, was: " + newThrottle);
            }

            if (newThrottle == null && throttleRate == 0) {
                throw new RuntimeExchangeException("The maxRequestsPerPeriodExpression was evaluated as null: " + maxRequestsPerPeriodExpression, exchange);
            }

            if (newThrottle != null) {
                if (newThrottle != throttleRate) {
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
        return states.values().stream().mapToInt(ThrottlingState::getThrottleRate).max().orElse(0);
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
