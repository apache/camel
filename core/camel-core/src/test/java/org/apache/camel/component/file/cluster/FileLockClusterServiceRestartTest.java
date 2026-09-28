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

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.cluster.CamelClusterView;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@link FileLockClusterService} that is stopped and started again in the same JVM, for example with the stop and
 * start JMX operations of the cluster service, must be able to take the leadership again.
 */
public class FileLockClusterServiceRestartTest {

    private static final String NAMESPACE = "ns";

    @TempDir
    Path root;

    private CamelContext context;
    private FileLockClusterService service;

    @BeforeEach
    public void setUp() throws Exception {
        service = new FileLockClusterService();
        service.setId("node-A");
        service.setRoot(root.toString());
        service.setAcquireLockDelay(100, TimeUnit.MILLISECONDS);
        service.setAcquireLockInterval(200, TimeUnit.MILLISECONDS);

        context = new DefaultCamelContext();
        context.disableJMX();
        context.addService(service);
        context.start();
    }

    @AfterEach
    public void tearDown() {
        context.stop();
    }

    @Test
    public void testLeaderAgainAfterServiceRestart() throws Exception {
        CamelClusterView view = service.getView(NAMESPACE);
        awaitLeader(view);

        service.stop();
        assertFalse(view.getLocalMember().isLeader());
        assertTrue(isLockFree(root.resolve(NAMESPACE)), "the stopped service holds the lock");

        // the view is started again with the service, and reads and writes the cluster data again
        service.start();
        awaitLeader(view);

        // and it still works after another restart
        service.stop();
        service.start();
        awaitLeader(view);
    }

    private void awaitLeader(CamelClusterView view) {
        // the leader writes its heartbeat to the data file through the cluster data task executor
        await().atMost(10, TimeUnit.SECONDS).until(() -> view.getLocalMember().isLeader()
                && FileLockClusterUtils.readClusterLeaderInfo(root.resolve(NAMESPACE + ".dat")) != null);
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
}
