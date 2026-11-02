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
package org.apache.camel.impl;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.util.concurrent.SizedScheduledExecutorService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A split or multicast with a timeout schedules a task that refers to all its exchanges, and cancels it when it
 * completes. That task must leave the queue when it is cancelled, or the exchanges stay in memory for the whole
 * timeout.
 * <p/>
 * This covers platform threads, and virtual threads when run with -Dcamel.threads.virtual.enabled=true.
 */
public class ScheduledThreadPoolRemoveOnCancelTest {

    @Test
    public void testCancelledTaskLeavesTheQueue() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();
            // as MulticastProcessor creates the pool for its timeout
            ScheduledExecutorService pool = context.getExecutorServiceManager().newScheduledThreadPool(this, "timeout", 0);

            ScheduledFuture<?> timeout = pool.schedule(() -> {
                // never runs
            }, 1, TimeUnit.HOURS);
            timeout.cancel(false);

            assertEquals(0, executorOf(pool).getQueue().size());
        }
    }

    private static ScheduledThreadPoolExecutor executorOf(ScheduledExecutorService pool) {
        return pool instanceof SizedScheduledExecutorService sized
                ? sized.getScheduledThreadPoolExecutor() : (ScheduledThreadPoolExecutor) pool;
    }
}
