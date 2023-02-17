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
package org.apache.camel.component.etcd3.policy;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.Lease;
import io.etcd.jetcd.Txn;
import io.etcd.jetcd.common.exception.ErrorCode;
import io.etcd.jetcd.common.exception.EtcdException;
import io.etcd.jetcd.kv.TxnResponse;
import io.etcd.jetcd.lease.LeaseKeepAliveResponse;
import io.etcd.jetcd.op.Cmp;
import io.etcd.jetcd.op.CmpTarget;
import io.etcd.jetcd.op.Op;
import io.etcd.jetcd.options.PutOption;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Route;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.etcd3.Etcd3Configuration;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.etcd3.Etcd3Constants.ETCD_DEFAULT_ENDPOINTS;

/**
 * An implementation of a route policy based on etcd.
 */
@ManagedResource(description = "Route policy using Etcd as clustered lock")
public class Etcd3RoutePolicy extends RoutePolicySupport implements CamelContextAware {

    /**
     * The logger
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(Etcd3RoutePolicy.class);
    /**
     * The mutex used to prevent concurrent access to {@code suspendedRoutes}.
     */
    private final Object mutex = new Object();
    /**
     * The flag indicating whether the current node is a leader.
     */
    private final AtomicBoolean leader = new AtomicBoolean();
    /**
     * The routes that have been suspended.
     */
    private final Set<Route> suspendedRoutes = new HashSet<>();
    /**
     * The time to live in seconds of a key-value pair inserted into etcd. Default value is {@code 60}.
     */
    private int ttl = 60;
    /**
     * The timeout in seconds of all requests. Default value is {@code 10}.
     */
    private int timeout = 10;
    /**
     * The route to which the policy is applied.
     */
    private volatile Route route;
    /**
     * The etcd service name.
     */
    private String serviceName;
    /**
     * The etcd service path.
     */
    private String servicePath;
    /**
     * The camel context associated to the policy.
     */
    private CamelContext camelContext;
    /**
     * The etcd endpoints.
     */
    private String[] endpoints;
    /**
     * The scheduler used to evaluate regularly the leadership.
     */
    private volatile ScheduledExecutorService executorService;
    /**
     * The flag indicating whether the consumer should be stopped.
     */
    private final AtomicBoolean shouldStopConsumer = new AtomicBoolean(true);
    /**
     * The id of the current lease. Only set if the current node is the leader.
     */
    private final AtomicLong leaseId = new AtomicLong();
    /**
     * The client to access to etcd.
     */
    private final AtomicReference<Client> client = new AtomicReference<>();
    /**
     * The client to access to the key-value pairs stored into etcd.
     */
    private final AtomicReference<KV> kv = new AtomicReference<>();
    /**
     * The client to access to the leases stored into etcd.
     */
    private final AtomicReference<Lease> lease = new AtomicReference<>();
    /**
     * The flag indicating whether the client has been created by the policy or outside the policy.
     */
    private final boolean managedClient;

    public Etcd3RoutePolicy() {
        this(ETCD_DEFAULT_ENDPOINTS);
    }

    public Etcd3RoutePolicy(Etcd3Configuration configuration) {
        this(configuration.createClient(), true);
    }

    public Etcd3RoutePolicy(Client client) {
        this(client, false);
    }

    private Etcd3RoutePolicy(Client client, boolean managedClient) {
        this.client.set(ObjectHelper.notNull(client, "client"));
        this.managedClient = managedClient;
    }

    public Etcd3RoutePolicy(String... endpoints) {
        this.endpoints = endpoints;
        this.managedClient = true;
    }

    @Override
    public void onInit(Route route) {
        super.onInit(route);
        this.route = route;
        if (executorService == null) {
            executorService = ObjectHelper.notNull(camelContext, "camelContext", this)
                    .getExecutorServiceManager().newSingleThreadScheduledExecutor(this,
                            "Etcd3RoutePolicy[" + route.getRouteId() + "]");
        }
    }

    @Override
    public void onStart(Route route) {
        if (!leader.get() && shouldStopConsumer.get()) {
            stopConsumer(route);
        }
    }

    @Override
    public void onStop(Route route) {
        synchronized (mutex) {
            suspendedRoutes.remove(route);
        }
    }

    @Override
    public synchronized void onSuspend(Route route) {
        synchronized (mutex) {
            suspendedRoutes.remove(route);
        }
    }

    @Override
    protected void doStart() throws Exception {
        Client c = client.get();
        if (c == null) {
            c = Client.builder().endpoints(ObjectHelper.notNull(endpoints, "endpoints")).build();
            this.client.set(c);
        }
        lease.set(c.getLeaseClient());
        kv.set(c.getKVClient());
        evaluateLeadershipAndSchedule();
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        if (executorService != null) {
            camelContext.getExecutorServiceManager().shutdownNow(executorService);
            executorService = null;
        }
        try {
            Client c = client.get();
            if (managedClient && c != null) {
                c.close();
            }
        } finally {
            super.doStop();
        }
    }

    // *************************************************************************
    //
    // *************************************************************************
    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    /**
     * Sets the latest leadership state of the current node and potentially triggers actions if the state has changed.
     *
     * @param isLeader {@code true} if the current node is the leader, {@code false} otherwise.
     */
    protected void setLeader(boolean isLeader) {
        if (isLeader) {
            if (leader.compareAndSet(false, true)) {
                LOGGER.info("Leadership taken (path={}, name={})", servicePath, serviceName);
                startAllStoppedConsumers();
            }
        } else if (leader.compareAndSet(true, false)) {
            LOGGER.info("Leadership lost (path={}, name={})", servicePath, serviceName);
        }
    }

    /**
     * Stops the consumer of the given route.
     *
     * @param route the route for which the consumer should be stopped.
     */
    private void stopConsumer(Route route) {
        synchronized (mutex) {
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

    /**
     * Start all the consumers that have been stopped.
     */
    private void startAllStoppedConsumers() {
        synchronized (mutex) {
            try {
                for (Route suspendedRoute : suspendedRoutes) {
                    LOGGER.debug("Starting consumer for {} ({})", suspendedRoute.getId(), suspendedRoute.getConsumer());
                    startConsumer(suspendedRoute.getConsumer());
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
    public Client getClient() {
        return client.get();
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

    @ManagedAttribute(description = "The request timeout (seconds)")
    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    @ManagedAttribute(description = "Whether to stop consumer when starting up and failed to become master")
    public boolean isShouldStopConsumer() {
        return shouldStopConsumer.get();
    }

    public void setShouldStopConsumer(boolean shouldStopConsumer) {
        this.shouldStopConsumer.set(shouldStopConsumer);
    }

    @ManagedAttribute(description = "Is this route the master or a slave")
    public boolean isLeader() {
        return leader.get();
    }

    @ManagedAttribute(description = "Etcd endpoints")
    public String getEndpoints() {
        return endpoints == null ? "" : String.join(",", endpoints);
    }

    public void setEndpoints(String[] endpoints) {
        this.endpoints = endpoints;
    }

    public void setEndpoints(String endpoints) {
        this.endpoints = endpoints.split(",");
    }

    long getLeaseId() {
        return leaseId.get();
    }

    /**
     * Evaluates the leadership and schedule the next evaluation.
     */
    private void evaluateLeadershipAndSchedule() {
        evaluateLeadership();
        executorService.schedule(this::evaluateLeadershipAndSchedule, Math.max(2 * ttl / 3, 1), TimeUnit.SECONDS);
    }

    /**
     * In case the current node is the leader, it tries to renew the lease if it failed or the current node is a not
     * leader, it tries to take the leadership.
     */
    private void evaluateLeadership() {
        if (isLeader() && renewLease()) {
            // The lease could be renewed successfully, so the status of the node did not change
            return;
        }
        setLeader(tryTakeLeadership());
    }

    /**
     * Renew the lease using a KeepAlive request to avoid losing the leadership.
     *
     * @return {@code true} if the lease could be renewed within the timeout, {@code false} otherwise.
     */
    private boolean renewLease() {
        long id = leaseId.get();
        if (id == 0) {
            return false;
        }
        try {
            LeaseKeepAliveResponse keepAliveResponse = lease.get().keepAliveOnce(id).get(timeout, TimeUnit.SECONDS);
            LOGGER.debug("New TTL of the lease {} is {} seconds", id, keepAliveResponse.getTTL());
            return true;
        } catch (ExecutionException e) {
            boolean notFound = false;
            if (e.getCause() instanceof EtcdException) {
                EtcdException ex = (EtcdException) e.getCause();
                notFound = ex.getErrorCode() == ErrorCode.NOT_FOUND;
            }
            if (notFound) {
                LOGGER.debug("The lease {} doesn't exist anymore", id);
                leaseId.set(0);
            } else {
                LOGGER.debug("Could not renew the lease {}", id, e);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (TimeoutException e) {
            LOGGER.debug("Timeout while trying to renew the lease {}", id);
        }
        return false;
    }

    /**
     * Grants a new lease and tries to put a key-value pair corresponding to the {@code servicePath} if it is absent.
     *
     * @return {@code true} if the put operation is successful indicating that the leadership has been taken,
     *         {@code false} otherwise.
     */
    private boolean tryTakeLeadership() {
        try {
            long id = lease.get().grant(ttl, timeout, TimeUnit.SECONDS).get(timeout, TimeUnit.SECONDS).getID();
            Txn transaction = kv.get().txn();
            ByteSequence key = ByteSequence.from(servicePath.getBytes());
            // Put if absent
            TxnResponse response = transaction.If(new Cmp(key, Cmp.Op.EQUAL, CmpTarget.version(0)))
                    .Then(Op.put(key, ByteSequence.from(serviceName.getBytes()),
                            PutOption.newBuilder().withLeaseId(id).build()))
                    .commit()
                    .get(timeout, TimeUnit.SECONDS);

            boolean succeeded = response.isSucceeded();
            if (succeeded) {
                leaseId.set(id);
            } else {
                lease.get().revoke(id);
            }
            return succeeded;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            LOGGER.debug("Could not try to take the leadership", e);
        } catch (TimeoutException e) {
            LOGGER.debug("Timeout while trying to take the leadership");
        }
        return false;
    }
}
