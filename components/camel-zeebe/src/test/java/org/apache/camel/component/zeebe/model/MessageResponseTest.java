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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MessageResponseTest {

    private static final String MARSHAL_TEST_RESULT_1
            = "{\"success\":true,\"correlation_key\":\"testKey\",\"message_key\":111}";
    private static final String MARSHAL_TEST_RESULT_2
            = "{\"success\":false,\"error_message\":\"Test Error\",\"error_code\":\"TestCode\",\"correlation_key\":\"testKey\",\"message_key\":111}";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void marshalTest() {
        MessageResponse message = new MessageResponse();
        message.setMessageKey(111);
        message.setCorrelationKey("testKey");
        message.setSuccess(true);

        String messageString = assertDoesNotThrow(() -> objectMapper.writeValueAsString(message));
        assertEquals(MARSHAL_TEST_RESULT_1, messageString);

        message.setSuccess(false);
        message.setErrorMessage("Test Error");
        message.setErrorCode("TestCode");

        messageString = assertDoesNotThrow(() -> objectMapper.writeValueAsString(message));
        assertEquals(MARSHAL_TEST_RESULT_2, messageString);
    }

    @Test
    public void unmarshalTest() {
        MessageResponse unmarshalledMessage1
                = assertDoesNotThrow(() -> objectMapper.readValue(MARSHAL_TEST_RESULT_1, MessageResponse.class));

        MessageResponse message = new MessageResponse();
        message.setMessageKey(111);
        message.setCorrelationKey("testKey");
        message.setSuccess(true);

        assertEquals(message, unmarshalledMessage1);

        MessageResponse unmarshalledMessage2
                = assertDoesNotThrow(() -> objectMapper.readValue(MARSHAL_TEST_RESULT_2, MessageResponse.class));

        message.setSuccess(false);
        message.setErrorMessage("Test Error");
        message.setErrorCode("TestCode");

        assertEquals(message, unmarshalledMessage2);
    }
}
