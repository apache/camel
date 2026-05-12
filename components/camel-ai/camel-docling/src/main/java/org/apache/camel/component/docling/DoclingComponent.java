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
package org.apache.camel.component.docling;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Component for integrating with Docling document processing library.
 */
@Component("docling")
public class DoclingComponent extends DefaultComponent {

    private static final Logger LOG = LoggerFactory.getLogger(DoclingComponent.class);

    @Metadata
    DoclingConfiguration configuration;

    // Shared across all producers so that SUBMIT_ASYNC_CONVERSION and CHECK_CONVERSION_STATUS
    // (which may resolve to different endpoints/producers) can see each other's tasks.
    private final Map<String, AsyncTaskEntry> pendingAsyncTasks = new ConcurrentHashMap<>();
    private final AtomicLong taskIdCounter = new AtomicLong();
    private ScheduledExecutorService cleanupExecutor;

    public DoclingComponent() {
        this(null);
    }

    public DoclingComponent(CamelContext context) {
        super(context);
        this.configuration = new DoclingConfiguration();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // Start scheduled cleanup task for expired async tasks
        long ttl = configuration.getAsyncTaskTtl();
        // Run cleanup every 10% of TTL, with a 1 second minimum to bound the polling rate.
        // The cleanup is cheap when no tasks have expired (early return), so a low minimum is safe.
        long cleanupInterval = Math.max(ttl / 10, 1000);

        cleanupExecutor = getCamelContext().getExecutorServiceManager()
                .newScheduledThreadPool(this, "DoclingAsyncTaskCleanup", 1);

        cleanupExecutor.scheduleWithFixedDelay(
                this::cleanupExpiredTasks,
                cleanupInterval,
                cleanupInterval,
                TimeUnit.MILLISECONDS);

        LOG.debug("Started async task cleanup with TTL={}ms, cleanup interval={}ms", ttl, cleanupInterval);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        DoclingConfiguration config = this.configuration.copy();
        DoclingEndpoint endpoint = new DoclingEndpoint(uri, this, remaining, config);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    public DoclingConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * The configuration for the Docling Endpoint
     */
    public void setConfiguration(DoclingConfiguration configuration) {
        this.configuration = configuration;
    }

    Map<String, AsyncTaskEntry> getPendingAsyncTasks() {
        return pendingAsyncTasks;
    }

    AtomicLong getTaskIdCounter() {
        return taskIdCounter;
    }

    /**
     * Cleanup expired async tasks based on configured TTL. Runs periodically in background to prevent memory leaks.
     */
    private void cleanupExpiredTasks() {
        if (pendingAsyncTasks.isEmpty()) {
            return;
        }

        long ttl = configuration.getAsyncTaskTtl();
        long now = System.currentTimeMillis();
        List<String> expiredTaskIds = new ArrayList<>();

        // Identify expired tasks
        for (Map.Entry<String, AsyncTaskEntry> entry : pendingAsyncTasks.entrySet()) {
            AsyncTaskEntry taskEntry = entry.getValue();
            long age = now - taskEntry.getCreatedAtMs();

            if (age > ttl) {
                expiredTaskIds.add(entry.getKey());
            }
        }

        // Remove expired tasks
        for (String taskId : expiredTaskIds) {
            AsyncTaskEntry removed = pendingAsyncTasks.remove(taskId);
            if (removed != null) {
                // Cancel the future if still pending
                removed.getFuture().cancel(true);
                LOG.debug("Evicted expired async task: {} (age: {}ms, TTL: {}ms)",
                        taskId, removed.getAgeMs(), ttl);
            }
        }

        if (!expiredTaskIds.isEmpty()) {
            LOG.debug("Cleaned up {} expired async tasks (current map size: {})",
                    expiredTaskIds.size(), pendingAsyncTasks.size());
        }
    }

    @Override
    protected void doStop() throws Exception {
        // Shutdown cleanup executor
        if (cleanupExecutor != null) {
            getCamelContext().getExecutorServiceManager().shutdownGraceful(cleanupExecutor);
            cleanupExecutor = null;
            LOG.debug("Stopped async task cleanup executor");
        }

        // Cancel and clear all pending tasks
        pendingAsyncTasks.forEach((id, entry) -> entry.getFuture().cancel(true));
        pendingAsyncTasks.clear();
        super.doStop();
    }

}
