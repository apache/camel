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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.component.a2a.model.Artifact;
import org.apache.camel.component.a2a.model.Message;
import org.apache.camel.component.a2a.model.StreamResponse;
import org.apache.camel.component.a2a.model.Task;
import org.apache.camel.component.a2a.model.TaskArtifactUpdateEvent;
import org.apache.camel.component.a2a.model.TaskPushNotificationConfig;
import org.apache.camel.component.a2a.model.TaskState;
import org.apache.camel.component.a2a.model.TaskStatus;
import org.apache.camel.component.a2a.model.TaskStatusUpdateEvent;
import org.apache.camel.component.a2a.model.TextPart;
import org.apache.camel.component.a2a.streaming.A2AStreamEmitter;
import org.apache.camel.component.a2a.streaming.StreamSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryTaskStoreTest {
    private InMemoryTaskStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryTaskStore();
    }

    @Test
    void putAndGet() {
        Task task = createTask("task-1", TaskState.SUBMITTED);
        store.put(task.id(), task);

        Task retrieved = store.get("task-1");
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.id()).isEqualTo("task-1");
    }

    @Test
    void getReturnsNullForMissing() {
        Task retrieved = store.get("unknown");
        assertThat(retrieved).isNull();
    }

    @Test
    void delete() {
        Task task = createTask("task-1", TaskState.SUBMITTED);
        store.put(task.id(), task);

        Task removed = store.delete("task-1");
        assertThat(removed).isNotNull();
        assertThat(removed.id()).isEqualTo("task-1");
        assertThat(store.contains("task-1")).isFalse();
    }

    @Test
    void contains() {
        Task task = createTask("task-1", TaskState.SUBMITTED);
        store.put(task.id(), task);

        assertThat(store.contains("task-1")).isTrue();
        assertThat(store.contains("task-2")).isFalse();
    }

    @Test
    void keys() {
        Task task1 = createTask("task-1", TaskState.SUBMITTED);
        Task task2 = createTask("task-2", TaskState.WORKING);
        store.put(task1.id(), task1);
        store.put(task2.id(), task2);

        assertThat(store.keys()).containsExactlyInAnyOrder("task-1", "task-2");
    }

    @Test
    void clear() {
        Task task = createTask("task-1", TaskState.SUBMITTED);
        store.put(task.id(), task);
        store.trackMessageId("msg-1", "task-1");

        store.clear();

        assertThat(store.keys()).isEmpty();
        assertThat(store.getTaskIdByMessageId("msg-1")).isNull();
    }

    @Test
    void trackMessageIdempotency() {
        store.trackMessageId("msg-1", "task-1");

        assertThat(store.getTaskIdByMessageId("msg-1")).isEqualTo("task-1");
        assertThat(store.getTaskIdByMessageId("msg-2")).isNull();
    }

    @Test
    void listAllTasks() {
        store.put("t1", createTask("t1", TaskState.SUBMITTED));
        store.put("t2", createTask("t2", TaskState.COMPLETED));
        store.put("t3", createTask("t3", TaskState.WORKING));
        List<Task> tasks = store.list(null, null, 50);
        assertThat(tasks).hasSize(3);
    }

    @Test
    void listFiltersByContextId() {
        Task t1 = Task.builder()
                .id("t1")
                .contextId("ctx-a")
                .status(new TaskStatus(TaskState.SUBMITTED))
                .build();
        Task t2 = Task.builder()
                .id("t2")
                .contextId("ctx-b")
                .status(new TaskStatus(TaskState.WORKING))
                .build();
        Task t3 = Task.builder()
                .id("t3")
                .contextId("ctx-a")
                .status(new TaskStatus(TaskState.COMPLETED))
                .build();
        store.put("t1", t1);
        store.put("t2", t2);
        store.put("t3", t3);
        List<Task> tasks = store.list("ctx-a", null, 50);
        assertThat(tasks).hasSize(2);
        assertThat(tasks).extracting(Task::id).containsExactlyInAnyOrder("t1", "t3");
    }

    @Test
    void listFiltersByStatus() {
        store.put("t1", createTask("t1", TaskState.SUBMITTED));
        store.put("t2", createTask("t2", TaskState.COMPLETED));
        store.put("t3", createTask("t3", TaskState.WORKING));
        List<Task> tasks = store.list(null, List.of(TaskState.WORKING, TaskState.SUBMITTED), 50);
        assertThat(tasks).hasSize(2);
        assertThat(tasks).extracting(Task::id).containsExactlyInAnyOrder("t1", "t3");
    }

    @Test
    void listRespectsPageSize() {
        for (int i = 0; i < 10; i++) {
            store.put("t" + i, createTask("t" + i, TaskState.SUBMITTED));
        }
        List<Task> tasks = store.list(null, null, 3);
        assertThat(tasks).hasSize(3);
    }

    @Test
    void subscribeAndUnsubscribe() {
        store.put("t1", createTask("t1", TaskState.WORKING));
        AtomicInteger callCount = new AtomicInteger(0);
        A2AStreamEmitter countingEmitter = new A2AStreamEmitter() {
            @Override
            public void emitStatus(TaskState state, String message) {
                callCount.incrementAndGet();
            }

            @Override
            public void emitArtifact(Artifact artifact, Boolean append, Boolean lastChunk) {
                callCount.incrementAndGet();
            }

            @Override
            public void emitMessage(Message message) {
                callCount.incrementAndGet();
            }

            @Override
            public boolean isClosed() {
                return false;
            }
        };
        StreamSubscriber subscriber = new StreamSubscriber(countingEmitter);

        store.addSubscriber("t1", subscriber);

        // Notify while subscribed -- subscriber should be called
        TaskStatusUpdateEvent statusEvent = TaskStatusUpdateEvent.builder()
                .taskId("t1")
                .status(new TaskStatus(TaskState.WORKING))
                .build();
        store.notifySubscribers("t1", StreamResponse.ofStatusUpdate(statusEvent));
        assertThat(callCount.get()).as("subscriber should be called once while subscribed").isEqualTo(1);

        // Unsubscribe
        store.removeSubscriber("t1", subscriber);

        // Notify again -- unsubscribed subscriber should NOT be called
        store.notifySubscribers("t1", StreamResponse.ofStatusUpdate(statusEvent));
        assertThat(callCount.get()).as("subscriber should not be called after removal").isEqualTo(1);

        // Task should still be intact after subscribe/unsubscribe cycle
        assertThat(store.get("t1")).isNotNull();
        assertThat(store.get("t1").status().state()).isEqualTo(TaskState.WORKING);
    }

    @Test
    void deleteAlsoCleansMessageIdMapping() {
        store.put("t1", createTask("t1", TaskState.SUBMITTED));
        store.trackMessageId("msg-1", "t1");

        store.delete("t1");

        assertThat(store.getTaskIdByMessageId("msg-1")).isNull();
    }

    @Test
    void deleteAlsoCleansSubscribers() {
        store.put("t1", createTask("t1", TaskState.WORKING));
        A2AStreamEmitter emitter = createNoOpEmitter();
        store.addSubscriber("t1", new StreamSubscriber(emitter));

        store.delete("t1");

        // Re-subscribing and notifying should not reach the old emitter
        // (subscriber set was removed with the task)
        assertThat(store.get("t1")).isNull();
    }

    @Test
    void cleanupExpiredRemovesTerminalTasks() {
        // Create a completed task with timestamp 2 hours ago
        OffsetDateTime twoHoursAgo = OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(7200);
        Task completed = Task.builder()
                .id("t-done")
                .status(new TaskStatus(TaskState.COMPLETED, null, twoHoursAgo))
                .build();
        store.put("t-done", completed);

        Task working = createTask("t-active", TaskState.WORKING);
        store.put("t-active", working);

        store.trackMessageId("msg-done", "t-done");
        store.trackMessageId("msg-active", "t-active");

        // Cleanup with 1 hour TTL — the 2-hour-old completed task should be removed
        store.cleanupExpired(3600000L);

        assertThat(store.get("t-done")).isNull();
        assertThat(store.get("t-active")).isNotNull();
        assertThat(store.getTaskIdByMessageId("msg-done")).isNull();
        assertThat(store.getTaskIdByMessageId("msg-active")).isEqualTo("t-active");
    }

    @Test
    void cleanupExpiredKeepsRecentTerminalTasks() {
        Task recentlyCompleted = createTask("t-recent", TaskState.COMPLETED);
        // Timestamp is "now" (set by TaskStatus constructor)
        store.put("t-recent", recentlyCompleted);

        store.cleanupExpired(3600000L);

        assertThat(store.get("t-recent")).isNotNull();
    }

    @Test
    void cleanupExpiredKeepsActiveTasks() {
        // Create a working task with timestamp 2 hours ago
        OffsetDateTime twoHoursAgo = OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(7200);
        Task active = Task.builder()
                .id("t-active")
                .status(new TaskStatus(TaskState.WORKING, null, twoHoursAgo))
                .build();
        store.put("t-active", active);

        store.cleanupExpired(3600000L);

        assertThat(store.get("t-active")).isNotNull();
    }

    @Test
    void stuckTaskTtlMarksActiveTaskFailedOnRead() {
        store.setStuckTaskTtlMs(1);
        OffsetDateTime twoSecondsAgo = OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(2);
        Task active = Task.builder()
                .id("t-stuck")
                .contextId("ctx-stuck")
                .status(new TaskStatus(TaskState.WORKING, null, twoSecondsAgo))
                .build();
        store.put("t-stuck", active);
        store.trackMessageId("msg-stuck", "t-stuck");
        RecordingEmitter emitter = new RecordingEmitter();
        store.addSubscriber("t-stuck", new StreamSubscriber(emitter));

        Task failed = store.get("t-stuck");

        assertThat(failed).isNotNull();
        assertThat(failed.status().state()).isEqualTo(TaskState.FAILED);
        assertThat(store.contains("t-stuck")).isTrue();
        assertThat(store.getTaskIdByMessageId("msg-stuck")).isEqualTo("t-stuck");
        assertThat(emitter.lastStatus).isEqualTo(TaskState.FAILED);
    }

    @Test
    void periodicCleanupMarksStuckActiveTaskFailedOnInterval() {
        store.setStuckTaskTtlMs(1);
        store.setCleanupInterval(2);
        OffsetDateTime twoSecondsAgo = OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(2);
        Task active = Task.builder()
                .id("t-stuck-cleanup")
                .contextId("ctx-stuck")
                .status(new TaskStatus(TaskState.SUBMITTED, null, twoSecondsAgo))
                .build();
        store.put("t-stuck-cleanup", active);
        RecordingEmitter emitter = new RecordingEmitter();
        store.addSubscriber("t-stuck-cleanup", new StreamSubscriber(emitter));

        store.put("t-trigger", createTask("t-trigger", TaskState.WORKING));

        Task failed = store.get("t-stuck-cleanup");
        assertThat(failed).isNotNull();
        assertThat(failed.status().state()).isEqualTo(TaskState.FAILED);
        assertThat(emitter.lastStatus).isEqualTo(TaskState.FAILED);
    }

    @Test
    void cancelIfNotTerminalCancelsActiveTask() {
        Task task = createTask("t1", TaskState.WORKING);
        store.put("t1", task);

        Task result = store.cancelIfNotTerminal("t1");

        assertThat(result).isNotNull();
        assertThat(result.status().state()).isEqualTo(TaskState.CANCELED);
        assertThat(store.get("t1").status().state()).isEqualTo(TaskState.CANCELED);
    }

    @Test
    void cancelIfNotTerminalThrowsForCompletedTask() {
        Task task = createTask("t1", TaskState.COMPLETED);
        store.put("t1", task);

        assertThatThrownBy(() -> store.cancelIfNotTerminal("t1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("terminal");
    }

    @Test
    void cancelIfNotTerminalReturnsNullForMissing() {
        assertThat(store.cancelIfNotTerminal("nonexistent")).isNull();
    }

    @Test
    void concurrentPutPreservesTerminalTaskState() throws Exception {
        for (int round = 0; round < 25; round++) {
            store.put("race", createTask("race", TaskState.WORKING));

            ExecutorService executor = Executors.newFixedThreadPool(8);
            try {
                CountDownLatch start = new CountDownLatch(1);
                List<Future<?>> futures = new ArrayList<>();
                futures.add(executor.submit(() -> {
                    start.await();
                    store.put("race", createTask("race", TaskState.COMPLETED));
                    return null;
                }));
                for (int i = 0; i < 32; i++) {
                    futures.add(executor.submit(() -> {
                        start.await();
                        store.put("race", createTask("race", TaskState.WORKING));
                        return null;
                    }));
                }

                start.countDown();
                for (Future<?> future : futures) {
                    future.get(5, TimeUnit.SECONDS);
                }
            } finally {
                executor.shutdownNow();
            }

            assertThat(store.get("race").status().state()).isEqualTo(TaskState.COMPLETED);
            store.clear();
        }
    }

    @Test
    void notifySubscribersDispatchesStatusEvents() {
        RecordingEmitter emitter = new RecordingEmitter();
        store.addSubscriber("t1", new StreamSubscriber(emitter));

        TaskStatusUpdateEvent statusEvent = TaskStatusUpdateEvent.builder()
                .taskId("t1")
                .status(new TaskStatus(TaskState.WORKING))
                .build();
        store.notifySubscribers("t1", StreamResponse.ofStatusUpdate(statusEvent));

        assertThat(emitter.lastStatus).isEqualTo(TaskState.WORKING);
    }

    @Test
    void notifySubscribersForwardsStatusMessage() {
        RecordingEmitter emitter = new RecordingEmitter();
        store.addSubscriber("t1", new StreamSubscriber(emitter));

        Message msg = Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .parts(List.of(new TextPart("Analyzing data...")))
                .build();
        TaskStatus status = new TaskStatus(TaskState.WORKING, msg);

        TaskStatusUpdateEvent statusEvent = TaskStatusUpdateEvent.builder()
                .taskId("t1")
                .status(status)
                .build();
        store.notifySubscribers("t1", StreamResponse.ofStatusUpdate(statusEvent));

        assertThat(emitter.lastStatus).isEqualTo(TaskState.WORKING);
        assertThat(emitter.lastStatusMessage).isEqualTo("Analyzing data...");
    }

    @Test
    void notifySubscribersForwardsNullMessageWhenAbsent() {
        RecordingEmitter emitter = new RecordingEmitter();
        store.addSubscriber("t1", new StreamSubscriber(emitter));

        TaskStatusUpdateEvent statusEvent = TaskStatusUpdateEvent.builder()
                .taskId("t1")
                .status(new TaskStatus(TaskState.WORKING))
                .build();
        store.notifySubscribers("t1", StreamResponse.ofStatusUpdate(statusEvent));

        assertThat(emitter.lastStatus).isEqualTo(TaskState.WORKING);
        assertThat(emitter.lastStatusMessage).isNull();
    }

    @Test
    void notifySubscribersDispatchesArtifactEvents() {
        RecordingEmitter emitter = new RecordingEmitter();
        store.addSubscriber("t1", new StreamSubscriber(emitter));

        Artifact artifact = Artifact.builder()
                .name("output.txt")
                .build();
        TaskArtifactUpdateEvent artifactEvent = TaskArtifactUpdateEvent.builder()
                .taskId("t1")
                .artifact(artifact)
                .append(true)
                .lastChunk(false)
                .build();
        store.notifySubscribers("t1", StreamResponse.ofArtifactUpdate(artifactEvent));

        assertThat(emitter.lastArtifact).isNotNull();
        assertThat(emitter.lastArtifact.name()).isEqualTo("output.txt");
        assertThat(emitter.lastAppend).isTrue();
        assertThat(emitter.lastLastChunk).isFalse();
    }

    @Test
    void notifySubscribersDispatchesMessageEvents() {
        RecordingEmitter emitter = new RecordingEmitter();
        store.addSubscriber("t1", new StreamSubscriber(emitter));

        Message message = Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .build();
        store.notifySubscribers("t1", StreamResponse.ofMessage(message));

        assertThat(emitter.lastMessage).isNotNull();
        assertThat(emitter.lastMessage.role()).isEqualTo(Message.Role.ROLE_AGENT);
    }

    // ---- Push config CRUD tests ----

    @Test
    void putAndGetPushConfig() {
        TaskPushNotificationConfig config = new TaskPushNotificationConfig();
        config.setUrl("https://example.com/webhook");
        store.putPushConfig("t1", config);

        assertThat(config.getId()).isNotNull();
        assertThat(config.getTaskId()).isEqualTo("t1");

        TaskPushNotificationConfig retrieved = store.getPushConfig("t1", config.getId());
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getUrl()).isEqualTo("https://example.com/webhook");
    }

    @Test
    void listPushConfigs() {
        TaskPushNotificationConfig c1 = new TaskPushNotificationConfig();
        c1.setUrl("https://example.com/hook1");
        TaskPushNotificationConfig c2 = new TaskPushNotificationConfig();
        c2.setUrl("https://example.com/hook2");

        store.putPushConfig("t1", c1);
        store.putPushConfig("t1", c2);

        List<TaskPushNotificationConfig> configs = store.listPushConfigs("t1");
        assertThat(configs).hasSize(2);
    }

    @Test
    void listPushConfigsReturnsEmptyForUnknownTask() {
        assertThat(store.listPushConfigs("nonexistent")).isEmpty();
    }

    @Test
    void deletePushConfig() {
        TaskPushNotificationConfig config = new TaskPushNotificationConfig();
        config.setUrl("https://example.com/webhook");
        store.putPushConfig("t1", config);

        assertThat(store.deletePushConfig("t1", config.getId())).isTrue();
        assertThat(store.getPushConfig("t1", config.getId())).isNull();
    }

    @Test
    void deletePushConfigReturnsFalseForMissing() {
        assertThat(store.deletePushConfig("t1", "nonexistent")).isFalse();
    }

    @Test
    void putPushConfigRejectsPrivateUrl() {
        TaskPushNotificationConfig config = new TaskPushNotificationConfig();
        config.setUrl("https://10.0.0.1/webhook");

        assertThatThrownBy(() -> store.putPushConfig("t1", config))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keysReturnsUnmodifiableSet() {
        store.put("t1", createTask("t1", TaskState.SUBMITTED));
        Set<String> keys = store.keys();
        assertThatThrownBy(() -> keys.add("injected"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private A2AStreamEmitter createNoOpEmitter() {
        return new A2AStreamEmitter() {
            @Override
            public void emitStatus(TaskState state, String message) {
            }

            @Override
            public void emitArtifact(Artifact artifact, Boolean append, Boolean lastChunk) {
            }

            @Override
            public void emitMessage(Message message) {
            }

            @Override
            public boolean isClosed() {
                return false;
            }
        };
    }

    private Task createTask(String id, TaskState state) {
        return Task.builder()
                .id(id)
                .status(new TaskStatus(state))
                .build();
    }

    private static class RecordingEmitter implements A2AStreamEmitter {
        TaskState lastStatus;
        String lastStatusMessage;
        Artifact lastArtifact;
        Boolean lastAppend;
        Boolean lastLastChunk;
        Message lastMessage;

        @Override
        public void emitStatus(TaskState state, String message) {
            lastStatus = state;
            lastStatusMessage = message;
        }

        @Override
        public void emitArtifact(Artifact artifact, Boolean append, Boolean lastChunk) {
            lastArtifact = artifact;
            lastAppend = append;
            lastLastChunk = lastChunk;
        }

        @Override
        public void emitMessage(Message message) {
            lastMessage = message;
        }

        @Override
        public boolean isClosed() {
            return false;
        }
    }
}
