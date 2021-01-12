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
package org.apache.camel.component.infinispan.embedded;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import org.apache.camel.Service;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.infinispan.InfinispanRoutePolicy;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.infinispan.Cache;
import org.infinispan.notifications.Listener;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryExpired;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryRemoved;
import org.infinispan.notifications.cachelistener.event.CacheEntryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ManagedResource(description = "Route policy using Infinispan Embedded as clustered lock")
public class InfinispanEmbeddedRoutePolicy extends InfinispanRoutePolicy {
    private static final Logger LOGGER = LoggerFactory.getLogger(InfinispanEmbeddedRoutePolicy.class);

    private final InfinispanEmbeddedManager manager;

    public InfinispanEmbeddedRoutePolicy(InfinispanEmbeddedConfiguration configuration) {
        this(configuration, null, null);
    }

    public InfinispanEmbeddedRoutePolicy(InfinispanEmbeddedConfiguration configuration, String lockKey, String lockValue) {
        super(lockKey, lockValue);

        this.manager = new InfinispanEmbeddedManager(configuration);
    }

    @Override
    protected Service createService() {
        return new EmbeddedCacheService();
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

    @Listener(clustered = true, sync = false)
    private final class EmbeddedCacheService extends ServiceSupport {
        private Cache<String, String> cache;
        private ScheduledExecutorService executorService;
        private ScheduledFuture<?> future;

        EmbeddedCacheService() {
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void doStart() throws Exception {
            this.executorService = getCamelContext().getExecutorServiceManager().newSingleThreadScheduledExecutor(this,
                    getClass().getSimpleName());
            this.cache = manager.getCache(getLockMapName(), Cache.class);
            this.cache.addListener(this);
            this.future = executorService.scheduleAtFixedRate(this::run, 0, getLifespan() / 2, getLifespanTimeUnit());
        }

        @Override
        protected void doStop() throws Exception {
            if (cache != null) {
                this.cache.removeListener(this);
                this.cache.remove(getLockKey(), getLockValue());
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

            if (isLeader()) {
                // I'm still the leader, so refresh the key so it does not expire.
                if (!cache.replace(getLockKey(), getLockValue(), getLockValue(), getLifespan(), getLifespanTimeUnit())) {
                    // Looks like I've lost the leadership.
                    setLeader(false);
                }
            }

            if (!isLeader()) {
                Object result = cache.putIfAbsent(getLockKey(), getLockValue(), getLifespan(), getLifespanTimeUnit());
                if (result == null) {
                    // Acquired the key so I'm the leader.
                    setLeader(true);
                } else if (ObjectHelper.equal(getLockValue(), result) && !isLeader()) {
                    // Hey, I may have recovered from failure (or reboot was really
                    // fast) and my key was still there so yeah, I'm the leader again!
                    setLeader(true);
                } else {
                    setLeader(false);
                }
            }
        }

        @CacheEntryRemoved
        public void onCacheEntryRemoved(CacheEntryEvent<Object, Object> event) {
            LOGGER.debug("onCacheEntryExpired lock-key={}, event-key={}", getLockKey(), event.getKey());
            if (ObjectHelper.equal(getLockKey(), event.getKey())) {
                run();
            }
        }

        @CacheEntryExpired
        public void onCacheEntryExpired(CacheEntryEvent<Object, Object> event) {
            LOGGER.debug("onCacheEntryExpired lock-key={}, event-key={}", getLockKey(), event.getKey());
            if (ObjectHelper.equal(getLockKey(), event.getKey())) {
                run();
            }
        }
    }
}
