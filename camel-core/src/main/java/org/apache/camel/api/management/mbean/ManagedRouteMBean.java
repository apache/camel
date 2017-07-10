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
package org.apache.camel.api.management.mbean;

import org.apache.camel.Experimental;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.spi.RouteError;

public interface ManagedRouteMBean extends ManagedPerformanceCounterMBean {

    @ManagedAttribute(description = "Route ID")
    String getRouteId();

    @ManagedAttribute(description = "Route Description")
    String getDescription();

    @ManagedAttribute(description = "Route Endpoint URI", mask = true)
    String getEndpointUri();

    @ManagedAttribute(description = "Route State")
    String getState();

    @ManagedAttribute(description = "Route Uptime [human readable text]")
    String getUptime();

    @ManagedAttribute(description = "Route Uptime [milliseconds]")
    long getUptimeMillis();

    /**
     * @deprecated use {@link #getExchangesInflight()}
     */
    @ManagedAttribute(description = "Current number of inflight Exchanges")
    @Deprecated
    Integer getInflightExchanges();

    @ManagedAttribute(description = "Camel ID")
    String getCamelId();

    @ManagedAttribute(description = "Camel ManagementName")
    String getCamelManagementName();

    @ManagedAttribute(description = "Tracing")
    Boolean getTracing();

    @ManagedAttribute(description = "Tracing")
    void setTracing(Boolean tracing);

    @ManagedAttribute(description = "Message History")
    Boolean getMessageHistory();

    @ManagedAttribute(description = "Route Policy List")
    String getRoutePolicyList();

    @ManagedAttribute(description = "Average load over the last minute")
    String getLoad01();

    @ManagedAttribute(description = "Average load over the last five minutes")
    String getLoad05();

    @ManagedAttribute(description = "Average load over the last fifteen minutes")
    String getLoad15();

    @ManagedOperation(description = "Start route")
    void start() throws Exception;

    @ManagedOperation(description = "Stop route")
    void stop() throws Exception;

    @ManagedOperation(description = "Stop route (using timeout in seconds)")
    void stop(long timeout) throws Exception;

    @ManagedOperation(description = "Stop route, abort stop after timeout (in seconds)")
    boolean stop(Long timeout, Boolean abortAfterTimeout) throws Exception;

    /**
     * @deprecated will be removed in the near future. Use stop and remove instead
     */
    @ManagedOperation(description = "Shutdown route")
    @Deprecated
    void shutdown() throws Exception;

    /**
     * @deprecated will be removed in the near future. Use stop and remove instead
     */
    @ManagedOperation(description = "Shutdown route (using timeout in seconds)")
    @Deprecated
    void shutdown(long timeout) throws Exception;

    @ManagedOperation(description = "Remove route (must be stopped)")
    boolean remove() throws Exception;

    @ManagedOperation(description = "Dumps the route as XML")
    String dumpRouteAsXml() throws Exception;

    @ManagedOperation(description = "Dumps the route as XML")
    String dumpRouteAsXml(boolean resolvePlaceholders) throws Exception;

    @ManagedOperation(description = "Updates the route from XML")
    void updateRouteFromXml(String xml) throws Exception;

    @ManagedOperation(description = "Dumps the routes stats as XML")
    String dumpRouteStatsAsXml(boolean fullStats, boolean includeProcessors) throws Exception;

    @ManagedOperation(description = "Reset counters")
    void reset(boolean includeProcessors) throws Exception;

    @ManagedOperation(description = "Returns the JSON representation of all the static and dynamic endpoints defined in this route")
    String createRouteStaticEndpointJson();

    @ManagedOperation(description = "Returns the JSON representation of all the static endpoints (and possible dynamic) defined in this route")
    String createRouteStaticEndpointJson(boolean includeDynamic);

    @ManagedAttribute(description = "Oldest inflight exchange duration")
    Long getOldestInflightDuration();

    @ManagedAttribute(description = "Oldest inflight exchange id")
    String getOldestInflightExchangeId();

    @Experimental
    @ManagedAttribute(description = "Route controller")
    Boolean getHasRouteController();

    @Experimental
    @ManagedAttribute(description = "Last error")
    RouteError getLastError();
}