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
package org.apache.camel.component.a2a;

import org.apache.camel.spi.Metadata;

/**
 * Constants for the A2A component.
 */
public final class A2AConstants {

    @Metadata(label = "producer", description = "A2A operation to invoke", javaType = "String")
    public static final String OPERATION = "CamelA2AOperation";
    @Metadata(label = "common", description = "Task ID", javaType = "String")
    public static final String TASK_ID = "CamelA2ATaskId";
    @Metadata(label = "common", description = "Push notification config ID", javaType = "String")
    public static final String PUSH_CONFIG_ID = "CamelA2APushConfigId";
    @Metadata(label = "common", description = "Context ID for multi-turn conversations", javaType = "String")
    public static final String CONTEXT_ID = "CamelA2AContextId";
    @Metadata(label = "common", description = "Message ID", javaType = "String")
    public static final String MESSAGE_ID = "CamelA2AMessageId";
    @Metadata(label = "common", description = "Task state", javaType = "String")
    public static final String TASK_STATE = "CamelA2ATaskState";
    @Metadata(label = "producer", description = "A2A method name invoked", javaType = "String")
    public static final String METHOD = "CamelA2AMethod";
    @Metadata(label = "common", description = "Response type: task or message", javaType = "String")
    public static final String RESPONSE_TYPE = "CamelA2AResponseType";
    @Metadata(label = "common", description = "Return immediately flag", javaType = "Boolean")
    public static final String RETURN_IMMEDIATELY = "CamelA2AReturnImmediately";
    @Metadata(label = "common", description = "Max history messages", javaType = "Integer")
    public static final String HISTORY_LENGTH = "CamelA2AHistoryLength";
    @Metadata(label = "consumer", description = "SSE stream emitter for route processors",
              javaType = "org.apache.camel.component.a2a.streaming.A2AStreamEmitter")
    public static final String STREAM_EMITTER = "CamelA2AStreamEmitter";
    public static final String USER_PROFILE = "CamelA2AUserProfile";
    public static final String RESPONSE_TASK = "CamelA2AResponseTask";
    @Metadata(label = "common", description = "Context ID filter for task listing", javaType = "String")
    public static final String LIST_CONTEXT_ID = "CamelA2AListContextId";
    @Metadata(label = "common", description = "Page size for task listing", javaType = "Integer")
    public static final String LIST_PAGE_SIZE = "CamelA2AListPageSize";
    @Metadata(label = "common", description = "Page token for task listing pagination", javaType = "String")
    public static final String LIST_PAGE_TOKEN = "CamelA2AListPageToken";
    @Metadata(label = "common", description = "Whether to include artifacts in task listing", javaType = "Boolean")
    public static final String LIST_INCLUDE_ARTIFACTS = "CamelA2AListIncludeArtifacts";
    @Metadata(label = "common", description = "History length for task listing", javaType = "Integer")
    public static final String LIST_HISTORY_LENGTH = "CamelA2AListHistoryLength";
    @Metadata(label = "common", description = "Filter tasks by status timestamp after this value", javaType = "String")
    public static final String LIST_STATUS_TIMESTAMP_AFTER = "CamelA2AListStatusTimestampAfter";
    @Metadata(label = "common", description = "Comma-separated status filter for task listing", javaType = "String")
    public static final String LIST_STATUS = "CamelA2AListStatus";
    @Metadata(label = "consumer", description = "Negotiated A2A extension URIs requested by the client",
              javaType = "java.util.List")
    public static final String EXTENSIONS = "CamelA2AExtensions";
    public static final String EXTENSION_DECLARATIONS = "CamelA2AExtensionDeclarations";

    public static final String SCHEME = "a2a";
    public static final String A2A_VERSION = "1.0";
    public static final String CONTENT_TYPE = "application/a2a+json";
    public static final String JSONRPC_CONTENT_TYPE = "application/json";
    public static final String SSE_CONTENT_TYPE = "text/event-stream";
    public static final String WELL_KNOWN_PATH = "/.well-known/agent-card.json";
    public static final String HEADER_A2A_VERSION = "A2A-Version";
    public static final String HEADER_A2A_EXTENSIONS = "A2A-Extensions";

    public static final String PROTOCOL_REST = "HTTP+JSON";
    public static final String PROTOCOL_JSONRPC = "JSONRPC";
    public static final String PROTOCOL_REST_ALIAS = "rest";
    public static final String PROTOCOL_JSONRPC_ALIAS = "jsonrpc";

    private A2AConstants() {
        // utility class
    }
}
