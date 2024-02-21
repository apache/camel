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
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BackgroundIterationTimeTaskTest extends TaskTestSupport {

    @DisplayName("Test that the task does not run for more than the max iterations when using a supplier")
    @Test
    @Timeout(10)
    void testRunNoMoreSupplier() {
        // It should run 3 times only because that's the max iterations
        BackgroundTask task = Tasks.backgroundTask()
                .withScheduledExecutor(Executors.newSingleThreadScheduledExecutor())
                .withBudget(Budgets.iterationTimeBudget()
                        .withMaxIterations(3)
                        .withInterval(Duration.ofSeconds(1))
                        .withInitialDelay(Duration.ZERO)
                        // use unlimited duration so we're sure that the task is really canceled after maxIterations
                        .withUnlimitedDuration()
                        .build())
                .build();

        boolean completed = task.run(this::booleanSupplier);
        assertEquals(3, taskCount);
        assertFalse(completed, "The task did not complete, the return should be false");
    }

    @DisplayName("Test that the task does not run for more than the max iterations when using a supplier and initial delay")
    @Test
    @Timeout(10)
    void testRunNoMoreSupplierWithDelay() {
        BackgroundTask task = Tasks.backgroundTask()
                .withScheduledExecutor(Executors.newSingleThreadScheduledExecutor())
                .withBudget(Budgets.iterationTimeBudget()
                        .withInterval(Duration.ofSeconds(1))
                        .withInitialDelay(Duration.ofSeconds(1))
                        .withMaxDuration(Duration.ofSeconds(5))
                        .withMaxIterations(5)
                        .build())
                .build();

        boolean completed = task.run(this::booleanSupplier);
        assertTrue(maxIterations > taskCount, "The task execution should not exceed the max iterations");
        assertFalse(completed, "The task did not complete, the return should be false");
    }

    @DisplayName("Test that the task does not run for more than the max iterations when using a predicate and an initial delay")
    @Test
    @Timeout(10)
    void testRunNoMorePredicate() {
        BackgroundTask task = Tasks.backgroundTask()
                .withScheduledExecutor(Executors.newSingleThreadScheduledExecutor())
                .withBudget(Budgets.iterationTimeBudget()
                        .withInterval(Duration.ofSeconds(1))
                        .withInitialDelay(Duration.ZERO)
                        .withMaxIterations(3)
                        .build())
                .build();

        boolean completed = task.run(this::taskPredicate, new Object());
        assertEquals(3, taskCount);
        assertFalse(completed, "The task did not complete, the return should be false");
    }

    @DisplayName("Test that the task stops running once the predicate is true")
    @Test
    @Timeout(10)
    void testRunNoMorePredicateDeterministic() {
        // It should run 3 times only
        BackgroundTask task = Tasks.backgroundTask()
                .withScheduledExecutor(Executors.newSingleThreadScheduledExecutor())
                .withBudget(Budgets.iterationTimeBudget()
                        .withInterval(Duration.ofSeconds(1))
                        .withInitialDelay(Duration.ZERO)
                        .withMaxDuration(Duration.ofSeconds(5))
                        .withMaxIterations(4)
                        .build())
                .build();

        boolean completed = task.run(this::taskPredicateWithDeterministicStop, Integer.valueOf(3));
        assertEquals(3, taskCount);
        assertTrue(completed, "The task did complete, the return should be true");
    }

    @DisplayName("Test that the task stops running once the predicate is true when the test is slow")
    @Test
    @Timeout(10)
    void testRunNoMorePredicateDeterministicSlow() {
        // It should run 5 times in 4 seconds because there is no delay
        BackgroundTask task = Tasks.backgroundTask()
                .withScheduledExecutor(Executors.newSingleThreadScheduledExecutor())
                .withBudget(Budgets.iterationTimeBudget()
                        .withMaxIterations(4)
                        .withInterval(Duration.ofSeconds(1))
                        .withInitialDelay(Duration.ZERO)
                        .build())
                .build();

        boolean completed = task.run(this::taskPredicateWithDeterministicStopSlow, Integer.valueOf(3));
        assertTrue(taskCount < maxIterations);
        assertFalse(completed, "The task did not complete because of timeout, the return should be false");
    }

    @DisplayName("Test that the task stops running once the predicate is true when the test is slow")
    @Test
    @Timeout(10)
    void testRunNoMorePredicateDeterministicSlowWithDelay() {
        // It should run 5 times in 4 seconds because there is no delay
        BackgroundTask task = Tasks.backgroundTask()
                .withScheduledExecutor(Executors.newSingleThreadScheduledExecutor())
                .withBudget(Budgets.iterationTimeBudget()
                        .withInterval(Duration.ofSeconds(1))
                        .withInitialDelay(Duration.ofSeconds(1))
                        .withMaxIterations(4)
                        .build())
                .build();

        boolean completed = task.run(this::taskPredicateWithDeterministicStopSlow, Integer.valueOf(3));
        assertTrue(taskCount < maxIterations);
        assertFalse(completed, "The task did not complete because of timeout, the return should be false");
    }

    @DisplayName("Test that the task runs until the boolean supplier succeeds")
    @Test
    @Timeout(10)
    void testRunNoMoreBooleanSupplierWithForever() {
        BackgroundTask task = Tasks.backgroundTask()
                .withScheduledExecutor(Executors.newSingleThreadScheduledExecutor())
                .withBudget(Budgets.iterationTimeBudget()
                        .withMaxIterations(Integer.MAX_VALUE)
                        .withInitialDelay(Duration.ofSeconds(1))
                        .withUnlimitedDuration()
                        .build())
                .build();

        boolean completed = task.run(this::taskPredicateWithDeterministicStop, 4);
        assertTrue(maxIterations > taskCount, "The task execution should not exceed the max iterations");
        assertTrue(completed, "The task did not complete, the return should be false");
    }
}
