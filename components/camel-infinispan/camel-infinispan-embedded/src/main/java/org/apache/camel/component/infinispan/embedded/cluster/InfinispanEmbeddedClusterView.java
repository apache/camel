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
package org.apache.camel.component.infinispan.embedded.cluster;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.apache.camel.cluster.CamelClusterMember;
import org.apache.camel.component.infinispan.InfinispanUtil;
import org.apache.camel.component.infinispan.cluster.InfinispanClusterService;
import org.apache.camel.component.infinispan.cluster.InfinispanClusterView;
import org.apache.camel.component.infinispan.embedded.InfinispanEmbeddedManager;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.infinispan.Cache;
import org.infinispan.notifications.Listener;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryExpired;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryRemoved;
import org.infinispan.notifications.cachelistener.event.CacheEntryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.util.function.Predicates.negate;

public class InfinispanEmbeddedClusterView extends InfinispanClusterView {
    private static final Logger LOGGER = LoggerFactory.getLogger(InfinispanEmbeddedClusterView.class);

    private final InfinispanEmbeddedClusterConfiguration configuration;
    private final InfinispanEmbeddedManager manager;
    private final LocalMember localMember;
    private final LeadershipService leadership;

    private Cache<String, String> cache;

    protected InfinispanEmbeddedClusterView(
                                            InfinispanEmbeddedClusterService cluster,
                                            InfinispanEmbeddedClusterConfiguration configuration,
                                            String namespace) {
        super(cluster, namespace);

        this.configuration = configuration;
        this.manager = new InfinispanEmbeddedManager(this.configuration.getConfiguration());
        this.leadership = new LeadershipService();
        this.localMember = new LocalMember(cluster.getId());
    }

    @SuppressWarnings("unchecked")
    @Override
    public void doStart() throws Exception {
        super.doStart();

        ServiceHelper.startService(manager);

        this.cache = manager.getCache(getNamespace(), Cache.class);

        ServiceHelper.startService(leadership);
    }

    @Override
    public void doStop() throws Exception {
        super.doStop();

        ServiceHelper.stopService(leadership);
        ServiceHelper.stopService(manager);

        this.cache = null;
    }

    @Override
    public CamelClusterMember getLocalMember() {
        return this.localMember;
    }

    @Override
    public List<CamelClusterMember> getMembers() {
        return this.cache != null
                ? cache.keySet().stream()
                        .filter(negate(InfinispanClusterService.LEADER_KEY::equals))
                        .map(ClusterMember::new)
                        .collect(Collectors.toList())
                : Collections.emptyList();
    }

    @Override
    public Optional<CamelClusterMember> getLeader() {
        if (this.cache == null) {
            return Optional.empty();
        }

        String id = cache.get(InfinispanClusterService.LEADER_KEY);
        if (id == null) {
            return Optional.empty();
        }

        return Optional.of(new ClusterMember(id));
    }

    @Override
    protected boolean isLeader(String id) {
        if (this.cache == null) {
            return false;
        }
        if (id == null) {
            return false;
        }

        final String key = InfinispanClusterService.LEADER_KEY;
        final String val = this.cache.get(key);

        return Objects.equals(id, val);
    }

    // *****************************************
    //
    // Service
    //
    // *****************************************

    @Listener(clustered = true, sync = false)
    private final class LeadershipService extends ServiceSupport {
        private final AtomicBoolean running;
        private ScheduledExecutorService executorService;

        LeadershipService() {
            this.running = new AtomicBoolean(false);
        }

        @Override
        protected void doStart() throws Exception {
            super.doStart();

            this.running.set(true);
            this.executorService = InfinispanUtil.newSingleThreadScheduledExecutor(
                    getCamelContext(),
                    this,
                    getLocalMember().getId());

            // register the local member to the inventory
            cache.put(
                    getLocalMember().getId(),
                    "false",
                    configuration.getLifespan(),
                    configuration.getLifespanTimeUnit());

            cache.addListener(this);

            executorService.scheduleAtFixedRate(
                    this::run,
                    0,
                    configuration.getLifespan() / 2,
                    configuration.getLifespanTimeUnit());
        }

        @Override
        protected void doStop() throws Exception {
            super.doStop();

            this.running.set(false);

            if (cache != null) {
                cache.removeListener(this);
            }

            getCamelContext().getExecutorServiceManager().shutdownGraceful(executorService);

            if (cache != null) {
                cache.remove(InfinispanClusterService.LEADER_KEY, getClusterService().getId());

                LOGGER.info("Removing local member, key={}", getLocalMember().getId());
                cache.remove(getLocalMember().getId());
            }
        }

        private boolean isLeader() {
            return getLocalMember().isLeader();
        }

        private void setLeader(boolean leader) {
            ((LocalMember) getLocalMember()).setLeader(leader);
        }

        private synchronized void run() {
            if (!running.get()) {
                return;
            }

            final String leaderKey = InfinispanClusterService.LEADER_KEY;
            final String localId = getLocalMember().getId();

            if (isLeader()) {
                LOGGER.debug("Lock refresh key={}, id{}", leaderKey, localId);

                // I'm still the leader, so refresh the key so it does not expire.
                if (!cache.replace(
                        InfinispanClusterService.LEADER_KEY,
                        getClusterService().getId(),
                        getClusterService().getId(),
                        configuration.getLifespan(),
                        configuration.getLifespanTimeUnit())) {

                    LOGGER.debug("Failed to refresh the lock key={}, id={}", leaderKey, localId);

                    // Looks like I've lost the leadership.
                    setLeader(false);
                }
            }
            if (!isLeader()) {
                LOGGER.debug("Try to acquire lock key={}, id={}", leaderKey, localId);

                Object result = cache.putIfAbsent(
                        InfinispanClusterService.LEADER_KEY,
                        getClusterService().getId(),
                        configuration.getLifespan(),
                        configuration.getLifespanTimeUnit());
                if (result == null) {
                    LOGGER.debug("Lock acquired key={}, id={}", leaderKey, localId);
                    // Acquired the key so I'm the leader.
                    setLeader(true);
                } else if (Objects.equals(getClusterService().getId(), result) && !isLeader()) {
                    LOGGER.debug("Lock resumed key={}, id={}", leaderKey, localId);
                    // Hey, I may have recovered from failure (or reboot was really
                    // fast) and my key was still there so yeah, I'm the leader again!
                    setLeader(true);
                } else {
                    LOGGER.debug("Failed to acquire the lock key={}, id={}", leaderKey, localId);
                    setLeader(false);
                }
            }

            // refresh local membership
            cache.put(
                    getLocalMember().getId(),
                    isLeader() ? "true" : "false",
                    configuration.getLifespan(),
                    configuration.getLifespanTimeUnit());
        }

        @CacheEntryRemoved
        public void onCacheEntryRemoved(CacheEntryEvent<Object, Object> event) {
            if (!running.get()) {
                return;
            }

            LOGGER.debug("onCacheEntryRemoved id={}, lock-key={}, event-key={}",
                    getLocalMember().getId(),
                    InfinispanClusterService.LEADER_KEY,
                    event.getKey());

            if (Objects.equals(InfinispanClusterService.LEADER_KEY, event.getKey())) {
                executorService.execute(this::run);
            }
        }

        @CacheEntryExpired
        public void onCacheEntryExpired(CacheEntryEvent<Object, Object> event) {
            if (!running.get()) {
                return;
            }

            LOGGER.debug("onCacheEntryExpired id={}, lock-key={}, event-key={}",
                    getLocalMember().getId(),
                    InfinispanClusterService.LEADER_KEY,
                    event.getKey());

            if (Objects.equals(InfinispanClusterService.LEADER_KEY, event.getKey())) {
                executorService.execute(this::run);
            }
        }
    }
}
