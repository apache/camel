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
package org.apache.camel.component.hazelcast.policy;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.NonManagedService;
import org.apache.camel.Route;
import org.apache.camel.component.hazelcast.HazelcastUtil;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HazelcastRoutePolicy extends RoutePolicySupport implements CamelContextAware, NonManagedService {
    private static final Logger LOGGER = LoggerFactory.getLogger(HazelcastRoutePolicy.class);

    private final boolean managedInstance;
    private final AtomicBoolean leader;
    private final Set<Route> suspendedRoutes;

    private CamelContext camelContext;
    private ExecutorService executorService;
    private HazelcastInstance instance;
    private String lockMapName;
    private String lockKey;
    private String lockValue;
    private long tryLockTimeout;
    private TimeUnit tryLockTimeoutUnit;
    private IMap<String, String> locks;
    private volatile Future<Void> future;
    private boolean shouldStopConsumer;

    public HazelcastRoutePolicy() {
        this(HazelcastUtil.newInstance(), true);
    }

    public HazelcastRoutePolicy(HazelcastInstance instance) {
        this(instance, false);
    }

    public HazelcastRoutePolicy(HazelcastInstance instance, boolean managedInstance) {
        this.instance = instance;
        this.managedInstance = managedInstance;
        this.suspendedRoutes =  new HashSet<>();
        this.leader = new AtomicBoolean(false);
        this.lockMapName = null;
        this.lockKey = null;
        this.lockValue = null;
        this.tryLockTimeout = Long.MAX_VALUE;
        this.tryLockTimeoutUnit = TimeUnit.MILLISECONDS;
        this.locks = null;
        this.future = null;
        this.shouldStopConsumer = true;
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
    public void onStart(Route route) {
        if (!leader.get() && shouldStopConsumer) {
            stopConsumer(route);
        }
    }

    @Override
    public synchronized void onStop(Route route) {
        suspendedRoutes.remove(route);
    }

    @Override
    public synchronized void onSuspend(Route route) {
        suspendedRoutes.remove(route);
    }

    @Override
    protected void doStart() throws Exception {
        // validate
        StringHelper.notEmpty(lockMapName, "lockMapName", this);
        StringHelper.notEmpty(lockKey, "lockKey", this);
        StringHelper.notEmpty(lockValue, "lockValue", this);

        executorService = getCamelContext().getExecutorServiceManager().newSingleThreadExecutor(this, "HazelcastRoutePolicy");

        locks = instance.getMap(lockMapName);
        future = executorService.submit(this::acquireLeadership);

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        if (future != null) {
            future.cancel(true);
            future = null;
        }

        if (managedInstance) {
            instance.shutdown();
        }

        getCamelContext().getExecutorServiceManager().shutdownGraceful(executorService);

        super.doStop();
    }
    // *************************************************************************
    //
    // *************************************************************************

    protected void setLeader(boolean isLeader) {
        if (isLeader && leader.compareAndSet(false, isLeader)) {
            LOGGER.info("Leadership taken (map={}, key={}, val={})",
                lockMapName,
                lockKey,
                lockValue);

            startAllStoppedConsumers();
        } else {
            if (!leader.getAndSet(isLeader) && isLeader) {
                LOGGER.info("Leadership lost (map={}, key={} val={})",
                    lockMapName,
                    lockKey,
                    lockValue);
            }
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

    public String getLockMapName() {
        return lockMapName;
    }

    public void setLockMapName(String lockMapName) {
        this.lockMapName = lockMapName;
    }

    public boolean isShouldStopConsumer() {
        return shouldStopConsumer;
    }

    public void setShouldStopConsumer(boolean shouldStopConsumer) {
        this.shouldStopConsumer = shouldStopConsumer;
    }

    public String getLockKey() {
        return lockKey;
    }

    public void setLockKey(String lockKey) {
        this.lockKey = lockKey;
    }

    public String getLockValue() {
        return lockValue;
    }

    public void setLockValue(String lockValue) {
        this.lockValue = lockValue;
    }

    public long getTryLockTimeout() {
        return tryLockTimeout;
    }

    public void setTryLockTimeout(long tryLockTimeout) {
        this.tryLockTimeout = tryLockTimeout;
    }

    public void setTryLockTimeout(long tryLockTimeout, TimeUnit tryLockTimeoutUnit) {
        this.tryLockTimeout = tryLockTimeout;
        this.tryLockTimeoutUnit = tryLockTimeoutUnit;
    }

    public TimeUnit getTryLockTimeoutUnit() {
        return tryLockTimeoutUnit;
    }

    public void setTryLockTimeoutUnit(TimeUnit tryLockTimeoutUnit) {
        this.tryLockTimeoutUnit = tryLockTimeoutUnit;
    }

    public boolean isLeader() {
        return leader.get();
    }

    // *************************************************************************
    //
    // *************************************************************************

    private Void acquireLeadership() throws Exception {
        boolean locked = false;
        while (isRunAllowed()) {
            try {
                locked = locks.tryLock(lockKey, tryLockTimeout, tryLockTimeoutUnit);
                if (locked) {
                    locks.put(lockKey, lockValue);
                    setLeader(true);

                    // Wait almost forever
                    Thread.sleep(Long.MAX_VALUE);
                } else {
                    LOGGER.debug("Failed to acquire lock (map={}, key={}, val={}) after {} {}",
                        lockMapName,
                        lockKey,
                        lockValue,
                        tryLockTimeout,
                        tryLockTimeoutUnit.name()
                    );
                }
            } catch (InterruptedException e) {
                // ignore
            } catch (Exception e) {
                getExceptionHandler().handleException(e);
            } finally {
                if (locked) {
                    locks.remove(lockKey);
                    locks.unlock(lockKey);
                    locked = false;
                }

                setLeader(false);
            }
        }

        return null;
    }
}
