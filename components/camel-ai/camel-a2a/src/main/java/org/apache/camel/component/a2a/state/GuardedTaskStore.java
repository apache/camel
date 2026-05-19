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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

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
 * Safety wrapper for custom {@link A2ATaskStore} implementations discovered from the registry.
 * <p>
 * The wrapper serializes terminal-preserving write operations ({@link #put(String, Task)},
 * {@link #updateStatusAndNotify(String, TaskStatus)}, and {@link #cancelIfNotTerminal(String)}) so callers using the
 * wrapped store cannot replace a terminal task snapshot through a check-then-put race. Subscriber registration and
 * notification are delegated to the wrapped store; this wrapper only tracks per-task subscribers so it can ask the
 * delegate to remove them after a terminal event.
 *
 * @since 4.21
 */
public final class GuardedTaskStore implements A2ATaskStore {

    private static final Logger LOG = LoggerFactory.getLogger(GuardedTaskStore.class);

    private final A2ATaskStore delegate;
    private final boolean allowLocalWebhookUrls;
    private final Object lifecycleLock = new Object();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<A2ATaskSubscriber>> subscribers
            = new ConcurrentHashMap<>();

    public GuardedTaskStore(A2ATaskStore delegate, boolean allowLocalWebhookUrls) {
        this.delegate = delegate;
        this.allowLocalWebhookUrls = allowLocalWebhookUrls;
    }

    @Override
    public void put(String taskId, Task task) {
        synchronized (lifecycleLock) {
            Task existing = delegate.get(taskId);
            if (isTerminal(existing)) {
                LOG.debug("Ignoring put for task {} - already in terminal state {}", taskId, existing.status().state());
                return;
            }
            delegate.put(taskId, task);
        }
    }

    @Override
    public Task get(String taskId) {
        return delegate.get(taskId);
    }

    @Override
    public Task delete(String taskId) {
        Task removed = delegate.delete(taskId);
        if (removed != null) {
            subscribers.remove(taskId);
        }
        return removed;
    }

    @Override
    public boolean contains(String taskId) {
        return delegate.contains(taskId);
    }

    @Override
    public Set<String> keys() {
        return delegate.keys();
    }

    @Override
    public void clear() {
        delegate.clear();
        subscribers.clear();
    }

    @Override
    public void trackMessageId(String messageId, String taskId) {
        delegate.trackMessageId(messageId, taskId);
    }

    @Override
    public String getTaskIdByMessageId(String messageId) {
        return delegate.getTaskIdByMessageId(messageId);
    }

    @Override
    public List<Task> list(String contextId, List<TaskState> states, int maxResults) {
        return delegate.list(contextId, states, maxResults);
    }

    @Override
    public void addSubscriber(String taskId, A2ATaskSubscriber subscriber) {
        subscribers.computeIfAbsent(taskId, key -> new CopyOnWriteArrayList<>()).add(subscriber);
        delegate.addSubscriber(taskId, subscriber);
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
        delegate.removeSubscriber(taskId, subscriber);
    }

    @Override
    public void notifySubscribers(String taskId, StreamResponse event) {
        delegate.notifySubscribers(taskId, event);
        if (event.getStatusUpdate() != null && event.getStatusUpdate().isFinal()) {
            removeTrackedSubscribers(taskId);
        }
    }

    @Override
    public void addGlobalSubscriber(A2ATaskSubscriber subscriber) {
        delegate.addGlobalSubscriber(subscriber);
    }

    @Override
    public void putPushConfig(String taskId, TaskPushNotificationConfig config) {
        WebhookUrlValidator.validate(config.getUrl(), allowLocalWebhookUrls);
        if (config.getId() == null || config.getId().isEmpty()) {
            config.setId(UUID.randomUUID().toString());
        }
        config.setTaskId(taskId);
        delegate.putPushConfig(taskId, config);
    }

    @Override
    public TaskPushNotificationConfig getPushConfig(String taskId, String configId) {
        return delegate.getPushConfig(taskId, configId);
    }

    @Override
    public List<TaskPushNotificationConfig> listPushConfigs(String taskId) {
        return delegate.listPushConfigs(taskId);
    }

    @Override
    public boolean deletePushConfig(String taskId, String configId) {
        return delegate.deletePushConfig(taskId, configId);
    }

    @Override
    public void cleanupExpired(long ttlMs) {
        delegate.cleanupExpired(ttlMs);
    }

    @Override
    public void updateStatusAndNotify(String taskId, TaskStatus status) {
        StreamResponse event;
        synchronized (lifecycleLock) {
            Task task = delegate.get(taskId);
            if (task == null) {
                return;
            }
            if (isTerminal(task)) {
                LOG.debug("Ignoring status update for task {} - already in terminal state {}",
                        taskId, task.status().state());
                return;
            }
            Task updated = Task.builder(task).status(status).build();
            delegate.put(taskId, updated);
            event = StreamResponse.ofStatusUpdate(TaskStatusUpdateEvent.builder()
                    .taskId(taskId)
                    .contextId(task.contextId())
                    .status(status)
                    .build());
        }
        notifySubscribers(taskId, event);
    }

    @Override
    public Task cancelIfNotTerminal(String taskId) {
        synchronized (lifecycleLock) {
            Task task = delegate.get(taskId);
            if (task == null) {
                return null;
            }
            if (isTerminal(task)) {
                throw new IllegalStateException(
                        "Task is in terminal state: " + task.status().state());
            }
            Task canceled = Task.builder(task).status(new TaskStatus(TaskState.CANCELED)).build();
            delegate.put(taskId, canceled);
            return canceled;
        }
    }

    private static boolean isTerminal(Task task) {
        return task != null
                && task.status() != null
                && task.status().state() != null
                && task.status().state().isTerminal();
    }

    private void removeTrackedSubscribers(String taskId) {
        List<A2ATaskSubscriber> removed = subscribers.remove(taskId);
        if (removed != null) {
            for (A2ATaskSubscriber subscriber : removed) {
                delegate.removeSubscriber(taskId, subscriber);
            }
        }
    }
}
