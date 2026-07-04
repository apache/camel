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
package org.apache.camel.component.a2a.state;

import org.apache.camel.component.a2a.model.StreamResponse;
import org.apache.camel.component.a2a.model.Task;
import org.apache.camel.component.a2a.model.TaskState;
import org.apache.camel.component.a2a.model.TaskStatus;

/**
 * Combined A2A task-store SPI.
 * <p>
 * The preview SPI is split into smaller parent contracts for task snapshots, subscriptions, push notification configs,
 * and cleanup hooks. Existing custom stores can continue implementing this interface.
 */
public interface A2ATaskStore
        extends A2ATaskRepository, A2ATaskSubscriptions, A2APushConfigStore, A2ATaskCleanup {
    /**
     * Atomically update the task status and notify all subscribers. Convenience method that combines
     * {@link #put(String, Task)} and {@link #notifySubscribers(String, StreamResponse)} in a single atomic operation.
     *
     * @param taskId the task ID
     * @param status the new task status
     */
    void updateStatusAndNotify(String taskId, TaskStatus status);

    /**
     * Cancel a task if it is not in a terminal state. The default implementation is NOT atomic; implementors should
     * ensure atomicity in concurrent environments (e.g., using {@code ConcurrentHashMap.computeIfPresent}).
     *
     * @param  taskId                the task ID
     * @return                       the canceled task, or null if not found
     * @throws IllegalStateException if the task is in a terminal state
     */
    default Task cancelIfNotTerminal(String taskId) {
        Task task = get(taskId);
        if (task == null) {
            return null;
        }
        if (task.status() != null && task.status().state() != null
                && task.status().state().isTerminal()) {
            throw new IllegalStateException(
                    "Task is in terminal state: " + task.status().state());
        }
        Task canceled = Task.builder(task).status(new TaskStatus(TaskState.CANCELED)).build();
        put(taskId, canceled);
        return canceled;
    }
}
