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
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.cluster.CamelClusterView;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
        AtomicInteger keyFailures = new AtomicInteger();
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
                    Path resolveLockPathKey() throws IOException {
                        if (keyFailures.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                            throw new IOException("Simulated failure resolving the lock file directory");
                        }
                        return super.resolveLockPathKey();
                    }
                };
            }
        };
        service.setId("node-A");
        service.setRoot(root.toString());
        service.setAcquireLockDelay(100, TimeUnit.MILLISECONDS);
        service.setAcquireLockInterval(100, TimeUnit.MILLISECONDS);
        service.setHeartbeatTimeoutMultiplier(2);
        service.setClusterDataTaskMaxAttempts(2);

        context = new DefaultCamelContext();
        context.disableJMX();
        context.start();
        context.addService(service);
        CamelClusterView view = service.getView(NAMESPACE);
        Path dataFile = root.resolve(NAMESPACE + ".dat");
        await().atMost(10, TimeUnit.SECONDS).until(() -> view.getLocalMember().isLeader()
                && FileLockClusterUtils.readClusterLeaderInfo(dataFile) != null);
        String id = FileLockClusterUtils.readClusterLeaderInfo(dataFile).getId();

        // the next read of the leader fails, so it gives up the leadership. If failFirstAttempt, every attempt of the
        // task that resolves the lock file key fails in its first try to take the leadership back
        if (failFirstAttempt) {
            keyFailures.set(service.getClusterDataTaskMaxAttempts());
        }
        readFailures.set(1);
        await().atMost(10, TimeUnit.SECONDS).until(() -> !view.getLocalMember().isLeader());
        await().atMost(10, TimeUnit.SECONDS).until(() -> readFailures.get() == 0 && keyFailures.get() == 0);

        // the file system is healthy from now on
        await().atMost(10, TimeUnit.SECONDS).until(() -> view.getLocalMember().isLeader());
        assertEquals(id, FileLockClusterUtils.readClusterLeaderInfo(dataFile).getId());
    }
}
