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
package org.apache.camel.component.consul.policy;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.google.common.base.Optional;
import com.orbitz.consul.Consul;
import com.orbitz.consul.KeyValueClient;
import com.orbitz.consul.SessionClient;
import com.orbitz.consul.async.ConsulResponseCallback;
import com.orbitz.consul.model.ConsulResponse;
import com.orbitz.consul.model.kv.Value;
import com.orbitz.consul.model.session.ImmutableSession;
import com.orbitz.consul.option.QueryOptions;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.NonManagedService;
import org.apache.camel.Route;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConsulRoutePolicy extends RoutePolicySupport implements NonManagedService, CamelContextAware {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConsulRoutePolicy.class);

    private final Object lock;
    private final Consul consul;
    private final SessionClient sessionClient;
    private final KeyValueClient keyValueClient;
    private final AtomicBoolean leader;
    private final Set<Route> suspendedRoutes;
    private final AtomicReference<BigInteger> index;

    private CamelContext camelContext;
    private String serviceName;
    private String servicePath;
    private int ttl;
    private int lockDelay;
    private ExecutorService executorService;
    private boolean shouldStopConsumer;

    private String sessionId;

    public ConsulRoutePolicy() {
        this(Consul.builder().build());
    }

    public ConsulRoutePolicy(Consul consul) {
        this.consul = consul;
        this.sessionClient = consul.sessionClient();
        this.keyValueClient = consul.keyValueClient();
        this.suspendedRoutes =  new HashSet<>();
        this.leader = new AtomicBoolean(false);
        this.lock = new Object();
        this.index = new AtomicReference<>(BigInteger.valueOf(0));
        this.serviceName = null;
        this.servicePath = null;
        this.ttl = 60;
        this.lockDelay = 10;
        this.executorService = null;
        this.shouldStopConsumer = true;
        this.sessionId = null;
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
    public void onStart(Route route)  {
        if (!leader.get() && shouldStopConsumer) {
            stopConsumer(route);
        }
    }

    @Override
    public void onStop(Route route) {
        synchronized (lock) {
            suspendedRoutes.remove(route);
        }
    }

    @Override
    public synchronized void onSuspend(Route route) {
        synchronized (lock) {
            suspendedRoutes.remove(route);
        }
    }

    @Override
    protected void doStart() throws Exception {
        if (sessionId == null) {
            sessionId = sessionClient.createSession(
                ImmutableSession.builder()
                    .name(serviceName)
                    .ttl(ttl + "s")
                    .lockDelay(lockDelay + "s")
                    .build()
                ).getId();

            LOGGER.debug("SessionID = {}", sessionId);
            if (executorService == null) {
                executorService = getCamelContext().getExecutorServiceManager().newSingleThreadExecutor(this, "HazelcastRoutePolicy");
            }

            setLeader(keyValueClient.acquireLock(servicePath, sessionId));

            executorService.submit(new Watcher());
        }

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (sessionId != null) {
            sessionClient.destroySession(sessionId);
            sessionId = null;

            if (executorService != null) {
                getCamelContext().getExecutorServiceManager().shutdownGraceful(executorService);
            }
        }
    }

    // *************************************************************************
    //
    // *************************************************************************

    protected void setLeader(boolean isLeader) {
        if (isLeader && leader.compareAndSet(false, isLeader)) {
            LOGGER.debug("Leadership taken ({}, {})", serviceName, sessionId);
            startAllStoppedConsumers();
        } else {
            if (!leader.getAndSet(isLeader) && isLeader) {
                LOGGER.debug("Leadership lost ({}, {})", serviceName, sessionId);
            }
        }
    }

    private void startConsumer(Route route) {
        synchronized (lock) {
            try {
                if (suspendedRoutes.contains(route)) {
                    startConsumer(route.getConsumer());
                    suspendedRoutes.remove(route);
                }
            } catch (Exception e) {
                handleException(e);
            }
        }
    }

    private void stopConsumer(Route route) {
        synchronized (lock) {
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
    }

    private void startAllStoppedConsumers() {
        synchronized (lock) {
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
    }

    // *************************************************************************
    // Getter/Setters
    // *************************************************************************

    public Consul getConsul() {
        return consul;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
        this.servicePath = String.format("/service/%s/leader", serviceName);
    }

    public int getTtl() {
        return ttl;
    }

    public void setTtl(int ttl) {
        this.ttl = ttl > 10 ? ttl : 10;
    }

    public int getLockDelay() {
        return lockDelay;
    }

    public void setLockDelay(int lockDelay) {
        this.lockDelay = lockDelay > 10 ? lockDelay : 10;
    }

    public boolean isShouldStopConsumer() {
        return shouldStopConsumer;
    }

    public void setShouldStopConsumer(boolean shouldStopConsumer) {
        this.shouldStopConsumer = shouldStopConsumer;
    }

    // *************************************************************************
    // Watch
    // *************************************************************************

    private class Watcher implements Runnable, ConsulResponseCallback<Optional<Value>> {

        @Override
        public void onComplete(ConsulResponse<Optional<Value>> consulResponse) {
            if (isRunAllowed()) {
                Value response = consulResponse.getResponse().orNull();
                if (response != null) {
                    String sid = response.getSession().orNull();
                    if (ObjectHelper.isEmpty(sid)) {
                        // If the key is not held by any session, try acquire a
                        // lock (become leader)
                        LOGGER.debug("Try to take leadership ...");
                        setLeader(keyValueClient.acquireLock(servicePath, sessionId));
                    } else if (!sessionId.equals(sid) && leader.get()) {
                        // Looks like I've lost leadership
                        setLeader(false);
                    }
                }

                index.set(consulResponse.getIndex());
                run();
            }
        }

        @Override
        public void onFailure(Throwable throwable) {
            handleException(throwable);
        }

        @Override
        public void run() {
            if (isRunAllowed()) {
                // Refresh session
                sessionClient.renewSession(sessionId);

                keyValueClient.getValue(
                    servicePath,
                    QueryOptions.blockSeconds(ttl / 3, index.get()).build(),
                    this);
            }
        }
    }
}
