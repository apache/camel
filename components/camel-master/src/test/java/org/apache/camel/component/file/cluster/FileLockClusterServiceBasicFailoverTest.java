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
package org.apache.camel.component.file.cluster;

import java.nio.file.Files;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.component.mock.MockEndpoint;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileLockClusterServiceBasicFailoverTest extends FileLockClusterServiceTestBase {
    @Test
    void singleClusterMemberLeaderElection() throws Exception {
        try (CamelContext clusterLeader = createCamelContext()) {
            MockEndpoint mockEndpoint = clusterLeader.getEndpoint("mock:result", MockEndpoint.class);
            mockEndpoint.expectedMessageCount(5);

            clusterLeader.start();

            mockEndpoint.assertIsSatisfied();

            Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                assertTrue(Files.exists(lockFile));
                assertTrue(Files.exists(dataFile));

                FileLockClusterLeaderInfo clusterLeaderInfo = FileLockClusterUtils.readClusterLeaderInfo(dataFile);
                assertNotNull(clusterLeaderInfo);

                String leaderId = clusterLeaderInfo.getId();
                assertNotNull(leaderId);
                assertDoesNotThrow(() -> UUID.fromString(leaderId));
            });
        }

        assertEquals(0, Files.size(dataFile));
    }

    @Test
    void multiClusterMemberLeaderElection() throws Exception {
        CamelContext clusterLeader = createCamelContext();

        ClusterConfig followerConfig = new ClusterConfig();
        followerConfig.setAcquireLockDelay(2);
        CamelContext clusterFollower = createCamelContext(followerConfig);

        try {
            MockEndpoint mockEndpointClustered = clusterLeader.getEndpoint("mock:result", MockEndpoint.class);
            mockEndpointClustered.expectedMessageCount(5);

            clusterLeader.start();
            clusterFollower.start();

            mockEndpointClustered.assertIsSatisfied();

            Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                assertTrue(Files.exists(lockFile));
                assertTrue(Files.exists(dataFile));
                assertTrue(getClusterMember(clusterLeader).isLeader());

                FileLockClusterLeaderInfo clusterLeaderInfo = FileLockClusterUtils.readClusterLeaderInfo(dataFile);
                assertNotNull(clusterLeaderInfo);

                String leaderId = clusterLeaderInfo.getId();
                assertNotNull(leaderId);
                assertDoesNotThrow(() -> UUID.fromString(leaderId));
            });

            // Wait enough time for the follower to have run its lock acquisition scheduled task
            Thread.sleep(followerConfig.getStartupDelayWithOffsetMillis());

            // The follower should not have produced any messages
            MockEndpoint mockEndpointFollower = clusterFollower.getEndpoint("mock:result", MockEndpoint.class);
            assertTrue(mockEndpointFollower.getExchanges().isEmpty());
        } finally {
            clusterFollower.stop();
            clusterLeader.stop();
        }

        assertEquals(0, Files.size(dataFile));
    }

    @Test
    void clusterFailoverWhenLeaderCamelContextStopped() throws Exception {
        CamelContext clusterLeader = createCamelContext();

        ClusterConfig followerConfig = new ClusterConfig();
        followerConfig.setAcquireLockDelay(2);
        CamelContext clusterFollower = createCamelContext(followerConfig);

        try {
            MockEndpoint mockEndpointClustered = clusterLeader.getEndpoint("mock:result", MockEndpoint.class);
            mockEndpointClustered.expectedMessageCount(5);

            clusterLeader.start();
            clusterFollower.start();

            mockEndpointClustered.assertIsSatisfied();

            AtomicReference<String> leaderId = new AtomicReference<>();
            Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                assertTrue(Files.exists(lockFile));
                assertTrue(Files.exists(dataFile));
                assertTrue(getClusterMember(clusterLeader).isLeader());

                FileLockClusterLeaderInfo clusterLeaderInfo = FileLockClusterUtils.readClusterLeaderInfo(dataFile);
                assertNotNull(clusterLeaderInfo);

                assertNotNull(clusterLeaderInfo.getId());
                assertDoesNotThrow(() -> UUID.fromString(clusterLeaderInfo.getId()));
                leaderId.set(clusterLeaderInfo.getId());
            });

            // Wait enough time for the follower to have run its lock acquisition scheduled task
            Thread.sleep(followerConfig.getStartupDelayWithOffsetMillis());

            // The follower should not have produced any messages
            MockEndpoint mockEndpointFollower = clusterFollower.getEndpoint("mock:result", MockEndpoint.class);
            assertTrue(mockEndpointFollower.getExchanges().isEmpty());

            // Stop the cluster leader
            clusterLeader.stop();

            // Verify the follower was elected as the new cluster leader
            Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                assertTrue(getClusterMember(clusterFollower).isLeader());

                FileLockClusterLeaderInfo updatedClusterLeaderInfo = FileLockClusterUtils.readClusterLeaderInfo(dataFile);
                assertNotNull(updatedClusterLeaderInfo);

                String newLeaderId = updatedClusterLeaderInfo.getId();
                assertNotNull(newLeaderId);
                assertDoesNotThrow(() -> UUID.fromString(newLeaderId));
                assertNotEquals(leaderId.get(), newLeaderId);
                assertEquals(5, mockEndpointFollower.getExchanges().size());
            });
        } finally {
            clusterFollower.stop();
        }

        assertEquals(0, Files.size(dataFile));
    }

    @Test
    void clusterFailoverWithBackoffWhenLeaderCamelContextStopped() throws Exception {
        CamelContext clusterLeader = createCamelContext();

        ClusterConfig followerConfig = new ClusterConfig();
        followerConfig.setAcquireLockDelay(2);
        followerConfig.setAcquireLeadershipBackoff(5);
        CamelContext clusterFollower = createCamelContext(followerConfig);

        try {
            MockEndpoint mockEndpointClustered = clusterLeader.getEndpoint("mock:result", MockEndpoint.class);
            mockEndpointClustered.expectedMessageCount(5);

            clusterLeader.start();
            clusterFollower.start();

            mockEndpointClustered.assertIsSatisfied();

            AtomicReference<String> leaderId = new AtomicReference<>();
            Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                assertTrue(Files.exists(lockFile));
                assertTrue(Files.exists(dataFile));
                assertTrue(getClusterMember(clusterLeader).isLeader());

                FileLockClusterLeaderInfo clusterLeaderInfo = FileLockClusterUtils.readClusterLeaderInfo(dataFile);
                assertNotNull(clusterLeaderInfo);

                assertNotNull(clusterLeaderInfo.getId());
                assertDoesNotThrow(() -> UUID.fromString(clusterLeaderInfo.getId()));
                leaderId.set(clusterLeaderInfo.getId());
            });

            // Wait enough time for the follower to have run its lock acquisition scheduled task
            Thread.sleep(followerConfig.getStartupDelayWithOffsetMillis());

            // The follower should not have produced any messages
            MockEndpoint mockEndpointFollower = clusterFollower.getEndpoint("mock:result", MockEndpoint.class);
            assertTrue(mockEndpointFollower.getExchanges().isEmpty());

            // Stop the cluster leader
            clusterLeader.stop();

            // Backoff is configured so the follower should not claim leadership within most of that period
            Awaitility.await().during(Duration.ofMillis(4900)).untilAsserted(() -> {
                assertFalse(getClusterMember(clusterFollower).isLeader());
            });

            // Verify the follower was elected as the new cluster leader
            Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                FileLockClusterLeaderInfo updatedClusterLeaderInfo = FileLockClusterUtils.readClusterLeaderInfo(dataFile);
                assertNotNull(updatedClusterLeaderInfo);

                String newLeaderId = updatedClusterLeaderInfo.getId();
                assertNotNull(newLeaderId);
                assertDoesNotThrow(() -> UUID.fromString(newLeaderId));
                assertNotEquals(leaderId.get(), newLeaderId);
                assertEquals(5, mockEndpointFollower.getExchanges().size());
            });
        } finally {
            clusterFollower.stop();
        }

        assertEquals(0, Files.size(dataFile));
    }

    @Test
    void singleClusterMemberRecoversLeadershipIfUUIDRemovedFromLockFile() throws Exception {
        try (CamelContext clusterLeader = createCamelContext()) {
            MockEndpoint mockEndpoint = clusterLeader.getEndpoint("mock:result", MockEndpoint.class);
            mockEndpoint.expectedMinimumMessageCount(1);

            clusterLeader.start();

            mockEndpoint.assertIsSatisfied();

            AtomicReference<String> leaderId = new AtomicReference<>();
            Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                assertTrue(Files.exists(lockFile));
                assertTrue(Files.exists(dataFile));
                assertTrue(getClusterMember(clusterLeader).isLeader());

                FileLockClusterLeaderInfo clusterLeaderInfo = FileLockClusterUtils.readClusterLeaderInfo(dataFile);
                assertNotNull(clusterLeaderInfo);

                assertNotNull(clusterLeaderInfo.getId());
                assertDoesNotThrow(() -> UUID.fromString(clusterLeaderInfo.getId()));
                leaderId.set(clusterLeaderInfo.getId());
            });

            // Truncate the lock file
            Files.write(dataFile, new byte[0]);
            Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                // Leadership should be lost
                assertFalse(getClusterMember(clusterLeader).isLeader());
            });

            mockEndpoint.reset();
            mockEndpoint.expectedMinimumMessageCount(1);

            // Await recovery
            Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                assertTrue(getClusterMember(clusterLeader).isLeader());

                FileLockClusterLeaderInfo recoveredClusterLeaderInfo = FileLockClusterUtils.readClusterLeaderInfo(dataFile);
                assertNotNull(recoveredClusterLeaderInfo);

                String recoveredLeaderId = recoveredClusterLeaderInfo.getId();
                assertNotNull(recoveredLeaderId);
                assertDoesNotThrow(() -> UUID.fromString(recoveredLeaderId));
                assertEquals(leaderId.get(), recoveredLeaderId);

                mockEndpoint.assertIsSatisfied();
            });
        }

        assertEquals(0, Files.size(dataFile));
    }

    @Test
    void negativeHeartbeatTimeoutMultiplierThrowsException() throws Exception {
        ClusterConfig config = new ClusterConfig();
        config.setHeartbeatTimeoutMultiplier(-1);

        Exception exception = assertThrows(Exception.class, () -> {
            try (CamelContext camelContext = createCamelContext(config)) {
                camelContext.start();
            }
        });
        assertIsInstanceOf(IllegalArgumentException.class, exception.getCause());
    }

    @Test
    void zeroHeartbeatTimeoutMultiplierThrowsException() throws Exception {
        ClusterConfig config = new ClusterConfig();
        config.setHeartbeatTimeoutMultiplier(0);

        Exception exception = assertThrows(Exception.class, () -> {
            try (CamelContext camelContext = createCamelContext(config)) {
                camelContext.start();
            }
        });
        assertIsInstanceOf(IllegalArgumentException.class, exception.getCause());
    }

    @Test
    void negativeAcquireLeadershipBackoffThrowsException() throws Exception {
        ClusterConfig config = new ClusterConfig();
        config.setAcquireLeadershipBackoff(-1);

        Exception exception = assertThrows(Exception.class, () -> {
            try (CamelContext camelContext = createCamelContext(config)) {
                camelContext.start();
            }
        });
        assertIsInstanceOf(IllegalArgumentException.class, exception.getCause());
    }
}
