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
package org.apache.camel.component.kubernetes.cluster;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.fabric8.kubernetes.api.model.ConfigMapBuilder;

import org.apache.camel.component.kubernetes.KubernetesConfiguration;
import org.apache.camel.component.kubernetes.cluster.utils.ConfigMapLockSimulator;
import org.apache.camel.component.kubernetes.cluster.utils.LeaderRecorder;
import org.apache.camel.component.kubernetes.cluster.utils.LockTestServer;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Test leader election scenarios using a mock server.
 */
public class KubernetesClusterServiceTest extends CamelTestSupport {

    private static final int LEASE_TIME_MILLIS = 2000;
    private static final int RENEW_DEADLINE_MILLIS = 1000;
    private static final int RETRY_PERIOD_MILLIS = 200;
    private static final double JITTER_FACTOR = 1.1;

    private ConfigMapLockSimulator lockSimulator;

    private Map<String, LockTestServer> lockServers;

    @Before
    public void prepareLock() {
        this.lockSimulator = new ConfigMapLockSimulator("leaders");
        this.lockServers = new HashMap<>();
    }

    @After
    public void shutdownLock() {
        for (LockTestServer server : this.lockServers.values()) {
            try {
                server.destroy();
            } catch (Exception e) {
                // can happen in case of delay
            }
        }
    }

    @Test
    public void testSimpleLeaderElection() throws Exception {
        LeaderRecorder mypod1 = addMember("mypod1");
        LeaderRecorder mypod2 = addMember("mypod2");
        context.start();

        mypod1.waitForAnyLeader(2, TimeUnit.SECONDS);
        mypod2.waitForAnyLeader(2, TimeUnit.SECONDS);

        String leader = mypod1.getCurrentLeader();
        assertNotNull(leader);
        assertTrue(leader.startsWith("mypod"));
        assertEquals("Leaders should be equals", mypod2.getCurrentLeader(), leader);
    }

    @Test
    public void testMultipleMembersLeaderElection() throws Exception {
        int number = 5;
        List<LeaderRecorder> members = IntStream.range(0, number).mapToObj(i -> addMember("mypod" + i)).collect(Collectors.toList());
        context.start();

        for (LeaderRecorder member : members) {
            member.waitForAnyLeader(2, TimeUnit.SECONDS);
        }

        Set<String> leaders = members.stream().map(LeaderRecorder::getCurrentLeader).collect(Collectors.toSet());
        assertEquals(1, leaders.size());
        String leader = leaders.iterator().next();
        assertTrue(leader.startsWith("mypod"));
    }

    @Test
    public void testSimpleLeaderElectionWithExistingConfigMap() throws Exception {
        lockSimulator.setConfigMap(new ConfigMapBuilder()
                .withNewMetadata()
                .withName("leaders")
                .and().build(), true);

        LeaderRecorder mypod1 = addMember("mypod1");
        LeaderRecorder mypod2 = addMember("mypod2");
        context.start();

        mypod1.waitForAnyLeader(2, TimeUnit.SECONDS);
        mypod2.waitForAnyLeader(2, TimeUnit.SECONDS);

        String leader = mypod1.getCurrentLeader();
        assertTrue(leader.startsWith("mypod"));
        assertEquals("Leaders should be equals", mypod2.getCurrentLeader(), leader);
    }

    @Test
    public void testLeadershipLoss() throws Exception {
        LeaderRecorder mypod1 = addMember("mypod1");
        LeaderRecorder mypod2 = addMember("mypod2");
        context.start();

        mypod1.waitForAnyLeader(2, TimeUnit.SECONDS);
        mypod2.waitForAnyLeader(2, TimeUnit.SECONDS);

        String firstLeader = mypod1.getCurrentLeader();

        LeaderRecorder formerLeaderRecorder = firstLeader.equals("mypod1") ? mypod1 : mypod2;
        LeaderRecorder formerLoserRecorder = firstLeader.equals("mypod1") ? mypod2 : mypod1;

        refuseRequestsFromPod(firstLeader);
        disconnectPod(firstLeader);

        formerLeaderRecorder.waitForALeaderChange(7, TimeUnit.SECONDS);
        formerLoserRecorder.waitForANewLeader(firstLeader, 7, TimeUnit.SECONDS);

        String secondLeader = formerLoserRecorder.getCurrentLeader();
        assertNotEquals("The firstLeader should be different from the new one", firstLeader, secondLeader);

        Long lossTimestamp = formerLeaderRecorder.getLastTimeOf(l -> l == null);
        Long gainTimestamp = formerLoserRecorder.getLastTimeOf(secondLeader::equals);

        assertTrue("At least half distance must elapse from leadership loss and regain (see renewDeadlineSeconds)", gainTimestamp >= lossTimestamp + (LEASE_TIME_MILLIS - RENEW_DEADLINE_MILLIS) / 2);
        checkLeadershipChangeDistance((LEASE_TIME_MILLIS - RENEW_DEADLINE_MILLIS) / 2, TimeUnit.MILLISECONDS, mypod1, mypod2);
    }

    @Test
    public void testSlowLeaderLosingLeadershipOnlyInternally() throws Exception {
        LeaderRecorder mypod1 = addMember("mypod1");
        LeaderRecorder mypod2 = addMember("mypod2");
        context.start();

        mypod1.waitForAnyLeader(2, TimeUnit.SECONDS);
        mypod2.waitForAnyLeader(2, TimeUnit.SECONDS);

        String firstLeader = mypod1.getCurrentLeader();

        LeaderRecorder formerLeaderRecorder = firstLeader.equals("mypod1") ? mypod1 : mypod2;
        LeaderRecorder formerLoserRecorder = firstLeader.equals("mypod1") ? mypod2 : mypod1;

        delayRequestsFromPod(firstLeader, 10, TimeUnit.SECONDS);

        Thread.sleep(LEASE_TIME_MILLIS);
        assertNull(formerLeaderRecorder.getCurrentLeader());
        assertEquals(firstLeader, formerLoserRecorder.getCurrentLeader());
    }

    @Test
    public void testRecoveryAfterFailure() throws Exception {
        LeaderRecorder mypod1 = addMember("mypod1");
        LeaderRecorder mypod2 = addMember("mypod2");
        context.start();

        mypod1.waitForAnyLeader(2, TimeUnit.SECONDS);
        mypod2.waitForAnyLeader(2, TimeUnit.SECONDS);

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
    public void testSharedConfigMap() throws Exception {
        LeaderRecorder a1 = addMember("a1");
        LeaderRecorder a2 = addMember("a2");
        LeaderRecorder b1 = addMember("b1", "app2");
        LeaderRecorder b2 = addMember("b2", "app2");
        context.start();

        a1.waitForAnyLeader(2, TimeUnit.SECONDS);
        a2.waitForAnyLeader(2, TimeUnit.SECONDS);
        b1.waitForAnyLeader(2, TimeUnit.SECONDS);
        b2.waitForAnyLeader(2, TimeUnit.SECONDS);

        assertNotNull(a1.getCurrentLeader());
        assertTrue(a1.getCurrentLeader().startsWith("a"));
        assertEquals(a1.getCurrentLeader(), a2.getCurrentLeader());
        assertNotNull(b1.getCurrentLeader());
        assertTrue(b1.getCurrentLeader().startsWith("b"));
        assertEquals(b1.getCurrentLeader(), b2.getCurrentLeader());

        assertNotEquals(a1.getCurrentLeader(), b2.getCurrentLeader());
    }

    private void delayRequestsFromPod(String pod, long delay, TimeUnit unit) {
        this.lockServers.get(pod).setDelayRequests(TimeUnit.MILLISECONDS.convert(delay, unit));
    }

    private void refuseRequestsFromPod(String pod) {
        this.lockServers.get(pod).setRefuseRequests(true);
    }

    private void allowRequestsFromPod(String pod) {
        this.lockServers.get(pod).setRefuseRequests(false);
    }

    private void disconnectPod(String pod) {
        for (LockTestServer server : this.lockServers.values()) {
            server.removePod(pod);
        }
    }

    private void connectPod(String pod) {
        for (LockTestServer server : this.lockServers.values()) {
            server.addPod(pod);
        }
    }

    private void checkLeadershipChangeDistance(long minimum, TimeUnit unit, LeaderRecorder... recorders) {
        List<LeaderRecorder.LeadershipInfo> infos = Arrays.stream(recorders)
                .flatMap(lr -> lr.getLeadershipInfo().stream())
                .sorted((li1, li2) -> Long.compare(li1.getChangeTimestamp(), li2.getChangeTimestamp()))
                .collect(Collectors.toList());

        LeaderRecorder.LeadershipInfo currentLeaderLastSeen = null;
        for (LeaderRecorder.LeadershipInfo info : infos) {
            if (currentLeaderLastSeen == null || currentLeaderLastSeen.getLeader() == null) {
                currentLeaderLastSeen = info;
            } else {
                if (Objects.equals(info.getLeader(), currentLeaderLastSeen.getLeader())) {
                    currentLeaderLastSeen = info;
                } else if (info.getLeader() != null && !info.getLeader().equals(currentLeaderLastSeen.getLeader())) {
                    // switch
                    long delay = info.getChangeTimestamp() - currentLeaderLastSeen.getChangeTimestamp();
                    assertTrue("Lease time not elapsed between switch, minimum=" + TimeUnit.MILLISECONDS.convert(minimum, unit) + ", found=" + delay, delay >= TimeUnit.MILLISECONDS.convert(minimum,
                            unit));
                    currentLeaderLastSeen = info;
                }
            }
        }
    }

    private LeaderRecorder addMember(String name) {
        return addMember(name, "app");
    }

    private LeaderRecorder addMember(String name, String namespace) {
        assertNull(this.lockServers.get(name));

        LockTestServer lockServer = new LockTestServer(lockSimulator);
        this.lockServers.put(name, lockServer);

        KubernetesConfiguration configuration = new KubernetesConfiguration();
        configuration.setKubernetesClient(lockServer.createClient());

        KubernetesClusterService member = new KubernetesClusterService(configuration);
        member.setKubernetesNamespace("test");
        member.setPodName(name);
        member.setLeaseDurationMillis(LEASE_TIME_MILLIS);
        member.setRenewDeadlineMillis(RENEW_DEADLINE_MILLIS);
        member.setRetryPeriodMillis(RETRY_PERIOD_MILLIS);
        member.setJitterFactor(JITTER_FACTOR);

        LeaderRecorder recorder = new LeaderRecorder();
        try {
            context().addService(member);
            member.getView(namespace).addEventListener(recorder);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        for (String pod : this.lockServers.keySet()) {
            connectPod(pod);
        }
        return recorder;
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }
}
