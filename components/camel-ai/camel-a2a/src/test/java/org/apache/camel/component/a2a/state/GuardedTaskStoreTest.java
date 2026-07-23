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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.component.a2a.model.StreamResponse;
import org.apache.camel.component.a2a.model.Task;
import org.apache.camel.component.a2a.model.TaskPushNotificationConfig;
import org.apache.camel.component.a2a.model.TaskState;
import org.apache.camel.component.a2a.model.TaskStatus;
import org.apache.camel.component.a2a.model.TaskStatusUpdateEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuardedTaskStoreTest {

    @Test
    void putDoesNotOverwriteTerminalTaskInCustomStore() {
        UnsafeTaskStore delegate = new UnsafeTaskStore();
        GuardedTaskStore store = new GuardedTaskStore(delegate, false);
        delegate.put("t-done", task("t-done", TaskState.COMPLETED));

        store.put("t-done", task("t-done", TaskState.WORKING));

        assertThat(delegate.get("t-done").status().state()).isEqualTo(TaskState.COMPLETED);
    }

    @Test
    void updateStatusAndNotifyDoesNotOverwriteTerminalTaskInCustomStore() {
        UnsafeTaskStore delegate = new UnsafeTaskStore();
        GuardedTaskStore store = new GuardedTaskStore(delegate, false);
        delegate.put("t-done", task("t-done", TaskState.COMPLETED));

        store.updateStatusAndNotify("t-done", new TaskStatus(TaskState.WORKING));

        assertThat(delegate.get("t-done").status().state()).isEqualTo(TaskState.COMPLETED);
        assertThat(delegate.events).isEmpty();
    }

    @Test
    void cancelIfNotTerminalRejectsTerminalTaskInCustomStore() {
        UnsafeTaskStore delegate = new UnsafeTaskStore();
        GuardedTaskStore store = new GuardedTaskStore(delegate, false);
        delegate.put("t-done", task("t-done", TaskState.COMPLETED));

        assertThatThrownBy(() -> store.cancelIfNotTerminal("t-done"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("terminal state");
        assertThat(delegate.get("t-done").status().state()).isEqualTo(TaskState.COMPLETED);
    }

    @Test
    void putPushConfigValidatesAndNormalizesCustomStoreConfig() {
        UnsafeTaskStore delegate = new UnsafeTaskStore();
        GuardedTaskStore store = new GuardedTaskStore(delegate, false);
        TaskPushNotificationConfig config = new TaskPushNotificationConfig();
        config.setUrl("https://example.com/webhook");

        store.putPushConfig("t-push", config);

        TaskPushNotificationConfig stored = delegate.getPushConfig("t-push", config.getId());
        assertThat(stored).isNotNull();
        assertThat(stored.getTaskId()).isEqualTo("t-push");
        assertThat(stored.getId()).isNotBlank();

        TaskPushNotificationConfig invalid = new TaskPushNotificationConfig();
        invalid.setUrl("http://127.0.0.1/webhook");
        assertThatThrownBy(() -> store.putPushConfig("t-push", invalid))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void wrapperDelegatesSubscriberFanoutForCustomStores() {
        UnsafeTaskStore delegate = new UnsafeTaskStore();
        GuardedTaskStore store = new GuardedTaskStore(delegate, false);
        List<StreamResponse> taskEvents = new ArrayList<>();
        List<StreamResponse> globalEvents = new ArrayList<>();

        store.addSubscriber("t-stream", (taskId, event) -> taskEvents.add(event));
        store.addGlobalSubscriber((taskId, event) -> globalEvents.add(event));
        store.notifySubscribers("t-stream", StreamResponse.ofStatusUpdate(
                TaskStatusUpdateEvent.builder()
                        .taskId("t-stream")
                        .status(new TaskStatus(TaskState.WORKING))
                        .build()));

        assertThat(taskEvents).hasSize(1);
        assertThat(globalEvents).hasSize(1);
        assertThat(delegate.events).hasSize(1);
    }

    @Test
    void updateStatusAndNotifyUsesDelegateEventBus() {
        UnsafeTaskStore delegate = new UnsafeTaskStore();
        GuardedTaskStore store = new GuardedTaskStore(delegate, false);
        List<StreamResponse> taskEvents = new ArrayList<>();
        delegate.put("t-working", task("t-working", TaskState.WORKING));

        store.addSubscriber("t-working", (taskId, event) -> taskEvents.add(event));
        store.updateStatusAndNotify("t-working", new TaskStatus(TaskState.COMPLETED));

        assertThat(delegate.get("t-working").status().state()).isEqualTo(TaskState.COMPLETED);
        assertThat(taskEvents).hasSize(1);
        assertThat(delegate.events).hasSize(1);
    }

    @Test
    void wrapperRemovesTaskSubscribersAfterFinalEvent() {
        UnsafeTaskStore delegate = new UnsafeTaskStore();
        GuardedTaskStore store = new GuardedTaskStore(delegate, false);
        List<StreamResponse> taskEvents = new ArrayList<>();

        store.addSubscriber("t-stream", (taskId, event) -> taskEvents.add(event));
        store.notifySubscribers("t-stream", StreamResponse.ofStatusUpdate(
                TaskStatusUpdateEvent.builder()
                        .taskId("t-stream")
                        .status(new TaskStatus(TaskState.COMPLETED))
                        .build()));
        store.notifySubscribers("t-stream", StreamResponse.ofStatusUpdate(
                TaskStatusUpdateEvent.builder()
                        .taskId("t-stream")
                        .status(new TaskStatus(TaskState.WORKING))
                        .build()));

        assertThat(taskEvents).hasSize(1);
        assertThat(delegate.events).hasSize(2);
    }

    private static Task task(String id, TaskState state) {
        return Task.builder()
                .id(id)
                .status(new TaskStatus(state))
                .build();
    }

    private static final class UnsafeTaskStore implements A2ATaskStore {
        private final Map<String, Task> tasks = new LinkedHashMap<>();
        private final Map<String, TaskPushNotificationConfig> pushConfigs = new LinkedHashMap<>();
        private final Map<String, List<A2ATaskSubscriber>> subscribers = new LinkedHashMap<>();
        private final List<A2ATaskSubscriber> globalSubscribers = new ArrayList<>();
        private final List<StreamResponse> events = new ArrayList<>();

        @Override
        public void put(String taskId, Task task) {
            tasks.put(taskId, task);
        }

        @Override
        public Task get(String taskId) {
            return tasks.get(taskId);
        }

        @Override
        public Task delete(String taskId) {
            return tasks.remove(taskId);
        }

        @Override
        public boolean contains(String taskId) {
            return tasks.containsKey(taskId);
        }

        @Override
        public Set<String> keys() {
            return tasks.keySet();
        }

        @Override
        public void clear() {
            tasks.clear();
        }

        @Override
        public void trackMessageId(String messageId, String taskId) {
        }

        @Override
        public String getTaskIdByMessageId(String messageId) {
            return null;
        }

        @Override
        public List<Task> list(String contextId, List<TaskState> states, int maxResults) {
            return tasks.values().stream()
                    .filter(task -> contextId == null || contextId.equals(task.contextId()))
                    .filter(task -> states == null || states.isEmpty()
                            || task.status() != null && states.contains(task.status().state()))
                    .limit(maxResults)
                    .toList();
        }

        @Override
        public void addSubscriber(String taskId, A2ATaskSubscriber subscriber) {
            subscribers.computeIfAbsent(taskId, key -> new ArrayList<>()).add(subscriber);
        }

        @Override
        public void removeSubscriber(String taskId, A2ATaskSubscriber subscriber) {
            List<A2ATaskSubscriber> taskSubscribers = subscribers.get(taskId);
            if (taskSubscribers != null) {
                taskSubscribers.remove(subscriber);
                if (taskSubscribers.isEmpty()) {
                    subscribers.remove(taskId);
                }
            }
        }

        @Override
        public void addGlobalSubscriber(A2ATaskSubscriber subscriber) {
            globalSubscribers.add(subscriber);
        }

        @Override
        public void notifySubscribers(String taskId, StreamResponse event) {
            events.add(event);
            for (A2ATaskSubscriber subscriber : List.copyOf(subscribers.getOrDefault(taskId, List.of()))) {
                subscriber.onEvent(taskId, event);
            }
            for (A2ATaskSubscriber subscriber : List.copyOf(globalSubscribers)) {
                subscriber.onEvent(taskId, event);
            }
        }

        @Override
        public void putPushConfig(String taskId, TaskPushNotificationConfig config) {
            pushConfigs.put(taskId + "/" + config.getId(), config);
        }

        @Override
        public TaskPushNotificationConfig getPushConfig(String taskId, String configId) {
            return pushConfigs.get(taskId + "/" + configId);
        }

        @Override
        public List<TaskPushNotificationConfig> listPushConfigs(String taskId) {
            return Collections.emptyList();
        }

        @Override
        public boolean deletePushConfig(String taskId, String configId) {
            return pushConfigs.remove(taskId + "/" + configId) != null;
        }

        @Override
        public void cleanupExpired(long ttlMs) {
        }

        @Override
        public void updateStatusAndNotify(String taskId, TaskStatus status) {
            Task task = tasks.get(taskId);
            if (task != null) {
                tasks.put(taskId, Task.builder(task).status(status).build());
                notifySubscribers(taskId, StreamResponse.ofStatusUpdate(
                        TaskStatusUpdateEvent.builder()
                                .taskId(taskId)
                                .status(status)
                                .build()));
            }
        }
    }
}
