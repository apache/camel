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

import java.time.Instant;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.telegram.model.Chat;
import org.apache.camel.component.telegram.model.IncomingMessage;
import org.apache.camel.component.telegram.model.UpdateResult;
import org.apache.camel.component.telegram.model.User;
import org.apache.camel.component.telegram.util.TelegramTestSupport;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

/**
 * Tests the JSON mapping of the API updates.
 */
public class TelegramConsumerMappingTest extends TelegramTestSupport {

    @EndpointInject(uri = "mock:telegram")
    private MockEndpoint endpoint;

    @Before
    public void mockAPIs() {
        TelegramService api = mockTelegramService();

        UpdateResult res1 = getJSONResource("messages/updates-single.json", UpdateResult.class);

        UpdateResult defaultRes = getJSONResource("messages/updates-empty.json", UpdateResult.class);

        when(api.getUpdates(any(), any(), any(), any())).thenReturn(res1).thenAnswer((i) -> defaultRes);
    }

    @Test
    public void testMessageMapping() throws Exception {
        endpoint.expectedMinimumMessageCount(1);
        endpoint.expectedMessageCount(1);
        endpoint.assertIsSatisfied();

        Exchange ex = endpoint.getExchanges().get(0);
        Message m = ex.getIn();

        assertNotNull(m);

        // checking headers
        assertEquals("-45658", m.getHeader(TelegramConstants.TELEGRAM_CHAT_ID));

        // checking body
        assertNotNull(m.getBody());
        assertTrue(m.getBody() instanceof IncomingMessage);
        IncomingMessage body = (IncomingMessage) m.getBody();

        assertEquals("a message", body.getText());
        assertEquals(Long.valueOf(179L), body.getMessageId());
        assertEquals(Instant.ofEpochSecond(1463436626L), body.getDate());

        // checking from
        User user = body.getFrom();
        assertNotNull(user);
        assertEquals("John", user.getFirstName());
        assertEquals("Doe", user.getLastName());
        assertEquals(Long.valueOf(1585844777), user.getId());

        // checking chat
        Chat chat = body.getChat();
        assertNotNull(chat);
        assertEquals("-45658", chat.getId());
        assertEquals("A chat group", chat.getTitle());
        assertEquals("group", chat.getType());

    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("telegram:bots/mock-token").to("mock:telegram");
            }
        };
    }

}
