/**
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

import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.telegram.model.OutgoingTextMessage;
import org.apache.camel.component.telegram.model.UpdateResult;
import org.apache.camel.component.telegram.util.TelegramTestSupport;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.verification.Timeout;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


/**
 * Tests a chain made of a consumer and a producer to create a direct chat-bot.
 */
public class TelegramChatBotTest extends TelegramTestSupport {

    @Before
    public void mockAPIs() {
        TelegramService service = mockTelegramService();

        UpdateResult request = getJSONResource("messages/updates-single.json", UpdateResult.class);
        request.getUpdates().get(0).getMessage().setText("Hello World!");
        request.getUpdates().get(0).getMessage().getChat().setId("my-chat-id");

        UpdateResult request2 = getJSONResource("messages/updates-single.json", UpdateResult.class);
        request2.getUpdates().get(0).getMessage().setText("intercept");
        request2.getUpdates().get(0).getMessage().getChat().setId("my-chat-id");

        UpdateResult defaultRes = getJSONResource("messages/updates-empty.json", UpdateResult.class);

        when(service.getUpdates(any(), any(), any(), any()))
                .thenReturn(request)
                .thenReturn(request2)
                .thenAnswer((i) -> defaultRes);
    }

    @Test
    public void testChatBotResult() throws Exception {

        TelegramService service = currentMockService();

        ArgumentCaptor<OutgoingTextMessage> captor = ArgumentCaptor.forClass(OutgoingTextMessage.class);

        verify(service, new Timeout(5000, times(2))).sendMessage(eq("mock-token"), captor.capture());

        List<OutgoingTextMessage> msgs = captor.getAllValues();

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
     * @param message the message coming from the telegram bot
     * @return the reply, if any
     */
    public String chatBotProcess2(String message) {
        return "echo from the bot: " + message;
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                from("telegram:bots/mock-token")
                        .bean(TelegramChatBotTest.this, "chatBotProcess1")
                        .bean(TelegramChatBotTest.this, "chatBotProcess2")
                        .to("telegram:bots/mock-token");
            }
        };
    }

}
