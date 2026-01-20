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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.cluster.CamelClusterMember;
import org.apache.camel.support.cluster.AbstractCamelClusterView;
import org.apache.camel.util.function.ThrowingHelper;
import org.apache.camel.util.function.ThrowingSupplier;
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
    private RandomAccessFile leaderLockFile;
    private RandomAccessFile leaderDataFile;
    private FileLock lock;
    private ScheduledFuture<?> task;
    private int heartbeatTimeoutMultiplier;
    private long acquireLockIntervalMilliseconds;
    private FileLockClusterTaskExecutor clusterTaskExecutor;

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
        FileLockClusterService service = getClusterService().unwrap(FileLockClusterService.class);

        // Start critical section
        try {
            contextStartLock.lock();

            if (leaderLockFile != null) {
                closeInternal();
                fireLeadershipChangedEvent((CamelClusterMember) null);
            }

            // Attempt to pre-create cluster data directories. On failure, it will either be attempted by another cluster member or run again within the tryLock task loop
            try {
                if (!Files.exists(leaderLockPath.getParent())) {
                    Files.createDirectories(leaderLockPath.getParent());
                }
            } catch (IOException e) {
                LOGGER.debug("Error creating directory {}", leaderLockPath.getParent(), e);
            }
        } finally {
            // End critical section
            contextStartLock.unlock();
        }

        clusterTaskExecutor = new FileLockClusterTaskExecutor(service);

        acquireLockIntervalMilliseconds = TimeUnit.MILLISECONDS.convert(
                service.getAcquireLockInterval(),
                service.getAcquireLockIntervalUnit());

        heartbeatTimeoutMultiplier = service.getHeartbeatTimeoutMultiplier();

        scheduleTryLock(true);

        localMember.setStatus(ClusterMemberStatus.STARTED);
    }

    @Override
    protected void doStop() throws Exception {
        if (localMember.isLeader() && leaderDataFile != null) {
            clusterTaskExecutor.run(ThrowingHelper.wrapAsSupplier(new ThrowingSupplier<Void, Throwable>() {
                @Override
                public Void get() throws Throwable {
                    try {
                        FileChannel channel = leaderDataFile.getChannel();
                        channel.truncate(0);
                        channel.force(true);
                    } catch (Exception e) {
                        // Log and ignore since we need to release the file lock and do cleanup
                        LOGGER.debug("Failed to truncate {} on {} stop", leaderDataPath, getClass().getSimpleName(), e);
                    }
                    return null;
                }
            }));
        }

        closeInternal();
        localMember.setStatus(ClusterMemberStatus.STOPPED);
        clusterLeaderInfoRef.set(null);
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
                    } catch (Exception e) {
                        LOGGER.debug("Failed writing cluster leader data to {}", leaderDataPath, e);
                    }
                }

                // Non-null lock at this point signifies leadership has been lost or relinquished
                if (lock != null) {
                    LOGGER.info("Lock on file {} lost (lock={}, cluster-member-id={})", leaderLockPath, lock,
                            localMember.getUuid());
                    localMember.setStatus(ClusterMemberStatus.FOLLOWER);
                    fireLeadershipChangedEvent((CamelClusterMember) null);
                    clusterLeaderInfoRef.set(null);
                    releaseFileLock();
                    closeLockFiles();
                    lock = null;
                    return;
                }

                // Must be follower to reach here
                localMember.setStatus(ClusterMemberStatus.FOLLOWER);

                // Get & update cluster leader state
                LOGGER.debug("Reading cluster leader state from {}", leaderDataPath);
                FileLockClusterLeaderInfo latestClusterLeaderInfo = readClusterLeaderInfo();
                FileLockClusterLeaderInfo previousClusterLeaderInfo = clusterLeaderInfoRef.getAndSet(latestClusterLeaderInfo);

                // Compare the cluster leader lock interval to our own and warn if not in sync
                validateAcquireLockInterval(latestClusterLeaderInfo);

                // Check if we can attempt to take cluster leadership
                if (isLeaderStale(latestClusterLeaderInfo, previousClusterLeaderInfo)
                        || canReclaimLeadership(latestClusterLeaderInfo)) {
                    if (previousClusterLeaderInfo != null && canReclaimLeadership(previousClusterLeaderInfo)) {
                        // Backoff so the current cluster leader can notice leadership is relinquished
                        return;
                    }

                    // Try to recreate the cluster data directory in case it got removed
                    createClusterRootDirectoryIfRequired();

                    // Attempt to obtain cluster leadership
                    LOGGER.debug("Try to acquire a lock on {} (cluster-member-id={})", leaderLockPath, localMember.getUuid());

                    lock = null;
                    leaderLockFile = createRandomAccessFile(leaderLockPath);
                    leaderDataFile = createRandomAccessFile(leaderDataPath);
                    if (leaderLockFile != null && leaderDataFile != null) {
                        lock = leaderLockFile.getChannel().tryLock(0, Math.max(1, leaderLockFile.getChannel().size()), false);
                    }

                    if (lockIsValid()) {
                        LOGGER.info("Lock on file {} acquired (lock={}, cluster-member-id={})", leaderLockPath, lock,
                                localMember.getUuid());
                        localMember.setStatus(ClusterMemberStatus.LEADER);
                        clusterLeaderInfoRef.set(null);
                        fireLeadershipChangedEvent(localMember);
                        writeClusterLeaderInfo(true);
                    } else {
                        LOGGER.debug("Lock on file {} not acquired", leaderLockPath);
                    }
                } else {
                    LOGGER.debug("Existing cluster leader is valid. Retrying leadership acquisition on next interval");
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
                scheduleTryLock(false);
            }
        }
    }

    void validateAcquireLockInterval(FileLockClusterLeaderInfo clusterLeaderInfo) {
        if (clusterLeaderInfo != null
                && clusterLeaderInfo.getHeartbeatUpdateIntervalMilliseconds() != acquireLockIntervalMilliseconds) {
            LOGGER.warn(
                    "This cluster member (cluster-member-id={}) acquireLockIntervalMilliseconds configuration {}ms does not match {}ms set on the cluster leader (cluster-member-id={}). This can lead to unpredictable behavior. Please ensure the configuration is set consistently for all cluster members.",
                    localMember.getUuid(), acquireLockIntervalMilliseconds,
                    clusterLeaderInfo.getHeartbeatUpdateIntervalMilliseconds(),
                    clusterLeaderInfo.getId());
        }
    }

    void scheduleTryLock(boolean isFirstRun) {
        long offset = System.currentTimeMillis() % acquireLockIntervalMilliseconds;
        long delay = acquireLockIntervalMilliseconds - offset;
        if (delay <= 0) {
            delay = acquireLockIntervalMilliseconds;
        }

        if (isFirstRun) {
            // If it seems that other members are running, apply the user provided initial delay
            if (Files.exists(leaderLockPath.getParent()) && Files.exists(leaderLockPath) && Files.exists(leaderDataPath)) {
                FileLockClusterService service = getClusterService().unwrap(FileLockClusterService.class);
                delay = TimeUnit.MILLISECONDS.convert(service.getAcquireLockDelay(), service.getAcquireLockDelayUnit());
            }

            if (delay > 30000) {
                LOGGER.warn(
                        "Initial acquire lock delay is high ({} ms). Consider reducing acquireLockIntervalMilliseconds or acquireLockDelay for faster leader acquisition.",
                        delay);
            }

            LOGGER.info("Waiting {}ms to attempt initial cluster leadership acquisition", delay);
        }

        LOGGER.debug("Scheduling tryLock with delay {}ms", delay);

        getClusterService().unwrap(FileLockClusterService.class)
                .getExecutor()
                .schedule(this::tryLock, delay, TimeUnit.MILLISECONDS);
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

    void createClusterRootDirectoryIfRequired() throws ExecutionException, TimeoutException {
        clusterTaskExecutor.run(ThrowingHelper.wrapAsSupplier(new ThrowingSupplier<Void, Throwable>() {
            @Override
            public Void get() throws Throwable {
                if (!Files.exists(leaderLockPath.getParent())) {
                    Files.createDirectories(leaderLockPath.getParent());
                }
                return null;
            }
        }));
    }

    RandomAccessFile createRandomAccessFile(Path path) throws ExecutionException, TimeoutException {
        return clusterTaskExecutor.run(ThrowingHelper.wrapAsSupplier(new ThrowingSupplier<RandomAccessFile, Throwable>() {
            @Override
            public RandomAccessFile get() throws Throwable {
                return new RandomAccessFile(path.toFile(), "rw");
            }
        }));
    }

    FileLockClusterLeaderInfo readClusterLeaderInfo() throws Exception {
        return clusterTaskExecutor
                .run(ThrowingHelper.wrapAsSupplier(new ThrowingSupplier<FileLockClusterLeaderInfo, Throwable>() {
                    @Override
                    public FileLockClusterLeaderInfo get() throws Throwable {
                        return FileLockClusterUtils.readClusterLeaderInfo(leaderDataPath);
                    }
                }));
    }

    void writeClusterLeaderInfo(boolean forceMetaData) throws Exception {
        FileLockClusterLeaderInfo latestClusterLeaderInfo = new FileLockClusterLeaderInfo(
                localMember.getUuid(),
                acquireLockIntervalMilliseconds,
                System.currentTimeMillis());

        clusterTaskExecutor.run(ThrowingHelper.wrapAsSupplier(new ThrowingSupplier<Void, Throwable>() {
            @Override
            public Void get() throws Throwable {
                FileLockClusterUtils.writeClusterLeaderInfo(
                        leaderDataPath,
                        leaderDataFile.getChannel(),
                        latestClusterLeaderInfo,
                        forceMetaData);
                return null;
            }
        }));
    }

    boolean isLeaderInternal() {
        if (localMember.isLeader()) {
            try {
                FileLockClusterLeaderInfo leaderInfo = readClusterLeaderInfo();
                boolean leaderStale = isLeaderStale(leaderInfo, clusterLeaderInfoRef.getAndSet(leaderInfo));
                LOGGER.debug("Leader read cluster data {}, isStale={}", leaderInfo, leaderStale);

                return leaderInfo != null
                        && !leaderStale
                        && localMember.getUuid().equals(leaderInfo.getId())
                        && lockIsValid();
            } catch (Exception e) {
                LOGGER.debug("Failed to read {} (cluster-member-id={})", leaderLockPath, localMember.getUuid(), e);
                return false;
            }
        }
        return false;
    }

    boolean lockIsValid() throws ExecutionException, TimeoutException {
        if (lock != null && lock.isValid()) {
            return clusterTaskExecutor.run(ThrowingHelper.wrapAsSupplier(new ThrowingSupplier<Boolean, Throwable>() {
                @Override
                public Boolean get() throws Throwable {
                    return Files.exists(leaderLockPath);
                }
            }));
        }
        return false;
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
