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
package org.apache.camel.component.zeebe.internal;

public enum OperationName {
    START_PROCESS("startProcess"),
    CANCEL_PROCESS("cancelProcess"),
    PUBLISH_MESSAGE("publishMessage"),
    COMPLETE_JOB("completeJob"),
    FAIL_JOB("failJob"),
    UPDATE_JOB_RETRIES("updateJobRetries"),
    REGISTER_JOB_WORKER("worker"),
    THROW_ERROR("throwError"),
    DEPLOY_RESOURCE("deployResource");

    private final String value;

    OperationName(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static OperationName fromValue(String value) {
        for (OperationName operationName : OperationName.values()) {
            if (operationName.value.equals(value)) {
                return operationName;
            }
        }
        throw new IllegalArgumentException(value);
    }
}
