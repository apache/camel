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
package org.apache.camel.component.netty;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.LoggingLevel;
import org.apache.camel.TimeoutMap;
import org.apache.camel.TimeoutMap.Listener.Type;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.support.DefaultTimeoutMap;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A base class for using {@link NettyCamelStateCorrelationManager} that supports timeout.
 */
public abstract class TimeoutCorrelationManagerSupport extends ServiceSupport
        implements CamelContextAware, NettyCamelStateCorrelationManager {

    private static final Logger LOG = LoggerFactory.getLogger(TimeoutCorrelationManagerSupport.class);

    private volatile ScheduledExecutorService scheduledExecutorService;
    private volatile boolean stopScheduledExecutorService;
    private volatile ExecutorService workerPool;
    private volatile boolean stopWorkerPool;
    private volatile TimeoutMap<String, NettyCamelState> map;
    private volatile CamelLogger timeoutLogger;
    // the producers that use this manager (guarded by the lock of the service)
    private int users;

    private CamelContext camelContext;
    private long timeout = 30000;
    private long timeoutChecker = 1000;
    private LoggingLevel timeoutLoggingLevel = LoggingLevel.DEBUG;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public long getTimeout() {
        return timeout;
    }

    /**
     * Sets timeout value in millis seconds. The default value is 30000 (30 seconds).
     */
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public long getTimeoutChecker() {
        return timeoutChecker;
    }

    /**
     * Time in millis how frequent to check for timeouts. Set this to a lower value if you want to react faster upon
     * timeouts. The default value is 1000.
     */
    public void setTimeoutChecker(long timeoutChecker) {
        this.timeoutChecker = timeoutChecker;
    }

    public LoggingLevel getTimeoutLoggingLevel() {
        return timeoutLoggingLevel;
    }

    /**
     * Sets the logging level to use when a timeout was hit.
     */
    public void setTimeoutLoggingLevel(LoggingLevel timeoutLoggingLevel) {
        this.timeoutLoggingLevel = timeoutLoggingLevel;
    }

    public ExecutorService getWorkerPool() {
        return workerPool;
    }

    /**
     * To use a shared worker pool for processing timed out requests.
     */
    public void setWorkerPool(ExecutorService workerPool) {
        this.workerPool = workerPool;
    }

    /**
     * Implement this method to extract the correlation id from the request message body.
     */
    public abstract String getRequestCorrelationId(Object request);

    /**
     * Implement this method to extract the correlation id from the response message body.
     */
    public abstract String getResponseCorrelationId(Object response);

    /**
     * Override this to implement a custom timeout response message.
     *
     * @param  correlationId the correlation id
     * @param  request       the request message
     * @return               the response message or <tt>null</tt> to use an {@link ExchangeTimedOutException}
     *                       exception.
     */
    public String getTimeoutResponse(String correlationId, Object request) {
        return null;
    }

    @Override
    public void putState(Channel channel, NettyCamelState state) {
        TimeoutMap<String, NettyCamelState> current = map;
        if (!isRunning(current)) {
            // the timeout of a request in the map of a stopped manager never fires: fail the request instead
            throw new IllegalStateException("The correlation manager is not started");
        }
        // grab the correlation id
        Object body = state.getExchange().getMessage().getBody();
        // the correlation id is the first part of the message
        String cid = getRequestCorrelationId(body);
        if (ObjectHelper.isEmpty(cid)) {
            throw new IllegalArgumentException("CorrelationID is missing");
        }
        LOG.debug("putState({}) on channel: {}", cid, channel.id());
        current.put(cid, state, timeout);
        if (!isRunning(current)) {
            // the manager stopped while the state was put (and may have started again with a new map): its stop drained
            // the map before or after the put. Take the state back, and fail the request unless the drain already
            // completed it (markDone makes sure that only one of the two completes the exchange)
            current.remove(cid);
            if (state.markDone()) {
                throw new IllegalStateException("The correlation manager is not started");
            }
        }
    }

    /**
     * Whether a state put in the given map is completed by its reply, its timeout or the drain of the stop: the map
     * runs from the start of the manager until its stop drains it, and the manager creates a new map when it starts
     * again.
     */
    private boolean isRunning(TimeoutMap<String, NettyCamelState> current) {
        return current != null && isRunAllowed() && ServiceHelper.isStarted(current);
    }

    /**
     * The timeout map of the pending requests (for tests).
     */
    TimeoutMap<String, NettyCamelState> getTimeoutMap() {
        return map;
    }

    @Override
    public void removeState(ChannelHandlerContext ctx, Channel channel) {
        // noop
    }

    @Override
    public NettyCamelState getState(ChannelHandlerContext ctx, Channel channel, Object msg) {
        String cid = getResponseCorrelationId(msg);
        if (ObjectHelper.isEmpty(cid)) {
            LOG.warn("CorrelationID is missing from response message.");
            return null;
        }
        LOG.debug("getState({}) on channel: {}", cid, channel.id());
        // lets remove after use as its no longer needed
        return map.remove(cid);
    }

    @Override
    public NettyCamelState getState(ChannelHandlerContext ctx, Channel channel, Throwable cause) {
        // noop
        return null;
    }

    /**
     * Takes a reference on this manager for a producer that uses it, and starts the manager when it is not started. A
     * manager configured as a bean can be used by several producers, which must not stop it under each other.
     */
    void retain() {
        lock.lock();
        try {
            ServiceHelper.startService(this);
            users++;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Releases the reference a producer took with {@link #retain()}. The last producer that releases the manager stops
     * it, which completes the requests that still wait for their reply.
     */
    void release() {
        lock.lock();
        try {
            if (users > 0) {
                users--;
                if (users == 0) {
                    ServiceHelper.stopService(this);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(camelContext, "CamelContext", this);

        timeoutLogger = new CamelLogger(LOG, timeoutLoggingLevel);

        if (scheduledExecutorService == null) {
            scheduledExecutorService = camelContext.getExecutorServiceManager().newSingleThreadScheduledExecutor(this,
                    "NettyTimeoutCorrelationManager");
            stopScheduledExecutorService = true;
        }
        if (workerPool == null) {
            workerPool = camelContext.getExecutorServiceManager().newDefaultThreadPool(this, "NettyTimeoutWorkerPool");
            stopWorkerPool = true;
        }

        map = new CorrelationTimeoutMap(scheduledExecutorService, timeoutChecker);
        map.addListener(this::onEviction);

        ServiceHelper.startService(map);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(map);

        if (scheduledExecutorService != null && stopScheduledExecutorService) {
            camelContext.getExecutorServiceManager().shutdown(scheduledExecutorService);
            scheduledExecutorService = null;
        }
        if (workerPool != null && stopWorkerPool) {
            camelContext.getExecutorServiceManager().shutdown(workerPool);
            workerPool = null;
        }
    }

    @Override
    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownService(map);

        if (scheduledExecutorService != null && stopScheduledExecutorService) {
            camelContext.getExecutorServiceManager().shutdown(scheduledExecutorService);
            scheduledExecutorService = null;
        }
        if (workerPool != null && stopWorkerPool) {
            camelContext.getExecutorServiceManager().shutdown(workerPool);
            workerPool = null;
        }
    }

    /**
     * The timeout map of the pending requests. When it stops (the producer stops), the requests still waiting for a
     * reply are evicted, so they complete as if they timed out: the worker pool runs them before it is shut down.
     */
    private static final class CorrelationTimeoutMap extends DefaultTimeoutMap<String, NettyCamelState> {

        CorrelationTimeoutMap(ScheduledExecutorService executor, long requestMapPollTimeMillis) {
            super(executor, requestMapPollTimeMillis);
        }

        @Override
        protected void doStop() throws Exception {
            drainAndEvictAll();
            super.doStop();
        }
    }

    private void onEviction(Type type, String key, NettyCamelState value) {
        if (type != Type.Evict) {
            return;
        }

        Runnable task = () -> {
            Exchange exchange = value.getExchange();
            AsyncCallback callback = value.getCallback();
            // the exchange may have been completed meanwhile (the write failed, or this task already ran), then it
            // must not be touched
            if (exchange != null && callback != null && value.markDone()) {
                timeoutLogger.log("Timeout of correlation id: " + key);
                Object timeoutBody = getTimeoutResponse(key, exchange.getMessage().getBody());
                if (timeoutBody != null) {
                    exchange.getMessage().setBody(timeoutBody);
                } else {
                    exchange.setException(new ExchangeTimedOutException(exchange, timeout));
                }
                callback.done(false);
            }
        };
        ExecutorService pool = workerPool;
        if (pool != null) {
            try {
                pool.submit(task);
            } catch (RejectedExecutionException e) {
                LOG.debug("The worker pool rejected the timeout of correlation id: {}. Running it in this thread.", key);
                task.run();
                return;
            }
        }
        if (pool == null || pool.isShutdown()) {
            // the manager is stopping (an entry the purge task evicted while the producer stopped). A shut-down pool
            // with the CallerRunsPolicy (the default rejection policy of Camel thread pools) discards the task without
            // throwing, so submit can return normally while the task never runs. Run it here as well: markDone makes
            // sure the exchange is completed at most once
            task.run();
        }
    }
}
