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

import java.util.List;
import java.util.Set;

import org.apache.camel.component.a2a.model.Task;
import org.apache.camel.component.a2a.model.TaskState;

/**
 * Storage abstraction for A2A task snapshots and message idempotency mappings.
 *
 * @since 4.21
 */
public interface A2ATaskRepository {

    /**
     * Store a task by its ID.
     *
     * @param taskId the task ID
     * @param task   the task to store
     */
    void put(String taskId, Task task);

    /**
     * Retrieve a task by its ID.
     *
     * @param  taskId the task ID
     * @return        the task, or null if not found
     */
    Task get(String taskId);

    /**
     * Remove a task from the store.
     *
     * @param  taskId the task ID
     * @return        the removed task, or null if not found
     */
    Task delete(String taskId);

    /**
     * Check if a task exists in the store.
     *
     * @param  taskId the task ID
     * @return        true if the task exists
     */
    boolean contains(String taskId);

    /**
     * Get all task IDs in the store.
     *
     * @return set of task IDs
     */
    Set<String> keys();

    /**
     * Remove all tasks from the store.
     */
    void clear();

    /**
     * Track a message ID to task ID mapping for idempotency.
     *
     * @param messageId the message ID
     * @param taskId    the task ID
     */
    void trackMessageId(String messageId, String taskId);

    /**
     * Retrieve the task ID associated with a message ID.
     *
     * @param  messageId the message ID
     * @return           the task ID, or null if not tracked
     */
    String getTaskIdByMessageId(String messageId);

    /**
     * List tasks with optional filtering.
     *
     * @param  contextId  optional context ID filter (null = no filter)
     * @param  states     optional task state filter (null = no filter)
     * @param  maxResults maximum number of results
     * @return            filtered list sorted by status timestamp descending
     */
    List<Task> list(String contextId, List<TaskState> states, int maxResults);
}
