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
package org.apache.camel.component.a2a.operation;

import org.apache.camel.Exchange;
import org.apache.camel.component.a2a.A2AConstants;
import org.apache.camel.component.a2a.A2ADataFormat;
import org.apache.camel.component.a2a.model.Task;

/**
 * Internal contract for built-in A2A operation implementations. Each operation knows how to build requests, parse
 * responses, parse incoming requests, and build outgoing responses for its specific A2A method.
 * <p>
 * This is not a third-party operation extension SPI. User-level operation selection is exposed through
 * {@link A2AOperations} and the endpoint/header options.
 */
public interface A2AOperation {

    /**
     * Returns the operation type this implementation handles.
     */
    A2AOperations getOperationType();

    /**
     * Returns the JSON-RPC method name for this operation (e.g., "SendMessage").
     */
    default String methodName() {
        return getOperationType().getMethodName();
    }

    /**
     * Returns true if this operation uses streaming (SSE) responses.
     */
    default boolean isStreaming() {
        return false;
    }

    /**
     * Returns true if this operation is infrastructure-only and should not be exposed as a user-invocable endpoint
     * operation.
     */
    default boolean isInfrastructureOnly() {
        return false;
    }

    /**
     * Builds an A2A protocol request object from the exchange (for producers sending requests to remote agents).
     *
     * @param  exchange the Camel exchange
     * @return          the protocol request object (e.g., SendMessageRequest)
     */
    default Object buildRequest(Exchange exchange) {
        return exchange.getMessage().getBody();
    }

    /**
     * Parses an A2A protocol response object and updates the exchange (for producers receiving responses from remote
     * agents).
     *
     * @param exchange the Camel exchange
     * @param response the protocol response object (e.g., SendMessageResponse)
     */
    default void parseResponse(Exchange exchange, Object response) {
        exchange.getMessage().setBody(response);
    }

    /**
     * Parses an incoming A2A protocol request object and updates the exchange (for consumers receiving requests from
     * remote agents).
     *
     * @param exchange the Camel exchange
     * @param request  the protocol request object (e.g., SendMessageRequest)
     */
    default void parseRequest(Exchange exchange, Object request) {
        exchange.getMessage().setBody(request);
        exchange.getMessage().setHeader(A2AConstants.OPERATION, methodName());
    }

    /**
     * Parses an incoming A2A protocol request with dataFormat control. Subclasses override this to apply different body
     * formats (PAYLOAD, POJO, RAW) based on the dataFormat parameter.
     *
     * @param exchange   the Camel exchange
     * @param request    the protocol request object
     * @param dataFormat the data format to use
     */
    default void parseRequest(Exchange exchange, Object request, A2ADataFormat dataFormat) {
        parseRequest(exchange, request);
    }

    /**
     * Builds an A2A protocol response object from the exchange (for consumers sending responses to remote agents).
     *
     * @param  exchange the Camel exchange
     * @return          the protocol response object (e.g., SendMessageResponse)
     */
    default Object buildResponse(Exchange exchange) {
        return exchange.getMessage().getBody();
    }

    /**
     * Helper to set common task-related headers on the exchange. Centralizes the response-parsing logic shared by
     * task-returning operations (GetTask, CancelTask, etc.).
     *
     * @param exchange the Camel exchange
     * @param task     the task to apply headers from
     */
    default void applyTaskHeaders(Exchange exchange, Task task) {
        exchange.getMessage().setBody(task);
        exchange.getMessage().setHeader(A2AConstants.TASK_ID, task.id());
        if (task.contextId() != null) {
            exchange.getMessage().setHeader(A2AConstants.CONTEXT_ID, task.contextId());
        }
        if (task.status() != null && task.status().state() != null) {
            exchange.getMessage().setHeader(A2AConstants.TASK_STATE,
                    task.status().state().getProtoName());
        }
        exchange.getMessage().setHeader(A2AConstants.RESPONSE_TYPE, "task");
    }
}
