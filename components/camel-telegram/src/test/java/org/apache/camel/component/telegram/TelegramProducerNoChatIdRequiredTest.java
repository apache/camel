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
package org.apache.camel.component.telegram;

import java.util.Collections;

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.telegram.model.NoChatIdRequired;
import org.apache.camel.component.telegram.model.OutgoingAnswerInlineQuery;
import org.apache.camel.component.telegram.model.OutgoingCallbackQueryMessage;
import org.apache.camel.component.telegram.util.TelegramMockRoutes;
import org.apache.camel.component.telegram.util.TelegramTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests that {@link NoChatIdRequired} message types work without setting a chat ID.
 */
public class TelegramProducerNoChatIdRequiredTest extends TelegramTestSupport {

    @EndpointInject("direct:telegram")
    private Endpoint endpoint;

    @Test
    public void testOutgoingAnswerInlineQueryWithoutChatId() {
        OutgoingAnswerInlineQuery message = OutgoingAnswerInlineQuery.builder()
                .inlineQueryId("test-inline-query-id")
                .results(Collections.emptyList())
                .build();

        // The message should not have a chatId set
        assertNull(message.getChatId(), "ChatId should not be set");

        // This should work without throwing an exception since answerInlineQuery
        // does not require a chat_id according to Telegram API
        assertDoesNotThrow(() -> template.sendBody(endpoint, message),
                "OutgoingAnswerInlineQuery should not require a chatId");
    }

    @Test
    public void testOutgoingCallbackQueryMessageWithoutChatId() {
        OutgoingCallbackQueryMessage message = new OutgoingCallbackQueryMessage();
        message.setCallbackQueryId("test-callback-query-id");
        message.setText("Callback response");

        // The message should not have a chatId set
        assertNull(message.getChatId(), "ChatId should not be set");

        // This should work without throwing an exception since answerCallbackQuery
        // does not require a chat_id according to Telegram API
        assertDoesNotThrow(() -> template.sendBody(endpoint, message),
                "OutgoingCallbackQueryMessage should not require a chatId");
    }

    @Override
    protected RoutesBuilder[] createRouteBuilders() {
        return new RoutesBuilder[] {
                getMockRoutes(),
                new RouteBuilder() {
                    @Override
                    public void configure() {
                        from("direct:telegram")
                                .to("telegram:bots?authorizationToken=mock-token");
                    }
                } };
    }

    @Override
    protected TelegramMockRoutes createMockRoutes() {
        // Response for both answerInlineQuery and answerCallbackQuery is just {"ok": true, "result": true}
        String successResponse = "{\"ok\": true, \"result\": true}";
        return new TelegramMockRoutes(port)
                .addEndpoint(
                        "answerInlineQuery",
                        "POST",
                        OutgoingAnswerInlineQuery.class,
                        successResponse)
                .addEndpoint(
                        "answerCallbackQuery",
                        "POST",
                        OutgoingCallbackQueryMessage.class,
                        successResponse);
    }
}
