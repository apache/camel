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
package org.apache.camel.spring.boot.actuate.endpoint;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.StatefulService;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.camel.api.management.mbean.RouteError;
import org.apache.camel.management.ManagedCamelContext;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.ModelHelper;
import org.apache.camel.model.RouteDefinition;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.lang.Nullable;


/**
 * {@link Endpoint} to expose {@link org.apache.camel.Route} information.
 */
@Endpoint(id = "camelroutes", enableByDefault = true)
public class CamelRoutesEndpoint {

    private CamelContext camelContext;
    private CamelRoutesEndpointProperties properties;

    public CamelRoutesEndpoint(CamelContext camelContext, CamelRoutesEndpointProperties properties) {
        this.camelContext = camelContext;
        this.properties = properties;
    }

    @ReadOperation
    public List<RouteEndpointInfo> readRoutes() {
        return getRoutesInfo();
    }

    @ReadOperation
    public Object doReadAction(@Selector String id, @Selector ReadAction action) {
        switch (action) {
        case DETAIL:
            return getRouteDetailsInfo(id);
        case INFO:
            return getRouteInfo(id);
        default:
            throw new IllegalArgumentException("Unsupported read action " + action);
        }
    }

    @WriteOperation
    public void doWriteAction(@Selector String id, @Selector WriteAction action, @Nullable TimeInfo timeInfo) {
        if (this.properties.isReadOnly()) {
            throw new IllegalArgumentException(String.format("Read only: write action %s is not allowed", action));
        }

        switch (action) {
        case STOP:
            stopRoute(
                    id,
                    Optional.ofNullable(timeInfo).flatMap(ti -> Optional.ofNullable(ti.getTimeout())),
                    Optional.of(TimeUnit.SECONDS),
                    Optional.ofNullable(timeInfo).flatMap(ti -> Optional.ofNullable(ti.getAbortAfterTimeout())));
            break;
        case START:
            startRoute(id);
            break;
        case RESET:
            resetRoute(id);
            break;
        case SUSPEND:
            suspendRoute(id,
                    Optional.ofNullable(timeInfo).flatMap(ti -> Optional.ofNullable(ti.getTimeout())),
                    Optional.of(TimeUnit.SECONDS));
            break;
        case RESUME:
            resumeRoute(id);
            break;
        default:
            throw new IllegalArgumentException("Unsupported write action " + action);
        }
    }

    @WriteOperation
    public String getRouteDump(@Selector String id) {
        if (this.properties.isReadOnly()) {
            throw new IllegalArgumentException("Read only: route dump is not permitted in read-only mode");
        }

        RouteDefinition route = camelContext.adapt(ModelCamelContext.class).getRouteDefinition(id);
        if (route != null) {
            try {
                return ModelHelper.dumpModelAsXml(camelContext, route);
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }
        return null;
    }

    private RouteEndpointInfo getRouteInfo(String id) {
        Route route = camelContext.getRoute(id);
        if (route != null) {
            return new RouteEndpointInfo(route);
        }

        return null;
    }

    private List<RouteEndpointInfo> getRoutesInfo() {
        return camelContext.getRoutes().stream()
                .map(RouteEndpointInfo::new)
                .collect(Collectors.toList());
    }

    private RouteDetailsEndpointInfo getRouteDetailsInfo(String id) {
        Route route = camelContext.getRoute(id);
        if (route != null) {
            return new RouteDetailsEndpointInfo(camelContext, route);
        }

        return null;
    }

    private void startRoute(String id) {
        try {
            camelContext.getRouteController().startRoute(id);
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }
    }

    private void resetRoute(String id) {
        try {
            ManagedRouteMBean managedRouteMBean = camelContext.adapt(ManagedCamelContext.class).getManagedRoute(id, ManagedRouteMBean.class);
            if (managedRouteMBean != null) {
                managedRouteMBean.reset(true);
            }
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }
    }

    private void stopRoute(String id, Optional<Long> timeout, Optional<TimeUnit> timeUnit, Optional<Boolean> abortAfterTimeout) {
        try {
            if (timeout.isPresent()) {
                camelContext.getRouteController().stopRoute(id, timeout.get(), timeUnit.orElse(TimeUnit.SECONDS), abortAfterTimeout.orElse(Boolean.TRUE));
            } else {
                camelContext.getRouteController().stopRoute(id);
            }
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }
    }

    private void suspendRoute(String id, Optional<Long> timeout, Optional<TimeUnit> timeUnit) {
        try {
            if (timeout.isPresent()) {
                camelContext.getRouteController().suspendRoute(id, timeout.get(), timeUnit.orElse(TimeUnit.SECONDS));
            } else {
                camelContext.getRouteController().suspendRoute(id);
            }
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }
    }

    private void resumeRoute(String id) {
        try {
            camelContext.getRouteController().resumeRoute(id);
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }
    }

    /**
     * Container for exposing {@link org.apache.camel.Route} information as JSON.
     */
    @JsonPropertyOrder({"id", "group", "description", "uptime", "uptimeMillis"})
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class RouteEndpointInfo {

        private final String id;
        
        private final String group;

        private final Map<String, Object> properties;

        private final String description;

        private final String uptime;

        private final long uptimeMillis;

        private final String status;

        public RouteEndpointInfo(Route route) {
            this.id = route.getId();
            this.group = route.getGroup();
            this.description = route.getDescription();
            this.uptime = route.getUptime();
            this.uptimeMillis = route.getUptimeMillis();

            if (route.getProperties() != null) {
                this.properties = new HashMap<>(route.getProperties());
            } else {
                this.properties = Collections.emptyMap();
            }

            if (route instanceof StatefulService) {
                this.status = ((StatefulService) route).getStatus().name();
            } else {
                this.status = null;
            }
        }

        public String getId() {
            return id;
        }
        
        public String getGroup() {
            return group;
        }

        public Map<String, Object> getProperties() {
            return properties;
        }

        public String getDescription() {
            return description;
        }

        public String getUptime() {
            return uptime;
        }

        public long getUptimeMillis() {
            return uptimeMillis;
        }

        public String getStatus() {
            return status;
        }
    }

    /**
     * Container for exposing {@link org.apache.camel.Route} information
     * with route details as JSON. Route details are retrieved from JMX.
     */
    public static class RouteDetailsEndpointInfo extends RouteEndpointInfo {

        @JsonProperty("details")
        private RouteDetails routeDetails;

        public RouteDetailsEndpointInfo(final CamelContext camelContext, final Route route) {
            super(route);

            if (camelContext.getManagementStrategy().getManagementAgent() != null) {
                this.routeDetails = new RouteDetails(camelContext.adapt(ManagedCamelContext.class).getManagedRoute(route.getId(), ManagedRouteMBean.class));
            }
        }

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        static class RouteDetails {

            private long deltaProcessingTime;

            private long exchangesInflight;

            private long exchangesTotal;

            private long externalRedeliveries;

            private long failuresHandled;

            private String firstExchangeCompletedExchangeId;

            private Date firstExchangeCompletedTimestamp;

            private String firstExchangeFailureExchangeId;

            private Date firstExchangeFailureTimestamp;

            private String lastExchangeCompletedExchangeId;

            private Date lastExchangeCompletedTimestamp;

            private String lastExchangeFailureExchangeId;

            private Date lastExchangeFailureTimestamp;

            private long lastProcessingTime;

            private String load01;

            private String load05;

            private String load15;

            private long maxProcessingTime;

            private long meanProcessingTime;

            private long minProcessingTime;

            private Long oldestInflightDuration;

            private String oldestInflightExchangeId;

            private long redeliveries;

            private long totalProcessingTime;

            private RouteError lastError;

            private boolean hasRouteController;

            RouteDetails(ManagedRouteMBean managedRoute) {
                try {
                    this.deltaProcessingTime = managedRoute.getDeltaProcessingTime();
                    this.exchangesInflight = managedRoute.getExchangesInflight();
                    this.exchangesTotal = managedRoute.getExchangesTotal();
                    this.externalRedeliveries = managedRoute.getExternalRedeliveries();
                    this.failuresHandled = managedRoute.getFailuresHandled();
                    this.firstExchangeCompletedExchangeId = managedRoute.getFirstExchangeCompletedExchangeId();
                    this.firstExchangeCompletedTimestamp = managedRoute.getFirstExchangeCompletedTimestamp();
                    this.firstExchangeFailureExchangeId = managedRoute.getFirstExchangeFailureExchangeId();
                    this.firstExchangeFailureTimestamp = managedRoute.getFirstExchangeFailureTimestamp();
                    this.lastExchangeCompletedExchangeId = managedRoute.getLastExchangeCompletedExchangeId();
                    this.lastExchangeCompletedTimestamp = managedRoute.getLastExchangeCompletedTimestamp();
                    this.lastExchangeFailureExchangeId = managedRoute.getLastExchangeFailureExchangeId();
                    this.lastExchangeFailureTimestamp = managedRoute.getLastExchangeFailureTimestamp();
                    this.lastProcessingTime = managedRoute.getLastProcessingTime();
                    this.load01 = managedRoute.getLoad01();
                    this.load05 = managedRoute.getLoad05();
                    this.load15 = managedRoute.getLoad15();
                    this.maxProcessingTime = managedRoute.getMaxProcessingTime();
                    this.meanProcessingTime = managedRoute.getMeanProcessingTime();
                    this.minProcessingTime = managedRoute.getMinProcessingTime();
                    this.oldestInflightDuration = managedRoute.getOldestInflightDuration();
                    this.oldestInflightExchangeId = managedRoute.getOldestInflightExchangeId();
                    this.redeliveries = managedRoute.getRedeliveries();
                    this.totalProcessingTime = managedRoute.getTotalProcessingTime();
                    this.lastError = managedRoute.getLastError();
                    this.hasRouteController = managedRoute.getHasRouteController();
                } catch (Exception e) {
                    // Ignore
                }
            }

            public long getDeltaProcessingTime() {
                return deltaProcessingTime;
            }

            public long getExchangesInflight() {
                return exchangesInflight;
            }

            public long getExchangesTotal() {
                return exchangesTotal;
            }

            public long getExternalRedeliveries() {
                return externalRedeliveries;
            }

            public long getFailuresHandled() {
                return failuresHandled;
            }

            public String getFirstExchangeCompletedExchangeId() {
                return firstExchangeCompletedExchangeId;
            }

            public Date getFirstExchangeCompletedTimestamp() {
                return firstExchangeCompletedTimestamp;
            }

            public String getFirstExchangeFailureExchangeId() {
                return firstExchangeFailureExchangeId;
            }

            public Date getFirstExchangeFailureTimestamp() {
                return firstExchangeFailureTimestamp;
            }

            public String getLastExchangeCompletedExchangeId() {
                return lastExchangeCompletedExchangeId;
            }

            public Date getLastExchangeCompletedTimestamp() {
                return lastExchangeCompletedTimestamp;
            }

            public String getLastExchangeFailureExchangeId() {
                return lastExchangeFailureExchangeId;
            }

            public Date getLastExchangeFailureTimestamp() {
                return lastExchangeFailureTimestamp;
            }

            public long getLastProcessingTime() {
                return lastProcessingTime;
            }

            public String getLoad01() {
                return load01;
            }

            public String getLoad05() {
                return load05;
            }

            public String getLoad15() {
                return load15;
            }

            public long getMaxProcessingTime() {
                return maxProcessingTime;
            }

            public long getMeanProcessingTime() {
                return meanProcessingTime;
            }

            public long getMinProcessingTime() {
                return minProcessingTime;
            }

            public Long getOldestInflightDuration() {
                return oldestInflightDuration;
            }

            public String getOldestInflightExchangeId() {
                return oldestInflightExchangeId;
            }

            public long getRedeliveries() {
                return redeliveries;
            }

            public long getTotalProcessingTime() {
                return totalProcessingTime;
            }

            public RouteError getLastError() {
                return lastError;
            }

            public boolean getHasRouteController() {
                return hasRouteController;
            }
        }
    }

    /**
     * List of write actions available for the endpoint
     */
    public enum WriteAction {
        STOP,
        START,
        RESET,
        SUSPEND,
        RESUME
    }

    /*
     * List of read actions available for the endpoint
     */
    public enum ReadAction {
        DETAIL,
        INFO
    }

    /**
     * Optional time information for the actions
     */
    public static class TimeInfo {
        private Long timeout;
        private Boolean abortAfterTimeout;

        public Long getTimeout() {
            return timeout;
        }

        public void setTimeout(Long timeout) {
            this.timeout = timeout;
        }

        public Boolean getAbortAfterTimeout() {
            return abortAfterTimeout;
        }

        public void setAbortAfterTimeout(Boolean abortAfterTimeout) {
            this.abortAfterTimeout = abortAfterTimeout;
        }
    }

}
