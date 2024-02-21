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

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.telegram.model.OutgoingTextMessage;
import org.apache.camel.component.telegram.model.UpdateResult;
import org.apache.camel.component.telegram.util.TelegramMockRoutes;
import org.apache.camel.component.telegram.util.TelegramTestSupport;
import org.apache.camel.component.telegram.util.TelegramTestUtil;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertCollectionSize;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests a chain made of a consumer and a producer to create a direct chat-bot.
 */
public class TelegramChatBotTest extends TelegramTestSupport {

    @Test
    public void testChatBotResult() {

        List<OutgoingTextMessage> msgs = Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .until(() -> getMockRoutes().getMock("sendMessage").getRecordedMessages(),
                        rawMessages -> rawMessages.size() >= 2)
                .stream()
                .map(message -> (OutgoingTextMessage) message)
                .toList();

        assertCollectionSize(msgs, 2);
        assertTrue(msgs.stream().anyMatch(m -> "echo from the bot: Hello World!".equals(m.getText())));
        assertTrue(msgs.stream().anyMatch(m -> "echo from the bot: taken".equals(m.getText())));
        assertTrue(msgs.stream().noneMatch(m -> m.getParseMode() != null));
    }

    /**
     * This method simulates the first step of the chat-bot logic.
     *
     * @param exchange the current exchange originating from the telegram bot
     */
    public void chatBotProcess1(Exchange exchange) {
        if (exchange.getIn().getBody(String.class).equals("intercept")) {
            exchange.getIn().setBody("taken");
        }
    }

    /**
     * This method simulates the second step of the chat-bot logic.
     *
     * @param  message the message coming from the telegram bot
     * @return         the reply, if any
     */
    public String chatBotProcess2(String message) {
        return "echo from the bot: " + message;
    }

    @Override
    protected RoutesBuilder[] createRouteBuilders() {
        return new RoutesBuilder[] {
                getMockRoutes(),
                new RouteBuilder() {
                    @Override
                    public void configure() {

                        from("telegram:bots?authorizationToken=mock-token")
                                .bean(TelegramChatBotTest.this, "chatBotProcess1")
                                .bean(TelegramChatBotTest.this, "chatBotProcess2")
                                .to("telegram:bots?authorizationToken=mock-token");
                    }
                } };
    }

    @Override
    protected TelegramMockRoutes createMockRoutes() {
        UpdateResult request = getJSONResource("messages/updates-single.json", UpdateResult.class);
        request.getUpdates().get(0).getMessage().setText("Hello World!");
        request.getUpdates().get(0).getMessage().getChat().setId("my-chat-id");

        UpdateResult request2 = getJSONResource("messages/updates-single.json", UpdateResult.class);
        request2.getUpdates().get(0).getMessage().setText("intercept");
        request2.getUpdates().get(0).getMessage().getChat().setId("my-chat-id");

        return new TelegramMockRoutes(port)
                .addEndpoint(
                        "getUpdates",
                        "GET",
                        String.class,
                        TelegramTestUtil.serialize(request),
                        TelegramTestUtil.serialize(request2),
                        TelegramTestUtil.stringResource("messages/updates-empty.json"))
                .addEndpoint(
                        "sendMessage",
                        "POST",
                        OutgoingTextMessage.class,
                        TelegramTestUtil.stringResource("messages/send-message.json"),
                        TelegramTestUtil.stringResource("messages/send-message.json"),
                        TelegramTestUtil.stringResource("messages/send-message.json"));
    }

}
