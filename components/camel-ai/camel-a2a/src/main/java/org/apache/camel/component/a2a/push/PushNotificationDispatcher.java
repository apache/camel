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
package org.apache.camel.component.a2a.push;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.component.a2a.A2AConstants;
import org.apache.camel.component.a2a.model.AuthenticationInfo;
import org.apache.camel.component.a2a.model.StreamResponse;
import org.apache.camel.component.a2a.model.TaskPushNotificationConfig;
import org.apache.camel.component.a2a.state.A2ATaskStore;
import org.apache.camel.component.a2a.util.A2AJsonMapper;
import org.apache.camel.component.a2a.util.WebhookUrlValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dispatches push notification events to registered webhook URLs. When task state changes, the task store calls
 * {@link #dispatch(String, StreamResponse)} which POSTs the event to all registered push configs for that task in
 * parallel. Supports configurable retry with exponential backoff.
 */
public class PushNotificationDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(PushNotificationDispatcher.class);
    private static final ObjectMapper OBJECT_MAPPER = A2AJsonMapper.instance();
    private static final int HTTP_GONE = 410;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_RETRY_ATTEMPTS = 30;
    private static final long MAX_RETRY_DELAY_MS = TimeUnit.HOURS.toMillis(1);
    private static final int MAX_PENDING_WORK = 1024;

    private final HttpClient httpClient;
    private final A2ATaskStore store;
    private final int maxRetries;
    private final long initialBackoffMs;
    private final ScheduledExecutorService executor;
    private final boolean allowLocalWebhookUrls;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicInteger pendingWork = new AtomicInteger();
    private final Set<CompletableFuture<?>> inFlightRequests = ConcurrentHashMap.newKeySet();
    private final Set<ScheduledFuture<?>> scheduledRetries = ConcurrentHashMap.newKeySet();

    public PushNotificationDispatcher(HttpClient httpClient, A2ATaskStore store,
                                      int maxRetries, long initialBackoffMs,
                                      ScheduledExecutorService executor,
                                      boolean allowLocalWebhookUrls) {
        if (maxRetries < 0 || maxRetries > MAX_RETRY_ATTEMPTS) {
            throw new IllegalArgumentException("maxRetries must be between 0 and " + MAX_RETRY_ATTEMPTS);
        }
        if (initialBackoffMs < 0) {
            throw new IllegalArgumentException("initialBackoffMs must be greater than or equal to zero");
        }
        if (maxRetries > 0 && initialBackoffMs == 0) {
            throw new IllegalArgumentException("initialBackoffMs must be greater than zero when retries are enabled");
        }
        this.httpClient = httpClient;
        this.store = store;
        this.maxRetries = maxRetries;
        this.initialBackoffMs = initialBackoffMs;
        this.executor = executor;
        this.allowLocalWebhookUrls = allowLocalWebhookUrls;
    }

    /**
     * Dispatch a push notification event to all registered webhook configs for the given task. Each config is
     * dispatched in parallel on a background thread (fire-and-forget).
     */
    public void dispatch(String taskId, StreamResponse event) {
        dispatch(store.listPushConfigs(taskId), taskId, event);
    }

    /**
     * Dispatch a push notification event using pre-fetched configs. Use this overload when configs were snapshotted
     * under a lock to avoid the eviction-notification race.
     */
    public void dispatch(List<TaskPushNotificationConfig> configs, String taskId, StreamResponse event) {
        if (closed.get() || configs.isEmpty()) {
            return;
        }

        byte[] body;
        try {
            body = OBJECT_MAPPER.writeValueAsBytes(event);
        } catch (Exception e) {
            LOG.error("Failed to serialize push notification event for taskId={}", taskId, e);
            return;
        }

        for (TaskPushNotificationConfig config : configs) {
            if (!tryAdmitWork(config, "dispatch")) {
                continue;
            }
            try {
                trackAdmittedWork(CompletableFuture.runAsync(() -> dispatchToWebhook(config, body), executor));
            } catch (RejectedExecutionException e) {
                releaseWork();
                LOG.debug("Push notification executor rejected webhook dispatch for taskId={}", taskId, e);
            }
        }
    }

    private void dispatchToWebhook(TaskPushNotificationConfig config, byte[] body) {
        if (closed.get()) {
            return;
        }
        try {
            WebhookUrlValidator.validate(config.getUrl(), allowLocalWebhookUrls);
        } catch (IllegalArgumentException e) {
            LOG.warn("Skipping invalid push notification webhook for taskId={}: {}", config.getTaskId(), e.getMessage());
            return;
        }

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(config.getUrl()))
                .header("Content-Type", A2AConstants.CONTENT_TYPE)
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));

        applyAuth(requestBuilder, config);
        HttpRequest request = requestBuilder.build();

        sendWithRetry(request, config);
    }

    private void applyAuth(HttpRequest.Builder builder, TaskPushNotificationConfig config) {
        AuthenticationInfo auth = config.getAuthentication();
        if (auth != null && auth.getScheme() != null && auth.getCredentials() != null) {
            builder.header("Authorization", auth.getScheme() + " " + auth.getCredentials());
        } else if (config.getToken() != null) {
            builder.header("Authorization", "Bearer " + config.getToken());
        }
    }

    private void sendWithRetry(HttpRequest request, TaskPushNotificationConfig config) {
        sendWithRetryAttempt(request, config, 0);
    }

    private void sendWithRetryAttempt(HttpRequest request, TaskPushNotificationConfig config, int attempt) {
        if (closed.get()) {
            return;
        }
        if (!tryAdmitWork(config, "HTTP request")) {
            return;
        }
        CompletableFuture<HttpResponse<InputStream>> responseFuture;
        try {
            responseFuture = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (RuntimeException e) {
            releaseWork();
            throw e;
        }
        trackAdmittedWork(responseFuture);
        try {
            track(responseFuture.whenCompleteAsync((response, failure) -> {
                if (closed.get()) {
                    if (response != null) {
                        closeResponseBody(response.body(), config);
                    }
                    return;
                }
                boolean retry = false;
                if (failure == null) {
                    closeResponseBody(response.body(), config);
                    retry = shouldRetry(response, config, attempt);
                } else {
                    Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                            ? failure.getCause()
                            : failure;
                    LOG.warn("Push notification to {} failed (attempt {}/{}, taskId={}): {}",
                            config.getUrl(), attempt + 1, maxRetries + 1, config.getTaskId(), cause.getMessage());
                    retry = true;
                }
                if (retry && attempt < maxRetries) {
                    scheduleRetry(request, config, attempt);
                } else if (retry) {
                    LOG.error("Push notification to {} exhausted all {} retries for taskId={}",
                            config.getUrl(), maxRetries + 1, config.getTaskId());
                }
            }, executor));
        } catch (RejectedExecutionException e) {
            responseFuture.cancel(true);
            LOG.debug("Push notification executor rejected completion handling for taskId={}", config.getTaskId(), e);
        }
    }

    private void scheduleRetry(HttpRequest request, TaskPushNotificationConfig config, int attempt) {
        if (closed.get()) {
            return;
        }
        if (!tryAdmitWork(config, "retry")) {
            return;
        }
        long delay = retryDelay(attempt);
        AtomicReference<ScheduledFuture<?>> scheduledRef = new AtomicReference<>();
        try {
            ScheduledFuture<?> scheduled = executor.schedule(() -> {
                scheduledRetries.remove(scheduledRef.get());
                releaseWork();
                sendWithRetryAttempt(request, config, attempt + 1);
            }, delay, TimeUnit.MILLISECONDS);
            scheduledRef.set(scheduled);
            scheduledRetries.add(scheduled);
        } catch (RejectedExecutionException e) {
            releaseWork();
            LOG.debug("Push notification executor rejected retry scheduling for taskId={}", config.getTaskId(), e);
        }
    }

    private long retryDelay(int attempt) {
        if (initialBackoffMs == 0) {
            return 0;
        }
        int shift = Math.min(attempt, Long.SIZE - 2);
        long multiplier = 1L << shift;
        long delay = initialBackoffMs > Long.MAX_VALUE / multiplier
                ? Long.MAX_VALUE
                : initialBackoffMs * multiplier;
        return Math.min(delay, MAX_RETRY_DELAY_MS);
    }

    private void trackAdmittedWork(CompletableFuture<?> future) {
        track(future, true);
    }

    private void track(CompletableFuture<?> future) {
        track(future, false);
    }

    private void track(CompletableFuture<?> future, boolean releaseOnCompletion) {
        inFlightRequests.add(future);
        future.whenComplete((result, failure) -> {
            inFlightRequests.remove(future);
            if (releaseOnCompletion) {
                releaseWork();
            }
        });
    }

    private boolean tryAdmitWork(TaskPushNotificationConfig config, String workType) {
        while (true) {
            int current = pendingWork.get();
            if (current >= MAX_PENDING_WORK) {
                LOG.warn("Dropping push notification {} for taskId={} — pending work limit {} reached",
                        workType, config.getTaskId(), MAX_PENDING_WORK);
                return false;
            }
            if (pendingWork.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    private void releaseWork() {
        pendingWork.updateAndGet(current -> current > 0 ? current - 1 : 0);
    }

    private boolean shouldRetry(HttpResponse<InputStream> response, TaskPushNotificationConfig config, int attempt) {
        int status = response.statusCode();

        if (status >= 200 && status < 300) {
            LOG.debug("Push notification delivered to {} for taskId={}",
                    config.getUrl(), config.getTaskId());
            return false;
        }

        if (status == HTTP_GONE) {
            LOG.info("Webhook {} returned 410 Gone — removing push config {} for taskId={}",
                    config.getUrl(), config.getId(), config.getTaskId());
            store.deletePushConfig(config.getTaskId(), config.getId());
            return false;
        }

        if (status >= 400 && status < 500) {
            LOG.warn("Push notification to {} failed with client error {} — no retry (taskId={})",
                    config.getUrl(), status, config.getTaskId());
            return false;
        }

        LOG.warn("Push notification to {} failed with status {} (attempt {}/{}, taskId={})",
                config.getUrl(), status, attempt + 1, maxRetries + 1, config.getTaskId());
        return true;
    }

    public void shutdown() {
        if (closed.compareAndSet(false, true)) {
            for (ScheduledFuture<?> scheduledRetry : scheduledRetries) {
                if (scheduledRetry.cancel(false)) {
                    releaseWork();
                }
            }
            scheduledRetries.clear();
            for (CompletableFuture<?> inFlightRequest : inFlightRequests) {
                inFlightRequest.cancel(true);
            }
            inFlightRequests.clear();
            executor.shutdownNow();
        }
    }

    private static void closeResponseBody(InputStream body, TaskPushNotificationConfig config) {
        try {
            body.close();
        } catch (IOException e) {
            LOG.debug("Failed to close push notification response body for taskId={}: {}",
                    config.getTaskId(), e.getMessage());
        }
    }
}
