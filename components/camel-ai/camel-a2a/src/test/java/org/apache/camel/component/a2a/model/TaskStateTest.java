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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskStateTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void serializesToProtoName() throws JsonProcessingException {
        String json = mapper.writeValueAsString(TaskState.SUBMITTED);
        assertThat(json).isEqualTo("\"TASK_STATE_SUBMITTED\"");
    }

    @Test
    void deserializesFromProtoName() throws JsonProcessingException {
        TaskState state = mapper.readValue("\"TASK_STATE_COMPLETED\"", TaskState.class);
        assertThat(state).isEqualTo(TaskState.COMPLETED);
    }

    @Test
    void unspecifiedHandledGracefully() throws JsonProcessingException {
        TaskState state = mapper.readValue("\"TASK_STATE_UNSPECIFIED\"", TaskState.class);
        assertThat(state).isEqualTo(TaskState.UNSPECIFIED);
    }

    @Test
    void isTerminal() {
        assertThat(TaskState.COMPLETED.isTerminal()).isTrue();
        assertThat(TaskState.FAILED.isTerminal()).isTrue();
        assertThat(TaskState.CANCELED.isTerminal()).isTrue();
        assertThat(TaskState.REJECTED.isTerminal()).isTrue();

        assertThat(TaskState.WORKING.isTerminal()).isFalse();
        assertThat(TaskState.INPUT_REQUIRED.isTerminal()).isFalse();
    }

    @Test
    void isInterrupted() {
        assertThat(TaskState.INPUT_REQUIRED.isInterrupted()).isTrue();
        assertThat(TaskState.AUTH_REQUIRED.isInterrupted()).isTrue();

        assertThat(TaskState.WORKING.isInterrupted()).isFalse();
    }
}
