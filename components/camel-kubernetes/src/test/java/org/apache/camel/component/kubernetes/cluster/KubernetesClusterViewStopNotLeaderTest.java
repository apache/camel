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
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.coordination.v1.Lease;
import io.fabric8.kubernetes.api.model.coordination.v1.LeaseBuilder;
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
 * Stopping a {@link KubernetesClusterView} whose pod is not leader, while the lease may still name the pod, releases
 * the lease, so that another pod can lead while the pod stays ready.
 */
class KubernetesClusterViewStopNotLeaderTest extends CamelTestSupport {

    private static final int LEASE_TIME_MILLIS = 4000;
    private static final int RENEW_DEADLINE_MILLIS = 3000;
    private static final int RETRY_PERIOD_MILLIS = 200;
    // shorter than the renew deadline, which the stop and the release may take together
    private static final long ANSWER_DELAY_MILLIS = 1000;
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
    void testStopWhileCreatingTheLeaseReleasesIt(LeaseResourceType type) throws Exception {
        lockSimulator = type == LeaseResourceType.Lease
                ? new LeaseLockSimulator("leaders-" + NS) : new ConfigMapLockSimulator("leaders");
        stopWhileTakingAnEmptyLeaseReleasesIt(type);
    }

    @ParameterizedTest
    @EnumSource(LeaseResourceType.class)
    void testStopWhileTakingAnEmptyLeaseReleasesIt(LeaseResourceType type) throws Exception {
        if (type == LeaseResourceType.Lease) {
            LeaseLockSimulator lease = new LeaseLockSimulator("leaders-" + NS);
            // a lease without holder, as left by a release
            lease.setResource(new LeaseBuilder().withNewMetadata().withName("leaders-" + NS).withNamespace("test")
                    .endMetadata().withNewSpec().endSpec().build(), true);
            lockSimulator = lease;
        } else {
            ConfigMapLockSimulator configMap = new ConfigMapLockSimulator("leaders");
            configMap.setResource(new ConfigMapBuilder().withNewMetadata().withName("leaders").withNamespace("test")
                    .endMetadata().build(), true);
            lockSimulator = configMap;
        }
        stopWhileTakingAnEmptyLeaseReleasesIt(type);
    }

    private void stopWhileTakingAnEmptyLeaseReleasesIt(LeaseResourceType type) throws Exception {
        addMember("mypod1", type);
        addMember("mypod2", type);
        // mypod2 cannot reach the API server for now, and the API server applies a request of mypod1 when it arrives
        // but answers it only after a delay
        servers.get("mypod2").setRefuseRequests(true);
        servers.get("mypod1").setDelayRequests(ANSWER_DELAY_MILLIS);
        context.start();

        // the API server has applied the request of mypod1 that takes the lease, and its answer is on the way
        await().atMost(10, TimeUnit.SECONDS).pollInterval(10, TimeUnit.MILLISECONDS)
                .until(() -> "mypod1".equals(getHolder()));
        servers.get("mypod1").setDelayRequests(null);
        assertFalse(services.get("mypod1").isLeader(NS));

        // the stop interrupts the request: mypod1 never was leader, and the lease that names it is released
        KubernetesClusterView view = stopView("mypod1");
        assertFalse(view.getLocalMember().isLeader());
        assertNull(recorders.get("mypod1").getLastTimeOf("mypod1"::equals));
        assertNull(getHolder());

        // so mypod1, which is still ready, does not keep the other pods from leading
        servers.get("mypod2").setRefuseRequests(false);
        recorders.get("mypod2").waitForLeader("mypod2"::equals, 10, TimeUnit.SECONDS);
        assertTrue(services.get("mypod2").isLeader(NS));
        assertEquals("mypod2", getHolder());
    }

    @ParameterizedTest
    @EnumSource(LeaseResourceType.class)
    void testStopWhileNotReadyReleasesTheLease(LeaseResourceType type) throws Exception {
        lockSimulator = type == LeaseResourceType.Lease
                ? new LeaseLockSimulator("leaders-" + NS) : new ConfigMapLockSimulator("leaders");
        addMember("mypod1", type);
        addMember("mypod2", type);
        servers.get("mypod2").setRefuseRequests(true);
        context.start();
        await().atMost(10, TimeUnit.SECONDS).until(() -> services.get("mypod1").isLeader(NS));
        assertEquals("mypod1", getHolder());

        // mypod1 is not ready for a moment (and mypod2 cannot reach the API server meanwhile, so it cannot take over):
        // mypod1 is no longer leader, but the lease still names it
        servers.values().forEach(s -> s.removePod("mypod1"));
        await().atMost(10, TimeUnit.SECONDS).until(() -> !services.get("mypod1").isLeader(NS));
        assertEquals("mypod1", getHolder());

        // the view of mypod1 is stopped in that window: the lease is released
        KubernetesClusterView view = stopView("mypod1");
        assertFalse(view.getLocalMember().isLeader());
        assertNull(getHolder());

        // so when mypod1 is ready again, it does not keep the other pods from leading
        servers.values().forEach(s -> s.addPod("mypod1"));
        servers.get("mypod2").setRefuseRequests(false);
        recorders.get("mypod2").waitForLeader("mypod2"::equals, 10, TimeUnit.SECONDS);
        assertTrue(services.get("mypod2").isLeader(NS));
        assertEquals("mypod2", getHolder());
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
