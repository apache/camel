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
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.cluster.CamelClusterMember;
import org.apache.camel.support.cluster.AbstractCamelClusterView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileLockClusterView extends AbstractCamelClusterView {

    // Used only during service startup as each context could try to access it concurrently.
    // It isolates the critical section making sure only one service creates the files.
    private static final ReentrantLock contextStartLock = new ReentrantLock();
    private static final Logger LOGGER = LoggerFactory.getLogger(FileLockClusterView.class);

    private final ClusterMember localMember;
    private final Path leaderLockPath;
    private final Path leaderDataPath;
    private final AtomicReference<FileLockClusterLeaderInfo> clusterLeaderInfoRef = new AtomicReference<>();
    private final AtomicBoolean firstAcquireLockAttemptCompleted = new AtomicBoolean();
    private RandomAccessFile leaderLockFile;
    private RandomAccessFile leaderDataFile;
    private FileLock lock;
    private ScheduledFuture<?> task;
    private int heartbeatTimeoutMultiplier;
    private long acquireLockIntervalMilliseconds;
    private long acquireLeadershipBackoffMilliseconds;

    FileLockClusterView(FileLockClusterService cluster, String namespace) {
        super(cluster, namespace);

        Objects.requireNonNull(cluster.getRoot(), "FileLockClusterService root directory must be specified");
        this.localMember = new ClusterMember();
        this.leaderLockPath = Paths.get(cluster.getRoot(), namespace);
        this.leaderDataPath = Paths.get(cluster.getRoot(), namespace + ".dat");
    }

    @Override
    public Optional<CamelClusterMember> getLeader() {
        return this.localMember.isLeader() ? Optional.of(this.localMember) : Optional.empty();
    }

    @Override
    public CamelClusterMember getLocalMember() {
        return this.localMember;
    }

    @Override
    public List<CamelClusterMember> getMembers() {
        // It may be useful to lock only a region of the file and then have views
        // appending their id to the file on different regions so we can
        // have a list of members. Root/Header region that is used for locking
        // purpose may also contain the lock holder.
        return Collections.emptyList();
    }

    @Override
    protected void doStart() throws Exception {
        // Start critical section
        try {
            contextStartLock.lock();

            if (leaderLockFile != null) {
                closeInternal();
                fireLeadershipChangedEvent((CamelClusterMember) null);
            }

            if (!Files.exists(leaderLockPath.getParent())) {
                Files.createDirectories(leaderLockPath.getParent());
            }

            if (!Files.exists(leaderLockPath)) {
                Files.createFile(leaderLockPath);
            }

            if (!Files.exists(leaderDataPath)) {
                Files.createFile(leaderDataPath);
            }
        } finally {
            // End critical section
            contextStartLock.unlock();
        }

        FileLockClusterService service = getClusterService().unwrap(FileLockClusterService.class);
        acquireLockIntervalMilliseconds = TimeUnit.MILLISECONDS.convert(
                service.getAcquireLockInterval(),
                service.getAcquireLockIntervalUnit());

        heartbeatTimeoutMultiplier = service.getHeartbeatTimeoutMultiplier();
        if (heartbeatTimeoutMultiplier <= 0) {
            throw new IllegalArgumentException("HeartbeatTimeoutMultiplier must be greater than 0");
        }

        long acquireLeadershipBackoff = service.getAcquireLeadershipBackoff();
        if (acquireLeadershipBackoff < 0) {
            throw new IllegalArgumentException("acquireLeadershipBackoff must not be a negative value");
        }
        acquireLeadershipBackoffMilliseconds = TimeUnit.MILLISECONDS.convert(
                acquireLeadershipBackoff,
                service.getAcquireLeadershipBackoffIntervalUnit());

        ScheduledExecutorService executor = service.getExecutor();
        task = executor.scheduleWithFixedDelay(this::tryLock,
                TimeUnit.MILLISECONDS.convert(service.getAcquireLockDelay(), service.getAcquireLockDelayUnit()),
                TimeUnit.MILLISECONDS.convert(service.getAcquireLockInterval(), service.getAcquireLockIntervalUnit()),
                TimeUnit.MILLISECONDS);

        localMember.setStatus(ClusterMemberStatus.STARTED);
    }

    @Override
    protected void doStop() throws Exception {
        if (localMember.isLeader() && leaderDataFile != null) {
            try {
                FileChannel channel = leaderDataFile.getChannel();
                channel.truncate(0);
                channel.force(true);
            } catch (Exception e) {
                // Log and ignore since we need to release the file lock and do cleanup
                LOGGER.debug("Failed to truncate {} on {} stop", leaderDataPath, getClass().getSimpleName(), e);
            }
        }

        closeInternal();
        localMember.setStatus(ClusterMemberStatus.STOPPED);
        clusterLeaderInfoRef.set(null);
        firstAcquireLockAttemptCompleted.set(false);
    }

    private void closeInternal() {
        if (task != null) {
            task.cancel(true);
        }

        releaseFileLock();
        closeLockFiles();
    }

    private void closeLockFiles() {
        if (leaderLockFile != null) {
            try {
                leaderLockFile.close();
            } catch (Exception ignore) {
                LOGGER.warn("{}", ignore.getMessage(), ignore);
            }
            leaderLockFile = null;
        }

        if (leaderDataFile != null) {
            try {
                leaderDataFile.close();
            } catch (Exception ignore) {
                LOGGER.warn("{}", ignore.getMessage(), ignore);
            }
            leaderDataFile = null;
        }
    }

    private void releaseFileLock() {
        if (lock != null) {
            try {
                lock.release();
            } catch (Exception ignore) {
                LOGGER.warn("{}", ignore.getMessage(), ignore);
            }
        }
    }

    private void tryLock() {
        if (isStarting() || isStarted()) {
            Exception reason = null;

            try {
                if (isLeaderInternal()) {
                    LOGGER.debug("Holding the lock on file {} (lock={}, cluster-member-id={})", leaderLockPath, lock,
                            localMember.getUuid());
                    try {
                        // Update the cluster data file with the leader state so that other cluster members can interrogate it
                        writeClusterLeaderInfo(false);
                        return;
                    } catch (IOException e) {
                        LOGGER.debug("Failed writing cluster leader data to {}", leaderDataPath, e);
                    }
                }

                // Non-null lock at this point signifies leadership has been lost or relinquished
                if (lock != null) {
                    LOGGER.info("Lock on file {} lost (lock={}, cluster-member-id={})", leaderLockPath, lock,
                            localMember.getUuid());
                    localMember.setStatus(ClusterMemberStatus.FOLLOWER);
                    fireLeadershipChangedEvent((CamelClusterMember) null);
                    releaseFileLock();
                    closeLockFiles();
                    lock = null;
                    return;
                }

                // Must be follower to reach here
                localMember.setStatus(ClusterMemberStatus.FOLLOWER);

                // Get & update cluster leader state
                LOGGER.debug("Reading cluster leader state from {}", leaderDataPath);
                FileLockClusterLeaderInfo latestClusterLeaderInfo = FileLockClusterUtils.readClusterLeaderInfo(leaderDataPath);
                FileLockClusterLeaderInfo previousClusterLeaderInfo = clusterLeaderInfoRef.getAndSet(latestClusterLeaderInfo);

                // Check if we can attempt to take cluster leadership
                if (isLeaderStale(latestClusterLeaderInfo, previousClusterLeaderInfo)
                        || canReclaimLeadership(latestClusterLeaderInfo)) {
                    if (previousClusterLeaderInfo != null && canReclaimLeadership(previousClusterLeaderInfo)) {
                        // Backoff so the current cluster leader can notice leadership is relinquished
                        return;
                    }

                    // Try to recreate the cluster data directory in case it got removed
                    if (!Files.exists(leaderLockPath.getParent())) {
                        Files.createDirectories(leaderLockPath.getParent());
                    }

                    // Attempt to obtain cluster leadership
                    LOGGER.debug("Try to acquire a lock on {} (cluster-member-id={})", leaderLockPath, localMember.getUuid());
                    leaderLockFile = new RandomAccessFile(leaderLockPath.toFile(), "rw");
                    leaderDataFile = new RandomAccessFile(leaderDataPath.toFile(), "rw");

                    lock = null;
                    lock = leaderLockFile.getChannel().tryLock(0, Math.max(1, leaderLockFile.getChannel().size()), false);

                    if (lock != null) {
                        LOGGER.info("Lock on file {} acquired (lock={}, cluster-member-id={})", leaderLockPath, lock,
                                localMember.getUuid());

                        boolean interrupted = false;
                        if (firstAcquireLockAttemptCompleted.get()) {
                            // If this is not the initial attempt to claim leadership, give some time for the existing leader to fully relinquish leadership
                            interrupted = applyAcquireLeadershipBackoff();
                        }

                        if (interrupted) {
                            // applyAcquireLeadershipBackoff sleep was interrupted, likely because we are being shutdown
                            lock = null;
                        } else {
                            localMember.setStatus(ClusterMemberStatus.LEADER);
                            clusterLeaderInfoRef.set(null);
                            firstAcquireLockAttemptCompleted.set(false);
                            fireLeadershipChangedEvent(localMember);
                            writeClusterLeaderInfo(true);
                        }
                    } else {
                        LOGGER.debug("Lock on file {} not acquired", leaderLockPath);
                    }
                } else {
                    LOGGER.debug("Existing cluster leader is valid. Retrying leadership acquisition on next interval");
                }

                if (localMember.getStatus().equals(ClusterMemberStatus.FOLLOWER)) {
                    firstAcquireLockAttemptCompleted.set(true);
                }
            } catch (OverlappingFileLockException e) {
                reason = new IOException(e);
            } catch (Exception e) {
                reason = e;
            } finally {
                if (lock == null) {
                    LOGGER.debug("Lock on file {} not acquired (cluster-member-id={})", leaderLockPath, localMember.getUuid(),
                            reason);
                    closeLockFiles();
                }
            }
        }
    }

    boolean isLeaderStale(FileLockClusterLeaderInfo clusterLeaderInfo, FileLockClusterLeaderInfo previousClusterLeaderInfo) {
        return FileLockClusterUtils.isLeaderStale(
                clusterLeaderInfo,
                previousClusterLeaderInfo,
                System.currentTimeMillis(),
                heartbeatTimeoutMultiplier);
    }

    boolean canReclaimLeadership(FileLockClusterLeaderInfo leaderInfo) {
        return leaderInfo != null && localMember.getUuid().equals(leaderInfo.getId());
    }

    void writeClusterLeaderInfo(boolean forceMetaData) throws IOException {
        FileLockClusterLeaderInfo latestClusterLeaderInfo = new FileLockClusterLeaderInfo(
                localMember.getUuid(),
                acquireLockIntervalMilliseconds,
                System.currentTimeMillis());

        FileLockClusterUtils.writeClusterLeaderInfo(
                leaderDataPath,
                leaderDataFile.getChannel(),
                latestClusterLeaderInfo,
                forceMetaData);
    }

    boolean isLeaderInternal() {
        if (localMember.isLeader()) {
            try {
                FileLockClusterLeaderInfo leaderInfo = FileLockClusterUtils.readClusterLeaderInfo(leaderDataPath);
                return leaderInfo != null
                        && lock != null
                        && lock.isValid()
                        && localMember.getUuid().equals(leaderInfo.getId())
                        && Files.exists(leaderLockPath);
            } catch (Exception e) {
                LOGGER.debug("Failed to read {} (cluster-member-id={})", leaderLockPath, localMember.getUuid(), e);
                return false;
            }
        }
        return false;
    }

    boolean applyAcquireLeadershipBackoff() {
        boolean interrupted = false;
        if (acquireLeadershipBackoffMilliseconds > 0) {
            try {
                LOGGER.debug("Waiting {} milliseconds before claiming leadership", acquireLeadershipBackoffMilliseconds);
                Thread.sleep(acquireLeadershipBackoffMilliseconds);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                interrupted = true;
            }
        }
        return interrupted;
    }

    private final class ClusterMember implements CamelClusterMember {
        private final AtomicReference<ClusterMemberStatus> status = new AtomicReference<>(ClusterMemberStatus.STOPPED);
        private final String uuid = UUID.randomUUID().toString();

        @Override
        public boolean isLeader() {
            return getStatus().equals(ClusterMemberStatus.LEADER);
        }

        @Override
        public boolean isLocal() {
            return true;
        }

        @Override
        public String getId() {
            return getClusterService().getId();
        }

        public String getUuid() {
            return uuid;
        }

        public ClusterMemberStatus getStatus() {
            return status.get();
        }

        private void setStatus(ClusterMemberStatus status) {
            this.status.set(status);
        }
    }

    private enum ClusterMemberStatus {
        FOLLOWER,
        LEADER,
        STARTED,
        STOPPED
    }
}
