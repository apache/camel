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

import org.apache.camel.support.task.budget.Budgets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForegroundTimeTaskTest extends TaskTestSupport {

    @DisplayName("Test that the task does not run for more than the max iterations when using a supplier")
    @Test
    @Timeout(10)
    void testRunNoMoreSupplier() {
        // It should run 5 times in 4 seconds because there is no delay
        ForegroundTask task = Tasks.foregroundTask()
                .withBudget(Budgets.iterationTimeBudget()
                        .withMaxDuration(Duration.ofSeconds(5))
                        .withMaxIterations(5)
                        .withInitialDelay(Duration.ZERO)
                        .withInterval(Duration.ofSeconds(1))
                        .build())
                .build();

        task.run(this::booleanSupplier);
        assertEquals(maxIterations, taskCount);

        Duration duration = task.elapsed();
        assertNotNull(duration);
        assertFalse(duration.isNegative());
        assertFalse(duration.isZero());
        assertTrue(duration.toMillis() > 0);
    }

    @DisplayName("Test that the task does not run for more than the max iterations when using a supplier and an initial delay")
    @Test
    @Timeout(10)
    void testRunNoMoreSupplierWithDelay() {
        // this should run 5 times in a total duration of 6 seconds (5s executing + 1s delay)
        ForegroundTask task = Tasks.foregroundTask()
                .withBudget(Budgets.iterationTimeBudget()
                        .withMaxDuration(Duration.ofMillis(6_500)) // Add 500 ms delay to make the test more flexible
                        .withMaxIterations(5)
                        .withInitialDelay(Duration.ofSeconds(1))
                        .withInterval(Duration.ofSeconds(1))
                        .build())
                .build();

        task.run(this::booleanSupplier);
        assertEquals(maxIterations, taskCount);
    }

    @DisplayName("Test that the task does not run for more than the max iterations when using a predicate and an initial delay")
    @Test
    @Timeout(10)
    void testRunNoMorePredicate() {
        // It should run 5 times in 4 seconds because there is no delay
        ForegroundTask task = Tasks.foregroundTask()
                .withBudget(Budgets.iterationTimeBudget()
                        .withMaxDuration(Duration.ofSeconds(5))
                        .withMaxIterations(5)
                        .withInitialDelay(Duration.ZERO)
                        .withInterval(Duration.ofSeconds(1))
                        .build())
                .build();

        task.run(this::taskPredicate, new Object());
        assertEquals(maxIterations, taskCount);
    }

    @DisplayName("Test that the task does not run for more than the max duration when using a predicate and an initial delay")
    @Test
    @Timeout(10)
    void testRunNoMorePredicateMaxDuration() {
        // It should run 5 times in 4 seconds because there is no delay
        ForegroundTask task = Tasks.foregroundTask()
                .withBudget(Budgets.iterationTimeBudget()
                        .withMaxDuration(Duration.ofSeconds(5))
                        .withMaxIterations(50)
                        .withInitialDelay(Duration.ZERO)
                        .withInterval(Duration.ofSeconds(1))
                        .build())
                .build();

        task.run(this::taskPredicate, new Object());
        assertEquals(maxIterations, taskCount);
    }

    @DisplayName("Test that the task stops running once the predicate is true")
    @Test
    @Timeout(10)
    void testRunNoMorePredicateDeterministic() {
        // It should run 5 times in 4 seconds because there is no delay
        ForegroundTask task = Tasks.foregroundTask()
                .withBudget(Budgets.iterationTimeBudget()
                        .withMaxDuration(Duration.ofSeconds(5))
                        .withMaxIterations(5)
                        .withInitialDelay(Duration.ZERO)
                        .withInterval(Duration.ofSeconds(1))
                        .build())
                .build();

        task.run(this::taskPredicateWithDeterministicStop, 3);
        assertEquals(3, taskCount);
    }

    @DisplayName("Test that the task stops running once the predicate is true when the test is slow")
    @Test
    @Timeout(10)
    void testRunNoMorePredicateDeterministicSlow() {
        // It should run 5 times in 4 seconds because there is no delay
        ForegroundTask task = Tasks.foregroundTask()
                .withBudget(Budgets.iterationTimeBudget()
                        .withMaxDuration(Duration.ofSeconds(5))
                        .withMaxIterations(5)
                        .withInitialDelay(Duration.ZERO)
                        .withInterval(Duration.ofSeconds(1))
                        .build())
                .build();

        task.run(this::taskPredicateWithDeterministicStopSlow, 3);
        assertTrue(taskCount < maxIterations);
    }

    @DisplayName("Test that the task stops running once the predicate is true when the test is slow")
    @Test
    @Timeout(10)
    void testRunNoMorePredicateDeterministicSlowWithDelay() {
        // It should run 5 times in 4 seconds because there is no delay
        ForegroundTask task = Tasks.foregroundTask()
                .withBudget(Budgets.iterationTimeBudget()
                        .withMaxDuration(Duration.ofSeconds(5))
                        .withMaxIterations(5)
                        .withInitialDelay(Duration.ofSeconds(1))
                        .withInterval(Duration.ofSeconds(1))
                        .build())
                .build();

        task.run(this::taskPredicateWithDeterministicStopSlow, 3);
        assertTrue(taskCount < maxIterations);
    }
}
