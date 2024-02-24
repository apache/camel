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
import org.apache.camel.StartupStep;
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
 * <p>
 * This code has been refactored out of {@link AbstractCamelContext} to its own class.
 */
final class InternalRouteStartupManager {

    private static final Logger LOG = LoggerFactory.getLogger(InternalRouteStartupManager.class);

    private final ThreadLocal<Route> setupRoute = new ThreadLocal<>();
    private final CamelLogger routeLogger = new CamelLogger(LOG);
    private int defaultRouteStartupOrder = 1000;

    /**
     * If Camel is currently starting up a route then this returns the route.
     */
    public Route getSetupRoute() {
        return setupRoute.get();
    }

    /**
     * Initializes the routes
     *
     * @param  routeServices the routes to initialize
     * @throws Exception     is thrown if error initializing routes
     */
    public void doInitRoutes(AbstractCamelContext camelContext, Map<String, RouteService> routeServices)
            throws Exception {

        camelContext.setStartingRoutes(true);
        try {
            for (RouteService routeService : routeServices.values()) {
                StartupStep step = camelContext.getCamelContextExtension().getStartupStepRecorder().beginStep(Route.class,
                        routeService.getId(),
                        "Init Route");
                try {
                    LOG.debug("Initializing route id: {}", routeService.getId());
                    setupRoute.set(routeService.getRoute());
                    // initializing route is called doSetup as we do not want to change the service state on the RouteService
                    // so it can remain as stopped, when Camel is booting as this was the previous behavior - otherwise its state
                    // would be initialized
                    routeService.setUp();
                } finally {
                    setupRoute.remove();
                    camelContext.getCamelContextExtension().getStartupStepRecorder().endStep(step);
                }
            }
        } finally {
            camelContext.setStartingRoutes(false);
        }
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
            AbstractCamelContext camelContext,
            Map<String, RouteService> routeServices, boolean checkClash, boolean startConsumer, boolean resumeConsumer,
            boolean addingRoutes)
            throws Exception {
        camelContext.setStartingRoutes(true);
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
            safelyStartRouteServices(camelContext, checkClash, startConsumer, resumeConsumer, addingRoutes, filtered.values());

        } finally {
            camelContext.setStartingRoutes(false);
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
            AbstractCamelContext camelContext,
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
            DefaultRouteStartupOrder order = doPrepareRouteToBeStarted(camelContext, routeService);
            // check for clash before we add it as input
            if (checkClash) {
                doCheckStartupOrderClash(camelContext, order, inputs);
            }
            inputs.put(order.getStartupOrder(), order);
        }

        // warm up routes before we start them
        doWarmUpRoutes(camelContext, inputs, startConsumer);

        // sort the startup listeners so they are started in the right order
        camelContext.getStartupListeners().sort(OrderedComparator.get());
        // now call the startup listeners where the routes has been warmed up
        // (only the actual route consumer has not yet been started)
        for (StartupListener startup : camelContext.getStartupListeners()) {
            startup.onCamelContextStarted(camelContext.getCamelContextReference(), camelContext.isStarted());
        }
        // because the consumers may also register startup listeners we need to
        // reset
        // the already started listeners
        List<StartupListener> backup = new ArrayList<>(camelContext.getStartupListeners());
        camelContext.getStartupListeners().clear();

        // now start the consumers
        if (startConsumer) {
            if (resumeConsumer) {
                // and now resume the routes
                doResumeRouteConsumers(camelContext, inputs, addingRoutes);
            } else {
                // and now start the routes
                // and check for clash with multiple consumers of the same
                // endpoints which is not allowed
                doStartRouteConsumers(camelContext, inputs, addingRoutes);
            }
        }

        // sort the startup listeners so they are started in the right order
        camelContext.getStartupListeners().sort(OrderedComparator.get());
        // now the consumers that was just started may also add new
        // StartupListeners (such as timer)
        // so we need to ensure they get started as well
        for (StartupListener startup : camelContext.getStartupListeners()) {
            startup.onCamelContextStarted(camelContext.getCamelContextReference(), camelContext.isStarted());
        }
        // and add the previous started startup listeners to the list so we have
        // them all
        camelContext.getStartupListeners().addAll(0, backup);

        // inputs no longer needed
        inputs.clear();
    }

    /**
     * @see #safelyStartRouteServices(AbstractCamelContext, boolean, boolean, boolean, boolean, Collection)
     */
    public synchronized void safelyStartRouteServices(
            AbstractCamelContext camelContext,
            boolean forceAutoStart, boolean checkClash, boolean startConsumer, boolean resumeConsumer, boolean addingRoutes,
            RouteService... routeServices)
            throws Exception {
        safelyStartRouteServices(camelContext, checkClash, startConsumer, resumeConsumer, addingRoutes,
                Arrays.asList(routeServices));
    }

    DefaultRouteStartupOrder doPrepareRouteToBeStarted(AbstractCamelContext camelContext, RouteService routeService) {
        // add the inputs from this route service to the list to start
        // afterwards
        // should be ordered according to the startup number
        Integer startupOrder = routeService.getRoute().getStartupOrder();
        if (startupOrder == null) {
            // auto assign a default startup order
            startupOrder = defaultRouteStartupOrder++;
        }

        // create holder object that contains information about this route to be
        // started
        Route route = routeService.getRoute();
        return new DefaultRouteStartupOrder(startupOrder, route, routeService);
    }

    boolean doCheckStartupOrderClash(
            AbstractCamelContext camelContext, DefaultRouteStartupOrder answer, Map<Integer, DefaultRouteStartupOrder> inputs)
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
        for (RouteStartupOrder order : camelContext.getCamelContextExtension().getRouteStartupOrder()) {
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

    void doWarmUpRoutes(AbstractCamelContext camelContext, Map<Integer, DefaultRouteStartupOrder> inputs, boolean autoStartup)
            throws FailedToStartRouteException {
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
            StartupStep step = camelContext.getCamelContextExtension().getStartupStepRecorder().beginStep(Route.class,
                    routeService.getId(),
                    "Warump Route");
            try {
                LOG.debug("Warming up route id: {} having autoStartup={}", routeService.getId(), autoStartup);
                setupRoute.set(routeService.getRoute());
                // ensure we setup before warmup
                routeService.setUp();
                routeService.warmUp();
            } finally {
                setupRoute.remove();
                camelContext.getCamelContextExtension().getStartupStepRecorder().endStep(step);
            }
        }
    }

    void doResumeRouteConsumers(
            AbstractCamelContext camelContext, Map<Integer, DefaultRouteStartupOrder> inputs, boolean addingRoutes)
            throws Exception {
        doStartOrResumeRouteConsumers(camelContext, inputs, true, addingRoutes);
    }

    void doStartRouteConsumers(
            AbstractCamelContext camelContext, Map<Integer, DefaultRouteStartupOrder> inputs, boolean addingRoutes)
            throws Exception {
        doStartOrResumeRouteConsumers(camelContext, inputs, false, addingRoutes);
    }

    private LoggingLevel getRouteLoggerLogLevel(AbstractCamelContext camelContext) {
        return camelContext.getRouteController().getLoggingLevel();
    }

    private void doStartOrResumeRouteConsumers(
            AbstractCamelContext camelContext,
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
                        getRouteLoggerLogLevel(camelContext));
                continue;
            }

            StartupStep step = camelContext.getCamelContextExtension().getStartupStepRecorder().beginStep(Route.class,
                    route.getRouteId(),
                    "Start Route");

            // do some preparation before starting the consumer on the route
            Consumer consumer = routeService.getInput();
            if (consumer != null) {
                Endpoint endpoint = consumer.getEndpoint();

                // check multiple consumer violation, with the other routes to be started
                if (!doCheckMultipleConsumerSupportClash(endpoint, routeInputs)) {
                    throw new FailedToStartRouteException(
                            routeService.getId(), "Multiple consumers for the same endpoint is not allowed: " + endpoint);
                }

                // check for multiple consumer violations with existing routes
                // which have already been started, or is currently starting
                List<Endpoint> existingEndpoints = new ArrayList<>();
                for (Route existingRoute : camelContext.getRoutes()) {
                    if (route.getId().equals(existingRoute.getId())) {
                        // skip ourselves
                        continue;
                    }
                    Endpoint existing = existingRoute.getEndpoint();
                    ServiceStatus status = camelContext.getRouteStatus(existingRoute.getId());
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
                            getRouteLoggerLogLevel(camelContext));
                } else {
                    // when starting we should invoke the lifecycle strategies
                    for (LifecycleStrategy strategy : camelContext.getLifecycleStrategies()) {
                        strategy.onServiceAdd(camelContext.getCamelContextReference(), consumer, route);
                    }
                    try {
                        camelContext.startService(consumer);
                        route.getProperties().remove("route.start.exception");
                    } catch (Exception e) {
                        route.getProperties().put("route.start.exception", e);
                        throw e;
                    }

                    // use basic endpoint uri to not log verbose details or potential sensitive data
                    String uri = endpoint.getEndpointBaseUri();
                    uri = URISupport.sanitizeUri(uri);
                    routeLogger.log("Route: " + route.getId() + " started and consuming from: " + uri,
                            getRouteLoggerLogLevel(camelContext));
                }

                routeInputs.add(endpoint);

                // add to the order which they was started, so we know how to
                // stop them in reverse order
                // but only add if we haven't already registered it before (we
                // dont want to double add when restarting)
                boolean found = false;
                for (RouteStartupOrder other : camelContext.getCamelContextExtension().getRouteStartupOrder()) {
                    if (other.getRoute().getId().equals(route.getId())) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    camelContext.getCamelContextExtension().getRouteStartupOrder().add(entry.getValue());
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

            camelContext.getCamelContextExtension().getStartupStepRecorder().endStep(step);
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

    int incrementRouteStartupOrder() {
        return defaultRouteStartupOrder++;
    }

}
