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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.task.BackgroundTask;
import org.apache.camel.support.task.TaskManagerRegistry;
import org.apache.camel.support.task.Tasks;
import org.apache.camel.support.task.budget.Budgets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class BackgroundTaskRegistryTest {

    private CamelContext camelContext;
    private TaskManagerRegistry registry;

    @BeforeEach
    void setUp() throws Exception {
        camelContext = new DefaultCamelContext();
        camelContext.start();
        registry = PluginHelper.getTaskManagerRegistry(camelContext.getCamelContextExtension());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (camelContext != null) {
            camelContext.stop();
        }
    }

    @DisplayName("Test that run() registers task immediately before first scheduled tick")
    @Test
    @Timeout(10)
    void testTaskRegisteredImmediatelyOnRun() throws Exception {
        AtomicBoolean supplierCalled = new AtomicBoolean(false);

        BackgroundTask task = Tasks.backgroundTask()
                .withScheduledExecutor(Executors.newSingleThreadScheduledExecutor())
                .withBudget(Budgets.timeBudget()
                        .withInterval(Duration.ofMillis(100))
                        .withInitialDelay(Duration.ofSeconds(2))
                        .withMaxDuration(Duration.ofSeconds(5))
                        .build())
                .build();

        Thread runner = new Thread(() -> task.run(camelContext, () -> {
            supplierCalled.set(true);
            return true;
        }));
        runner.start();

        await().atMost(1, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(registry.getTasks()).hasSize(1));

        assertThat(supplierCalled.get())
                .as("Supplier should not have been called yet (initial delay is 2s)")
                .isFalse();

        assertThat(registry.getTasks().iterator().next()).isNotNull();

        runner.join(10000);
        assertThat(registry.getTasks()).isEmpty();
    }

    @DisplayName("Test that task stays registered during retries and is removed after run() returns")
    @Test
    @Timeout(10)
    void testTaskStaysRegisteredDuringRetries() throws Exception {
        AtomicBoolean shouldSucceed = new AtomicBoolean(false);
        CountDownLatch firstAttempt = new CountDownLatch(1);

        BackgroundTask task = Tasks.backgroundTask()
                .withScheduledExecutor(Executors.newSingleThreadScheduledExecutor())
                .withBudget(Budgets.timeBudget()
                        .withInterval(Duration.ofMillis(200))
                        .withInitialDelay(Duration.ZERO)
                        .withMaxDuration(Duration.ofSeconds(5))
                        .build())
                .build();

        Thread runner = new Thread(() -> task.run(camelContext, () -> {
            firstAttempt.countDown();
            return shouldSucceed.get();
        }));
        runner.start();

        assertThat(firstAttempt.await(3, TimeUnit.SECONDS)).isTrue();

        for (int i = 0; i < 3; i++) {
            Thread.sleep(250);
            assertThat(registry.getTasks())
                    .as("Task should stay in registry during retry %d", i)
                    .hasSize(1);
        }

        shouldSucceed.set(true);
        runner.join(5000);

        assertThat(registry.getTasks()).isEmpty();
    }

    @DisplayName("Test that task is removed from registry after supplier succeeds")
    @Test
    @Timeout(10)
    void testTaskRemovedAfterSuccess() throws Exception {
        CountDownLatch supplierReady = new CountDownLatch(1);
        CountDownLatch supplierRelease = new CountDownLatch(1);
        AtomicBoolean runCompleted = new AtomicBoolean(false);

        BackgroundTask task = Tasks.backgroundTask()
                .withScheduledExecutor(Executors.newSingleThreadScheduledExecutor())
                .withBudget(Budgets.timeBudget()
                        .withInterval(Duration.ofMillis(100))
                        .withInitialDelay(Duration.ZERO)
                        .withMaxDuration(Duration.ofSeconds(5))
                        .build())
                .build();

        Thread runner = new Thread(() -> {
            task.run(camelContext, () -> {
                supplierReady.countDown();
                try {
                    supplierRelease.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                return true;
            });
            runCompleted.set(true);
        });
        runner.start();

        assertThat(supplierReady.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(registry.getTasks()).hasSize(1);

        supplierRelease.countDown();
        runner.join(5000);

        assertThat(runCompleted.get()).isTrue();
        assertThat(registry.getTasks()).isEmpty();
    }
}
