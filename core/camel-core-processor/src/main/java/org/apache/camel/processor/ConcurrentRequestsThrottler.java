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
package org.apache.camel.processor;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.RuntimeExchangeException;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A <a href="http://camel.apache.org/throttler.html">Throttler</a> will set a limit on the maximum number of message
 * exchanges which can be sent to a processor concurrently.
 * <p/>
 * This pattern can be extremely useful if you have some external system which meters access; such as only allowing 10
 * concurrent requests; or if huge load can cause a particular system to malfunction or to reduce its throughput you
 * might want to introduce some throttling.
 *
 * This throttle implementation is thread-safe and is therefore safe to be used by multiple concurrent threads in a
 * single route.
 *
 * The throttling mechanism is a Semaphore with maxConcurrentRequests permits on it. Callers trying to acquire a permit
 * will block if necessary when maxConcurrentRequests permits have been acquired.
 */
public class ConcurrentRequestsThrottler extends AbstractThrottler {

    private static final Logger LOG = LoggerFactory.getLogger(ConcurrentRequestsThrottler.class);

    private static final String DEFAULT_KEY = "CamelThrottlerDefaultKey";

    private static final String PROPERTY_EXCHANGE_QUEUED_TIME = "CamelThrottlerExchangeQueuedTime";
    private static final String PROPERTY_EXCHANGE_STATE = "CamelThrottlerExchangeState";
    private static final long CLEAN_PERIOD = 1000L * 10;

    private enum State {
        SYNC,
        ASYNC,
        ASYNC_REJECTED
    }

    private final Map<String, ThrottlingState> states = new ConcurrentHashMap<>();

    public ConcurrentRequestsThrottler(final CamelContext camelContext, final Expression maxRequestsExpression,
                                       final ScheduledExecutorService asyncExecutor, final boolean shutdownAsyncExecutor,
                                       final boolean rejectExecution, Expression correlation) {
        super(asyncExecutor, shutdownAsyncExecutor, camelContext, rejectExecution, correlation, maxRequestsExpression);
    }

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        long queuedStart = 0;
        if (LOG.isTraceEnabled()) {
            queuedStart = exchange.getProperty(PROPERTY_EXCHANGE_QUEUED_TIME, 0L, Long.class);
            exchange.removeProperty(PROPERTY_EXCHANGE_QUEUED_TIME);
        }
        State state = exchange.getProperty(PROPERTY_EXCHANGE_STATE, State.SYNC, State.class);
        exchange.removeProperty(PROPERTY_EXCHANGE_STATE);
        boolean doneSync = state == State.SYNC || state == State.ASYNC_REJECTED;

        try {
            if (!isRunAllowed()) {
                throw new RejectedExecutionException("Run is not allowed");
            }

            return doProcess(exchange, callback, state, queuedStart, doneSync);

        } catch (final InterruptedException e) {
            return handleInterrupt(exchange, callback, e, doneSync);
        } catch (final Exception t) {
            return handleException(exchange, callback, t, doneSync);
        }
    }

    private boolean doProcess(Exchange exchange, AsyncCallback callback, State state, long queuedStart, boolean doneSync)
            throws Exception {
        String key = DEFAULT_KEY;
        if (correlationExpression != null) {
            key = correlationExpression.evaluate(exchange, String.class);
        }
        ThrottlingState throttlingState = states.computeIfAbsent(key, ThrottlingState::new);
        throttlingState.calculateAndSetMaxConcurrentRequestsExpression(exchange);

        if (!throttlingState.tryAcquire(exchange)) {
            if (isRejectExecution()) {
                throw new ThrottlerRejectedExecutionException(
                        "Exceeded the max throttle rate of " + throttlingState.getThrottleRate());
            } else {
                // delegate to async pool
                if (isAsyncDelayed() && !exchange.isTransacted() && state == State.SYNC) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(
                                "Throttle rate exceeded but AsyncDelayed enabled, so queueing for async processing, exchangeId: {}",
                                exchange.getExchangeId());
                    }
                    return processAsynchronously(exchange, callback, throttlingState);
                }

                doThrottle(exchange, throttlingState, state, queuedStart);
            }
        } else {
            // permit acquired
            if (state == State.ASYNC) {
                if (LOG.isTraceEnabled()) {
                    long queuedTime = Duration.ofNanos(System.nanoTime() - queuedStart).toMillis();
                    LOG.trace("Queued for {}ms, No throttling applied (throttle cleared while queued), for exchangeId: {}",
                            queuedTime, exchange.getExchangeId());
                }
            } else {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("No throttling applied to exchangeId: {}", exchange.getExchangeId());
                }
            }
        }

        callback.done(doneSync);
        return doneSync;
    }

    private static void doThrottle(Exchange exchange, ThrottlingState throttlingState, State state, long queuedStart)
            throws InterruptedException {
        // block waiting for a permit
        long start = 0;
        long elapsed = 0;
        if (LOG.isTraceEnabled()) {
            start = System.nanoTime();
        }
        throttlingState.acquire(exchange);
        if (LOG.isTraceEnabled()) {
            elapsed = System.nanoTime() - start;
        }
        if (state == State.ASYNC) {
            if (LOG.isTraceEnabled()) {
                long queuedTime = start - queuedStart;
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Queued for {}ms, Throttled for {}ms, exchangeId: {}", queuedTime, elapsed,
                            exchange.getExchangeId());
                }
            }
        } else {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Throttled for {}ms, exchangeId: {}", elapsed, exchange.getExchangeId());
            }
        }
    }

    /**
     * Delegate blocking to an asyncExecutor. Except if the executor rejects the submission and
     * isCallerRunsWhenRejected() is enabled, then this method will delegate back to process(), but not before changing
     * the exchange state to stop any recursion.
     */
    protected boolean processAsynchronously(
            final Exchange exchange, final AsyncCallback callback, ThrottlingState throttlingState) {
        try {
            if (LOG.isTraceEnabled()) {
                exchange.setProperty(PROPERTY_EXCHANGE_QUEUED_TIME, System.nanoTime());
            }
            exchange.setProperty(PROPERTY_EXCHANGE_STATE, State.ASYNC);
            asyncExecutor.submit(() -> process(exchange, callback));
            return false;
        } catch (final RejectedExecutionException e) {
            if (isCallerRunsWhenRejected()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("AsyncExecutor is full, rejected exchange will run in the current thread, exchangeId: {}",
                            exchange.getExchangeId());
                }
                exchange.setProperty(PROPERTY_EXCHANGE_STATE, State.ASYNC_REJECTED);
                return process(exchange, callback);
            }
            throw e;
        }
    }

    @Override
    protected void doStart() throws Exception {
        if (isAsyncDelayed()) {
            ObjectHelper.notNull(asyncExecutor, "executorService", this);
        }
    }

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
        private final AtomicReference<ScheduledFuture<?>> cleanFuture = new AtomicReference<>();
        private volatile int throttleRate;
        private WrappedSemaphore semaphore;

        ThrottlingState(String key) {
            this.key = key;
            semaphore = new WrappedSemaphore();
        }

        public int getThrottleRate() {
            return throttleRate;
        }

        public void clean() {
            states.remove(key);
        }

        public boolean tryAcquire(Exchange exchange) {
            boolean acquired = semaphore.tryAcquire();
            if (acquired) {
                addSynchronization(exchange);
            }
            return acquired;
        }

        public void acquire(Exchange exchange) throws InterruptedException {
            semaphore.acquire();
            addSynchronization(exchange);
        }

        private void addSynchronization(final Exchange exchange) {
            exchange.getExchangeExtension().addOnCompletion(new Synchronization() {
                @Override
                public void onComplete(Exchange exchange) {
                    release(exchange);
                }

                @Override
                public void onFailure(Exchange exchange) {
                    release(exchange);
                }
            });
        }

        /**
         * Returns a permit.
         */
        public void release(final Exchange exchange) {
            semaphore.release();
            try {
                ScheduledFuture<?> next = asyncExecutor.schedule(this::clean, CLEAN_PERIOD, TimeUnit.MILLISECONDS);
                ScheduledFuture<?> prev = cleanFuture.getAndSet(next);
                if (prev != null) {
                    prev.cancel(false);
                }
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Permit released, for exchangeId: {}", exchange.getExchangeId());
                }
            } catch (RejectedExecutionException e) {
                LOG.debug("Throttle cleaning rejected", e);
            }
        }

        /**
         * Evaluates the maxConcurrentRequestsExpression and adjusts the throttle rate up or down.
         */
        public synchronized void calculateAndSetMaxConcurrentRequestsExpression(final Exchange exchange) throws Exception {
            Integer newThrottle = getMaximumRequestsExpression().evaluate(exchange, Integer.class);

            if (newThrottle != null && newThrottle < 0) {
                throw new IllegalStateException("The maximumConcurrentRequests must be a positive number, was: " + newThrottle);
            }

            if (newThrottle == null && throttleRate == 0) {
                throw new RuntimeExchangeException(
                        "The maxConcurrentRequestsExpression was evaluated as null: " + getMaximumRequestsExpression(),
                        exchange);
            }

            if (newThrottle != null) {
                if (newThrottle != throttleRate) {
                    // decrease
                    if (throttleRate > newThrottle) {
                        int delta = throttleRate - newThrottle;

                        // discard any permits that are needed to decrease throttling
                        semaphore.reducePermits(delta);
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Throttle rate decreased from {} to {}, triggered by ExchangeId: {}", throttleRate,
                                    newThrottle, exchange.getExchangeId());
                        }

                        // increase
                    } else if (newThrottle > throttleRate) {
                        int delta = newThrottle - throttleRate;
                        semaphore.increasePermits(delta);
                        if (throttleRate == 0) {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("Initial throttle rate set to {}, triggered by ExchangeId: {}", newThrottle,
                                        exchange.getExchangeId());
                            }
                        } else {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("Throttle rate increase from {} to {}, triggered by ExchangeId: {}", throttleRate,
                                        newThrottle, exchange.getExchangeId());
                            }
                        }
                    }
                    throttleRate = newThrottle;
                }
            }
        }
    }

    // extend Semaphore so we can reduce permits if required
    private class WrappedSemaphore extends Semaphore {
        public WrappedSemaphore() {
            super(0, true);
        }

        public boolean tryAcquire() {
            try {
                // honours fairness setting
                return super.tryAcquire(0L, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        // decrease throttling
        public void reducePermits(int n) {
            super.reducePermits(n);
        }

        // increase throttling
        public void increasePermits(int n) {
            super.release(n);
        }
    }

    @Override
    public String getMode() {
        return "ConcurrentRequests";
    }

    /**
     * Gets the current maximum request. If it is grouped throttling applied with correlationExpression then the max
     * within the group will return
     */
    @Override
    public int getCurrentMaximumRequests() {
        return states.values().stream().mapToInt(ThrottlingState::getThrottleRate).max().orElse(0);
    }

    @Override
    public String getTraceLabel() {
        return "throttle[" + getMaximumRequestsExpression() + "]";
    }

    @Override
    public String toString() {
        return getId();
    }
}
