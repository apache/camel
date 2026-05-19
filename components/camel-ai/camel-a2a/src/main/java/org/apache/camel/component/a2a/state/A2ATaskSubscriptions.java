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

/**
 * Subscription contract for A2A task update events.
 *
 * @since 4.21
 */
public interface A2ATaskSubscriptions {

    /**
     * Register a subscriber to receive updates for a task.
     *
     * @param taskId     the task ID
     * @param subscriber the subscriber to register
     */
    void addSubscriber(String taskId, A2ATaskSubscriber subscriber);

    /**
     * Remove a subscriber from task updates.
     *
     * @param taskId     the task ID
     * @param subscriber the subscriber to remove
     */
    void removeSubscriber(String taskId, A2ATaskSubscriber subscriber);

    /**
     * Notify all subscribers of a task update event.
     *
     * @param taskId the task ID
     * @param event  the stream response event to broadcast
     */
    void notifySubscribers(String taskId, StreamResponse event);

    /**
     * Register a global subscriber that receives events for ALL tasks. Global subscribers are notified in addition to
     * per-task subscribers. Used for cross-cutting concerns like push notification dispatch.
     *
     * @param subscriber the global subscriber to register
     */
    default void addGlobalSubscriber(A2ATaskSubscriber subscriber) {
    }
}
