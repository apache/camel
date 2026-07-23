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
package org.apache.camel.spi;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.LoggingLevel;
import org.apache.camel.StaticService;

/**
 * Pluggable strategy that performs the actual <a href="https://camel.apache.org/manual/graceful-shutdown.html">graceful
 * shutdown</a> sequence when a {@link CamelContext} is stopping.
 * <p/>
 * Graceful shutdown is non-trivial: Camel must stop all route consumers (so no new messages enter) while keeping routes
 * alive long enough to drain in-flight exchanges, including messages sitting in in-memory queues (SEDA, etc.). The
 * default implementation shuts down routes in reverse startup order (see {@link RouteStartupOrder}), waits for
 * in-flight exchanges to complete up to the configured {@link #setTimeout timeout}, then forces shutdown if the timeout
 * expires.
 * <p/>
 * This SPI is intended for framework and container integrators. End users who need to stop individual routes should use
 * {@link RouteController} via {@link CamelContext#getRouteController()} instead. Per-route and per-consumer shutdown
 * behaviour can be tuned via {@link org.apache.camel.ShutdownRoute} and {@link org.apache.camel.ShutdownRunningTask}
 * without replacing the strategy.
 *
 * @see RouteController
 * @see RouteStartupOrder
 * @see org.apache.camel.ShutdownRoute
 * @see org.apache.camel.ShutdownRunningTask
 * @see org.apache.camel.spi.ShutdownAware
 */
public interface ShutdownStrategy extends StaticService {

    /**
     * Shutdown the routes, forcing shutdown being more aggressive, if timeout occurred.
     * <p/>
     * This operation is used when {@link CamelContext} is shutting down, to ensure Camel will shutdown if messages
     * seems to be <i>stuck</i>.
     *
     * @param  context   the camel context
     * @param  routes    the routes, ordered by the order they were started
     * @throws Exception is thrown if error shutting down the consumers, however its preferred to avoid this
     */
    void shutdownForced(CamelContext context, List<RouteStartupOrder> routes) throws Exception;

    /**
     * Shutdown the routes
     *
     * @param  context   the camel context
     * @param  routes    the routes, ordered by the order they were started
     * @throws Exception is thrown if error shutting down the consumers, however its preferred to avoid this
     */
    void shutdown(CamelContext context, List<RouteStartupOrder> routes) throws Exception;

    /**
     * Suspends the routes
     *
     * @param  context   the camel context
     * @param  routes    the routes, ordered by the order they are started
     * @throws Exception is thrown if error suspending the consumers, however its preferred to avoid this
     */
    void suspend(CamelContext context, List<RouteStartupOrder> routes) throws Exception;

    /**
     * Shutdown the routes using a specified timeout instead of the default timeout values
     *
     * @param  context   the camel context
     * @param  routes    the routes, ordered by the order they are started
     * @param  timeout   timeout
     * @param  timeUnit  the unit to use
     * @throws Exception is thrown if error shutting down the consumers, however its preferred to avoid this
     */
    void shutdown(CamelContext context, List<RouteStartupOrder> routes, long timeout, TimeUnit timeUnit) throws Exception;

    /**
     * Shutdown the route using a specified timeout instead of the default timeout values and supports abortAfterTimeout
     * mode
     *
     * @param  context           the camel context
     * @param  route             the route
     * @param  timeout           timeout
     * @param  timeUnit          the unit to use
     * @param  abortAfterTimeout should abort shutdown after timeout
     * @return                   <tt>true</tt> if the route is stopped before the timeout
     * @throws Exception         is thrown if error shutting down the consumer, however its preferred to avoid this
     */
    boolean shutdown(CamelContext context, RouteStartupOrder route, long timeout, TimeUnit timeUnit, boolean abortAfterTimeout)
            throws Exception;

    /**
     * Suspends the routes using a specified timeout instead of the default timeout values
     *
     * @param  context   the camel context
     * @param  routes    the routes, ordered by the order they were started
     * @param  timeout   timeout
     * @param  timeUnit  the unit to use
     * @throws Exception is thrown if error suspending the consumers, however its preferred to avoid this
     */
    void suspend(CamelContext context, List<RouteStartupOrder> routes, long timeout, TimeUnit timeUnit) throws Exception;

    /**
     * Set a timeout to wait for the shutdown to complete.
     * <p/>
     * You must set a positive value. If you want to wait (forever) then use a very high value such as
     * {@link Long#MAX_VALUE}
     * <p/>
     * The default timeout unit is <tt>SECONDS</tt>
     *
     * @throws IllegalArgumentException if the timeout value is 0 or negative
     * @param  timeout                  timeout
     */
    void setTimeout(long timeout);

    /**
     * Gets the timeout.
     * <p/>
     * The default timeout unit is <tt>SECONDS</tt>
     *
     * @return the timeout
     */
    long getTimeout();

    /**
     * Set the time unit to use
     *
     * @param timeUnit the unit to use
     */
    void setTimeUnit(TimeUnit timeUnit);

    /**
     * Gets the time unit used
     *
     * @return the time unit
     */
    TimeUnit getTimeUnit();

    /**
     * Whether Camel should try to suppress logging during shutdown and timeout was triggered, meaning forced shutdown
     * is happening. And during forced shutdown we want to avoid logging errors/warnings et al. in the logs as a side
     * effect of the forced timeout.
     * <p/>
     * By default, this is <tt>false</tt>
     * <p/>
     * Notice the suppression is a <i>best effort</i> as there may still be some logs coming from 3rd party libraries
     * and whatnot, which Camel cannot control.
     *
     * @param suppressLoggingOnTimeout <tt>true</tt> to suppress logging, false to log as usual.
     */
    void setSuppressLoggingOnTimeout(boolean suppressLoggingOnTimeout);

    /**
     * Whether Camel should try to suppress logging during shutdown and timeout was triggered, meaning forced shutdown
     * is happening. And during forced shutdown we want to avoid logging errors/warnings et al. in the logs as a side
     * effect of the forced timeout.
     * <p/>
     * By default, this is <tt>false</tt>
     * <p/>
     * Notice the suppression is a <i>best effort</i> as there may still be some logs coming from 3rd party libraries
     * and whatnot, which Camel cannot control.
     */
    boolean isSuppressLoggingOnTimeout();

    /**
     * Sets whether to force shutdown of all consumers when a timeout occurred and thus not all consumers was shutdown
     * within that period.
     * <p/>
     * You should have good reasons to set this option to <tt>false</tt> as it means that the routes keep running and is
     * halted abruptly when {@link CamelContext} has been shutdown.
     *
     * @param shutdownNowOnTimeout <tt>true</tt> to force shutdown, <tt>false</tt> to leave them running
     */
    void setShutdownNowOnTimeout(boolean shutdownNowOnTimeout);

    /**
     * Whether to force shutdown of all consumers when a timeout occurred.
     *
     * @return force shutdown or not
     */
    boolean isShutdownNowOnTimeout();

    /**
     * Sets whether routes should be shutdown in reverse or the same order as they were started.
     *
     * @param shutdownRoutesInReverseOrder <tt>true</tt> to shutdown in reverse order
     */
    void setShutdownRoutesInReverseOrder(boolean shutdownRoutesInReverseOrder);

    /**
     * Whether to shut down routes in reverse order than they were started.
     * <p/>
     * This option is by default set to <tt>true</tt>.
     *
     * @return <tt>true</tt> if routes should be shutdown in reverse order.
     */
    boolean isShutdownRoutesInReverseOrder();

    /**
     * Sets whether to log information about the inflight {@link org.apache.camel.Exchange}s which are still running
     * during a shutdown which didn't complete without the given timeout.
     *
     * @param logInflightExchangesOnTimeout <tt>true</tt> to log information about the inflight exchanges,
     *                                      <tt>false</tt> to not log
     */
    void setLogInflightExchangesOnTimeout(boolean logInflightExchangesOnTimeout);

    /**
     * Whether to log information about the inflight {@link org.apache.camel.Exchange}s which are still running during a
     * shutdown which didn't complete without the given timeout.
     */
    boolean isLogInflightExchangesOnTimeout();

    /**
     * Whether the shutdown strategy is forcing to shut down
     */
    boolean isForceShutdown();

    /**
     * Whether a timeout has occurred during a shutdown.
     *
     * @deprecated use {@link #isTimeoutOccurred()}
     */
    @Deprecated(since = "4.8.0")
    boolean hasTimeoutOccurred();

    /**
     * Whether a timeout has occurred during a shutdown.
     */
    default boolean isTimeoutOccurred() {
        return hasTimeoutOccurred();
    }

    /**
     * Gets the logging level used for logging shutdown activity (such as starting and stopping routes). The default
     * logging level is DEBUG.
     */
    LoggingLevel getLoggingLevel();

    /**
     * Sets the logging level used for logging shutdown activity (such as starting and stopping routes). The default
     * logging level is DEBUG.
     */
    void setLoggingLevel(LoggingLevel loggingLevel);

}
