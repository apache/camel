/*
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
package org.apache.camel.impl.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Channel;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointAware;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.FailedToStartRouteException;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.RouteAware;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.Service;
import org.apache.camel.processor.ErrorHandler;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.support.ChildServiceSupport;
import org.apache.camel.support.EventHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.slf4j.MDC;

import static org.apache.camel.spi.UnitOfWork.MDC_CAMEL_CONTEXT_ID;
import static org.apache.camel.spi.UnitOfWork.MDC_ROUTE_ID;

/**
 * Represents the runtime objects for a given route so that it can be stopped independently
 * of other routes
 */
public abstract class BaseRouteService extends ChildServiceSupport {

    private final AbstractCamelContext camelContext;
    private final RouteContext routeContext;
    private final Route route;
    private boolean removingRoutes;
    private final Map<Route, Consumer> inputs = new HashMap<>();
    private final AtomicBoolean warmUpDone = new AtomicBoolean(false);
    private final AtomicBoolean endpointDone = new AtomicBoolean(false);

    public BaseRouteService(Route route) {
        this.route = route;
        this.routeContext = this.route.getRouteContext();
        this.camelContext = this.routeContext.getCamelContext().adapt(AbstractCamelContext.class);
    }

    public String getId() {
        return route.getId();
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public RouteContext getRouteContext() {
        return routeContext;
    }

    public Route getRoute() {
        return route;
    }

    public abstract Integer getStartupOrder();

    /**
     * Gather all the endpoints this route service uses
     * <p/>
     * This implementation finds the endpoints by searching all the child services
     * for {@link EndpointAware} processors which uses an endpoint.
     */
    public Set<Endpoint> gatherEndpoints() {
        Set<Endpoint> answer = new LinkedHashSet<>();
        Set<Service> services = gatherChildServices();
        for (Service service : services) {
            if (service instanceof EndpointAware) {
                Endpoint endpoint = ((EndpointAware) service).getEndpoint();
                if (endpoint != null) {
                    answer.add(endpoint);
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

    public void warmUp() throws FailedToStartRouteException {
        try {
            doWarmUp();
        } catch (Exception e) {
            throw new FailedToStartRouteException(getId(), getRouteDescription(), e);
        }
    }

    protected abstract String getRouteDescription();

    public abstract boolean isAutoStartup() throws Exception;

    protected synchronized void doWarmUp() throws Exception {
        if (endpointDone.compareAndSet(false, true)) {
            // endpoints should only be started once as they can be reused on other routes
            // and whatnot, thus their lifecycle is to start once, and only to stop when Camel shutdown
            // ensure endpoint is started first (before the route services, such as the consumer)
            ServiceHelper.startService(route.getEndpoint());
        }

        if (warmUpDone.compareAndSet(false, true)) {

            try (MDCHelper mdcHelper = new MDCHelper(route.getId())) {
                // warm up the route first
                route.warmUp();

                List<Service> services = route.getServices();

                // callback that we are staring these services
                route.onStartingServices(services);

                // gather list of services to start as we need to start child services as well
                Set<Service> list = new LinkedHashSet<>();
                for (Service service : services) {
                    list.addAll(ServiceHelper.getChildServices(service));
                }

                // split into consumers and child services as we need to start the consumers
                // afterwards to avoid them being active while the others start
                List<Service> childServices = new ArrayList<>();
                for (Service service : list) {

                    // inject the route
                    if (service instanceof RouteAware) {
                        ((RouteAware) service).setRoute(route);
                    }
                    if (service instanceof RouteIdAware) {
                        ((RouteIdAware) service).setRouteId(route.getId());
                    }
                    // inject camel context
                    if (service instanceof CamelContextAware) {
                        ((CamelContextAware) service).setCamelContext(camelContext);
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

            // ensure lifecycle strategy is invoked which among others enlist the route in JMX
            for (LifecycleStrategy strategy : camelContext.getLifecycleStrategies()) {
                strategy.onRoutesAdd(Collections.singletonList(route));
            }

            // add routes to camel context
            camelContext.addRoute(route);

            // add the routes to the inflight registry so they are pre-installed
            camelContext.getInflightRepository().addRoute(route.getId());
        }
    }

    @Override
    protected void doStart() {
        try {
            warmUp();
        } catch (FailedToStartRouteException e) {
            throw RuntimeCamelException.wrapRuntimeException(e);
        }

        try (MDCHelper mdcHelper = new MDCHelper(route.getId())) {
            // start the route itself
            ServiceHelper.startService(route);

            // invoke callbacks on route policy
            routePolicyCallback(RoutePolicy::onStart);

            // fire event
            EventHelper.notifyRouteStarted(camelContext, route);
        }
    }

    @Override
    protected void doStop() {

        // if we are stopping CamelContext then we are shutting down
        boolean isShutdownCamelContext = camelContext.isStopping();

        if (isShutdownCamelContext || isRemovingRoutes()) {
            // need to call onRoutesRemove when the CamelContext is shutting down or Route is shutdown
            for (LifecycleStrategy strategy : camelContext.getLifecycleStrategies()) {
                strategy.onRoutesRemove(Collections.singletonList(route));
            }
        }
        
        try (MDCHelper mdcHelper = new MDCHelper(route.getId())) {
            // gather list of services to stop as we need to start child services as well
            Set<Service> services = gatherChildServices();

            // stop services
            stopChildService(route, services, isShutdownCamelContext);

            // stop the route itself
            if (isShutdownCamelContext) {
                ServiceHelper.stopAndShutdownServices(route);
            } else {
                ServiceHelper.stopService(route);
            }

            // invoke callbacks on route policy
            routePolicyCallback(RoutePolicy::onStop);

            // fire event
            EventHelper.notifyRouteStopped(camelContext, route);
        }
        if (isRemovingRoutes()) {
            camelContext.removeRoute(route);
        }
        // need to warm up again
        warmUpDone.set(false);
    }

    @Override
    protected void doShutdown() {
        try (MDCHelper mdcHelper = new MDCHelper(route.getId())) {
            // gather list of services to stop as we need to start child services as well
            Set<Service> services = gatherChildServices();

            // shutdown services
            stopChildService(route, services, true);

            // shutdown the route itself
            ServiceHelper.stopAndShutdownServices(route);

            // endpoints should only be stopped when Camel is shutting down
            // see more details in the warmUp method
            ServiceHelper.stopAndShutdownServices(route.getEndpoint());

            // invoke callbacks on route policy
            routePolicyCallback(RoutePolicy::onRemove);

            // fire event
            EventHelper.notifyRouteRemoved(camelContext, route);
        }

        // need to call onRoutesRemove when the CamelContext is shutting down or Route is shutdown
        for (LifecycleStrategy strategy : camelContext.getLifecycleStrategies()) {
            strategy.onRoutesRemove(Collections.singletonList(route));
        }
        
        // remove the routes from the inflight registry
        camelContext.getInflightRepository().removeRoute(route.getId());

        // remove the routes from the collections
        camelContext.removeRoute(route);

        // clear inputs on shutdown
        inputs.clear();
        warmUpDone.set(false);
        endpointDone.set(false);
    }

    @Override
    protected void doSuspend() {
        // suspend and resume logic is provided by DefaultCamelContext which leverages ShutdownStrategy
        // to safely suspend and resume
        try (MDCHelper mdcHelper = new MDCHelper(route.getId())) {
            routePolicyCallback(RoutePolicy::onSuspend);
        }
    }

    @Override
    protected void doResume() {
        // suspend and resume logic is provided by DefaultCamelContext which leverages ShutdownStrategy
        // to safely suspend and resume
        try (MDCHelper mdcHelper = new MDCHelper(route.getId())) {
            routePolicyCallback(RoutePolicy::onResume);
        }
    }

    private void routePolicyCallback(java.util.function.BiConsumer<RoutePolicy, Route> callback) {
        if (routeContext.getRoutePolicyList() != null) {
            for (RoutePolicy routePolicy : routeContext.getRoutePolicyList()) {
                callback.accept(routePolicy, route);
            }
        }
    }

    protected void startChildService(Route route, List<Service> services) {
        for (Service service : services) {
            for (LifecycleStrategy strategy : camelContext.getLifecycleStrategies()) {
                strategy.onServiceAdd(camelContext, service, route);
            }
            ServiceHelper.startService(service);
            addChildService(service);
        }
    }

    protected void stopChildService(Route route, Set<Service> services, boolean shutdown) {
        for (Service service : services) {
            if (service instanceof ErrorHandler) {
                // special for error handlers
                for (LifecycleStrategy strategy : camelContext.getLifecycleStrategies()) {
                    ErrorHandlerFactory errorHandlerFactory = routeContext.getErrorHandlerFactory();
                    strategy.onErrorHandlerRemove(routeContext, (Processor) service, errorHandlerFactory);
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
    private Set<Service> gatherChildServices() {
        // gather list of services to stop as we need to start child services as well
        List<Service> services = new ArrayList<>(route.getServices());
        // also get route scoped services
        doGetRouteScopedServices(services);
        Set<Service> list = new LinkedHashSet<>();
        for (Service service : services) {
            list.addAll(ServiceHelper.getChildServices(service));
        }
        // also get route scoped error handler (which must be done last)
        doGetRouteScopedErrorHandler(list);
        return list;
    }

    /**
     * Gather the route scoped error handler from the given route
     */
    private void doGetRouteScopedErrorHandler(Set<Service> services) {
        // only include error handlers if they are route scoped
        boolean includeErrorHandler = !isContextScopedErrorHandler();
        List<Service> extra = new ArrayList<>();
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

    public abstract boolean isContextScopedErrorHandler();

    /**
     * Gather all other kind of route scoped services from the given route, except error handler
     */
    protected abstract void doGetRouteScopedServices(List<Service> services);

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
