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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ProcessRequestTest {

    private static final String MARSHAL_TEST_RESULT_1
            = "{\"variables\":{},\"process_id\":\"process_1\",\"process_version\":1,\"process_key\":1,\"process_instance_key\":1}";
    private static final String MARSHAL_TEST_RESULT_2
            = "{\"variables\":{\"varC\":{},\"varB\":10,\"varA\":\"test\"},\"process_id\":\"process_1\",\"process_version\":1,\"process_key\":1,\"process_instance_key\":1}";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void marshalTest() throws JsonProcessingException {
        ProcessRequest message = new ProcessRequest();
        message.setProcessInstanceKey(1);
        message.setProcessId("process_1");
        message.setProcessVersion(1);
        message.setProcessKey(1);

        String messageString = assertDoesNotThrow(() -> objectMapper.writeValueAsString(message));
        assertEquals(MARSHAL_TEST_RESULT_1, messageString);

        HashMap<String, Object> variables = new HashMap<>();
        variables.put("varA", "test");
        variables.put("varB", 10);
        variables.put("varC", Collections.emptyMap());
        message.setVariables(variables);

        messageString = assertDoesNotThrow(() -> objectMapper.writeValueAsString(message));
        assertEquals(MARSHAL_TEST_RESULT_2, messageString);
    }

    @Test
    public void unmarshalTest() {
        ProcessRequest unmarshalledMessage1
                = assertDoesNotThrow(() -> objectMapper.readValue(MARSHAL_TEST_RESULT_1, ProcessRequest.class));

        ProcessRequest message = new ProcessRequest();
        message.setProcessInstanceKey(1);
        message.setProcessId("process_1");
        message.setProcessVersion(1);
        message.setProcessKey(1);

        assertEquals(message, unmarshalledMessage1);

        ProcessRequest unmarshalledMessage2
                = assertDoesNotThrow(() -> objectMapper.readValue(MARSHAL_TEST_RESULT_2, ProcessRequest.class));

        HashMap<String, Object> variables = new HashMap<>();
        variables.put("varA", "test");
        variables.put("varB", 10);
        variables.put("varC", Collections.emptyMap());
        message.setVariables(variables);

        assertEquals(message, unmarshalledMessage2);
    }
}
