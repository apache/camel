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
package org.apache.camel.component.infinispan.remote;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import org.apache.camel.Service;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.infinispan.InfinispanRoutePolicy;
import org.apache.camel.component.infinispan.InfinispanUtil;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.infinispan.client.hotrod.Flag;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.annotation.ClientCacheEntryExpired;
import org.infinispan.client.hotrod.annotation.ClientCacheEntryRemoved;
import org.infinispan.client.hotrod.annotation.ClientListener;
import org.infinispan.client.hotrod.event.ClientCacheEntryExpiredEvent;
import org.infinispan.client.hotrod.event.ClientCacheEntryRemovedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ManagedResource(description = "Route policy using Infinispan Remote as clustered lock")
public class InfinispanRemoteRoutePolicy extends InfinispanRoutePolicy {
    private static final Logger LOGGER = LoggerFactory.getLogger(InfinispanRemoteRoutePolicy.class);

    private final InfinispanRemoteManager manager;

    public InfinispanRemoteRoutePolicy(InfinispanRemoteConfiguration configuration) {
        this(configuration, null, null);
    }

    public InfinispanRemoteRoutePolicy(InfinispanRemoteConfiguration configuration, String lockKey, String lockValue) {
        super(lockKey, lockValue);

        this.manager = new InfinispanRemoteManager(configuration);
    }

    @Override
    protected Service createService() {
        return new RemoteCacheService();
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();
        this.manager.start();
    }

    @Override
    public void doStop() throws Exception {
        super.doStop();
        this.manager.stop();
    }

    // *************************************************************************
    //
    // *************************************************************************

    @ClientListener
    private final class RemoteCacheService extends ServiceSupport {
        private final int lifespan;

        private RemoteCache<String, String> cache;
        private ScheduledExecutorService executorService;
        private ScheduledFuture<?> future;
        private Long version;

        RemoteCacheService() {
            this.lifespan = (int) getLifespanTimeUnit().toSeconds(getLifespan());
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void doStart() throws Exception {
            this.executorService = InfinispanUtil.newSingleThreadScheduledExecutor(getCamelContext(), this);
            this.cache = manager.getCache(getLockMapName(), RemoteCache.class);
            this.cache.addClientListener(this);
            this.future = executorService.scheduleAtFixedRate(this::run, 0, getLifespan() / 2, getLifespanTimeUnit());
        }

        @Override
        protected void doStop() throws Exception {
            if (cache != null) {
                this.cache.removeClientListener(this);

                if (this.version != null) {
                    this.cache.removeWithVersion(getLockKey(), this.version);
                }
            }

            if (future != null) {
                future.cancel(true);
                future = null;
            }

            getCamelContext().getExecutorServiceManager().shutdownGraceful(executorService);
        }

        private void run() {
            if (!isRunAllowed()) {
                return;
            }

            if (isLeader() && version != null) {
                LOGGER.debug("Lock refresh key={} with version={}", getLockKey(), version);

                // I'm still the leader, so refresh the key so it does not expire.
                if (!cache.replaceWithVersion(getLockKey(), getLockValue(), version, lifespan)) {
                    setLeader(false);
                } else {
                    version = cache.getWithMetadata(getLockKey()).getVersion();
                    LOGGER.debug("Lock refreshed key={} with new version={}", getLockKey(), version);
                }
            }

            if (!isLeader()) {
                Object result = cache.withFlags(Flag.FORCE_RETURN_VALUE).putIfAbsent(getLockKey(), getLockValue(),
                        getLifespan(), getLifespanTimeUnit());
                if (result == null) {
                    // Acquired the key so I'm the leader.
                    setLeader(true);

                    // Get the version
                    version = cache.getWithMetadata(getLockKey()).getVersion();

                    LOGGER.debug("Lock acquired key={} with version={}", getLockKey(), version);
                } else if (ObjectHelper.equal(getLockValue(), result) && !isLeader()) {
                    // Hey, I may have recovered from failure (or reboot was really
                    // fast) and my key was still there so yeah, I'm the leader again!
                    setLeader(true);

                    // Get the version
                    version = cache.getWithMetadata(getLockKey()).getVersion();

                    LOGGER.debug("Lock resumed key={} with version={}", getLockKey(), version);
                } else {
                    setLeader(false);
                }
            }
        }

        @ClientCacheEntryRemoved
        public void onCacheEntryRemoved(ClientCacheEntryRemovedEvent<String> event) {
            LOGGER.debug("onCacheEntryRemoved lock-key={}, event-key={}", getLockKey(), event.getKey());
            if (ObjectHelper.equal(getLockKey(), event.getKey())) {
                run();
            }
        }

        @ClientCacheEntryExpired
        public void onCacheEntryExpired(ClientCacheEntryExpiredEvent<String> event) {
            LOGGER.debug("onCacheEntryExpired lock-key={}, event-key={}", getLockKey(), event.getKey());
            if (ObjectHelper.equal(getLockKey(), event.getKey())) {
                run();
            }
        }
    }
}
