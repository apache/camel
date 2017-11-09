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
import org.apache.camel.Route;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.consul.ConsulConfiguration;
import org.apache.camel.component.consul.ConsulConstants;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @deprecated use {@link org.apache.camel.component.consul.cluster.ConsulClusterService} and {@link org.apache.camel.impl.cluster.ClusteredRoutePolicy} instead.
 */
@Deprecated
@ManagedResource(description = "Route policy using Consul as clustered lock")
public final class ConsulRoutePolicy extends RoutePolicySupport implements CamelContextAware {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConsulRoutePolicy.class);

    private final Object lock = new Object();
    private final AtomicBoolean leader = new AtomicBoolean(false);
    private final Set<Route> suspendedRoutes = new HashSet<>();
    private final AtomicReference<BigInteger> index = new AtomicReference<>(BigInteger.valueOf(0));

    private Route route;
    private CamelContext camelContext;
    private String serviceName;
    private String servicePath;
    private ExecutorService executorService;

    private int ttl = 60;
    private int lockDelay = 10;
    private boolean shouldStopConsumer = true;
    private String consulUrl = ConsulConstants.CONSUL_DEFAULT_URL;

    private Consul consul;
    private SessionClient sessionClient;
    private KeyValueClient keyValueClient;

    private String sessionId;

    public ConsulRoutePolicy() {
    }

    public ConsulRoutePolicy(String consulUrl) {
        this.consulUrl = consulUrl;
    }

    public ConsulRoutePolicy(ConsulConfiguration configuration) throws Exception {
        this.consulUrl = configuration.getUrl();
        this.consul = configuration.createConsulClient(camelContext);
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public String getConsulUrl() {
        return consulUrl;
    }

    public void setConsulUrl(String consulUrl) {
        this.consulUrl = consulUrl;
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
        ObjectHelper.notNull(serviceName, "serviceName");
        ObjectHelper.notNull(servicePath, "servicePath");

        if (consul == null) {
            Consul.Builder builder = Consul.builder();
            if (consulUrl != null) {
                builder.withUrl(consulUrl);
            }

            consul = builder.build();
        }

        if (sessionClient == null) {
            sessionClient = consul.sessionClient();
        }
        if (keyValueClient == null) {
            keyValueClient = consul.keyValueClient();
        }

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
                executorService = getCamelContext().getExecutorServiceManager().newSingleThreadExecutor(this, "ConsulRoutePolicy");
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
        }

        if (executorService != null) {
            getCamelContext().getExecutorServiceManager().shutdownGraceful(executorService);
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

    public Consul getConsul() {
        return consul;
    }

    @ManagedAttribute(description = "The consul service name")
    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
        this.servicePath = String.format("/service/%s/leader", serviceName);
    }

    @ManagedAttribute(description = "The time to live (seconds)")
    public int getTtl() {
        return ttl;
    }

    public void setTtl(int ttl) {
        this.ttl = ttl > 10 ? ttl : 10;
    }

    @ManagedAttribute(description = "The lock delay (seconds)")
    public int getLockDelay() {
        return lockDelay;
    }

    public void setLockDelay(int lockDelay) {
        this.lockDelay = lockDelay > 10 ? lockDelay : 10;
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

    // *************************************************************************
    // Watch
    // *************************************************************************

    private class Watcher implements Runnable, ConsulResponseCallback<Optional<Value>> {

        @Override
        public void onComplete(ConsulResponse<Optional<Value>> consulResponse) {
            if (isRunAllowed()) {
                Optional<Value> value = consulResponse.getResponse();
                if (value.isPresent()) {
                    Optional<String> sid = value.get().getSession();
                    if (sid.isPresent() && ObjectHelper.isNotEmpty(sid.get())) {
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
                    this
                );
            }
        }
    }
}
