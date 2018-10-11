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
import org.apache.camel.StatefulService;
import org.apache.camel.StaticService;
import org.apache.camel.Suspendable;
import org.apache.camel.SuspendableService;
import org.apache.camel.api.management.ManagedInstance;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedServiceMBean;
import org.apache.camel.spi.ManagementStrategy;

@ManagedResource(description = "Managed Service")
public class ManagedService implements ManagedInstance, ManagedServiceMBean {
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

    public boolean isStaticService() {
        return service instanceof StaticService;
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

    public String getState() {
        // must use String type to be sure remote JMX can read the attribute without requiring Camel classes.
        if (service instanceof StatefulService) {
            ServiceStatus status = ((StatefulService) service).getStatus();
            return status.name();
        }

        // assume started if not a ServiceSupport instance
        return ServiceStatus.Started.name();
    }

    public String getCamelId() {
        return context.getName();
    }

    public String getCamelManagementName() {
        return context.getManagementName();
    }

    public String getRouteId() {
        if (route != null) {
            return route.getId();
        }
        return null;
    }

    public String getServiceType() {
        if (service != null) {
            return service.getClass().getSimpleName();
        }
        return null;
    }

    public void start() throws Exception {
        if (!context.getStatus().isStarted()) {
            throw new IllegalArgumentException("CamelContext is not started");
        }
        service.start();
    }

    public void stop() throws Exception {
        if (!context.getStatus().isStarted()) {
            throw new IllegalArgumentException("CamelContext is not started");
        }
        service.stop();
    }

    public boolean isSupportSuspension() {
        return service instanceof Suspendable && service instanceof SuspendableService;
    }

    public boolean isSuspended() {
        if (service instanceof SuspendableService) {
            SuspendableService ss = (SuspendableService) service;
            return ss.isSuspended();
        } else {
            return false;
        }
    }

    public void suspend() throws Exception {
        if (!context.getStatus().isStarted()) {
            throw new IllegalArgumentException("CamelContext is not started");
        }
        if (service instanceof Suspendable && service instanceof SuspendableService) {
            SuspendableService ss = (SuspendableService) service;
            ss.suspend();
        } else {
            throw new UnsupportedOperationException("suspend() is not a supported operation");
        }
    }

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
