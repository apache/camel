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
package org.apache.camel.impl.cluster;

import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.ExtendedStartupListener;
import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.cluster.CamelClusterEventListener;
import org.apache.camel.cluster.CamelClusterMember;
import org.apache.camel.cluster.CamelClusterService;
import org.apache.camel.cluster.CamelClusterView;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.CamelContextStartedEvent;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.support.SimpleEventNotifierSupport;
import org.apache.camel.support.cluster.ClusterServiceHelper;
import org.apache.camel.support.cluster.ClusterServiceSelectors;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ReferenceCount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ManagedResource(description = "Clustered Route policy")
public final class ClusteredRoutePolicy extends RoutePolicySupport implements CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(ClusteredRoutePolicy.class);

    private final AtomicBoolean leader;
    private final Set<Route> autoStartupRoutes;
    private final Set<Route> startedRoutes;
    private final Set<Route> stoppedRoutes;
    private final ReferenceCount refCount;
    private final CamelClusterEventListener.Leadership leadershipEventListener;
    private final CamelContextStartupListener listener;
    private final AtomicBoolean contextStarted;

    private final String namespace;
    private final CamelClusterService.Selector clusterServiceSelector;
    private CamelClusterService clusterService;
    private CamelClusterView clusterView;
    private volatile boolean startManagedRoutesEarly;

    private Duration initialDelay;
    private ScheduledExecutorService executorService;

    private CamelContext camelContext;

    private ClusteredRoutePolicy(CamelClusterService clusterService, CamelClusterService.Selector clusterServiceSelector,
                                 String namespace) {
        this.namespace = namespace;
        this.clusterService = clusterService;
        this.clusterServiceSelector = clusterServiceSelector;

        ObjectHelper.notNull(namespace, "Namespace");

        this.leadershipEventListener = new CamelClusterLeadershipListener();

        this.stoppedRoutes = new HashSet<>();
        this.startedRoutes = new HashSet<>();
        this.autoStartupRoutes = new HashSet<>();
        this.leader = new AtomicBoolean();
        this.contextStarted = new AtomicBoolean();
        this.initialDelay = Duration.ofMillis(0);

        try {
            this.listener = new CamelContextStartupListener();
            this.listener.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Cleanup the policy when all the routes it manages have been removed
        // so a single policy instance can be shared among routes.
        // Acquire cluster view once a route is added to the policy
        this.refCount = ReferenceCount.on(this::retainClusterView, this::releaseClusterView);
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        if (this.camelContext == camelContext) {
            return;
        }

        if (this.camelContext != null && this.camelContext != camelContext) {
            throw new IllegalStateException(
                    "CamelContext should not be changed: current=" + this.camelContext + ", new=" + camelContext);
        }

        try {
            this.camelContext = camelContext;
            this.camelContext.addStartupListener(this.listener);
            this.camelContext.getManagementStrategy().addEventNotifier(this.listener);
            this.executorService
                    = camelContext.getExecutorServiceManager().newSingleThreadScheduledExecutor(this, "ClusteredRoutePolicy");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Duration getInitialDelay() {
        return initialDelay;
    }

    public void setInitialDelay(Duration initialDelay) {
        this.initialDelay = initialDelay;
    }

    // ****************************************************
    // life-cycle
    // ****************************************************

    private ServiceStatus getStatus(Route route) {
        if (camelContext != null) {
            ServiceStatus answer = camelContext.getRouteController().getRouteStatus(route.getId());
            if (answer == null) {
                answer = ServiceStatus.Stopped;
            }
            return answer;
        }
        return null;
    }

    @Override
    public void onInit(Route route) {
        super.onInit(route);

        // Increase number of managed routes by this policy, acquire policy view on first run
        this.refCount.retain();

        if (route.isAutoStartup()) {
            autoStartupRoutes.add(route);
        }

        if (camelContext.isStarted() && isLeader()) {
            // when camel context is already started, and we add new routes
            // then let the route controller start the route as usual (no need to mark as auto startup false)
            startedRoutes.add(route);
        } else {
            LOG.info("Route managed by {}. Setting route {} AutoStartup flag to false.", getClass(), route.getId());
            route.setAutoStartup(false);
            this.stoppedRoutes.add(route);
        }

        startManagedRoutes();
    }

    @Override
    protected void doInit() throws Exception {
        if (clusterService == null) {
            clusterService = ClusterServiceHelper.lookupService(camelContext, clusterServiceSelector)
                    .orElseThrow(() -> new IllegalStateException("CamelCluster service not found"));
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("ClusteredRoutePolicy {} is using ClusterService instance {} (id={}, type={})", this, clusterService,
                    clusterService.getId(),
                    clusterService.getClass().getName());
        }
    }

    @Override
    public void onRemove(Route route) {
        // Decrease number of managed routes, release view once there are no route left
        refCount.release();
        autoStartupRoutes.remove(route);
    }

    @Override
    protected void doShutdown() throws Exception {
        releaseClusterView();
        removeCamelEventListeners();
    }

    // ****************************************************
    // Management
    // ****************************************************

    private void removeCamelEventListeners() {
        if (camelContext != null) {
            camelContext.getManagementStrategy().removeEventNotifier(listener);
            if (executorService != null) {
                camelContext.getExecutorServiceManager().shutdownNow(executorService);
            }
        }
    }

    private synchronized void retainClusterView() {
        try {
            clusterView = clusterService.getView(namespace);
            clusterView.addEventListener(leadershipEventListener);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private synchronized void releaseClusterView() {
        try {
            // Remove event listener
            if (clusterView != null) {
                clusterView.removeEventListener(leadershipEventListener);

                // If all the routes have been removed then the view and its
                // resources can eventually be released.
                clusterView.getClusterService().releaseView(clusterView);
                clusterView = null;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            setLeader(false);
        }
    }

    @ManagedAttribute(description = "Is this route the master or a slave")
    public boolean isLeader() {
        return leader.get();
    }

    // ****************************************************
    // Route managements
    // ****************************************************

    private synchronized void setLeader(boolean isLeader) {
        if (isLeader && leader.compareAndSet(false, isLeader)) {
            LOG.debug("Leadership taken");
            startManagedRoutes();
        } else if (!isLeader && leader.getAndSet(isLeader)) {
            LOG.debug("Leadership lost");
            stopManagedRoutes();
        }
    }

    private void startManagedRoutes() {
        if (isLeader()) {
            doStartManagedRoutes();
        } else {
            // If the leadership has been lost in the meanwhile, stop any
            // eventually started route
            doStopManagedRoutes();
        }
    }

    private void doStartManagedRoutes() {
        // if we are currently starting up Camel context then defer starting routes till its fully started
        if (camelContext.isStarting()) {
            LOG.debug("Will defer starting managed routes until camel context is fully started");
            startManagedRoutesEarly = true;
            return;
        }

        if (!isRunAllowed()) {
            return;
        }

        try {
            for (Route route : stoppedRoutes) {
                ServiceStatus status = getStatus(route);
                boolean autostart = autoStartupRoutes.contains(route);

                if (status != null && status.isStartable() && autostart) {
                    LOG.debug("Starting route '{}'", route.getId());
                    camelContext.getRouteController().startRoute(route.getId());

                    startedRoutes.add(route);
                }
            }

            stoppedRoutes.removeAll(startedRoutes);
        } catch (Exception e) {
            handleException(e);
        }
    }

    private void stopManagedRoutes() {
        if (isLeader()) {
            // If became a leader in the meanwhile, start any eventually stopped
            // route
            doStartManagedRoutes();
        } else {
            doStopManagedRoutes();
        }
    }

    private void doStopManagedRoutes() {
        if (!isRunAllowed()) {
            return;
        }

        try {
            for (Route route : startedRoutes) {
                ServiceStatus status = getStatus(route);
                if (status != null && status.isStoppable()) {
                    LOG.debug("Stopping route '{}'", route.getId());
                    stopRoute(route);

                    stoppedRoutes.add(route);
                }
            }

            startedRoutes.removeAll(stoppedRoutes);
        } catch (Exception e) {
            handleException(e);
        }
    }

    private void onCamelContextStarted() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Apply cluster policy (stopped-routes='{}', started-routes='{}')",
                    stoppedRoutes.stream().map(Route::getId).collect(Collectors.joining(",")),
                    startedRoutes.stream().map(Route::getId).collect(Collectors.joining(",")));
        }

        if (startManagedRoutesEarly) {
            LOG.debug(
                    "CamelContext is now fully started, can now start managed routes eager as we were appointed leader during early startup");
            startManagedRoutesEarly = false;
            startManagedRoutes();
        }
    }

    // ****************************************************
    // Event handling
    // ****************************************************

    private class CamelClusterLeadershipListener implements CamelClusterEventListener.Leadership {
        @Override
        public void leadershipChanged(CamelClusterView view, Optional<CamelClusterMember> leader) {
            setLeader(clusterView.getLocalMember().isLeader());
        }
    }

    private class CamelContextStartupListener extends SimpleEventNotifierSupport implements ExtendedStartupListener {
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

        private void onCamelContextStarted() {
            // Start managing the routes only when the camel context is started
            // so start/stop of managed routes do not clash with CamelContext
            // startup
            if (contextStarted.compareAndSet(false, true)) {

                // Eventually delay the startup of the routes a later time
                if (initialDelay.toMillis() > 0) {
                    LOG.debug("Policy will be effective in {}", initialDelay);
                    executorService.schedule(ClusteredRoutePolicy.this::onCamelContextStarted, initialDelay.toMillis(),
                            TimeUnit.MILLISECONDS);
                } else {
                    ClusteredRoutePolicy.this.onCamelContextStarted();
                }
            }
        }
    }

    // ****************************************************
    // Static helpers
    // ****************************************************

    public static ClusteredRoutePolicy forNamespace(
            CamelContext camelContext, CamelClusterService.Selector selector, String namespace)
            throws Exception {
        ClusteredRoutePolicy policy = new ClusteredRoutePolicy(null, selector, namespace);
        policy.setCamelContext(camelContext);

        return policy;
    }

    public static ClusteredRoutePolicy forNamespace(CamelContext camelContext, String namespace) throws Exception {
        return forNamespace(camelContext, ClusterServiceSelectors.DEFAULT_SELECTOR, namespace);
    }

    public static ClusteredRoutePolicy forNamespace(CamelClusterService service, String namespace) throws Exception {
        return new ClusteredRoutePolicy(service, ClusterServiceSelectors.DEFAULT_SELECTOR, namespace);
    }

    public static ClusteredRoutePolicy forNamespace(CamelClusterService.Selector selector, String namespace) throws Exception {
        return new ClusteredRoutePolicy(null, selector, namespace);
    }

    public static ClusteredRoutePolicy forNamespace(String namespace) throws Exception {
        return forNamespace(ClusterServiceSelectors.DEFAULT_SELECTOR, namespace);
    }
}
