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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.cluster.CamelClusterView;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two cluster views of one JVM on the same lock file (two CamelContexts with a FileLockClusterService on the same
 * root). The file locks are locks of the process, and closing any channel of the lock file releases them all at OS
 * level (see {@link java.nio.channels.FileLock}): the follower must not open (and close) a channel on the lock file
 * while the leader of its JVM holds the lock, as a member in another JVM could then take the lock too.
 */
class FileLockClusterViewSameJvmTest {

    private static final String NAMESPACE = "ns";

    @TempDir
    Path root;

    private final List<CamelContext> contexts = new ArrayList<>();

    @AfterEach
    void stopContexts() {
        contexts.forEach(CamelContext::stop);
    }

    @Test
    void followerDoesNotOpenTheLockFileHeldInItsJvm() throws Exception {
        CamelContext contextA = startContext();
        FileLockClusterService serviceA = newService(new FileLockClusterService(), "A");
        contextA.addService(serviceA);
        CamelClusterView viewA = serviceA.getView(NAMESPACE);
        await().atMost(10, TimeUnit.SECONDS).until(() -> viewA.getLocalMember().isLeader()
                && FileLockClusterUtils.readClusterLeaderInfo(root.resolve(NAMESPACE + ".dat")) != null);

        // the leadership checks of A stall (as in a long GC pause), so its heartbeat gets stale: this is the case where
        // the lock must keep the members of other JVMs out
        CountDownLatch stall = new CountDownLatch(1);
        serviceA.getExecutor().execute(() -> {
            try {
                stall.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        try {
            CountingFileLockClusterService serviceB = newService(new CountingFileLockClusterService(), "B");
            CamelContext contextB = startContext();
            contextB.addService(serviceB);
            CamelClusterView viewB = serviceB.getView(NAMESPACE);

            // B finds the leader stale and tries to take the leadership. Once a later check has started, that attempt
            // is over
            await().atMost(10, TimeUnit.SECONDS).until(() -> serviceB.staleChecks.get() >= 2);

            assertEquals(0, serviceB.lockFileOpens.get(),
                    "the follower opened the lock file that a view of its JVM holds");
            assertTrue(viewA.getLocalMember().isLeader());
            assertFalse(viewB.getLocalMember().isLeader());
        } finally {
            stall.countDown();
        }
    }

    @Test
    void followerTakesOverWhenTheLeaderOfItsJvmStops() throws Exception {
        CamelContext contextA = startContext();
        FileLockClusterService serviceA = newService(new FileLockClusterService(), "A");
        contextA.addService(serviceA);
        CamelClusterView viewA = serviceA.getView(NAMESPACE);
        await().atMost(10, TimeUnit.SECONDS).until(() -> viewA.getLocalMember().isLeader());

        CountingFileLockClusterService serviceB = newService(new CountingFileLockClusterService(), "B");
        CamelContext contextB = startContext();
        contextB.addService(serviceB);
        CamelClusterView viewB = serviceB.getView(NAMESPACE);
        await().atMost(10, TimeUnit.SECONDS).until(() -> serviceB.checks.get() >= 2);
        assertFalse(viewB.getLocalMember().isLeader());

        // the leader releases the lock, so the follower of the same JVM can take it
        contextA.stop();
        await().atMost(10, TimeUnit.SECONDS).until(() -> viewB.getLocalMember().isLeader());
        assertFalse(viewA.getLocalMember().isLeader());
    }

    private CamelContext startContext() {
        CamelContext context = new DefaultCamelContext();
        context.disableJMX();
        contexts.add(context);
        context.start();
        return context;
    }

    private <T extends FileLockClusterService> T newService(T service, String id) {
        service.setId("node-" + id);
        service.setRoot(root.toString());
        service.setAcquireLockDelay(100, TimeUnit.MILLISECONDS);
        service.setAcquireLockInterval(100, TimeUnit.MILLISECONDS);
        service.setHeartbeatTimeoutMultiplier(2);
        service.setClusterDataTaskTimeout(30, TimeUnit.SECONDS);
        return service;
    }

    /**
     * Counts the leadership checks of its view, the checks that found the leader stale, and the times the view opened
     * the lock file.
     */
    private static final class CountingFileLockClusterService extends FileLockClusterService {
        final AtomicInteger checks = new AtomicInteger();
        final AtomicInteger staleChecks = new AtomicInteger();
        final AtomicInteger lockFileOpens = new AtomicInteger();

        @Override
        protected FileLockClusterView createView(String namespace) {
            return new FileLockClusterView(this, namespace) {
                @Override
                boolean isLeaderStale(FileLockClusterLeaderInfo info, FileLockClusterLeaderInfo previous) {
                    checks.incrementAndGet();
                    boolean stale = super.isLeaderStale(info, previous);
                    if (stale) {
                        staleChecks.incrementAndGet();
                    }
                    return stale;
                }

                @Override
                RandomAccessFile createRandomAccessFile(Path path) throws ExecutionException, TimeoutException {
                    if (path.getFileName().toString().equals(NAMESPACE)) {
                        lockFileOpens.incrementAndGet();
                    }
                    return super.createRandomAccessFile(path);
                }
            };
        }
    }
}
