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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import org.apache.camel.component.a2a.model.StreamResponse;
import org.apache.camel.component.a2a.model.Task;
import org.apache.camel.component.a2a.model.TaskPushNotificationConfig;
import org.apache.camel.component.a2a.model.TaskState;
import org.apache.camel.component.a2a.model.TaskStatus;
import org.apache.camel.component.a2a.model.TaskStatusUpdateEvent;
import org.apache.camel.component.a2a.util.WebhookUrlValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread-safe in-memory implementation of {@link A2ATaskStore} using {@link ConcurrentHashMap}.
 * <p>
 * Cleanup strategy (lazy hybrid):
 * <ul>
 * <li><b>Read-time eviction</b> — {@link #get(String)} and {@link #contains(String)} check TTL for terminal tasks and
 * evict expired ones on access. Callers never see expired data.</li>
 * <li><b>Stuck-task failure</b> — if {@code stuckTaskTtlMs} is enabled, stale non-terminal tasks transition to
 * {@code FAILED} and subscribers are notified instead of losing the task snapshot.</li>
 * <li><b>Capacity cap</b> — {@link #put(String, Task)} triggers eviction of the oldest terminal tasks when the store
 * exceeds {@code maxStoredTasks}. Prevents unbounded memory growth.</li>
 * <li><b>Periodic bulk cleanup</b> — every {@code cleanupInterval} puts, {@link #cleanupExpired(long)} scans and
 * removes all expired terminal tasks as a backup mechanism.</li>
 * </ul>
 */
public class InMemoryTaskStore implements A2ATaskStore {

    private static final Logger LOG = LoggerFactory.getLogger(InMemoryTaskStore.class);

    private final ConcurrentHashMap<String, Task> tasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> messageIdToTaskId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<A2ATaskSubscriber>> subscribers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, TaskPushNotificationConfig>> pushConfigs
            = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<A2ATaskSubscriber> globalSubscribers = new CopyOnWriteArrayList<>();
    private final ReadWriteLock evictionLock = new ReentrantReadWriteLock();

    private final AtomicInteger putCounter = new AtomicInteger(0);

    private volatile long completedTaskTtlMs = 3600000L;
    private volatile long stuckTaskTtlMs = 0;
    private volatile int maxStoredTasks = 10000;
    private volatile int cleanupInterval = 100;
    private volatile boolean allowLocalWebhookUrls;

    // ---- Configuration ----

    /**
     * TTL for terminal tasks (COMPLETED, FAILED, CANCELED, REJECTED). Tasks older than this are evicted on read or
     * during periodic cleanup. Default: 1 hour.
     */
    public void setCompletedTaskTtlMs(long ttlMs) {
        this.completedTaskTtlMs = ttlMs;
    }

    public long getCompletedTaskTtlMs() {
        return completedTaskTtlMs;
    }

    /**
     * TTL for non-terminal stuck tasks (SUBMITTED, WORKING). Tasks older than this transition to FAILED on read or
     * cleanup. Default: 0 (disabled — let asyncTimeout handle stuck tasks). Set to a positive value as a last-resort
     * safety net.
     */
    public void setStuckTaskTtlMs(long ttlMs) {
        this.stuckTaskTtlMs = ttlMs;
    }

    public long getStuckTaskTtlMs() {
        return stuckTaskTtlMs;
    }

    /**
     * Maximum number of tasks stored. When exceeded, the oldest terminal tasks are evicted on put. Default: 10000. Set
     * to 0 for unlimited.
     */
    public void setMaxStoredTasks(int max) {
        this.maxStoredTasks = max;
    }

    public int getMaxStoredTasks() {
        return maxStoredTasks;
    }

    /**
     * Number of put() calls between periodic bulk cleanup runs. Default: 100.
     */
    public void setCleanupInterval(int interval) {
        this.cleanupInterval = Math.max(1, interval);
    }

    public int getCleanupInterval() {
        return cleanupInterval;
    }

    public void setAllowLocalWebhookUrls(boolean allow) {
        this.allowLocalWebhookUrls = allow;
    }

    // ---- Core operations ----

    @Override
    public void put(String taskId, Task task) {
        Objects.requireNonNull(task, "task");
        AtomicBoolean stored = new AtomicBoolean();
        tasks.compute(taskId, (key, existing) -> {
            if (isTerminal(existing)) {
                LOG.debug("Ignoring put for task {} — already in terminal state {}", taskId, existing.status().state());
                return existing;
            }
            stored.set(true);
            return task;
        });

        if (!stored.get()) {
            return;
        }

        if (maxStoredTasks > 0 && tasks.size() > maxStoredTasks) {
            evictOldestTerminal();
        }

        if (putCounter.incrementAndGet() % cleanupInterval == 0) {
            cleanupExpired(completedTaskTtlMs);
        }
    }

    @Override
    public Task get(String taskId) {
        Task task = tasks.get(taskId);
        if (task != null && isTerminalExpired(task)) {
            evict(taskId);
            LOG.debug("Task {} evicted on read (expired)", taskId);
            return null;
        }
        if (task != null && isStuck(task)) {
            return failStuckTask(taskId, task);
        }
        return task;
    }

    @Override
    public Task delete(String taskId) {
        evictionLock.writeLock().lock();
        try {
            Task removed = tasks.remove(taskId);
            if (removed != null) {
                messageIdToTaskId.values().removeIf(mappedTaskId -> mappedTaskId.equals(taskId));
                subscribers.remove(taskId);
                pushConfigs.remove(taskId);
            }
            return removed;
        } finally {
            evictionLock.writeLock().unlock();
        }
    }

    @Override
    public boolean contains(String taskId) {
        Task task = tasks.get(taskId);
        if (task != null && isTerminalExpired(task)) {
            evict(taskId);
            return false;
        }
        if (task != null && isStuck(task)) {
            failStuckTask(taskId, task);
        }
        return task != null;
    }

    @Override
    public Set<String> keys() {
        return Collections.unmodifiableSet(new HashSet<>(tasks.keySet()));
    }

    @Override
    public void clear() {
        evictionLock.writeLock().lock();
        try {
            tasks.clear();
            messageIdToTaskId.clear();
            subscribers.clear();
            pushConfigs.clear();
        } finally {
            evictionLock.writeLock().unlock();
        }
    }

    @Override
    public void trackMessageId(String messageId, String taskId) {
        messageIdToTaskId.put(messageId, taskId);
    }

    @Override
    public String getTaskIdByMessageId(String messageId) {
        return messageIdToTaskId.get(messageId);
    }

    @Override
    public List<Task> list(String contextId, List<TaskState> states, int maxResults) {
        return tasks.values().stream()
                .filter(task -> {
                    if (isTerminalExpired(task)) {
                        evict(task.id());
                        return false;
                    }
                    return true;
                })
                .map(task -> isStuck(task) ? failStuckTask(task.id(), task) : task)
                .filter(task -> task != null)
                .filter(task -> contextId == null || contextId.equals(task.contextId()))
                .filter(task -> states == null || states.isEmpty()
                        || (task.status() != null && states.contains(task.status().state())))
                .sorted((leftTask, rightTask) -> {
                    OffsetDateTime leftTimestamp
                            = leftTask.status() != null ? leftTask.status().timestamp() : OffsetDateTime.MIN;
                    OffsetDateTime rightTimestamp
                            = rightTask.status() != null ? rightTask.status().timestamp() : OffsetDateTime.MIN;
                    return rightTimestamp.compareTo(leftTimestamp);
                })
                .limit(maxResults)
                .collect(Collectors.toList());
    }

    // ---- Subscribers ----

    @Override
    public void addSubscriber(String taskId, A2ATaskSubscriber subscriber) {
        subscribers.computeIfAbsent(taskId, k -> new CopyOnWriteArrayList<>()).add(subscriber);
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
        List<A2ATaskSubscriber> subscriberSnapshot;

        evictionLock.readLock().lock();
        try {
            List<A2ATaskSubscriber> taskSubscribers = subscribers.get(taskId);
            subscriberSnapshot = taskSubscribers != null ? new ArrayList<>(taskSubscribers) : List.of();
        } finally {
            evictionLock.readLock().unlock();
        }

        for (A2ATaskSubscriber subscriber : subscriberSnapshot) {
            try {
                subscriber.onEvent(taskId, event);
            } catch (Exception e) {
                LOG.warn("Subscriber notification failed for task {}: {}", taskId, e.getMessage());
            }
        }

        for (A2ATaskSubscriber globalSub : globalSubscribers) {
            try {
                globalSub.onEvent(taskId, event);
            } catch (Exception e) {
                LOG.warn("Global subscriber notification failed for task {}: {}", taskId, e.getMessage());
            }
        }

        if (event.getStatusUpdate() != null && event.getStatusUpdate().isFinal()) {
            subscribers.remove(taskId);
        }
    }

    @Override
    public void updateStatusAndNotify(String taskId, TaskStatus status) {
        AtomicReference<TaskStatusUpdateEvent> pendingEvent = new AtomicReference<>();
        tasks.computeIfPresent(taskId, (k, task) -> {
            if (task.status() != null && task.status().state() != null
                    && task.status().state().isTerminal()) {
                LOG.debug("Ignoring status update for task {} — already in terminal state {}",
                        taskId, task.status().state());
                return task;
            }
            Task updated = Task.builder(task).status(status).build();
            pendingEvent.set(TaskStatusUpdateEvent.builder()
                    .taskId(taskId)
                    .contextId(task.contextId())
                    .status(status)
                    .build());
            return updated;
        });
        TaskStatusUpdateEvent event = pendingEvent.get();
        if (event != null) {
            notifySubscribers(taskId, StreamResponse.ofStatusUpdate(event));
        }
    }

    @Override
    public Task cancelIfNotTerminal(String taskId) {
        AtomicReference<IllegalStateException> error = new AtomicReference<>();
        Task result = tasks.computeIfPresent(taskId, (k, task) -> {
            if (task.status() != null && task.status().state() != null
                    && task.status().state().isTerminal()) {
                error.set(new IllegalStateException(
                        "Task is in terminal state: " + task.status().state()));
                return task;
            }
            return Task.builder(task).status(new TaskStatus(TaskState.CANCELED)).build();
        });
        if (error.get() != null) {
            throw error.get();
        }
        return result;
    }

    // ---- Push notification config storage ----

    @Override
    public void putPushConfig(String taskId, TaskPushNotificationConfig config) {
        WebhookUrlValidator.validate(config.getUrl(), allowLocalWebhookUrls);
        if (config.getId() == null || config.getId().isEmpty()) {
            config.setId(UUID.randomUUID().toString());
        }
        config.setTaskId(taskId);
        pushConfigs.computeIfAbsent(taskId, k -> new ConcurrentHashMap<>())
                .put(config.getId(), config);
    }

    @Override
    public TaskPushNotificationConfig getPushConfig(String taskId, String configId) {
        ConcurrentHashMap<String, TaskPushNotificationConfig> configs = pushConfigs.get(taskId);
        return configs != null ? configs.get(configId) : null;
    }

    @Override
    public List<TaskPushNotificationConfig> listPushConfigs(String taskId) {
        ConcurrentHashMap<String, TaskPushNotificationConfig> configs = pushConfigs.get(taskId);
        return configs != null ? new ArrayList<>(configs.values()) : Collections.emptyList();
    }

    @Override
    public boolean deletePushConfig(String taskId, String configId) {
        ConcurrentHashMap<String, TaskPushNotificationConfig> configs = pushConfigs.get(taskId);
        if (configs != null) {
            boolean removed = configs.remove(configId) != null;
            if (configs.isEmpty()) {
                pushConfigs.remove(taskId);
            }
            return removed;
        }
        return false;
    }

    // ---- Cleanup ----

    /**
     * Check if a terminal task is expired based on its TTL configuration.
     * <p>
     * Terminal tasks expire after {@code completedTaskTtlMs}. Non-terminal tasks are not expired; they can be failed by
     * {@link #isStuck(Task)} when the stuck-task TTL is enabled.
     */
    private boolean isTerminalExpired(Task task) {
        if (task.status() == null || task.status().timestamp() == null) {
            return false;
        }

        TaskState state = task.status().state();
        OffsetDateTime timestamp = task.status().timestamp();
        OffsetDateTime now = OffsetDateTime.now();

        if (state != null && state.isTerminal()) {
            return timestamp.isBefore(now.minusNanos(completedTaskTtlMs * 1_000_000L));
        }

        return false;
    }

    private boolean isStuck(Task task) {
        if (task.status() == null || task.status().timestamp() == null) {
            return false;
        }

        TaskState state = task.status().state();
        OffsetDateTime timestamp = task.status().timestamp();
        OffsetDateTime now = OffsetDateTime.now();

        if (stuckTaskTtlMs > 0 && state != null && !state.isTerminal()) {
            return timestamp.isBefore(now.minusNanos(stuckTaskTtlMs * 1_000_000L));
        }

        return false;
    }

    private static boolean isTerminal(Task task) {
        return task != null
                && task.status() != null
                && task.status().state() != null
                && task.status().state().isTerminal();
    }

    /**
     * Evict a single task and all its associated data (message ID mappings, subscribers, push configs).
     */
    private void evict(String taskId) {
        evictionLock.writeLock().lock();
        try {
            tasks.remove(taskId);
            messageIdToTaskId.values().removeIf(mappedTaskId -> mappedTaskId.equals(taskId));
            subscribers.remove(taskId);
            pushConfigs.remove(taskId);
        } finally {
            evictionLock.writeLock().unlock();
        }
    }

    private Task failStuckTask(String taskId, Task expectedTask) {
        AtomicReference<Task> failedTask = new AtomicReference<>();
        tasks.computeIfPresent(taskId, (key, current) -> {
            if (current != expectedTask && !current.equals(expectedTask)) {
                return current;
            }
            if (!isStuck(current)) {
                return current;
            }
            Task failed = Task.builder(current).status(new TaskStatus(TaskState.FAILED)).build();
            failedTask.set(failed);
            return failed;
        });

        Task failed = failedTask.get();
        if (failed != null) {
            LOG.debug("Task {} marked FAILED on read (stuck TTL exceeded)", taskId);
            notifySubscribers(taskId, StreamResponse.ofStatusUpdate(
                    TaskStatusUpdateEvent.builder()
                            .taskId(taskId)
                            .contextId(failed.contextId())
                            .status(failed.status())
                            .build()));
            return failed;
        }
        return tasks.get(taskId);
    }

    /**
     * Evict the oldest terminal task to make room when the capacity cap is exceeded. If no terminal tasks exist, no
     * eviction occurs — admission control ({@code maxConcurrentTasks}) should prevent this scenario.
     */
    private void evictOldestTerminal() {
        String oldestId = null;
        OffsetDateTime oldestTimestamp = OffsetDateTime.MAX;

        for (Map.Entry<String, Task> entry : tasks.entrySet()) {
            Task task = entry.getValue();
            if (task.status() != null && task.status().state() != null
                    && task.status().state().isTerminal()
                    && task.status().timestamp() != null
                    && task.status().timestamp().isBefore(oldestTimestamp)) {
                oldestId = entry.getKey();
                oldestTimestamp = task.status().timestamp();
            }
        }

        if (oldestId != null) {
            evict(oldestId);
            LOG.debug("Evicted oldest terminal task {} (capacity cap exceeded)", oldestId);
        }
    }

    /**
     * Bulk-remove terminal tasks older than the specified TTL. Called periodically as a backup cleanup mechanism
     * alongside the lazy read-time eviction.
     *
     * @param ttlMs TTL in milliseconds
     */
    public void cleanupExpired(long ttlMs) {
        OffsetDateTime cutoff = OffsetDateTime.now().minusNanos(ttlMs * 1_000_000L);
        int evicted = 0;
        int failed = 0;
        for (Map.Entry<String, Task> entry : tasks.entrySet()) {
            Task task = entry.getValue();
            if (task.status() != null && task.status().state() != null
                    && task.status().state().isTerminal()
                    && task.status().timestamp() != null
                    && task.status().timestamp().isBefore(cutoff)) {
                evict(entry.getKey());
                evicted++;
            } else if (isStuck(task)) {
                failStuckTask(entry.getKey(), task);
                failed++;
            }
        }
        if (evicted > 0) {
            LOG.debug("Periodic cleanup evicted {} expired terminal tasks", evicted);
        }
        if (failed > 0) {
            LOG.debug("Periodic cleanup marked {} stuck tasks FAILED", failed);
        }
    }

    /**
     * Returns the current number of tasks in the store (for monitoring/testing).
     */
    public int size() {
        return tasks.size();
    }
}
