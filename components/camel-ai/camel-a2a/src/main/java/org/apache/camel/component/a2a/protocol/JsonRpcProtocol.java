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

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.a2a.A2AConstants;
import org.apache.camel.component.a2a.model.StreamResponse;
import org.apache.camel.component.a2a.operation.A2AOperations;
import org.apache.camel.component.a2a.util.A2AJsonMapper;

/**
 * JSON-RPC 2.0 implementation of the A2A protocol. All operations are routed through a single POST endpoint at "/".
 * Method names are PascalCase per A2A v1.0 specification (e.g., "SendMessage", "GetTask"). Handles JSON-RPC envelope
 * wrapping/unwrapping and error handling. This class is a built-in binding implementation, not a user extension point.
 */
public final class JsonRpcProtocol implements A2AProtocol {

    public static final String VERSION = "2.0";

    // JSON-RPC 2.0 spec error codes (section 5.1)
    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int INTERNAL_ERROR = -32603;

    private static final ObjectMapper OBJECT_MAPPER = A2AJsonMapper.instance();

    @Override
    public String contentType() {
        return A2AConstants.JSONRPC_CONTENT_TYPE;
    }

    @Override
    public String resolvePath(A2AOperations operation, String resourceId) {
        // All JSON-RPC operations go through a single endpoint
        return "/";
    }

    @Override
    public String resolveHttpMethod(A2AOperations operation) {
        // All JSON-RPC operations use POST
        return "POST";
    }

    @Override
    public byte[] wrapRequest(String methodName, Object payload) {
        return wrapJsonRpcRequest(methodName, payload);
    }

    /**
     * Wraps a request payload into a JSON-RPC 2.0 envelope.
     *
     * @param  method the JSON-RPC method name (e.g., "SendMessage")
     * @param  params the method parameters (typically a Map or POJO)
     * @return        the serialized JSON-RPC request as bytes
     */
    public byte[] wrapJsonRpcRequest(String method, Object params) {
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("jsonrpc", VERSION);
            envelope.put("method", method);
            envelope.put("params", params);
            envelope.put("id", UUID.randomUUID().toString());
            return OBJECT_MAPPER.writeValueAsBytes(envelope);
        } catch (Exception e) {
            throw new RuntimeCamelException("Failed to serialize JSON-RPC request", e);
        }
    }

    /**
     * Unwraps a JSON-RPC 2.0 response envelope and extracts the result.
     *
     * @param  responseBody the serialized JSON-RPC response bytes
     * @return              the result payload as a Map
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> unwrapJsonRpcResponse(byte[] responseBody) {
        try {
            Map<String, Object> envelope = OBJECT_MAPPER.readValue(responseBody, Map.class);

            // Check for error
            if (envelope.containsKey("error")) {
                Map<String, Object> error = (Map<String, Object>) envelope.get("error");
                int code = (Integer) error.get("code");
                String message = (String) error.get("message");
                throw new RuntimeCamelException("JSON-RPC error: code=" + code + ", message=" + message);
            }

            // Extract result
            return (Map<String, Object>) envelope.get("result");
        } catch (RuntimeCamelException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeCamelException("Failed to deserialize JSON-RPC response", e);
        }
    }

    @Override
    public Object unwrapResponse(byte[] responseBody, Class<?> responseType) {
        Map<String, Object> result = unwrapJsonRpcResponse(responseBody);
        return OBJECT_MAPPER.convertValue(result, responseType);
    }

    /**
     * Wraps a response payload into a JSON-RPC 2.0 success envelope.
     *
     * @param  result    the result payload to wrap
     * @param  requestId the request ID from the original JSON-RPC request
     * @return           the serialized JSON-RPC response as bytes
     */
    public byte[] wrapJsonRpcResponse(Object result, Object requestId) {
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("jsonrpc", VERSION);
            envelope.put("result", result);
            envelope.put("id", requestId);
            return OBJECT_MAPPER.writeValueAsBytes(envelope);
        } catch (Exception e) {
            throw new RuntimeCamelException("Failed to serialize JSON-RPC response", e);
        }
    }

    /**
     * Unwraps a single SSE event's data from a JSON-RPC 2.0 response envelope, extracting the result as a
     * {@link StreamResponse}. Used by the producer to decode streaming events when using JSON-RPC protocol binding.
     *
     * @param  eventData the SSE data line content (a JSON-RPC response envelope)
     * @return           the decoded streaming event
     */
    public StreamResponse unwrapStreamingEvent(String eventData) {
        try {
            byte[] bytes = eventData.getBytes(StandardCharsets.UTF_8);
            Map<String, Object> result = unwrapJsonRpcResponse(bytes);
            StreamResponse response = OBJECT_MAPPER.convertValue(result, StreamResponse.class);
            response.validate();
            return response;
        } catch (RuntimeCamelException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeCamelException("Failed to unwrap JSON-RPC streaming event", e);
        }
    }

    /**
     * Wraps an error into a JSON-RPC 2.0 error envelope.
     *
     * @param  code      the JSON-RPC error code (e.g., -32601 for Method not found)
     * @param  message   the error message
     * @param  requestId the request ID from the original JSON-RPC request
     * @return           the serialized JSON-RPC error response as bytes
     */
    public byte[] wrapJsonRpcError(int code, String message, Object requestId) {
        return wrapJsonRpcError(code, message, requestId, null);
    }

    public byte[] wrapJsonRpcError(int code, String message, Object requestId, Object data) {
        try {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("code", code);
            error.put("message", message);
            if (data != null) {
                error.put("data", data);
            }

            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("jsonrpc", VERSION);
            envelope.put("error", error);
            envelope.put("id", requestId);
            return OBJECT_MAPPER.writeValueAsBytes(envelope);
        } catch (Exception e) {
            throw new RuntimeCamelException("Failed to serialize JSON-RPC error", e);
        }
    }

    @Override
    public byte[] wrapResponse(Object payload, Object requestId) {
        return wrapJsonRpcResponse(payload, requestId);
    }

    /**
     * Parses the JSON-RPC envelope from a request body into a Map. This method performs a single parse that can be
     * reused to extract method, params, and id without re-parsing the same bytes.
     *
     * @param  requestBody the serialized JSON-RPC request bytes
     * @return             the parsed envelope as a Map
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> parseEnvelope(byte[] requestBody) {
        try {
            return OBJECT_MAPPER.readValue(requestBody, Map.class);
        } catch (Exception e) {
            throw new RuntimeCamelException("Failed to parse JSON-RPC envelope", e);
        }
    }

    /**
     * Detects the JSON-RPC method name from a request envelope.
     *
     * @param  requestBody the serialized JSON-RPC request bytes
     * @return             the method name (e.g., "SendMessage")
     */
    public String detectMethod(byte[] requestBody) {
        return detectMethod(parseEnvelope(requestBody));
    }

    public String detectMethod(Map<String, Object> envelope) {
        Object method = envelope.get("method");
        return method instanceof String text ? text : null;
    }

    /**
     * Extracts the params field from a JSON-RPC request envelope.
     *
     * @param  requestBody the serialized JSON-RPC request bytes
     * @return             the params payload as a Map
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> extractParams(byte[] requestBody) {
        return extractParams(parseEnvelope(requestBody));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> extractParams(Map<String, Object> envelope) {
        return (Map<String, Object>) envelope.get("params");
    }

    /**
     * Extracts the id field from a JSON-RPC request envelope.
     *
     * @param  requestBody the serialized JSON-RPC request bytes
     * @return             the request ID, preserving its original JSON type (String or Number)
     */
    public Object extractId(byte[] requestBody) {
        return extractId(parseEnvelope(requestBody));
    }

    public Object extractId(Map<String, Object> envelope) {
        return envelope.get("id");
    }

    @Override
    public Object unwrapRequest(byte[] requestBody, Class<?> requestType) {
        Map<String, Object> params = extractParams(requestBody);
        return OBJECT_MAPPER.convertValue(params, requestType);
    }
}
