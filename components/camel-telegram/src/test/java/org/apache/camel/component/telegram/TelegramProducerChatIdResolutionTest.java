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

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.telegram.model.OutgoingTextMessage;
import org.apache.camel.component.telegram.util.TelegramMockRoutes;
import org.apache.camel.component.telegram.util.TelegramMockRoutes.MockProcessor;
import org.apache.camel.component.telegram.util.TelegramTestSupport;
import org.apache.camel.component.telegram.util.TelegramTestUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests a producer route with a fixed destination.
 */
public class TelegramProducerChatIdResolutionTest extends TelegramTestSupport {

    @EndpointInject("direct:telegram")
    private Endpoint endpoint;

    @Test
    public void testRouteWithFixedChatId() throws Exception {
        final MockProcessor<OutgoingTextMessage> mockProcessor = getMockRoutes().getMock("sendMessage");
        mockProcessor.clearRecordedMessages();

        template.sendBody(endpoint, "Hello");

        final OutgoingTextMessage message = mockProcessor.awaitRecordedMessages(1, 5000).get(0);
        assertEquals("my-id", message.getChatId());
        assertEquals("Hello", message.getText());
        assertNull(message.getParseMode());
    }

    @Test
    public void testRouteWithOverridenChatId() throws Exception {
        final MockProcessor<OutgoingTextMessage> mockProcessor = getMockRoutes().getMock("sendMessage");
        mockProcessor.clearRecordedMessages();

        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody("Hello 2");
        exchange.getIn().setHeader(TelegramConstants.TELEGRAM_CHAT_ID, "my-second-id");

        template.send(endpoint, exchange);

        final OutgoingTextMessage message = mockProcessor.awaitRecordedMessages(1, 5000).get(0);
        assertEquals("my-second-id", message.getChatId());
        assertEquals("Hello 2", message.getText());
        assertNull(message.getParseMode());

    }

    @Override
    protected RoutesBuilder[] createRouteBuilders() throws Exception {
        return new RoutesBuilder[] {
            getMockRoutes(),
            new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("direct:telegram")
                            .to("telegram:bots?authorizationToken=mock-token&chatId=my-id");
                }
            }};
    }

    @Override
    protected TelegramMockRoutes createMockRoutes() {
        return new TelegramMockRoutes(port)
                .addEndpoint(
                        "sendMessage",
                        "POST",
                        OutgoingTextMessage.class,
                        TelegramTestUtil.stringResource("messages/send-message.json"));
    }
}
