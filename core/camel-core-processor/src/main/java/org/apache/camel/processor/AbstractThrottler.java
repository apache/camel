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

import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.RouteStoppingException;
import org.apache.camel.Traceable;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.spi.StepIdAware;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractThrottler extends BaseProcessorSupport
        implements Traceable, IdAware, RouteIdAware, StepIdAware, Throttler {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractThrottler.class);

    protected static final String DEFAULT_KEY = "CamelThrottlerDefaultKey";
    protected static final String PROPERTY_EXCHANGE_QUEUED_TIMESTAMP = "CamelThrottlerExchangeQueuedTimestamp";
    protected static final String PROPERTY_EXCHANGE_STATE = "CamelThrottlerExchangeState";
    protected final ScheduledExecutorService asyncExecutor;
    protected final boolean shutdownAsyncExecutor;
    protected final CamelContext camelContext;
    protected final Expression correlationExpression;
    protected String id;
    protected String routeId;
    protected String stepId;
    protected boolean rejectExecution;
    protected boolean asyncDelayed;
    protected boolean callerRunsWhenRejected = true;
    protected volatile Expression maxRequestsExpression;
    // the asynchronous tasks (asyncDelayed) that are queued in the thread pool and not yet started: a task and
    // doShutdown each claim a task by removing it, so an exchange is processed, or failed when the thread pool is shut
    // down, but never both
    private final Set<AsyncCall> asyncCalls = ConcurrentHashMap.newKeySet();

    AbstractThrottler(final ScheduledExecutorService asyncExecutor, final boolean shutdownAsyncExecutor,
                      final CamelContext camelContext, final boolean rejectExecution, Expression correlation,
                      final Expression maxRequestsExpression) {
        this.asyncExecutor = asyncExecutor;
        this.shutdownAsyncExecutor = shutdownAsyncExecutor;
        this.camelContext = camelContext;
        this.rejectExecution = rejectExecution;
        this.correlationExpression = correlation;
        this.maxRequestsExpression = ObjectHelper.notNull(maxRequestsExpression, "maxRequestsExpression");
    }

    /**
     * Schedules a clean of a throttling state, unless the pending one can stay. This is called for every exchange, and
     * scheduling and cancelling takes the executor queue lock about three times. The pending clean stays while at least
     * a fifth of the clean period is left, so it may run up to a fifth earlier than the clean period after this call.
     */
    void scheduleClean(AtomicReference<ScheduledFuture<?>> cleanFuture, Runnable clean, long cleanPeriodMillis) {
        ScheduledFuture<?> pending = cleanFuture.get();
        if (pending != null && pending.getDelay(TimeUnit.MILLISECONDS) >= cleanPeriodMillis / 5) {
            return;
        }
        ScheduledFuture<?> next = asyncExecutor.schedule(clean, cleanPeriodMillis, TimeUnit.MILLISECONDS);
        ScheduledFuture<?> prev = cleanFuture.getAndSet(next);
        if (prev != null) {
            prev.cancel(false);
        }
    }

    /**
     * A task that processes the exchange again on the thread pool, unless doShutdown failed the exchange before.
     */
    final class AsyncCall implements Callable<Boolean> {
        private final Exchange exchange;
        private final AsyncCallback callback;

        private AsyncCall(Exchange exchange, AsyncCallback callback) {
            this.exchange = exchange;
            this.callback = callback;
        }

        // a Callable, as the task was before, so the thread pool is called with the same submit and schedule methods
        @Override
        public Boolean call() {
            if (asyncCalls.remove(this)) {
                return process(exchange, callback);
            }
            return false;
        }

        /**
         * The thread pool did not take the task (rejected), so it is no longer queued.
         */
        void rejected() {
            asyncCalls.remove(this);
        }
    }

    /**
     * Creates the task to process the exchange again on the thread pool, and remembers it until it starts.
     */
    AsyncCall newAsyncCall(Exchange exchange, AsyncCallback callback) {
        AsyncCall call = new AsyncCall(exchange, callback);
        asyncCalls.add(call);
        return call;
    }

    /**
     * Fails the exchanges whose asynchronous task has not started yet. They would never be processed once the thread
     * pool is shut down (such as when the route is removed after the shutdown timeout ran out), and their callers would
     * wait for ever. Only called once doShutdown shut down the thread pool of this EIP: a thread pool of the user still
     * runs the tasks.
     */
    void failAsyncCalls() {
        for (AsyncCall call : asyncCalls) {
            if (asyncCalls.remove(call)) {
                LOG.debug("Throttled exchange is not processed as the throttler is shut down, exchangeId: {}",
                        call.exchange.getExchangeId());
                // the exchange is no longer queued (a later throttler must process it synchronously)
                call.exchange.removeProperty(PROPERTY_EXCHANGE_STATE);
                call.exchange.setException(
                        new RouteStoppingException("Throttled exchange is not processed as the throttler is shut down"));
                call.callback.done(false);
            }
        }
    }

    protected static boolean handleInterrupt(
            Exchange exchange, AsyncCallback callback, InterruptedException e, boolean doneSync) {
        // determine if we can still run, or the camel context is forcing a shutdown
        boolean forceShutdown = exchange.getContext().getShutdownStrategy().isForceShutdown();
        if (forceShutdown) {
            String msg = "Run not allowed as ShutdownStrategy is forcing shutting down, will reject executing exchange: "
                         + exchange;
            LOG.debug(msg);
            exchange.setException(new RouteStoppingException(msg, e));
        } else {
            exchange.setException(e);
        }
        callback.done(doneSync);
        return doneSync;
    }

    protected static boolean handleException(Exchange exchange, AsyncCallback callback, Exception t, boolean doneSync) {
        exchange.setException(t);
        callback.done(doneSync);
        return doneSync;
    }

    @Override
    public boolean isRejectExecution() {
        return rejectExecution;
    }

    public void setRejectExecution(boolean rejectExecution) {
        this.rejectExecution = rejectExecution;
    }

    @Override
    public boolean isAsyncDelayed() {
        return asyncDelayed;
    }

    public void setAsyncDelayed(boolean asyncDelayed) {
        this.asyncDelayed = asyncDelayed;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(final String id) {
        this.id = id;
    }

    @Override
    public String getRouteId() {
        return routeId;
    }

    @Override
    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    @Override
    public String getStepId() {
        return stepId;
    }

    @Override
    public void setStepId(String stepId) {
        this.stepId = stepId;
    }

    @Override
    public boolean isCallerRunsWhenRejected() {
        return callerRunsWhenRejected;
    }

    public void setCallerRunsWhenRejected(boolean callerRunsWhenRejected) {
        this.callerRunsWhenRejected = callerRunsWhenRejected;
    }

    /**
     * Sets the maximum number of concurrent requests.
     */
    @Override
    public void setMaximumRequestsExpression(Expression maxRequestsExpression) {
        this.maxRequestsExpression = maxRequestsExpression;
    }

    public Expression getMaximumRequestsExpression() {
        return maxRequestsExpression;
    }

    protected enum State {
        SYNC,
        ASYNC,
        ASYNC_REJECTED
    }

    @Override
    public abstract String getMode();
}
