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

    @Metadata(label = "producer",
              description = "The operation that the producer performs, in place of the operation option: the enum"
                            + " name, such as TASK_GET, or the A2A method name, such as GetTask. For an unknown value,"
                            + " the producer logs a warning and uses the operation option.",
              javaType = "String")
    public static final String OPERATION = "CamelA2AOperation";
    @Metadata(label = "consumer",
              description = "The A2A method of the request that the consumer passes to the route: SendMessage or"
                            + " SendStreamingMessage, as the consumer answers the other methods itself. Also set as an"
                            + " exchange property of the same name.",
              javaType = "String")
    public static final String REQUEST_OPERATION = "CamelA2ARequestOperation";
    @Metadata(label = "consumer",
              description = "The id of the task that the consumer created or continues for the request. The reply of"
                            + " the consumer uses the exchange property of the same name, not this header.",
              javaType = "String")
    public static final String REQUEST_TASK_ID = "CamelA2ARequestTaskId";
    @Metadata(label = "consumer",
              description = "The context id of the request: the contextId of the message, else that of the task that"
                            + " it continues, else a new id. The reply of the consumer uses the exchange property of"
                            + " the same name, not this header.",
              javaType = "String")
    public static final String REQUEST_CONTEXT_ID = "CamelA2ARequestContextId";
    @Metadata(label = "consumer",
              description = "The state of the task in the reply of the consumer to SendMessage, set by the route, for"
                            + " example TASK_STATE_INPUT_REQUIRED. If not set, the task is TASK_STATE_COMPLETED, and"
                            + " an unknown value gives TASK_STATE_UNSPECIFIED. Not used for SendStreamingMessage,"
                            + " whose task ends as TASK_STATE_COMPLETED.",
              javaType = "String")
    public static final String REPLY_TASK_STATE = "CamelA2AReplyTaskState";
    @Metadata(label = "consumer",
              description = "The kind of reply of the consumer to SendMessage, set by the route: task, the default,"
                            + " for a task that holds the reply message, or message for the reply message alone. The"
                            + " consumer does not keep a task that it created for a request answered with a message.",
              javaType = "String")
    public static final String REPLY_TYPE = "CamelA2AReplyType";
    // Exchange property only; @Metadata would incorrectly advertise it as a message header.
    public static final String REQUEST_MESSAGE = "CamelA2ARequestMessage";
    @Metadata(label = "producer",
              description = "The id of the task for the producer operations TASK_GET, TASK_CANCEL, TASK_SUBSCRIBE and"
                            + " the PUSH_CONFIG operations. The producer sets it to the id of the task of the response"
                            + " of MESSAGE_SEND, TASK_GET and TASK_CANCEL. MESSAGE_SEND and MESSAGE_STREAM do not read"
                            + " it: to continue a task, send a Message whose taskId is set.",
              javaType = "String")
    public static final String TASK_ID = "CamelA2ATaskId";
    @Metadata(label = "common",
              description = "The id of the push notification config for the producer operations PUSH_CONFIG_GET and"
                            + " PUSH_CONFIG_DELETE, which the producer sets from the response of PUSH_CONFIG_CREATE"
                            + " and PUSH_CONFIG_GET. With protocolBinding HTTP+JSON, these two operations do not read"
                            + " it, and take the task id and the config id from the CamelA2ATaskId header written as"
                            + " taskId:configId.",
              javaType = "String")
    public static final String PUSH_CONFIG_ID = "CamelA2APushConfigId";
    @Metadata(label = "producer",
              description = "The context id that the producer sets on the message that it sends with MESSAGE_SEND or"
                            + " MESSAGE_STREAM, unless the body is a SendMessageRequest. The producer sets it to the"
                            + " context id of the task of the response of MESSAGE_SEND, TASK_GET and TASK_CANCEL.",
              javaType = "String")
    public static final String CONTEXT_ID = "CamelA2AContextId";
    @Metadata(label = "common",
              description = "The id of a message. The consumer sets it to the messageId of the message of the request."
                            + " The producer sets it to the messageId of a response that is a message, not a task.",
              javaType = "String")
    public static final String MESSAGE_ID = "CamelA2AMessageId";
    @Metadata(label = "producer",
              description = "The state of the task of the response of the producer, for MESSAGE_SEND, TASK_GET and"
                            + " TASK_CANCEL, for example TASK_STATE_COMPLETED or TASK_STATE_WORKING.",
              javaType = "String")
    public static final String TASK_STATE = "CamelA2ATaskState";
    @Metadata(label = "producer",
              description = "The A2A method that the producer called, for example SendMessage or GetTask.",
              javaType = "String")
    public static final String METHOD = "CamelA2AMethod";
    @Metadata(label = "producer",
              description = "The kind of response that the producer received: task, message, taskList, pushConfigList"
                            + " or stream. Not set for PUSH_CONFIG_CREATE, PUSH_CONFIG_GET and PUSH_CONFIG_DELETE, nor"
                            + " with dataFormat RAW. The CamelA2AReplyType header sets the kind of reply of the"
                            + " consumer instead.",
              javaType = "String")
    public static final String RESPONSE_TYPE = "CamelA2AResponseType";
    @Metadata(label = "common",
              description = "Not used: the consumer looks for it in the HTTP request after it removed every header"
                            + " whose name starts with Camel, so the configuration of the request and the"
                            + " returnImmediately option apply. The producer does not send it.",
              javaType = "Boolean")
    public static final String RETURN_IMMEDIATELY = "CamelA2AReturnImmediately";
    @Metadata(label = "common",
              description = "The maximum number of the latest messages of the task history that the remote agent"
                            + " returns, for the producer operations TASK_GET and TASK_SUBSCRIBE.",
              javaType = "Integer")
    public static final String HISTORY_LENGTH = "CamelA2AHistoryLength";
    @Metadata(label = "consumer",
              description = "The emitter of the server-sent event stream of a SendStreamingMessage request, which the"
                            + " route can use to send status updates, artifacts and messages of the task to the"
                            + " client. Only set for SendStreamingMessage.",
              javaType = "org.apache.camel.component.a2a.streaming.A2AStreamEmitter")
    public static final String STREAM_EMITTER = "CamelA2AStreamEmitter";
    public static final String USER_PROFILE = "CamelA2AUserProfile";
    public static final String OAUTH_TOKEN_VALIDATION_RESULT = "CamelOAuthTokenValidationResult";
    public static final String RESPONSE_TASK = "CamelA2AResponseTask";
    @Metadata(label = "common",
              description = "Only list the tasks of this context id, for the producer operation TASK_LIST.",
              javaType = "String")
    public static final String LIST_CONTEXT_ID = "CamelA2AListContextId";
    @Metadata(label = "common",
              description = "The maximum number of tasks of the page that the remote agent returns, for the producer"
                            + " operation TASK_LIST.",
              javaType = "Integer")
    public static final String LIST_PAGE_SIZE = "CamelA2AListPageSize";
    @Metadata(label = "common",
              description = "The token of the page of tasks to return, for the producer operation TASK_LIST: the"
                            + " nextPageToken of the previous response, which only dataFormat RAW gives.",
              javaType = "String")
    public static final String LIST_PAGE_TOKEN = "CamelA2AListPageToken";
    @Metadata(label = "common",
              description = "Whether the remote agent includes the artifacts of the tasks, for the producer operation"
                            + " TASK_LIST.",
              javaType = "Boolean")
    public static final String LIST_INCLUDE_ARTIFACTS = "CamelA2AListIncludeArtifacts";
    @Metadata(label = "common",
              description = "The maximum number of the latest messages of the history of each task that the remote"
                            + " agent returns, for the producer operation TASK_LIST.",
              javaType = "Integer")
    public static final String LIST_HISTORY_LENGTH = "CamelA2AListHistoryLength";
    @Metadata(label = "common",
              description = "Only list the tasks whose status changed after this time, an ISO-8601 date-time with an"
                            + " offset, for the producer operation TASK_LIST.",
              javaType = "String")
    public static final String LIST_STATUS_TIMESTAMP_AFTER = "CamelA2AListStatusTimestampAfter";
    @Metadata(label = "common",
              description = "Only list the tasks in these states, a comma-separated list such as"
                            + " TASK_STATE_WORKING,TASK_STATE_COMPLETED, for the producer operation TASK_LIST.",
              javaType = "String")
    public static final String LIST_STATUS = "CamelA2AListStatus";
    @Metadata(label = "consumer",
              description = "The URIs of the A2A extensions that the client requested in the A2A-Extensions HTTP"
                            + " header and that the agent card of the consumer declares. Also set as an exchange"
                            + " property of the same name.",
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
