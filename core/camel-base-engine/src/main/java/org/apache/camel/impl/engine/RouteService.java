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
import org.apache.camel.FailedToStartRouteException;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.RouteAware;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.Service;
import org.apache.camel.StartupStep;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.InternalProcessor;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.spi.StartupStepRecorder;
import org.apache.camel.support.ChildServiceSupport;
import org.apache.camel.support.EventHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.slf4j.MDC;

import static org.apache.camel.spi.UnitOfWork.MDC_CAMEL_CONTEXT_ID;
import static org.apache.camel.spi.UnitOfWork.MDC_ROUTE_ID;

/**
 * Represents the runtime objects for a given route so that it can be stopped independently of other routes
 */
public class RouteService extends ChildServiceSupport {

    private final CamelContext camelContext;
    private final StartupStepRecorder startupStepRecorder;
    private final Route route;
    private boolean removingRoutes;
    private Consumer input;
    private final AtomicBoolean setUpDone = new AtomicBoolean();
    private final AtomicBoolean warmUpDone = new AtomicBoolean();
    private final AtomicBoolean endpointDone = new AtomicBoolean();

    public RouteService(Route route) {
        this.route = route;
        this.camelContext = this.route.getCamelContext();
        this.startupStepRecorder = this.camelContext.getCamelContextExtension().getStartupStepRecorder();
    }

    public String getId() {
        return route.getId();
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public Route getRoute() {
        return route;
    }

    /**
     * Gather all the endpoints this route service uses
     * <p/>
     * This implementation finds the endpoints by searching all the child services for {@link EndpointAware} processors
     * which uses an endpoint.
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

    public Consumer getInput() {
        return input;
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
            throw new FailedToStartRouteException(getId(), e.getLocalizedMessage(), e);
        }
    }

    public void setUp() throws FailedToStartRouteException {
        if (setUpDone.compareAndSet(false, true)) {
            try {
                doSetup();
            } catch (Exception e) {
                throw new FailedToStartRouteException(getId(), e.getLocalizedMessage(), e);
            }
        }
    }

    public boolean isAutoStartup() {
        if (!getCamelContext().isAutoStartup()) {
            return false;
        }
        return getRoute().isAutoStartup();
    }

    protected synchronized void doSetup() throws Exception {
        // to setup we initialize the services
        ServiceHelper.initService(route.getEndpoint());

        try (MDCHelper mdcHelper = new MDCHelper(route.getId())) {

            // ensure services are initialized first
            route.initializeServices();
            List<Service> services = route.getServices();

            // split into consumers and child services as we need to start the consumers
            // afterwards to avoid them being active while the others start
            List<Service> list = new ArrayList<>();
            for (Service service : services) {

                // inject the route
                if (service instanceof RouteAware) {
                    ((RouteAware) service).setRoute(route);
                }
                if (service instanceof RouteIdAware) {
                    ((RouteIdAware) service).setRouteId(route.getId());
                }
                // inject camel context
                CamelContextAware.trySetCamelContext(service, camelContext);

                if (service instanceof Consumer) {
                    this.input = (Consumer) service;
                } else {
                    list.add(service);
                }
            }
            initChildServices(list);
        }
    }

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

                startChildServices(route, childServices);

                // fire event
                EventHelper.notifyRouteAdded(camelContext, route);
            }

            // ensure lifecycle strategy is invoked which among others enlist the route in JMX
            for (LifecycleStrategy strategy : camelContext.getLifecycleStrategies()) {
                strategy.onRoutesAdd(Collections.singletonList(route));
            }

            // add routes to camel context
            camelContext.getCamelContextExtension().addRoute(route);

            // add the routes to the inflight registry so they are pre-installed
            camelContext.getInflightRepository().addRoute(route.getId());
        }
    }

    @Override
    protected void doStart() {
        try (MDCHelper mdcHelper = new MDCHelper(route.getId())) {
            // fire event
            EventHelper.notifyRouteStarting(camelContext, route);
        }

        try {
            // ensure we are warmed up
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
        try (MDCHelper mdcHelper = new MDCHelper(route.getId())) {
            // fire event
            EventHelper.notifyRouteStopping(camelContext, route);
        }

        // if we are stopping CamelContext then we are shutting down
        boolean isShutdownCamelContext = camelContext.isStopping();

        if (isShutdownCamelContext || isRemovingRoutes()) {
            // need to call onRoutesRemove when the CamelContext is shutting down or Route is shutdown
            for (LifecycleStrategy strategy : camelContext.getLifecycleStrategies()) {
                strategy.onRoutesRemove(Collections.singletonList(route));
            }
        }

        try (MDCHelper mdcHelper = new MDCHelper(route.getId())) {
            // gather list of services to stop
            Set<Service> services = gatherChildServices();

            // stop services
            stopChildServices(route, services, isShutdownCamelContext);

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
            camelContext.getCamelContextExtension().removeRoute(route);
        }
        // need to redo if we start again after being stopped
        input = null;
        childServices = null;
        warmUpDone.set(false);
        setUpDone.set(false);
        endpointDone.set(false);
        setUpDone.set(false);
        warmUpDone.set(false);
    }

    @Override
    protected void doShutdown() {
        try (MDCHelper mdcHelper = new MDCHelper(route.getId())) {
            // gather list of services to shutdown
            Set<Service> services = gatherChildServices();

            // shutdown services
            stopChildServices(route, services, true);

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
        camelContext.getCamelContextExtension().removeRoute(route);

        // clear inputs on shutdown
        input = null;
        childServices = null;
        warmUpDone.set(false);
        setUpDone.set(false);
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
        if (route.getRoutePolicyList() != null) {
            for (RoutePolicy routePolicy : route.getRoutePolicyList()) {
                callback.accept(routePolicy, route);
            }
        }
    }

    private StartupStep beginStep(Service service, String description) {
        Class<?> type = service instanceof Processor ? Processor.class : Service.class;
        description = description + " " + service.getClass().getSimpleName();
        String id = null;
        if (service instanceof IdAware) {
            id = ((IdAware) service).getId();
        }
        return startupStepRecorder.beginStep(type, id, description);
    }

    protected void initChildServices(List<Service> services) {
        for (Service service : services) {
            StartupStep step = null;
            // skip internal services / route pipeline (starting point for route)
            boolean record
                    = !(service instanceof InternalProcessor || "RoutePipeline".equals(service.getClass().getSimpleName()));
            if (record) {
                step = beginStep(service, "Init");
            }
            ServiceHelper.initService(service);
            if (step != null) {
                startupStepRecorder.endStep(step);
            }
            // add and remember as child service
            addChildService(service);
        }
    }

    protected void startChildServices(Route route, List<Service> services) {
        for (Service service : services) {
            StartupStep step = null;
            // skip internal services / route pipeline (starting point for route)
            boolean record
                    = !(service instanceof InternalProcessor || "RoutePipeline".equals(service.getClass().getSimpleName()));
            if (record) {
                step = beginStep(service, "Start");
            }
            for (LifecycleStrategy strategy : camelContext.getLifecycleStrategies()) {
                strategy.onServiceAdd(camelContext, service, route);
            }
            ServiceHelper.startService(service);
            if (step != null) {
                startupStepRecorder.endStep(step);
            }
        }
    }

    protected void stopChildServices(Route route, Set<Service> services, boolean shutdown) {
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
     * Gather all child services
     */
    private Set<Service> gatherChildServices() {
        List<Service> services = new ArrayList<>(route.getServices());
        // also get route scoped services
        doGetRouteServices(services);
        Set<Service> list = new LinkedHashSet<>();
        for (Service service : services) {
            list.addAll(ServiceHelper.getChildServices(service));
        }
        // also get route scoped error handler (which must be done last)
        doGetErrorHandler(list);
        return list;
    }

    /**
     * Gather the route scoped error handler from the given route
     */
    private void doGetErrorHandler(Set<Service> services) {
        // only include error handlers if they are route scoped
        List<Service> extra = new ArrayList<>();
        for (Service service : services) {
            if (service instanceof Channel) {
                Processor eh = ((Channel) service).getErrorHandler();
                if (eh instanceof Service) {
                    extra.add((Service) eh);
                }
            }
        }
        if (!extra.isEmpty()) {
            services.addAll(extra);
        }
    }

    /**
     * Gather all other kind of route services from the given route, except error handler
     */
    protected void doGetRouteServices(List<Service> services) {
        for (Processor proc : getRoute().getOnExceptions()) {
            if (proc instanceof Service) {
                services.add((Service) proc);
            }
        }
        for (Processor proc : getRoute().getOnCompletions()) {
            if (proc instanceof Service) {
                services.add((Service) proc);
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
