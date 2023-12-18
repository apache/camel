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
package org.apache.camel.api.management.mbean;

import java.util.Collection;

import javax.management.openmbean.TabularData;

import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;

public interface ManagedSupervisingRouteControllerMBean extends ManagedRouteControllerMBean {

    @ManagedAttribute(description = "Whether supervising is enabled")
    boolean isEnabled();

    @ManagedAttribute(description = "The number of threads used by the scheduled thread pool that are used for restarting routes")
    int getThreadPoolSize();

    @ManagedAttribute(description = "Initial delay in milli seconds before the route controller starts")
    long getInitialDelay();

    @ManagedAttribute(description = "Backoff delay in millis when restarting a route that failed to startup")
    long getBackOffDelay();

    @ManagedAttribute(description = "Backoff maximum delay in millis when restarting a route that failed to startup")
    long getBackOffMaxDelay();

    @ManagedAttribute(description = "Backoff maximum elapsed time in millis, after which the backoff should be considered exhausted and no more attempts should be made")
    long getBackOffMaxElapsedTime();

    @ManagedAttribute(description = "Backoff maximum number of attempts to restart a route that failed to startup")
    long getBackOffMaxAttempts();

    @ManagedAttribute(description = "Backoff multiplier to use for exponential backoff")
    double getBackOffMultiplier();

    @ManagedAttribute(description = "Pattern for filtering routes to be included as supervised")
    String getIncludeRoutes();

    @ManagedAttribute(description = "Pattern for filtering routes to be excluded as supervised")
    String getExcludeRoutes();

    @ManagedAttribute(description = "Whether to mark the route as unhealthy (down) when all restarting attempts (backoff) have failed and the route is not successfully started and the route manager is giving up.")
    boolean isUnhealthyOnExhausted();

    @ManagedAttribute(description = "Whether to mark the route as unhealthy (down) when the route failed to initially start, and is being controlled for restarting (backoff)")
    boolean isUnhealthyOnRestarting();

    @ManagedAttribute(description = "Number of routes controlled by the controller")
    int getNumberOfControlledRoutes();

    @ManagedAttribute(description = "Number of routes which have failed to startup and are currently managed to be restarted")
    int getNumberOfRestartingRoutes();

    @ManagedAttribute(description = "Number of routes which have failed all attempts to startup and are now exhausted")
    int getNumberOfExhaustedRoutes();

    @ManagedAttribute(description = "Exhausted routes")
    Collection<String> getExhaustedRoutes();

    @ManagedAttribute(description = "Routes that are restarting or scheduled to restart")
    Collection<String> getRestartingRoutes();

    @ManagedOperation(description = "Lists detailed status about all the routes (incl failure details for routes failed to start)")
    TabularData routeStatus(boolean exhausted, boolean restarting, boolean includeStacktrace);

}
