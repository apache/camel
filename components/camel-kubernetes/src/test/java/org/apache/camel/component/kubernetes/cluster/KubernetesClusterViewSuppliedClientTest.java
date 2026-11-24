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
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.camel.cluster.CamelClusterView;
import org.apache.camel.component.kubernetes.KubernetesConfiguration;
import org.apache.camel.component.kubernetes.cluster.utils.ConfigMapLockSimulator;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@link KubernetesClient} set on the {@link KubernetesConfiguration} of the cluster service belongs to the user, and
 * is shared by all the views of the service: a view that stops must not close it.
 */
class KubernetesClusterViewSuppliedClientTest extends CamelTestSupport {

    private static final int LEASE_TIME_MILLIS = 2000;
    private static final int RENEW_DEADLINE_MILLIS = 1000;
    private static final int RETRY_PERIOD_MILLIS = 200;
    private static final String POD = "mypod1";
    private static final List<String> GROUPS = List.of("group-a", "group-b");

    private final Map<String, ResourceLockSimulator<?>> simulators = new HashMap<>();
    private LockTestServer<?> server;
    private KubernetesClient client;
    private KubernetesClusterService service;

    @AfterEach
    void shutdownLock() {
        context.stop();
        if (server != null) {
            server.destroy();
        }
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @ParameterizedTest
    @EnumSource(LeaseResourceType.class)
    void testStoppedViewKeepsTheClientOfTheOtherGroups(LeaseResourceType type) throws Exception {
        startGroups(type);

        // the clustered route of group-a is removed while the CamelContext runs, which stops the view of group-a
        CamelClusterView stopped = stopView("group-a");
        assertFalse(stopped.getLocalMember().isLeader());

        // group-b runs on the same client: it keeps the leadership and renews its lease
        await().during(2L * LEASE_TIME_MILLIS, TimeUnit.MILLISECONDS)
                .atMost(3L * LEASE_TIME_MILLIS, TimeUnit.MILLISECONDS)
                .until(() -> service.isLeader("group-b"));
        assertEquals(POD, getHolder(type, "group-b"));
        // and the client of the user still works
        assertNotNull(client.pods().inNamespace("test").list());
    }

    @ParameterizedTest
    @EnumSource(LeaseResourceType.class)
    void testRestartedViewWorksWithTheSuppliedClient(LeaseResourceType type) throws Exception {
        startGroups(type);
        stopView("group-a");

        // the view is used again (such as the clustered route added again): it takes the leadership again
        KubernetesClusterView restarted = service.getView("group-a");
        assertTrue(restarted.isStarted());
        await().atMost(3L * LEASE_TIME_MILLIS, TimeUnit.MILLISECONDS).until(() -> service.isLeader("group-a"));
        assertEquals(POD, getHolder(type, "group-a"));
    }

    /**
     * Starts one pod that leads all the {@link #GROUPS}, with a client set on the configuration of its cluster service.
     */
    private void startGroups(LeaseResourceType type) throws Exception {
        server = new LockTestServer<>();
        server.addPod(POD);
        if (type == LeaseResourceType.Lease) {
            for (String group : GROUPS) {
                simulators.put(group, new LeaseLockSimulator("leaders-" + group));
            }
        } else {
            ResourceLockSimulator<?> configMap = new ConfigMapLockSimulator("leaders");
            GROUPS.forEach(group -> simulators.put(group, configMap));
        }
        simulators.values().stream().distinct().forEach(server::addSimulator);

        client = server.createClient();
        KubernetesConfiguration configuration = new KubernetesConfiguration();
        configuration.setKubernetesClient(client);
        service = new KubernetesClusterService(configuration);
        service.setNamespace("test");
        service.setPodName(POD);
        service.setLeaseDurationMillis(LEASE_TIME_MILLIS);
        service.setRenewDeadlineMillis(RENEW_DEADLINE_MILLIS);
        service.setRetryPeriodMillis(RETRY_PERIOD_MILLIS);
        service.setJitterFactor(1.1);
        service.setLeaseResourceType(type);
        context().addService(service);
        for (String group : GROUPS) {
            service.getView(group);
        }
        context.start();

        await().atMost(5, TimeUnit.SECONDS).until(() -> GROUPS.stream().allMatch(service::isLeader));
        for (String group : GROUPS) {
            assertEquals(POD, getHolder(type, group));
        }
    }

    /**
     * Releases the view of the group as its last user does (MasterConsumer, ClusteredRoutePolicy), which stops it.
     */
    private CamelClusterView stopView(String group) throws Exception {
        CamelClusterView view = service.getView(group);
        // the view is retained twice: by the getView of startGroups, and by the one above
        service.releaseView(view);
        service.releaseView(view);
        assertTrue(((KubernetesClusterView) view).isStopped(), "view should be stopped");
        return view;
    }

    private String getHolder(LeaseResourceType type, String group) {
        Object resource = simulators.get(group).getResource();
        if (type == LeaseResourceType.Lease) {
            Lease lease = (Lease) resource;
            return lease != null && lease.getSpec() != null ? lease.getSpec().getHolderIdentity() : null;
        }
        ConfigMap configMap = (ConfigMap) resource;
        return configMap != null && configMap.getData() != null ? configMap.getData().get("leader.pod." + group) : null;
    }
}
