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
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.coordination.v1.Lease;
import org.apache.camel.component.kubernetes.KubernetesConfiguration;
import org.apache.camel.component.kubernetes.cluster.utils.ConfigMapLockSimulator;
import org.apache.camel.component.kubernetes.cluster.utils.LeaderRecorder;
import org.apache.camel.component.kubernetes.cluster.utils.LeaderRecorder.LeadershipInfo;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A pod that holds the lease but is no longer ready gives up the leadership, and must not report itself as leader again
 * while it is not ready: it is not a valid leader, and another pod can take the lease over.
 */
class KubernetesNotReadyLeaseHolderTest extends CamelTestSupport {

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
    void testNotReadyHolderDoesNotReportLeadership(LeaseResourceType type) {
        String leader = startCluster(type);

        // no pod is ready: the lease keeps naming the leader, as no other pod can take it over
        long notReadySince = System.currentTimeMillis();
        servers.values().forEach(s -> {
            s.removePod("mypod1");
            s.removePod("mypod2");
        });
        awaitLeadershipGivenUp(leader, notReadySince);

        // the pod is not a valid leader while it is not ready, so it must not report the leadership again
        await().during(2L * LEASE_TIME_MILLIS, TimeUnit.MILLISECONDS)
                .atMost(3L * LEASE_TIME_MILLIS, TimeUnit.MILLISECONDS)
                .until(() -> !services.get(leader).isLeader(NS) && recorders.get(leader).getCurrentLeader() == null);
        assertEquals(leader, getHolder());
    }

    @ParameterizedTest
    @EnumSource(LeaseResourceType.class)
    void testNotReadyHolderIsReplacedWithoutReportingLeadership(LeaseResourceType type) {
        String leader = startCluster(type);
        String other = leader.equals("mypod1") ? "mypod2" : "mypod1";
        long notReadySince = System.currentTimeMillis();

        // the leader is no longer ready: the other pod takes the lease over and becomes the leader
        servers.values().forEach(s -> s.removePod(leader));
        recorders.get(other).waitForLeader(other::equals, 3L * LEASE_TIME_MILLIS, TimeUnit.MILLISECONDS);
        assertEquals(other, getHolder());

        // once the old leader saw that it is not ready (no leader), it did not report itself as leader again
        List<String> events = recorders.get(leader).getLeadershipInfo().stream()
                .filter(info -> info.getChangeTimestamp() >= notReadySince)
                .map(LeadershipInfo::getLeader)
                .toList();
        int stepDown = events.indexOf(null);
        assertTrue(stepDown >= 0, "the pod did not give up the leadership: " + events);
        assertFalse(events.subList(stepDown, events.size()).contains(leader),
                "the pod reported the leadership again while it was not ready: " + events);
        assertFalse(services.get(leader).isLeader(NS));
    }

    @ParameterizedTest
    @EnumSource(LeaseResourceType.class)
    void testHolderReadyAgainBecomesLeaderAgain(LeaseResourceType type) {
        String leader = startCluster(type);
        long notReadySince = System.currentTimeMillis();
        servers.values().forEach(s -> {
            s.removePod("mypod1");
            s.removePod("mypod2");
        });
        awaitLeadershipGivenUp(leader, notReadySince);

        // the pods are ready again: the pod named by the lease leads again, after the lease duration
        servers.values().forEach(s -> {
            s.addPod("mypod1");
            s.addPod("mypod2");
        });
        recorders.get(leader).waitForLeader(leader::equals, 3L * LEASE_TIME_MILLIS, TimeUnit.MILLISECONDS);
        await().atMost(LEASE_TIME_MILLIS, TimeUnit.MILLISECONDS).until(() -> services.get(leader).isLeader(NS));
        assertEquals(leader, getHolder());
    }

    private void awaitLeadershipGivenUp(String pod, long since) {
        await().atMost(2L * LEASE_TIME_MILLIS, TimeUnit.MILLISECONDS)
                .until(() -> recorders.get(pod).getLeadershipInfo().stream()
                        .anyMatch(info -> info.getChangeTimestamp() >= since && info.getLeader() == null));
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
