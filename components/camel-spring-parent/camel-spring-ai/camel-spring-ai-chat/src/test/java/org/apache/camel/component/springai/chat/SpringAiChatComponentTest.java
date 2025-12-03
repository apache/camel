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
package org.apache.camel.component.springai.chat;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SpringAiChatComponentTest extends CamelTestSupport {

    private ChatModel mockChatModel;

    @Override
    protected void doPreSetup() throws Exception {
        super.doPreSetup();

        // Create a mock ChatModel
        mockChatModel = mock(ChatModel.class);

        // Mock response
        AssistantMessage assistantMessage = new AssistantMessage("Hello! I'm a mock AI assistant.");
        Generation generation = new Generation(assistantMessage);
        ChatResponse chatResponse = new ChatResponse(java.util.List.of(generation));

        when(mockChatModel.call(any(Prompt.class))).thenReturn(chatResponse);
    }

    @Test
    public void testChatComponent() throws Exception {
        String response = template.requestBody("direct:chat", "Hello, AI!", String.class);

        assertNotNull(response);
        assertEquals("Hello! I'm a mock AI assistant.", response);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                SpringAiChatComponent component = new SpringAiChatComponent();
                component.setChatModel(mockChatModel);
                context.addComponent("spring-ai-chat", component);

                from("direct:chat")
                        .to("spring-ai-chat:test");
            }
        };
    }
}
