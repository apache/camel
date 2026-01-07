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
package org.apache.camel.component.telegram.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SendChatActionMessage} JSON serialization.
 */
class SendChatActionMessageTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testActionSerializesToJson() throws JsonProcessingException {
        SendChatActionMessage message = new SendChatActionMessage(SendChatActionMessage.Action.TYPING);
        message.setChatId("123456");
        message.setMessageThreadId(42);
        message.setBusinessConnectionId("bc_123");

        String json = objectMapper.writeValueAsString(message);

        assertThat(json)
                .contains("\"action\":\"typing\"")
                .contains("\"chat_id\":\"123456\"")
                .contains("\"message_thread_id\":42")
                .contains("\"business_connection_id\":\"bc_123\"");
    }

    @Test
    void testAllActionEnumValuesAreSnakeCase() {
        for (SendChatActionMessage.Action action : SendChatActionMessage.Action.values()) {
            assertThat(action.getValue())
                    .isEqualTo(action.getValue().toLowerCase())
                    .doesNotContain(" ");
        }
    }
}
