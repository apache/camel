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
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.etcd.EtcdConfiguration;
import org.apache.camel.component.etcd.EtcdConstants;
import org.apache.camel.component.etcd.EtcdHelper;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ManagedResource(description = "Route policy using Etcd as clustered lock")
public class EtcdRoutePolicy extends RoutePolicySupport implements ResponsePromise.IsSimplePromiseResponseHandler<EtcdKeysResponse>, CamelContextAware {
    private static final Logger LOGGER = LoggerFactory.getLogger(EtcdRoutePolicy.class);

    private final Object lock = new Object();
    private final AtomicBoolean leader = new AtomicBoolean(false);
    private final Set<Route> suspendedRoutes = new HashSet<>();
    private final AtomicLong index = new AtomicLong(0);

    private int ttl = 60;
    private int watchTimeout = 60 / 3;
    private boolean shouldStopConsumer = true;

    private Route route;
    private CamelContext camelContext;

    private String serviceName;
    private String servicePath;
    private EtcdClient client;
    private boolean managedClient;
    private String clientUris = EtcdConstants.ETCD_DEFAULT_URIS;

    public EtcdRoutePolicy() {
        this.client = null;
        this.managedClient = false;
    }

    public EtcdRoutePolicy(EtcdConfiguration configuration) throws Exception {
        this.client = configuration.createClient();
        this.managedClient = true;
    }

    public EtcdRoutePolicy(EtcdClient client) {
        this(client, false);
    }

    public EtcdRoutePolicy(EtcdClient client, boolean managedClient) {
        this.client = client;
        this.managedClient = managedClient;
    }

    public EtcdRoutePolicy(String clientUris) {
        this.clientUris = clientUris;
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
        ObjectHelper.notNull(camelContext, "camelContext");
        ObjectHelper.notNull(clientUris, "clientUris");

        if (client == null) {
            client = new EtcdClient(EtcdHelper.resolveURIs(camelContext, clientUris));
            managedClient = true;
        }

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

    public String getServiceName() {
        return serviceName;
    }

    @ManagedAttribute(description = "The etcd service name")
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    @ManagedAttribute(description = "The etcd service path")
    public String getServicePath() {
        return servicePath;
    }

    public void setServicePath(String servicePath) {
        this.servicePath = servicePath;
    }

    @ManagedAttribute(description = "The time to live (seconds)")
    public int getTtl() {
        return ttl;
    }

    public void setTtl(int ttl) {
        this.ttl = ttl;
    }

    @ManagedAttribute(description = "The watch timeout (seconds)")
    public int getWatchTimeout() {
        return watchTimeout;
    }

    public void setWatchTimeout(int watchTimeout) {
        this.watchTimeout = watchTimeout;
    }

    @ManagedAttribute(description = "Whether to stop consumer when starting up and failed to become master")
    public boolean isShouldStopConsumer() {
        return shouldStopConsumer;
    }

    public void setShouldStopConsumer(boolean shouldStopConsumer) {
        this.shouldStopConsumer = shouldStopConsumer;
    }

    @ManagedAttribute(description = "Is this route the master or a slave")
    public boolean isLeader() {
        return leader.get();
    }

    @ManagedAttribute(description = "Etcd endpoints")
    public String getClientUris() {
        return clientUris;
    }

    public void setClientUris(String clientUris) {
        this.clientUris = clientUris;
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
        if (throwable instanceof EtcdException) {
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
                .timeout(watchTimeout, TimeUnit.SECONDS)
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
