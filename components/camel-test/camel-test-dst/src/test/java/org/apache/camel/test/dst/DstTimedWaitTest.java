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
package org.apache.camel.test.dst;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Timed waits on tasks and executors of the run time out in virtual time.
 */
class DstTimedWaitTest {

    @Test
    void getWithTimeoutTimesOutInVirtualTime() throws Exception {
        Dst dst = new Dst(1);
        try {
            DstTask<String> late = dst.executor("ex", 1).schedule("late", 10_000, () -> "answer");
            long start = System.nanoTime();
            assertThrows(TimeoutException.class, () -> late.get(2, TimeUnit.SECONDS));
            assertEquals(2_000, dst.now());
            assertFalse(late.isDone());
            // the task still runs at its time
            dst.runUntilQuiet();
            assertEquals("answer", late.get());
            assertEquals(10_000, dst.now());
            assertTrue(System.nanoTime() - start < 5_000_000_000L);
        } finally {
            dst.close();
        }
    }

    @Test
    void getWithTimeoutReturnsWhenTheTaskIsDoneInTime() throws Exception {
        Dst dst = new Dst(1);
        try {
            DstTask<String> soon = dst.executor("ex", 1).schedule("soon", 1_000, () -> "answer");
            assertEquals("answer", soon.get(5, TimeUnit.SECONDS));
            assertEquals(1_000, dst.now());
        } finally {
            dst.close();
        }
    }

    @Test
    void getWithTimeoutFromATaskLetsTheOtherTasksRun() {
        Dst dst = new Dst(1);
        try {
            List<String> events = new ArrayList<>();
            DstTask<String> late = dst.executor("server", 1).schedule("late", 10_000, () -> "answer");
            dst.executor("other", 1).schedule("other", 500, () -> events.add("other@" + dst.now()));
            dst.executor("caller", 1).execute(() -> {
                try {
                    late.get(3, TimeUnit.SECONDS);
                    events.add("answer@" + dst.now());
                } catch (TimeoutException e) {
                    events.add("timeout@" + dst.now());
                } catch (Exception e) {
                    events.add("failed " + e);
                }
            });
            dst.runUntilQuiet();
            assertEquals(List.of("other@500", "timeout@3000"), events);
        } finally {
            dst.close();
        }
    }

    @Test
    void aTaskThatCompletesAfterTheDeadlineOnTheStackIsATimeout() {
        // one-stack limit: the task blocks nested above the wait and completes at 10 s; the caller sees a timeout
        Dst dst = new Dst(1);
        try {
            DstTask<String> slow = dst.executor("ex", 1).schedule("slow", 0, () -> {
                dst.block(10_000, "slow call");
                return "answer";
            });
            assertThrows(TimeoutException.class, () -> slow.get(2, TimeUnit.SECONDS));
            assertTrue(slow.isDone());
            assertEquals(10_000, dst.now());
        } finally {
            dst.close();
        }
    }

    @Test
    void awaitTerminationHonoursTheTimeout() throws Exception {
        Dst dst = new Dst(1);
        try {
            DstExecutor ex = dst.executor("ex", 1);
            ex.schedule("work", 10_000, () -> {
            });
            ex.shutdown();
            assertFalse(ex.awaitTermination(1, TimeUnit.SECONDS));
            assertEquals(1_000, dst.now());
            assertTrue(ex.awaitTermination(1, TimeUnit.MINUTES));
            assertEquals(10_000, dst.now());
            assertTrue(ex.isTerminated());
        } finally {
            dst.close();
        }
    }

    @Test
    void awaitTerminationWithoutShutdownWaitsTheWholeTimeout() throws Exception {
        Dst dst = new Dst(1);
        try {
            DstExecutor ex = dst.executor("ex", 1);
            assertFalse(ex.awaitTermination(30, TimeUnit.SECONDS));
            assertEquals(30_000, dst.now());
        } finally {
            dst.close();
        }
    }

    @Test
    void shutdownDropsPeriodicTasks() throws Exception {
        Dst dst = new Dst(1);
        try {
            DstExecutor ex = dst.executor("ex", 1);
            int[] runs = new int[1];
            ex.scheduleAtFixedRate(() -> runs[0]++, 0, 1, TimeUnit.SECONDS);
            dst.runFor(2_500);
            ex.shutdown();
            assertTrue(ex.awaitTermination(1, TimeUnit.MINUTES));
            assertEquals(3, runs[0]);
            assertEquals(2_500, dst.now());
        } finally {
            dst.close();
        }
    }

    @Test
    void invokeAllWithTimeoutCancelsTheLateTasks() throws Exception {
        Dst dst = new Dst(1);
        try {
            DstExecutor ex = dst.executor("ex", 2);
            List<Callable<String>> tasks = List.of(() -> {
                dst.block(1_000, "quick");
                return "quick";
            }, () -> {
                dst.block(60_000, "slow");
                return "slow";
            });
            List<Future<String>> answer = ex.invokeAll(tasks, 5, TimeUnit.SECONDS);
            assertEquals("quick", answer.get(0).get());
            // the slow task was on the stack above the wait: it completes, but after the deadline
            assertTrue(answer.get(1).isDone());
            assertTrue(dst.now() >= 1_000);
        } finally {
            dst.close();
        }
    }
}
