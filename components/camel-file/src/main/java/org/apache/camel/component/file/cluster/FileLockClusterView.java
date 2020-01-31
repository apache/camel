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

import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.camel.cluster.CamelClusterMember;
import org.apache.camel.support.cluster.AbstractCamelClusterView;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileLockClusterView extends AbstractCamelClusterView {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileLockClusterView.class);

    private final ClusterMember localMember;
    private final Path path;
    private RandomAccessFile file;
    private FileChannel channel;
    private FileLock lock;
    private ScheduledFuture<?> task;

    FileLockClusterView(FileLockClusterService cluster, String namespace) {
        super(cluster, namespace);

        this.localMember = new ClusterMember();
        this.path = Paths.get(cluster.getRoot(), namespace);

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
        // It may be useful to lock only a region of the file an then have views
        // appending their id to the file on different regions so we can
        // have a list of members. Root/Header region that is used for locking
        // purpose may also contains the lock holder.
        return Collections.emptyList();
    }

    @Override
    protected void doStart() throws Exception {
        if (file != null) {
            closeInternal();

            fireLeadershipChangedEvent(Optional.empty());
        }

        if (!Files.exists(path.getParent())) {
            Files.createDirectories(path.getParent());
        }

        file = new RandomAccessFile(path.toFile(), "rw");
        channel = file.getChannel();

        FileLockClusterService service = getClusterService().unwrap(FileLockClusterService.class);
        ScheduledExecutorService executor = service.getExecutor();

        task = executor.scheduleAtFixedRate(this::tryLock, TimeUnit.MILLISECONDS.convert(service.getAcquireLockDelay(), service.getAcquireLockDelayUnit()),
                                            TimeUnit.MILLISECONDS.convert(service.getAcquireLockInterval(), service.getAcquireLockIntervalUnit()), TimeUnit.MILLISECONDS);
    }

    @Override
    protected void doStop() throws Exception {
        closeInternal();
    }

    // *********************************
    //
    // *********************************

    private void closeInternal() throws Exception {
        if (task != null) {
            task.cancel(true);
        }

        if (lock != null) {
            lock.release();
        }

        if (file != null) {
            IOHelper.close(channel);
            IOHelper.close(file);

            channel = null;
            file = null;
        }
    }

    private void tryLock() {
        if (isStarting() || isStarted()) {
            try {
                if (localMember.isLeader()) {
                    LOGGER.trace("Holding the lock on file {} (lock={})", path, lock);
                    return;
                }

                synchronized (FileLockClusterView.this) {
                    if (lock != null) {
                        LOGGER.info("Lock on file {} lost (lock={})", path, lock);
                        fireLeadershipChangedEvent(Optional.empty());
                    }

                    LOGGER.debug("Try to acquire a lock on {}", path);

                    lock = null;
                    lock = channel.tryLock();

                    if (lock != null) {
                        LOGGER.info("Lock on file {} acquired (lock={})", path, lock);
                        fireLeadershipChangedEvent(Optional.of(localMember));
                    } else {
                        LOGGER.debug("Lock on file {} not acquired ", path);
                    }
                }
            } catch (OverlappingFileLockException e) {
                LOGGER.debug("Lock on file {} not acquired ", path);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private final class ClusterMember implements CamelClusterMember {
        @Override
        public boolean isLeader() {
            synchronized (FileLockClusterView.this) {
                return lock != null && lock.isValid();
            }
        }

        @Override
        public boolean isLocal() {
            return true;
        }

        @Override
        public String getId() {
            return getClusterService().getId();
        }
    }
}
