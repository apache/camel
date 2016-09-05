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
package org.apache.camel.component.etcd.policy;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import mousio.client.promises.ResponsePromise;
import mousio.etcd4j.EtcdClient;
import mousio.etcd4j.responses.EtcdErrorCode;
import mousio.etcd4j.responses.EtcdException;
import mousio.etcd4j.responses.EtcdKeysResponse;
import org.apache.camel.NonManagedService;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.etcd.EtcdHelper;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EtcdRoutePolicy extends RoutePolicySupport implements ResponsePromise.IsSimplePromiseResponseHandler<EtcdKeysResponse>, NonManagedService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EtcdRoutePolicy.class);

    private final Object lock;
    private final EtcdClient client;
    private final boolean managedClient;
    private final AtomicBoolean leader;
    private final Set<Route> suspendedRoutes;
    private final AtomicLong index;

    private String serviceName;
    private String servicePath;
    private int ttl;
    private int watchTimeout;
    private boolean shouldStopConsumer;

    public EtcdRoutePolicy() {
        this(new EtcdClient(), true);
    }

    public EtcdRoutePolicy(EtcdClient client) {
        this(client, false);
    }

    public EtcdRoutePolicy(EtcdClient client, boolean managedClient) {
        this.client = client;
        this.managedClient = managedClient;
        this.suspendedRoutes =  new HashSet<>();
        this.leader = new AtomicBoolean(false);
        this.lock = new Object();
        this.index = new AtomicLong(0);
        this.serviceName = null;
        this.servicePath = null;
        this.ttl = 60;
        this.watchTimeout = ttl / 3;
        this.shouldStopConsumer = true;
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
        setLeader(tryTakeLeadership());
        watch();

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        if (managedClient) {
            client.close();
        }

        super.doStop();
    }

    // *************************************************************************
    //
    // *************************************************************************

    protected void setLeader(boolean isLeader) {
        if (isLeader && leader.compareAndSet(false, isLeader)) {
            LOGGER.info("Leadership taken (path={}, name={})", servicePath, serviceName);
            startAllStoppedConsumers();
        } else {
            if (!leader.getAndSet(isLeader) && isLeader) {
                LOGGER.info("Leadership lost (path={}, name={})", servicePath, serviceName);
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

    public EtcdClient getClient() {
        return client;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getServicePath() {
        return servicePath;
    }

    public void setServicePath(String servicePath) {
        this.servicePath = servicePath;
    }

    public int getTtl() {
        return ttl;
    }

    public void setTtl(int ttl) {
        this.ttl = ttl;
    }

    public int getWatchTimeout() {
        return watchTimeout;
    }

    public void setWatchTimeout(int watchTimeout) {
        this.watchTimeout = watchTimeout;
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

    @Override
    public void onResponse(ResponsePromise<EtcdKeysResponse> promise) {
        if (!isRunAllowed()) {
            return;
        }

        Throwable throwable = promise.getException();
        if (throwable != null && throwable instanceof EtcdException) {
            EtcdException exception = (EtcdException) throwable;
            if (EtcdHelper.isOutdatedIndexException(exception)) {
                LOGGER.debug("Outdated index, key={}, cause={}", servicePath, exception.etcdCause);
                index.set(exception.index + 1);
                throwable = null;
            }
        } else {
            try {
                EtcdKeysResponse response = promise.get();
                EtcdHelper.setIndex(index, response);

                if (response.node.value == null) {
                    setLeader(tryTakeLeadership());
                } else if (!ObjectHelper.equal(serviceName, response.node.value) && leader.get()) {
                    // Looks like I've lost leadership
                    setLeader(false);
                }
            } catch (TimeoutException e) {
                LOGGER.debug("Timeout watching for {}", servicePath);
                throwable = null;
            } catch (Exception e1) {
                throwable = e1;
            }
        }

        if (throwable == null) {
            watch();
        } else {
            throw new RuntimeCamelException(throwable);
        }
    }

    private void watch() {
        if (!isRunAllowed()) {
            return;
        }

        try {
            if (leader.get()) {
                EtcdHelper.setIndex(index, client.refresh(servicePath, ttl)
                    .send()
                    .get()
                );
            }

            LOGGER.debug("Watch (path={}, isLeader={}, index={})", servicePath, leader.get(), index.get());

            client.get(servicePath)
                .waitForChange(index.get())
                .timeout(ttl / 3, TimeUnit.SECONDS)
                .send()
                .addListener(this);
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }
    }

    private boolean tryTakeLeadership() throws Exception {
        boolean result = false;

        try {
            EtcdKeysResponse response = getClient()
                .put(servicePath, serviceName)
                .prevExist(false)
                .ttl(ttl)
                .send()
                .get();

            result = ObjectHelper.equal(serviceName, response.node.value);
            EtcdHelper.setIndex(index, response);
        } catch (EtcdException e) {
            if (!e.isErrorCode(EtcdErrorCode.NodeExist)) {
                throw e;
            }
        }

        return result;
    }
}