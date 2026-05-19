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
package org.apache.camel.component.a2a.protocol;

import org.apache.camel.component.a2a.A2AConstants;
import org.apache.camel.component.a2a.operation.A2AOperations;

/**
 * Closed component strategy for supported A2A protocol bindings. Implementations handle the wire protocol details,
 * including path resolution, HTTP method selection, and request/response serialization.
 * <p>
 * This is not a third-party extension SPI. The interface is sealed intentionally; select one of the supported bindings
 * with the endpoint {@code protocolBinding} option.
 */
public sealed interface A2AProtocol permits RestProtocol, JsonRpcProtocol {

    /**
     * Resolves the HTTP path for a given A2A operation.
     *
     * @param  operation  the A2A operation
     * @param  resourceId the resource identifier (e.g., task ID), may be null for operations that don't require it
     * @return            the HTTP path (e.g., "/message:send", "/tasks/task-123")
     */
    String resolvePath(A2AOperations operation, String resourceId);

    /**
     * Resolves the HTTP method for a given A2A operation.
     *
     * @param  operation the A2A operation
     * @return           the HTTP method (GET, POST, DELETE)
     */
    String resolveHttpMethod(A2AOperations operation);

    /**
     * Wraps a request payload into its wire format (e.g., JSON bytes). Delegates to
     * {@link #wrapRequest(String, Object)} with a {@code null} method name.
     *
     * @param  payload the payload to serialize
     * @return         the serialized payload as bytes
     */
    default byte[] wrapRequest(Object payload) {
        return wrapRequest(null, payload);
    }

    /**
     * Wraps a request payload with protocol-specific context. For REST, the method name is ignored. For JSON-RPC, the
     * method name is included in the envelope.
     *
     * @param  methodName the A2A method name (e.g., "SendMessage"), may be null
     * @param  payload    the payload to serialize
     * @return            the serialized payload as bytes
     */
    byte[] wrapRequest(String methodName, Object payload);

    /**
     * Unwraps a response from its wire format into a Java object.
     *
     * @param  responseBody the serialized response bytes
     * @param  responseType the expected response type
     * @return              the deserialized response object
     */
    Object unwrapResponse(byte[] responseBody, Class<?> responseType);

    /**
     * Returns the media type produced by this binding for non-streaming A2A JSON responses.
     *
     * @return the response content type
     */
    default String contentType() {
        return A2AConstants.CONTENT_TYPE;
    }

    /**
     * Whether the operation returns a server-sent event stream instead of a single JSON response.
     *
     * @param  operation the A2A operation
     * @return           true for streaming operations
     */
    default boolean isStreamingOperation(A2AOperations operation) {
        return operation == A2AOperations.MESSAGE_STREAM || operation == A2AOperations.TASK_SUBSCRIBE;
    }

    /**
     * Wraps a response payload into its wire format (e.g., JSON bytes). Delegates to
     * {@link #wrapResponse(Object, String)} with a {@code null} request ID.
     *
     * @param  payload the payload to serialize
     * @return         the serialized payload as bytes
     */
    default byte[] wrapResponse(Object payload) {
        return wrapResponse(payload, null);
    }

    /**
     * Wraps a response payload with protocol-specific context. For REST, the request ID is ignored. For JSON-RPC, the
     * request ID is included in the envelope.
     *
     * @param  payload   the payload to serialize
     * @param  requestId the original request ID for correlation, may be null
     * @return           the serialized payload as bytes
     */
    byte[] wrapResponse(Object payload, Object requestId);

    /**
     * Unwraps a request from its wire format into a Java object.
     *
     * @param  requestBody the serialized request bytes
     * @param  requestType the expected request type
     * @return             the deserialized request object
     */
    Object unwrapRequest(byte[] requestBody, Class<?> requestType);
}
