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
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.cluster.CamelClusterEventListener;
import org.apache.camel.cluster.CamelClusterView;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The only member of a cluster loses the leadership after a failed read of the cluster data. The data file still holds
 * its id, so the member tries to take the leadership back at once, and once the file system is healthy again it must
 * get it, even if that first attempt fails too.
 */
class FileLockClusterViewReclaimLeadershipTest {

    private static final String NAMESPACE = "ns";

    @TempDir
    Path root;

    private CamelContext context;

    @AfterEach
    void stopContext() {
        if (context != null) {
            context.stop();
        }
    }

    @Test
    void memberTakesTheLeadershipBackAfterAFailedAttempt() throws Exception {
        loseAndTakeBackLeadership(true);
    }

    @Test
    void memberTakesTheLeadershipBackAtOnce() throws Exception {
        loseAndTakeBackLeadership(false);
    }

    private void loseAndTakeBackLeadership(boolean failFirstAttempt) throws Exception {
        AtomicInteger readFailures = new AtomicInteger();
        AtomicInteger openFailures = new AtomicInteger();
        FileLockClusterService service = new FileLockClusterService() {
            @Override
            protected FileLockClusterView createView(String namespace) {
                return new FileLockClusterView(this, namespace) {
                    @Override
                    FileLockClusterLeaderInfo readClusterLeaderInfo() throws Exception {
                        if (readFailures.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                            throw new IOException("Simulated failure reading the cluster data");
                        }
                        return super.readClusterLeaderInfo();
                    }

                    @Override
                    RandomAccessFile createRandomAccessFile(Path path) throws ExecutionException, TimeoutException {
                        if (openFailures.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                            throw new ExecutionException(new IOException("Simulated failure opening " + path));
                        }
                        return super.createRandomAccessFile(path);
                    }
                };
            }
        };
        service.setId("node-A");
        service.setRoot(root.toString());
        service.setAcquireLockDelay(100, TimeUnit.MILLISECONDS);
        service.setAcquireLockInterval(100, TimeUnit.MILLISECONDS);
        service.setHeartbeatTimeoutMultiplier(2);

        context = new DefaultCamelContext();
        context.disableJMX();
        context.start();
        context.addService(service);
        CamelClusterView view = service.getView(NAMESPACE);
        Path dataFile = root.resolve(NAMESPACE + ".dat");
        await().atMost(10, TimeUnit.SECONDS).until(() -> view.getLocalMember().isLeader()
                && FileLockClusterUtils.readClusterLeaderInfo(dataFile) != null);
        String id = FileLockClusterUtils.readClusterLeaderInfo(dataFile).getId();

        // record the leadership changes (true: this member is leader) instead of polling for the loss: a member that
        // takes the leadership back at once is follower for only one check, which polling can miss. The listener is
        // told the current leader when it is added, so the first entry is true
        List<Boolean> leadership = new CopyOnWriteArrayList<>();
        view.addEventListener((CamelClusterEventListener.Leadership) (v, leader) -> leadership
                .add(leader != null && leader.isLocal()));
        assertEquals(List.of(true), leadership);

        // the next read of the leader fails, so it gives up the leadership. If failFirstAttempt, opening the lock file
        // fails in its first try to take the leadership back. The file system is healthy after that, so the member must
        // take the leadership back
        if (failFirstAttempt) {
            openFailures.set(1);
        }
        readFailures.set(1);
        await().atMost(10, TimeUnit.SECONDS).until(() -> leadership.size() >= 3);
        assertEquals(List.of(true, false, true), leadership);
        assertEquals(0, readFailures.get());
        assertEquals(0, openFailures.get());
        assertTrue(view.getLocalMember().isLeader());
        // the member is told it is leader before it writes the cluster data, which it truncates first
        await().atMost(10, TimeUnit.SECONDS).until(() -> {
            FileLockClusterLeaderInfo info = FileLockClusterUtils.readClusterLeaderInfo(dataFile);
            return info != null && id.equals(info.getId());
        });
    }
}
