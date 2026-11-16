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
package org.apache.camel.support.task.task;

import java.time.Duration;
import java.util.concurrent.Executors;

import org.apache.camel.support.task.BackgroundTask;
import org.apache.camel.support.task.Tasks;
import org.apache.camel.support.task.budget.Budgets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BackgroundIterationTimeTaskTest extends TaskTestSupport {

    // these tests count the iterations of a task and do not measure the elapsed time, so the budgets of one second are
    // shortened to a fifth of it (and the slow predicate with them) and the test is five times faster
    @Override
    protected long slowPredicateMillis() {
        return 400;
    }

    @DisplayName("Test that the task does not run for more than the max iterations when using a supplier")
    @Test
    @Timeout(10)
    void testRunNoMoreSupplier() {
        // It should run 3 times only because that's the max iterations
        BackgroundTask task = Tasks.backgroundTask()
                .withScheduledExecutor(Executors.newSingleThreadScheduledExecutor())
                .withBudget(Budgets.iterationTimeBudget()
                        .withMaxIterations(3)
                        .withInterval(Duration.ofMillis(200))
                        .withInitialDelay(Duration.ZERO)
                        // use unlimited duration so we're sure that the task is really canceled after maxIterations
                        .withUnlimitedDuration()
                        .build())
                .build();

        boolean completed = task.run(camelContext, this::booleanSupplier);
        assertEquals(3, taskCount.intValue());
        assertFalse(completed, "The task did not complete, the return should be false");
    }

    @DisplayName("Test that the task does not run for more than the max iterations when using a supplier and initial delay")
    @Test
    @Timeout(10)
    void testRunNoMoreSupplierWithDelay() {
        BackgroundTask task = Tasks.backgroundTask()
                .withScheduledExecutor(Executors.newSingleThreadScheduledExecutor())
                .withBudget(Budgets.iterationTimeBudget()
                        .withInterval(Duration.ofMillis(200))
                        .withInitialDelay(Duration.ofMillis(200))
                        .withMaxDuration(Duration.ofMillis(1000))
                        .withMaxIterations(5)
                        .build())
                .build();

        boolean completed = task.run(camelContext, this::booleanSupplier);
        assertTrue(maxIterations > taskCount.intValue(), "The task execution should not exceed the max iterations");
        assertFalse(completed, "The task did not complete, the return should be false");
    }

    @DisplayName("Test that the task does not run for more than the max iterations when using a predicate and an initial delay")
    @Test
    @Timeout(10)
    void testRunNoMorePredicate() {
        BackgroundTask task = Tasks.backgroundTask()
                .withScheduledExecutor(Executors.newSingleThreadScheduledExecutor())
                .withBudget(Budgets.iterationTimeBudget()
                        .withInterval(Duration.ofMillis(200))
                        .withInitialDelay(Duration.ZERO)
                        .withMaxIterations(3)
                        .build())
                .build();

        boolean completed = task.run(camelContext, this::taskPredicate, new Object());
        assertEquals(3, taskCount.intValue());
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
                        .withInterval(Duration.ofMillis(200))
                        .withInitialDelay(Duration.ZERO)
                        .withMaxDuration(Duration.ofMillis(1000))
                        .withMaxIterations(4)
                        .build())
                .build();

        boolean completed = task.run(camelContext, this::taskPredicateWithDeterministicStop, Integer.valueOf(3));
        assertEquals(3, taskCount.intValue());
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
                        .withMaxDuration(Duration.ofMillis(1000))
                        .withInterval(Duration.ofMillis(200))
                        .withInitialDelay(Duration.ZERO)
                        .build())
                .build();

        boolean completed = task.run(camelContext, this::taskPredicateWithDeterministicStopSlow, Integer.valueOf(3));
        assertTrue(taskCount.intValue() < maxIterations);
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
                        .withInterval(Duration.ofMillis(200))
                        .withInitialDelay(Duration.ofMillis(200))
                        .withMaxIterations(4)
                        .withMaxDuration(Duration.ofMillis(1000))
                        .build())
                .build();

        boolean completed = task.run(camelContext, this::taskPredicateWithDeterministicStopSlow, Integer.valueOf(3));
        assertTrue(taskCount.intValue() < maxIterations);
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
                        .withInitialDelay(Duration.ofMillis(200))
                        .withUnlimitedDuration()
                        .build())
                .build();

        boolean completed = task.run(camelContext, this::taskPredicateWithDeterministicStop, 4);
        assertTrue(maxIterations > taskCount.intValue(), "The task execution should not exceed the max iterations");
        assertTrue(completed, "The task did not complete, the return should be false");
    }
}
