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
package org.apache.camel.core.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;

import org.apache.camel.model.IdentifiedType;
import org.apache.camel.spi.Metadata;

/**
 * Route controller configuration.
 */
@Metadata(label = "spring,configuration")
@XmlRootElement(name = "routeController")
@XmlAccessorType(XmlAccessType.FIELD)
public class CamelRouteControllerDefinition extends IdentifiedType {

    @XmlAttribute
    @Metadata(defaultValue = "false", javaType = "java.lang.Boolean")
    private String supervising;
    @XmlAttribute
    private String includeRoutes;
    @XmlAttribute
    private String excludeRoutes;
    @XmlAttribute
    @Metadata(defaultValue = "1")
    private String threadPoolSize;
    @XmlAttribute
    private String initialDelay;
    @XmlAttribute
    @Metadata(defaultValue = "2000")
    private String backOffDelay;
    @XmlAttribute
    private String backOffMaxDelay;
    @XmlAttribute
    private String backOffMaxElapsedTime;
    @XmlAttribute
    private String backOffMaxAttempts;
    @XmlAttribute
    @Metadata(defaultValue = "1.0")
    private String backOffMultiplier;
    @XmlAttribute
    @Metadata(defaultValue = "false", javaType = "java.lang.Boolean")
    private String unhealthyOnExhausted;
    @XmlAttribute
    @Metadata(defaultValue = "false", javaType = "java.lang.Boolean")
    private String unhealthyOnRestarting;
    @XmlAttribute
    @Metadata(javaType = "org.apache.camel.LoggingLevel", defaultValue = "DEBUG", enums = "TRACE,DEBUG,INFO,WARN,ERROR,OFF")
    private String loggingLevel;

    public String getSupervising() {
        return supervising;
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
    public void setSupervising(String supervising) {
        this.supervising = supervising;
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

    public String getThreadPoolSize() {
        return threadPoolSize;
    }

    /**
     * The number of threads used by the scheduled thread pool that are used for restarting routes. The pool uses 1
     * thread by default, but you can increase this to allow the controller to concurrently attempt to restart multiple
     * routes in case more than one route has problems starting.
     */
    public void setThreadPoolSize(String threadPoolSize) {
        this.threadPoolSize = threadPoolSize;
    }

    public String getInitialDelay() {
        return initialDelay;
    }

    /**
     * Initial delay in milli seconds before the route controller starts, after CamelContext has been started.
     */
    public void setInitialDelay(String initialDelay) {
        this.initialDelay = initialDelay;
    }

    public String getBackOffDelay() {
        return backOffDelay;
    }

    /**
     * Backoff delay in millis when restarting a route that failed to startup.
     */
    public void setBackOffDelay(String backOffDelay) {
        this.backOffDelay = backOffDelay;
    }

    public String getBackOffMaxDelay() {
        return backOffMaxDelay;
    }

    /**
     * Backoff maximum delay in millis when restarting a route that failed to startup.
     */
    public void setBackOffMaxDelay(String backOffMaxDelay) {
        this.backOffMaxDelay = backOffMaxDelay;
    }

    public String getBackOffMaxElapsedTime() {
        return backOffMaxElapsedTime;
    }

    /**
     * Backoff maximum elapsed time in millis, after which the backoff should be considered exhausted and no more
     * attempts should be made.
     */
    public void setBackOffMaxElapsedTime(String backOffMaxElapsedTime) {
        this.backOffMaxElapsedTime = backOffMaxElapsedTime;
    }

    public String getBackOffMaxAttempts() {
        return backOffMaxAttempts;
    }

    /**
     * Backoff maximum number of attempts to restart a route that failed to startup. When this threshold has been
     * exceeded then the controller will give up attempting to restart the route, and the route will remain as stopped.
     */
    public void setBackOffMaxAttempts(String backOffMaxAttempts) {
        this.backOffMaxAttempts = backOffMaxAttempts;
    }

    public String getBackOffMultiplier() {
        return backOffMultiplier;
    }

    /**
     * Backoff multiplier to use for exponential backoff. This is used to extend the delay between restart attempts.
     */
    public void setBackOffMultiplier(String backOffMultiplier) {
        this.backOffMultiplier = backOffMultiplier;
    }

    public String getUnhealthyOnExhausted() {
        return unhealthyOnExhausted;
    }

    /**
     * Whether to mark the route as unhealthy (down) when all restarting attempts (backoff) have failed and the route is
     * not successfully started and the route manager is giving up.
     *
     * Setting this to true allows health checks to know about this and can report the Camel application as DOWN.
     */
    public void setUnhealthyOnExhausted(String unhealthyOnExhausted) {
        this.unhealthyOnExhausted = unhealthyOnExhausted;
    }

    public String getUnhealthyOnRestarting() {
        return unhealthyOnRestarting;
    }

    /**
     * Whether to mark the route as unhealthy (down) when the route failed to initially start, and is being controlled
     * for restarting (backoff).
     *
     * Setting this to true allows health checks to know about this and can report the Camel application as DOWN.
     */
    public void setUnhealthyOnRestarting(String unhealthyOnRestarting) {
        this.unhealthyOnRestarting = unhealthyOnRestarting;
    }

    public String getLoggingLevel() {
        return loggingLevel;
    }

    /**
     * Sets the logging level used for logging route activity (such as starting and stopping routes). The default
     * logging level is DEBUG.
     */
    public void setLoggingLevel(String loggingLevel) {
        this.loggingLevel = loggingLevel;
    }
}
