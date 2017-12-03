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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.Channel;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointAware;
import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.RouteAware;
import org.apache.camel.Service;
import org.apache.camel.model.OnCompletionDefinition;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.processor.ErrorHandler;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.support.ChildServiceSupport;
import org.apache.camel.util.EventHelper;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static org.apache.camel.impl.MDCUnitOfWork.MDC_CAMEL_CONTEXT_ID;
import static org.apache.camel.impl.MDCUnitOfWork.MDC_ROUTE_ID;

/**
 * Represents the runtime objects for a given {@link RouteDefinition} so that it can be stopped independently
 * of other routes
 *
 * @version 
 */
public class RouteService extends ChildServiceSupport {

    private static final Logger LOG = LoggerFactory.getLogger(RouteService.class);

    private final DefaultCamelContext camelContext;
    private final RouteDefinition routeDefinition;
    private final List<RouteContext> routeContexts;
    private final List<Route> routes;
    private final String id;
    private boolean removingRoutes;
    private final Map<Route, Consumer> inputs = new HashMap<Route, Consumer>();
    private final AtomicBoolean warmUpDone = new AtomicBoolean(false);
    private final AtomicBoolean endpointDone = new AtomicBoolean(false);

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
     * Gather all the endpoints this route service uses
     * <p/>
     * This implementation finds the endpoints by searching all the child services
     * for {@link org.apache.camel.EndpointAware} processors which uses an endpoint.
     */
    public Set<Endpoint> gatherEndpoints() {
        Set<Endpoint> answer = new LinkedHashSet<Endpoint>();
        for (Route route : routes) {
            Set<Service> services = gatherChildServices(route, true);
            for (Service service : services) {
                if (service instanceof EndpointAware) {
                    Endpoint endpoint = ((EndpointAware) service).getEndpoint();
                    if (endpoint != null) {
                        answer.add(endpoint);
                    }
                }
            }
        }
        return answer;
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
        try {
            doWarmUp();
        } catch (Exception e) {
            throw new FailedToCreateRouteException(routeDefinition.getId(), routeDefinition.toString(), e);
        }
    }

    protected synchronized void doWarmUp() throws Exception {
        if (endpointDone.compareAndSet(false, true)) {
            // endpoints should only be started once as they can be reused on other routes
            // and whatnot, thus their lifecycle is to start once, and only to stop when Camel shutdown
            for (Route route : routes) {
                // ensure endpoint is started first (before the route services, such as the consumer)
                ServiceHelper.startService(route.getEndpoint());
            }
        }

        if (warmUpDone.compareAndSet(false, true)) {

            for (Route route : routes) {
                try (MDCHelper mdcHelper = new MDCHelper(route.getId())) {
                    // warm up the route first
                    route.warmUp();

                    LOG.debug("Starting services on route: {}", route.getId());
                    List<Service> services = route.getServices();

                    // callback that we are staring these services
                    route.onStartingServices(services);

                    // gather list of services to start as we need to start child services as well
                    Set<Service> list = new LinkedHashSet<Service>();
                    for (Service service : services) {
                        list.addAll(ServiceHelper.getChildServices(service));
                    }

                    // split into consumers and child services as we need to start the consumers
                    // afterwards to avoid them being active while the others start
                    List<Service> childServices = new ArrayList<Service>();
                    for (Service service : list) {

                        // inject the route
                        if (service instanceof RouteAware) {
                            ((RouteAware) service).setRoute(route);
                        }

                        if (service instanceof Consumer) {
                            inputs.put(route, (Consumer) service);
                        } else {
                            childServices.add(service);
                        }
                    }
                    startChildService(route, childServices);

                    // fire event
                    EventHelper.notifyRouteAdded(camelContext, route);
                }
            }

            // ensure lifecycle strategy is invoked which among others enlist the route in JMX
            for (LifecycleStrategy strategy : camelContext.getLifecycleStrategies()) {
                strategy.onRoutesAdd(routes);
            }

            // add routes to camel context
            camelContext.addRouteCollection(routes);

            // add the routes to the inflight registry so they are pre-installed
            for (Route route : routes) {
                camelContext.getInflightRepository().addRoute(route.getId());
            }
        }
    }

    protected void doStart() throws Exception {
        warmUp();

        for (Route route : routes) {
            try (MDCHelper mdcHelper = new MDCHelper(route.getId())) {
                // start the route itself
                ServiceHelper.startService(route);

                // invoke callbacks on route policy
                if (route.getRouteContext().getRoutePolicyList() != null) {
                    for (RoutePolicy routePolicy : route.getRouteContext().getRoutePolicyList()) {
                        routePolicy.onStart(route);
                    }
                }

                // fire event
                EventHelper.notifyRouteStarted(camelContext, route);
            }
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
            try (MDCHelper mdcHelper = new MDCHelper(route.getId())) {
                LOG.debug("Stopping services on route: {}", route.getId());

                // gather list of services to stop as we need to start child services as well
                Set<Service> services = gatherChildServices(route, true);

                // stop services
                stopChildService(route, services, isShutdownCamelContext);

                // stop the route itself
                if (isShutdownCamelContext) {
                    ServiceHelper.stopAndShutdownServices(route);
                } else {
                    ServiceHelper.stopServices(route);
                }

                // invoke callbacks on route policy
                if (route.getRouteContext().getRoutePolicyList() != null) {
                    for (RoutePolicy routePolicy : route.getRouteContext().getRoutePolicyList()) {
                        routePolicy.onStop(route);
                    }
                }
                // fire event
                EventHelper.notifyRouteStopped(camelContext, route);
            }
        }
        if (isRemovingRoutes()) {
            camelContext.removeRouteCollection(routes);
        }
        // need to warm up again
        warmUpDone.set(false);
    }

    @Override
    protected void doShutdown() throws Exception {
        for (Route route : routes) {
            try (MDCHelper mdcHelper = new MDCHelper(route.getId())) {
                LOG.debug("Shutting down services on route: {}", route.getId());

                // gather list of services to stop as we need to start child services as well
                Set<Service> services = gatherChildServices(route, true);

                // shutdown services
                stopChildService(route, services, true);

                // shutdown the route itself
                ServiceHelper.stopAndShutdownServices(route);

                // endpoints should only be stopped when Camel is shutting down
                // see more details in the warmUp method
                ServiceHelper.stopAndShutdownServices(route.getEndpoint());
                // invoke callbacks on route policy
                if (route.getRouteContext().getRoutePolicyList() != null) {
                    for (RoutePolicy routePolicy : route.getRouteContext().getRoutePolicyList()) {
                        routePolicy.onRemove(route);
                    }
                }

                // fire event
                EventHelper.notifyRouteRemoved(camelContext, route);
            }
        }

        // need to call onRoutesRemove when the CamelContext is shutting down or Route is shutdown
        for (LifecycleStrategy strategy : camelContext.getLifecycleStrategies()) {
            strategy.onRoutesRemove(routes);
        }
        
        // remove the routes from the inflight registry
        for (Route route : routes) {
            camelContext.getInflightRepository().removeRoute(route.getId());
        }

        // remove the routes from the collections
        camelContext.removeRouteCollection(routes);
        
        // clear inputs on shutdown
        inputs.clear();
        warmUpDone.set(false);
        endpointDone.set(false);
    }

    @Override
    protected void doSuspend() throws Exception {
        // suspend and resume logic is provided by DefaultCamelContext which leverages ShutdownStrategy
        // to safely suspend and resume
        for (Route route : routes) {
            try (MDCHelper mdcHelper = new MDCHelper(route.getId())) {
                if (route.getRouteContext().getRoutePolicyList() != null) {
                    for (RoutePolicy routePolicy : route.getRouteContext().getRoutePolicyList()) {
                        routePolicy.onSuspend(route);
                    }
                }
            }
        }
    }

    @Override
    protected void doResume() throws Exception {
        // suspend and resume logic is provided by DefaultCamelContext which leverages ShutdownStrategy
        // to safely suspend and resume
        for (Route route : routes) {
            try (MDCHelper mdcHelper = new MDCHelper(route.getId())) {
                if (route.getRouteContext().getRoutePolicyList() != null) {
                    for (RoutePolicy routePolicy : route.getRouteContext().getRoutePolicyList()) {
                        routePolicy.onResume(route);
                    }
                }
            }
        }
    }

    protected void startChildService(Route route, List<Service> services) throws Exception {
        for (Service service : services) {
            LOG.debug("Starting child service on route: {} -> {}", route.getId(), service);
            for (LifecycleStrategy strategy : camelContext.getLifecycleStrategies()) {
                strategy.onServiceAdd(camelContext, service, route);
            }
            ServiceHelper.startService(service);
            addChildService(service);
        }
    }

    protected void stopChildService(Route route, Set<Service> services, boolean shutdown) throws Exception {
        for (Service service : services) {
            LOG.debug("{} child service on route: {} -> {}", new Object[]{shutdown ? "Shutting down" : "Stopping", route.getId(), service});
            if (service instanceof ErrorHandler) {
                // special for error handlers
                for (LifecycleStrategy strategy : camelContext.getLifecycleStrategies()) {
                    strategy.onErrorHandlerRemove(route.getRouteContext(), (Processor) service, route.getRouteContext().getRoute().getErrorHandlerBuilder());
                }
            } else {
                for (LifecycleStrategy strategy : camelContext.getLifecycleStrategies()) {
                    strategy.onServiceRemove(camelContext, service, route);
                }
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
     * Gather all child services
     */
    private Set<Service> gatherChildServices(Route route, boolean includeErrorHandler) {
        // gather list of services to stop as we need to start child services as well
        List<Service> services = new ArrayList<Service>();
        services.addAll(route.getServices());
        // also get route scoped services
        doGetRouteScopedServices(services, route);
        Set<Service> list = new LinkedHashSet<Service>();
        for (Service service : services) {
            list.addAll(ServiceHelper.getChildServices(service));
        }
        if (includeErrorHandler) {
            // also get route scoped error handler (which must be done last)
            doGetRouteScopedErrorHandler(list, route);
        }
        Set<Service> answer = new LinkedHashSet<Service>();
        answer.addAll(list);
        return answer;
    }

    /**
     * Gather the route scoped error handler from the given route
     */
    private void doGetRouteScopedErrorHandler(Set<Service> services, Route route) {
        // only include error handlers if they are route scoped
        boolean includeErrorHandler = !routeDefinition.isContextScopedErrorHandler(route.getRouteContext().getCamelContext());
        List<Service> extra = new ArrayList<Service>();
        if (includeErrorHandler) {
            for (Service service : services) {
                if (service instanceof Channel) {
                    Processor eh = ((Channel) service).getErrorHandler();
                    if (eh instanceof Service) {
                        extra.add((Service) eh);
                    }
                }
            }
        }
        if (!extra.isEmpty()) {
            services.addAll(extra);
        }
    }

    /**
     * Gather all other kind of route scoped services from the given route, except error handler
     */
    private void doGetRouteScopedServices(List<Service> services, Route route) {
        for (ProcessorDefinition<?> output : route.getRouteContext().getRoute().getOutputs()) {
            if (output instanceof OnExceptionDefinition) {
                OnExceptionDefinition onExceptionDefinition = (OnExceptionDefinition) output;
                if (onExceptionDefinition.isRouteScoped()) {
                    Processor errorHandler = onExceptionDefinition.getErrorHandler(route.getId());
                    if (errorHandler instanceof Service) {
                        services.add((Service) errorHandler);
                    }
                }
            } else if (output instanceof OnCompletionDefinition) {
                OnCompletionDefinition onCompletionDefinition = (OnCompletionDefinition) output;
                if (onCompletionDefinition.isRouteScoped()) {
                    Processor onCompletionProcessor = onCompletionDefinition.getOnCompletion(route.getId());
                    if (onCompletionProcessor instanceof Service) {
                        services.add((Service) onCompletionProcessor);
                    }
                }
            }
        }
    }

    class MDCHelper implements AutoCloseable {
        final Map<String, String> originalContextMap;

        MDCHelper(String routeId) {
            if (getCamelContext().isUseMDCLogging()) {
                originalContextMap = MDC.getCopyOfContextMap();
                MDC.put(MDC_CAMEL_CONTEXT_ID, getCamelContext().getName());
                MDC.put(MDC_ROUTE_ID, routeId);
            } else {
                originalContextMap = null;
            }
        }

        @Override
        public void close() {
            if (getCamelContext().isUseMDCLogging()) {
                if (originalContextMap != null) {
                    MDC.setContextMap(originalContextMap);
                } else {
                    MDC.clear();
                }
            }
        }

    }

}
