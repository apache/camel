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
package org.apache.camel.util.concurrent;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
public class SizedScheduledExecutorServiceTest {

    @Test
    public void testSizedScheduledExecutorService() {
        ScheduledThreadPoolExecutor delegate = new ScheduledThreadPoolExecutor(5);

        @SuppressWarnings("resource")
        // Will be shutdown in the finally clause.
        SizedScheduledExecutorService sized = new SizedScheduledExecutorService(delegate, 2);
        try {
            Runnable task = new Runnable() {
                @Override
                public void run() {
                    // noop
                }
            };

            sized.schedule(task, 2, TimeUnit.SECONDS);
            sized.schedule(task, 3, TimeUnit.SECONDS);

            RejectedExecutionException e
                    = assertThrows(RejectedExecutionException.class, () -> sized.schedule(task, 4, TimeUnit.SECONDS),
                            "Should have thrown a RejectedExecutionException");
            assertEquals("Task rejected due queue size limit reached", e.getMessage());
        } finally {
            sized.shutdownNow();
            assertTrue(sized.isShutdown() || sized.isTerminating(), "Should be shutdown");
            assertTrue(delegate.isShutdown() || sized.isTerminating(), "Should be shutdown");
        }
    }
}
