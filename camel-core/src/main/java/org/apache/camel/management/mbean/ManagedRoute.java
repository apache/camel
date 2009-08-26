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

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.apache.camel.spi.ManagementStrategy;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;

@ManagedResource(description = "Managed Route")
public class ManagedRoute extends ManagedPerformanceCounter {

    public static final String VALUE_UNKNOWN = "Unknown";
    private Route route;
    private String description;
    private CamelContext context;

    public ManagedRoute(ManagementStrategy strategy, CamelContext context, Route route) {
        super(strategy);
        this.route = route;
        this.context = context;
        this.description = route.toString();
    }

    public Route getRoute() {
        return route;
    }

    public CamelContext getContext() {
        return context;
    }

    @ManagedAttribute(description = "Route id")
    public String getId() {
        String id = route.getId();
        if (id == null) {
            id = VALUE_UNKNOWN;
        }
        return id;
    }

    @ManagedAttribute(description = "Route Description")
    public String getDescription() {
        return description;
    }

    @ManagedAttribute(description = "Route Endpoint Uri")
    public String getEndpointUri() {
        Endpoint ep = route.getEndpoint();
        return ep != null ? ep.getEndpointUri() : VALUE_UNKNOWN;
    }

    @ManagedAttribute(description = "Route state")
    public String getState() {
        // must use String type to be sure remote JMX can read the attribute without requiring Camel classes.
        ServiceStatus status = context.getRouteStatus(route.getId());
        // if no status exists then its stopped
        if (status == null) {
            status = ServiceStatus.Stopped;
        }
        return status.name();
    }

    @ManagedOperation(description = "Start Route")
    public void start() throws Exception {
        throw new IllegalArgumentException("Start not supported");
    }

    @ManagedOperation(description = "Stop Route")
    public void stop() throws Exception {
        throw new IllegalArgumentException("Stop not supported");
    }
}
