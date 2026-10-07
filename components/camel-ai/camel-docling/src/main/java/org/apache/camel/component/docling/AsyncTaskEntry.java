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

import java.util.concurrent.CompletableFuture;

import ai.docling.serve.api.convert.response.ConvertDocumentResponse;

/**
 * Wrapper for async conversion tasks that tracks creation timestamp for TTL-based cleanup.
 */
final class AsyncTaskEntry {

    private final String taskId;
    private final CompletableFuture<ConvertDocumentResponse> future;
    private final String outputFormat;
    private final long createdAtMs;

    AsyncTaskEntry(String taskId, CompletableFuture<ConvertDocumentResponse> future, String outputFormat) {
        this.taskId = taskId;
        this.future = future;
        this.outputFormat = outputFormat;
        this.createdAtMs = System.currentTimeMillis();
    }

    public String getTaskId() {
        return taskId;
    }

    public CompletableFuture<ConvertDocumentResponse> getFuture() {
        return future;
    }

    /**
     * The output format the task was submitted with (resolved from the {@code CamelDoclingOutputFormat} header or the
     * producer endpoint's {@code outputFormat} at submit time). The content must be extracted with this format — not
     * the consuming endpoint's — because docling-serve only returns the format that was requested.
     */
    public String getOutputFormat() {
        return outputFormat;
    }

    public long getCreatedAtMs() {
        return createdAtMs;
    }

    public long getAgeMs() {
        return System.currentTimeMillis() - createdAtMs;
    }

}
