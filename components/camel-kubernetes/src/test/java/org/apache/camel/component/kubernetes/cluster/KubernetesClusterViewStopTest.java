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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.coordination.v1.Lease;
import org.apache.camel.component.kubernetes.KubernetesConfiguration;
import org.apache.camel.component.kubernetes.cluster.utils.ConfigMapLockSimulator;
import org.apache.camel.component.kubernetes.cluster.utils.LeaderRecorder;
import org.apache.camel.component.kubernetes.cluster.utils.LeaseLockSimulator;
import org.apache.camel.component.kubernetes.cluster.utils.LockTestServer;
import org.apache.camel.component.kubernetes.cluster.utils.ResourceLockSimulator;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        // the lease no longer names the pod (which is still ready), so the other pod takes over
        assertNull(getHolder());
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
        // request again, by default up to 10 times): the stop waits for the release up to the renew deadline only
        servers.get(leader).setDelayRequests(5L * RENEW_DEADLINE_MILLIS);
        long start = System.nanoTime();
        KubernetesClusterView view = stopView(leader);
        long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertTrue(millis < 4L * RENEW_DEADLINE_MILLIS, "The stop took " + millis + " ms");
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
