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

import org.apache.camel.Exchange;
import org.apache.camel.NonManagedService;
import org.apache.camel.Route;
import org.apache.camel.support.RoutePolicySupport;

/**
 * <code>ZooKeeperRoutePolicy</code> uses the leader election capabilities of a
 * ZooKeeper cluster to control how routes are enabled. It is typically used in
 * fail-over scenarios controlling identical instances of a route across a
 * cluster of Camel based servers.
 * <p>
 * The policy is configured with a 'top n' number of routes that should be
 * allowed to start, for a master/slave scenario this would be 1. Each instance
 * of the policy will execute the election algorithm to obtain its position in
 * the hierarchy of servers, if it is within the 'top n' servers then the policy
 * is enabled and exchanges can be processed by the route. If not it waits for a
 * change in the leader hierarchy and then reruns this scenario to see if it is
 * now in the top n.
 * <p>
 * All instances of the policy must also be configured with the same path on the
 * ZooKeeper cluster where the election will be carried out. It is good practice
 * for this to indicate the application e.g. <tt>/someapplication/someroute/</tt> note
 * that these nodes should exist before using the policy.
 * <p>
 * See <a href="http://hadoop.apache.org/zookeeper/docs/current/recipes.html#sc_leaderElection">
 *     for more on how Leader election</a> is archived with ZooKeeper.
 */
public class ZooKeeperRoutePolicy extends RoutePolicySupport implements ElectionWatcher, NonManagedService {

    private final String uri;
    private final int enabledCount;
    private final Lock lock = new ReentrantLock();
    private final Set<Route> suspendedRoutes = new CopyOnWriteArraySet<Route>();
    private final AtomicBoolean shouldProcessExchanges = new AtomicBoolean();
    private volatile boolean shouldStopConsumer = true;

    private final Lock electionLock = new ReentrantLock();
    private ZooKeeperElection election;

    public ZooKeeperRoutePolicy(String uri, int enabledCount) {
        this.uri = uri;
        this.enabledCount = enabledCount;
    }

    public ZooKeeperRoutePolicy(ZooKeeperElection election) {
        this.election = election;
        this.uri = null;
        this.enabledCount = -1;
    }

    @Override
    public void onExchangeBegin(Route route, Exchange exchange) {
        ensureElectionIsCreated(route);

        if (election.isMaster()) {
            if (shouldStopConsumer) {
                startConsumer(route);
            }
        } else {
            if (shouldStopConsumer) {
                stopConsumer(route);
            }

            IllegalStateException e = new IllegalStateException("Zookeeper based route policy prohibits processing exchanges, stopping route and failing the exchange");
            exchange.setException(e);
        }
    }

    private void ensureElectionIsCreated(Route route) {
        if (election == null) {
            electionLock.lock();
            try {
                if (election == null) { // re-test
                    election = new ZooKeeperElection(route.getRouteContext().getCamelContext(), uri, enabledCount);
                    election.addElectionWatcher(this);
                }
            } finally {
                electionLock.unlock();
            }
        }
    }

    private void startConsumer(Route route) {
        try {
            lock.lock();
            if (suspendedRoutes.contains(route)) {
                startConsumer(route.getConsumer());
                suspendedRoutes.remove(route);
            }
        } catch (Exception e) {
            handleException(e);
        } finally {
            lock.unlock();
        }
    }

    private void stopConsumer(Route route) {
        try {
            lock.lock();
            // check that we should still suspend once the lock is acquired
            if (!suspendedRoutes.contains(route) && !shouldProcessExchanges.get()) {
                stopConsumer(route.getConsumer());
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
            startAllStoppedConsumers();
        }
    }

    private void startAllStoppedConsumers() {
        try {
            lock.lock();
            if (!suspendedRoutes.isEmpty()) {
                if (log.isDebugEnabled()) {
                    log.debug("{} have been stopped previously by policy, restarting.", suspendedRoutes.size());
                }
                for (Route suspended : suspendedRoutes) {
                    startConsumer(suspended.getConsumer());
                }
                suspendedRoutes.clear();
            }

        } catch (Exception e) {
            handleException(e);
        } finally {
            lock.unlock();
        }
    }

    public boolean isShouldStopConsumer() {
        return shouldStopConsumer;
    }

    public void setShouldStopConsumer(boolean shouldStopConsumer) {
        this.shouldStopConsumer = shouldStopConsumer;
    }
}
