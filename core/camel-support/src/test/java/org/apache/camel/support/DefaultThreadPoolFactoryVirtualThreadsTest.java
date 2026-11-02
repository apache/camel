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
package org.apache.camel.support;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.util.concurrent.CamelThreadFactory;
import org.apache.camel.util.concurrent.SizedScheduledExecutorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The thread pools for virtual threads must honor the same settings as those for platform threads. The type of thread
 * is decided once per JVM, so this test runs in its own execution of surefire with virtual threads enabled, see the
 * pom.xml of this module.
 */
@EnabledIfSystemProperty(named = "camel.threads.virtual.enabled", matches = "true")
class DefaultThreadPoolFactoryVirtualThreadsTest {

    private final DefaultThreadPoolFactory factory = new DefaultThreadPoolFactory();
    private final CamelThreadFactory threadFactory = new CamelThreadFactory("#name#", "test", true);

    @Test
    void cancelledTaskLeavesTheQueue() {
        // a split or multicast cancels its timeout when it completes, and that task refers to all its exchanges
        ScheduledExecutorService pool = scheduledPool(0);
        try {
            ScheduledFuture<?> timeout = pool.schedule(() -> {
                // never runs
            }, 1, TimeUnit.HOURS);
            timeout.cancel(false);

            assertEquals(0, executorOf(pool).getQueue().size());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void taskScheduledAfterShutdownIsDiscardedLikeOnPlatformThreads() {
        // the default rejected policy of a profile is CallerRuns, which discards a task once the pool is shut down
        ScheduledExecutorService pool = scheduledPool(1);
        pool.shutdownNow();

        assertDoesNotThrow(() -> pool.schedule(() -> {
            // never runs
        }, 1, TimeUnit.HOURS));
    }

    @Test
    void scheduledPoolRunsAsManyTasksAsThePoolSize() throws Exception {
        // concurrentConsumers of a scheduled consumer depends on this
        ScheduledExecutorService pool = scheduledPool(3);
        try {
            CountDownLatch allRunning = new CountDownLatch(3);
            for (int i = 0; i < 3; i++) {
                pool.schedule(() -> {
                    allRunning.countDown();
                    try {
                        allRunning.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }, 0, TimeUnit.MILLISECONDS);
            }

            assertTrue(allRunning.await(5, TimeUnit.SECONDS), "The tasks ran one at a time");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void maxPoolSizeBelowPoolSizeIsRejected() {
        Exception e = assertThrows(IllegalArgumentException.class, () -> factory.newThreadPool(profile(5, 2), threadFactory));
        assertEquals("MaxPoolSize must be >= corePoolSize, was 2 >= 5", e.getMessage());
    }

    @Test
    void negativePoolSizeIsRejected() {
        Exception e = assertThrows(IllegalArgumentException.class, () -> factory.newThreadPool(profile(-1, 2), threadFactory));
        assertEquals("CorePoolSize must be >= 0, was -1", e.getMessage());
    }

    private static ScheduledThreadPoolExecutor executorOf(ScheduledExecutorService pool) {
        return pool instanceof SizedScheduledExecutorService sized
                ? sized.getScheduledThreadPoolExecutor() : (ScheduledThreadPoolExecutor) pool;
    }

    private ScheduledExecutorService scheduledPool(int poolSize) {
        return factory.newScheduledThreadPool(profile(poolSize, 20), threadFactory);
    }

    private static ThreadPoolProfile profile(int poolSize, int maxPoolSize) {
        ThreadPoolProfile profile = new ThreadPoolProfile("test");
        profile.setPoolSize(poolSize);
        profile.setMaxPoolSize(maxPoolSize);
        profile.setMaxQueueSize(1000);
        profile.setKeepAliveTime(60L);
        profile.setTimeUnit(TimeUnit.SECONDS);
        return profile;
    }
}
