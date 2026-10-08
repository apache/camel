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
package org.apache.camel.component.kafka;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.support.task.BackgroundTask;
import org.apache.camel.support.task.Tasks;
import org.apache.camel.support.task.budget.Budgets;

/**
 * Support for the task that creates (and recreates) the Kafka consumer of a consumer thread.
 */
public final class KafkaReconnectSupport {

    private KafkaReconnectSupport() {
    }

    /**
     * Creates the background task that creates the Kafka consumer, retrying at the given interval. The task stays
     * registered in the internal task registry for visibility via management (TUI/CLI/Hawtio).
     *
     * @param  reconnectPool the scheduled executor that runs the attempts
     * @param  name          the name of the task
     * @param  maxAttempts   the maximum number of attempts, or 0 to retry forever
     * @param  interval      the delay in millis between two attempts
     * @return               the task
     */
    public static BackgroundTask createReconnectTask(
            ScheduledExecutorService reconnectPool, String name, int maxAttempts, long interval) {
        return Tasks.backgroundTask()
                .withScheduledExecutor(reconnectPool)
                .withBudget(Budgets.iterationTimeBudget()
                        .withMaxIterations(maxAttempts)
                        .withInterval(Duration.ofMillis(interval))
                        .withInitialDelay(Duration.ZERO)
                        .withUnlimitedDuration()
                        .build())
                .withName(name)
                .build();
    }
}
