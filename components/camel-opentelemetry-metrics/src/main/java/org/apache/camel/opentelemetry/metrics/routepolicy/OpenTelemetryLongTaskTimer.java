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
package org.apache.camel.opentelemetry.metrics.routepolicy;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongUpDownCounter;
import org.apache.camel.Route;
import org.apache.camel.opentelemetry.metrics.TaskTimer;

/**
 * This timer is the equivalent of a 'long task' timer in other metrics systems. It measures tasks that are still
 * running, and publishes the number of active tasks and their total duration.
 */
public class OpenTelemetryLongTaskTimer {

    // number of active tasks
    private final ObservableLongUpDownCounter longTasksActive;
    // total duration of all in progress tasks
    private final ObservableLongUpDownCounter longTasksDuration;
    private final Queue<LongTask> activeTasks = new ConcurrentLinkedQueue<>();

    public OpenTelemetryLongTaskTimer(Route route, Meter meter, Attributes attributes,
                                      OpenTelemetryRoutePolicyConfiguration configuration,
                                      OpenTelemetryRoutePolicyNamingStrategy namingStrategy, TimeUnit longTaskTimeUnit) {

        this.longTasksActive = meter
                .upDownCounterBuilder(namingStrategy.getLongTasksActiveName(route))
                .setDescription(route != null ? "Route active long task metric" : "CamelContext active long task metric")
                .buildWithCallback(
                        observableMeasurement -> {
                            observableMeasurement.record(activeTasks.size(), attributes);
                        });

        this.longTasksDuration = meter
                .upDownCounterBuilder(namingStrategy.getLongTasksDurationName(route))
                .setDescription(route != null ? "Route long task duration metric" : "CamelContext long task duration metric")
                .setUnit(longTaskTimeUnit.name().toLowerCase())
                .buildWithCallback(
                        observableMeasurement -> observableMeasurement.record(
                                allLongTaskDuration(longTaskTimeUnit), attributes));
    }

    public void remove() {
        if (longTasksActive != null) {
            longTasksActive.close();
        }
        if (longTasksDuration != null) {
            longTasksDuration.close();
        }
    }

    public TaskTimer start() {
        LongTask task = new LongTask();
        activeTasks.add(task);
        return task;
    }

    private long allLongTaskDuration(TimeUnit unit) {
        long sum = 0L;
        long now = System.nanoTime();
        for (LongTask task : activeTasks) {
            sum += now - task.getStartTime();
        }
        return unit.convert(sum, TimeUnit.NANOSECONDS);
    }

    private class LongTask extends TaskTimer {
        public void stop() {
            activeTasks.remove(this);
            super.stop();
        }
    }
}
