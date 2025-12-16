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
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Miscellaneous utility methods for managing the file lock cluster state.
 */
final class FileLockClusterUtils {
    /**
     * Length of byte[] obtained from java.util.UUID.
     */
    static final int UUID_BYTE_LENGTH = 36;
    /**
     * The lock file buffer capacity when writing data for the cluster leader.
     * <ul>
     * <li>Cluster leader ID (UUID String) - 36 bytes</li>
     * <li>Cluster leader heartbeat timestamp (long) - 8 bytes</li>
     * <li>Cluster leader update interval (long) - 8 bytes</li>
     * </ul>
     */
    static final int LOCKFILE_BUFFER_SIZE = UUID_BYTE_LENGTH + 2 * Long.BYTES;

    private FileLockClusterUtils() {
        // Utility class
    }

    /**
     * Writes information about the state of the cluster leader to the lock file.
     *
     * @param  leaderDataPath    The path to the lock file
     * @param  channel           The file channel to write to
     * @param  clusterLeaderInfo The {@link FileLockClusterLeaderInfo} instance where the cluster leader state is held
     * @param  forceMetaData     Whether to force changes to both the file content and metadata
     * @throws IOException       If the lock file is missing or writing data failed
     */
    static void writeClusterLeaderInfo(
            Path leaderDataPath,
            FileChannel channel,
            FileLockClusterLeaderInfo clusterLeaderInfo,
            boolean forceMetaData)
            throws IOException {

        Objects.requireNonNull(channel, "channel cannot be null");
        Objects.requireNonNull(clusterLeaderInfo, "clusterLeaderInfo cannot be null");

        if (!Files.exists(leaderDataPath)) {
            throw new FileNotFoundException("Cluster leader data file " + leaderDataPath + " not found");
        }

        String uuidStr = clusterLeaderInfo.getId();
        byte[] uuidBytes = uuidStr.getBytes(StandardCharsets.UTF_8);

        ByteBuffer buf = ByteBuffer.allocate(LOCKFILE_BUFFER_SIZE);
        buf.put(uuidBytes);
        buf.putLong(clusterLeaderInfo.getHeartbeatUpdateIntervalMilliseconds());
        buf.putLong(clusterLeaderInfo.getHeartbeatMilliseconds());
        buf.flip();

        if (forceMetaData) {
            channel.truncate(0);
        }

        channel.position(0);
        while (buf.hasRemaining()) {
            channel.write(buf);
        }
        channel.force(forceMetaData);
    }

    /**
     * Reads information about the state of the cluster leader from the lock file.
     *
     * @param  leaderDataPath The path to the lock file
     * @return                {@link FileLockClusterLeaderInfo} instance representing the state of the cluster leader.
     *                        {@code null} if the lock file does not exist or reading the file content is in an
     *                        inconsistent state
     * @throws IOException    If reading the lock file failed
     */
    static FileLockClusterLeaderInfo readClusterLeaderInfo(Path leaderDataPath) throws IOException {
        try {
            byte[] bytes = Files.readAllBytes(leaderDataPath);

            if (bytes.length < LOCKFILE_BUFFER_SIZE) {
                // Data is incomplete or in a transient / corrupt state
                return null;
            }

            // Parse the cluster leader data
            ByteBuffer buf = ByteBuffer.wrap(bytes);
            byte[] uuidBytes = new byte[UUID_BYTE_LENGTH];
            buf.get(uuidBytes);

            String uuidStr = new String(uuidBytes, StandardCharsets.UTF_8);
            long intervalMillis = buf.getLong();
            long lastHeartbeat = buf.getLong();

            return new FileLockClusterLeaderInfo(uuidStr, intervalMillis, lastHeartbeat);
        } catch (NoSuchFileException e) {
            // Handle NoSuchFileException to give the ClusterView a chance to recreate the leadership data
            return null;
        }
    }

    /**
     * Determines whether the current cluster leader is stale. Typically, when the leader has not updated the cluster
     * lock file within acceptable bounds.
     *
     * @param  latestClusterLeaderInfo   The {@link FileLockClusterLeaderInfo} instance representing the latest cluster
     *                                   leader state
     * @param  previousClusterLeaderInfo The {@link FileLockClusterLeaderInfo} instance representing the previously
     *                                   recorded cluster leader state
     * @param  currentTimeMillis         The current time in milliseconds, as returned by
     *                                   {@link System#currentTimeMillis()} is held
     * @return                           {@code true} if the leader is considered stale. {@code false} if the leader is
     *                                   still active
     */
    static boolean isLeaderStale(
            FileLockClusterLeaderInfo latestClusterLeaderInfo,
            FileLockClusterLeaderInfo previousClusterLeaderInfo,
            long currentTimeMillis,
            int heartbeatTimeoutMultiplier) {

        if (latestClusterLeaderInfo == null) {
            return true;
        }

        // Cluster leader changed since last observation so assume not stale
        if (!latestClusterLeaderInfo.equals(previousClusterLeaderInfo)) {
            return false;
        }

        final long latestHeartbeat = latestClusterLeaderInfo.getHeartbeatMilliseconds();
        final long previousObservedHeartbeat = previousClusterLeaderInfo.getHeartbeatMilliseconds();

        if (latestHeartbeat > previousObservedHeartbeat) {
            // Not stale. Cluster leader is alive and updating the lock file
            return false;
        }

        if (latestHeartbeat < previousObservedHeartbeat) {
            // Heartbeat somehow went backwards, maybe due to stale data
            return true;
        }

        // Check if cluster leader has updated the lock file within acceptable limits
        final long elapsed = currentTimeMillis - previousObservedHeartbeat;
        final long heartbeatUpdateIntervalMilliseconds = latestClusterLeaderInfo.getHeartbeatUpdateIntervalMilliseconds();
        final long timeout = heartbeatUpdateIntervalMilliseconds * (long) heartbeatTimeoutMultiplier;
        return elapsed > timeout;
    }
}
