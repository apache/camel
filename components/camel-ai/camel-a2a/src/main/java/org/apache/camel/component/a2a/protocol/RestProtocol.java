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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.a2a.operation.A2AOperations;
import org.apache.camel.component.a2a.util.A2AJsonMapper;

/**
 * RESTful HTTP implementation of the A2A protocol. Maps A2A operations to HTTP methods and paths using the A2A v1.0
 * colon notation (e.g., /message:send, /tasks/{id}:cancel). Handles JSON serialization/deserialization via Jackson.
 * This class is a built-in binding implementation, not a user extension point.
 */
public final class RestProtocol implements A2AProtocol {

    private static final ObjectMapper OBJECT_MAPPER = A2AJsonMapper.instance();

    @Override
    public String resolvePath(A2AOperations operation, String resourceId) {
        return switch (operation) {
            case MESSAGE_SEND -> "/message:send";
            case MESSAGE_STREAM -> "/message:stream";
            case TASK_GET -> "/tasks/" + resourceId;
            case TASK_LIST -> "/tasks";
            case TASK_CANCEL -> "/tasks/" + resourceId + ":cancel";
            case TASK_SUBSCRIBE -> "/tasks/" + resourceId + ":subscribe";
            case PUSH_CONFIG_CREATE -> "/tasks/" + resourceId + "/pushNotificationConfigs";
            case PUSH_CONFIG_LIST -> "/tasks/" + resourceId + "/pushNotificationConfigs";
            case PUSH_CONFIG_GET, PUSH_CONFIG_DELETE -> {
                // resourceId format: "taskId:configId" for GET/DELETE operations
                if (resourceId != null && resourceId.contains(":")) {
                    String[] parts = resourceId.split(":", 2);
                    yield "/tasks/" + parts[0] + "/pushNotificationConfigs/" + parts[1];
                }
                yield "/tasks/" + resourceId + "/pushNotificationConfigs";
            }
        };
    }

    @Override
    public String resolveHttpMethod(A2AOperations operation) {
        return switch (operation) {
            case MESSAGE_SEND, MESSAGE_STREAM, TASK_CANCEL, TASK_SUBSCRIBE, PUSH_CONFIG_CREATE -> "POST";
            case TASK_GET, TASK_LIST, PUSH_CONFIG_GET, PUSH_CONFIG_LIST -> "GET";
            case PUSH_CONFIG_DELETE -> "DELETE";
        };
    }

    @Override
    public byte[] wrapRequest(String methodName, Object payload) {
        try {
            return OBJECT_MAPPER.writeValueAsBytes(payload);
        } catch (Exception e) {
            throw new RuntimeCamelException("Failed to serialize request payload", e);
        }
    }

    @Override
    public Object unwrapResponse(byte[] responseBody, Class<?> responseType) {
        try {
            return OBJECT_MAPPER.readValue(responseBody, responseType);
        } catch (Exception e) {
            throw new RuntimeCamelException("Failed to deserialize response payload", e);
        }
    }

    @Override
    public byte[] wrapResponse(Object payload, Object requestId) {
        try {
            return OBJECT_MAPPER.writeValueAsBytes(payload);
        } catch (Exception e) {
            throw new RuntimeCamelException("Failed to serialize response payload", e);
        }
    }

    @Override
    public Object unwrapRequest(byte[] requestBody, Class<?> requestType) {
        try {
            return OBJECT_MAPPER.readValue(requestBody, requestType);
        } catch (Exception e) {
            throw new RuntimeCamelException("Failed to deserialize request payload", e);
        }
    }
}
