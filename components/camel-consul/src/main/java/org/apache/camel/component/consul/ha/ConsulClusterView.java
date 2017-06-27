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
package org.apache.camel.component.consul.ha;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import com.orbitz.consul.Consul;
import com.orbitz.consul.KeyValueClient;
import com.orbitz.consul.SessionClient;
import com.orbitz.consul.async.ConsulResponseCallback;
import com.orbitz.consul.model.ConsulResponse;
import com.orbitz.consul.model.kv.Value;
import com.orbitz.consul.model.session.ImmutableSession;
import com.orbitz.consul.model.session.SessionInfo;
import com.orbitz.consul.option.QueryOptions;
import org.apache.camel.CamelContext;
import org.apache.camel.ha.CamelClusterMember;
import org.apache.camel.impl.ha.AbstractCamelClusterView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ConsulClusterView extends AbstractCamelClusterView {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConsulClusterService.class);

    private final ConsulClusterConfiguration configuration;
    private final ConsulLocalMember localMember;
    private final ConsulClusterMember nullMember;
    private final Watcher watcher;

    private Consul client;
    private SessionClient sessionClient;
    private KeyValueClient keyValueClient;
    private String sessionId;
    private String path;

    ConsulClusterView(ConsulClusterService service, ConsulClusterConfiguration configuration, String namespace) throws Exception {
        super(service, namespace);

        this.configuration = configuration;
        this.localMember = new ConsulLocalMember();
        this.nullMember = new ConsulClusterMember();
        this.watcher = new Watcher();
        this.path = configuration.getRootPath() + "/" + namespace;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        super.setCamelContext(camelContext);

        this.configuration.setCamelContext(camelContext);
    }

    @Override
    public Optional<CamelClusterMember> getMaster() {
        if (keyValueClient == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(
            keyValueClient.getSession(configuration.getRootPath())
                .transform(ConsulClusterMember::new)
                .orNull()
        );
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

        return sessionClient.listSessions().stream()
            .filter(i -> i.getName().equals(getNamespace()))
            .map(ConsulClusterMember::new)
            .collect(Collectors.toList());
    }

    @Override
    protected void doStart() throws Exception {
        if (sessionId == null) {
            client = configuration.createConsulClient();
            sessionClient = client.sessionClient();
            keyValueClient = client.keyValueClient();

            sessionId = sessionClient.createSession(
                ImmutableSession.builder()
                    .name(getNamespace())
                    .ttl(configuration.getSessionTtl() + "s")
                    .lockDelay(configuration.getSessionLockDelay() + "s")
                    .build()
            ).getId();

            LOGGER.debug("Acquired session with id '{}'", sessionId);
            boolean lock = keyValueClient.acquireLock(this.path, sessionId);
            LOGGER.debug("Acquire lock on path '{}' with id '{}' result '{}'", path, sessionId, lock);

            localMember.setMaster(lock);
            watcher.watch();
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (sessionId != null) {
            if (keyValueClient.releaseLock(this.path, sessionId)) {
                LOGGER.debug("Successfully released lock on path '{}' with id '{}'", path, sessionId);
            }

            sessionClient.destroySession(sessionId);
            localMember.setMaster(false);
        }
    }

    // ***********************************************
    //
    // ***********************************************

    private final class ConsulLocalMember implements CamelClusterMember {
        private AtomicBoolean master = new AtomicBoolean(false);

        void setMaster(boolean master) {
            if (master && this.master.compareAndSet(false, true)) {
                LOGGER.debug("Leadership taken for session id {}", sessionId);
                fireLeadershipChangedEvent(this);
                return;
            }
            if (!master && this.master.compareAndSet(true, false)) {
                LOGGER.debug("Leadership lost for session id {}", sessionId);
                fireLeadershipChangedEvent(getMaster().orElse(nullMember));
                return;
            }
        }

        @Override
        public boolean isMaster() {
            return master.get();
        }

        @Override
        public String getId() {
            return sessionId;
        }

        @Override
        public String toString() {
            return "ConsulLocalMember{"
                + "master=" + master
                + '}';
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
        public boolean isMaster() {
            if (keyValueClient == null) {
                return false;
            }
            if (id == null) {
                return false;
            }

            return id.equals(keyValueClient.getSession(path));
        }

        @Override
        public String toString() {
            return "ConsulClusterMember{"
                + "id='" + id + '\''
                + '}';
        }
    }

    // *************************************************************************
    // Watch
    // *************************************************************************

    private class Watcher implements ConsulResponseCallback<com.google.common.base.Optional<Value>> {
        private final AtomicReference<BigInteger> index;

        public Watcher() {
            this.index = new AtomicReference<>(new BigInteger("0"));
        }

        @Override
        public void onComplete(ConsulResponse<com.google.common.base.Optional<Value>> consulResponse) {
            if (isRunAllowed()) {
                com.google.common.base.Optional<Value> value = consulResponse.getResponse();
                if (value.isPresent()) {
                    com.google.common.base.Optional<String> sid = value.get().getSession();
                    if (!sid.isPresent()) {
                        // If the key is not held by any session, try acquire a
                        // lock (become leader)
                        boolean lock = keyValueClient.acquireLock(configuration.getRootPath(), sessionId);
                        LOGGER.debug("Try to acquire lock on path '{}' with id '{}', result '{}'", path, sessionId, lock);

                        localMember.setMaster(lock);
                    } else {
                        localMember.setMaster(sessionId.equals(sid.get()));
                    }
                }

                index.set(consulResponse.getIndex());
                watch();
            }
        }

        @Override
        public void onFailure(Throwable throwable) {
            LOGGER.debug("", throwable);

            if (sessionId != null) {
                keyValueClient.releaseLock(configuration.getRootPath(), sessionId);
            }

            localMember.setMaster(false);
            watch();
        }

        public void watch() {
            if (isRunAllowed()) {
                // Watch for changes
                keyValueClient.getValue(
                    path,
                    QueryOptions.blockSeconds(configuration.getSessionRefreshInterval(), index.get()).build(),
                    this
                );

                // Refresh session
                sessionClient.renewSession(sessionId);
            }
        }
    }
}
