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
package org.apache.camel.component.file.strategy;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A delayed release that the thread pool rejects is not kept until the stop of the read lock.
 */
class DelayedReadLockReleaseTasksTest {

    @Test
    void rejectedReleaseIsNotKeptForTheStop() {
        ScheduledThreadPoolExecutor accepting = new ScheduledThreadPoolExecutor(1);
        // a shut down pool rejects with the default abort policy, as a pool with a full queue does
        ScheduledThreadPoolExecutor rejecting = new ScheduledThreadPoolExecutor(1);
        rejecting.shutdown();
        try {
            DelayedReadLockReleaseTasks tasks = new DelayedReadLockReleaseTasks();
            AtomicInteger scheduled = new AtomicInteger();
            AtomicInteger rejected = new AtomicInteger();

            tasks.schedule(accepting, scheduled::incrementAndGet, 60000);
            assertThrows(RejectedExecutionException.class,
                    () -> tasks.schedule(rejecting, rejected::incrementAndGet, 60000));

            // the stop runs the pending release that was scheduled, and not the one that was rejected
            assertEquals(1, tasks.runPending(), "The stop did not count the one release it ran");
            assertEquals(1, scheduled.get(), "The scheduled release did not run at the stop");
            assertEquals(0, rejected.get(), "The rejected release was kept and ran at the stop");
            // a release runs once: nothing is left for a second stop
            assertEquals(0, tasks.runPending(), "A release ran or was counted twice");
            assertEquals(1, scheduled.get(), "The scheduled release ran twice");
        } finally {
            accepting.shutdownNow();
        }
    }
}
