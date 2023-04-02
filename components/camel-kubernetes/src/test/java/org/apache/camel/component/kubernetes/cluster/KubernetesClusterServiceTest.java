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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.coordination.v1.LeaseBuilder;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.cluster.CamelPreemptiveClusterService;
import org.apache.camel.component.kubernetes.KubernetesConfiguration;
import org.apache.camel.component.kubernetes.cluster.utils.ConfigMapLockSimulator;
import org.apache.camel.component.kubernetes.cluster.utils.LeaderRecorder;
import org.apache.camel.component.kubernetes.cluster.utils.LeaseLockSimulator;
import org.apache.camel.component.kubernetes.cluster.utils.LockTestServer;
import org.apache.camel.component.kubernetes.cluster.utils.ResourceLockSimulator;
import org.apache.camel.support.cluster.RebalancingCamelClusterService;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test leader election scenarios using a mock server.
 */
public class KubernetesClusterServiceTest extends CamelTestSupport {

    private static final int LEASE_TIME_MILLIS = 2000;
    private static final int RENEW_DEADLINE_MILLIS = 1000;
    private static final int RETRY_PERIOD_MILLIS = 200;
    private static final double JITTER_FACTOR = 1.1;

    private ConfigMapLockSimulator configMapLockSimulator;
    private Map<String, LeaseLockSimulator> leaseLockSimulators = new HashMap<>();

    private Map<String, LockTestServer<?>> lockServers = new HashMap<>();

    private Map<String, CamelPreemptiveClusterService> clusterServices = new HashMap<>();

    @AfterEach
    public void shutdownLock() {
        for (LockTestServer<?> server : this.lockServers.values()) {
            try {
                server.destroy();
            } catch (Exception e) {
                // can happen in case of delay
            }
        }
        this.lockServers = new HashMap<>();
        configMapLockSimulator = null;
        leaseLockSimulators = new HashMap<>();
        clusterServices = new HashMap<>();
    }

    @ParameterizedTest
    @EnumSource(LeaseResourceType.class)
    public void testSimpleLeaderElection(LeaseResourceType type) {
        LeaderRecorder mypod1 = addMember("mypod1", type);
        LeaderRecorder mypod2 = addMember("mypod2", type);
        context.start();

        mypod1.waitForAnyLeader(5, TimeUnit.SECONDS);
        mypod2.waitForAnyLeader(5, TimeUnit.SECONDS);

        String leader = mypod1.getCurrentLeader();
        assertNotNull(leader);
        assertTrue(leader.startsWith("mypod"));
        assertEquals(mypod2.getCurrentLeader(), leader, "Leaders should be equals");
    }

    @ParameterizedTest
    @EnumSource(LeaseResourceType.class)
    public void testMultipleMembersLeaderElection(LeaseResourceType type) {
        int number = 5;
        List<LeaderRecorder> members
                = IntStream.range(0, number).mapToObj(i -> addMember("mypod" + i, type)).collect(Collectors.toList());
        context.start();

        for (LeaderRecorder member : members) {
            member.waitForAnyLeader(5, TimeUnit.SECONDS);
        }

        Set<String> leaders = members.stream().map(LeaderRecorder::getCurrentLeader).collect(Collectors.toSet());
        assertEquals(1, leaders.size());
        String leader = leaders.iterator().next();
        assertTrue(leader.startsWith("mypod"));
    }

    @Test
    public void testSimpleLeaderElectionWithExistingConfigMap() {
        this.configMapLockSimulator = new ConfigMapLockSimulator("leaders");
        configMapLockSimulator.setResource(
                new ConfigMapBuilder().withNewMetadata().withNamespace("test").withName("leaders").and().build(), true);

        LeaderRecorder mypod1 = addMember("mypod1", LeaseResourceType.ConfigMap);
        LeaderRecorder mypod2 = addMember("mypod2", LeaseResourceType.ConfigMap);
        context.start();

        mypod1.waitForAnyLeader(10, TimeUnit.SECONDS);
        mypod2.waitForAnyLeader(10, TimeUnit.SECONDS);

        String leader = mypod1.getCurrentLeader();
        assertTrue(leader.startsWith("mypod"));
        assertEquals(mypod2.getCurrentLeader(), leader, "Leaders should be equals");
    }

    @Test
    public void testSimpleLeaderElectionWithExistingLeases() {
        LeaseLockSimulator simulator = new LeaseLockSimulator("leaders-mygroup");
        simulator.setResource(new LeaseBuilder()
                .withNewMetadata().withName("leaders-mygroup").withNamespace("test")
                .and()
                .build(), true);
        this.leaseLockSimulators.put("mygroup", simulator);

        LeaderRecorder mypod1 = addMember("mypod1", "mygroup", LeaseResourceType.Lease);
        LeaderRecorder mypod2 = addMember("mypod2", "mygroup", LeaseResourceType.Lease);
        context.start();

        mypod1.waitForAnyLeader(10, TimeUnit.SECONDS);
        mypod2.waitForAnyLeader(10, TimeUnit.SECONDS);

        String leader = mypod1.getCurrentLeader();
        assertTrue(leader.startsWith("mypod"));
        assertEquals(mypod2.getCurrentLeader(), leader, "Leaders should be equals");
    }

    @ParameterizedTest
    @EnumSource(LeaseResourceType.class)
    public void testLeadershipLoss(LeaseResourceType type) {
        LeaderRecorder mypod1 = addMember("mypod1", type);
        LeaderRecorder mypod2 = addMember("mypod2", type);
        context.start();

        mypod1.waitForAnyLeader(5, TimeUnit.SECONDS);
        mypod2.waitForAnyLeader(5, TimeUnit.SECONDS);

        String firstLeader = mypod1.getCurrentLeader();

        LeaderRecorder formerLeaderRecorder = firstLeader.equals("mypod1") ? mypod1 : mypod2;
        LeaderRecorder formerLoserRecorder = firstLeader.equals("mypod1") ? mypod2 : mypod1;

        refuseRequestsFromPod(firstLeader);
        disconnectPod(firstLeader);

        formerLeaderRecorder.waitForALeaderChange(7, TimeUnit.SECONDS);
        formerLoserRecorder.waitForANewLeader(firstLeader, 7, TimeUnit.SECONDS);

        String secondLeader = formerLoserRecorder.getCurrentLeader();
        assertNotEquals(firstLeader, secondLeader, "The firstLeader should be different from the new one");

        Long lossTimestamp = formerLeaderRecorder.getLastTimeOf(l -> l == null);
        Long gainTimestamp = formerLoserRecorder.getLastTimeOf(secondLeader::equals);

        assertTrue(gainTimestamp >= lossTimestamp + (LEASE_TIME_MILLIS - RENEW_DEADLINE_MILLIS) / 2,
                "At least half distance must elapse from leadership loss and regain (see renewDeadlineSeconds)");
        checkLeadershipChangeDistance((LEASE_TIME_MILLIS - RENEW_DEADLINE_MILLIS) / 2, TimeUnit.MILLISECONDS, mypod1, mypod2);
    }

    @ParameterizedTest
    @EnumSource(LeaseResourceType.class)
    public void testSlowLeaderLosingLeadershipOnlyInternally(LeaseResourceType type) {
        LeaderRecorder mypod1 = addMember("mypod1", type);
        LeaderRecorder mypod2 = addMember("mypod2", type);
        context.start();

        mypod1.waitForAnyLeader(5, TimeUnit.SECONDS);
        mypod2.waitForAnyLeader(5, TimeUnit.SECONDS);

        String firstLeader = mypod1.getCurrentLeader();

        LeaderRecorder formerLeaderRecorder = firstLeader.equals("mypod1") ? mypod1 : mypod2;
        LeaderRecorder formerLoserRecorder = firstLeader.equals("mypod1") ? mypod2 : mypod1;

        delayRequestsFromPod(firstLeader, 10, TimeUnit.SECONDS);

        await().atMost(LEASE_TIME_MILLIS, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertNull(formerLeaderRecorder.getCurrentLeader()));
        assertEquals(firstLeader, formerLoserRecorder.getCurrentLeader());
    }

    @ParameterizedTest
    @EnumSource(LeaseResourceType.class)
    public void testRecoveryAfterFailure(LeaseResourceType type) throws Exception {
        LeaderRecorder mypod1 = addMember("mypod1", type);
        LeaderRecorder mypod2 = addMember("mypod2", type);
        context.start();

        mypod1.waitForAnyLeader(5, TimeUnit.SECONDS);
        mypod2.waitForAnyLeader(5, TimeUnit.SECONDS);

        String firstLeader = mypod1.getCurrentLeader();

        for (int i = 0; i < 3; i++) {
            refuseRequestsFromPod(firstLeader);
            Thread.sleep(RENEW_DEADLINE_MILLIS);
            allowRequestsFromPod(firstLeader);
            Thread.sleep(LEASE_TIME_MILLIS);
        }

        assertEquals(firstLeader, mypod1.getCurrentLeader());
        assertEquals(firstLeader, mypod2.getCurrentLeader());
    }

    @Test
    public void testSharedConfigMap() {
        LeaderRecorder a1 = addMember("a1", LeaseResourceType.ConfigMap);
        LeaderRecorder a2 = addMember("a2", LeaseResourceType.ConfigMap);
        LeaderRecorder b1 = addMember("b1", "app2", LeaseResourceType.ConfigMap);
        LeaderRecorder b2 = addMember("b2", "app2", LeaseResourceType.ConfigMap);
        context.start();

        a1.waitForAnyLeader(5, TimeUnit.SECONDS);
        a2.waitForAnyLeader(5, TimeUnit.SECONDS);
        b1.waitForAnyLeader(5, TimeUnit.SECONDS);
        b2.waitForAnyLeader(5, TimeUnit.SECONDS);

        assertNotNull(a1.getCurrentLeader());
        assertTrue(a1.getCurrentLeader().startsWith("a"));
        assertEquals(a1.getCurrentLeader(), a2.getCurrentLeader());
        assertNotNull(b1.getCurrentLeader());
        assertTrue(b1.getCurrentLeader().startsWith("b"));
        assertEquals(b1.getCurrentLeader(), b2.getCurrentLeader());

        assertNotEquals(a1.getCurrentLeader(), b2.getCurrentLeader());
    }

    static Stream<Arguments> rebalancingProvider() {
        return Stream.of(
                // LeaseResourceType, pods, partitions, expected partitions owned, tolerance on owned partitions
                Arguments.of(LeaseResourceType.Lease, 4, 2, 0, 1),
                Arguments.of(LeaseResourceType.Lease, 1, 2, 2, 0),
                Arguments.of(LeaseResourceType.Lease, 2, 2, 1, 0),
                Arguments.of(LeaseResourceType.ConfigMap, 3, 10, 3, 1),
                Arguments.of(LeaseResourceType.Lease, 3, 10, 3, 1),
                Arguments.of(LeaseResourceType.ConfigMap, 6, 23, 3, 1),
                Arguments.of(LeaseResourceType.Lease, 6, 23, 3, 1));
    }

    @ParameterizedTest
    @MethodSource("rebalancingProvider")
    public void testRebalancing(LeaseResourceType type, int pods, int partitions, int expectedPartitionsPerPod, int tolerance) {
        Map<String, List<LeaderRecorder>> recorders = createCluster(type, pods, partitions);
        context.start();

        waitForAllLeaders(recorders, leaders -> {
            Map<String, Long> counts = leaders.values().stream()
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

            for (Long count : counts.values()) {
                if (count < expectedPartitionsPerPod || count > expectedPartitionsPerPod + tolerance) {
                    return false;
                }
            }
            return true;
        }, 30, TimeUnit.SECONDS);
    }

    private Map<String, List<LeaderRecorder>> createCluster(LeaseResourceType type, int pods, int partitions) {
        Map<String, List<LeaderRecorder>> recorders = new HashMap<>();
        for (int i = 0; i < partitions; i++) {
            String partitionName = "partition-" + i;
            recorders.put(partitionName, new ArrayList<>());
            for (int j = 0; j < pods; j++) {
                recorders.get(partitionName).add(addMember("mypod-" + j, partitionName, type, true));
            }
        }
        return recorders;
    }

    private void waitForAllLeaders(
            Map<String, List<LeaderRecorder>> partitionRecorders,
            Predicate<Map<String, String>> condition, long time, TimeUnit unit) {
        Awaitility.waitAtMost(time, unit).until(() -> {
            Map<String, String> leaders = new HashMap<>();
            for (Map.Entry<String, List<LeaderRecorder>> entry : partitionRecorders.entrySet()) {
                String leader = null;
                for (LeaderRecorder recorder : entry.getValue()) {
                    String partitionLeader = recorder.getCurrentLeader();
                    if (partitionLeader == null || isCurrentLeader(leader, partitionLeader)) {
                        return false;
                    }
                    leader = partitionLeader;
                }
                if (leader == null) {
                    return false;
                }
                leaders.put(entry.getKey(), leader);
            }
            return condition.test(leaders);
        });
    }

    private boolean isCurrentLeader(String leader, String partitionLeader) {
        return leader != null && !leader.equals(partitionLeader);
    }

    private void withLockServer(String pod, Consumer<LockTestServer<?>> consumer) {
        consumer.accept(this.lockServers.get(pod));
    }

    private void delayRequestsFromPod(String pod, long delay, TimeUnit unit) {
        withLockServer(pod, server -> server.setDelayRequests(TimeUnit.MILLISECONDS.convert(delay, unit)));
    }

    private void refuseRequestsFromPod(String pod) {
        withLockServer(pod, server -> server.setRefuseRequests(true));
    }

    private void allowRequestsFromPod(String pod) {
        withLockServer(pod, server -> server.setRefuseRequests(false));
    }

    private void disconnectPod(String pod) {
        for (LockTestServer<?> server : this.lockServers.values()) {
            server.removePod(pod);
        }
    }

    private void connectPod(String pod) {
        for (LockTestServer<?> server : this.lockServers.values()) {
            server.addPod(pod);
        }
    }

    private void connectSimulator(ResourceLockSimulator<?> lockSimulator) {
        for (LockTestServer<?> server : this.lockServers.values()) {
            server.addSimulator(lockSimulator);
        }
    }

    private void checkLeadershipChangeDistance(long minimum, TimeUnit unit, LeaderRecorder... recorders) {
        List<LeaderRecorder.LeadershipInfo> infos = Arrays.stream(recorders).flatMap(lr -> lr.getLeadershipInfo().stream())
                .sorted(Comparator.comparingLong(LeaderRecorder.LeadershipInfo::getChangeTimestamp))
                .collect(Collectors.toList());

        LeaderRecorder.LeadershipInfo currentLeaderLastSeen = null;
        for (LeaderRecorder.LeadershipInfo info : infos) {
            if (currentLeaderLastSeen == null || currentLeaderLastSeen.getLeader() == null) {
                currentLeaderLastSeen = info;
            } else {
                if (Objects.equals(info.getLeader(), currentLeaderLastSeen.getLeader())) {
                    currentLeaderLastSeen = info;
                } else if (isCurrentLeader(info.getLeader(), currentLeaderLastSeen.getLeader())) {
                    // switch
                    long delay = info.getChangeTimestamp() - currentLeaderLastSeen.getChangeTimestamp();
                    assertTrue(delay >= TimeUnit.MILLISECONDS.convert(minimum, unit),
                            "Lease time not elapsed between switch, minimum=" + TimeUnit.MILLISECONDS.convert(minimum, unit)
                                                                                      + ", found=" + delay);
                    currentLeaderLastSeen = info;
                }
            }
        }
    }

    private LeaderRecorder addMember(String name, LeaseResourceType type) {
        return addMember(name, "app", type);
    }

    private LeaderRecorder addMember(String name, String namespace, LeaseResourceType type) {
        return addMember(name, namespace, type, false);
    }

    private LeaderRecorder addMember(String name, String namespace, LeaseResourceType type, boolean rebalancing) {
        ResourceLockSimulator<?> lockSimulator;
        switch (type) {
            case ConfigMap:
                if (this.configMapLockSimulator == null) {
                    this.configMapLockSimulator = new ConfigMapLockSimulator("leaders");
                }
                lockSimulator = this.configMapLockSimulator;
                break;
            case Lease:
                if (!this.leaseLockSimulators.containsKey(namespace)) {
                    this.leaseLockSimulators.put(namespace, new LeaseLockSimulator("leaders-" + namespace));
                }
                lockSimulator = this.leaseLockSimulators.get(namespace);
                break;
            default:
                throw new IllegalArgumentException("Unsupported LeaseResourceType " + type);
        }

        if (!this.lockServers.containsKey(name)) {
            this.lockServers.put(name, new LockTestServer<>());
        }
        LockTestServer<?> lockServer = this.lockServers.get(name);

        CamelPreemptiveClusterService member = clusterServices.get(name);
        if (member == null) {
            KubernetesConfiguration configuration = new KubernetesConfiguration();
            configuration.setKubernetesClient(lockServer.createClient());

            KubernetesClusterService service = new KubernetesClusterService(configuration);
            service.setKubernetesNamespace("test");
            service.setPodName(name);
            service.setLeaseDurationMillis(LEASE_TIME_MILLIS);
            service.setRenewDeadlineMillis(RENEW_DEADLINE_MILLIS);
            service.setRetryPeriodMillis(RETRY_PERIOD_MILLIS);
            service.setJitterFactor(JITTER_FACTOR);
            service.setLeaseResourceType(type);

            if (rebalancing) {
                member = new RebalancingCamelClusterService(service, RETRY_PERIOD_MILLIS);
            } else {
                member = service;
            }

            try {
                context().addService(member);
            } catch (Exception ex) {
                throw new RuntimeCamelException(ex);
            }

            clusterServices.put(name, member);
        }

        LeaderRecorder recorder = new LeaderRecorder();
        try {
            member.getView(namespace).addEventListener(recorder);
        } catch (Exception ex) {
            throw new RuntimeCamelException(ex);
        }

        for (String pod : this.lockServers.keySet()) {
            connectPod(pod);
            connectSimulator(lockSimulator);
        }

        return recorder;
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }
}
