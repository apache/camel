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

package org.apache.camel.component.zeebe.model;

import java.util.Collections;
import java.util.HashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class JobWorkerMessageTest {

    private static final String MARSHAL_TEST_RESULT_1
            = "{\"key\":0,\"type\":\"type\",\"customHeaders\":{},\"processInstanceKey\":1,\"bpmnProcessId\":\"bpmn\",\"processDefinitionVersion\":1,\"processDefinitionKey\":1,\"elementId\":\"eid\",\"elementInstanceKey\":1,"
              + "\"worker\":\"worker\",\"retries\":1,\"deadline\":1,\"variablesAsMap\":{}}";
    private static final String MARSHAL_TEST_RESULT_2
            = "{\"key\":0,\"type\":\"type\",\"customHeaders\":{},\"processInstanceKey\":1,\"bpmnProcessId\":\"bpmn\",\"processDefinitionVersion\":1,\"processDefinitionKey\":1,\"elementId\":\"eid\",\"elementInstanceKey\":1,"
              + "\"worker\":\"worker\",\"retries\":1,\"deadline\":1,\"variablesAsMap\":{\"varC\":{},\"varB\":10,\"varA\":\"test\"}}";
    private static final String MARSHAL_TEST_RESULT_3
            = "{\"key\":0,\"type\":\"type\",\"customHeaders\":{\"h1\":\"test1\",\"h2\":\"test2\"},\"processInstanceKey\":1,\"bpmnProcessId\":\"bpmn\",\"processDefinitionVersion\":1,\"processDefinitionKey\":1,"
              + "\"elementId\":\"eid\",\"elementInstanceKey\":1,\"worker\":\"worker\",\"retries\":1,\"deadline\":1,\"variablesAsMap\":{\"varC\":{},\"varB\":10,\"varA\":\"test\"}}";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void marshalTest() {
        JobWorkerMessage message = new JobWorkerMessage();
        message.setWorker("worker");
        message.setRetries(1);
        message.setElementId("eid");
        message.setElementInstanceKey(1);
        message.setProcessInstanceKey(1);
        message.setProcessDefinitionVersion(1);
        message.setProcessDefinitionKey(1);
        message.setBpmnProcessId("bpmn");
        message.setDeadline(1);
        message.setType("type");

        String messageString = assertDoesNotThrow(() -> objectMapper.writeValueAsString(message));
        assertEquals(MARSHAL_TEST_RESULT_1, messageString);

        HashMap<String, Object> variables = new HashMap<>();
        variables.put("varA", "test");
        variables.put("varB", 10);
        variables.put("varC", Collections.emptyMap());
        message.setVariables(variables);

        messageString = assertDoesNotThrow(() -> objectMapper.writeValueAsString(message));
        assertEquals(MARSHAL_TEST_RESULT_2, messageString);

        HashMap<String, String> headers = new HashMap<>();
        headers.put("h1", "test1");
        headers.put("h2", "test2");

        message.setCustomHeaders(headers);

        messageString = assertDoesNotThrow(() -> objectMapper.writeValueAsString(message));
        assertEquals(MARSHAL_TEST_RESULT_3, messageString);
    }

    @Test
    public void unmarshalTest() {
        JobWorkerMessage unmarshalledMessage1
                = assertDoesNotThrow(() -> objectMapper.readValue(MARSHAL_TEST_RESULT_1, JobWorkerMessage.class));

        JobWorkerMessage message = new JobWorkerMessage();
        message.setWorker("worker");
        message.setRetries(1);
        message.setElementId("eid");
        message.setElementInstanceKey(1);
        message.setProcessInstanceKey(1);
        message.setProcessDefinitionVersion(1);
        message.setProcessDefinitionKey(1);
        message.setBpmnProcessId("bpmn");
        message.setDeadline(1);
        message.setType("type");

        assertEquals(message, unmarshalledMessage1);

        JobWorkerMessage unmarshalledMessage2
                = assertDoesNotThrow(() -> objectMapper.readValue(MARSHAL_TEST_RESULT_2, JobWorkerMessage.class));

        HashMap<String, Object> variables = new HashMap<>();
        variables.put("varA", "test");
        variables.put("varB", 10);
        variables.put("varC", Collections.emptyMap());
        message.setVariables(variables);

        assertEquals(message, unmarshalledMessage2);

        JobWorkerMessage unmarshalledMessage3
                = assertDoesNotThrow(() -> objectMapper.readValue(MARSHAL_TEST_RESULT_3, JobWorkerMessage.class));

        HashMap<String, String> headers = new HashMap<>();
        headers.put("h1", "test1");
        headers.put("h2", "test2");

        message.setCustomHeaders(headers);

        assertEquals(message, unmarshalledMessage3);
    }
}
