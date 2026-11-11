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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Route;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.hazelcast.HazelcastUtil;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ManagedResource(description = "Route policy using Hazelcast as clustered lock")
public class HazelcastRoutePolicy extends RoutePolicySupport implements CamelContextAware {
    private static final Logger LOGGER = LoggerFactory.getLogger(HazelcastRoutePolicy.class);
    // delay before the leader tries again to start a consumer whose start failed
    private static final long RETRY_DELAY_MILLIS = 5000;

    private final boolean managedInstance;
    private final AtomicBoolean leader;
    private final Set<Route> suspendedRoutes;
    private final Set<Route> routes;
    private final Set<Route> suspendedWhileStopped;
    // failed consumer starts in a row per route, for the log
    private final Map<Route, Integer> failedStarts = new HashMap<>();
    // guards the leader flag, the route sets and the leadership thread, so a leadership taken while a route starts,
    // stops or resumes is not missed. Not the lifecycle lock of the service: stop() holds that one while it waits for
    // the leadership thread
    private final Lock leadershipLock = new ReentrantLock();
    // the leadership threads told to end that may still wait for or release the lock (guarded by leadershipLock)
    private final List<Future<Void>> releasedFutures = new ArrayList<>();

    // a route of the policy, for JMX
    private volatile Route route;
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
    // counted down to make the leadership thread release the lock and end (set under leadershipLock, also read
    // without it)
    private volatile CountDownLatch leadershipRelease;
    // the latch of the leadership task that runs (and may hold the cluster lock)
    private volatile CountDownLatch runningRelease;
    private volatile long retryDelay = RETRY_DELAY_MILLIS;
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

        // the lifecycle lock, so the start of the policy is not in progress
        lock.lock();
        leadershipLock.lock();
        try {
            if (routes.add(route)) {
                if (this.route == null) {
                    this.route = route;
                }
                // a route added to a policy that gave up the leadership when its routes were removed
                if (leadershipRelease == null && isStarted()) {
                    startLeadership();
                }
            }
        } finally {
            leadershipLock.unlock();
            lock.unlock();
        }
    }

    @Override
    public void onRemove(Route route) {
        if (camelContext != null && camelContext.isStopping()) {
            // all the routes go: end the leadership first, so a consumer start in flight does not delay the stop
            interruptLeadership();
        }
        leadershipLock.lock();
        try {
            suspendedRoutes.remove(route);
            suspendedWhileStopped.remove(route);
            failedStarts.remove(route);
            if (routes.remove(route)) {
                if (this.route == route) {
                    this.route = routes.isEmpty() ? null : routes.iterator().next();
                }
                if (routes.isEmpty()) {
                    // no route uses this policy any more: give up the leadership, so another node runs the route. The
                    // thread is only interrupted when the CamelContext stops (as the stop of the policy does)
                    stopLeadership(camelContext != null && camelContext.isStopping());
                }
            }
        } finally {
            leadershipLock.unlock();
        }
    }

    @Override
    public void onStart(Route route) {
        // the flag is read under the lock that setLeader holds while it sets it and starts the stopped consumers
        leadershipLock.lock();
        try {
            if (!leader.get() && shouldStopConsumer) {
                stopConsumer(route);
            } else if (leader.get() && ServiceHelper.isStopped(route.getConsumer())) {
                // a start by the leadership thread that overlapped the start of the route stopped this consumer again
                // (the route status was not started yet): the leadership thread starts it
                suspendedRoutes.add(route);
            }
        } finally {
            leadershipLock.unlock();
        }
    }

    @Override
    public void onStop(Route route) {
        leadershipLock.lock();
        try {
            suspendedRoutes.remove(route);
            suspendedWhileStopped.remove(route);
            failedStarts.remove(route);
        } finally {
            leadershipLock.unlock();
        }
    }

    @Override
    public void onSuspend(Route route) {
        leadershipLock.lock();
        try {
            if (suspendedRoutes.remove(route)) {
                suspendedWhileStopped.add(route);
            }
        } finally {
            leadershipLock.unlock();
        }
    }

    @Override
    public void onResume(Route route) {
        leadershipLock.lock();
        try {
            boolean stoppedByPolicy = suspendedWhileStopped.remove(route);
            if (!leader.get() && shouldStopConsumer) {
                // resuming the route resumed its consumer, which a follower keeps stopped (as when the route starts)
                stopConsumer(route);
            } else if (stoppedByPolicy) {
                // the leadership was taken while the route was suspended: resuming a consumer that this policy had
                // stopped does not start it again (a SEDA consumer is marked started without consuming), so restart it
                LOGGER.debug("Restarting consumer for {} ({})", route.getId(), route.getConsumer());
                // the leadership thread tries again if the start fails
                suspendedRoutes.add(route);
                stopConsumer(route.getConsumer());
                startConsumer(route.getConsumer());
                suspendedRoutes.remove(route);
            }
        } catch (Exception e) {
            handleException(e);
        } finally {
            leadershipLock.unlock();
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
        leadershipLock.lock();
        try {
            if (!routes.isEmpty()) {
                startLeadership();
            }
        } finally {
            leadershipLock.unlock();
        }

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        // also interrupt the threads first, so a consumer start in flight, or a wait for the lock on a Hazelcast client
        // (on a member tryLock ends after its timeout), does not delay the stop
        interruptLeadership();
        leadershipLock.lock();
        try {
            stopLeadership(true);
        } finally {
            leadershipLock.unlock();
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

    // under the leadership lock
    private void startLeadership() {
        CountDownLatch release = new CountDownLatch(1);
        leadershipRelease = release;
        future = executorService.submit(() -> acquireLeadership(release));
    }

    // without the leadership lock: tells the leadership thread to end and interrupts it (when stopping)
    private void interruptLeadership() {
        CountDownLatch release = leadershipRelease;
        if (release != null) {
            release.countDown();
        }
        Future<Void> task = future;
        if (task != null) {
            task.cancel(true);
        }
    }

    // under the leadership lock: the leadership thread releases the lock and ends, and this node is no longer the
    // leader. The thread is told to end with its own latch, as isRunAllowed() stays true while the policy runs, and is
    // not interrupted unless the policy stops: an interrupted thread cannot release the lock on a client instance, and
    // a wait for the lock ends by itself after the tryLock timeout
    private void stopLeadership(boolean interrupt) {
        if (leadershipRelease != null) {
            leadershipRelease.countDown();
            leadershipRelease = null;
        }
        if (future != null) {
            // a thread that waits for the lock ends after the tryLock timeout, or (on a Hazelcast client) when it is
            // interrupted
            releasedFutures.add(future);
            future = null;
        }
        releasedFutures.removeIf(Future::isDone);
        if (interrupt) {
            releasedFutures.forEach(f -> f.cancel(true));
            releasedFutures.clear();
        }
        setLeader(false);
    }

    protected void setLeader(boolean isLeader) {
        if (isLeader) {
            List<Route> toStart;
            leadershipLock.lock();
            try {
                toStart = markLeader();
            } finally {
                leadershipLock.unlock();
            }
            startStoppedConsumers(toStart);
        } else {
            leadershipLock.lock();
            try {
                if (leader.compareAndSet(true, false)) {
                    LOGGER.info("Leadership lost (map={}, key={}, val={})",
                            lockMapName,
                            lockKey,
                            lockValue);
                }
            } finally {
                leadershipLock.unlock();
            }
        }
    }

    // under the leadership lock: sets the leader flag, and returns the routes whose consumer this policy stopped. Not
    // when the routes were removed meanwhile (stopLeadership), so the leadership is not taken after that
    private List<Route> markLeader() {
        // the latch of the running task, not only the current one: a task that ended its leadership (its routes were
        // removed) must not take it for a route added meanwhile, as it releases the cluster lock
        CountDownLatch release = leadershipRelease;
        if (release == null || release.getCount() == 0 || release != runningRelease
                || !leader.compareAndSet(false, true)) {
            return List.of();
        }
        LOGGER.info("Leadership taken (map={}, key={}, val={})",
                lockMapName,
                lockKey,
                lockValue);
        return new ArrayList<>(suspendedRoutes);
    }

    // under the leadership lock
    private void stopConsumer(Route route) {
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

    // Starts the consumers of the routes this policy stopped, without the leadership lock: a consumer start can take
    // long (a broker connect), and the route callbacks and the stop must not wait for it. Each start is checked again
    // under the lock: a route is forgotten only once its consumer started (so the leader tries again a start that
    // failed), and a consumer started for a route that is stopping or suspending meanwhile is stopped again.
    private void startStoppedConsumers(List<Route> toStart) {
        for (Route route : toStart) {
            if (!isStartWanted(route)) {
                continue;
            }
            Exception cause = null;
            try {
                LOGGER.debug("Starting consumer for {} ({})", route.getId(), route.getConsumer());
                startConsumer(route.getConsumer());
            } catch (Exception e) {
                cause = e;
            }
            afterStart(route, cause);
        }
    }

    private boolean isStartWanted(Route route) {
        leadershipLock.lock();
        try {
            return isRunAllowed() && leader.get() && suspendedRoutes.contains(route) && isResumeOrStartConsumerAllowed(route);
        } finally {
            leadershipLock.unlock();
        }
    }

    private void afterStart(Route route, Exception cause) {
        leadershipLock.lock();
        try {
            boolean wanted = isRunAllowed() && leader.get() && isResumeOrStartConsumerAllowed(route);
            if (cause != null) {
                if (!wanted) {
                    LOGGER.debug("Failed to start consumer for {} while it is no longer wanted", route.getId(), cause);
                    return;
                }
                int attempt = failedStarts.merge(route, 1, Integer::sum);
                getExceptionHandler().handleException("Failed to start consumer for route " + route.getId()
                                                      + " (attempt #" + attempt + "). Trying again in " + retryDelay
                                                      + " millis.",
                        cause);
            } else if (!wanted) {
                // the route is stopping or suspending (or this node is stopping): its consumer must not run
                LOGGER.debug("Stopping consumer for {} again, as the route is {}", route.getId(),
                        route.getCamelContext().getRouteController().getRouteStatus(route.getId()));
                stopConsumer(route.getConsumer());
            } else if (suspendedRoutes.remove(route)) {
                Integer failed = failedStarts.remove(route);
                if (failed != null) {
                    LOGGER.info("Consumer for route {} started after {} failed attempts", route.getId(), failed);
                }
            }
        } catch (Exception e) {
            handleException(e);
        } finally {
            leadershipLock.unlock();
        }
    }

    // the leader starts the consumers whose start failed
    private void retryStoppedConsumers() {
        List<Route> toStart;
        leadershipLock.lock();
        try {
            toStart = leader.get() ? new ArrayList<>(suspendedRoutes) : List.of();
        } finally {
            leadershipLock.unlock();
        }
        startStoppedConsumers(toStart);
    }

    /**
     * The delay before the leader tries again to start a consumer whose start failed (5 seconds). For tests.
     */
    void setRetryDelay(long retryDelay) {
        this.retryDelay = retryDelay;
    }

    // *************************************************************************
    // Getter/Setters
    // *************************************************************************

    @ManagedAttribute(description = "The route id")
    public String getRouteId() {
        Route r = route;
        if (r != null) {
            return r.getId();
        }
        return null;
    }

    @ManagedAttribute(description = "The consumer endpoint", mask = true)
    public String getEndpointUrl() {
        Route r = route;
        if (r != null && r.getConsumer() != null && r.getConsumer().getEndpoint() != null) {
            return r.getConsumer().getEndpoint().toString();
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
        runningRelease = release;
        boolean locked = false;
        while (isRunAllowed() && release.getCount() > 0) {
            try {
                locked = locks.tryLock(lockKey, tryLockTimeout, tryLockTimeoutUnit);
                if (locked && release.getCount() > 0) {
                    locks.put(lockKey, lockValue);
                    if (takeLeadership(release)) {
                        // hold the lock until the policy stops or its routes are removed, and meanwhile try again to
                        // start the consumers whose start failed
                        while (!release.await(retryDelay, TimeUnit.MILLISECONDS)) {
                            retryStoppedConsumers();
                        }
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
        if (release.getCount() == 0) {
            return false;
        }
        setLeader(true);
        return true;
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
