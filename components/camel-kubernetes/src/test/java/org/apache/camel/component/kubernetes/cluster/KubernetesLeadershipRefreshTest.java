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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each change of {@link KubernetesClusterView#setDisabled(boolean)} (RebalancingCamelClusterService changes it on every
 * rebalance) refreshes the leadership status at once, and must not leave one more chain of refreshes behind: the pod
 * keeps polling the API server once per retry period.
 */
class KubernetesLeadershipRefreshTest extends CamelTestSupport {

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
    void testSetDisabledDoesNotAddRefreshes(LeaseResourceType type) throws Exception {
        String leader = startCluster(type);
        String follower = leader.equals("mypod1") ? "mypod2" : "mypod1";
        LockTestServer<?> server = servers.get(follower);
        KubernetesClusterView view = (KubernetesClusterView) services.get(follower).getView(NS);

        // the follower reads the lease and the pods once per retry period: 20 requests take about 10 retry periods
        long before = millisForRequests(server, 20);

        // the partition of the follower is disabled and enabled again, as on 5 rebalances
        for (int i = 0; i < 5; i++) {
            view.setDisabled(true);
            view.setDisabled(false);
        }
        long after = millisForRequests(server, 20);

        assertTrue(3 * after > before,
                "The API server is polled more often after setDisabled: 20 requests took " + after + " ms, " + before
                                       + " ms before");
        assertEquals(leader, getHolder());
    }

    @ParameterizedTest
    @EnumSource(LeaseResourceType.class)
    void testDisabledLeaderGivesUpLeadership(LeaseResourceType type) throws Exception {
        String leader = startCluster(type);
        String other = leader.equals("mypod1") ? "mypod2" : "mypod1";

        ((KubernetesClusterView) services.get(leader).getView(NS)).setDisabled(true);

        recorders.get(other).waitForLeader(other::equals, 4L * LEASE_TIME_MILLIS, TimeUnit.MILLISECONDS);
        await().atMost(LEASE_TIME_MILLIS, TimeUnit.MILLISECONDS).until(() -> !services.get(leader).isLeader(NS));
        assertEquals(other, getHolder());
    }

    private static long millisForRequests(LockTestServer<?> server, int requests) {
        int start = server.getRequestCount();
        long t0 = System.nanoTime();
        await().atMost(30, TimeUnit.SECONDS).until(() -> server.getRequestCount() >= start + requests);
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
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
