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

import com.openai.models.batches.Batch;
import com.openai.models.batches.BatchCreateParams.Endpoint;
import com.openai.models.batches.BatchError;
import org.apache.camel.Message;
import org.apache.camel.util.ObjectHelper;

/**
 * Shared helpers of the batch operations: validating the endpoint a batch targets, and reporting a batch on the message
 * headers.
 */
public final class OpenAIBatchSupport {

    /**
     * The endpoints the SDK knows the Batch API accepts, for the error message of a rejected value. The check itself
     * asks the SDK, so an endpoint added by a newer SDK is accepted even before it is listed here.
     */
    private static final List<Endpoint> KNOWN_ENDPOINTS = List.of(
            Endpoint.V1_RESPONSES, Endpoint.V1_CHAT_COMPLETIONS, Endpoint.V1_EMBEDDINGS, Endpoint.V1_COMPLETIONS,
            Endpoint.V1_MODERATIONS, Endpoint.V1_IMAGES_GENERATIONS, Endpoint.V1_IMAGES_EDITS, Endpoint.V1_VIDEOS);

    private OpenAIBatchSupport() {
    }

    /**
     * Validates the options of the batch operation when the endpoint starts, before a client or an MCP session is
     * created. A batch request runs once and returns a file, so options needing several round-trips cannot be honoured
     * and are refused rather than silently dropped.
     */
    public static void validateConfiguration(OpenAIConfiguration config) {
        if (config.isStreaming()) {
            throw new IllegalArgumentException("The batch operation cannot stream responses; set streaming=false");
        }
        if (config.isConversationMemory()) {
            throw new IllegalArgumentException(
                    "The batch operation runs each request once, so conversationMemory is not supported");
        }
        if (ObjectHelper.isNotEmpty(config.getMcpServer()) || ObjectHelper.isNotEmpty(config.getTags())) {
            throw new IllegalArgumentException(
                    "The batch operation cannot run a tool loop, so mcpServer and tags are not supported");
        }
        if (ObjectHelper.isNotEmpty(config.getBatchEndpoint())) {
            validateEndpoint(config.getBatchEndpoint());
        }
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
        validateEndpoint(endpoint);
        return endpoint;
    }

    private static void validateEndpoint(String endpoint) {
        if (!Endpoint.of(endpoint).isValid()) {
            throw new IllegalArgumentException(
                    "Unsupported batch endpoint: " + endpoint + ". Supported: " + sortedEndpoints());
        }
    }

    /**
     * Reports the batch on the message headers, so a route can poll it and route on its status without reading the SDK
     * object. The endpoint of the batch is deliberately not reported: its header is the per-message override of the
     * batchEndpoint option, and echoing it would make a batch created earlier in the route dictate the endpoint of the
     * next one.
     */
    public static void setBatchHeaders(Message message, Batch batch) {
        message.setHeader(OpenAIConstants.BATCH_ID, batch.id());
        message.setHeader(OpenAIConstants.BATCH_STATUS, batch.status().asString());
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
        return String.join(", ", KNOWN_ENDPOINTS.stream().map(Endpoint::asString).sorted().toList());
    }
}
