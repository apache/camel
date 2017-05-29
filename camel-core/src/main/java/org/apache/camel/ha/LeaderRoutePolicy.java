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
package org.apache.camel.ha;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Route;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ReferenceCount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ManagedResource(description = "Route policy using ...")
public class LeaderRoutePolicy extends RoutePolicySupport implements CamelContextAware {
    private static final Logger LOGGER = LoggerFactory.getLogger(LeaderRoutePolicy.class);

    private final AtomicBoolean leader;
    private final Set<Route> startedRoutes;
    private final Set<Route> stoppedRoutes;
    private final ReferenceCount refCount;
    private final CamelClusterView clusterView;
    private final CamelClusterMember clusterMember;
    private final BiConsumer<CamelClusterView.Event, Object> clusterEventConsumer;
    private CamelContext camelContext;

    public LeaderRoutePolicy(CamelClusterView clusterView, CamelClusterMember clusterMember) {
        this.clusterMember = clusterMember;
        this.clusterView = clusterView;
        this.clusterEventConsumer = this::onClusterEvent;
        this.stoppedRoutes = new HashSet<>();
        this.startedRoutes = new HashSet<>();
        this.leader = new AtomicBoolean(false);

        this.refCount = ReferenceCount.on(
            () -> clusterView.addEventListener(clusterEventConsumer),
            () -> clusterView.removeEventListener(clusterEventConsumer)
        );
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public synchronized void onInit(Route route) {
        super.onInit(route);

        LOGGER.info("Route managed by {}. Setting route {} AutoStartup flag to false.", getClass(), route.getId());
        route.getRouteContext().getRoute().setAutoStartup("false");

        stoppedRoutes.add(route);

        this.refCount.retain();

        startManagedRoutes();
    }

    @Override
    public synchronized void doShutdown() {
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

    private void onClusterEvent(CamelClusterView.Event event, Object payload) {
        if (event == CamelClusterView.Event.KEEP_ALIVE) {
            LOGGER.debug("Got KEEP_ALIVE from cluster '{}' with payload '{}'", clusterView.getCluster().getId(), Objects.toString(payload));
        }
        if (event == CamelClusterView.Event.LEADERSHIP_CHANGED) {
            boolean isLeader = ObjectHelper.equal(clusterMember.getId(), clusterView.getMaster().getId());

            if (isLeader && leader.compareAndSet(false, isLeader)) {
                LOGGER.info("Leadership taken");
                startManagedRoutes();
            } else if (!isLeader && leader.getAndSet(isLeader)) {
                LOGGER.info("Leadership lost");
                stopManagedRoutes();
            }
        }
    }

    private synchronized void startManagedRoutes() {
        if (isLeader()) {
            doStartManagedRoutes();
        } else {
            // If the leadership has been lost in the meanwhile, stop any
            // eventually started route
            doStopManagedRoutes();
        }
    }

    private synchronized void doStartManagedRoutes() {
        try {
            for (Route route : stoppedRoutes) {
                LOGGER.debug("Starting route {}", route.getId());
                startRoute(route);
                startedRoutes.add(route);
            }

            stoppedRoutes.removeAll(startedRoutes);
        } catch (Exception e) {
            handleException(e);
        }
    }

    private synchronized void stopManagedRoutes() {
        if (isLeader()) {
            // If became a leader in the meanwhile, start any eventually stopped
            // route
            doStartManagedRoutes();
        } else {
            doStopManagedRoutes();
        }
    }

    private synchronized void doStopManagedRoutes() {
        try {
            for (Route route : startedRoutes) {
                LOGGER.debug("Stopping route {}", route.getId());
                stopRoute(route);
                stoppedRoutes.add(route);
            }

            startedRoutes.removeAll(stoppedRoutes);
        } catch (Exception e) {
            handleException(e);
        }
    }
}
