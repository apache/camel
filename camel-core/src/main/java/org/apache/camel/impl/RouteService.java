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
import java.util.Collection;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Navigate;
import org.apache.camel.Route;
import org.apache.camel.Service;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.EventHelper;
import org.apache.camel.util.ServiceHelper;

/**
 * Represents the runtime objects for a given {@link RouteDefinition} so that it can be stopped independently
 * of other routes
 *
 * @version $Revision$
 */
public class RouteService extends ServiceSupport {

    private final DefaultCamelContext camelContext;
    private final RouteDefinition routeDefinition;
    private final List<RouteContext> routeContexts;
    private final Collection<Route> routes;
    private final String id;

    public RouteService(DefaultCamelContext camelContext, RouteDefinition routeDefinition, List<RouteContext> routeContexts, Collection<Route> routes) {
        this.camelContext = camelContext;
        this.routeDefinition = routeDefinition;
        this.routeContexts = routeContexts;
        this.routes = routes;
        this.id = routeDefinition.idOrCreate(camelContext.getNodeIdFactory());
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

    public RouteDefinition getRouteDefinition() {
        return routeDefinition;
    }

    public Collection<Route> getRoutes() {
        return routes;
    }

    protected void doStart() throws Exception {
        camelContext.addRouteCollection(routes);

        for (LifecycleStrategy strategy : camelContext.getLifecycleStrategies()) {
            strategy.onRoutesAdd(routes);
        }

        for (Route route : routes) {
            List<Service> services = route.getServicesForRoute();

            // gather list of services to start as we need to start child services as well
            List<Service> list = new ArrayList<Service>();
            for (Service service : services) {
                doGetServiesToStart(list, service);
            }
            startChildService(list);

            // fire event
            EventHelper.notifyRouteStarted(camelContext, route);
        }
    }

    /**
     * Need to recursive start child services for routes
     */
    private void doGetServiesToStart(List<Service> services, Service service) throws Exception {
        services.add(service);

        if (service instanceof Navigate) {
            Navigate<?> nav = (Navigate<?>) service;
            if (nav.hasNext()) {
                List<?> children = nav.next();
                for (Object child : children) {
                    if (child instanceof Service) {
                        doGetServiesToStart(services, (Service) child);
                    }
                }
            }
        }
    }

    protected void doStop() throws Exception {
        for (LifecycleStrategy strategy : camelContext.getLifecycleStrategies()) {
            strategy.onRoutesRemove(routes);
        }

        // do not stop child services as in doStart
        // as route.getServicesForRoute() will restart
        // already stopped services, so we end up starting
        // stuff when we stop.

        // fire events
        for (Route route : routes) {
            EventHelper.notifyRouteStopped(camelContext, route);
        }

        camelContext.removeRouteCollection(routes);
    }

    protected void startChildService(List<Service> services) throws Exception {
        for (Service service : services) {
            for (LifecycleStrategy strategy : camelContext.getLifecycleStrategies()) {
                strategy.onServiceAdd(camelContext, service);
            }
            ServiceHelper.startService(service);
            addChildService(service);
        }
    }

}
