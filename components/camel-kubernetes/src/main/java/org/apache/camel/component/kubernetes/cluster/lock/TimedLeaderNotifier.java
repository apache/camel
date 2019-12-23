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
package org.apache.camel.component.kubernetes.cluster.lock;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.CamelContext;
import org.apache.camel.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Receives information about the current leader and handles expiration
 * automatically.
 */
public class TimedLeaderNotifier implements Service {

    private static final Logger LOG = LoggerFactory.getLogger(TimedLeaderNotifier.class);

    private static final long FIXED_DELAY = 10;

    private CamelContext camelContext;

    private KubernetesClusterEventHandler handler;

    private Lock lock = new ReentrantLock();

    private ScheduledExecutorService executor;

    private Optional<String> lastCommunicatedLeader = Optional.empty();
    private Set<String> lastCommunicatedMembers = Collections.emptySet();

    private Optional<String> currentLeader = Optional.empty();

    private Set<String> currentMembers;

    private Long timestamp;

    private Long lease;

    private long changeCounter;

    public TimedLeaderNotifier(CamelContext camelContext, KubernetesClusterEventHandler handler) {
        this.camelContext = Objects.requireNonNull(camelContext, "Camel context must be present");
        this.handler = Objects.requireNonNull(handler, "Handler must be present");
    }

    public void refreshLeadership(Optional<String> leader, Long timestamp, Long lease, Set<String> members) {
        Objects.requireNonNull(leader, "leader must be non null (use Optional.empty)");
        Objects.requireNonNull(members, "members must be non null (use empty set)");
        long version;
        try {
            lock.lock();

            this.currentLeader = leader;
            this.currentMembers = members;
            this.timestamp = timestamp;
            this.lease = lease;
            version = ++changeCounter;
        } finally {
            lock.unlock();
        }

        LOG.debug("Updated leader to {} at version version {}", leader, version);
        this.executor.execute(() -> checkAndNotify(version));
        if (leader.isPresent()) {
            long time = System.currentTimeMillis();
            long delay = Math.max(timestamp + lease + FIXED_DELAY - time, FIXED_DELAY);
            LOG.debug("Setting expiration in {} millis for version {}", delay, version);
            this.executor.schedule(() -> expiration(version), delay, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void start() {
        if (this.executor == null) {
            this.executor = camelContext.getExecutorServiceManager().newSingleThreadScheduledExecutor(this, "CamelKubernetesLeaderNotifier");
        }
    }

    @Override
    public void stop() {
        if (this.executor != null) {
            ScheduledExecutorService executor = this.executor;
            this.executor = null;

            executor.shutdownNow();
            try {
                executor.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

    private void expiration(long version) {
        try {
            lock.lock();

            if (version != this.changeCounter) {
                return;
            }

            long time = System.currentTimeMillis();
            if (time < this.timestamp + this.lease) {
                long delay = this.timestamp + this.lease - time;
                LOG.debug("Delaying expiration by {} millis at version version {}", delay + FIXED_DELAY, version);
                this.executor.schedule(() -> expiration(version), delay + FIXED_DELAY, TimeUnit.MILLISECONDS);
                return;
            }
        } finally {
            lock.unlock();
        }

        checkAndNotify(version);
    }

    private void checkAndNotify(long version) {
        Optional<String> leader;
        Set<String> members;
        try {
            lock.lock();

            if (version != this.changeCounter) {
                return;
            }

            leader = this.currentLeader;
            members = this.currentMembers;

            if (leader.isPresent()) {
                long time = System.currentTimeMillis();
                if (time >= this.timestamp + this.lease) {
                    leader = Optional.empty();
                }
            }

        } finally {
            lock.unlock();
        }

        final Optional<String> newLeader = leader;
        if (!newLeader.equals(lastCommunicatedLeader)) {
            lastCommunicatedLeader = newLeader;
            LOG.info("The cluster has a new leader: {}", newLeader);
            try {
                handler.onKubernetesClusterEvent(new KubernetesClusterEvent.KubernetesClusterLeaderChangedEvent() {
                    @Override
                    public Optional<String> getData() {
                        return newLeader;
                    }
                });
            } catch (Throwable t) {
                LOG.warn("Error while communicating the new leader to the handler", t);
            }
        }

        final Set<String> newMembers = members;
        if (!newMembers.equals(lastCommunicatedMembers)) {
            lastCommunicatedMembers = newMembers;
            LOG.info("The list of cluster members has changed: {}", newMembers);
            try {
                handler.onKubernetesClusterEvent(new KubernetesClusterEvent.KubernetesClusterMemberListChangedEvent() {
                    @Override
                    public Set<String> getData() {
                        return newMembers;
                    }
                });
            } catch (Throwable t) {
                LOG.warn("Error while communicating the cluster members to the handler", t);
            }
        }

    }

}
