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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.apache.camel.component.file.cluster.FileLockClusterUtils.LOCKFILE_BUFFER_SIZE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileLockClusterUtilsTest {
    @Test
    void nullLeaderInfoIsStale() {
        assertTrue(FileLockClusterUtils.isLeaderStale(null, null, System.currentTimeMillis(), 5));
    }

    @Test
    void newHeartbeatNotStale() {
        String clusterMemberId = UUID.randomUUID().toString();
        FileLockClusterLeaderInfo previousClusterLeaderInfo = new FileLockClusterLeaderInfo(
                clusterMemberId,
                TimeUnit.SECONDS.toMillis(1),
                System.currentTimeMillis());

        FileLockClusterLeaderInfo latestClusterLeaderInfo = new FileLockClusterLeaderInfo(
                clusterMemberId,
                TimeUnit.SECONDS.toMillis(1),
                System.currentTimeMillis());

        assertFalse(
                FileLockClusterUtils.isLeaderStale(latestClusterLeaderInfo, previousClusterLeaderInfo,
                        System.currentTimeMillis(), 5));
    }

    @Test
    void sameHeartbeatIsStale() {
        String clusterMemberId = UUID.randomUUID().toString();
        long heartbeatMilliseconds = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(10);
        FileLockClusterLeaderInfo previousClusterLeaderInfo = new FileLockClusterLeaderInfo(
                clusterMemberId,
                TimeUnit.SECONDS.toMillis(1),
                heartbeatMilliseconds);

        FileLockClusterLeaderInfo latestClusterLeaderInfo = new FileLockClusterLeaderInfo(
                clusterMemberId,
                TimeUnit.SECONDS.toMillis(1),
                heartbeatMilliseconds);

        assertTrue(
                FileLockClusterUtils.isLeaderStale(latestClusterLeaderInfo, previousClusterLeaderInfo,
                        System.currentTimeMillis(), 3));
    }

    @Test
    void oldHeartbeatStale() {
        String clusterMemberId = UUID.randomUUID().toString();
        FileLockClusterLeaderInfo previousClusterLeaderInfo = new FileLockClusterLeaderInfo(
                clusterMemberId,
                TimeUnit.SECONDS.toMillis(1),
                System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(5));

        FileLockClusterLeaderInfo latestClusterLeaderInfo = new FileLockClusterLeaderInfo(
                clusterMemberId,
                TimeUnit.SECONDS.toMillis(1),
                System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(10));

        assertTrue(
                FileLockClusterUtils.isLeaderStale(latestClusterLeaderInfo, previousClusterLeaderInfo,
                        System.currentTimeMillis(), 3));
    }

    @Test
    void heartbeatExactlyAtThreshold() {
        int heartbeatMultiplier = 3;
        long now = System.currentTimeMillis();
        long updateInterval = TimeUnit.SECONDS.toMillis(1);
        long heartbeat = now - (updateInterval * heartbeatMultiplier);

        String clusterMemberId = UUID.randomUUID().toString();
        FileLockClusterLeaderInfo previousClusterLeaderInfo = new FileLockClusterLeaderInfo(
                clusterMemberId,
                TimeUnit.SECONDS.toMillis(1),
                heartbeat);

        FileLockClusterLeaderInfo latestClusterLeaderInfo = new FileLockClusterLeaderInfo(
                clusterMemberId,
                TimeUnit.SECONDS.toMillis(1),
                heartbeat);

        assertFalse(FileLockClusterUtils.isLeaderStale(latestClusterLeaderInfo, previousClusterLeaderInfo, now,
                heartbeatMultiplier));
    }

    @Test
    void leaderChangedNotStale() {
        FileLockClusterLeaderInfo previousClusterLeaderInfo = new FileLockClusterLeaderInfo(
                UUID.randomUUID().toString(),
                TimeUnit.SECONDS.toMillis(1),
                System.currentTimeMillis());

        FileLockClusterLeaderInfo latestClusterLeaderInfo = new FileLockClusterLeaderInfo(
                UUID.randomUUID().toString(),
                TimeUnit.SECONDS.toMillis(1),
                System.currentTimeMillis());

        assertFalse(
                FileLockClusterUtils.isLeaderStale(latestClusterLeaderInfo, previousClusterLeaderInfo,
                        System.currentTimeMillis(), 3));
    }

    @Test
    void expectedFileLockBufferSize() {
        // To catch cases where the lock file format is modified but the buffer size was not updated
        assertEquals(52, LOCKFILE_BUFFER_SIZE);
    }

    @Test
    void writeClusterLeaderInfoLockNullChannel() {
        assertThrows(NullPointerException.class, () -> {
            FileLockClusterUtils.writeClusterLeaderInfo(Paths.get("."), null, new FileLockClusterLeaderInfo("", 1L, 1L), true);
        });
    }

    @Test
    void writeClusterLeaderInfoWithNullData(@TempDir Path tempDir) {
        assertThrows(NullPointerException.class, () -> {
            try (RandomAccessFile raf = new RandomAccessFile(tempDir.resolve("lock").toFile(), "rw")) {
                FileLockClusterUtils.writeClusterLeaderInfo(Paths.get("."), raf.getChannel(), null, true);
            }
        });
    }

    @Test
    void writeClusterLeaderInfoClusterDataFileNotFound(@TempDir Path tempDir) {
        assertThrows(FileNotFoundException.class, () -> {
            try (RandomAccessFile raf = new RandomAccessFile(tempDir.resolve("leader.dat").toFile(), "rw")) {
                FileLockClusterLeaderInfo leaderInfo = new FileLockClusterLeaderInfo(UUID.randomUUID().toString(), 1L, 1L);
                FileLockClusterUtils.writeClusterLeaderInfo(Paths.get("/invalid/data/file"), raf.getChannel(), leaderInfo,
                        true);
            }
        });
    }

    @Test
    void writeClusterLeaderInfoData(@TempDir Path tempDir) throws IOException {
        Path clusterData = tempDir.resolve("leader.dat");
        try (RandomAccessFile raf = new RandomAccessFile(clusterData.toFile(), "rw")) {
            FileLockClusterLeaderInfo leaderInfo = new FileLockClusterLeaderInfo(UUID.randomUUID().toString(), 1L, 2L);
            FileLockClusterUtils.writeClusterLeaderInfo(clusterData, raf.getChannel(), leaderInfo, true);
            assertEquals(LOCKFILE_BUFFER_SIZE, Files.size(clusterData));
        }
    }

    @Test
    void readClusterLeaderInfoLockFileNotFound() throws Exception {
        assertNull(FileLockClusterUtils.readClusterLeaderInfo(Paths.get("/invalid/data/file")));
    }

    @Test
    void readClusterLeaderInfoLock(@TempDir Path tempDir) throws Exception {
        writeClusterLeaderInfoData(tempDir);

        Path lockFile = tempDir.resolve("leader.dat");
        FileLockClusterLeaderInfo clusterLeaderInfo = FileLockClusterUtils.readClusterLeaderInfo(lockFile);
        assertNotNull(clusterLeaderInfo);

        assertEquals(1L, clusterLeaderInfo.getHeartbeatUpdateIntervalMilliseconds());
        assertEquals(2L, clusterLeaderInfo.getHeartbeatMilliseconds());
        assertDoesNotThrow(() -> UUID.fromString(clusterLeaderInfo.getId()));
    }
}
