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

package org.apache.camel.support.task;

import java.time.Duration;
import java.util.concurrent.Executors;

import org.apache.camel.support.task.budget.Budgets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * On most tests we evaluate a range or task count executions within the time budget because:
 * very fast systems may be able to kick off the task very quickly (i.e.: at the 0th millisecond). Combined
 * with the time drift and limits in time precision for different platforms, as well as scheduler differences
 * between scheduler behavior for each OS, this means that an execution may happen at the last second (i.e:
 * at 0th, 1st, 2nd, 3rd and 4th).
 */
public class BackgroundTaskTest extends TaskTestSupport {

    @DisplayName("Test that the task does not run for more than the max duration when using a supplier with no delay")
    @Test
    @Timeout(10)
    void testRunNoMoreSupplier() {
        /*
         * It should run at most 5 times in 4 seconds because:
         * 1) there is no delay.
         * 2) the interval is of 1 second
         */
        BackgroundTask task = Tasks.backgroundTask()
                .withScheduledExecutor(Executors.newSingleThreadScheduledExecutor())
                .withBudget(Budgets.timeBudget()
                        .withInterval(Duration.ofSeconds(1))
                        .withInitialDelay(Duration.ZERO)
                        .withMaxDuration(Duration.ofSeconds(4))
                        .build())
                .build();

        boolean completed = task.run(this::booleanSupplier);
        assertTrue(taskCount <= maxIterations);
        assertFalse(completed, "The task did not complete, the return should be false");

        Duration duration = task.elapsed();
        assertNotNull(duration);
        assertFalse(duration.isNegative());
        assertFalse(duration.isZero());
        assertTrue(duration.getSeconds() >= 4);
        assertTrue(duration.getSeconds() <= 5);
    }

    @DisplayName("Test that the task does not run for more than the max duration when using a supplier with delay")
    @Test
    @Timeout(10)
    void testRunNoMoreSupplierWithDelay() {
        /*
         * It should run at most 4 times in 4 seconds because:
         * 1) there is a delay.
         * 2) the interval is of 1 second
         */
        BackgroundTask task = Tasks.backgroundTask()
                .withScheduledExecutor(Executors.newSingleThreadScheduledExecutor())
                .withBudget(Budgets.timeBudget()
                        .withInterval(Duration.ofSeconds(1))
                        .withInitialDelay(Duration.ofSeconds(1))
                        .withMaxDuration(Duration.ofSeconds(4))
                        .build())
                .build();

        boolean completed = task.run(this::booleanSupplier);
        assertTrue((maxIterations - 1) <= taskCount);
        assertFalse(completed, "The task did not complete, the return should be false");

        Duration duration = task.elapsed();
        assertNotNull(duration);
        assertFalse(duration.isNegative());
        assertFalse(duration.isZero());
        assertTrue(duration.getSeconds() >= 4);
        assertTrue(duration.getSeconds() <= 5);
    }

    @DisplayName("Test that the task does not run for more than the max duration when using a predicate and an initial delay")
    @Test
    @Timeout(10)
    void testRunNoMorePredicate() {
        /*
         * It should run at most 5 times in 4 seconds because:
         * 1) there is no delay.
         * 2) the interval is of 1 second
         */
        BackgroundTask task = Tasks.backgroundTask()
                .withScheduledExecutor(Executors.newSingleThreadScheduledExecutor())
                .withBudget(Budgets.timeBudget()
                        .withInterval(Duration.ofSeconds(1))
                        .withInitialDelay(Duration.ZERO)
                        .withMaxDuration(Duration.ofSeconds(4))
                        .build())
                .build();

        boolean completed = task.run(this::taskPredicate, new Object());
        assertTrue(taskCount <= maxIterations);
        assertFalse(completed, "The task did not complete, the return should be false");

        Duration duration = task.elapsed();
        assertNotNull(duration);
        assertFalse(duration.isNegative());
        assertFalse(duration.isZero());
        assertTrue(duration.getSeconds() >= 4);
        assertTrue(duration.getSeconds() <= 5);
    }

    @DisplayName("Test that the task stops running once the predicate is true")
    @Test
    @Timeout(10)
    void testRunNoMorePredicateWithSuccess() {
        /*
         * It should run 3 times in 4 seconds because when the task return successfully, the result must be
         * deterministic.
         */
        BackgroundTask task = Tasks.backgroundTask()
                .withScheduledExecutor(Executors.newSingleThreadScheduledExecutor())
                .withBudget(Budgets.timeBudget()
                        .withInterval(Duration.ofSeconds(1))
                        .withInitialDelay(Duration.ZERO)
                        .withMaxDuration(Duration.ofSeconds(4))
                        .build())
                .build();

        boolean completed = task.run(this::taskPredicateWithDeterministicStop, Integer.valueOf(3));
        assertEquals(3, taskCount);
        assertTrue(completed, "The task did complete, the return should be true");
    }

    @DisplayName("Test that the task stops running once the predicate is true when the test is slow")
    @Test
    @Timeout(10)
    void testRunNoMorePredicateWithTimeout() {
        /*
         * Each execution takes 2 seconds to complete. Therefore, running the task every second means that the task
         * count should not exceed 2 because anything greater than that means that the timeout was exceeded.
         */
        BackgroundTask task = Tasks.backgroundTask()
                .withScheduledExecutor(Executors.newSingleThreadScheduledExecutor())
                .withBudget(Budgets.timeBudget()
                        .withInterval(Duration.ofSeconds(1))
                        .withInitialDelay(Duration.ZERO)
                        .withMaxDuration(Duration.ofSeconds(4))
                        .build())
                .build();

        boolean completed = task.run(this::taskPredicateWithDeterministicStopSlow, Integer.valueOf(3));
        assertTrue(taskCount <= 2, "Slow task: it should not run more than 2 times in 4 seconds");

        Duration duration = task.elapsed();
        assertNotNull(duration);
        assertFalse(duration.isNegative());
        assertFalse(duration.isZero());
        assertTrue(duration.getSeconds() >= 4);
        assertTrue(duration.getSeconds() <= 5);
        assertFalse(completed, "The task did not complete because of timeout, the return should be false");
    }

    @DisplayName("Test that the task stops running once the predicate is true when the test is slow")
    @Test
    @Timeout(10)
    void testRunNoMorePredicateWithTimeoutAndDelay() {
        /*
         * Each execution takes 2 seconds to complete, but it has a 1-second delay. Therefore, running the task every
         * second means that the task count should not exceed 1 because anything greater than that means that the
         * timeout was exceeded.
         */
        BackgroundTask task = Tasks.backgroundTask()
                .withScheduledExecutor(Executors.newSingleThreadScheduledExecutor())
                .withBudget(Budgets.timeBudget()
                        .withInterval(Duration.ofSeconds(1))
                        .withInitialDelay(Duration.ofSeconds(1))
                        .withMaxDuration(Duration.ofSeconds(4))
                        .build())
                .build();

        boolean completed = task.run(this::taskPredicateWithDeterministicStopSlow, Integer.valueOf(3));
        Duration duration = task.elapsed();
        assertNotNull(duration);
        assertFalse(duration.isNegative());
        assertFalse(duration.isZero());
        assertTrue(duration.getSeconds() >= 4);
        assertTrue(duration.getSeconds() <= 5);
        assertFalse(completed, "The task did not complete because of timeout, the return should be false");
    }
}
