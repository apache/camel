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

public class MessageRequestTest {

    private static final String MARSHAL_TEST_RESULT_1
            = "{\"name\":\"test\",\"variables\":{},\"correlation_key\":\"correlation\",\"time_to_live\":100,\"message_id\":\"message\"}";
    private static final String MARSHAL_TEST_RESULT_2
            = "{\"name\":\"test\",\"variables\":{\"varC\":{},\"varB\":10,\"varA\":\"test\"},\"correlation_key\":\"correlation\",\"time_to_live\":100,\"message_id\":\"message\"}";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void marshalTest() {
        MessageRequest message = new MessageRequest();
        message.setName("test");
        message.setCorrelationKey("correlation");
        message.setMessageId("message");
        message.setTimeToLive(100);

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
        MessageRequest unmarshalledMessage1
                = assertDoesNotThrow(() -> objectMapper.readValue(MARSHAL_TEST_RESULT_1, MessageRequest.class));

        MessageRequest message = new MessageRequest();
        message.setName("test");
        message.setCorrelationKey("correlation");
        message.setMessageId("message");
        message.setTimeToLive(100);

        assertEquals(message, unmarshalledMessage1);

        MessageRequest unmarshalledMessage2
                = assertDoesNotThrow(() -> objectMapper.readValue(MARSHAL_TEST_RESULT_2, MessageRequest.class));

        HashMap<String, Object> variables = new HashMap<>();
        variables.put("varA", "test");
        variables.put("varB", 10);
        variables.put("varC", Collections.emptyMap());
        message.setVariables(variables);

        assertEquals(message, unmarshalledMessage2);
    }
}
