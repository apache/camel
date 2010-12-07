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
import org.apache.camel.Route;
import org.apache.camel.Service;
import org.apache.camel.ServiceStatus;
import org.apache.camel.SuspendableService;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.spi.ManagementStrategy;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;

@ManagedResource(description = "Managed Service")
public class ManagedService implements ManagedInstance {
    private final CamelContext context;
    private final Service service;
    private Route route;

    public ManagedService(CamelContext context, Service service) {
        this.context = context;
        this.service = service;
    }

    public void init(ManagementStrategy strategy) {
        // do nothing
    }

    public Service getService() {
        return service;
    }

    public CamelContext getContext() {
        return context;
    }

    public Route getRoute() {
        return route;
    }

    public void setRoute(Route route) {
        this.route = route;
    }

    @ManagedAttribute(description = "Service State")
    public String getState() {
        // must use String type to be sure remote JMX can read the attribute without requiring Camel classes.
        if (service instanceof ServiceSupport) {
            ServiceStatus status = ((ServiceSupport) service).getStatus();
            // if no status exists then its stopped
            if (status == null) {
                status = ServiceStatus.Stopped;
            }
            return status.name();
        }

        // assume started if not a ServiceSupport instance
        return ServiceStatus.Started.name();
    }

    @ManagedAttribute(description = "Camel id")
    public String getCamelId() {
        return context.getName();
    }

    @ManagedAttribute(description = "Route id")
    public String getRouteId() {
        if (route != null) {
            return route.getId();
        }
        return null;
    }

    @ManagedOperation(description = "Start Service")
    public void start() throws Exception {
        if (!context.getStatus().isStarted()) {
            throw new IllegalArgumentException("CamelContext is not started");
        }
        service.start();
    }

    @ManagedOperation(description = "Stop Service")
    public void stop() throws Exception {
        if (!context.getStatus().isStarted()) {
            throw new IllegalArgumentException("CamelContext is not started");
        }
        service.stop();
    }

    @ManagedAttribute(description = "Whether this service supports suspension")
    public boolean isSupportSuspension() {
        return service instanceof SuspendableService;
    }

    @ManagedAttribute(description = "Whether this service is suspended")
    public boolean isSuspended() {
        if (service instanceof SuspendableService) {
            SuspendableService ss = (SuspendableService) service;
            return ss.isSuspended();
        } else {
            return false;
        }
    }

    @ManagedOperation(description = "Suspend Service")
    public void suspend() throws Exception {
        if (!context.getStatus().isStarted()) {
            throw new IllegalArgumentException("CamelContext is not started");
        }
        if (service instanceof SuspendableService) {
            SuspendableService ss = (SuspendableService) service;
            ss.suspend();
        } else {
            throw new UnsupportedOperationException("suspend() is not a supported operation");
        }
    }

    @ManagedOperation(description = "Resume Service")
    public void resume() throws Exception {
        if (!context.getStatus().isStarted()) {
            throw new IllegalArgumentException("CamelContext is not started");
        }
        if (service instanceof SuspendableService) {
            SuspendableService ss = (SuspendableService) service;
            ss.resume();
        } else {
            throw new UnsupportedOperationException("resume() is not a supported operation");
        }
    }

    public Object getInstance() {
        return service;
    }
}
