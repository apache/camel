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
package org.apache.camel.component.kubernetes.cluster;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.coordination.v1.Lease;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kubernetes.KubernetesConfiguration;
import org.apache.camel.component.kubernetes.cluster.lock.KubernetesLockConfiguration;
import org.apache.camel.component.kubernetes.cluster.utils.ConfigMapLockSimulator;
import org.apache.camel.component.kubernetes.cluster.utils.LeaderRecorder;
import org.apache.camel.component.kubernetes.cluster.utils.LeaseLockSimulator;
import org.apache.camel.component.kubernetes.cluster.utils.LockTestServer;
import org.apache.camel.component.kubernetes.cluster.utils.ResourceLockSimulator;
import org.apache.camel.impl.cluster.ClusteredRoutePolicy;
import org.apache.camel.support.LifecycleStrategySupport;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stopping a {@link KubernetesClusterView} (its last user releases it, or the cluster service stops) gives up the
 * leadership of the pod: the view no longer reports it, the listeners are told, and the lease is released so another
 * pod takes over.
 */
class KubernetesClusterViewStopTest extends CamelTestSupport {

    private static final int LEASE_TIME_MILLIS = 2000;
    private static final int RENEW_DEADLINE_MILLIS = 1000;
    private static final int RETRY_PERIOD_MILLIS = 200;
    private static final String NS = "app";
    private static final List<String> GROUPS = List.of("group-a", "group-b", "group-c");

    private ResourceLockSimulator<?> lockSimulator;
    private final Map<String, LockTestServer<?>> servers = new HashMap<>();
    private final Map<String, KubernetesClusterService> services = new HashMap<>();
    private final Map<String, LeaderRecorder> recorders = new HashMap<>();

    @AfterEach
    void shutdownLock() {
        // stop the services first, as they release the lease through the servers
        context.stop();
        servers.values().forEach(s -> {
            try {
                s.destroy();
            } catch (Exception e) {
                // ignore
            }
        });
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @ParameterizedTest
    @EnumSource(LeaseResourceType.class)
    void testStoppedViewReleasesLeadership(LeaseResourceType type) throws Exception {
        String leader = startCluster(type);
        String other = leader.equals("mypod1") ? "mypod2" : "mypod1";
        KubernetesClusterView view = stopView(leader);

        // the stopped view no longer reports the leadership, and its listeners are told
        assertFalse(view.getLocalMember().isLeader());
        assertTrue(view.getLeader().isEmpty());
        assertFalse(services.get(leader).isLeader(NS));
        assertNull(recorders.get(leader).getCurrentLeader());
        // the lease no longer names the pod (which is still ready), so the other pod takes over (the other pod may
        // have taken the lease already, so the lease is not expected to be empty here)
        assertNotEquals(leader, getHolder());
        recorders.get(other).waitForLeader(other::equals, 3L * LEASE_TIME_MILLIS, TimeUnit.MILLISECONDS);
        assertTrue(services.get(other).isLeader(NS));
        assertEquals(other, getHolder());
        assertFalse(services.get(leader).isLeader(NS));
    }

    @ParameterizedTest
    @EnumSource(LeaseResourceType.class)
    void testRestartedViewDoesNotReportStaleLeadership(LeaseResourceType type) throws Exception {
        String leader = startCluster(type);
        String other = leader.equals("mypod1") ? "mypod2" : "mypod1";
        stopView(leader);
        recorders.get(other).waitForLeader(other::equals, 3L * LEASE_TIME_MILLIS, TimeUnit.MILLISECONDS);

        // the view is used again (such as a clustered route added again) while the API server cannot be reached
        servers.get(leader).setRefuseRequests(true);
        KubernetesClusterView restarted = (KubernetesClusterView) services.get(leader).getView(NS);
        assertTrue(restarted.isStarted());
        await().during(2L * LEASE_TIME_MILLIS, TimeUnit.MILLISECONDS)
                .atMost(3L * LEASE_TIME_MILLIS, TimeUnit.MILLISECONDS)
                .until(() -> !restarted.getLocalMember().isLeader() && services.get(other).isLeader(NS));
    }

    @ParameterizedTest
    @EnumSource(LeaseResourceType.class)
    void testStopWhenTheLeaseCannotBeReleased(LeaseResourceType type) throws Exception {
        String leader = startCluster(type);
        String other = leader.equals("mypod1") ? "mypod2" : "mypod1";
        servers.get(leader).setRefuseUpdateRequests(true);
        KubernetesClusterView view = stopView(leader);

        // the failure is logged: the view is stopped and no longer reports the leadership, but the lease is kept
        assertTrue(view.isStopped());
        assertFalse(view.getLocalMember().isLeader());
        assertNull(recorders.get(leader).getCurrentLeader());
        assertEquals(leader, getHolder());

        // until the pod is no longer ready
        servers.values().forEach(s -> s.removePod(leader));
        recorders.get(other).waitForLeader(other::equals, 3L * LEASE_TIME_MILLIS + 2000, TimeUnit.MILLISECONDS);
        assertFalse(services.get(leader).isLeader(NS));
    }

    @ParameterizedTest
    @EnumSource(LeaseResourceType.class)
    void testStopGivesUpTheReleaseAfterTheRenewDeadline(LeaseResourceType type) throws Exception {
        String leader = startCluster(type);
        // the API server answers the leader pod only after a long delay (the Kubernetes client would also try a failed
        // request again, by default up to 10 times): the stop and the release together wait the renew deadline at most
        servers.get(leader).setDelayRequests(5L * RENEW_DEADLINE_MILLIS);
        long start = System.nanoTime();
        KubernetesClusterView view = stopView(leader);
        long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertTrue(millis < 2L * RENEW_DEADLINE_MILLIS, "The stop took " + millis + " ms");
        assertFalse(view.getLocalMember().isLeader());
        assertNull(recorders.get(leader).getCurrentLeader());
        // the release was given up, so the lease is kept until the pod is no longer ready, as before
        assertEquals(leader, getHolder());
    }

    @ParameterizedTest
    @EnumSource(LeaseResourceType.class)
    void testStopBeforeTheLeadershipTakesEffectKeepsTheLease(LeaseResourceType type) throws Exception {
        String leader = startCluster(type);
        String other = leader.equals("mypod1") ? "mypod2" : "mypod1";

        // the leader is no longer ready: the other pod takes the lease, and waits for the lease duration before it
        // acts as leader, so that the old leader has given up the leadership
        servers.values().forEach(s -> s.removePod(leader));
        await().atMost(LEASE_TIME_MILLIS, TimeUnit.MILLISECONDS).until(() -> other.equals(getHolder()));
        assertFalse(services.get(other).isLeader(NS));

        // stopped while waiting: the lease is kept, as an empty lease would let a pod take it over at once
        KubernetesClusterView view = stopView(other);
        assertFalse(view.getLocalMember().isLeader());
        assertEquals(other, getHolder());
    }

    @ParameterizedTest
    @EnumSource(LeaseResourceType.class)
    void testStopOfManyGroupsWaitsOnceForAnApiServerThatStoppedAnswering(LeaseResourceType type) throws Exception {
        Map<String, ResourceLockSimulator<?>> simulators = new HashMap<>();
        KubernetesClusterService service = startGroups(type, simulators);
        // the API server answers only after a long delay from now on: the groups share one renew deadline, so the
        // stop of the first group waits for its release until the renew deadline, and the next groups do not wait
        servers.get("mypod1").setDelayRequests(5L * RENEW_DEADLINE_MILLIS);
        long start = System.nanoTime();
        service.stop();
        long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertTrue(millis < 2L * RENEW_DEADLINE_MILLIS, "The stop of " + GROUPS.size() + " groups took " + millis + " ms");
        for (String group : GROUPS) {
            assertTrue(service.getView(group).isStopped());
            assertFalse(service.getView(group).getLocalMember().isLeader());
            // not released, so the lease is kept until the pod is no longer ready, as before
            assertEquals("mypod1", getHolder(type, simulators, group));
        }
    }

    @ParameterizedTest
    @EnumSource(LeaseResourceType.class)
    void testStopOfManyGroupsDoesNotWaitWhenTheLastLookupFailed(LeaseResourceType type) throws Exception {
        Map<String, ResourceLockSimulator<?>> simulators = new HashMap<>();
        KubernetesClusterService service = startGroups(type, simulators);
        // the API server fails the lookups (the test client does not try a failed request again, while the default
        // client does so for a while first): the leadership is lost locally once the renew deadline has passed
        LockTestServer<?> server = servers.get("mypod1");
        server.setRefuseRequests(true);
        await().atMost(5, TimeUnit.SECONDS).until(() -> GROUPS.stream().noneMatch(service::isLeader));
        // and then answers only after a long delay: the groups do not try to release the lease, so the stop does not
        // wait for the API server (a release that is tried would wait until the renew deadline)
        server.setDelayRequests(5L * RENEW_DEADLINE_MILLIS);
        long start = System.nanoTime();
        service.stop();
        long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        // a release that is tried waits until the renew deadline, so the stop would take at least that long
        assertTrue(millis < RENEW_DEADLINE_MILLIS, "The stop of " + GROUPS.size() + " groups took " + millis + " ms");
        for (String group : GROUPS) {
            assertTrue(service.getView(group).isStopped());
            assertEquals("mypod1", getHolder(type, simulators, group));
        }
    }

    @ParameterizedTest
    @EnumSource(LeaseResourceType.class)
    void testContextStopWithClusteredRoutesWaitsOnceForAnApiServerThatStoppedAnswering(LeaseResourceType type)
            throws Exception {
        Map<String, ResourceLockSimulator<?>> simulators = new HashMap<>();
        KubernetesClusterService service = startGroups(type, simulators, true);
        // when the CamelContext stops, each clustered route releases its view, which stops it, before the cluster
        // service stops: these views share one renew deadline too
        servers.get("mypod1").setDelayRequests(5L * RENEW_DEADLINE_MILLIS);
        long start = System.nanoTime();
        context.stop();
        long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertTrue(millis < 2L * RENEW_DEADLINE_MILLIS, "The stop of " + GROUPS.size() + " groups took " + millis + " ms");
        for (String group : GROUPS) {
            assertEquals("mypod1", getHolder(type, simulators, group));
        }
    }

    @ParameterizedTest
    @EnumSource(LeaseResourceType.class)
    void testContextStopReleasesAllGroupsWhenTheTimeBetweenTheirStopsIsLong(LeaseResourceType type) throws Exception {
        Map<String, ResourceLockSimulator<?>> simulators = new HashMap<>();
        startGroups(type, simulators, true);
        // the routes are shut down one after another, and each one takes longer than the renew deadline after it has
        // released its view (like the graceful shutdown of routes): only the time that the views spend in their stop
        // counts against the renew deadline that they share, so every group still releases its lease
        context.addLifecycleStrategy(new LifecycleStrategySupport() {
            @Override
            public void onRoutesRemove(Collection<Route> routes) {
                try {
                    new CountDownLatch(1).await(RENEW_DEADLINE_MILLIS * 3L / 2, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        context.stop();

        for (String group : GROUPS) {
            assertNull(getHolder(type, simulators, group), "The lease of " + group + " should be released");
        }
    }

    private KubernetesClusterService startGroups(LeaseResourceType type, Map<String, ResourceLockSimulator<?>> simulators)
            throws Exception {
        return startGroups(type, simulators, false);
    }

    /**
     * Starts one pod that leads all the {@link #GROUPS}, and returns its cluster service. The views are used by the
     * test, or by a route with a {@link ClusteredRoutePolicy} for each group.
     */
    private KubernetesClusterService startGroups(
            LeaseResourceType type, Map<String, ResourceLockSimulator<?>> simulators, boolean clusteredRoutes)
            throws Exception {
        LockTestServer<?> server = new LockTestServer<>();
        servers.put("mypod1", server);
        server.addPod("mypod1");
        if (type == LeaseResourceType.Lease) {
            for (String group : GROUPS) {
                simulators.put(group, new LeaseLockSimulator("leaders-" + group));
            }
        } else {
            ResourceLockSimulator<?> configMap = new ConfigMapLockSimulator("leaders");
            GROUPS.forEach(group -> simulators.put(group, configMap));
        }
        simulators.values().forEach(server::addSimulator);

        KubernetesClusterService service = new KubernetesClusterService(new KubernetesConfiguration()) {
            @Override
            protected KubernetesConfiguration setConfigDefaults(
                    KubernetesConfiguration configuration, KubernetesLockConfiguration lockConfiguration) {
                // a client for each view, as when the views create their clients from the master URL
                configuration.setKubernetesClient(server.createClient());
                return super.setConfigDefaults(configuration, lockConfiguration);
            }
        };
        service.setNamespace("test");
        service.setPodName("mypod1");
        service.setLeaseDurationMillis(LEASE_TIME_MILLIS);
        service.setRenewDeadlineMillis(RENEW_DEADLINE_MILLIS);
        service.setRetryPeriodMillis(RETRY_PERIOD_MILLIS);
        service.setJitterFactor(1.1);
        service.setLeaseResourceType(type);
        services.put("mypod1", service);
        context().addService(service);
        if (clusteredRoutes) {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    for (String group : GROUPS) {
                        from("direct:" + group).routeId(group)
                                .routePolicy(ClusteredRoutePolicy.forNamespace(service, group))
                                .to("mock:" + group);
                    }
                }
            });
        } else {
            for (String group : GROUPS) {
                service.getView(group);
            }
        }
        context.start();

        await().atMost(5, TimeUnit.SECONDS).until(() -> GROUPS.stream().allMatch(service::isLeader));
        for (String group : GROUPS) {
            assertEquals("mypod1", getHolder(type, simulators, group));
        }
        return service;
    }

    private static String getHolder(LeaseResourceType type, Map<String, ResourceLockSimulator<?>> simulators, String group) {
        Object resource = simulators.get(group).getResource();
        if (type == LeaseResourceType.Lease) {
            Lease lease = (Lease) resource;
            return lease != null && lease.getSpec() != null ? lease.getSpec().getHolderIdentity() : null;
        }
        ConfigMap configMap = (ConfigMap) resource;
        return configMap != null && configMap.getData() != null ? configMap.getData().get("leader.pod." + group) : null;
    }

    private String startCluster(LeaseResourceType type) {
        lockSimulator = type == LeaseResourceType.Lease
                ? new LeaseLockSimulator("leaders-" + NS) : new ConfigMapLockSimulator("leaders");
        addMember("mypod1", type);
        addMember("mypod2", type);
        context.start();

        recorders.get("mypod1").waitForAnyLeader(5, TimeUnit.SECONDS);
        recorders.get("mypod2").waitForAnyLeader(5, TimeUnit.SECONDS);
        String leader = recorders.get("mypod1").getCurrentLeader();
        await().atMost(5, TimeUnit.SECONDS).until(() -> services.get(leader).isLeader(NS));
        assertEquals(leader, getHolder());
        return leader;
    }

    /**
     * Releases the view of the pod as its last user does (MasterConsumer, ClusteredRoutePolicy), which stops it.
     */
    private KubernetesClusterView stopView(String pod) throws Exception {
        KubernetesClusterService service = services.get(pod);
        KubernetesClusterView view = (KubernetesClusterView) service.getView(NS);
        // the view was retained once when the recorder was added
        service.releaseView(view);
        service.releaseView(view);
        assertTrue(view.isStopped(), "view should be stopped");
        return view;
    }

    private String getHolder() {
        Object resource = lockSimulator.getResource();
        if (resource instanceof Lease lease) {
            return lease.getSpec() != null ? lease.getSpec().getHolderIdentity() : null;
        }
        ConfigMap configMap = (ConfigMap) resource;
        return configMap != null && configMap.getData() != null ? configMap.getData().get("leader.pod." + NS) : null;
    }

    private void addMember(String name, LeaseResourceType type) {
        LockTestServer<?> server = new LockTestServer<>();
        servers.put(name, server);
        KubernetesConfiguration configuration = new KubernetesConfiguration();
        configuration.setKubernetesClient(server.createClient());
        KubernetesClusterService service = new KubernetesClusterService(configuration);
        service.setNamespace("test");
        service.setPodName(name);
        service.setLeaseDurationMillis(LEASE_TIME_MILLIS);
        service.setRenewDeadlineMillis(RENEW_DEADLINE_MILLIS);
        service.setRetryPeriodMillis(RETRY_PERIOD_MILLIS);
        service.setJitterFactor(1.1);
        service.setLeaseResourceType(type);
        try {
            context().addService(service);
            LeaderRecorder recorder = new LeaderRecorder();
            service.getView(NS).addEventListener(recorder);
            recorders.put(name, recorder);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        services.put(name, service);

        for (LockTestServer<?> s : servers.values()) {
            for (String pod : servers.keySet()) {
                s.addPod(pod);
            }
            s.addSimulator(lockSimulator);
        }
    }
}
