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
package org.apache.camel.component.openai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.openai.models.batches.Batch;
import com.openai.models.batches.BatchError;
import org.apache.camel.Message;
import org.apache.camel.util.ObjectHelper;

/**
 * Shared helpers of the batch operations: validating the endpoint a batch targets, and reporting a batch on the message
 * headers.
 */
public final class OpenAIBatchSupport {

    /**
     * The endpoints the Batch API accepts, as listed by the OpenAI API specification.
     */
    public static final Set<String> SUPPORTED_ENDPOINTS = Set.of(
            "/v1/responses", "/v1/chat/completions", "/v1/embeddings", "/v1/completions",
            "/v1/moderations", "/v1/images/generations", "/v1/images/edits", "/v1/videos");

    /**
     * Statuses in which a batch has stopped processing, so its result files are final.
     */
    private static final Set<String> FINAL_STATUSES = Set.of("completed", "expired", "cancelled");

    private OpenAIBatchSupport() {
    }

    /**
     * Resolves the endpoint of a batch from the header or the endpoint option, and validates it before anything is
     * uploaded, so an unsupported value does not leave an orphan input file behind.
     */
    public static String resolveEndpoint(Message in, OpenAIConfiguration config) {
        String endpoint = in.getHeader(OpenAIConstants.BATCH_ENDPOINT, config.getBatchEndpoint(), String.class);
        if (ObjectHelper.isEmpty(endpoint)) {
            throw new IllegalArgumentException(
                    "The endpoint of the batch must be set with the batchEndpoint option or the "
                                               + OpenAIConstants.BATCH_ENDPOINT + " header. Supported: "
                                               + sortedEndpoints());
        }
        if (!SUPPORTED_ENDPOINTS.contains(endpoint)) {
            throw new IllegalArgumentException(
                    "Unsupported batch endpoint: " + endpoint + ". Supported: " + sortedEndpoints());
        }
        return endpoint;
    }

    public static boolean isFinal(Batch batch) {
        return FINAL_STATUSES.contains(batch.status().asString());
    }

    /**
     * Reports the batch on the message headers, so a route can poll it and route on its status without reading the SDK
     * object.
     */
    public static void setBatchHeaders(Message message, Batch batch) {
        message.setHeader(OpenAIConstants.BATCH_ID, batch.id());
        message.setHeader(OpenAIConstants.BATCH_STATUS, batch.status().asString());
        message.setHeader(OpenAIConstants.BATCH_ENDPOINT, batch.endpoint());
        message.setHeader(OpenAIConstants.BATCH_INPUT_FILE_ID, batch.inputFileId());
        batch.outputFileId().ifPresent(id -> message.setHeader(OpenAIConstants.BATCH_OUTPUT_FILE_ID, id));
        batch.errorFileId().ifPresent(id -> message.setHeader(OpenAIConstants.BATCH_ERROR_FILE_ID, id));
        batch.requestCounts().ifPresent(counts -> {
            message.setHeader(OpenAIConstants.BATCH_REQUEST_COUNT_TOTAL, counts.total());
            message.setHeader(OpenAIConstants.BATCH_REQUEST_COUNT_COMPLETED, counts.completed());
            message.setHeader(OpenAIConstants.BATCH_REQUEST_COUNT_FAILED, counts.failed());
        });
        batch.usage().ifPresent(usage -> {
            message.setHeader(OpenAIConstants.PROMPT_TOKENS, usage.inputTokens());
            message.setHeader(OpenAIConstants.COMPLETION_TOKENS, usage.outputTokens());
            message.setHeader(OpenAIConstants.TOTAL_TOKENS, usage.totalTokens());
        });
        List<Map<String, Object>> errors = errors(batch);
        if (!errors.isEmpty()) {
            message.setHeader(OpenAIConstants.BATCH_ERRORS, errors);
        }
    }

    /**
     * The errors that made a batch fail validation, as maps of the API fields.
     */
    public static List<Map<String, Object>> errors(Batch batch) {
        List<Map<String, Object>> errors = new ArrayList<>();
        batch.errors().flatMap(Batch.Errors::data).ifPresent(data -> {
            for (BatchError error : data) {
                Map<String, Object> entry = new LinkedHashMap<>();
                error.code().ifPresent(value -> entry.put("code", value));
                error.message().ifPresent(value -> entry.put("message", value));
                error.param().ifPresent(value -> entry.put("param", value));
                error.line().ifPresent(value -> entry.put("line", value));
                errors.add(entry);
            }
        });
        return errors;
    }

    private static String sortedEndpoints() {
        return String.join(", ", SUPPORTED_ENDPOINTS.stream().sorted().toList());
    }
}
