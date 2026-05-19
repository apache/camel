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

/**
 * Enumeration of all A2A v1.0 protocol operations. Maps each operation to its PascalCase JSON-RPC method name as
 * defined in A2A v1.0 Section 5.3 and Section 9.1 (PascalCase method names).
 */
public enum A2AOperations {
    MESSAGE_SEND("SendMessage"),
    MESSAGE_STREAM("SendStreamingMessage"),
    TASK_GET("GetTask"),
    TASK_LIST("ListTasks"),
    TASK_CANCEL("CancelTask"),
    TASK_SUBSCRIBE("SubscribeToTask"),
    PUSH_CONFIG_CREATE("CreateTaskPushNotificationConfig"),
    PUSH_CONFIG_GET("GetTaskPushNotificationConfig"),
    PUSH_CONFIG_LIST("ListTaskPushNotificationConfigs"),
    PUSH_CONFIG_DELETE("DeleteTaskPushNotificationConfig");

    private final String methodName;

    A2AOperations(String methodName) {
        this.methodName = methodName;
    }

    public String getMethodName() {
        return methodName;
    }

    /**
     * Looks up an operation by its JSON-RPC method name.
     *
     * @param  methodName the JSON-RPC method name (e.g., "SendMessage")
     * @return            the matching operation, or null if not found
     */
    public static A2AOperations fromMethodName(String methodName) {
        if (methodName == null) {
            return null;
        }
        for (A2AOperations operation : values()) {
            if (operation.methodName.equals(methodName)) {
                return operation;
            }
        }
        return null;
    }
}
