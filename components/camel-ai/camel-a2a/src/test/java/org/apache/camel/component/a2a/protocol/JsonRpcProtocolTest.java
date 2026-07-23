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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.component.a2a.A2AConstants;
import org.apache.camel.component.a2a.model.StreamResponse;
import org.apache.camel.component.a2a.model.TaskState;
import org.apache.camel.component.a2a.operation.A2AOperations;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonRpcProtocolTest {

    private final JsonRpcProtocol protocol = new JsonRpcProtocol();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void wrapRequestAddsJsonRpcEnvelope() throws Exception {
        // Given
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("text", "Hello, world!");

        // When
        byte[] wrapped = protocol.wrapJsonRpcRequest("SendMessage", payload);

        // Then
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = mapper.readValue(wrapped, Map.class);
        assertEquals("2.0", envelope.get("jsonrpc"));
        assertEquals("SendMessage", envelope.get("method"));
        assertNotNull(envelope.get("params"));
        assertNotNull(envelope.get("id"));
    }

    @Test
    void unwrapResponseExtractsResult() throws Exception {
        // Given
        String json = "{\"jsonrpc\":\"2.0\",\"result\":{\"id\":\"task-1\"},\"id\":\"req-1\"}";
        byte[] responseBody = json.getBytes();

        // When
        Map<String, Object> result = protocol.unwrapJsonRpcResponse(responseBody);

        // Then
        assertNotNull(result);
        assertEquals("task-1", result.get("id"));
    }

    @Test
    void unwrapResponseDetectsError() {
        // Given
        String json
                = "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32601,\"message\":\"Method not found\"},\"id\":\"req-1\"}";
        byte[] responseBody = json.getBytes();

        // When / Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            protocol.unwrapJsonRpcResponse(responseBody);
        });

        assertTrue(exception.getMessage().contains("JSON-RPC error"));
        assertTrue(exception.getMessage().contains("-32601"));
        assertTrue(exception.getMessage().contains("Method not found"));
    }

    @Test
    void resolvePathReturnsSingleEndpoint() {
        assertEquals("/", protocol.resolvePath(A2AOperations.MESSAGE_SEND, null));
        assertEquals("/", protocol.resolvePath(A2AOperations.TASK_GET, "task-123"));
        assertEquals("/", protocol.resolvePath(A2AOperations.TASK_CANCEL, "task-456"));
    }

    @Test
    void resolveHttpMethodAlwaysPost() {
        assertEquals("POST", protocol.resolveHttpMethod(A2AOperations.MESSAGE_SEND));
        assertEquals("POST", protocol.resolveHttpMethod(A2AOperations.TASK_GET));
    }

    @Test
    void contentTypeIsApplicationJson() {
        assertEquals(A2AConstants.JSONRPC_CONTENT_TYPE, protocol.contentType());
    }

    @Test
    void detectMethodFromRequest() throws Exception {
        // Given
        String json = "{\"jsonrpc\":\"2.0\",\"method\":\"GetTask\",\"params\":{\"id\":\"t1\"},\"id\":\"1\"}";
        byte[] requestBody = json.getBytes();

        // When
        String method = protocol.detectMethod(requestBody);

        // Then
        assertEquals("GetTask", method);
    }

    @Test
    void extractParamsFromRequest() throws Exception {
        // Given
        String json = "{\"jsonrpc\":\"2.0\",\"method\":\"GetTask\",\"params\":{\"id\":\"t1\"},\"id\":\"1\"}";
        byte[] requestBody = json.getBytes();

        // When
        Map<String, Object> params = protocol.extractParams(requestBody);

        // Then
        assertNotNull(params);
        assertEquals("t1", params.get("id"));
    }

    @Test
    void extractIdFromRequest() throws Exception {
        // Given
        String json = "{\"jsonrpc\":\"2.0\",\"method\":\"GetTask\",\"params\":{\"id\":\"t1\"},\"id\":\"request-123\"}";
        byte[] requestBody = json.getBytes();

        // When
        Object id = protocol.extractId(requestBody);

        // Then
        assertEquals("request-123", id);
    }

    @Test
    void wrapJsonRpcResponseCreatesSuccessEnvelope() throws Exception {
        // Given
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "completed");
        result.put("id", "task-999");

        // When
        byte[] wrapped = protocol.wrapJsonRpcResponse(result, "req-abc");

        // Then
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = mapper.readValue(wrapped, Map.class);
        assertEquals("2.0", envelope.get("jsonrpc"));
        assertEquals("req-abc", envelope.get("id"));
        assertNotNull(envelope.get("result"));

        @SuppressWarnings("unchecked")
        Map<String, Object> extractedResult = (Map<String, Object>) envelope.get("result");
        assertEquals("completed", extractedResult.get("status"));
        assertEquals("task-999", extractedResult.get("id"));
    }

    @Test
    void wrapJsonRpcErrorCreatesErrorEnvelope() throws Exception {
        // Given
        int code = -32700;
        String message = "Parse error";
        String requestId = "req-xyz";

        // When
        byte[] wrapped = protocol.wrapJsonRpcError(code, message, requestId);

        // Then
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = mapper.readValue(wrapped, Map.class);
        assertEquals("2.0", envelope.get("jsonrpc"));
        assertEquals("req-xyz", envelope.get("id"));
        assertNotNull(envelope.get("error"));

        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) envelope.get("error");
        assertEquals(-32700, error.get("code"));
        assertEquals("Parse error", error.get("message"));
    }

    @Test
    void wrapJsonRpcErrorIncludesDataWhenPresent() throws Exception {
        Map<String, Object> detail = Map.of("reason", "VERSION_NOT_SUPPORTED");

        byte[] wrapped = protocol.wrapJsonRpcError(-32009, "Unsupported version", "req-version", List.of(detail));

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = mapper.readValue(wrapped, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) envelope.get("error");
        assertNotNull(error.get("data"));
    }

    @Test
    void unwrapRequestExtractsParamsAsType() throws Exception {
        // Given
        String json = "{\"jsonrpc\":\"2.0\",\"method\":\"SendMessage\",\"params\":{\"text\":\"hello\"},\"id\":\"1\"}";
        byte[] requestBody = json.getBytes();

        // When
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) protocol.unwrapRequest(requestBody, Map.class);

        // Then
        assertNotNull(params);
        assertEquals("hello", params.get("text"));
    }

    @Test
    void unwrapResponseConvertsToType() throws Exception {
        // Given
        String json = "{\"jsonrpc\":\"2.0\",\"result\":{\"id\":\"task-1\",\"status\":\"pending\"},\"id\":\"req-1\"}";
        byte[] responseBody = json.getBytes();

        // When
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) protocol.unwrapResponse(responseBody, Map.class);

        // Then
        assertNotNull(result);
        assertEquals("task-1", result.get("id"));
        assertEquals("pending", result.get("status"));
    }

    @Test
    void unwrapStreamingEventExtractsStreamResponse() {
        // Given — JSON-RPC envelope containing a StreamResponse with statusUpdate
        String jsonRpcEvent
                = "{\"jsonrpc\":\"2.0\",\"result\":{\"statusUpdate\":{\"taskId\":\"task-s1\","
                  + "\"status\":{\"state\":\"TASK_STATE_WORKING\"}}},\"id\":\"req-1\"}";

        // When
        StreamResponse response = protocol.unwrapStreamingEvent(jsonRpcEvent);

        // Then
        assertNotNull(response);
        assertNotNull(response.getStatusUpdate());
        assertEquals("task-s1", response.getStatusUpdate().taskId());
        assertEquals(TaskState.WORKING, response.getStatusUpdate().status().state());
    }

    @Test
    void unwrapStreamingEventDetectsError() {
        // Given — JSON-RPC error envelope
        String jsonRpcError
                = "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32603,\"message\":\"Internal error\"},\"id\":\"req-1\"}";

        // When / Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            protocol.unwrapStreamingEvent(jsonRpcError);
        });

        assertTrue(exception.getMessage().contains("JSON-RPC error"));
        assertTrue(exception.getMessage().contains("-32603"));
    }
}
