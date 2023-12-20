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

import java.util.Collection;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContextAware;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.apache.camel.StaticService;

/**
 * Controller for managing the lifecycle of all the {@link Route}'s.
 */
public interface RouteController extends CamelContextAware, StaticService {

    /**
     * Gets the logging level used for logging route activity (such as starting and stopping routes). The default
     * logging level is DEBUG.
     */
    LoggingLevel getLoggingLevel();

    /**
     * Sets the logging level used for logging route activity (such as starting and stopping routes). The default
     * logging level is DEBUG.
     */
    void setLoggingLevel(LoggingLevel loggingLevel);

    /**
     * Whether this route controller is a regular or supervising controller.
     */
    boolean isSupervising();

    /**
     * Enables supervising {@link RouteController}.
     */
    SupervisingRouteController supervising();

    /**
     * Adapts this {@link org.apache.camel.spi.RouteController} to the specialized type.
     * <p/>
     * For example to adapt to <tt>SupervisingRouteController</tt>.
     *
     * @param  type the type to adapt to
     * @return      this {@link org.apache.camel.CamelContext} adapted to the given type
     */
    <T extends RouteController> T adapt(Class<T> type);

    /**
     * Return the list of routes controlled by this controller.
     *
     * @return the list of controlled routes
     */
    Collection<Route> getControlledRoutes();

    /**
     * Starts all the routes which currently is not started.
     *
     * @throws Exception is thrown if a route could not be started for whatever reason
     */
    void startAllRoutes() throws Exception;

    /**
     * Stops all the routes
     *
     * @throws Exception is thrown if a route could not be stopped for whatever reason
     */
    void stopAllRoutes() throws Exception;

    /**
     * Stops and removes all the routes
     *
     * @throws Exception is thrown if a route could not be stopped or removed for whatever reason
     */
    void removeAllRoutes() throws Exception;

    /**
     * Indicates whether the route controller is doing initial starting of the routes.
     */
    boolean isStartingRoutes();

    /**
     * Indicates if the route controller has routes that are currently unhealthy such as they have not yet been
     * successfully started, and if being supervised then the route can either be pending restarts or failed all restart
     * attempts and are exhausted.
     */
    boolean hasUnhealthyRoutes();

    /**
     * Reloads all the routes
     *
     * @throws Exception is thrown if a route could not be reloaded for whatever reason
     */
    void reloadAllRoutes() throws Exception;

    /**
     * Indicates whether current thread is reloading route(s).
     * <p/>
     * This can be useful to know by {@link LifecycleStrategy} or the likes, in case they need to react differently.
     *
     * @return <tt>true</tt> if current thread is reloading route(s), or <tt>false</tt> if not.
     */
    boolean isReloadingRoutes();

    /**
     * Returns the current status of the given route
     *
     * @param  routeId the route id
     * @return         the status for the route
     */
    ServiceStatus getRouteStatus(String routeId);

    /**
     * Starts the given route if it has been previously stopped
     *
     * @param  routeId   the route id
     * @throws Exception is thrown if the route could not be started for whatever reason
     */
    void startRoute(String routeId) throws Exception;

    /**
     * Stops the given route using {@link org.apache.camel.spi.ShutdownStrategy}.
     *
     * @param  routeId   the route id
     * @throws Exception is thrown if the route could not be stopped for whatever reason
     * @see              #suspendRoute(String)
     */
    void stopRoute(String routeId) throws Exception;

    /**
     * Stops and marks the given route as failed (health check is DOWN) due to a caused exception.
     *
     * @param  routeId   the route id
     * @param  cause     the exception that is causing this route to be stopped and marked as failed
     * @throws Exception is thrown if the route could not be stopped for whatever reason
     * @see              #suspendRoute(String)
     */
    void stopRoute(String routeId, Throwable cause) throws Exception;

    /**
     * Stops the given route using {@link org.apache.camel.spi.ShutdownStrategy} with a specified timeout.
     *
     * @param  routeId   the route id
     * @param  timeout   timeout
     * @param  timeUnit  the unit to use
     * @throws Exception is thrown if the route could not be stopped for whatever reason
     * @see              #suspendRoute(String, long, java.util.concurrent.TimeUnit)
     */
    void stopRoute(String routeId, long timeout, TimeUnit timeUnit) throws Exception;

    /**
     * Stops the given route using {@link org.apache.camel.spi.ShutdownStrategy} with a specified timeout and optional
     * abortAfterTimeout mode.
     *
     * @param  routeId           the route id
     * @param  timeout           timeout
     * @param  timeUnit          the unit to use
     * @param  abortAfterTimeout should abort shutdown after timeout
     * @return                   <tt>true</tt> if the route is stopped before the timeout
     * @throws Exception         is thrown if the route could not be stopped for whatever reason
     * @see                      #suspendRoute(String, long, java.util.concurrent.TimeUnit)
     */
    boolean stopRoute(String routeId, long timeout, TimeUnit timeUnit, boolean abortAfterTimeout) throws Exception;

    /**
     * Suspends the given route using {@link org.apache.camel.spi.ShutdownStrategy}.
     * <p/>
     * Suspending a route is more gently than stopping, as the route consumers will be suspended (if they support)
     * otherwise the consumers will be stopped.
     * <p/>
     * By suspending the route services will be kept running (if possible) and therefore its faster to resume the route.
     * <p/>
     * If the route does <b>not</b> support suspension the route will be stopped instead
     *
     * @param  routeId   the route id
     * @throws Exception is thrown if the route could not be suspended for whatever reason
     */
    void suspendRoute(String routeId) throws Exception;

    /**
     * Suspends the given route using {@link org.apache.camel.spi.ShutdownStrategy} with a specified timeout.
     * <p/>
     * Suspending a route is more gently than stopping, as the route consumers will be suspended (if they support)
     * otherwise the consumers will be stopped.
     * <p/>
     * By suspending the route services will be kept running (if possible) and therefore its faster to resume the route.
     * <p/>
     * If the route does <b>not</b> support suspension the route will be stopped instead
     *
     * @param  routeId   the route id
     * @param  timeout   timeout
     * @param  timeUnit  the unit to use
     * @throws Exception is thrown if the route could not be suspended for whatever reason
     */
    void suspendRoute(String routeId, long timeout, TimeUnit timeUnit) throws Exception;

    /**
     * Resumes the given route if it has been previously suspended
     * <p/>
     * If the route does <b>not</b> support suspension the route will be started instead
     *
     * @param  routeId   the route id
     * @throws Exception is thrown if the route could not be resumed for whatever reason
     */
    void resumeRoute(String routeId) throws Exception;

}
