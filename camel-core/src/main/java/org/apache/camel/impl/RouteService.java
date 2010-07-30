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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Navigate;
import org.apache.camel.Route;
import org.apache.camel.Service;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.EventHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Represents the runtime objects for a given {@link RouteDefinition} so that it can be stopped independently
 * of other routes
 *
 * @version $Revision$
 */
public class RouteService extends ServiceSupport {

    private static final Log LOG = LogFactory.getLog(RouteService.class);

    private final DefaultCamelContext camelContext;
    private final RouteDefinition routeDefinition;
    private final List<RouteContext> routeContexts;
    private final List<Route> routes;
    private final String id;
    private boolean removingRoutes;
    private final Map<Route, Consumer> inputs = new HashMap<Route, Consumer>();
    private final AtomicBoolean warmUpDone = new AtomicBoolean(false);

    public RouteService(DefaultCamelContext camelContext, RouteDefinition routeDefinition, List<RouteContext> routeContexts, List<Route> routes) {
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

    /**
     * Gets the inputs to the routes.
     *
     * @return list of {@link Consumer} as inputs for the routes
     */
    public Map<Route, Consumer> getInputs() {
        return inputs;
    }
    
    public boolean isRemovingRoutes() {
        return removingRoutes;
    }
    
    public void setRemovingRoutes(boolean removingRoutes) {
        this.removingRoutes = removingRoutes;
    }

    public void warmUp() throws Exception {
        camelContext.addRouteCollection(routes);

        for (LifecycleStrategy strategy : camelContext.getLifecycleStrategies()) {
            strategy.onRoutesAdd(routes);
        }

        for (Route route : routes) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Starting route services: " + route);
            }

            // TODO: We should also consider processors which are not services then we can manage all processors as well
            // otherwise its only the processors which is a Service

            List<Service> services = route.getServices();

            // callback that we are staring these services
            route.onStartingServices(services);

            // gather list of services to start as we need to start child services as well
            List<Service> list = new ArrayList<Service>();
            for (Service service : services) {
                doGetChildServices(list, service);
            }

            // split into consumers and child services as we need to start the consumers
            // afterwards to avoid them being active while the others start
            List<Service> childServices = new ArrayList<Service>();
            for (Service service : list) {
                if (service instanceof Consumer) {
                    inputs.put(route, (Consumer) service);
                } else {
                    childServices.add(service);
                }
            }
            startChildService(route, childServices);
        }

        warmUpDone.set(true);
    }

    protected void doStart() throws Exception {
        // ensure we are warmed up before starting the route
        if (warmUpDone.compareAndSet(false, true)) {
            warmUp();
        }

        for (Route route : routes) {
            // start the route itself
            ServiceHelper.startService(route);

            // fire event
            EventHelper.notifyRouteStarted(camelContext, route);
        }
    }

    protected void doStop() throws Exception {

        // if we are stopping CamelContext then we are shutting down
        boolean isShutdownCamelContext = camelContext.isStopping();

        if (isShutdownCamelContext || isRemovingRoutes()) {
            // need to call onRoutesRemove when the CamelContext is shutting down or Route is shutdown
            for (LifecycleStrategy strategy : camelContext.getLifecycleStrategies()) {
                strategy.onRoutesRemove(routes);
            }
        }
        
        for (Route route : routes) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Stopping route: " + route);
            }
            // getServices will not add services again
            List<Service> services = route.getServices();

            // gather list of services to stop as we need to start child services as well
            List<Service> list = new ArrayList<Service>();
            for (Service service : services) {
                doGetChildServices(list, service);
            }
            stopChildService(route, list, isShutdownCamelContext);

            // stop the route itself
            if (isShutdownCamelContext) {
                ServiceHelper.stopAndShutdownService(route);
            } else {
                ServiceHelper.stopService(route);
            }

            // fire event
            EventHelper.notifyRouteStopped(camelContext, route);
        }

        camelContext.removeRouteCollection(routes);
        warmUpDone.set(false);
    }

    @Override
    protected void doShutdown() throws Exception {
        // clear inputs on shutdown
        inputs.clear();
        warmUpDone.set(false);
    }

    protected void startChildService(Route route, List<Service> services) throws Exception {
        for (Service service : services) {
            for (LifecycleStrategy strategy : camelContext.getLifecycleStrategies()) {
                strategy.onServiceAdd(camelContext, service, route);
            }
            ServiceHelper.startService(service);
            addChildService(service);
        }
    }

    protected void stopChildService(Route route, List<Service> services, boolean shutdown) throws Exception {
        for (Service service : services) {
            for (LifecycleStrategy strategy : camelContext.getLifecycleStrategies()) {
                strategy.onServiceRemove(camelContext, service, route);
            }
            if (shutdown) {
                ServiceHelper.stopAndShutdownService(service);
            } else {
                ServiceHelper.stopService(service);
            }
            removeChildService(service);
        }
    }

    /**
     * Need to recursive start child services for routes
     */
    private static void doGetChildServices(List<Service> services, Service service) throws Exception {
        services.add(service);

        if (service instanceof Navigate) {
            Navigate<?> nav = (Navigate<?>) service;
            if (nav.hasNext()) {
                List<?> children = nav.next();
                for (Object child : children) {
                    if (child instanceof Service) {
                        doGetChildServices(services, (Service) child);
                    }
                }
            }
        }
    }

}
