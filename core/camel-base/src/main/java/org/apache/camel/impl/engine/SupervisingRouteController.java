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
import java.util.HashMap;
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
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.NamedNode;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.ServiceStatus;
import org.apache.camel.StartupListener;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.CamelContextStartedEvent;
import org.apache.camel.spi.HasId;
import org.apache.camel.spi.RouteController;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.spi.RoutePolicyFactory;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.backoff.BackOff;
import org.apache.camel.util.backoff.BackOffTimer;
import org.apache.camel.util.function.ThrowingConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A supervising capable {@link RouteController} that delays the startup
 * of the routes after the camel context startup and takes control of starting the routes in a safe manner.
 * This controller is able to retry starting failing routes, and have various options to configure
 * settings for backoff between restarting routes.
 */
public class SupervisingRouteController extends DefaultRouteController {
    private static final Logger LOGGER = LoggerFactory.getLogger(SupervisingRouteController.class);
    private final Object lock;
    private final AtomicBoolean contextStarted;
    private final AtomicInteger routeCount;
    private final List<Filter> filters;
    private final Set<RouteHolder> routes;
    private final CamelContextStartupListener listener;
    private final RouteManager routeManager;
    private BackOffTimer timer;
    private ScheduledExecutorService executorService;
    private BackOff defaultBackOff;
    private Map<String, BackOff> backOffConfigurations;
    private Duration initialDelay;

    public SupervisingRouteController() {
        this.lock = new Object();
        this.contextStarted = new AtomicBoolean(false);
        this.filters = new ArrayList<>();
        this.routeCount = new AtomicInteger(0);
        this.routes = new TreeSet<>();
        this.routeManager = new RouteManager();
        this.defaultBackOff = BackOff.builder().build();
        this.backOffConfigurations = new HashMap<>();
        this.initialDelay = Duration.ofMillis(0);

        try {
            this.listener = new CamelContextStartupListener();
            this.listener.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // *********************************
    // Properties
    // *********************************

    public BackOff getDefaultBackOff() {
        return defaultBackOff;
    }

    /**
     * Sets the default back-off.
     */
    public void setDefaultBackOff(BackOff defaultBackOff) {
        this.defaultBackOff = defaultBackOff;
    }

    public Map<String, BackOff> getBackOffConfigurations() {
        return backOffConfigurations;
    }

    /**
     * Set the back-off for the given IDs.
     */
    public void setBackOffConfigurations(Map<String, BackOff> backOffConfigurations) {
        this.backOffConfigurations = backOffConfigurations;
    }

    public BackOff getBackOff(String id) {
        return backOffConfigurations.getOrDefault(id, defaultBackOff);
    }

    /**
     * Sets the back-off to be applied to the given <code>id</code>.
     */
    public void setBackOff(String id, BackOff backOff) {
        backOffConfigurations.put(id, backOff);
    }

    public Duration getInitialDelay() {
        return initialDelay;
    }

    /**
     * Set the amount of time the route controller should wait before to start
     * the routes after the camel context is started or after the route is
     * initialized if the route is created after the camel context is started.
     *
     * @param initialDelay the initial delay.
     */
    public void setInitialDelay(Duration initialDelay) {
        this.initialDelay = initialDelay;
    }

    /**
     * #see {@link this#setInitialDelay(Duration)}
     *
     * @param initialDelay the initial delay amount.
     */
    public void setInitialDelay(long initialDelay, TimeUnit initialDelayUnit) {
        this.initialDelay = Duration.ofMillis(initialDelayUnit.toMillis(initialDelay));
    }

    /**
     * #see {@link this#setInitialDelay(Duration)}
     *
     * @param initialDelay the initial delay in milliseconds.
     */
    public void setInitialDelay(long initialDelay) {
        this.initialDelay = Duration.ofMillis(initialDelay);
    }

    /**
     * Add a filter used to determine the routes to supervise.
     */
    public void addFilter(Filter filter) {
        this.filters.add(filter);
    }

    /**
     * Sets the filters user to determine the routes to supervise.
     */
    public void setFilters(Collection<Filter> filters) {
        this.filters.clear();
        this.filters.addAll(filters);
    }

    public Collection<Filter> getFilters() {
        return Collections.unmodifiableList(filters);
    }

    public Optional<BackOffTimer.Task> getBackOffContext(String id) {
        return routeManager.getBackOffContext(id);
    }

    // *********************************
    // Lifecycle
    // *********************************

    @Override
    protected void doInit() throws Exception {
        CamelContext context = getCamelContext();
        context.setAutoStartup(false);
        context.addRoutePolicyFactory(new ManagedRoutePolicyFactory());
        context.addStartupListener(this.listener);
        context.getManagementStrategy().addEventNotifier(this.listener);
    }

    @Override
    protected void doStart() throws Exception {
        CamelContext context = getCamelContext();
        executorService = context.getExecutorServiceManager().newSingleThreadScheduledExecutor(this, "SupervisingRouteController");
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

    @Override
    protected void doShutdown() throws Exception {
        if (getCamelContext() != null) {
            getCamelContext().getManagementStrategy().removeEventNotifier(listener);
        }
    }

    // *********************************
    // Route management
    // *********************************

    @Override
    public void startRoute(String routeId) throws Exception {
        final Optional<RouteHolder> route = routes.stream().filter(r -> r.getId().equals(routeId)).findFirst();

        if (!route.isPresent()) {
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

        if (!route.isPresent()) {
            // This route is unknown to this controller, apply default behaviour
            // from super class.
            super.stopRoute(routeId);
        } else {
            doStopRoute(route.get(), true, r -> super.stopRoute(routeId));
        }
    }

    @Override
    public void stopRoute(String routeId, long timeout, TimeUnit timeUnit) throws Exception {
        final Optional<RouteHolder> route = routes.stream().filter(r -> r.getId().equals(routeId)).findFirst();

        if (!route.isPresent()) {
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
            final AtomicBoolean result = new AtomicBoolean(false);

            doStopRoute(route.get(), true, r -> result.set(super.stopRoute(r.getId(), timeout, timeUnit, abortAfterTimeout)));
            return result.get();
        }
    }

    @Override
    public void suspendRoute(String routeId) throws Exception {
        final Optional<RouteHolder> route = routes.stream().filter(r -> r.getId().equals(routeId)).findFirst();

        if (!route.isPresent()) {
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

        if (!route.isPresent()) {
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

        if (!route.isPresent()) {
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
            .collect(Collectors.toList());
    }

    // *********************************
    // Helpers
    // *********************************

    private void doStopRoute(RouteHolder route,  boolean checker, ThrowingConsumer<RouteHolder, Exception> consumer) throws Exception {
        synchronized (lock) {
            if (checker) {
                // remove it from checked routes so the route don't get started
                // by the routes manager task as a manual operation on the routes
                // indicates that the route is then managed manually
                routeManager.release(route);
            }

            LOGGER.info("Route {} has been requested to stop: stop supervising it", route.getId());

            // Mark the route as un-managed
            route.get().setRouteController(null);

            consumer.accept(route);
        }
    }

    private void doStartRoute(RouteHolder route, boolean checker, ThrowingConsumer<RouteHolder, Exception> consumer) throws Exception {
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

    private void startRoutes() {
        if (!isRunAllowed()) {
            return;
        }

        final List<String> routeList;

        synchronized (lock) {
            routeList = routes.stream()
                .filter(r -> r.getStatus() == ServiceStatus.Stopped)
                .map(RouteHolder::getId)
                .collect(Collectors.toList());
        }

        for (String route: routeList) {
            try {
                startRoute(route);
            } catch (Exception e) {
                // ignored, exception handled by startRoute
            }
        }

        LOGGER.info("Total managed routes: {} of which {} successfully started and {} re-starting",
            routes.size(),
            routes.stream().filter(r -> r.getStatus() == ServiceStatus.Started).count(),
            routeManager.routes.size()
        );
    }

    // *********************************
    // RouteChecker
    // *********************************

    private class RouteManager {
        private final Logger logger;
        private final ConcurrentMap<RouteHolder, BackOffTimer.Task> routes;

        RouteManager() {
            this.logger = LoggerFactory.getLogger(RouteManager.class);
            this.routes = new ConcurrentHashMap<>();
        }

        void start(RouteHolder route) {
            route.get().setRouteController(SupervisingRouteController.this);

            routes.computeIfAbsent(
                route,
                r -> {
                    BackOff backOff = getBackOff(r.getId());

                    logger.info("Start supervising route: {} with back-off: {}", r.getId(), backOff);

                    BackOffTimer.Task task = timer.schedule(backOff, context -> {
                        try {
                            logger.info("Try to restart route: {}", r.getId());

                            doStartRoute(r, false, rx -> SupervisingRouteController.super.startRoute(rx.getId()));
                            return false;
                        } catch (Exception e) {
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

                                if (backOffTask != null && backOffTask.getStatus() == BackOffTimer.Task.Status.Exhausted && stopped) {
                                    LOGGER.info("Back-off for route {} is exhausted, no more attempts will be made and stop supervising it", route.getId());
                                    r.get().setRouteController(null);
                                }
                            }
                        }

                        routes.remove(r);
                    });

                    return task;
                }
            );
        }

        boolean release(RouteHolder route) {
            BackOffTimer.Task task = routes.remove(route);
            if (task != null) {
                LOGGER.info("Cancel restart task for route {}", route.getId());
                task.cancel();
            }

            return task != null;
        }

        public Optional<BackOffTimer.Task> getBackOffContext(String id) {
            return routes.entrySet().stream()
                .filter(e -> ObjectHelper.equal(e.getKey().getId(), id))
                .findFirst()
                .map(Map.Entry::getValue);
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

            return this.route.equals(((RouteHolder)o).route);
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

    private class ManagedRoutePolicy extends RoutePolicySupport {

        private void startRoute(RouteHolder holder) {
            try {
                SupervisingRouteController.this.doStartRoute(
                    holder,
                    true,
                    r -> SupervisingRouteController.super.startRoute(r.getId())
                );
            } catch (Exception e) {
                throw new RuntimeCamelException(e);
            }
        }

        @Override
        public void onInit(Route route) {
            if (!route.isAutoStartup()) {
                LOGGER.info("Route {} won't be supervised (reason: has explicit auto-startup flag set to false)", route.getId());
                return;
            }

            for (Filter filter : filters) {
                FilterResult result = filter.apply(route);

                if (!result.supervised()) {
                    LOGGER.info("Route {} won't be supervised (reason: {})", route.getId(), result.reason());
                    return;
                }
            }

            RouteHolder holder = new RouteHolder(route, routeCount.incrementAndGet());
            if (routes.add(holder)) {
                holder.get().setRouteController(SupervisingRouteController.this);
                holder.get().setAutoStartup(false);

                if (contextStarted.get()) {
                    LOGGER.info("Context is already started: attempt to start route {}", route.getId());

                    // Eventually delay the startup of the route a later time
                    if (initialDelay.toMillis() > 0) {
                        LOGGER.debug("Route {} will be started in {}", holder.getId(), initialDelay);
                        executorService.schedule(() -> startRoute(holder), initialDelay.toMillis(), TimeUnit.MILLISECONDS);
                    } else {
                        startRoute(holder);
                    }
                } else {
                    LOGGER.info("Context is not yet started: defer route {} start", holder.getId());
                }
            }
        }

        @Override
        public void onRemove(Route route) {
            synchronized (lock) {
                routes.removeIf(
                    r -> ObjectHelper.equal(r.get(), route) || ObjectHelper.equal(r.getId(), route.getId())
                );
            }
        }

    }

    private class CamelContextStartupListener extends EventNotifierSupport implements StartupListener {
        @Override
        public void notify(CamelEvent event) throws Exception {
            onCamelContextStarted();
        }

        @Override
        public boolean isEnabled(CamelEvent event) {
            return event instanceof CamelContextStartedEvent;
        }

        @Override
        public void onCamelContextStarted(CamelContext context, boolean alreadyStarted) throws Exception {
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

        private void onCamelContextStarted() {
            // Start managing the routes only when the camel context is started
            // so start/stop of managed routes do not clash with CamelContext
            // startup
            if (contextStarted.compareAndSet(false, true)) {

                // Eventually delay the startup of the routes a later time
                if (initialDelay.toMillis() > 0) {
                    LOGGER.debug("Routes will be started in {}", initialDelay);
                    executorService.schedule(SupervisingRouteController.this::startRoutes, initialDelay.toMillis(), TimeUnit.MILLISECONDS);
                } else {
                    startRoutes();
                }
            }
        }
    }

    // *********************************
    // Filter
    // *********************************

    public static class FilterResult {
        public static final FilterResult SUPERVISED = new FilterResult(true, null);

        private final boolean controlled;
        private final String reason;

        public FilterResult(boolean controlled, String reason) {
            this.controlled = controlled;
            this.reason = reason;
        }

        public FilterResult(boolean controlled, String format, Object... args) {
            this(controlled, String.format(format, args));
        }

        public boolean supervised() {
            return controlled;
        }

        public String reason() {
            return reason;
        }
    }

    public interface Filter extends Function<Route, FilterResult> {
    }

}
