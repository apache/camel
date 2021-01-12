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
package org.apache.camel.component.infinispan;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.Service;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ReferenceCount;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class InfinispanRoutePolicy extends RoutePolicySupport implements CamelContextAware {
    private static final Logger LOGGER = LoggerFactory.getLogger(InfinispanRoutePolicy.class);

    private final AtomicBoolean leader;
    private final Set<Route> startedRoutes;
    private final Set<Route> stoppeddRoutes;
    private final ReferenceCount refCount;

    private CamelContext camelContext;
    private boolean shouldStopRoute;
    private String lockMapName;
    private String lockKey;
    private String lockValue;
    private long lifespan;
    private TimeUnit lifespanTimeUnit;
    private Service service;

    protected InfinispanRoutePolicy(String lockKey, String lockValue) {
        this.stoppeddRoutes = new HashSet<>();
        this.startedRoutes = new HashSet<>();
        this.leader = new AtomicBoolean();
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

    protected abstract Service createService();

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
            if (lifespanTimeUnit.convert(lifespan, TimeUnit.SECONDS) < 2) {
                throw new IllegalArgumentException("Lock lifespan can not be less that 2 seconds");
            }

            this.service = createService();
            this.service.start();
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }
    }

    private void stopService() {
        leader.set(false);

        try {
            if (this.service != null) {
                this.service.stop();
            }
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }
    }

    protected void setLeader(boolean isLeader) {
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
}
