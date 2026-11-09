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
package org.apache.camel.component.hazelcast.policy;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Route;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.hazelcast.HazelcastUtil;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ManagedResource(description = "Route policy using Hazelcast as clustered lock")
public class HazelcastRoutePolicy extends RoutePolicySupport implements CamelContextAware {
    private static final Logger LOGGER = LoggerFactory.getLogger(HazelcastRoutePolicy.class);

    private final boolean managedInstance;
    private final AtomicBoolean leader;
    private final Set<Route> suspendedRoutes;
    private final Set<Route> routes;
    private final Set<Route> suspendedWhileStopped;

    private Route route;
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
    // counted down to make the leadership thread release the lock and end (guarded by lock)
    private CountDownLatch leadershipRelease;
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
        this.suspendedRoutes = new HashSet<>();
        this.routes = new HashSet<>();
        this.suspendedWhileStopped = new HashSet<>();
        this.leader = new AtomicBoolean();
        this.lockMapName = null;
        this.lockKey = null;
        this.lockValue = null;
        this.tryLockTimeout = 10 * 1000;
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
    public void onInit(Route route) {
        super.onInit(route);
        this.route = route;

        lock.lock();
        try {
            routes.add(route);
            // a route added to a policy that gave up the leadership when its routes were removed
            if (leadershipRelease == null && isStarted()) {
                startLeadership();
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onRemove(Route route) {
        lock.lock();
        try {
            suspendedRoutes.remove(route);
            suspendedWhileStopped.remove(route);
            if (routes.remove(route) && routes.isEmpty()) {
                // no route uses this policy any more: give up the leadership, so another node runs the route. The
                // thread is only interrupted when the CamelContext stops (as the stop of the policy does)
                stopLeadership(camelContext != null && camelContext.isStopping());
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onStart(Route route) {
        if (!leader.get() && shouldStopConsumer) {
            stopConsumer(route);
        }
    }

    @Override
    public void onStop(Route route) {
        lock.lock();
        try {
            suspendedRoutes.remove(route);
            suspendedWhileStopped.remove(route);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onSuspend(Route route) {
        lock.lock();
        try {
            if (suspendedRoutes.remove(route)) {
                suspendedWhileStopped.add(route);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onResume(Route route) {
        lock.lock();
        try {
            boolean stoppedByPolicy = suspendedWhileStopped.remove(route);
            if (!leader.get() && shouldStopConsumer) {
                // resuming the route resumed its consumer, which a follower keeps stopped (as when the route starts)
                stopConsumer(route);
            } else if (stoppedByPolicy) {
                // the leadership was taken while the route was suspended: resuming a consumer that this policy had
                // stopped does not start it again (a SEDA consumer is marked started without consuming), so restart it
                LOGGER.debug("Restarting consumer for {} ({})", route.getId(), route.getConsumer());
                stopConsumer(route.getConsumer());
                startConsumer(route.getConsumer());
            }
        } catch (Exception e) {
            handleException(e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    protected void doStart() throws Exception {
        // validate
        StringHelper.notEmpty(lockMapName, "lockMapName", this);
        StringHelper.notEmpty(lockKey, "lockKey", this);
        StringHelper.notEmpty(lockValue, "lockValue", this);

        executorService = getCamelContext().getExecutorServiceManager().newSingleThreadExecutor(this, "HazelcastRoutePolicy");

        locks = instance.getMap(lockMapName);
        if (!routes.isEmpty()) {
            startLeadership();
        }

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        // also interrupt the thread, so a wait for the lock does not delay the stop
        stopLeadership(true);

        if (managedInstance) {
            instance.shutdown();
        }

        getCamelContext().getExecutorServiceManager().shutdownGraceful(executorService);

        super.doStop();
    }
    // *************************************************************************
    //
    // *************************************************************************

    // under the lock
    private void startLeadership() {
        CountDownLatch release = new CountDownLatch(1);
        leadershipRelease = release;
        future = executorService.submit(() -> acquireLeadership(release));
    }

    // under the lock: the leadership thread releases the lock and ends, and this node is no longer the leader. The
    // thread is told to end with its own latch, as isRunAllowed() stays true while the policy runs, and is not
    // interrupted unless the policy stops: an interrupted thread cannot release the lock on a client instance, and a
    // wait for the lock ends by itself after the tryLock timeout
    private void stopLeadership(boolean interrupt) {
        if (leadershipRelease != null) {
            leadershipRelease.countDown();
            leadershipRelease = null;
        }
        if (future != null) {
            if (interrupt) {
                future.cancel(true);
            }
            future = null;
        }
        setLeader(false);
    }

    protected void setLeader(boolean isLeader) {
        if (isLeader && leader.compareAndSet(false, isLeader)) {
            LOGGER.info("Leadership taken (map={}, key={}, val={})",
                    lockMapName,
                    lockKey,
                    lockValue);

            startAllStoppedConsumers();
        } else if (!isLeader && leader.getAndSet(false)) {
            LOGGER.info("Leadership lost (map={}, key={}, val={})",
                    lockMapName,
                    lockKey,
                    lockValue);
        }
    }

    private void stopConsumer(Route route) {
        lock.lock();
        try {
            if (!suspendedRoutes.contains(route)) {
                LOGGER.debug("Stopping consumer for {} ({})", route.getId(), route.getConsumer());
                stopConsumer(route.getConsumer());
                suspendedRoutes.add(route);
            }
        } catch (Exception e) {
            handleException(e);
        } finally {
            lock.unlock();
        }
    }

    private void startAllStoppedConsumers() {
        lock.lock();
        try {
            for (Route route : suspendedRoutes) {
                LOGGER.debug("Starting consumer for {} ({})", route.getId(), route.getConsumer());
                startConsumer(route.getConsumer());
            }

            suspendedRoutes.clear();
        } catch (Exception e) {
            handleException(e);
        } finally {
            lock.unlock();
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

    @ManagedAttribute(description = "The lock map name")
    public String getLockMapName() {
        return lockMapName;
    }

    public void setLockMapName(String lockMapName) {
        this.lockMapName = lockMapName;
    }

    @ManagedAttribute(description = "Whether to stop consumer when starting up and failed to become master")
    public boolean isShouldStopConsumer() {
        return shouldStopConsumer;
    }

    public void setShouldStopConsumer(boolean shouldStopConsumer) {
        this.shouldStopConsumer = shouldStopConsumer;
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

    @ManagedAttribute(description = "Timeout used by slaves to try to obtain the lock to become new master")
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

    @ManagedAttribute(description = "Timeout unit")
    public TimeUnit getTryLockTimeoutUnit() {
        return tryLockTimeoutUnit;
    }

    public void setTryLockTimeoutUnit(TimeUnit tryLockTimeoutUnit) {
        this.tryLockTimeoutUnit = tryLockTimeoutUnit;
    }

    @ManagedAttribute(description = "Is this route the master or a slave")
    public boolean isLeader() {
        return leader.get();
    }

    // *************************************************************************
    //
    // *************************************************************************

    private Void acquireLeadership(CountDownLatch release) {
        boolean locked = false;
        while (isRunAllowed() && release.getCount() > 0) {
            try {
                locked = locks.tryLock(lockKey, tryLockTimeout, tryLockTimeoutUnit);
                if (locked && release.getCount() > 0) {
                    locks.put(lockKey, lockValue);
                    if (takeLeadership(release)) {
                        // hold the lock until the policy stops or its routes are removed
                        release.await();
                    }
                } else if (!locked) {
                    LOGGER.debug("Failed to acquire lock (map={}, key={}, val={}) after {} {}",
                            lockMapName,
                            lockKey,
                            lockValue,
                            tryLockTimeout,
                            tryLockTimeoutUnit.name());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                getExceptionHandler().handleException(e);
            } finally {
                if (locked) {
                    releaseLock();
                    locked = false;
                }

                setLeader(false);
            }
        }

        return null;
    }

    private boolean takeLeadership(CountDownLatch release) {
        // under the lock, so the leadership is not taken after the routes were removed (stopLeadership)
        lock.lock();
        try {
            if (release.getCount() == 0) {
                return false;
            }
            setLeader(true);
            return true;
        } finally {
            lock.unlock();
        }
    }

    private void releaseLock() {
        // with the interrupt flag cleared: on a client instance an interrupted thread fails to remove and unlock
        boolean interrupted = Thread.interrupted();
        try {
            try {
                locks.remove(lockKey);
            } finally {
                locks.unlock(lockKey);
            }
        } catch (Exception e) {
            getExceptionHandler().handleException(e);
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
