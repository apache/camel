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
package org.apache.camel.component.infinispan.policy;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.Service;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.infinispan.InfinispanConfiguration;
import org.apache.camel.component.infinispan.InfinispanManager;
import org.apache.camel.component.infinispan.InfinispanUtil;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ReferenceCount;
import org.apache.camel.util.StringHelper;
import org.infinispan.Cache;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.annotation.ClientCacheEntryExpired;
import org.infinispan.client.hotrod.annotation.ClientCacheEntryRemoved;
import org.infinispan.client.hotrod.annotation.ClientListener;
import org.infinispan.client.hotrod.event.ClientCacheEntryExpiredEvent;
import org.infinispan.client.hotrod.event.ClientCacheEntryRemovedEvent;
import org.infinispan.commons.api.BasicCache;
import org.infinispan.commons.api.BasicCacheContainer;
import org.infinispan.notifications.Listener;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryExpired;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryRemoved;
import org.infinispan.notifications.cachelistener.event.CacheEntryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ManagedResource(description = "Route policy using Infinispan as clustered lock")
public class InfinispanRoutePolicy extends RoutePolicySupport implements CamelContextAware {
    private static final Logger LOGGER = LoggerFactory.getLogger(InfinispanRoutePolicy.class);

    private final AtomicBoolean leader;
    private final Set<Route> startedRoutes;
    private final Set<Route> stoppeddRoutes;
    private final InfinispanManager manager;
    private final ReferenceCount refCount;

    private CamelContext camelContext;
    private ScheduledExecutorService executorService;
    private boolean shouldStopRoute;
    private String lockMapName;
    private String lockKey;
    private String lockValue;
    private long lifespan;
    private TimeUnit lifespanTimeUnit;
    private ScheduledFuture<?> future;
    private Service service;

    public InfinispanRoutePolicy(InfinispanConfiguration configuration) {
        this(new InfinispanManager(configuration), null, null);
    }

    public InfinispanRoutePolicy(InfinispanManager manager) {
        this(manager, null, null);
    }

    public InfinispanRoutePolicy(InfinispanManager manager, String lockKey, String lockValue) {
        this.manager = manager;
        this.stoppeddRoutes = new HashSet<>();
        this.startedRoutes = new HashSet<>();
        this.leader = new AtomicBoolean(false);
        this.shouldStopRoute = true;
        this.lockKey = lockKey;
        this.lockValue = lockValue;
        this.lifespan = 30;
        this.lifespanTimeUnit = TimeUnit.SECONDS;
        this.service = null;
        this.refCount = ReferenceCount.on(this::startService, this::stopService);
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
        route.setAutoStartup(false);

        stoppeddRoutes.add(route);

        this.refCount.retain();

        startManagedRoutes();
    }

    @Override
    public synchronized void doShutdown() {
        this.refCount.release();
    }

    // ****************************************
    // Helpers
    // ****************************************

    private void startService() {
        // validate
        StringHelper.notEmpty(lockMapName, "lockMapName", this);
        StringHelper.notEmpty(lockKey, "lockKey", this);
        StringHelper.notEmpty(lockValue, "lockValue", this);
        ObjectHelper.notNull(camelContext, "camelContext", this);

        try {
            this.manager.start();
            this.executorService = getCamelContext().getExecutorServiceManager().newSingleThreadScheduledExecutor(this, "InfinispanRoutePolicy");

            if (lifespanTimeUnit.convert(lifespan, TimeUnit.SECONDS) < 2) {
                throw new IllegalArgumentException("Lock lifespan can not be less that 2 seconds");
            }

            if (manager.isCacheContainerEmbedded()) {
                BasicCache<String, String> cache = manager.getCache(lockMapName);
                this.service = new EmbeddedCacheService(InfinispanUtil.asEmbedded(cache));
            } else {
                // By default, previously existing values for java.util.Map operations
                // are not returned for remote caches but policy needs it so force it.
                BasicCache<String, String> cache = manager.getCache(lockMapName, true);
                this.service = new RemoteCacheService(InfinispanUtil.asRemote(cache));
            }

            service.start();
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }
    }

    private void stopService() {
        leader.set(false);

        try {
            if (future != null) {
                future.cancel(true);
                future = null;
            }

            manager.stop();

            if (this.service != null) {
                this.service.stop();
            }

            getCamelContext().getExecutorServiceManager().shutdownGraceful(executorService);
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }
    }

    private void setLeader(boolean isLeader) {
        if (isLeader && leader.compareAndSet(false, isLeader)) {
            LOGGER.info("Leadership taken (map={}, key={}, val={})", lockMapName, lockKey, lockValue);
            startManagedRoutes();
        } else if (!isLeader && leader.getAndSet(isLeader)) {
            LOGGER.info("Leadership lost (map={}, key={} val={})", lockMapName, lockKey, lockValue);
            stopManagedRoutes();
        }
    }

    private synchronized void startManagedRoutes() {
        if (!isLeader()) {
            return;
        }

        try {
            for (Route route : stoppeddRoutes) {
                LOGGER.debug("Starting route {}", route.getId());
                startRoute(route);
                startedRoutes.add(route);
            }

            stoppeddRoutes.removeAll(startedRoutes);
        } catch (Exception e) {
            handleException(e);
        }
    }

    private synchronized void stopManagedRoutes() {
        if (isLeader()) {
            return;
        }

        try {
            for (Route route : startedRoutes) {
                LOGGER.debug("Stopping route {}", route.getId());
                stopRoute(route);
                stoppeddRoutes.add(route);
            }

            startedRoutes.removeAll(stoppeddRoutes);
        } catch (Exception e) {
            handleException(e);
        }
    }

    // *************************************************************************
    // Getter/Setters
    // *************************************************************************

    @ManagedAttribute(description = "Whether to stop route when starting up and failed to become master")
    public boolean isShouldStopRoute() {
        return shouldStopRoute;
    }

    public void setShouldStopRoute(boolean shouldStopRoute) {
        this.shouldStopRoute = shouldStopRoute;
    }

    @ManagedAttribute(description = "The lock map name")
    public String getLockMapName() {
        return lockMapName;
    }

    public void setLockMapName(String lockMapName) {
        this.lockMapName = lockMapName;
    }

    @ManagedAttribute(description = "The lock key")
    public String getLockKey() {
        return lockKey;
    }

    public void setLockKey(String lockKey) {
        this.lockKey = lockKey;
    }

    @ManagedAttribute(description = "The lock value")
    public String getLockValue() {
        return lockValue;
    }

    public void setLockValue(String lockValue) {
        this.lockValue = lockValue;
    }


    @ManagedAttribute(description = "The key lifespan for the lock")
    public long getLifespan() {
        return lifespan;
    }

    public void setLifespan(long lifespan) {
        this.lifespan = lifespan;
    }

    public void setLifespan(long lifespan, TimeUnit lifespanTimeUnit) {
        this.lifespan = lifespan;
        this.lifespanTimeUnit = lifespanTimeUnit;
    }

    @ManagedAttribute(description = "The key lifespan time unit for the lock")
    public TimeUnit getLifespanTimeUnit() {
        return lifespanTimeUnit;
    }

    public void setLifespanTimeUnit(TimeUnit lifespanTimeUnit) {
        this.lifespanTimeUnit = lifespanTimeUnit;
    }

    @ManagedAttribute(description = "Is this route the master or a slave")
    public boolean isLeader() {
        return leader.get();
    }

    // *************************************************************************
    //
    // *************************************************************************

    @Listener(clustered = true, sync = false)
    private final class EmbeddedCacheService extends ServiceSupport implements Runnable {
        private Cache<String, String> cache;
        private ScheduledFuture<?> future;

        EmbeddedCacheService(Cache<String, String> cache) {
            this.cache = cache;
            this.future = null;
        }

        @Override
        protected void doStart() throws Exception {
            this.future = executorService.scheduleAtFixedRate(this::run, 0, lifespan / 2, lifespanTimeUnit);
            this.cache.addListener(this);
        }

        @Override
        protected void doStop() throws Exception {
            this.cache.removeListener(this);
            this.cache.remove(lockKey, lockValue);

            if (future != null) {
                future.cancel(true);
                future = null;
            }
        }

        @Override
        public void run() {
            if (!isRunAllowed()) {
                return;
            }

            if (isLeader()) {
                // I'm still the leader, so refresh the key so it does not expire.
                if (!cache.replace(lockKey, lockValue, lockValue, lifespan, lifespanTimeUnit)) {
                    // Looks like I've lost the leadership.
                    setLeader(false);
                }
            }

            if (!isLeader()) {
                Object result = cache.putIfAbsent(lockKey, lockValue, lifespan, lifespanTimeUnit);
                if (result == null) {
                    // Acquired the key so I'm the leader.
                    setLeader(true);
                } else if (ObjectHelper.equal(lockValue, result) && !isLeader()) {
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
            if (ObjectHelper.equal(lockKey, event.getKey())) {
                run();
            }
        }
        @CacheEntryExpired
        public void onCacheEntryExpired(CacheEntryEvent<Object, Object> event) {
            if (ObjectHelper.equal(lockKey, event.getKey())) {
                run();
            }
        }
    }

    @ClientListener
    private final class RemoteCacheService extends ServiceSupport  implements Runnable {
        private RemoteCache<String, String> cache;
        private ScheduledFuture<?> future;
        private Long version;

        RemoteCacheService(RemoteCache<String, String> cache) {
            this.cache = cache;
            this.future = null;
            this.version = null;
        }

        @Override
        protected void doStart() throws Exception {
            this.future = executorService.scheduleAtFixedRate(this::run, 0, lifespan / 2, lifespanTimeUnit);
            this.cache.addClientListener(this);
        }

        @Override
        protected void doStop() throws Exception {
            this.cache.removeClientListener(this);

            if (this.version != null) {
                this.cache.removeWithVersion(lockKey, this.version);
            }

            if (future != null) {
                future.cancel(true);
                future = null;
            }
        }

        @Override
        public void run() {
            if (!isRunAllowed()) {
                return;
            }

            if (isLeader() && version != null) {
                LOGGER.debug("Lock refresh key={} with version={}", lockKey, version);

                // I'm still the leader, so refresh the key so it does not expire.
                if (!cache.replaceWithVersion(lockKey, lockValue, version, (int)lifespanTimeUnit.toSeconds(lifespan))) {
                    setLeader(false);
                } else {
                    version = cache.getWithMetadata(lockKey).getVersion();
                    LOGGER.debug("Lock refreshed key={} with new version={}", lockKey, version);
                }
            }

            if (!isLeader()) {
                Object result = cache.putIfAbsent(lockKey, lockValue, lifespan, lifespanTimeUnit);
                if (result == null) {
                    // Acquired the key so I'm the leader.
                    setLeader(true);

                    // Get the version
                    version = cache.getWithMetadata(lockKey).getVersion();

                    LOGGER.debug("Lock acquired key={} with version={}", lockKey, version);
                } else if (ObjectHelper.equal(lockValue, result) && !isLeader()) {
                    // Hey, I may have recovered from failure (or reboot was really
                    // fast) and my key was still there so yeah, I'm the leader again!
                    setLeader(true);

                    // Get the version
                    version = cache.getWithMetadata(lockKey).getVersion();

                    LOGGER.debug("Lock resumed key={} with version={}", lockKey, version);
                } else {
                    setLeader(false);
                }
            }
        }

        @ClientCacheEntryRemoved
        public void onCacheEntryRemoved(ClientCacheEntryRemovedEvent<String> event) {
            if (ObjectHelper.equal(lockKey, event.getKey())) {
                run();
            }
        }

        @ClientCacheEntryExpired
        public void onCacheEntryExpired(ClientCacheEntryExpiredEvent<String> event) {
            if (ObjectHelper.equal(lockKey, event.getKey())) {
                run();
            }
        }
    }

    // *************************************************************************
    // Helpers
    // *************************************************************************

    public static InfinispanRoutePolicy withManager(BasicCacheContainer cacheContainer) {
        InfinispanConfiguration conf = new InfinispanConfiguration();
        conf.setCacheContainer(cacheContainer);

        return new InfinispanRoutePolicy(conf);
    }
}
