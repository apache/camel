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
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.camel.CamelContext;
import org.apache.camel.cluster.CamelClusterView;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A stopped {@link FileLockClusterView} must end its leadership check: a check that is still running when the view
 * stops must not take the lock or report leadership afterwards, and a quick stop and start must not leave two checks
 * running. The stop must release the lock even if the cluster data cannot be written.
 */
public class FileLockClusterViewStopTest {

    private static final String NAMESPACE = "ns";

    @TempDir
    Path root;

    private final List<CamelContext> contexts = new ArrayList<>();

    @AfterEach
    public void stopContexts() {
        contexts.forEach(CamelContext::stop);
    }

    @Test
    public void testViewStoppedDuringLeadershipCheckDoesNotKeepTheLock() throws Exception {
        CamelContext contextA = startContext();
        FileLockClusterService serviceA = newService("A");
        contextA.addService(serviceA);
        CamelClusterView viewA = serviceA.getView(NAMESPACE);
        await().atMost(10, TimeUnit.SECONDS).until(() -> viewA.getLocalMember().isLeader()
                && FileLockClusterUtils.readClusterLeaderInfo(root.resolve(NAMESPACE + ".dat")) != null);

        // B follows A, and its next check after A has gone is held just before it opens the lock file
        HookedFileLockClusterService serviceB = new HookedFileLockClusterService();
        configure(serviceB, "B");
        CamelContext contextB = startContext();
        contextB.addService(serviceB);
        CamelClusterView viewB = serviceB.getView(NAMESPACE);

        contextA.stop();
        assertTrue(serviceB.reached.await(10, TimeUnit.SECONDS), "B did not start a leadership check");

        // B's view is released by its last user (a master route or clustered route stopping) during that check
        serviceB.releaseView(viewB);
        serviceB.release.countDown();
        // the check runs on the single thread of B's executor: once this task has run, the check is over
        serviceB.getExecutor().submit(() -> {
        }).get(30, TimeUnit.SECONDS);

        // the check of the stopped view did not schedule another one
        assertTrue(serviceB.scheduler.getQueue().isEmpty(), "the stopped view still schedules leadership checks");
        assertFalse(viewB.getLocalMember().isLeader(), "the stopped view reports leadership");
        assertFalse(serviceB.isLeader(NAMESPACE), "the stopped view reports leadership");
        assertTrue(isLockFree(root.resolve(NAMESPACE)), "the stopped view holds the lock");

        // so another member can take over
        CamelContext contextC = startContext();
        FileLockClusterService serviceC = newService("C");
        contextC.addService(serviceC);
        CamelClusterView viewC = serviceC.getView(NAMESPACE);
        await().atMost(10, TimeUnit.SECONDS).until(() -> viewC.getLocalMember().isLeader());
    }

    @Test
    public void testQuickRestartDoesNotAddLeadershipChecks() throws Exception {
        HookedFileLockClusterService service = new HookedFileLockClusterService();
        configure(service, "A");
        service.release.countDown();
        CamelContext context = startContext();
        context.addService(service);
        CamelClusterView view = service.getView(NAMESPACE);
        await().atMost(10, TimeUnit.SECONDS).until(() -> view.getLocalMember().isLeader());

        // a master route or clustered route stopped and started again within one acquireLockInterval
        for (int i = 0; i < 3; i++) {
            service.releaseView(view);
            service.getView(NAMESPACE);
        }

        // every running check has exactly one run scheduled at a time. The four starts (the first one and three
        // restarts) queued four checks. The checks of the earlier starts end at their next run without rescheduling,
        // so after one interval only the check of the last start is left
        ScheduledThreadPoolExecutor executor = service.scheduler;
        await().atMost(10, TimeUnit.SECONDS).until(() -> executor.getQueue().size() == 1);
        await().atMost(10, TimeUnit.SECONDS).until(() -> view.getLocalMember().isLeader());
        await().during(2, TimeUnit.SECONDS).atMost(3, TimeUnit.SECONDS).until(() -> executor.getQueue().size() <= 1);
    }

    @Test
    public void testStopReleasesTheLockWhenTheDataFileCannotBeTruncated() throws Exception {
        HangingFileLockClusterService service = new HangingFileLockClusterService();
        configure(service, "A");
        CamelContext context = startContext();
        context.addService(service);
        CamelClusterView view = service.getView(NAMESPACE);
        await().atMost(10, TimeUnit.SECONDS).until(() -> view.getLocalMember().isLeader()
                && FileLockClusterUtils.readClusterLeaderInfo(root.resolve(NAMESPACE + ".dat")) != null);

        // the cluster data storage hangs (for example an NFS mount), so the truncate on stop times out
        try {
            service.setClusterDataTaskMaxAttempts(1);
            service.setClusterDataTaskTimeout(200, TimeUnit.MILLISECONDS);
            service.hang();
            assertThrows(RuntimeException.class, () -> service.releaseView(view),
                    "the truncate of the data file did not time out");
        } finally {
            service.unhang();
        }

        assertFalse(view.getLocalMember().isLeader(), "the stopped view reports leadership");
        assertTrue(isLockFree(root.resolve(NAMESPACE)), "the stopped view holds the lock");
    }

    private CamelContext startContext() {
        CamelContext context = new DefaultCamelContext();
        context.disableJMX();
        contexts.add(context);
        context.start();
        return context;
    }

    private FileLockClusterService newService(String id) {
        FileLockClusterService service = new FileLockClusterService();
        configure(service, id);
        return service;
    }

    private void configure(FileLockClusterService service, String id) {
        service.setId("node-" + id);
        service.setRoot(root.toString());
        service.setAcquireLockDelay(100, TimeUnit.MILLISECONDS);
        service.setAcquireLockInterval(500, TimeUnit.MILLISECONDS);
        service.setClusterDataTaskTimeout(30, TimeUnit.SECONDS);
    }

    private static boolean isLockFree(Path lockFile) throws Exception {
        try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            FileLock lock = channel.tryLock();
            if (lock == null) {
                return false;
            }
            lock.release();
            return true;
        } catch (OverlappingFileLockException e) {
            // held by another channel of this JVM
            return false;
        }
    }

    /**
     * Once {@link #hang()} is called, no leadership check runs any more, and the cluster data tasks run on an executor
     * whose only thread is blocked, so that they time out like the I/O on a hanging network file system.
     */
    private static final class HangingFileLockClusterService extends FileLockClusterService {
        private final CountDownLatch unblock = new CountDownLatch(1);
        private final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1);
        private final ExecutorService hangingExecutor = Executors.newSingleThreadExecutor();
        private volatile boolean hanging;

        void hang() throws InterruptedException {
            // wait until no leadership check is running, and hold the next ones
            CountDownLatch paused = new CountDownLatch(1);
            scheduler.execute(() -> {
                paused.countDown();
                awaitUnblock();
            });
            assertTrue(paused.await(10, TimeUnit.SECONDS), "the leadership checks were not paused");
            hangingExecutor.execute(this::awaitUnblock);
            hanging = true;
        }

        void unhang() {
            hanging = false;
            unblock.countDown();
            hangingExecutor.shutdown();
        }

        private void awaitUnblock() {
            try {
                unblock.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        ScheduledExecutorService getExecutor() {
            return scheduler;
        }

        @Override
        ExecutorService getClusterDataTaskExecutor() {
            return hanging ? hangingExecutor : super.getClusterDataTaskExecutor();
        }

        @Override
        protected void doStop() throws Exception {
            super.doStop();
            scheduler.shutdownNow();
        }
    }

    /**
     * Holds the first attempt to open the lock file until {@link #release} is counted down, and schedules the
     * leadership checks on an executor the test can inspect.
     */
    private static final class HookedFileLockClusterService extends FileLockClusterService {
        final CountDownLatch reached = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1);

        @Override
        protected FileLockClusterView createView(String namespace) {
            return new FileLockClusterView(this, namespace) {
                @Override
                RandomAccessFile createRandomAccessFile(Path path) throws ExecutionException, TimeoutException {
                    if (path.getFileName().toString().equals(NAMESPACE) && reached.getCount() > 0) {
                        reached.countDown();
                        try {
                            release.await(30, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    return super.createRandomAccessFile(path);
                }
            };
        }

        @Override
        ScheduledExecutorService getExecutor() {
            return scheduler;
        }

        @Override
        protected void doStop() throws Exception {
            super.doStop();
            scheduler.shutdownNow();
        }
    }
}
