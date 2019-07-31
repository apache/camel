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
import org.apache.camel.Service;
import org.apache.camel.StaticService;

/**
 * Pluggable shutdown strategy executed during shutdown of routes.
 * <p/>
 * Shutting down routes in a reliable and graceful manner is not a trivial task. Therefore Camel provides a pluggable
 * strategy allowing 3rd party to use their own strategy if needed.
 * <p/>
 * The key problem is to stop the input consumers for the routes such that no new messages is coming into Camel.
 * But at the same time still keep the routes running so the existing in flight exchanges can still be run to
 * completion. On top of that there are some in memory components (such as SEDA) which may have pending messages
 * on its in memory queue which we want to run to completion as well, otherwise they will get lost.
 * <p/>
 * Camel provides a default strategy which supports all that that can be used as inspiration for your own strategy.
 * @see org.apache.camel.spi.ShutdownAware
 */
public interface ShutdownStrategy extends StaticService {

    /**
     * Shutdown the routes, forcing shutdown being more aggressive, if timeout occurred.
     * <p/>
     * This operation is used when {@link CamelContext} is shutting down, to ensure Camel will shutdown
     * if messages seems to be <i>stuck</i>.
     *
     * @param context   the camel context
     * @param routes    the routes, ordered by the order they was started
     * @throws Exception is thrown if error shutting down the consumers, however its preferred to avoid this
     */
    void shutdownForced(CamelContext context, List<RouteStartupOrder> routes) throws Exception;

    /**
     * Shutdown the routes
     *
     * @param context   the camel context
     * @param routes    the routes, ordered by the order they was started
     * @throws Exception is thrown if error shutting down the consumers, however its preferred to avoid this
     */
    void shutdown(CamelContext context, List<RouteStartupOrder> routes) throws Exception;

    /**
     * Suspends the routes
     *
     * @param context   the camel context
     * @param routes    the routes, ordered by the order they was started
     * @throws Exception is thrown if error suspending the consumers, however its preferred to avoid this
     */
    void suspend(CamelContext context, List<RouteStartupOrder> routes) throws Exception;

    /**
     * Shutdown the routes using a specified timeout instead of the default timeout values
     *
     * @param context   the camel context
     * @param routes    the routes, ordered by the order they was started
     * @param timeout   timeout
     * @param timeUnit  the unit to use
     * @throws Exception is thrown if error shutting down the consumers, however its preferred to avoid this
     */
    void shutdown(CamelContext context, List<RouteStartupOrder> routes, long timeout, TimeUnit timeUnit) throws Exception;

    /**
     * Shutdown the route using a specified timeout instead of the default timeout values and supports abortAfterTimeout mode
     *
     * @param context   the camel context
     * @param route     the route
     * @param timeout   timeout
     * @param timeUnit  the unit to use
     * @param abortAfterTimeout   should abort shutdown after timeout
     * @return <tt>true</tt> if the route is stopped before the timeout
     * @throws Exception is thrown if error shutting down the consumer, however its preferred to avoid this
     */
    boolean shutdown(CamelContext context, RouteStartupOrder route, long timeout, TimeUnit timeUnit, boolean abortAfterTimeout) throws Exception;

    /**
     * Suspends the routes using a specified timeout instead of the default timeout values
     *
     * @param context   the camel context
     * @param routes    the routes, ordered by the order they was started
     * @param timeout   timeout
     * @param timeUnit  the unit to use
     * @throws Exception is thrown if error suspending the consumers, however its preferred to avoid this
     */
    void suspend(CamelContext context, List<RouteStartupOrder> routes, long timeout, TimeUnit timeUnit) throws Exception;

    /**
     * Set an timeout to wait for the shutdown to complete.
     * <p/>
     * You must set a positive value. If you want to wait (forever) then use
     * a very high value such as {@link Long#MAX_VALUE}
     * <p/>
     * The default timeout unit is <tt>SECONDS</tt>
     *
     * @throws IllegalArgumentException if the timeout value is 0 or negative
     * @param timeout timeout
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
     * Whether Camel should try to suppress logging during shutdown and timeout was triggered,
     * meaning forced shutdown is happening. And during forced shutdown we want to avoid logging
     * errors/warnings et all in the logs as a side-effect of the forced timeout.
     * <p/>
     * By default this is <tt>false</tt>
     * <p/>
     * Notice the suppress is a <i>best effort</i> as there may still be some logs coming
     * from 3rd party libraries and whatnot, which Camel cannot control.
     *
     * @param suppressLoggingOnTimeout <tt>true</tt> to suppress logging, false to log as usual.
     */
    void setSuppressLoggingOnTimeout(boolean suppressLoggingOnTimeout);

    /**
     * Whether Camel should try to suppress logging during shutdown and timeout was triggered,
     * meaning forced shutdown is happening. And during forced shutdown we want to avoid logging
     * errors/warnings et all in the logs as a side-effect of the forced timeout.
     * <p/>
     * By default this is <tt>false</tt>
     * <p/>
     * Notice the suppress is a <i>best effort</i> as there may still be some logs coming
     * from 3rd party libraries and whatnot, which Camel cannot control.
     */
    boolean isSuppressLoggingOnTimeout();

    /**
     * Sets whether to force shutdown of all consumers when a timeout occurred and thus
     * not all consumers was shutdown within that period.
     * <p/>
     * You should have good reasons to set this option to <tt>false</tt> as it means that the routes
     * keep running and is halted abruptly when {@link CamelContext} has been shutdown.
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
     * Sets whether routes should be shutdown in reverse or the same order as they where started.
     *
     * @param shutdownRoutesInReverseOrder <tt>true</tt> to shutdown in reverse order
     */
    void setShutdownRoutesInReverseOrder(boolean shutdownRoutesInReverseOrder);

    /**
     * Whether to shutdown routes in reverse order than they where started.
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
     * @param logInflightExchangesOnTimeout <tt>true</tt> to log information about the inflight exchanges, <tt>false</tt> to not log
     */
    void setLogInflightExchangesOnTimeout(boolean logInflightExchangesOnTimeout);

    /**
     * Whether to log information about the inflight {@link org.apache.camel.Exchange}s which are still running
     * during a shutdown which didn't complete without the given timeout.
     */
    boolean isLogInflightExchangesOnTimeout();

    /**
     * Whether a service is forced to shutdown.
     * <p/>
     * Can be used to signal to services that they are no longer allowed to run, such as if a forced
     * shutdown is currently in progress.
     * <p/>
     * For example the Camel {@link org.apache.camel.processor.RedeliveryErrorHandler} uses this information
     * to know if a forced shutdown is in progress, and then break out of redelivery attempts.
     * 
     * @param service the service
     * @return <tt>true</tt> indicates the service is to be forced to shutdown, <tt>false</tt> the service can keep running.
     */
    boolean forceShutdown(Service service);

    /**
     * Whether a timeout has occurred during a shutdown.
     */
    boolean hasTimeoutOccurred();

}
