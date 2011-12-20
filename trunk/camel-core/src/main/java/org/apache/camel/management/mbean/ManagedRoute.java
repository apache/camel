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
package org.apache.camel.management.mbean;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.ManagementStatisticsLevel;
import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.apache.camel.TimerListener;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.ModelHelper;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.util.ObjectHelper;

@ManagedResource(description = "Managed Route")
public class ManagedRoute extends ManagedPerformanceCounter implements TimerListener, ManagedRouteMBean {
    public static final String VALUE_UNKNOWN = "Unknown";
    protected final Route route;
    protected final String description;
    protected final ModelCamelContext context;
    private final LoadTriplet load = new LoadTriplet();

    public ManagedRoute(ModelCamelContext context, Route route) {
        this.route = route;
        this.context = context;
        this.description = route.toString();
        boolean enabled = context.getManagementStrategy().getStatisticsLevel() != ManagementStatisticsLevel.Off;
        setStatisticsEnabled(enabled);
    }

    public Route getRoute() {
        return route;
    }

    public CamelContext getContext() {
        return context;
    }

    public String getRouteId() {
        String id = route.getId();
        if (id == null) {
            id = VALUE_UNKNOWN;
        }
        return id;
    }

    public String getDescription() {
        return description;
    }

    public String getEndpointUri() {
        Endpoint ep = route.getEndpoint();
        return ep != null ? ep.getEndpointUri() : VALUE_UNKNOWN;
    }

    public String getState() {
        // must use String type to be sure remote JMX can read the attribute without requiring Camel classes.
        ServiceStatus status = context.getRouteStatus(route.getId());
        // if no status exists then its stopped
        if (status == null) {
            status = ServiceStatus.Stopped;
        }
        return status.name();
    }

    public Integer getInflightExchanges() {
        if (route.getEndpoint() != null) {
            return context.getInflightRepository().size(route.getEndpoint());
        } else {
            return null;
        }
    }

    public String getCamelId() {
        return context.getName();
    }

    public Boolean getTracing() {
        return route.getRouteContext().isTracing();
    }

    public void setTracing(Boolean tracing) {
        route.getRouteContext().setTracing(tracing);
    }

    public String getRoutePolicyList() {
        List<RoutePolicy> policyList = route.getRouteContext().getRoutePolicyList();

        if (policyList == null || policyList.isEmpty()) {
            // return an empty string to have it displayed nicely in JMX consoles
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < policyList.size(); i++) {
            RoutePolicy policy = policyList.get(i);
            sb.append(policy.getClass().getSimpleName());
            sb.append("(").append(ObjectHelper.getIdentityHashCode(policy)).append(")");
            if (i < policyList.size() - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    public String getLoad01() {
        return String.format("%.2f", load.getLoad1());
    }

    public String getLoad05() {
        return String.format("%.2f", load.getLoad5());
    }

    public String getLoad15() {
        return String.format("%.2f", load.getLoad15());
    }

    @Override
    public void onTimer() {
        load.update(getInflightExchanges());
    }
    
    public void start() throws Exception {
        if (!context.getStatus().isStarted()) {
            throw new IllegalArgumentException("CamelContext is not started");
        }
        context.startRoute(getRouteId());
    }

    public void stop() throws Exception {
        if (!context.getStatus().isStarted()) {
            throw new IllegalArgumentException("CamelContext is not started");
        }
        context.stopRoute(getRouteId());
    }

    public void stop(long timeout) throws Exception {
        if (!context.getStatus().isStarted()) {
            throw new IllegalArgumentException("CamelContext is not started");
        }
        context.stopRoute(getRouteId(), timeout, TimeUnit.SECONDS);
    }

    public boolean stop(Long timeout, Boolean abortAfterTimeout) throws Exception {
        if (!context.getStatus().isStarted()) {
            throw new IllegalArgumentException("CamelContext is not started");
        }
        return context.stopRoute(getRouteId(), timeout, TimeUnit.SECONDS, abortAfterTimeout);
    }

    public void shutdown() throws Exception {
        if (!context.getStatus().isStarted()) {
            throw new IllegalArgumentException("CamelContext is not started");
        }
        context.shutdownRoute(getRouteId());
    }

    public void shutdown(long timeout) throws Exception {
        if (!context.getStatus().isStarted()) {
            throw new IllegalArgumentException("CamelContext is not started");
        }
        context.shutdownRoute(getRouteId(), timeout, TimeUnit.SECONDS);
    }

    public boolean remove() throws Exception {
        if (!context.getStatus().isStarted()) {
            throw new IllegalArgumentException("CamelContext is not started");
        }
        return context.removeRoute(getRouteId());
    }

    public String dumpRouteAsXml() throws Exception {
        String id = route.getId();
        RouteDefinition def = context.getRouteDefinition(id);
        if (def != null) {
            return ModelHelper.dumpModelAsXml(def);
        }
        return null;
    }

    public void updateRouteFromXml(String xml) throws Exception {
        // convert to model from xml
        RouteDefinition def = ModelHelper.createModelFromXml(xml, RouteDefinition.class);
        if (def == null) {
            return;
        }

        // add will remove existing route first
        context.addRouteDefinition(def);
    }

}
