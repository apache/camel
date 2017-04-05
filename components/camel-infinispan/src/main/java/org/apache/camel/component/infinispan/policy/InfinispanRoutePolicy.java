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
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
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
    private final Set<Route> suspendedRoutes;
    private final InfinispanManager manager;

    private Route route;
    private CamelContext camelContext;
    private ScheduledExecutorService executorService;
    private boolean shouldStopConsumer;
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
        this.suspendedRoutes =  new HashSet<>();
        this.leader = new AtomicBoolean(false);
        this.shouldStopConsumer = true;
        this.lockKey = lockKey;
        this.lockValue = lockValue;
        this.lifespan = 30;
        this.lifespanTimeUnit = TimeUnit.SECONDS;
        this.service = null;
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
    public void onInit(Route route) {
        super.onInit(route);
        this.route = route;
    }

    @Override
    public void onStart(Route route) {
        try {
            startService();
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }

        if (!leader.get() && shouldStopConsumer) {
            stopConsumer(route);
        }
    }

    @Override
    public synchronized void onStop(Route route) {
        try {
            stopService();
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }

        suspendedRoutes.remove(route);
    }

    @Override
    public synchronized void onSuspend(Route route) {
        try {
            stopService();
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }

        suspendedRoutes.remove(route);
    }

    @Override
    protected void doStart() throws Exception {
        // validate
        StringHelper.notEmpty(lockMapName, "lockMapName", this);
        StringHelper.notEmpty(lockKey, "lockKey", this);
        StringHelper.notEmpty(lockValue, "lockValue", this);
        ObjectHelper.notNull(camelContext, "camelContext", this);

        if (this.lockValue == null) {
            this.lockValue = camelContext.getUuidGenerator().generateUuid();
        }

        this.manager.start();
        this.executorService = getCamelContext().getExecutorServiceManager().newSingleThreadScheduledExecutor(this, "InfinispanRoutePolicy");

        if (lifespanTimeUnit.convert(lifespan, TimeUnit.SECONDS) < 2) {
            throw new IllegalArgumentException("Lock lifespan can not be less that 2 seconds");
        }

        BasicCache<String, String> cache = manager.getCache(lockMapName);
        if (manager.isCacheContainerEmbedded()) {
            this.service = new EmbeddedCacheService(InfinispanUtil.asEmbedded(cache));
        } else {
            this.service = new RemoteCacheService(InfinispanUtil.asRemote(cache));
        }

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        if (future != null) {
            future.cancel(true);
            future = null;
        }

        if (this.service != null) {
            this.service.stop();
        }

        getCamelContext().getExecutorServiceManager().shutdownGraceful(executorService);

        leader.set(false);
        manager.stop();

        super.doStop();
    }

    private void startService() throws Exception {
        if (service == null) {
            throw new IllegalStateException("An Infinispan CacheService should be configured");
        }

        service.start();
    }

    private void stopService() throws Exception {
        leader.set(false);

        if (this.service != null) {
            this.service.stop();
        }
    }

    // *************************************************************************
    //
    // *************************************************************************

    protected void setLeader(boolean isLeader) {
        if (isLeader && leader.compareAndSet(false, isLeader)) {
            LOGGER.info("Leadership taken (map={}, key={}, val={})", lockMapName, lockKey, lockValue);

            startAllStoppedConsumers();
        } else if (!isLeader && leader.getAndSet(isLeader)) {
            LOGGER.info("Leadership lost (map={}, key={} val={})", lockMapName, lockKey, lockValue);
        }

        if (!isLeader && this.route != null) {
            stopConsumer(route);
        }
    }

    private synchronized void startConsumer(Route route) {
        try {
            if (suspendedRoutes.contains(route)) {
                startConsumer(route.getConsumer());
                suspendedRoutes.remove(route);
            }
        } catch (Exception e) {
            handleException(e);
        }
    }

    private synchronized void stopConsumer(Route route) {
        try {
            if (!suspendedRoutes.contains(route)) {
                LOGGER.debug("Stopping consumer for {} ({})", route.getId(), route.getConsumer());
                stopConsumer(route.getConsumer());
                suspendedRoutes.add(route);
            }
        } catch (Exception e) {
            handleException(e);
        }
    }

    private synchronized void startAllStoppedConsumers() {
        try {
            for (Route route : suspendedRoutes) {
                LOGGER.debug("Starting consumer for {} ({})", route.getId(), route.getConsumer());
                startConsumer(route.getConsumer());
            }

            suspendedRoutes.clear();
        } catch (Exception e) {
            handleException(e);
        }
    }

    // *************************************************************************
    // Getter/Setters
    // *************************************************************************

    @ManagedAttribute(description = "The route id")
    public String getRouteId() {
        if (route != null) {
            return route.getId();
        }
        return null;
    }

    @ManagedAttribute(description = "The consumer endpoint", mask = true)
    public String getEndpointUrl() {
        if (route != null && route.getConsumer() != null && route.getConsumer().getEndpoint() != null) {
            return route.getConsumer().getEndpoint().toString();
        }
        return null;
    }

    @ManagedAttribute(description = "Whether to stop consumer when starting up and failed to become master")
    public boolean isShouldStopConsumer() {
        return shouldStopConsumer;
    }

    public void setShouldStopConsumer(boolean shouldStopConsumer) {
        this.shouldStopConsumer = shouldStopConsumer;
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

        public EmbeddedCacheService(Cache<String, String> cache) {
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
            if (!isRunAllowed() || !InfinispanRoutePolicy.this.isRunAllowed()) {
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

        public RemoteCacheService(RemoteCache<String, String> cache) {
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
            if (!isRunAllowed() || !InfinispanRoutePolicy.this.isRunAllowed()) {
                return;
            }

            if (isLeader() && version != null) {
                LOGGER.debug("Lock refresh key={} with version={}", lockKey, version);

                // I'm still the leader, so refresh the key so it does not expire.
                if (!cache.replaceWithVersion(lockKey, lockValue, version, (int)lifespanTimeUnit.toSeconds(lifespan))) {
                    // Looks like I've lost the leadership.
                    setLeader(false);
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
        public void onCacheEntryRemoved(ClientCacheEntryRemovedEvent<Object> event) {
            if (ObjectHelper.equal(lockKey, event.getKey())) {
                run();
            }
        }

        @ClientCacheEntryExpired
        public void onCacheEntryExpired(ClientCacheEntryExpiredEvent<Object> event) {
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
