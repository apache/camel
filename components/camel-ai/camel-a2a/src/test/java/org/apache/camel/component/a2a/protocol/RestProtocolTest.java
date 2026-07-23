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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestProtocolTest {

    private final RestProtocol protocol = new RestProtocol();

    @Test
    void resolvePathForMessageSend() {
        String path = protocol.resolvePath(A2AOperations.MESSAGE_SEND, null);
        assertEquals("/message:send", path);
    }

    @Test
    void resolvePathForTaskGet() {
        String path = protocol.resolvePath(A2AOperations.TASK_GET, "task-123");
        assertEquals("/tasks/task-123", path);
    }

    @Test
    void resolvePathForTaskCancel() {
        String path = protocol.resolvePath(A2AOperations.TASK_CANCEL, "task-123");
        assertEquals("/tasks/task-123:cancel", path);
    }

    @Test
    void resolvePathForTaskList() {
        String path = protocol.resolvePath(A2AOperations.TASK_LIST, null);
        assertEquals("/tasks", path);
    }

    @Test
    void resolveHttpMethodForMessageSend() {
        String method = protocol.resolveHttpMethod(A2AOperations.MESSAGE_SEND);
        assertEquals("POST", method);
    }

    @Test
    void resolveHttpMethodForTaskGet() {
        String method = protocol.resolveHttpMethod(A2AOperations.TASK_GET);
        assertEquals("GET", method);
    }

    @Test
    void resolveHttpMethodForTaskList() {
        String method = protocol.resolveHttpMethod(A2AOperations.TASK_LIST);
        assertEquals("GET", method);
    }

    @Test
    void resolveHttpMethodForTaskSubscribe() {
        String method = protocol.resolveHttpMethod(A2AOperations.TASK_SUBSCRIBE);
        assertEquals("POST", method);
    }

    @Test
    void resolveHttpMethodForPushConfigDelete() {
        String method = protocol.resolveHttpMethod(A2AOperations.PUSH_CONFIG_DELETE);
        assertEquals("DELETE", method);
    }

    @Test
    void defaultProtocolCapabilitiesAreEvolvable() {
        assertEquals(A2AConstants.CONTENT_TYPE, protocol.contentType());
        assertTrue(protocol.isStreamingOperation(A2AOperations.MESSAGE_STREAM));
        assertTrue(protocol.isStreamingOperation(A2AOperations.TASK_SUBSCRIBE));
        assertFalse(protocol.isStreamingOperation(A2AOperations.MESSAGE_SEND));
    }

    @Test
    void resolvePathForPushConfigGet() {
        String path = protocol.resolvePath(A2AOperations.PUSH_CONFIG_GET, "task-123:config-456");
        assertEquals("/tasks/task-123/pushNotificationConfigs/config-456", path);
    }

    @Test
    void resolvePathForPushConfigGetWithoutConfigId() {
        String path = protocol.resolvePath(A2AOperations.PUSH_CONFIG_GET, "task-123");
        assertEquals("/tasks/task-123/pushNotificationConfigs", path);
    }

    @Test
    void resolvePathForPushConfigDelete() {
        String path = protocol.resolvePath(A2AOperations.PUSH_CONFIG_DELETE, "task-123:config-456");
        assertEquals("/tasks/task-123/pushNotificationConfigs/config-456", path);
    }

    @Test
    void resolvePathForPushConfigList() {
        String path = protocol.resolvePath(A2AOperations.PUSH_CONFIG_LIST, "task-123");
        assertEquals("/tasks/task-123/pushNotificationConfigs", path);
    }
}
