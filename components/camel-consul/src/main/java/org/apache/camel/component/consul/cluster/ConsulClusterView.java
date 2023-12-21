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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.apache.camel.cluster.CamelClusterMember;
import org.apache.camel.support.cluster.AbstractCamelClusterView;
import org.apache.camel.util.ObjectHelper;
import org.kiwiproject.consul.Consul;
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
    private final AtomicReference<String> sessionId;
    private final Watcher watcher;

    private Consul client;
    private SessionClient sessionClient;
    private KeyValueClient keyValueClient;
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

            sessionId.set(sessionClient
                    .createSession(ImmutableSession.builder().name(getNamespace()).ttl(configuration.getSessionTtl() + "s")
                            .lockDelay(configuration.getSessionLockDelay() + "s").build())
                    .getId());

            LOGGER.debug("Acquired session with id '{}'", sessionId.get());
            boolean lock = acquireLock();
            LOGGER.debug("Acquire lock on path '{}' with id '{}' result '{}'", path, sessionId.get(), lock);

            localMember.setMaster(lock);
            watcher.watch();
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (sessionId.get() != null) {
            if (keyValueClient.releaseLock(this.path, sessionId.get())) {
                LOGGER.debug("Successfully released lock on path '{}' with id '{}'", path, sessionId.get());
            }

            synchronized (sessionId) {
                sessionClient.destroySession(sessionId.getAndSet(null));
                localMember.setMaster(false);
            }
        }
    }

    private boolean acquireLock() {
        synchronized (sessionId) {
            String sid = sessionId.get();

            return (sid != null)
                    ? sessionClient.getSessionInfo(sid).map(si -> keyValueClient.acquireLock(path, sid)).orElse(Boolean.FALSE)
                    : false;
        }
    }

    // ***********************************************
    //
    // ***********************************************

    private final class ConsulLocalMember implements CamelClusterMember {
        private AtomicBoolean master = new AtomicBoolean();

        void setMaster(boolean master) {
            if (master && this.master.compareAndSet(false, true)) {
                LOGGER.debug("Leadership taken for session id {}", sessionId.get());
                fireLeadershipChangedEvent(Optional.of(this));
                return;
            }
            if (!master && this.master.compareAndSet(true, false)) {
                LOGGER.debug("Leadership lost for session id {}", sessionId.get());
                fireLeadershipChangedEvent(getLeader());
                return;
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
                Optional<Value> value = consulResponse.getResponse();
                if (value.isPresent()) {
                    Optional<String> sid = value.get().getSession();
                    if (!sid.isPresent()) {
                        // If the key is not held by any session, try acquire a
                        // lock (become leader)
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
                }

                index.set(consulResponse.getIndex());
                watch();
            }
        }

        @Override
        public void onFailure(Throwable throwable) {
            LOGGER.debug("{}", throwable.getMessage(), throwable);

            if (sessionId.get() != null) {
                keyValueClient.releaseLock(configuration.getRootPath(), sessionId.get());
            }

            localMember.setMaster(false);
            watch();
        }

        public void watch() {
            if (sessionId.get() == null) {
                return;
            }

            if (isStarting() || isStarted()) {
                // Watch for changes
                keyValueClient.getValue(path,
                        QueryOptions.blockSeconds(configuration.getSessionRefreshInterval(), index.get()).build(), this);

                if (sessionId.get() != null) {
                    // Refresh session
                    sessionClient.renewSession(sessionId.get());
                }
            }
        }
    }
}
