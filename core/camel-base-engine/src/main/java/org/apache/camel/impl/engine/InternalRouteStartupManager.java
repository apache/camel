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
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.FailedToStartRouteException;
import org.apache.camel.LoggingLevel;
import org.apache.camel.MultipleConsumersSupport;
import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.apache.camel.StartupListener;
import org.apache.camel.StatefulService;
import org.apache.camel.SuspendableService;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.RouteStartupOrder;
import org.apache.camel.support.OrderedComparator;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Internal route startup manager used by {@link AbstractCamelContext} to safely start internal route services during
 * starting routes.
 *
 * This code has been refactored out of {@link AbstractCamelContext} to its own class.
 */
class InternalRouteStartupManager {

    private static final Logger LOG = LoggerFactory.getLogger(InternalRouteStartupManager.class);

    private final ThreadLocal<Route> setupRoute = new ThreadLocal<>();
    private final AbstractCamelContext abstractCamelContext;
    private final CamelLogger routeLogger = new CamelLogger(LOG);

    public InternalRouteStartupManager(AbstractCamelContext abstractCamelContext) {
        this.abstractCamelContext = abstractCamelContext;
    }

    /**
     * If Camel is currently starting up a route then this returns the route.
     */
    public Route getSetupRoute() {
        return setupRoute.get();
    }

    /**
     * Starts or resumes the routes
     *
     * @param  routeServices  the routes to start (will only start a route if its not already started)
     * @param  checkClash     whether to check for startup ordering clash
     * @param  startConsumer  whether the route consumer should be started. Can be used to warmup the route without
     *                        starting the consumer.
     * @param  resumeConsumer whether the route consumer should be resumed.
     * @param  addingRoutes   whether we are adding new routes
     * @throws Exception      is thrown if error starting routes
     */
    protected void doStartOrResumeRoutes(
            Map<String, RouteService> routeServices, boolean checkClash, boolean startConsumer, boolean resumeConsumer,
            boolean addingRoutes)
            throws Exception {
        abstractCamelContext.setStartingRoutes(true);
        try {
            // filter out already started routes
            Map<String, RouteService> filtered = new LinkedHashMap<>();
            for (Map.Entry<String, RouteService> entry : routeServices.entrySet()) {
                boolean startable = false;

                Consumer consumer = entry.getValue().getRoute().getConsumer();
                if (consumer instanceof SuspendableService) {
                    // consumer could be suspended, which is not reflected in
                    // the BaseRouteService status
                    startable = ((SuspendableService) consumer).isSuspended();
                }

                if (!startable && consumer instanceof StatefulService) {
                    // consumer could be stopped, which is not reflected in the
                    // BaseRouteService status
                    startable = ((StatefulService) consumer).getStatus().isStartable();
                } else if (!startable) {
                    // no consumer so use state from route service
                    startable = entry.getValue().getStatus().isStartable();
                }

                if (startable) {
                    filtered.put(entry.getKey(), entry.getValue());
                }
            }

            // the context is in last phase of staring, so lets start the routes
            safelyStartRouteServices(checkClash, startConsumer, resumeConsumer, addingRoutes, filtered.values());

        } finally {
            abstractCamelContext.setStartingRoutes(false);
        }
    }

    /**
     * Starts the routes services in a proper manner which ensures the routes will be started in correct order, check
     * for clash and that the routes will also be shutdown in correct order as well.
     * <p/>
     * This method <b>must</b> be used to start routes in a safe manner.
     *
     * @param  checkClash     whether to check for startup order clash
     * @param  startConsumer  whether the route consumer should be started. Can be used to warmup the route without
     *                        starting the consumer.
     * @param  resumeConsumer whether the route consumer should be resumed.
     * @param  addingRoutes   whether we are adding new routes
     * @param  routeServices  the routes
     * @throws Exception      is thrown if error starting the routes
     */
    protected synchronized void safelyStartRouteServices(
            boolean checkClash, boolean startConsumer, boolean resumeConsumer, boolean addingRoutes,
            Collection<RouteService> routeServices)
            throws Exception {
        // list of inputs to start when all the routes have been prepared for
        // starting
        // we use a tree map so the routes will be ordered according to startup
        // order defined on the route
        Map<Integer, DefaultRouteStartupOrder> inputs = new TreeMap<>();

        // figure out the order in which the routes should be started
        for (RouteService routeService : routeServices) {
            DefaultRouteStartupOrder order = doPrepareRouteToBeStarted(routeService);
            // check for clash before we add it as input
            if (checkClash) {
                doCheckStartupOrderClash(order, inputs);
            }
            inputs.put(order.getStartupOrder(), order);
        }

        // warm up routes before we start them
        doWarmUpRoutes(inputs, startConsumer);

        // sort the startup listeners so they are started in the right order
        abstractCamelContext.getStartupListeners().sort(OrderedComparator.get());
        // now call the startup listeners where the routes has been warmed up
        // (only the actual route consumer has not yet been started)
        for (StartupListener startup : abstractCamelContext.getStartupListeners()) {
            startup.onCamelContextStarted(abstractCamelContext.getCamelContextReference(), abstractCamelContext.isStarted());
        }
        // because the consumers may also register startup listeners we need to
        // reset
        // the already started listeners
        List<StartupListener> backup = new ArrayList<>(abstractCamelContext.getStartupListeners());
        abstractCamelContext.getStartupListeners().clear();

        // now start the consumers
        if (startConsumer) {
            if (resumeConsumer) {
                // and now resume the routes
                doResumeRouteConsumers(inputs, addingRoutes);
            } else {
                // and now start the routes
                // and check for clash with multiple consumers of the same
                // endpoints which is not allowed
                doStartRouteConsumers(inputs, addingRoutes);
            }
        }

        // sort the startup listeners so they are started in the right order
        abstractCamelContext.getStartupListeners().sort(OrderedComparator.get());
        // now the consumers that was just started may also add new
        // StartupListeners (such as timer)
        // so we need to ensure they get started as well
        for (StartupListener startup : abstractCamelContext.getStartupListeners()) {
            startup.onCamelContextStarted(abstractCamelContext.getCamelContextReference(), abstractCamelContext.isStarted());
        }
        // and add the previous started startup listeners to the list so we have
        // them all
        abstractCamelContext.getStartupListeners().addAll(0, backup);

        // inputs no longer needed
        inputs.clear();
    }

    /**
     * @see #safelyStartRouteServices(boolean,boolean,boolean,boolean,Collection)
     */
    protected synchronized void safelyStartRouteServices(
            boolean forceAutoStart, boolean checkClash, boolean startConsumer, boolean resumeConsumer, boolean addingRoutes,
            RouteService... routeServices)
            throws Exception {
        safelyStartRouteServices(checkClash, startConsumer, resumeConsumer, addingRoutes, Arrays.asList(routeServices));
    }

    DefaultRouteStartupOrder doPrepareRouteToBeStarted(RouteService routeService) {
        // add the inputs from this route service to the list to start
        // afterwards
        // should be ordered according to the startup number
        Integer startupOrder = routeService.getRoute().getStartupOrder();
        if (startupOrder == null) {
            // auto assign a default startup order
            startupOrder = abstractCamelContext.defaultRouteStartupOrder++;
        }

        // create holder object that contains information about this route to be
        // started
        Route route = routeService.getRoute();
        return new DefaultRouteStartupOrder(startupOrder, route, routeService);
    }

    boolean doCheckStartupOrderClash(DefaultRouteStartupOrder answer, Map<Integer, DefaultRouteStartupOrder> inputs)
            throws FailedToStartRouteException {
        // check for clash by startupOrder id
        DefaultRouteStartupOrder other = inputs.get(answer.getStartupOrder());
        if (other != null && answer != other) {
            String otherId = other.getRoute().getId();
            throw new FailedToStartRouteException(
                    answer.getRoute().getId(), "startupOrder clash. Route " + otherId + " already has startupOrder " + answer
                            .getStartupOrder() + " configured which this route have as well. Please correct startupOrder to be unique among all your routes.");
        }
        // check in existing already started as well
        for (RouteStartupOrder order : abstractCamelContext.getRouteStartupOrder()) {
            String otherId = order.getRoute().getId();
            if (answer.getRoute().getId().equals(otherId)) {
                // its the same route id so skip clash check as its the same
                // route (can happen when using suspend/resume)
            } else if (answer.getStartupOrder() == order.getStartupOrder()) {
                throw new FailedToStartRouteException(
                        answer.getRoute().getId(), "startupOrder clash. Route " + otherId + " already has startupOrder "
                                                   + answer
                                                           .getStartupOrder()
                                                   + " configured which this route have as well. Please correct startupOrder to be unique among all your routes.");
            }
        }
        return true;
    }

    void doWarmUpRoutes(Map<Integer, DefaultRouteStartupOrder> inputs, boolean autoStartup) throws FailedToStartRouteException {
        // now prepare the routes by starting its services before we start the
        // input
        for (Map.Entry<Integer, DefaultRouteStartupOrder> entry : inputs.entrySet()) {
            // defer starting inputs till later as we want to prepare the routes
            // by starting
            // all their processors and child services etc.
            // then later we open the floods to Camel by starting the inputs
            // what this does is to ensure Camel is more robust on starting
            // routes as all routes
            // will then be prepared in time before we start inputs which will
            // consume messages to be routed
            RouteService routeService = entry.getValue().getRouteService();
            try {
                LOG.debug("Warming up route id: {} having autoStartup={}", routeService.getId(), autoStartup);
                setupRoute.set(routeService.getRoute());
                routeService.warmUp();
            } finally {
                setupRoute.remove();
            }
        }
    }

    void doResumeRouteConsumers(Map<Integer, DefaultRouteStartupOrder> inputs, boolean addingRoutes) throws Exception {
        doStartOrResumeRouteConsumers(inputs, true, addingRoutes);
    }

    void doStartRouteConsumers(Map<Integer, DefaultRouteStartupOrder> inputs, boolean addingRoutes) throws Exception {
        doStartOrResumeRouteConsumers(inputs, false, addingRoutes);
    }

    private LoggingLevel getRouteLoggerLogLevel() {
        return abstractCamelContext.getRouteController().getRouteStartupLoggingLevel();
    }

    private void doStartOrResumeRouteConsumers(
            Map<Integer, DefaultRouteStartupOrder> inputs, boolean resumeOnly, boolean addingRoute)
            throws Exception {
        List<Endpoint> routeInputs = new ArrayList<>();

        for (Map.Entry<Integer, DefaultRouteStartupOrder> entry : inputs.entrySet()) {
            Integer order = entry.getKey();
            Route route = entry.getValue().getRoute();
            RouteService routeService = entry.getValue().getRouteService();

            // if we are starting camel, then skip routes which are configured
            // to not be auto started
            boolean autoStartup = routeService.isAutoStartup();
            if (addingRoute && !autoStartup) {
                routeLogger.log(
                        "Skipping starting of route " + routeService.getId() + " as it's configured with autoStartup=false",
                        getRouteLoggerLogLevel());
                continue;
            }

            // start the service
            for (Consumer consumer : routeService.getInputs().values()) {
                Endpoint endpoint = consumer.getEndpoint();

                // check multiple consumer violation, with the other routes to
                // be started
                if (!doCheckMultipleConsumerSupportClash(endpoint, routeInputs)) {
                    throw new FailedToStartRouteException(
                            routeService.getId(), "Multiple consumers for the same endpoint is not allowed: " + endpoint);
                }

                // check for multiple consumer violations with existing routes
                // which
                // have already been started, or is currently starting
                List<Endpoint> existingEndpoints = new ArrayList<>();
                for (Route existingRoute : abstractCamelContext.getRoutes()) {
                    if (route.getId().equals(existingRoute.getId())) {
                        // skip ourselves
                        continue;
                    }
                    Endpoint existing = existingRoute.getEndpoint();
                    ServiceStatus status = abstractCamelContext.getRouteStatus(existingRoute.getId());
                    if (status != null && (status.isStarted() || status.isStarting())) {
                        existingEndpoints.add(existing);
                    }
                }
                if (!doCheckMultipleConsumerSupportClash(endpoint, existingEndpoints)) {
                    throw new FailedToStartRouteException(
                            routeService.getId(), "Multiple consumers for the same endpoint is not allowed: " + endpoint);
                }

                // start the consumer on the route
                LOG.debug("Route: {} >>> {}", route.getId(), route);
                if (resumeOnly) {
                    LOG.debug("Resuming consumer (order: {}) on route: {}", order, route.getId());
                } else {
                    LOG.debug("Starting consumer (order: {}) on route: {}", order, route.getId());
                }

                if (resumeOnly && route.supportsSuspension()) {
                    // if we are resuming and the route can be resumed
                    ServiceHelper.resumeService(consumer);
                    // use basic endpoint uri to not log verbose details or potential sensitive data
                    String uri = endpoint.getEndpointBaseUri();
                    uri = URISupport.sanitizeUri(uri);
                    routeLogger.log("Route: " + route.getId() + " resumed and consuming from: " + uri,
                            getRouteLoggerLogLevel());
                } else {
                    // when starting we should invoke the lifecycle strategies
                    for (LifecycleStrategy strategy : abstractCamelContext.getLifecycleStrategies()) {
                        strategy.onServiceAdd(abstractCamelContext.getCamelContextReference(), consumer, route);
                    }
                    try {
                        abstractCamelContext.startService(consumer);
                        route.getProperties().remove("route.start.exception");
                    } catch (Exception e) {
                        route.getProperties().put("route.start.exception", e);
                        throw e;
                    }

                    // use basic endpoint uri to not log verbose details or potential sensitive data
                    String uri = endpoint.getEndpointBaseUri();
                    uri = URISupport.sanitizeUri(uri);
                    routeLogger.log("Route: " + route.getId() + " started and consuming from: " + uri,
                            getRouteLoggerLogLevel());
                }

                routeInputs.add(endpoint);

                // add to the order which they was started, so we know how to
                // stop them in reverse order
                // but only add if we haven't already registered it before (we
                // dont want to double add when restarting)
                boolean found = false;
                for (RouteStartupOrder other : abstractCamelContext.getRouteStartupOrder()) {
                    if (other.getRoute().getId().equals(route.getId())) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    abstractCamelContext.getRouteStartupOrder().add(entry.getValue());
                }
            }

            if (resumeOnly) {
                routeService.resume();
            } else {
                // and start the route service (no need to start children as
                // they are already warmed up)
                try {
                    routeService.start();
                    route.getProperties().remove("route.start.exception");
                } catch (Exception e) {
                    route.getProperties().put("route.start.exception", e);
                    throw e;
                }
            }
        }
    }

    private boolean doCheckMultipleConsumerSupportClash(Endpoint endpoint, List<Endpoint> routeInputs) {
        // is multiple consumers supported
        boolean multipleConsumersSupported = false;
        if (endpoint instanceof MultipleConsumersSupport) {
            multipleConsumersSupported = ((MultipleConsumersSupport) endpoint).isMultipleConsumersSupported();
        }

        if (multipleConsumersSupported) {
            // multiple consumer allowed, so return true
            return true;
        }

        // check in progress list
        if (routeInputs.contains(endpoint)) {
            return false;
        }

        return true;
    }

}
