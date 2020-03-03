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

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.telegram.model.IncomingGame;
import org.apache.camel.component.telegram.model.IncomingMessage;
import org.apache.camel.component.telegram.model.InlineKeyboardMarkup;
import org.apache.camel.component.telegram.util.TelegramMockRoutes;
import org.apache.camel.component.telegram.util.TelegramTestSupport;
import org.apache.camel.component.telegram.util.TelegramTestUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests the reception of messages without text having media content.
 */
public class TelegramConsumerMediaGameTest extends TelegramTestSupport {

    @EndpointInject("mock:telegram")
    private MockEndpoint endpoint;

    @Test
    public void testReceptionOfAMessageWithAGame() throws Exception {
        endpoint.expectedMinimumMessageCount(1);
        endpoint.assertIsSatisfied(5000);

        Exchange mediaExchange = endpoint.getExchanges().get(0);
        IncomingMessage msg = mediaExchange.getIn().getBody(IncomingMessage.class);

        IncomingGame game = msg.getGame();
        InlineKeyboardMarkup inlineKeyboardMarkup = (InlineKeyboardMarkup)msg.getReplyMarkup();

        Assertions.assertNotNull(game);
        Assertions.assertEquals("test_game", game.getTitle());
        Assertions.assertEquals("test", game.getDescription());
        Assertions.assertEquals("game text", game.getText());
        Assertions.assertEquals(1, game.getPhoto().size());
        Assertions.assertEquals("AgADBAADnrAxG1rhiVAsV1IghUpUwn4eqhsABAEAAwIAA20AA32sBQABFgQ",
            game.getPhoto().get(0).getFileId());
        Assertions.assertEquals(2469, game.getPhoto().get(0).getFileSize());
        Assertions.assertEquals(180, game.getPhoto().get(0).getHeight());
        Assertions.assertEquals(320, game.getPhoto().get(0).getWidth());
        Assertions.assertEquals("AQADfh6qGwAEfawFAAE", game.getPhoto().get(0).getFileUniqueId());

        Assertions.assertNotNull(inlineKeyboardMarkup);
        Assertions.assertEquals("Play test_game", inlineKeyboardMarkup.getInlineKeyboard().get(0).get(0).getText());

    }

    @Override
    protected RoutesBuilder[] createRouteBuilders() throws Exception {
        return new RoutesBuilder[]{
            getMockRoutes(),
            new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("telegram:bots?authorizationToken=mock-token")
                        .to("mock:telegram");
                }
            }};
    }

    @Override
    protected TelegramMockRoutes createMockRoutes() {
        return new TelegramMockRoutes(port)
            .addEndpoint(
                "getUpdates",
                "GET",
                String.class,
                TelegramTestUtil.stringResource("messages/updates-media-game.json"),
                TelegramTestUtil.stringResource("messages/updates-empty.json"));
    }
}
