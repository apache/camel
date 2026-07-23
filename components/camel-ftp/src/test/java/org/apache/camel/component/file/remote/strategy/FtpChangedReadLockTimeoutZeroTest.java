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
package org.apache.camel.component.file.remote.strategy;

import java.time.Duration;

import org.apache.camel.support.task.BlockingTask;
import org.apache.camel.support.task.Tasks;
import org.apache.camel.support.task.budget.Budgets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that readLockTimeout=0 (documented as "forever") does not cause the Tasks budget to expire immediately.
 * Regression test for CAMEL-24096.
 */
class FtpChangedReadLockTimeoutZeroTest {

    @Test
    void timeoutZeroShouldNotExpireImmediately() {
        long timeout = 0;
        long checkInterval = 100;

        var budgetBuilder = Budgets.iterationTimeBudget()
                .withInterval(Duration.ofMillis(checkInterval));
        if (timeout > 0) {
            budgetBuilder.withMaxDuration(Duration.ofMillis(timeout));
        } else {
            budgetBuilder.withUnlimitedDuration();
        }
        BlockingTask task = Tasks.foregroundTask()
                .withBudget(budgetBuilder.build())
                .withName("ftp-acquire-exclusive-read-lock")
                .build();

        int[] counter = { 0 };
        boolean result = task.run(null, () -> {
            counter[0]++;
            return counter[0] >= 2;
        });

        assertTrue(result, "Task with timeout=0 should have acquired the lock");
        assertTrue(counter[0] >= 2, "Task should have run at least twice");
    }

    @Test
    void timeoutNegativeShouldNotExpireImmediately() {
        long timeout = -1;
        long checkInterval = 100;

        var budgetBuilder = Budgets.iterationTimeBudget()
                .withInterval(Duration.ofMillis(checkInterval));
        if (timeout > 0) {
            budgetBuilder.withMaxDuration(Duration.ofMillis(timeout));
        } else {
            budgetBuilder.withUnlimitedDuration();
        }
        BlockingTask task = Tasks.foregroundTask()
                .withBudget(budgetBuilder.build())
                .withName("ftp-acquire-exclusive-read-lock")
                .build();

        int[] counter = { 0 };
        boolean result = task.run(null, () -> {
            counter[0]++;
            return counter[0] >= 2;
        });

        assertTrue(result, "Task with timeout=-1 should have acquired the lock");
    }

    @Test
    void timeoutPositiveShouldExpireWhenExceeded() {
        long timeout = 200;
        long checkInterval = 100;

        var budgetBuilder = Budgets.iterationTimeBudget()
                .withInterval(Duration.ofMillis(checkInterval));
        if (timeout > 0) {
            budgetBuilder.withMaxDuration(Duration.ofMillis(timeout));
        } else {
            budgetBuilder.withUnlimitedDuration();
        }
        BlockingTask task = Tasks.foregroundTask()
                .withBudget(budgetBuilder.build())
                .withName("ftp-acquire-exclusive-read-lock")
                .build();

        boolean result = task.run(null, () -> false);

        assertFalse(result, "Task with positive timeout should have timed out");
    }
}
