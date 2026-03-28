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
package org.apache.camel.component.infinispan.remote;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.support.task.ForegroundTask;
import org.apache.camel.support.task.Tasks;
import org.apache.camel.support.task.budget.Budgets;
import org.infinispan.client.hotrod.exceptions.RemoteIllegalLifecycleStateException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the retry pattern used by {@link InfinispanRemoteManager} for schema registration when the Infinispan server is
 * not yet ready.
 */
class InfinispanRemoteSchemaRegistrationRetryTest {

    /**
     * Simulates the retry pattern from {@code registerSchemaWithRetry()}: catches
     * {@link RemoteIllegalLifecycleStateException} and returns false, succeeds on subsequent attempt.
     */
    @Test
    void retrySucceedsAfterTransientLifecycleFailures() {
        AtomicInteger attempts = new AtomicInteger();
        int failCount = 3;

        ForegroundTask task = Tasks.foregroundTask()
                .withName("infinispan-schema-registration")
                .withBudget(Budgets.iterationTimeBudget()
                        .withInterval(Duration.ofMillis(50))
                        .withMaxDuration(Duration.ofSeconds(5))
                        .build())
                .build();

        boolean registered = task.run(null, () -> {
            try {
                if (attempts.incrementAndGet() <= failCount) {
                    throw new RemoteIllegalLifecycleStateException("Server not ready", 0, (short) 0, null);
                }
                return true;
            } catch (RemoteIllegalLifecycleStateException e) {
                return false;
            }
        });

        assertTrue(registered);
        assertEquals(failCount + 1, attempts.get());
    }

    @Test
    void retryExhaustedWhenServerNeverBecomesReady() {
        ForegroundTask task = Tasks.foregroundTask()
                .withName("infinispan-schema-registration")
                .withBudget(Budgets.iterationTimeBudget()
                        .withInterval(Duration.ofMillis(50))
                        .withMaxDuration(Duration.ofMillis(200))
                        .build())
                .build();

        boolean registered = task.run(null, () -> {
            try {
                throw new RemoteIllegalLifecycleStateException("Server not ready", 0, (short) 0, null);
            } catch (RemoteIllegalLifecycleStateException e) {
                return false;
            }
        });

        assertFalse(registered);
    }

    @Test
    void nonLifecycleExceptionPropagatesImmediately() {
        AtomicInteger attempts = new AtomicInteger();

        ForegroundTask task = Tasks.foregroundTask()
                .withName("infinispan-schema-registration")
                .withBudget(Budgets.iterationTimeBudget()
                        .withInterval(Duration.ofMillis(50))
                        .withMaxDuration(Duration.ofSeconds(5))
                        .build())
                .build();

        assertThrows(RuntimeException.class, () -> {
            task.run(null, () -> {
                attempts.incrementAndGet();
                throw new RuntimeException("Auth failure");
            });
        });

        assertEquals(1, attempts.get());
    }
}
