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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedStartupListener;
import org.apache.camel.FailedToStartRouteException;
import org.apache.camel.NamedNode;
import org.apache.camel.NonManagedService;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.ServiceStatus;
import org.apache.camel.StartupSummaryLevel;
import org.apache.camel.spi.HasId;
import org.apache.camel.spi.RouteController;
import org.apache.camel.spi.RouteError;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.spi.RoutePolicyFactory;
import org.apache.camel.spi.SupervisingRouteController;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.backoff.BackOff;
import org.apache.camel.util.backoff.BackOffTimer;
import org.apache.camel.util.function.ThrowingConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A supervising capable {@link RouteController} that delays the startup of the routes after the camel context startup
 * and takes control of starting the routes in a safe manner. This controller is able to retry starting failing routes,
 * and have various options to configure settings for backoff between restarting routes.
 *
 * @see DefaultRouteController
 */
public class DefaultSupervisingRouteController extends DefaultRouteController implements SupervisingRouteController {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultSupervisingRouteController.class);
    private final Object lock;
    private final AtomicBoolean contextStarted;
    private final AtomicInteger routeCount;
    private final Set<RouteHolder> routes;
    private final Set<String> nonSupervisedRoutes;
    private final RouteManager routeManager;
    private volatile CamelContextStartupListener listener;
    private volatile BackOffTimer timer;
    private volatile ScheduledExecutorService executorService;
    private volatile BackOff backOff;
    private String includeRoutes;
    private String excludeRoutes;
    private int threadPoolSize = 1;
    private long initialDelay;
    private long backOffDelay = 2000;
    private long backOffMaxDelay;
    private long backOffMaxElapsedTime;
    private long backOffMaxAttempts;
    private double backOffMultiplier = 1.0d;
    private boolean unhealthyOnExhausted;

    public DefaultSupervisingRouteController() {
        this.lock = new Object();
        this.contextStarted = new AtomicBoolean();
        this.routeCount = new AtomicInteger();
        this.routes = new TreeSet<>();
        this.nonSupervisedRoutes = new HashSet<>();
        this.routeManager = new RouteManager();
    }

    // *********************************
    // Properties
    // *********************************

    public String getIncludeRoutes() {
        return includeRoutes;
    }

    public void setIncludeRoutes(String includeRoutes) {
        this.includeRoutes = includeRoutes;
    }

    public String getExcludeRoutes() {
        return excludeRoutes;
    }

    public void setExcludeRoutes(String excludeRoutes) {
        this.excludeRoutes = excludeRoutes;
    }

    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    public void setThreadPoolSize(int threadPoolSize) {
        this.threadPoolSize = threadPoolSize;
    }

    public long getInitialDelay() {
        return initialDelay;
    }

    public void setInitialDelay(long initialDelay) {
        this.initialDelay = initialDelay;
    }

    public long getBackOffDelay() {
        return backOffDelay;
    }

    public void setBackOffDelay(long backOffDelay) {
        this.backOffDelay = backOffDelay;
    }

    public long getBackOffMaxDelay() {
        return backOffMaxDelay;
    }

    public void setBackOffMaxDelay(long backOffMaxDelay) {
        this.backOffMaxDelay = backOffMaxDelay;
    }

    public long getBackOffMaxElapsedTime() {
        return backOffMaxElapsedTime;
    }

    public void setBackOffMaxElapsedTime(long backOffMaxElapsedTime) {
        this.backOffMaxElapsedTime = backOffMaxElapsedTime;
    }

    public long getBackOffMaxAttempts() {
        return backOffMaxAttempts;
    }

    public void setBackOffMaxAttempts(long backOffMaxAttempts) {
        this.backOffMaxAttempts = backOffMaxAttempts;
    }

    public double getBackOffMultiplier() {
        return backOffMultiplier;
    }

    public void setBackOffMultiplier(double backOffMultiplier) {
        this.backOffMultiplier = backOffMultiplier;
    }

    public boolean isUnhealthyOnExhausted() {
        return unhealthyOnExhausted;
    }

    public void setUnhealthyOnExhausted(boolean unhealthyOnExhausted) {
        this.unhealthyOnExhausted = unhealthyOnExhausted;
    }

    protected BackOff getBackOff(String id) {
        // currently all routes use the same backoff
        return backOff;
    }

    // *********************************
    // Lifecycle
    // *********************************

    @Override
    protected void doInit() throws Exception {
        this.listener = new CamelContextStartupListener();

        // prevent routes from automatic being started by default
        CamelContext context = getCamelContext();
        context.setAutoStartup(false);
        // use route policy to supervise the routes
        context.addRoutePolicyFactory(new ManagedRoutePolicyFactory());
        // use startup listener to hook into camel context to let this begin supervising routes after context is started
        context.addStartupListener(this.listener);
    }

    @Override
    protected void doStart() throws Exception {
        this.backOff = new BackOff(
                Duration.ofMillis(backOffDelay),
                backOffMaxDelay > 0 ? Duration.ofMillis(backOffMaxDelay) : null,
                backOffMaxElapsedTime > 0 ? Duration.ofMillis(backOffMaxElapsedTime) : null,
                backOffMaxAttempts > 0 ? backOffMaxAttempts : Long.MAX_VALUE,
                backOffMultiplier);

        CamelContext context = getCamelContext();
        if (threadPoolSize == 1) {
            executorService
                    = context.getExecutorServiceManager().newSingleThreadScheduledExecutor(this, "SupervisingRouteController");
        } else {
            executorService = context.getExecutorServiceManager().newScheduledThreadPool(this, "SupervisingRouteController",
                    threadPoolSize);
        }
        timer = new BackOffTimer(executorService);
    }

    @Override
    protected void doStop() throws Exception {
        if (getCamelContext() != null && executorService != null) {
            getCamelContext().getExecutorServiceManager().shutdown(executorService);
            executorService = null;
            timer = null;
        }
    }

    // *********************************
    // Route management
    // *********************************

    @Override
    public void startRoute(String routeId) throws Exception {
        final Optional<RouteHolder> route = routes.stream().filter(r -> r.getId().equals(routeId)).findFirst();

        if (route.isEmpty()) {
            // This route is unknown to this controller, apply default behaviour
            // from super class.
            super.startRoute(routeId);
        } else {
            doStartRoute(route.get(), true, r -> super.startRoute(routeId));
        }
    }

    @Override
    public void stopRoute(String routeId) throws Exception {
        final Optional<RouteHolder> route = routes.stream().filter(r -> r.getId().equals(routeId)).findFirst();

        if (route.isEmpty()) {
            // This route is unknown to this controller, apply default behaviour
            // from super class.
            super.stopRoute(routeId);
        } else {
            doStopRoute(route.get(), true, r -> super.stopRoute(routeId));
        }
    }

    @Override
    public void stopRoute(String routeId, Throwable cause) throws Exception {
        final Optional<RouteHolder> route = routes.stream().filter(r -> r.getId().equals(routeId)).findFirst();

        if (route.isEmpty()) {
            // This route is unknown to this controller, apply default behaviour
            // from super class.
            super.stopRoute(routeId, cause);
        } else {
            doStopRoute(route.get(), true, r -> super.stopRoute(routeId, cause));
        }
    }

    @Override
    public void stopRoute(String routeId, long timeout, TimeUnit timeUnit) throws Exception {
        final Optional<RouteHolder> route = routes.stream().filter(r -> r.getId().equals(routeId)).findFirst();

        if (route.isEmpty()) {
            // This route is unknown to this controller, apply default behaviour
            // from super class.
            super.stopRoute(routeId, timeout, timeUnit);
        } else {
            doStopRoute(route.get(), true, r -> super.stopRoute(r.getId(), timeout, timeUnit));
        }
    }

    @Override
    public boolean stopRoute(String routeId, long timeout, TimeUnit timeUnit, boolean abortAfterTimeout) throws Exception {
        final Optional<RouteHolder> route = routes.stream().filter(r -> r.getId().equals(routeId)).findFirst();

        if (!route.isPresent()) {
            // This route is unknown to this controller, apply default behaviour
            // from super class.
            return super.stopRoute(routeId, timeout, timeUnit, abortAfterTimeout);
        } else {
            final AtomicBoolean result = new AtomicBoolean();

            doStopRoute(route.get(), true, r -> result.set(super.stopRoute(r.getId(), timeout, timeUnit, abortAfterTimeout)));
            return result.get();
        }
    }

    @Override
    public void suspendRoute(String routeId) throws Exception {
        final Optional<RouteHolder> route = routes.stream().filter(r -> r.getId().equals(routeId)).findFirst();

        if (route.isEmpty()) {
            // This route is unknown to this controller, apply default behaviour
            // from super class.
            super.suspendRoute(routeId);
        } else {
            doStopRoute(route.get(), true, r -> super.suspendRoute(r.getId()));
        }
    }

    @Override
    public void suspendRoute(String routeId, long timeout, TimeUnit timeUnit) throws Exception {
        final Optional<RouteHolder> route = routes.stream().filter(r -> r.getId().equals(routeId)).findFirst();

        if (route.isEmpty()) {
            // This route is unknown to this controller, apply default behaviour
            // from super class.
            super.suspendRoute(routeId, timeout, timeUnit);
        } else {
            doStopRoute(route.get(), true, r -> super.suspendRoute(r.getId(), timeout, timeUnit));
        }
    }

    @Override
    public void resumeRoute(String routeId) throws Exception {
        final Optional<RouteHolder> route = routes.stream().filter(r -> r.getId().equals(routeId)).findFirst();

        if (route.isEmpty()) {
            // This route is unknown to this controller, apply default behaviour
            // from super class.
            super.resumeRoute(routeId);
        } else {
            doStartRoute(route.get(), true, r -> super.startRoute(routeId));
        }
    }

    @Override
    public Collection<Route> getControlledRoutes() {
        return routes.stream()
                .map(RouteHolder::get)
                .toList();
    }

    @Override
    public Collection<Route> getRestartingRoutes() {
        return routeManager.routes.keySet().stream()
                .map(RouteHolder::get)
                .toList();
    }

    @Override
    public Collection<Route> getExhaustedRoutes() {
        return routeManager.exhausted.keySet().stream()
                .map(RouteHolder::get)
                .toList();
    }

    @Override
    public Set<String> getNonControlledRouteIds() {
        return Collections.unmodifiableSet(nonSupervisedRoutes);
    }

    @Override
    public BackOffTimer.Task getRestartingRouteState(String routeId) {
        return routeManager.getBackOffContext(routeId).orElse(null);
    }

    @Override
    public Throwable getRestartException(String routeId) {
        return routeManager.exceptions.get(routeId);
    }

    // *********************************
    // Helpers
    // *********************************

    private void doStopRoute(RouteHolder route, boolean checker, ThrowingConsumer<RouteHolder, Exception> consumer)
            throws Exception {
        synchronized (lock) {
            if (checker) {
                // remove it from checked routes so the route don't get started
                // by the routes manager task as a manual operation on the routes
                // indicates that the route is then managed manually
                routeManager.release(route);
            }

            LOG.debug("Route {} has been requested to stop", route.getId());

            // Mark the route as un-managed
            route.get().setRouteController(null);

            consumer.accept(route);
        }
    }

    private void doStartRoute(RouteHolder route, boolean checker, ThrowingConsumer<RouteHolder, Exception> consumer)
            throws Exception {
        synchronized (lock) {
            // If a manual start is triggered, then the controller should take
            // care that the route is started
            route.get().setRouteController(this);

            try {
                if (checker) {
                    // remove it from checked routes as a manual start may trigger
                    // a new back off task if start fails
                    routeManager.release(route);
                }

                consumer.accept(route);
            } catch (Exception e) {
                if (checker) {
                    // if start fails the route is moved to controller supervision
                    // so its get (eventually) restarted
                    routeManager.start(route);
                }

                throw e;
            }
        }
    }

    private void startNonSupervisedRoutes() throws Exception {
        if (!isRunAllowed()) {
            return;
        }

        final List<String> routeList;

        synchronized (lock) {
            routeList = routes.stream()
                    .filter(r -> r.getStatus() == ServiceStatus.Stopped)
                    .filter(r -> !isSupervised(r.route))
                    .map(RouteHolder::getId)
                    .toList();
        }

        for (String route : routeList) {
            try {
                // let non supervising controller start the route by calling super
                LOG.debug("Starting non-supervised route {}", route);
                super.startRoute(route);
            } catch (Exception e) {
                throw new FailedToStartRouteException(route, e.getMessage(), e);
            }
        }
    }

    private void startSupervisedRoutes() {
        if (!isRunAllowed()) {
            return;
        }

        final List<String> routeList;

        synchronized (lock) {
            routeList = routes.stream()
                    .filter(r -> r.getStatus() == ServiceStatus.Stopped)
                    .filter(r -> isSupervised(r.route))
                    .map(RouteHolder::getId)
                    .toList();
        }

        LOG.debug("Starting {} supervised routes", routeList.size());
        for (String route : routeList) {
            try {
                startRoute(route);
            } catch (Exception e) {
                // ignored, exception handled by startRoute
            }
        }

        if (getCamelContext().getStartupSummaryLevel() != StartupSummaryLevel.Off
                && getCamelContext().getStartupSummaryLevel() != StartupSummaryLevel.Oneline) {
            // log after first round of attempts
            logRouteStartupSummary();
        }
    }

    private void logRouteStartupSummary() {
        int started = 0;
        int total = 0;
        int restarting = 0;
        int exhausted = 0;
        List<String> lines = new ArrayList<>();
        List<String> configs = new ArrayList<>();
        for (RouteHolder route : routes) {
            String id = route.getId();
            String status = getRouteStatus(id).name();
            if (ServiceStatus.Started.name().equals(status)) {
                // only include started routes as we pickup restarting/exhausted in the following
                total++;
                started++;
                // use basic endpoint uri to not log verbose details or potential sensitive data
                String uri = route.get().getEndpoint().getEndpointBaseUri();
                uri = URISupport.sanitizeUri(uri);
                lines.add(String.format("    %s %s (%s)", status, id, uri));
                String cid = route.get().getConfigurationId();
                if (cid != null) {
                    configs.add(String.format("    %s (%s)", id, cid));
                }
            }
        }
        for (RouteHolder route : routeManager.routes.keySet()) {
            total++;
            restarting++;
            String id = route.getId();
            String status = "Restarting";
            // use basic endpoint uri to not log verbose details or potential sensitive data
            String uri = route.get().getEndpoint().getEndpointBaseUri();
            uri = URISupport.sanitizeUri(uri);
            BackOff backOff = getBackOff(id);
            lines.add(String.format("    %s %s (%s) with %s", status, id, uri, backOff));
            String cid = route.get().getConfigurationId();
            if (cid != null) {
                configs.add(String.format("    %s (%s)", id, cid));
            }
        }
        for (RouteHolder route : routeManager.exhausted.keySet()) {
            total++;
            exhausted++;
            String id = route.getId();
            String status = "Exhausted";
            // use basic endpoint uri to not log verbose details or potential sensitive data
            String uri = route.get().getEndpoint().getEndpointBaseUri();
            uri = URISupport.sanitizeUri(uri);
            lines.add(String.format("    %s %s (%s)", status, id, uri));
            String cid = route.get().getConfigurationId();
            if (cid != null) {
                configs.add(String.format("    %s (%s)", id, cid));
            }
        }

        if (restarting == 0 && exhausted == 0) {
            LOG.info("Routes startup (total:{} started:{})", total, started);
        } else {
            LOG.info("Routes startup (total:{} started:{} restarting:{} exhausted:{})", total, started, restarting,
                    exhausted);
        }
        if (getCamelContext().getStartupSummaryLevel() == StartupSummaryLevel.Default
                || getCamelContext().getStartupSummaryLevel() == StartupSummaryLevel.Verbose) {
            for (String line : lines) {
                LOG.info(line);
            }
            if (getCamelContext().getStartupSummaryLevel() == StartupSummaryLevel.Verbose) {
                LOG.info("Routes configuration:");
                for (String line : configs) {
                    LOG.info(line);
                }
            }
        }
    }

    private boolean isSupervised(Route route) {
        return !nonSupervisedRoutes.contains(route.getId());
    }

    // *********************************
    // RouteChecker
    // *********************************

    private class RouteManager {
        private final Logger logger;
        private final ConcurrentMap<RouteHolder, BackOffTimer.Task> routes;
        private final ConcurrentMap<RouteHolder, BackOffTimer.Task> exhausted;
        private final ConcurrentMap<String, Throwable> exceptions;

        RouteManager() {
            this.logger = LoggerFactory.getLogger(RouteManager.class);
            this.routes = new ConcurrentHashMap<>();
            this.exhausted = new ConcurrentHashMap<>();
            this.exceptions = new ConcurrentHashMap<>();
        }

        void start(RouteHolder route) {
            route.get().setRouteController(DefaultSupervisingRouteController.this);

            routes.computeIfAbsent(
                    route,
                    r -> {
                        BackOff backOff = getBackOff(r.getId());

                        logger.debug("Supervising route: {} with back-off: {}", r.getId(), backOff);

                        BackOffTimer.Task task = timer.schedule(backOff, context -> {
                            final BackOffTimer.Task state = getBackOffContext(r.getId()).orElse(null);
                            long attempt = state != null ? state.getCurrentAttempts() : 0;
                            try {
                                logger.info("Restarting route: {} attempt: {}", r.getId(), attempt);
                                doStartRoute(r, false, rx -> DefaultSupervisingRouteController.super.startRoute(rx.getId()));
                                logger.info("Route: {} started after {} attempts", r.getId(), attempt);
                                return false;
                            } catch (Exception e) {
                                exceptions.put(r.getId(), e);
                                String cause = e.getClass().getName() + ": " + e.getMessage();
                                logger.info("Failed restarting route: {} attempt: {} due: {} (stacktrace in debug log level)",
                                        r.getId(), attempt, cause);
                                logger.debug("    Error restarting route caused by: " + e.getMessage(), e);
                                return true;
                            }
                        });

                        task.whenComplete((backOffTask, throwable) -> {
                            if (backOffTask == null || backOffTask.getStatus() != BackOffTimer.Task.Status.Active) {
                                // This indicates that the task has been cancelled
                                // or that back-off retry is exhausted thus if the
                                // route is not started it is moved out of the
                                // supervisor control.

                                synchronized (lock) {
                                    final ServiceStatus status = route.getStatus();
                                    final boolean stopped = status.isStopped() || status.isStopping();

                                    if (backOffTask != null && backOffTask.getStatus() == BackOffTimer.Task.Status.Exhausted
                                            && stopped) {
                                        LOG.warn(
                                                "Restarting route: {} is exhausted after {} attempts. No more attempts will be made"
                                                 + " and the route is no longer supervised by this route controller and remains as stopped.",
                                                route.getId(), backOffTask.getCurrentAttempts() - 1);
                                        r.get().setRouteController(null);
                                        // remember exhausted routes
                                        routeManager.exhausted.put(r, task);

                                        if (unhealthyOnExhausted) {
                                            // store as last error on route as it was exhausted
                                            Throwable t = getRestartException(route.getId());
                                            if (t != null) {
                                                DefaultRouteError.set(getCamelContext(), r.getId(), RouteError.Phase.START, t,
                                                        true);
                                            }
                                        }
                                    }
                                }
                            }

                            routes.remove(r);
                        });

                        return task;
                    });
        }

        boolean release(RouteHolder route) {
            exceptions.remove(route.getId());
            BackOffTimer.Task task = routes.remove(route);
            if (task != null) {
                LOG.debug("Cancelling restart task for route: {}", route.getId());
                task.cancel();
            }

            return task != null;
        }

        public Optional<BackOffTimer.Task> getBackOffContext(String id) {
            Optional<BackOffTimer.Task> answer = routes.entrySet().stream()
                    .filter(e -> ObjectHelper.equal(e.getKey().getId(), id))
                    .findFirst()
                    .map(Map.Entry::getValue);
            if (!answer.isPresent()) {
                answer = exhausted.entrySet().stream()
                        .filter(e -> ObjectHelper.equal(e.getKey().getId(), id))
                        .findFirst()
                        .map(Map.Entry::getValue);
            }
            return answer;
        }
    }

    // *********************************
    //
    // *********************************

    private static class RouteHolder implements HasId, Comparable<RouteHolder> {
        private final int order;
        private final Route route;

        RouteHolder(Route route, int order) {
            this.route = route;
            this.order = order;
        }

        @Override
        public String getId() {
            return this.route.getId();
        }

        public Route get() {
            return this.route;
        }

        public ServiceStatus getStatus() {
            return route.getCamelContext().getRouteController().getRouteStatus(getId());
        }

        int getInitializationOrder() {
            return order;
        }

        public int getStartupOrder() {
            Integer order = route.getStartupOrder();
            if (order == null) {
                order = Integer.MAX_VALUE;
            }

            return order;
        }

        @Override
        public int compareTo(RouteHolder o) {
            int answer = Integer.compare(getStartupOrder(), o.getStartupOrder());
            if (answer == 0) {
                answer = Integer.compare(getInitializationOrder(), o.getInitializationOrder());
            }

            return answer;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            return this.route.equals(((RouteHolder) o).route);
        }

        @Override
        public int hashCode() {
            return route.hashCode();
        }
    }

    // *********************************
    // Policies
    // *********************************

    private class ManagedRoutePolicyFactory implements RoutePolicyFactory {
        private final RoutePolicy policy = new ManagedRoutePolicy();

        @Override
        public RoutePolicy createRoutePolicy(CamelContext camelContext, String routeId, NamedNode route) {
            return policy;
        }
    }

    private class ManagedRoutePolicy extends RoutePolicySupport implements NonManagedService {

        // we dont want this policy to be registered in JMX

        private void startRoute(RouteHolder holder) {
            try {
                DefaultSupervisingRouteController.this.doStartRoute(
                        holder,
                        true,
                        r -> DefaultSupervisingRouteController.super.startRoute(r.getId()));
            } catch (Exception e) {
                throw new RuntimeCamelException(e);
            }
        }

        @Override
        public void onInit(Route route) {
            if (!route.isAutoStartup()) {
                LOG.info("Route: {} will not be supervised (Reason: has explicit auto-startup flag set to false)",
                        route.getId());
                return;
            }

            // exclude takes precedence
            if (excludeRoutes != null) {
                for (String part : excludeRoutes.split(",")) {
                    String id = route.getRouteId();
                    String uri = route.getEndpoint().getEndpointUri();
                    boolean exclude = PatternHelper.matchPattern(id, part) || PatternHelper.matchPattern(uri, part);
                    if (exclude) {
                        LOG.debug("Route: {} excluded from being supervised", route.getId());
                        RouteHolder holder = new RouteHolder(route, routeCount.incrementAndGet());
                        if (routes.add(holder)) {
                            nonSupervisedRoutes.add(route.getId());
                            holder.get().setRouteController(DefaultSupervisingRouteController.this);
                            // this route should be started
                            holder.get().setAutoStartup(true);
                        }
                        return;
                    }
                }
            }
            if (includeRoutes != null) {
                boolean include = false;
                for (String part : includeRoutes.split(",")) {
                    String id = route.getRouteId();
                    String uri = route.getEndpoint().getEndpointUri();
                    include = PatternHelper.matchPattern(id, part) || PatternHelper.matchPattern(uri, part);
                    if (include) {
                        break;
                    }
                }
                if (!include) {
                    LOG.debug("Route: {} excluded from being supervised", route.getId());
                    RouteHolder holder = new RouteHolder(route, routeCount.incrementAndGet());
                    if (routes.add(holder)) {
                        nonSupervisedRoutes.add(route.getId());
                        holder.get().setRouteController(DefaultSupervisingRouteController.this);
                        // this route should be started
                        holder.get().setAutoStartup(true);
                    }
                    return;
                }
            }

            RouteHolder holder = new RouteHolder(route, routeCount.incrementAndGet());
            if (routes.add(holder)) {
                holder.get().setRouteController(DefaultSupervisingRouteController.this);
                holder.get().setAutoStartup(false);
                holder.get().getProperties().put(Route.SUPERVISED, true); // mark route as being supervised

                if (contextStarted.get()) {
                    LOG.debug("Context is already started: attempt to start route {}", route.getId());

                    // Eventually delay the startup of the route a later time
                    if (initialDelay > 0) {
                        LOG.debug("Route {} will be started in {} millis", holder.getId(), initialDelay);
                        executorService.schedule(() -> startRoute(holder), initialDelay, TimeUnit.MILLISECONDS);
                    } else {
                        startRoute(holder);
                    }
                } else {
                    LOG.debug("CamelContext is not yet started. Deferring staring route: {}", holder.getId());
                }
            }
        }

        @Override
        public void onRemove(Route route) {
            synchronized (lock) {
                routes.removeIf(
                        r -> ObjectHelper.equal(r.get(), route) || ObjectHelper.equal(r.getId(), route.getId()));
            }
        }

    }

    private class CamelContextStartupListener implements ExtendedStartupListener {

        @Override
        public void onCamelContextStarting(CamelContext context, boolean alreadyStarted) throws Exception {
            // noop
        }

        @Override
        public void onCamelContextStarted(CamelContext context, boolean alreadyStarted) throws Exception {
            // noop
        }

        @Override
        public void onCamelContextFullyStarted(CamelContext context, boolean alreadyStarted) throws Exception {
            if (alreadyStarted) {
                // Invoke it only if the context was already started as this
                // method is not invoked at last event as documented but after
                // routes warm-up so this is useful for routes deployed after
                // the camel context has been started-up. For standard routes
                // configuration the notification of the camel context started
                // is provided by EventNotifier.
                //
                // We should check why this callback is not invoked at latest
                // stage, or maybe rename it as it is misleading and provide a
                // better alternative for intercept camel events.
                onCamelContextStarted();
            }
        }

        private void onCamelContextStarted() throws Exception {
            // Start managing the routes only when the camel context is started
            // so start/stop of managed routes do not clash with CamelContext
            // startup
            if (contextStarted.compareAndSet(false, true)) {
                // start non supervised routes first as if they fail then
                // camel context fails to start which is the behaviour of non-supervised routes
                startNonSupervisedRoutes();

                // Eventually delay the startup of the routes a later time
                if (initialDelay > 0) {
                    LOG.debug("Supervised routes will be started in {} millis", initialDelay);
                    executorService.schedule(DefaultSupervisingRouteController.this::startSupervisedRoutes, initialDelay,
                            TimeUnit.MILLISECONDS);
                } else {
                    startSupervisedRoutes();
                }
            }
        }
    }

}
