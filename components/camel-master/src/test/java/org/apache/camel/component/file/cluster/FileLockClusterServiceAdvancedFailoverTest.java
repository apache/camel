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

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.apache.camel.CamelContext;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.FileUtil;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(value = OS.WINDOWS, disabledReason = "Platform file locking impl prevents cluster data move / deletion")
class FileLockClusterServiceAdvancedFailoverTest extends FileLockClusterServiceTestBase {
    @Test
    void singleClusterMemberRecoversLeadershipIfLockFileDeleted() throws Exception {
        ClusterConfig config = new ClusterConfig();
        config.setTimerRepeatCount(-1);

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

            // Delete the lock file
            Files.deleteIfExists(lockFile);

            mockEndpoint.reset();
            mockEndpoint.expectedMinimumMessageCount(1);

            // Wait for leadership to be relinquished
            Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                assertFalse(getClusterMember(clusterLeader).isLeader());
            });

            // Leadership should be retained
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

        String leaderId = Files.readString(dataFile);
        assertTrue(leaderId.isEmpty());
    }

    @Test
    void multipleClusterMembersReelectLeaderIfLockFileDeleted() throws Exception {
        ClusterConfig leaderConfig = new ClusterConfig();
        leaderConfig.setTimerRepeatCount(-1);

        CamelContext clusterLeader = createCamelContext(leaderConfig);

        ClusterConfig followerConfig = new ClusterConfig();
        followerConfig.setTimerRepeatCount(-1);
        followerConfig.setAcquireLockDelay(2);

        CamelContext clusterFollower = createCamelContext(followerConfig);

        try {
            MockEndpoint mockEndpointLeader = clusterLeader.getEndpoint("mock:result", MockEndpoint.class);
            mockEndpointLeader.expectedMinimumMessageCount(1);

            clusterLeader.start();
            clusterFollower.start();

            mockEndpointLeader.assertIsSatisfied();

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

            mockEndpointLeader.reset();
            mockEndpointLeader.expectedMinimumMessageCount(1);

            // Delete the lock file
            Files.deleteIfExists(lockFile);

            // Wait for leadership to be relinquished
            Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                assertFalse(getClusterMember(clusterLeader).isLeader());
            });

            // Wait for leadership to be gained by one of the members
            CamelContext oldLeader = clusterLeader;
            Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                boolean newLeaderElected = false;

                // Original cluster leader regained leadership
                if (getClusterMember(oldLeader).isLeader()) {
                    newLeaderElected = true;
                    mockEndpointLeader.assertIsSatisfied();
                }

                // A different cluster member gained leadership
                if (getClusterMember(clusterFollower).isLeader()) {
                    newLeaderElected = true;
                    mockEndpointFollower.assertIsSatisfied();
                }

                assertTrue(newLeaderElected);
            });
        } finally {
            clusterLeader.stop();
            clusterFollower.stop();
        }
    }

    @Test
    void multipleClusterMembersReelectLeaderIfClusterDataDirectoryDeleted() throws Exception {
        ClusterConfig leaderConfig = new ClusterConfig();
        leaderConfig.setTimerRepeatCount(-1);

        CamelContext clusterLeader = createCamelContext(leaderConfig);

        ClusterConfig followerConfig = new ClusterConfig();
        followerConfig.setTimerRepeatCount(-1);
        followerConfig.setAcquireLockDelay(2);

        CamelContext clusterFollower = createCamelContext(followerConfig);

        try {
            MockEndpoint mockEndpointLeader = clusterLeader.getEndpoint("mock:result", MockEndpoint.class);
            mockEndpointLeader.expectedMinimumMessageCount(1);

            clusterLeader.start();
            clusterFollower.start();

            mockEndpointLeader.assertIsSatisfied();

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

            mockEndpointLeader.reset();
            mockEndpointLeader.expectedMinimumMessageCount(1);

            // Delete the cluster data directory
            FileUtil.removeDir(clusterDir.toFile());

            // Wait for leadership to be relinquished
            Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                assertFalse(getClusterMember(clusterLeader).isLeader());
            });

            // Wait for leadership to be gained by one of the members
            CamelContext oldLeader = clusterLeader;
            Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                boolean newLeaderElected = false;

                // Original cluster leader regained leadership
                if (getClusterMember(oldLeader).isLeader()) {
                    newLeaderElected = true;
                    mockEndpointLeader.assertIsSatisfied();
                }

                // A different cluster member gained leadership
                if (getClusterMember(clusterFollower).isLeader()) {
                    newLeaderElected = true;
                    mockEndpointFollower.assertIsSatisfied();
                }

                assertTrue(newLeaderElected);
            });
        } finally {
            clusterLeader.stop();
            clusterFollower.stop();
        }
    }

    @Test
    void notStaleLockFileForRestoredFileSystemElectsOriginalLeader(@TempDir Path clusterMovedLocation) throws Exception {
        ClusterConfig leaderConfig = new ClusterConfig();
        leaderConfig.setTimerRepeatCount(-1);

        CamelContext clusterLeader = createCamelContext(leaderConfig);

        ClusterConfig followerConfig = new ClusterConfig();
        followerConfig.setTimerRepeatCount(-1);
        followerConfig.setAcquireLockDelay(2);

        CamelContext clusterFollower = createCamelContext(followerConfig);

        try {
            MockEndpoint mockEndpointLeader = clusterLeader.getEndpoint("mock:result", MockEndpoint.class);
            mockEndpointLeader.expectedMessageCount(5);

            clusterLeader.start();
            clusterFollower.start();

            mockEndpointLeader.assertIsSatisfied();

            AtomicReference<FileLockClusterLeaderInfo> leaderInfo = new AtomicReference<>();
            Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                assertTrue(Files.exists(lockFile));
                assertTrue(Files.exists(dataFile));
                assertTrue(getClusterMember(clusterLeader).isLeader());

                FileLockClusterLeaderInfo clusterLeaderInfo = FileLockClusterUtils.readClusterLeaderInfo(dataFile);
                assertNotNull(clusterLeaderInfo);
                leaderInfo.set(clusterLeaderInfo);

                String leaderId = clusterLeaderInfo.getId();
                assertNotNull(leaderId);
                assertDoesNotThrow(() -> UUID.fromString(leaderId));
            });

            // Wait enough time for the follower to have run its lock acquisition scheduled task
            Thread.sleep(followerConfig.getStartupDelayWithOffsetMillis());

            // The follower should not have produced any messages
            MockEndpoint mockEndpointFollower = clusterFollower.getEndpoint("mock:result", MockEndpoint.class);
            assertTrue(mockEndpointFollower.getExchanges().isEmpty());

            mockEndpointLeader.reset();
            mockEndpointLeader.expectedMinimumMessageCount(1);

            // Simulate the file system becoming detached by moving the cluster data directory
            Files.move(clusterDir, clusterMovedLocation, StandardCopyOption.REPLACE_EXISTING);

            // Simulate reattaching the file system by moving the cluster directory back to the original location
            try (Stream<Path> stream = Files.walk(clusterMovedLocation)) {
                stream.forEach(path -> {
                    try {
                        Path destination = clusterDir.resolve(clusterMovedLocation.relativize(path));
                        if (Files.isDirectory(path)) {
                            Files.createDirectories(destination);
                        } else {
                            Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }

            FileLockClusterLeaderInfo updatedInfo
                    = new FileLockClusterLeaderInfo(
                            leaderInfo.get().getId(), TimeUnit.MILLISECONDS.toMillis(2), System.currentTimeMillis());
            Path data = clusterMovedLocation.resolve(NAMESPACE + ".data");
            try (RandomAccessFile file = new RandomAccessFile(data.toFile(), "rw")) {
                FileLockClusterUtils.writeClusterLeaderInfo(data, file.getChannel(), updatedInfo,
                        true);
            }

            // Since the lock file is not considered 'stale', the original leader should resume leadership
            Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                assertTrue(getClusterMember(clusterLeader).isLeader());
                mockEndpointLeader.assertIsSatisfied();
            });

            assertTrue(mockEndpointFollower.getExchanges().isEmpty());
        } finally {
            clusterLeader.stop();
            clusterFollower.stop();
        }
    }

    @Test
    void staleLockFileForRestoredFileSystemElectsNewLeader(@TempDir Path clusterMovedLocation) throws Exception {
        ClusterConfig leaderConfig = new ClusterConfig();
        leaderConfig.setTimerRepeatCount(-1);

        CamelContext clusterLeader = createCamelContext(leaderConfig);

        ClusterConfig followerConfig = new ClusterConfig();
        followerConfig.setTimerRepeatCount(-1);
        followerConfig.setAcquireLockDelay(2);

        CamelContext clusterFollower = createCamelContext(followerConfig);

        try {
            MockEndpoint mockEndpointLeader = clusterLeader.getEndpoint("mock:result", MockEndpoint.class);
            mockEndpointLeader.expectedMessageCount(5);

            clusterLeader.start();
            clusterFollower.start();

            mockEndpointLeader.assertIsSatisfied();

            AtomicReference<FileLockClusterLeaderInfo> leaderInfo = new AtomicReference<>();
            Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                assertTrue(Files.exists(lockFile));
                assertTrue(Files.exists(dataFile));
                assertTrue(getClusterMember(clusterLeader).isLeader());

                FileLockClusterLeaderInfo clusterLeaderInfo = FileLockClusterUtils.readClusterLeaderInfo(dataFile);
                assertNotNull(clusterLeaderInfo);
                leaderInfo.set(clusterLeaderInfo);

                String leaderId = clusterLeaderInfo.getId();
                assertNotNull(leaderId);
                assertDoesNotThrow(() -> UUID.fromString(leaderId));
            });

            // Wait enough time for the follower to have run its lock acquisition scheduled task
            Thread.sleep(followerConfig.getStartupDelayWithOffsetMillis());

            // The follower should not have produced any messages
            MockEndpoint mockEndpointFollower = clusterFollower.getEndpoint("mock:result", MockEndpoint.class);
            assertTrue(mockEndpointFollower.getExchanges().isEmpty());

            mockEndpointLeader.reset();
            mockEndpointLeader.expectedMinimumMessageCount(1);

            // Simulate the file system becoming detached by moving the lock directory
            Files.move(clusterDir, clusterMovedLocation, StandardCopyOption.REPLACE_EXISTING);

            // Wait for leadership to be relinquished
            Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                assertFalse(getClusterMember(clusterLeader).isLeader());
            });

            // Stop the cluster leader to simulate it going 'offline' while the lock file system is detached
            clusterLeader.stop();

            // Make the cluster data file appear stale (i.e. not updated within acceptable bounds)
            long staleHeartbeatTimestamp = leaderInfo.get().getHeartbeatMilliseconds() - TimeUnit.SECONDS.toMillis(100);

            FileLockClusterLeaderInfo updatedInfo
                    = new FileLockClusterLeaderInfo(
                            leaderInfo.get().getId(), TimeUnit.MILLISECONDS.toMillis(2), staleHeartbeatTimestamp);
            Path data = clusterMovedLocation.resolve(NAMESPACE + ".data");
            try (RandomAccessFile file = new RandomAccessFile(data.toFile(), "rw")) {
                FileLockClusterUtils.writeClusterLeaderInfo(data, file.getChannel(), updatedInfo,
                        true);
            }

            // Simulate reattaching the file system by moving the cluster directory back to the original location
            try (Stream<Path> stream = Files.walk(clusterMovedLocation)) {
                stream.forEach(path -> {
                    try {
                        Path destination = clusterDir.resolve(clusterMovedLocation.relativize(path));
                        if (Files.isDirectory(path)) {
                            Files.createDirectories(destination);
                        } else {
                            Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }

            mockEndpointFollower.expectedMinimumMessageCount(1);

            // Since the lock file is considered 'stale', the follower should be elected the leader
            Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                assertTrue(getClusterMember(clusterFollower).isLeader());
                mockEndpointFollower.assertIsSatisfied();
            });
        } finally {
            clusterFollower.stop();
        }
    }
}
