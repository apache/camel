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
package org.apache.camel.test.executor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PooledExecutorTest {
    static final int THREAD_COUNT = 2;
    Logger log = LoggerFactory.getLogger(this.getClass());
    TestExecutor instance;

    @BeforeEach
    public void setUp() {
        instance = new TestExecutor(THREAD_COUNT);
    }

    @AfterEach
    public void tearDown() {
        instance.stop();
    }

    /**
     * Verify that the executor accepts runnables up to the thread pool capacity and rejects excess ones. With a
     * SynchronousQueue and THREAD_COUNT threads, at most THREAD_COUNT runnables can be accepted simultaneously.
     */
    @Test
    void testAddRunnable() {
        int runnableCount = 3;
        int runCount = 5;

        log.info("Starting first set of runnables");
        List<TestRunnable> firstBatch = startRunnables(runnableCount, runCount);
        // SynchronousQueue with 2 threads: at most THREAD_COUNT runnables can be accepted
        assertTrue(firstBatch.size() <= THREAD_COUNT,
                "Accepted runnables should not exceed thread pool size, but got " + firstBatch.size());
        assertFalse(firstBatch.isEmpty(), "At least one runnable should have been accepted");

        log.info("Starting second set of runnables");
        // Second batch: threads are still occupied by first batch (each runnable sleeps runCount seconds),
        // so all should be rejected
        List<TestRunnable> secondBatch = startRunnables(runnableCount, runCount);
        assertEquals(0, secondBatch.size(), "All runnables in second batch should be rejected (threads still busy)");
    }

    List<TestRunnable> startRunnables(int runnableCount, int runCount) {
        List<TestRunnable> accepted = new ArrayList<>();
        for (int id = 1; id <= runnableCount; ++id) {
            TestRunnable runnable = new TestRunnable(id, runCount);
            try {
                instance.addRunnable(runnable);
                accepted.add(runnable);
            } catch (RejectedExecutionException rejectedEx) {
                log.warn("Unable to add Runnable {}", id, rejectedEx);
            }
        }
        return accepted;
    }
}
