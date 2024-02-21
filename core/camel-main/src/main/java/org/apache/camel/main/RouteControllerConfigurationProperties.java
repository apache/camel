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
package org.apache.camel.main;

import org.apache.camel.spi.BootstrapCloseable;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;

/**
 * Route controller configuration.
 */
@Configurer(bootstrap = true)
public class RouteControllerConfigurationProperties implements BootstrapCloseable {

    private MainConfigurationProperties parent;

    @Metadata
    private boolean enabled;
    @Metadata
    private String includeRoutes;
    @Metadata
    private String excludeRoutes;
    @Metadata
    private boolean unhealthyOnExhausted;
    @Metadata
    private boolean unhealthyOnRestarting;
    @Metadata
    private long initialDelay;
    @Metadata(defaultValue = "2000")
    private long backOffDelay;
    @Metadata
    private long backOffMaxDelay;
    @Metadata
    private long backOffMaxElapsedTime;
    @Metadata
    private long backOffMaxAttempts;
    @Metadata
    private double backOffMultiplier;
    @Metadata(label = "advanced", defaultValue = "1")
    private int threadPoolSize;

    public RouteControllerConfigurationProperties(MainConfigurationProperties parent) {
        this.parent = parent;
    }

    public MainConfigurationProperties end() {
        return parent;
    }

    @Override
    public void close() {
        parent = null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * To enable using supervising route controller which allows Camel to startup and then the controller takes care of
     * starting the routes in a safe manner.
     *
     * This can be used when you want to startup Camel despite a route may otherwise fail fast during startup and cause
     * Camel to fail to startup as well. By delegating the route startup to the supervising route controller then its
     * manages the startup using a background thread. The controller allows to be configured with various settings to
     * attempt to restart failing routes.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getIncludeRoutes() {
        return includeRoutes;
    }

    /**
     * Pattern for filtering routes to be included as supervised.
     *
     * The pattern is matching on route id, and endpoint uri for the route. Multiple patterns can be separated by comma.
     *
     * For example to include all kafka routes, you can say <tt>kafka:*</tt>. And to include routes with specific route
     * ids <tt>myRoute,myOtherRoute</tt>. The pattern supports wildcards and uses the matcher from
     * org.apache.camel.support.PatternHelper#matchPattern.
     */
    public void setIncludeRoutes(String includeRoutes) {
        this.includeRoutes = includeRoutes;
    }

    public String getExcludeRoutes() {
        return excludeRoutes;
    }

    /**
     * Pattern for filtering routes to be excluded as supervised.
     *
     * The pattern is matching on route id, and endpoint uri for the route. Multiple patterns can be separated by comma.
     *
     * For example to exclude all JMS routes, you can say <tt>jms:*</tt>. And to exclude routes with specific route ids
     * <tt>mySpecialRoute,myOtherSpecialRoute</tt>. The pattern supports wildcards and uses the matcher from
     * org.apache.camel.support.PatternHelper#matchPattern.
     */
    public void setExcludeRoutes(String excludeRoutes) {
        this.excludeRoutes = excludeRoutes;
    }

    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    /**
     * The number of threads used by the route controller scheduled thread pool that are used for restarting routes. The
     * pool uses 1 thread by default, but you can increase this to allow the controller to concurrently attempt to
     * restart multiple routes in case more than one route has problems starting.
     */
    public void setThreadPoolSize(int threadPoolSize) {
        this.threadPoolSize = threadPoolSize;
    }

    public long getInitialDelay() {
        return initialDelay;
    }

    /**
     * Initial delay in milli seconds before the route controller starts, after CamelContext has been started.
     */
    public void setInitialDelay(long initialDelay) {
        this.initialDelay = initialDelay;
    }

    public long getBackOffDelay() {
        return backOffDelay;
    }

    /**
     * Backoff delay in millis when restarting a route that failed to startup.
     */
    public void setBackOffDelay(long backOffDelay) {
        this.backOffDelay = backOffDelay;
    }

    public long getBackOffMaxDelay() {
        return backOffMaxDelay;
    }

    /**
     * Backoff maximum delay in millis when restarting a route that failed to startup.
     */
    public void setBackOffMaxDelay(long backOffMaxDelay) {
        this.backOffMaxDelay = backOffMaxDelay;
    }

    public long getBackOffMaxElapsedTime() {
        return backOffMaxElapsedTime;
    }

    /**
     * Backoff maximum elapsed time in millis, after which the backoff should be considered exhausted and no more
     * attempts should be made.
     */
    public void setBackOffMaxElapsedTime(long backOffMaxElapsedTime) {
        this.backOffMaxElapsedTime = backOffMaxElapsedTime;
    }

    public long getBackOffMaxAttempts() {
        return backOffMaxAttempts;
    }

    /**
     * Backoff maximum number of attempts to restart a route that failed to startup. When this threshold has been
     * exceeded then the controller will give up attempting to restart the route, and the route will remain as stopped.
     */
    public void setBackOffMaxAttempts(long backOffMaxAttempts) {
        this.backOffMaxAttempts = backOffMaxAttempts;
    }

    public double getBackOffMultiplier() {
        return backOffMultiplier;
    }

    /**
     * Backoff multiplier to use for exponential backoff. This is used to extend the delay between restart attempts.
     */
    public void setBackOffMultiplier(double backOffMultiplier) {
        this.backOffMultiplier = backOffMultiplier;
    }

    public boolean isUnhealthyOnExhausted() {
        return unhealthyOnExhausted;
    }

    /**
     * Whether to mark the route as unhealthy (down) when all restarting attempts (backoff) have failed and the route is
     * not successfully started and the route manager is giving up.
     *
     * Setting this to true allows health checks to know about this and can report the Camel application as DOWN.
     *
     * The default is false.
     */
    public void setUnhealthyOnExhausted(boolean unhealthyOnExhausted) {
        this.unhealthyOnExhausted = unhealthyOnExhausted;
    }

    public boolean isUnhealthyOnRestarting() {
        return unhealthyOnRestarting;
    }

    /**
     * Whether to mark the route as unhealthy (down) when the route failed to initially start, and is being controlled
     * for restarting (backoff).
     *
     * Setting this to true allows health checks to know about this and can report the Camel application as DOWN.
     *
     * The default is false.
     */
    public void setUnhealthyOnRestarting(boolean unhealthyOnRestarting) {
        this.unhealthyOnRestarting = unhealthyOnRestarting;
    }

    /**
     * To enable using supervising route controller which allows Camel to startup and then the controller takes care of
     * starting the routes in a safe manner.
     *
     * This can be used when you want to startup Camel despite a route may otherwise fail fast during startup and cause
     * Camel to fail to startup as well. By delegating the route startup to the supervising route controller then its
     * manages the startup using a background thread. The controller allows to be configured with various settings to
     * attempt to restart failing routes.
     */
    public RouteControllerConfigurationProperties withEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /**
     * Initial delay in milli seconds before the route controller starts, after CamelContext has been started.
     */
    public RouteControllerConfigurationProperties withInitialDelay(long initialDelay) {
        this.initialDelay = initialDelay;
        return this;
    }

    /**
     * Backoff delay in millis when restarting a route that failed to startup.
     */
    public RouteControllerConfigurationProperties withBackOffDelay(long backOffDelay) {
        this.backOffDelay = backOffDelay;
        return this;
    }

    /**
     * Backoff maximum delay in millis when restarting a route that failed to startup.
     */
    public RouteControllerConfigurationProperties withBackOffMaxDelay(long backOffMaxDelay) {
        this.backOffMaxDelay = backOffMaxDelay;
        return this;
    }

    /**
     * Backoff maximum elapsed time in millis, after which the backoff should be considered exhausted and no more
     * attempts should be made.
     */
    public RouteControllerConfigurationProperties withBackOffMaxElapsedTime(long backOffMaxElapsedTime) {
        this.backOffMaxElapsedTime = backOffMaxElapsedTime;
        return this;
    }

    /**
     * Backoff maximum number of attempts to restart a route that failed to startup. When this threshold has been
     * exceeded then the controller will give up attempting to restart the route, and the route will remain as stopped.
     */
    public RouteControllerConfigurationProperties withBackOffMaxAttempts(long backOffMaxAttempts) {
        this.backOffMaxAttempts = backOffMaxAttempts;
        return this;
    }

    /**
     * Backoff multiplier to use for exponential backoff. This is used to extend the delay between restart attempts.
     */
    public RouteControllerConfigurationProperties withBackOffMultiplier(double backOffMultiplier) {
        this.backOffMultiplier = backOffMultiplier;
        return this;
    }

    /**
     * The number of threads used by the route controller scheduled thread pool that are used for restarting routes. The
     * pool uses 1 thread by default, but you can increase this to allow the controller to concurrently attempt to
     * restart multiple routes in case more than one route has problems starting.
     */
    public RouteControllerConfigurationProperties withThreadPoolSize(int threadPoolSize) {
        this.threadPoolSize = threadPoolSize;
        return this;
    }

    /**
     * Pattern for filtering routes to be included as supervised.
     *
     * The pattern is matching on route id, and endpoint uri for the route. Multiple patterns can be separated by comma.
     *
     * For example to include all kafka routes, you can say <tt>kafka:*</tt>. And to include routes with specific route
     * ids <tt>myRoute,myOtherRoute</tt>. The pattern supports wildcards and uses the matcher from
     * org.apache.camel.support.PatternHelper#matchPattern.
     */
    public RouteControllerConfigurationProperties withIncludeRoutes(String includeRoutes) {
        this.includeRoutes = includeRoutes;
        return this;
    }

    /**
     * Pattern for filtering routes to be excluded as supervised.
     *
     * The pattern is matching on route id, and endpoint uri for the route. Multiple patterns can be separated by comma.
     *
     * For example to exclude all JMS routes, you can say <tt>jms:*</tt>. And to exclude routes with specific route ids
     * <tt>mySpecialRoute,myOtherSpecialRoute</tt>. The pattern supports wildcards and uses the matcher from
     * org.apache.camel.support.PatternHelper#matchPattern.
     */
    public RouteControllerConfigurationProperties withExcludeRoutes(String excludeRoutes) {
        this.excludeRoutes = excludeRoutes;
        return this;
    }

    /**
     * Whether to mark the route as unhealthy (down) when all restarting attempts (backoff) have failed and the route is
     * not successfully started and the route manager is giving up.
     *
     * Setting this to true allows health checks to know about this and can report the Camel application as DOWN.
     *
     * The default is false.
     */
    public RouteControllerConfigurationProperties withUnhealthyOnExhausted(boolean unhealthyOnExhausted) {
        this.unhealthyOnExhausted = unhealthyOnExhausted;
        return this;
    }

    /**
     * Whether to mark the route as unhealthy (down) when the route failed to initially start, and is being controlled
     * for restarting (backoff).
     *
     * Setting this to true allows health checks to know about this and can report the Camel application as DOWN.
     *
     * The default is false.
     */
    public RouteControllerConfigurationProperties withUnhealthyOnRestarting(boolean unhealthyOnRestarting) {
        this.unhealthyOnRestarting = unhealthyOnRestarting;
        return this;
    }

}
