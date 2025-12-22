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
package org.apache.camel.component.file.cluster;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes cluster data read / write tasks asynchronously, with timeouts to guard against potential unpredictable
 * blocking I/O periods.
 */
class FileLockClusterTaskExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileLockClusterTaskExecutor.class);
    private final FileLockClusterService service;

    FileLockClusterTaskExecutor(FileLockClusterService service) {
        Objects.requireNonNull(service, "FileLockClusterService cannot be null");
        this.service = service;
    }

    /**
     * If the cluster data root is network based, like an NFS mount, avoid potential long blocking I/O to fail fast and
     * reliably reason about the cluster state.
     *
     * @param task Supplier representing a task to run
     */
    <T> T run(Supplier<T> task) throws ExecutionException, TimeoutException {
        Objects.requireNonNull(task, "Task cannot be null");

        int maxAttempts = service.getClusterDataTaskMaxAttempts();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            LOGGER.debug("Running cluster task attempt {} of {}", attempt, maxAttempts);

            CompletableFuture<T> future = CompletableFuture.supplyAsync(task, service.getClusterDataTaskExecutor());
            try {
                return future.get(service.getClusterDataTaskTimeout(), service.getClusterDataTaskTimeoutUnit());
            } catch (InterruptedException e) {
                LOGGER.trace("Cluster task interrupted on attempt {} of {}", attempt, maxAttempts);
                future.cancel(true);
                Thread.currentThread().interrupt();
                return null;
            } catch (ExecutionException | TimeoutException e) {
                LOGGER.debug("Cluster task encountered an exception on attempt {} of {}", attempt, maxAttempts, e);
                future.cancel(true);
                if (attempt == maxAttempts) {
                    LOGGER.debug("Cluster task retry limit ({}) reached", maxAttempts, e);
                    throw e;
                }
            } finally {
                LOGGER.debug("Cluster task attempt {} ended", attempt);
            }
        }
        return null;
    }
}
