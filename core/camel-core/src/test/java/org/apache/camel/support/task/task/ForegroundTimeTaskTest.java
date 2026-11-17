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

import org.apache.camel.support.task.ForegroundTask;
import org.apache.camel.support.task.Tasks;
import org.apache.camel.support.task.budget.Budgets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForegroundTimeTaskTest extends TaskTestSupport {

    // these tests count the iterations of a task and do not measure the elapsed time, so the budgets of one second are
    // shortened to a fifth of it (and the slow predicate with them) and the test is five times faster
    @Override
    protected long slowPredicateMillis() {
        return 400;
    }

    @DisplayName("Test that the task does not run for more than the max iterations when using a supplier")
    @Test
    @Timeout(20)
    void testRunNoMoreSupplier() {
        // It should run 5 times in 800 milliseconds because there is no initial delay
        ForegroundTask task = Tasks.foregroundTask()
                .withBudget(Budgets.iterationTimeBudget()
                        .withMaxDuration(Duration.ofMillis(5_000))
                        .withMaxIterations(5)
                        .withInitialDelay(Duration.ZERO)
                        .withInterval(Duration.ofMillis(200))
                        .build())
                .build();

        task.run(camelContext, this::booleanSupplier);
        assertEquals(maxIterations, taskCount.intValue());

        Duration duration = task.elapsed();
        assertNotNull(duration);
        assertFalse(duration.isNegative());
        assertFalse(duration.isZero());
        assertTrue(duration.toMillis() > 0);
    }

    @DisplayName("Test that the task does not run for more than the max iterations when using a supplier and an initial delay")
    @Test
    @Timeout(20)
    void testRunNoMoreSupplierWithDelay() {
        // this should run 5 times in a total duration of 1.2 seconds (1s executing + 200ms delay)
        ForegroundTask task = Tasks.foregroundTask()
                .withBudget(Budgets.iterationTimeBudget()
                        .withMaxDuration(Duration.ofMillis(5_000))
                        .withMaxIterations(5)
                        .withInitialDelay(Duration.ofMillis(200))
                        .withInterval(Duration.ofMillis(200))
                        .build())
                .build();

        task.run(camelContext, this::booleanSupplier);
        assertEquals(maxIterations, taskCount.intValue());
    }

    @DisplayName("Test that the task does not run for more than the max iterations when using a predicate and an initial delay")
    @Test
    @Timeout(20)
    void testRunNoMorePredicate() {
        // It should run 5 times in 800 milliseconds because there is no initial delay
        ForegroundTask task = Tasks.foregroundTask()
                .withBudget(Budgets.iterationTimeBudget()
                        .withMaxDuration(Duration.ofMillis(5_000))
                        .withMaxIterations(5)
                        .withInitialDelay(Duration.ZERO)
                        .withInterval(Duration.ofMillis(200))
                        .build())
                .build();

        task.run(camelContext, this::taskPredicate, new Object());
        assertEquals(maxIterations, taskCount.intValue());
    }

    @DisplayName("Test that the task does not run for more than the max duration when using a predicate and an initial delay")
    @Test
    @Timeout(20)
    void testRunNoMorePredicateMaxDuration() {
        // It should run 5 times in 800 milliseconds because there is no initial delay
        ForegroundTask task = Tasks.foregroundTask()
                .withBudget(Budgets.iterationTimeBudget()
                        .withMaxDuration(Duration.ofMillis(1000))
                        .withMaxIterations(50)
                        .withInitialDelay(Duration.ZERO)
                        .withInterval(Duration.ofMillis(200))
                        .build())
                .build();

        task.run(camelContext, this::taskPredicate, new Object());
        assertEquals(maxIterations, taskCount.intValue());
    }

    @DisplayName("Test that the task stops running once the predicate is true")
    @Test
    @Timeout(20)
    void testRunNoMorePredicateDeterministic() {
        // It should run 5 times in 800 milliseconds because there is no initial delay
        ForegroundTask task = Tasks.foregroundTask()
                .withBudget(Budgets.iterationTimeBudget()
                        .withMaxDuration(Duration.ofMillis(5_000))
                        .withMaxIterations(5)
                        .withInitialDelay(Duration.ZERO)
                        .withInterval(Duration.ofMillis(200))
                        .build())
                .build();

        task.run(camelContext, this::taskPredicateWithDeterministicStop, 3);
        assertEquals(3, taskCount.intValue());
    }

    @DisplayName("Test that the task stops running once the predicate is true when the test is slow")
    @Test
    @Timeout(20)
    void testRunNoMorePredicateDeterministicSlow() {
        // It should run 5 times in 800 milliseconds because there is no initial delay
        ForegroundTask task = Tasks.foregroundTask()
                .withBudget(Budgets.iterationTimeBudget()
                        .withMaxDuration(Duration.ofMillis(1000))
                        .withMaxIterations(5)
                        .withInitialDelay(Duration.ZERO)
                        .withInterval(Duration.ofMillis(200))
                        .build())
                .build();

        task.run(camelContext, this::taskPredicateWithDeterministicStopSlow, 3);
        assertTrue(taskCount.intValue() < maxIterations);
    }

    @DisplayName("Test that the task stops running once the predicate is true when the test is slow")
    @Test
    @Timeout(20)
    void testRunNoMorePredicateDeterministicSlowWithDelay() {
        // It should run 5 times in 800 milliseconds because there is no initial delay
        ForegroundTask task = Tasks.foregroundTask()
                .withBudget(Budgets.iterationTimeBudget()
                        .withMaxDuration(Duration.ofMillis(1000))
                        .withMaxIterations(5)
                        .withInitialDelay(Duration.ofMillis(200))
                        .withInterval(Duration.ofMillis(200))
                        .build())
                .build();

        task.run(camelContext, this::taskPredicateWithDeterministicStopSlow, 3);
        assertTrue(taskCount.intValue() < maxIterations);
    }
}
