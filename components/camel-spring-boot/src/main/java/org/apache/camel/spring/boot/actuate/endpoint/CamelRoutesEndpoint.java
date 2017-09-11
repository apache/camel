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

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.StatefulService;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.camel.spi.RouteError;
import org.apache.camel.spring.boot.actuate.endpoint.CamelRoutesEndpoint.RouteEndpointInfo;
import org.springframework.boot.actuate.endpoint.AbstractEndpoint;
import org.springframework.boot.actuate.endpoint.Endpoint;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@link Endpoint} to expose {@link org.apache.camel.Route} information.
 */
@ConfigurationProperties(prefix = "endpoints." + CamelRoutesEndpoint.ENDPOINT_ID)
public class CamelRoutesEndpoint extends AbstractEndpoint<List<RouteEndpointInfo>> {

    public static final String ENDPOINT_ID = "camelroutes";

    private CamelContext camelContext;

    public CamelRoutesEndpoint(CamelContext camelContext) {
        super(ENDPOINT_ID);
        this.camelContext = camelContext;
        // is enabled by default
        this.setEnabled(true);
    }

    @Override
    public List<RouteEndpointInfo> invoke() {
        return getRoutesInfo();
    }

    public RouteEndpointInfo getRouteInfo(String id) {
        Route route = camelContext.getRoute(id);
        if (route != null) {
            return new RouteEndpointInfo(route);
        }

        return null;
    }

    public List<RouteEndpointInfo> getRoutesInfo() {
        return camelContext.getRoutes().stream()
                .map(RouteEndpointInfo::new)
                .collect(Collectors.toList());
    }

    public RouteDetailsEndpointInfo getRouteDetailsInfo(String id) {
        Route route = camelContext.getRoute(id);
        if (route != null) {
            return new RouteDetailsEndpointInfo(camelContext, route);
        }

        return null;
    }

    public void startRoute(String id) throws Exception {
        camelContext.getRouteController().startRoute(id);
    }

    public void resetRoute(String id) throws Exception {
        ManagedRouteMBean managedRouteMBean = camelContext.getManagedRoute(id, ManagedRouteMBean.class);
        if (managedRouteMBean != null) {
            managedRouteMBean.reset(true);
        } 
    }

    public void stopRoute(String id, Optional<Long> timeout, Optional<TimeUnit> timeUnit, Optional<Boolean> abortAfterTimeout) throws Exception {
        if (timeout.isPresent()) {
            camelContext.getRouteController().stopRoute(id, timeout.get(), timeUnit.orElse(TimeUnit.SECONDS), abortAfterTimeout.orElse(Boolean.TRUE));
        } else {
            camelContext.getRouteController().stopRoute(id);
        }
    }

    public void suspendRoute(String id, Optional<Long> timeout, Optional<TimeUnit> timeUnit) throws Exception {
        if (timeout.isPresent()) {
            camelContext.getRouteController().suspendRoute(id, timeout.get(), timeUnit.orElse(TimeUnit.SECONDS));
        } else {
            camelContext.getRouteController().suspendRoute(id);
        }
    }

    public void resumeRoute(String id) throws Exception {
        camelContext.getRouteController().resumeRoute(id);
    }

    /**
     * Container for exposing {@link org.apache.camel.Route} information as JSON.
     */
    @JsonPropertyOrder({"id", "description", "uptime", "uptimeMillis"})
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class RouteEndpointInfo {

        private final String id;

        private final String description;

        private final String uptime;

        private final long uptimeMillis;

        private final String status;

        public RouteEndpointInfo(Route route) {
            this.id = route.getId();
            this.description = route.getDescription();
            this.uptime = route.getUptime();
            this.uptimeMillis = route.getUptimeMillis();

            if (route instanceof StatefulService) {
                this.status = ((StatefulService) route).getStatus().name();
            } else {
                this.status = null;
            }
        }

        public String getId() {
            return id;
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
                this.routeDetails = new RouteDetails(camelContext.getManagedRoute(route.getId(), ManagedRouteMBean.class));
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

}
