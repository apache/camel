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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
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
    // The lock files that a view of this JVM has locked or is trying to lock, with that view. The file locks are
    // locks of the process (POSIX fcntl locks on Linux and macOS), and closing any channel of a file releases all the
    // locks of the process on it (see FileLock), while the FileLock of the holder still says it is valid. So a view
    // must not open (and then close) a channel on a lock file that another view of the JVM has locked: it does not try
    // the lock while the path is registered here.
    private static final Map<Path, FileLockClusterView> LOCK_PATHS = new ConcurrentHashMap<>();

    private final ClusterMember localMember;
    private final Path leaderLockPath;
    private final Path leaderDataPath;
    private final AtomicReference<FileLockClusterLeaderInfo> clusterLeaderInfoRef = new AtomicReference<>();
    // Written under stateLock (published by acquireLock, taken over by doStop or by the leadership-lost path of a
    // current check). Volatile as the leadership check and the cluster data tasks read them without the lock.
    private volatile RandomAccessFile leaderLockFile;
    private volatile RandomAccessFile leaderDataFile;
    private volatile FileLock lock;
    // the key of the lock file in LOCK_PATHS while the view holds the lock (written with lock, under stateLock)
    private volatile Path lockPathKey;
    // The opens of the lock file that acquireLock gave up on and that are still running (an attempt that timed out
    // keeps running, as the cluster task executor cannot interrupt it), and the key of the lock file to remove from
    // LOCK_PATHS when the last of them ended (both guarded by stateLock). Closing a channel of the lock file releases the
    // lock of the process (see LOCK_PATHS), so the file of such an open must not be left to the garbage collector, which
    // closes it at any time, also while this view or another view of the JVM holds the lock. It is closed as soon as it
    // is opened, and until then the view keeps the key registered and does not try the lock. If such an open never
    // returns (a hung mount), no view of this JVM tries the lock again until the JVM restarts: both survive a stop and
    // start of the view, as the open is still running.
    private int lateLockFileOpens;
    private Path lateLockFileOpensKey;
    private int heartbeatTimeoutMultiplier;
    private long acquireLockIntervalMilliseconds;
    private FileLockClusterTaskExecutor clusterTaskExecutor;
    // Guards generation and the hand-over of lock, leaderLockFile and leaderDataFile between the leadership check and
    // doStop. It is only held for short state changes, never during file I/O or while listeners are notified.
    private final ReentrantLock stateLock = new ReentrantLock();
    // Incremented on every start and stop. A leadership check belongs to the generation of the start that scheduled
    // it, and it ends without taking the lock or rescheduling itself once that generation is over.
    private long generation;

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

            // Defensive only: doStop and the leadership-lost path always take over and clear the lock and files
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
        if (acquireLockIntervalMilliseconds < 1) {
            throw new IllegalArgumentException(
                    "acquireLockInterval must be at least 1 millisecond, was: " + service.getAcquireLockInterval() + " "
                                               + service.getAcquireLockIntervalUnit());
        }

        heartbeatTimeoutMultiplier = service.getHeartbeatTimeoutMultiplier();

        long gen;
        stateLock.lock();
        try {
            gen = ++generation;
        } finally {
            stateLock.unlock();
        }
        scheduleTryLock(true, gen);

        localMember.setStatus(ClusterMemberStatus.STARTED);
    }

    @Override
    protected void doStop() throws Exception {
        final boolean wasLeader;
        final FileLock heldLock;
        final RandomAccessFile heldLockFile;
        final RandomAccessFile heldDataFile;
        final Path heldLockPathKey;
        stateLock.lock();
        try {
            // A leadership check that is running now can no longer publish a lock it acquires: it re-checks the
            // generation under stateLock and releases the lock instead. So the lock and files taken here are the
            // only ones this view holds.
            generation++;
            wasLeader = localMember.isLeader();
            localMember.setStatus(ClusterMemberStatus.STOPPED);
            heldLock = lock;
            heldLockFile = leaderLockFile;
            heldDataFile = leaderDataFile;
            heldLockPathKey = lockPathKey;
            lock = null;
            leaderLockFile = null;
            leaderDataFile = null;
            lockPathKey = null;
        } finally {
            stateLock.unlock();
        }

        if (wasLeader) {
            // tell the listeners (such as clustered routes) that this member is no longer the leader, before the lock
            // is released and another member can take over the leadership
            fireLeadershipChangedEvent((CamelClusterMember) null);
        }

        try {
            if (wasLeader && heldDataFile != null) {
                clusterTaskExecutor.run(ThrowingHelper.wrapAsSupplier(new ThrowingSupplier<Void, Throwable>() {
                    @Override
                    public Void get() throws Throwable {
                        try {
                            FileChannel channel = heldDataFile.getChannel();
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
        } finally {
            // The fields no longer reference the lock and files, so they must be released even if the truncate task
            // timed out (for example on a hanging NFS mount), otherwise the stopped view would keep the lock
            releaseFileLock(heldLock);
            closeFile(heldLockFile);
            closeFile(heldDataFile);
            unregisterLockPath(heldLockPathKey);
            clusterLeaderInfoRef.set(null);
        }
    }

    /**
     * The lock, lock file and data file taken over from this view by {@link #takeOver(long)}.
     */
    private record HeldLock(FileLock lock, RandomAccessFile lockFile, RandomAccessFile dataFile, Path lockPathKey) {
        void release(FileLockClusterView view) {
            releaseFileLock(lock);
            closeFile(lockFile);
            closeFile(dataFile);
            view.unregisterLockPath(lockPathKey);
        }
    }

    /**
     * If generation {@code gen} is still current, sets the member to FOLLOWER and takes the lock and files over from
     * the view, so that the caller releases them outside stateLock. Returns null if the view has been stopped or
     * started again since, in which case doStop (or the check of the newer generation) owns them.
     */
    private HeldLock takeOver(long gen) {
        stateLock.lock();
        try {
            if (gen != generation) {
                return null;
            }
            HeldLock held = new HeldLock(lock, leaderLockFile, leaderDataFile, lockPathKey);
            lock = null;
            leaderLockFile = null;
            leaderDataFile = null;
            lockPathKey = null;
            localMember.setStatus(ClusterMemberStatus.FOLLOWER);
            return held;
        } finally {
            stateLock.unlock();
        }
    }

    private void closeInternal() {
        releaseFileLock();
        closeLockFiles();
        // Safe without stateLock: closeInternal() is only called from doStart() under contextStartLock,
        // before the first scheduleTryLock(), so no concurrent acquireLock() writes lockPathKey here.
        unregisterLockPath(lockPathKey);
        lockPathKey = null;
    }

    /**
     * The key of the lock file in the JVM-wide registry (see {@link #resolveLockPathKey()}). Resolving it reads the
     * file system, so it runs through the cluster task executor with the cluster data task timeout and attempts, like
     * the other file I/O of the leadership check: on a hung network mount the attempt fails instead of blocking the
     * check (and the other checks of the service, which share its scheduler thread). Returns null if the check was
     * interrupted.
     */
    private Path lockPathKey() throws ExecutionException, TimeoutException {
        return clusterTaskExecutor.run(ThrowingHelper.wrapAsSupplier(new ThrowingSupplier<Path, Throwable>() {
            @Override
            public Path get() throws Throwable {
                return resolveLockPathKey();
            }
        }));
    }

    /**
     * The absolute path of the lock file, with the symbolic links of its directory resolved, so that two services
     * configured with different paths to one directory share the key. If the directory cannot be resolved, the attempt
     * fails rather than use a key that may differ from the key of another view (opening the lock file would fail
     * without the directory anyway).
     */
    Path resolveLockPathKey() throws IOException {
        Path path = leaderLockPath.toAbsolutePath().normalize();
        Path parent = path.getParent();
        return parent != null ? parent.toRealPath().resolve(path.getFileName()) : path;
    }

    private void unregisterLockPath(Path key) {
        if (key != null) {
            stateLock.lock();
            try {
                if (lateLockFileOpens > 0) {
                    // removed when the last late open of the lock file ended
                    lateLockFileOpensKey = key;
                } else {
                    LOCK_PATHS.remove(key, this);
                }
            } finally {
                stateLock.unlock();
            }
        }
    }

    private boolean hasLateLockFileOpens() {
        stateLock.lock();
        try {
            return lateLockFileOpens > 0;
        } finally {
            stateLock.unlock();
        }
    }

    private void lateLockFileOpenEnded() {
        boolean last;
        stateLock.lock();
        try {
            lateLockFileOpens--;
            last = lateLockFileOpens == 0;
            if (last && lateLockFileOpensKey != null) {
                LOCK_PATHS.remove(lateLockFileOpensKey, this);
                lateLockFileOpensKey = null;
            }
        } finally {
            stateLock.unlock();
        }
        if (last) {
            LOGGER.info("The open of lock file {} that timed out has ended, trying the lock again (cluster-member-id={})",
                    leaderLockPath, localMember.getUuid());
        }
    }

    private void closeLockFiles() {
        closeFile(leaderLockFile);
        leaderLockFile = null;
        closeFile(leaderDataFile);
        leaderDataFile = null;
    }

    private static void closeFile(RandomAccessFile file) {
        if (file != null) {
            try {
                file.close();
            } catch (Exception ignore) {
                LOGGER.warn("{}", ignore.getMessage(), ignore);
            }
        }
    }

    private void releaseFileLock() {
        releaseFileLock(lock);
    }

    private static void releaseFileLock(FileLock fileLock) {
        if (fileLock != null) {
            try {
                fileLock.release();
            } catch (Exception ignore) {
                LOGGER.warn("{}", ignore.getMessage(), ignore);
            }
        }
    }

    private boolean isCurrentGeneration(long gen) {
        stateLock.lock();
        try {
            return gen == generation;
        } finally {
            stateLock.unlock();
        }
    }

    private boolean setFollower(long gen) {
        stateLock.lock();
        try {
            if (gen != generation) {
                return false;
            }
            localMember.setStatus(ClusterMemberStatus.FOLLOWER);
            return true;
        } finally {
            stateLock.unlock();
        }
    }

    private void tryLock(long gen) {
        // A check scheduled by an earlier start, or by a start that has been stopped since, ends here without
        // rescheduling itself, so that a stop ends the chain and a quick stop and start does not add a second chain
        if (isCurrentGeneration(gen) && (isStarting() || isStarted())) {
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
                    // Only if the view has not been stopped meanwhile: doStop then owns the lock and files, and the
                    // member stays STOPPED
                    HeldLock held = takeOver(gen);
                    if (held != null) {
                        LOGGER.info("Lock on file {} lost (lock={}, cluster-member-id={})", leaderLockPath, held.lock(),
                                localMember.getUuid());
                        fireLeadershipChangedEvent((CamelClusterMember) null);
                        clusterLeaderInfoRef.set(null);
                        held.release(this);
                    }
                    return;
                }

                // Must be follower to reach here. A stopped view stays STOPPED and its check ends here
                if (!setFollower(gen)) {
                    return;
                }

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
                        // Backoff so the current cluster leader can notice leadership is relinquished. Only for this
                        // check: the cluster data still names this member because no leader has written it since the
                        // last attempt to take the lock failed, so the next check tries again. Otherwise a member
                        // whose attempt failed (for example on a transient I/O error) would back off for good, and a
                        // cluster with no other member would stay without a leader.
                        clusterLeaderInfoRef.compareAndSet(latestClusterLeaderInfo, null);
                        return;
                    }

                    // Try to recreate the cluster data directory in case it got removed
                    createClusterRootDirectoryIfRequired();

                    // Attempt to obtain cluster leadership
                    LOGGER.debug("Try to acquire a lock on {} (cluster-member-id={})", leaderLockPath, localMember.getUuid());

                    if (acquireLock(gen)) {
                        LOGGER.info("Lock on file {} acquired (lock={}, cluster-member-id={})", leaderLockPath, lock,
                                localMember.getUuid());
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
                }
                if (isCurrentGeneration(gen)) {
                    scheduleTryLock(false, gen);
                }
            }
        }
    }

    /**
     * Opens the lock and data files and tries to lock the lock file. The files and the lock are only published to this
     * view, and the member only becomes leader, if the view has not been stopped since the check of generation
     * {@code gen} started. Otherwise the lock is released again. The file I/O runs without holding stateLock.
     */
    private boolean acquireLock(long gen) throws Exception {
        if (!isCurrentGeneration(gen)) {
            // stopped while the cluster data was read: do not take the lock, even briefly
            return false;
        }
        if (hasLateLockFileOpens()) {
            LOGGER.debug("An open of lock file {} that timed out is still running (cluster-member-id={}), not trying the lock",
                    leaderLockPath, localMember.getUuid());
            return false;
        }
        // resolved before the key is registered, so a timeout leaves nothing in LOCK_PATHS
        Path key = lockPathKey();
        if (key == null) {
            // interrupted while resolving the key
            return false;
        }
        FileLockClusterView holder = LOCK_PATHS.putIfAbsent(key, this);
        if (holder != null) {
            // another view of this JVM holds (or is trying) the lock: trying it here would fail anyway, and closing
            // the channel afterwards would release the lock of that view at OS level (see LOCK_PATHS)
            LOGGER.debug("Lock on file {} is held by another cluster view of this JVM (cluster-member-id={}), not trying it",
                    leaderLockPath, holder.localMember.getUuid());
            return false;
        }
        RandomAccessFile newLockFile = null;
        RandomAccessFile newDataFile = null;
        FileLock newLock = null;
        boolean published = false;
        try {
            newLockFile = createRandomAccessFile(leaderLockPath);
            if (newLockFile != null) {
                // null if an open of the lock file is still running: the lock is not tried, so the data file is not
                // needed
                newDataFile = createRandomAccessFile(leaderDataPath);
            }
            if (newLockFile != null && newDataFile != null) {
                newLock = newLockFile.getChannel().tryLock(0, Math.max(1, newLockFile.getChannel().size()), false);
            }

            if (lockIsValid(newLock)) {
                stateLock.lock();
                try {
                    if (gen == generation) {
                        lock = newLock;
                        leaderLockFile = newLockFile;
                        leaderDataFile = newDataFile;
                        lockPathKey = key;
                        localMember.setStatus(ClusterMemberStatus.LEADER);
                        clusterLeaderInfoRef.set(null);
                        published = true;
                    }
                } finally {
                    stateLock.unlock();
                }

                if (!published) {
                    LOGGER.debug("Lock on file {} acquired after the view was stopped, releasing it (cluster-member-id={})",
                            leaderLockPath, localMember.getUuid());
                }
            }
        } finally {
            if (!published) {
                releaseFileLock(newLock);
                closeFile(newLockFile);
                closeFile(newDataFile);
                unregisterLockPath(key);
            }
        }
        return published;
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

    void scheduleTryLock(boolean isFirstRun, long gen) {
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
                .schedule(() -> tryLock(gen), delay, TimeUnit.MILLISECONDS);
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
        if (path.equals(leaderLockPath)) {
            LockFileOpen open = new LockFileOpen();
            try {
                return open.end(clusterTaskExecutor.run(ThrowingHelper.wrapAsSupplier(open)));
            } finally {
                // closes what the attempts opened if the executor gave up (no effect if the call already ended)
                open.end(null);
            }
        }
        return clusterTaskExecutor.run(ThrowingHelper.wrapAsSupplier(new ThrowingSupplier<RandomAccessFile, Throwable>() {
            @Override
            public RandomAccessFile get() throws Throwable {
                return openFile(path);
            }
        }));
    }

    RandomAccessFile openFile(Path path) throws IOException {
        return new RandomAccessFile(path.toFile(), "rw");
    }

    /**
     * One open of the lock file by acquireLock. The cluster task executor runs it once per attempt, and an attempt that
     * timed out keeps running. Every file an attempt opens is returned by {@link #end} or closed: by end if the attempt
     * finished before the call ended, otherwise as soon as it is opened (see lateLockFileOpens).
     */
    private final class LockFileOpen implements ThrowingSupplier<RandomAccessFile, Throwable> {
        // guarded by stateLock
        private final List<RandomAccessFile> opened = new ArrayList<>();
        private int running;
        private boolean ended;

        @Override
        public RandomAccessFile get() throws Throwable {
            stateLock.lock();
            try {
                if (ended) {
                    // an attempt that only starts after the call ended
                    return null;
                }
                running++;
            } finally {
                stateLock.unlock();
            }
            RandomAccessFile file = null;
            try {
                file = openFile(leaderLockPath);
                return file;
            } finally {
                attemptEnded(file);
            }
        }

        private void attemptEnded(RandomAccessFile file) {
            boolean late;
            stateLock.lock();
            try {
                running--;
                late = ended;
                if (!late && file != null) {
                    opened.add(file);
                }
            } finally {
                stateLock.unlock();
            }
            if (late) {
                // this view has not tried the lock since the call ended, and no other view of the JVM has, as the key
                // is still registered: closing the file releases no lock
                closeFile(file);
                lateLockFileOpenEnded();
            }
        }

        /**
         * Ends the call with the file the executor returned. Returns that file, or null if an attempt is still running:
         * the lock must not be tried then, as closing the file of that attempt later would release it. Closes the other
         * files the attempts opened.
         */
        RandomAccessFile end(RandomAccessFile result) {
            List<RandomAccessFile> files;
            boolean stillRunning;
            stateLock.lock();
            try {
                if (ended) {
                    return null;
                }
                ended = true;
                stillRunning = running > 0;
                lateLockFileOpens += running;
                files = new ArrayList<>(opened);
                opened.clear();
            } finally {
                stateLock.unlock();
            }
            RandomAccessFile file = stillRunning ? null : result;
            for (RandomAccessFile f : files) {
                if (f != file) {
                    closeFile(f);
                }
            }
            if (stillRunning) {
                // logged once per wait (acquireLock does not open the lock file again until the wait ended), at WARN
                // as the wait lasts until the JVM restarts if the open never returns
                LOGGER.warn(
                        "An open of lock file {} timed out and is still running. This cluster member, and any other cluster view of this JVM on the same lock file, does not try the lock until it has ended, and if it never ends (for example on a hung mount) not until the JVM restarts (cluster-member-id={})",
                        leaderLockPath, localMember.getUuid());
            }
            return file;
        }
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
        return lockIsValid(lock);
    }

    private boolean lockIsValid(FileLock fileLock) throws ExecutionException, TimeoutException {
        if (fileLock != null && fileLock.isValid()) {
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
