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

import java.util.Collection;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.Service;
import org.apache.camel.model.RouteType;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.RouteContext;

/**
 * Represents the runtime objects for a given {@link RouteType} so that it can be stopped independently
 * of other routes
 *
 * @version $Revision: 1.1 $
 */
public class RouteService extends ServiceSupport {

    private final DefaultCamelContext camelContext;
    private final RouteType routeType;
    private final List<RouteContext> routeContexts;
    private final Collection<Route> routes;
    private String id;

    public RouteService(DefaultCamelContext camelContext, RouteType routeType, List<RouteContext> routeContexts, Collection<Route> routes) {
        this.camelContext = camelContext;
        this.routeType = routeType;
        this.routeContexts = routeContexts;
        this.routes = routes;
        this.id = routeType.idOrCreate();
    }

    public String getId() {
        return id;
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public List<RouteContext> getRouteContexts() {
        return routeContexts;
    }

    public RouteType getRouteType() {
        return routeType;
    }

    public Collection<Route> getRoutes() {
        return routes;
    }

    protected void doStart() throws Exception {
        camelContext.addRouteCollection(routes);

        getLifecycleStrategy().onRoutesAdd(routes);

        for (Route route : routes) {
            List<Service> services = route.getServicesForRoute();
            for (Service service : services) {
                startChildService(service);
            }
        }
    }

    protected void doStop() throws Exception {
        camelContext.removeRouteCollection(routes);
        // TODO should we stop the actual Route objects??
    }

    protected LifecycleStrategy getLifecycleStrategy() {
        return camelContext.getLifecycleStrategy();
    }

    protected void startChildService(Service service) throws Exception {
        getLifecycleStrategy().onServiceAdd(camelContext, service);
        service.start();
        addChildService(service);
    }
}
