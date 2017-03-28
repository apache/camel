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
package org.apache.camel.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.Route;
import org.apache.camel.Service;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.TimeUtils;

/**
 * Default implementation of {@link Route}.
 * <p/>
 * Use the API from {@link org.apache.camel.CamelContext} to control the lifecycle of a route,
 * such as starting and stopping using the {@link org.apache.camel.CamelContext#startRoute(String)}
 * and {@link org.apache.camel.CamelContext#stopRoute(String)} methods.
 *
 * @version 
 */
public abstract class DefaultRoute extends ServiceSupport implements Route {

    private final Endpoint endpoint;
    private final Map<String, Object> properties = new HashMap<String, Object>();
    private final List<Service> services = new ArrayList<Service>();
    private final RouteContext routeContext;
    private Date startDate;

    public DefaultRoute(RouteContext routeContext, Endpoint endpoint) {
        this.routeContext = routeContext;
        this.endpoint = endpoint;
    }

    public DefaultRoute(RouteContext routeContext, Endpoint endpoint, Service... services) {
        this(routeContext, endpoint);
        for (Service service : services) {
            addService(service);
        }
    }

    @Override
    public String toString() {
        return "Route " + getId();
    }

    public String getId() {
        return (String) properties.get(Route.ID_PROPERTY);
    }

    public String getUptime() {
        long delta = getUptimeMillis();
        if (delta == 0) {
            return "";
        }
        return TimeUtils.printDuration(delta);
    }

    public long getUptimeMillis() {
        if (startDate == null) {
            return 0;
        }
        return new Date().getTime() - startDate.getTime();
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public RouteContext getRouteContext() {
        return routeContext;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public String getDescription() {
        Object value = properties.get(Route.DESCRIPTION_PROPERTY);
        return value != null ? value.toString() : null;
    }

    public void onStartingServices(List<Service> services) throws Exception {
        addServices(services);
    }

    public List<Service> getServices() {
        return services;
    }

    public void addService(Service service) {
        if (!services.contains(service)) {
            services.add(service);
        }
    }

    public void warmUp() {
        getServices().clear();
    }

    /**
     * Do not invoke this method directly, use {@link org.apache.camel.CamelContext#startRoute(String)} to start a route.
     */
    @Override
    public void start() throws Exception {
        super.start();
    }

    /**
     * Do not invoke this method directly, use {@link org.apache.camel.CamelContext#stopRoute(String)} to stop a route.
     */
    @Override
    public void stop() throws Exception {
        super.stop();
    }

    /**
     * Strategy method to allow derived classes to lazily load services for the route
     */
    protected void addServices(List<Service> services) throws Exception {
    }

    protected void doStart() throws Exception {
        startDate = new Date();
    }

    protected void doStop() throws Exception {
        // and clear start date
        startDate = null;
    }

    @Override
    protected void doShutdown() throws Exception {
        // and clear start date
        startDate = null;
        // clear services when shutting down
        services.clear();
    }
}
