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

package org.apache.camel.component.wal;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A log supervisor that runs in the background executing a task in the background. It is used to flush the data to disk
 * at regular intervals
 */
public class DefaultLogSupervisor implements LogSupervisor {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultLogSupervisor.class);
    private long interval;

    private ScheduledExecutorService scheduledExecutorService;

    /**
     * Constructs a new log supervisor
     *
     * @param interval the interval between executions of the task
     */
    public DefaultLogSupervisor(long interval) {
        this(interval, Executors.newScheduledThreadPool(1));
    }

    /**
     * Constructs a new log supervisor
     *
     * @param interval                 the interval between executions of the task
     * @param scheduledExecutorService the executor service to use for running the task
     */
    public DefaultLogSupervisor(long interval, ScheduledExecutorService scheduledExecutorService) {
        this.interval = interval;
        this.scheduledExecutorService = scheduledExecutorService;
    }

    @Override
    public void start(Runnable runnable) {
        scheduledExecutorService.scheduleAtFixedRate(runnable, 0, interval, TimeUnit.MILLISECONDS);
    }

    @Override
    public void stop() {
        scheduledExecutorService.shutdown();
        try {
            if (!scheduledExecutorService.awaitTermination(1, TimeUnit.SECONDS)) {
                scheduledExecutorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            LOG.error("Failed to shutdown log flusher: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
        }
    }
}
