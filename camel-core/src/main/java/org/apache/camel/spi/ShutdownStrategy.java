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
package org.apache.camel.spi;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Service;

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
 *
 * @version $Revision$
 * @see org.apache.camel.spi.ShutdownAware
 */
public interface ShutdownStrategy extends Service {

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
     * Shutdown the routes using a specified timeout instead of the default timeout values and supports "give up" mode
     *
     * @param context   the camel context
     * @param routes    the routes, ordered by the order they was started
     * @param timeout   timeout
     * @param timeUnit  the unit to use
     * @param giveUp    should give up after timeout
     * @return <tt>true</tt> if the route is stopped before the timeout
     * @throws Exception is thrown if error shutting down the consumers, however its preferred to avoid this
     */
    boolean shutdown(CamelContext context, List<RouteStartupOrder> routes, long timeout, TimeUnit timeUnit, boolean giveUp) throws Exception;

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
     * Setting a value of 0 or negative will disable timeout and wait until complete
     * (potential blocking forever)
     * <p/>
     * The default timeout unit is <tt>SECONDS</tt>
     *
     * @param timeout timeout
     */
    void setTimeout(long timeout);

    /**
     * Gets the timeout.
     * <p/>
     * Use 0 or a negative value to disable timeout
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

}
