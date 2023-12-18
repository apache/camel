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
import java.util.Set;

import org.apache.camel.Route;
import org.apache.camel.util.backoff.BackOffTimer;

/**
 * A supervising capable {@link RouteController} that delays the startup of the routes after the camel context startup
 * and takes control of starting the routes in a safe manner. This controller is able to retry starting failing routes,
 * and have various options to configure settings for backoff between restarting routes.
 */
public interface SupervisingRouteController extends RouteController {

    String getIncludeRoutes();

    /**
     * Pattern for filtering routes to be included as supervised.
     *
     * The pattern is matching on route id, and endpoint uri for the route. Multiple patterns can be separated by comma.
     *
     * For example to include all kafka routes, you can say <tt>kafka:*</tt>. And to include routes with specific route
     * ids <tt>myRoute,myOtherRoute</tt>. The pattern supports wildcards and uses the matcher from
     * org.apache.camel.support.PatternHelper#matchPattern.
     */
    void setIncludeRoutes(String includeRoutes);

    String getExcludeRoutes();

    /**
     * Pattern for filtering routes to be excluded as supervised.
     *
     * The pattern is matching on route id, and endpoint uri for the route. Multiple patterns can be separated by comma.
     *
     * For example to exclude all JMS routes, you can say <tt>jms:*</tt>. And to exclude routes with specific route ids
     * <tt>mySpecialRoute,myOtherSpecialRoute</tt>. The pattern supports wildcards and uses the matcher from
     * org.apache.camel.support.PatternHelper#matchPattern.
     */
    void setExcludeRoutes(String excludeRoutes);

    int getThreadPoolSize();

    /**
     * The number of threads used by the scheduled thread pool that are used for restarting routes. The pool uses 1
     * thread by default, but you can increase this to allow the controller to concurrently attempt to restart multiple
     * routes in case more than one route has problems starting.
     */
    void setThreadPoolSize(int threadPoolSize);

    long getInitialDelay();

    /**
     * Initial delay in milli seconds before the route controller starts, after CamelContext has been started.
     */
    void setInitialDelay(long initialDelay);

    long getBackOffDelay();

    /**
     * Backoff delay in millis when restarting a route that failed to startup.
     */
    void setBackOffDelay(long backOffDelay);

    long getBackOffMaxDelay();

    /**
     * Backoff maximum delay in millis when restarting a route that failed to startup.
     */
    void setBackOffMaxDelay(long backOffMaxDelay);

    long getBackOffMaxElapsedTime();

    /**
     * Backoff maximum elapsed time in millis, after which the backoff should be considered exhausted and no more
     * attempts should be made.
     */
    void setBackOffMaxElapsedTime(long backOffMaxElapsedTime);

    long getBackOffMaxAttempts();

    /**
     * Backoff maximum number of attempts to restart a route that failed to startup. When this threshold has been
     * exceeded then the controller will give up attempting to restart the route, and the route will remain as stopped.
     */
    void setBackOffMaxAttempts(long backOffMaxAttempts);

    double getBackOffMultiplier();

    /**
     * Backoff multiplier to use for exponential backoff. This is used to extend the delay between restart attempts.
     */
    void setBackOffMultiplier(double backOffMultiplier);

    /**
     * Whether to mark the route as unhealthy (down) when all restarting attempts (backoff) have failed and the route is
     * not successfully started and the route manager is giving up.
     *
     * Setting this to true allows health checks to know about this and can report the Camel application as DOWN.
     *
     * The default is false.
     */
    void setUnhealthyOnExhausted(boolean unhealthyOnExhausted);

    /**
     * Whether to mark the route as unhealthy (down) when all restarting attempts (backoff) have failed and the route is
     * not successfully started and the route manager is giving up.
     *
     * Setting this to true allows health checks to know about this and can report the Camel application as DOWN.
     *
     * The default is false.
     */
    boolean isUnhealthyOnExhausted();

    boolean isUnhealthyOnRestarting();

    /**
     * Whether to mark the route as unhealthy (down) when the route failed to initially start, and is being controlled
     * for restarting (backoff).
     *
     * Setting this to true allows health checks to know about this and can report the Camel application as DOWN.
     *
     * The default is false.
     */
    void setUnhealthyOnRestarting(boolean unhealthyOnRestarting);

    /**
     * Return the list of routes that are currently under restarting by this controller.
     *
     * In other words the routes which has failed during startup and are know managed to be restarted.
     */
    Collection<Route> getRestartingRoutes();

    /**
     * Return the list of routes that have failed all attempts to startup and are now exhausted.
     */
    Collection<Route> getExhaustedRoutes();

    /**
     * Returns the route ids of routes which are non controlled (such as routes that was excluded)
     */
    Set<String> getNonControlledRouteIds();

    /**
     * Gets the state of the backoff for the given route if its managed and under restarting.
     *
     * @param  routeId the route id
     * @return         the state, or <tt>null</tt> if the route is not under restarting
     */
    BackOffTimer.Task getRestartingRouteState(String routeId);

    /**
     * Gets the last exception that caused the route to not startup for the given route
     *
     * @param  routeId the route id
     * @return         the caused exception
     */
    Throwable getRestartException(String routeId);

    /**
     * Whether the route controller is currently starting routes for the first time. This only reports on the first time
     * start phase.
     */
    boolean isStartingRoutes();

}
