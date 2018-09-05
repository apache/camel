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

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.telegram.model.OutgoingTextMessage;
import org.apache.camel.component.telegram.util.TelegramTestSupport;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.eq;

/**
 * Tests a producer route with a fixed destination.
 */
public class TelegramProducerChatIdResolutionTest extends TelegramTestSupport {

    @EndpointInject(uri = "direct:telegram")
    private Endpoint endpoint;

    @Test
    public void testRouteWithFixedChatId() throws Exception {

        TelegramService api = mockTelegramService();

        context().createProducerTemplate().sendBody(endpoint, "Hello");

        ArgumentCaptor<OutgoingTextMessage> captor = ArgumentCaptor.forClass(OutgoingTextMessage.class);

        Mockito.verify(api).sendMessage(eq("mock-token"), captor.capture());
        assertEquals("my-id", captor.getValue().getChatId());
        assertEquals("Hello", captor.getValue().getText());
        assertNull(captor.getValue().getParseMode());
    }

    @Test
    public void testRouteWithOverridenChatId() throws Exception {

        TelegramService api = mockTelegramService();

        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody("Hello 2");
        exchange.getIn().setHeader(TelegramConstants.TELEGRAM_CHAT_ID, "my-second-id");

        context().createProducerTemplate().send(endpoint, exchange);

        ArgumentCaptor<OutgoingTextMessage> captor = ArgumentCaptor.forClass(OutgoingTextMessage.class);

        Mockito.verify(api).sendMessage(eq("mock-token"), captor.capture());
        assertEquals("my-second-id", captor.getValue().getChatId());
        assertEquals("Hello 2", captor.getValue().getText());
        assertNull(captor.getValue().getParseMode());

    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:telegram").to("telegram:bots/mock-token?chatId=my-id");
            }
        };
    }

}
