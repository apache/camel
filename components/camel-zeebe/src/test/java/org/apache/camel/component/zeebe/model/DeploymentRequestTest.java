/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.camel.component.zeebe.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class DeploymentRequestTest {

    private final String MARSHAL_TEST_RESULT_1 = "{\"name\":\"test.bpmn\",\"content\":\"dGVzdCBjb250ZW50\"}";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void marshalTest() {
        DeploymentRequest message = new DeploymentRequest();
        message.setName("test.bpmn");
        message.setContent("test content".getBytes());

        try {
            String messageString = objectMapper.writeValueAsString(message);
            assertEquals(MARSHAL_TEST_RESULT_1, messageString);
        } catch (JsonProcessingException e) {
            fail("Error in JSON processing");
        }
    }

    @Test
    public void unmarshalTest() {
        try {
            DeploymentRequest unmarshalledMessage1 = objectMapper.readValue(MARSHAL_TEST_RESULT_1, DeploymentRequest.class);

            DeploymentRequest message = new DeploymentRequest();
            message.setName("test.bpmn");
            message.setContent("test content".getBytes());

            assertEquals(message, unmarshalledMessage1);
        } catch (JsonProcessingException e) {
            fail("Error in JSON processing");
        }
    }
}