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
package org.apache.camel.component.a2a.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A2A protocol task state enum mapping internal names to proto wire names.
 */
public enum TaskState {
    UNSPECIFIED("TASK_STATE_UNSPECIFIED"),
    SUBMITTED("TASK_STATE_SUBMITTED"),
    WORKING("TASK_STATE_WORKING"),
    COMPLETED("TASK_STATE_COMPLETED"),
    FAILED("TASK_STATE_FAILED"),
    CANCELED("TASK_STATE_CANCELED"),
    INPUT_REQUIRED("TASK_STATE_INPUT_REQUIRED"),
    REJECTED("TASK_STATE_REJECTED"),
    AUTH_REQUIRED("TASK_STATE_AUTH_REQUIRED");

    private static final Logger LOG = LoggerFactory.getLogger(TaskState.class);

    private final String protoName;

    TaskState(String protoName) {
        this.protoName = protoName;
    }

    @JsonValue
    public String getProtoName() {
        return protoName;
    }

    @JsonCreator
    public static TaskState fromProtoName(String protoName) {
        if (protoName == null) {
            return UNSPECIFIED;
        }
        for (TaskState state : values()) {
            if (state.protoName.equals(protoName)) {
                return state;
            }
        }
        LOG.warn("Unknown A2A task state proto name '{}' — defaulting to UNSPECIFIED", protoName);
        return UNSPECIFIED;
    }

    /**
     * Returns true if this state represents a terminal task state.
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELED || this == REJECTED;
    }

    /**
     * Returns true if this state represents an interrupted task state requiring external input.
     */
    public boolean isInterrupted() {
        return this == INPUT_REQUIRED || this == AUTH_REQUIRED;
    }
}
