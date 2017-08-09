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
package org.apache.camel.impl.ha;

import java.time.Duration;
import java.util.EventObject;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.apache.camel.StartupListener;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.ha.CamelClusterEventListener;
import org.apache.camel.ha.CamelClusterMember;
import org.apache.camel.ha.CamelClusterService;
import org.apache.camel.ha.CamelClusterView;
import org.apache.camel.management.event.CamelContextStartedEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.util.ReferenceCount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ManagedResource(description = "Clustered Route policy using")
public class ClusteredRoutePolicy extends RoutePolicySupport implements CamelContextAware {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClusteredRoutePolicy.class);

    private final AtomicBoolean leader;
    private final Set<Route> startedRoutes;
    private final Set<Route> stoppedRoutes;
    private final ReferenceCount refCount;
    private final CamelClusterView clusterView;
    private final CamelClusterEventListener.Leadership leadershipEventListener;
    private final CamelContextStartupListener listener;
    private final AtomicBoolean contextStarted;
    private Duration initialDelay;
    private ScheduledExecutorService executorService;

    private CamelContext camelContext;

    public ClusteredRoutePolicy(CamelClusterView clusterView) {
        this.clusterView = clusterView;
        this.leadershipEventListener = new CamelClusterLeadershipListener();

        this.stoppedRoutes = new HashSet<>();
        this.startedRoutes = new HashSet<>();
        this.leader = new AtomicBoolean(false);
        this.contextStarted = new AtomicBoolean(false);
        this.initialDelay = Duration.ofMillis(0);

        try {
            this.listener = new CamelContextStartupListener();
            this.listener.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Cleanup the policy when all the routes it manages have been shut down
        // so a single policy instance can be shared among routes.
        this.refCount = ReferenceCount.onRelease(() -> {
            if (camelContext != null) {
                camelContext.getManagementStrategy().removeEventNotifier(listener);
                if (executorService != null) {
                    camelContext.getExecutorServiceManager().shutdownNow(executorService);
                }
            }

            clusterView.removeEventListener(leadershipEventListener);
            setLeader(false);
        });
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
                "CamelContext should not be changed: current=" + this.camelContext + ", new=" + camelContext
            );
        }

        try {
            this.camelContext = camelContext;
            this.camelContext.addStartupListener(this.listener);
            this.camelContext.getManagementStrategy().addEventNotifier(this.listener);
            this.executorService = camelContext.getExecutorServiceManager().newSingleThreadScheduledExecutor(this, "ClusteredRoutePolicy");
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

    @Override
    public void onInit(Route route) {
        super.onInit(route);

        LOGGER.info("Route managed by {}. Setting route {} AutoStartup flag to false.", getClass(), route.getId());
        route.getRouteContext().getRoute().setAutoStartup("false");

        this.refCount.retain();
        this.stoppedRoutes.add(route);

        startManagedRoutes();
    }

    @Override
    public void doShutdown() throws Exception {
        this.refCount.release();
    }

    // ****************************************************
    // Management
    // ****************************************************

    @ManagedAttribute(description = "Is this route the master or a slave")
    public boolean isLeader() {
        return leader.get();
    }

    // ****************************************************
    // Route managements
    // ****************************************************

    private synchronized void setLeader(boolean isLeader) {
        if (isLeader && leader.compareAndSet(false, isLeader)) {
            LOGGER.debug("Leadership taken");
            startManagedRoutes();
        } else if (!isLeader && leader.getAndSet(isLeader)) {
            LOGGER.debug("Leadership lost");
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
        if (!isRunAllowed()) {
            return;
        }

        try {
            for (Route route : stoppedRoutes) {
                ServiceStatus status = route.getRouteContext().getRoute().getStatus(getCamelContext());
                if (status.isStartable()) {
                    LOGGER.debug("Starting route '{}'", route.getId());
                    camelContext.startRoute(route.getId());

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
                ServiceStatus status = route.getRouteContext().getRoute().getStatus(getCamelContext());
                if (status.isStoppable()) {
                    LOGGER.debug("Stopping route '{}'", route.getId());
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
        LOGGER.debug("Apply cluster policy (stopped-routes='{}', started-routes='{}')",
            stoppedRoutes.stream().map(Route::getId).collect(Collectors.joining(",")),
            startedRoutes.stream().map(Route::getId).collect(Collectors.joining(","))
        );

        clusterView.addEventListener(leadershipEventListener);
        setLeader(clusterView.getLocalMember().isMaster());
    }

    // ****************************************************
    // Event handling
    // ****************************************************

    private class CamelClusterLeadershipListener implements CamelClusterEventListener.Leadership {
        @Override
        public void leadershipChanged(CamelClusterView view, CamelClusterMember leader) {
            setLeader(clusterView.getLocalMember().isMaster());
        }
    }

    private class CamelContextStartupListener extends EventNotifierSupport implements StartupListener {
        @Override
        public void notify(EventObject event) throws Exception {
            onCamelContextStarted();
        }

        @Override
        public boolean isEnabled(EventObject event) {
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
                    LOGGER.debug("Policy will be effective in {}", initialDelay);
                    executorService.schedule(ClusteredRoutePolicy.this::onCamelContextStarted, initialDelay.toMillis(), TimeUnit.MILLISECONDS);
                } else {
                    ClusteredRoutePolicy.this.onCamelContextStarted();
                }
            }
        }
    }

    // ****************************************************
    // Static helpers
    // ****************************************************

    public static ClusteredRoutePolicy forNamespace(CamelContext camelContext, String namespace) throws Exception {
        CamelClusterService cluster = camelContext.hasService(CamelClusterService.class);
        if (cluster == null) {
            throw new IllegalStateException("CamelCluster service not found");
        }

        return forNamespace(cluster, namespace);
    }

    public static ClusteredRoutePolicy forNamespace(CamelClusterService cluster, String namespace) throws Exception {
        return forView(cluster.getView(namespace));
    }

    public static ClusteredRoutePolicy forView(CamelClusterView view) throws Exception  {
        ClusteredRoutePolicy policy = new ClusteredRoutePolicy(view);
        policy.setCamelContext(view.getCamelContext());

        return policy;
    }
}
