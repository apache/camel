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
package org.apache.camel.component.zookeeper.policy;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.NonManagedService;
import org.apache.camel.Route;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.RoutePolicySupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



/**
 * <code>CuratorMultiMasterLeaderRoutePolicy</code> uses Apache Curator InterProcessSemaphoreV2 receipe to implement the behavior of having
 * at multiple active instances of  a route, controlled by a specific policy, running. It is typically used in
 * fail-over scenarios controlling identical instances of a route across a cluster of Camel based servers.
 * <p>
 * The policy affects the normal startup lifecycle of CamelContext and Routes, automatically set autoStart property of
 * routes controlled by this policy to false.
 * After Curator receipe identifies the current Policy instance as the Leader between a set of clients that are
 * competing for the role, it will start the route, and only at that moment the route will start its business.
 * This specific behavior is designed to avoid scenarios where such a policy would kick in only after a route had
 * already been started, with the risk, for consumers for example, that some source event might have already been
 * consumed.
 * <p>
 * All instances of the policy must also be configured with the same path on the
 * ZooKeeper cluster where the election will be carried out. It is good practice
 * for this to indicate the application e.g. <tt>/someapplication/someroute/</tt> note
 * that these nodes should exist before using the policy.
 * <p>
 * See <a href="http://hadoop.apache.org/zookeeper/docs/current/recipes.html#sc_leaderElection">
 *     for more on how Leader election</a> is archived with ZooKeeper.
 */
public class CuratorMultiMasterLeaderRoutePolicy extends RoutePolicySupport implements ElectionWatcher, NonManagedService {

    private static final Logger LOG = LoggerFactory.getLogger(CuratorMultiMasterLeaderRoutePolicy.class);
    private final String uri;
    private final Lock lock = new ReentrantLock();
    private final Set<Route> suspendedRoutes = new CopyOnWriteArraySet<Route>();
    private final AtomicBoolean shouldProcessExchanges = new AtomicBoolean();
    private volatile boolean shouldStopRoute = true;
    private final int enabledCount;


    private final Lock electionLock = new ReentrantLock();

    private CuratorMultiMasterLeaderElection election;

    public CuratorMultiMasterLeaderRoutePolicy(String uri, int enabledCount) {
        this.uri = uri;
        this.enabledCount = enabledCount;
    }
    public CuratorMultiMasterLeaderRoutePolicy(String uri) {
        this(uri, 1);
    }

    @Override
    public void onInit(Route route) {
        ensureElectionIsCreated();
        LOG.info("Route managed by {}. Setting route [{}] AutoStartup flag to false.", this.getClass(), route.getId());
        route.getRouteContext().getRoute().setAutoStartup("false");


        if (election.isMaster()) {
            if (shouldStopRoute) {
                startManagedRoute(route);
            }
        } else {
            if (shouldStopRoute) {
                stopManagedRoute(route);
            }
        }

    }

    private void ensureElectionIsCreated() {
        if (election == null) {
            electionLock.lock();
            try {
                if (election == null) { // re-test
                    election = new CuratorMultiMasterLeaderElection(uri, enabledCount);
                    election.addElectionWatcher(this);

                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                electionLock.unlock();
            }
        }
    }

    private void startManagedRoute(Route route) {
        try {
            lock.lock();
            if (suspendedRoutes.contains(route)) {
                startRoute(route);
                suspendedRoutes.remove(route);
            }
        } catch (Exception e) {
            handleException(e);
        } finally {
            lock.unlock();
        }
    }

    private void stopManagedRoute(Route route) {
        try {
            lock.lock();
            // check that we should still suspend once the lock is acquired
            if (!suspendedRoutes.contains(route) && !shouldProcessExchanges.get()) {
                stopRoute(route);
                suspendedRoutes.add(route);
            }
        } catch (Exception e) {
            handleException(e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void electionResultChanged() {
        if (election.isMaster()) {
            startAllStoppedRoutes();
        }
    }

    private void startAllStoppedRoutes() {
        try {
            lock.lock();

            if (!suspendedRoutes.isEmpty()) {
                if (log.isDebugEnabled()) {
                    log.info("{} route(s) have been stopped previously by policy, restarting.", suspendedRoutes.size());
                }
                for (Route suspended : suspendedRoutes) {
                    DefaultCamelContext ctx = (DefaultCamelContext)suspended.getRouteContext().getCamelContext();
                    while (!ctx.isStarted()) {
                        log.info("Context {} is not started yet. Sleeping for a bit.", ctx.getName());
                        Thread.sleep(5000);
                    }
                    log.info("Starting route [{}] defined in context [{}].", suspended.getId(), ctx.getName());
                    startRoute(suspended);
                }
                suspendedRoutes.clear();
            }

        } catch (Exception e) {
            handleException(e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    protected void doShutdown() throws Exception {
        try {
            electionLock.lock();
            election.shutdownClients();
            election = null;
        } finally {
            electionLock.unlock();
        }
    }

    public CuratorMultiMasterLeaderElection getElection() {
        return election;
    }
}
