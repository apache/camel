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
package org.apache.camel.component.consul.cluster;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.apache.camel.cluster.CamelClusterMember;
import org.apache.camel.support.cluster.AbstractCamelClusterView;
import org.apache.camel.util.ObjectHelper;
import org.kiwiproject.consul.Consul;
import org.kiwiproject.consul.ConsulException;
import org.kiwiproject.consul.KeyValueClient;
import org.kiwiproject.consul.SessionClient;
import org.kiwiproject.consul.async.ConsulResponseCallback;
import org.kiwiproject.consul.model.ConsulResponse;
import org.kiwiproject.consul.model.kv.Value;
import org.kiwiproject.consul.model.session.ImmutableSession;
import org.kiwiproject.consul.model.session.SessionInfo;
import org.kiwiproject.consul.option.QueryOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ConsulClusterView extends AbstractCamelClusterView {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConsulClusterView.class);

    private final ConsulClusterConfiguration configuration;
    private final ConsulLocalMember localMember;
    private final Lock sessionIdLock = new ReentrantLock();
    private final AtomicReference<String> sessionId;
    private final Watcher watcher;

    private Consul client;
    private SessionClient sessionClient;
    private KeyValueClient keyValueClient;
    private ScheduledExecutorService executorService;
    private String path;

    ConsulClusterView(ConsulClusterService service, ConsulClusterConfiguration configuration, String namespace) {
        super(service, namespace);

        this.configuration = configuration;
        this.localMember = new ConsulLocalMember();
        this.sessionId = new AtomicReference<>();
        this.watcher = new Watcher();
        this.path = configuration.getRootPath() + "/" + namespace;
    }

    @Override
    public Optional<CamelClusterMember> getLeader() {
        if (keyValueClient == null) {
            return Optional.empty();
        }

        return keyValueClient.getSession(path).map(ConsulClusterMember::new);
    }

    @Override
    public CamelClusterMember getLocalMember() {
        return this.localMember;
    }

    @Override
    public List<CamelClusterMember> getMembers() {
        if (sessionClient == null) {
            return Collections.emptyList();
        }

        return sessionClient.listSessions().stream().filter(i -> i.getName().orElse("").equals(getNamespace()))
                .map(ConsulClusterMember::new).collect(Collectors.toList());
    }

    @Override
    protected void doStart() throws Exception {
        if (sessionId.get() == null) {
            client = configuration.createConsulClient(getCamelContext());
            sessionClient = client.sessionClient();
            keyValueClient = client.keyValueClient();

            sessionId.set(createSession());
            LOGGER.debug("Acquired session with id '{}'", sessionId.get());

            // to watch again after a failed query. Created once the session exists, as a view that fails to start is
            // not stopped
            executorService = getCamelContext().getExecutorServiceManager().newSingleThreadScheduledExecutor(this,
                    "ConsulClusterView");
            try {
                boolean lock = acquireLock();
                LOGGER.debug("Acquire lock on path '{}' with id '{}' result '{}'", path, sessionId.get(), lock);

                localMember.setMaster(lock);
                watcher.watch();
            } catch (Exception e) {
                getCamelContext().getExecutorServiceManager().shutdownNow(executorService);
                executorService = null;
                throw e;
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (executorService != null) {
            getCamelContext().getExecutorServiceManager().shutdownNow(executorService);
            executorService = null;
        }
        if (sessionId.get() != null) {
            if (keyValueClient.releaseLock(this.path, sessionId.get())) {
                LOGGER.debug("Successfully released lock on path '{}' with id '{}'", path, sessionId.get());
            }
            sessionIdLock.lock();
            try {
                sessionClient.destroySession(sessionId.getAndSet(null));
                localMember.setMaster(false);
            } finally {
                sessionIdLock.unlock();
            }
        }
    }

    private String createSession() {
        return sessionClient
                .createSession(ImmutableSession.builder().name(getNamespace()).ttl(configuration.getSessionTtl() + "s")
                        .lockDelay(configuration.getSessionLockDelay() + "s").build())
                .getId();
    }

    private void renewSession(String sid) {
        try {
            if (sessionClient.renewSession(sid).isPresent()) {
                return;
            }
        } catch (ConsulException e) {
            if (!e.hasCode() || e.getCode() != 404) {
                // for example Consul cannot be reached: the session is renewed again with the next query
                LOGGER.debug("Failed to renew session with id '{}': {}", sid, e.getMessage(), e);
                return;
            }
        }

        // the session does not exist anymore: Consul invalidated it (its TTL expired, the health check of the agent
        // failed, Consul lost its data) and released the lock. Create a new session, or this node can never take the
        // leadership again
        sessionIdLock.lock();
        try {
            if ((isStarting() || isStarted()) && sid.equals(sessionId.get())) {
                localMember.setMaster(false);
                sessionId.set(createSession());
                LOGGER.info("Session with id '{}' was invalidated by Consul, created session with id '{}'", sid,
                        sessionId.get());
            }
        } catch (Exception e) {
            // tried again with the next query
            LOGGER.debug("Failed to create a session to replace session with id '{}': {}", sid, e.getMessage(), e);
        } finally {
            sessionIdLock.unlock();
        }
    }

    private CamelClusterMember currentLeader() {
        try {
            return getLeader().orElse(null);
        } catch (Exception e) {
            // for example Consul cannot be reached
            LOGGER.debug("Failed to get the leader on path '{}': {}", path, e.getMessage(), e);
            return null;
        }
    }

    private boolean acquireLock() {
        sessionIdLock.lock();
        try {
            String sid = sessionId.get();

            return (sid != null)
                    && sessionClient.getSessionInfo(sid).map(si -> keyValueClient.acquireLock(path, sid)).orElse(Boolean.FALSE);
        } finally {
            sessionIdLock.unlock();
        }
    }

    // ***********************************************
    //
    // ***********************************************

    private final class ConsulLocalMember implements CamelClusterMember {
        private final AtomicBoolean master = new AtomicBoolean();

        void setMaster(boolean master) {
            if (master && this.master.compareAndSet(false, true)) {
                LOGGER.debug("Leadership taken for session id {}", sessionId.get());
                fireLeadershipChangedEvent(this);
                return;
            }
            if (!master && this.master.compareAndSet(true, false)) {
                LOGGER.debug("Leadership lost for session id {}", sessionId.get());
                fireLeadershipChangedEvent(currentLeader());
            }
        }

        @Override
        public boolean isLeader() {
            return master.get();
        }

        @Override
        public boolean isLocal() {
            return true;
        }

        @Override
        public String getId() {
            return sessionId.get();
        }

        @Override
        public String toString() {
            return "ConsulLocalMember{" + "master=" + master + '}';
        }
    }

    private final class ConsulClusterMember implements CamelClusterMember {
        private final String id;

        ConsulClusterMember() {
            this.id = null;
        }

        ConsulClusterMember(SessionInfo info) {
            this(info.getId());
        }

        ConsulClusterMember(String id) {
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public boolean isLeader() {
            if (keyValueClient == null) {
                return false;
            }
            if (id == null) {
                return false;
            }

            return id.equals(keyValueClient.getSession(path).orElse(""));
        }

        @Override
        public boolean isLocal() {
            if (id == null) {
                return false;
            }

            return ObjectHelper.equal(id, localMember.getId());
        }

        @Override
        public String toString() {
            return "ConsulClusterMember{" + "id='" + id + '\'' + '}';
        }
    }

    // *************************************************************************
    // Watch
    // *************************************************************************

    private class Watcher implements ConsulResponseCallback<Optional<Value>> {
        private final AtomicReference<BigInteger> index;

        public Watcher() {
            this.index = new AtomicReference<>(new BigInteger("0"));
        }

        @Override
        public void onComplete(ConsulResponse<Optional<Value>> consulResponse) {
            if (isStarting() || isStarted()) {
                index.set(consulResponse.getIndex());
                try {
                    Optional<String> sid = consulResponse.getResponse().flatMap(Value::getSession);
                    if (!sid.isPresent()) {
                        // If the key is not held by any session (or does not exist, for
                        // example after Consul lost its data), try acquire a lock (become leader)
                        boolean lock = acquireLock();
                        LOGGER.debug("Try to acquire lock on path '{}' with id '{}', result '{}'", path, sessionId.get(), lock);

                        localMember.setMaster(lock);
                    } else {
                        boolean master = sid.get().equals(sessionId.get());
                        if (!master) {
                            LOGGER.debug("Path {} is held by session {}, local session is {}", path, sid.get(),
                                    sessionId.get());
                        }

                        localMember.setMaster(sid.get().equals(sessionId.get()));
                    }
                } catch (Exception e) {
                    // for example Consul cannot be reached anymore: the leadership cannot be confirmed
                    LOGGER.debug("Failed to update the leadership on path '{}': {}", path, e.getMessage(), e);
                    localMember.setMaster(false);
                }

                watch();
            }
        }

        @Override
        public void onFailure(Throwable throwable) {
            LOGGER.debug("{}", throwable.getMessage(), throwable);

            // the leadership cannot be confirmed: give it up locally, which can only lead to no leader, never to two.
            // The lock is kept: releasing it explicitly skips the lock-delay of Consul, so another node could take the
            // leadership while the clustered routes of this node are still stopping. If this node really is cut off
            // from Consul, its session expires and Consul releases the lock and applies the lock-delay
            localMember.setMaster(false);

            // keep watching, the leadership is taken again when Consul answers. Wait, so that a Consul agent that
            // cannot be reached is not queried in a loop
            ScheduledExecutorService executor = executorService;
            if ((isStarting() || isStarted()) && executor != null) {
                executor.schedule(this::watch, Math.max(1, configuration.getSessionRefreshInterval()), TimeUnit.SECONDS);
            }
        }

        public void watch() {
            String sid = sessionId.get();
            if (sid == null) {
                return;
            }

            if (isStarting() || isStarted()) {
                // Watch for changes
                keyValueClient.getValue(path,
                        QueryOptions.blockSeconds(configuration.getSessionRefreshInterval(), index.get()).build(), this);

                // Refresh session
                renewSession(sid);
            }
        }
    }
}
