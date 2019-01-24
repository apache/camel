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
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.telegram.model.Chat;
import org.apache.camel.component.telegram.model.IncomingMessage;
import org.apache.camel.component.telegram.model.UpdateResult;
import org.apache.camel.component.telegram.util.TelegramTestSupport;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;


/**
 * 
 * Test channel data updates are converted by camel application.
 *
 */
public class TelegramConsumerChannelPostTest extends TelegramTestSupport {

    @EndpointInject(uri = "mock:telegram")
    private MockEndpoint endpoint;

    @Before
    public void mockAPIs() {
        TelegramService api = mockTelegramService();

        UpdateResult res1 = getJSONResource("messages/updates-channelMessage.json", UpdateResult.class);

        UpdateResult defaultRes = getJSONResource("messages/updates-empty.json", UpdateResult.class);

        when(api.getUpdates(any(), any(), any(), any())).thenReturn(res1).thenAnswer((i) -> defaultRes);
    }
    
    @Test
    public void testReceptionOfMessageWithAMessage() throws Exception {
        endpoint.expectedMinimumMessageCount(1);
        endpoint.assertIsSatisfied();

        Exchange mediaExchange = endpoint.getExchanges().get(0);
        IncomingMessage msg = mediaExchange.getIn().getBody(IncomingMessage.class);
        
        assertEquals("-1001245756934", mediaExchange.getIn().getHeader(TelegramConstants.TELEGRAM_CHAT_ID));
        
        //checking body
        assertNotNull(msg);
        assertEquals("test", msg.getText());
        assertEquals(Long.valueOf(67L), msg.getMessageId());
        assertEquals(Instant.ofEpochSecond(1546505413L), msg.getDate());
        
        // checking chat
        Chat chat = msg.getChat();
        assertNotNull(chat);
        assertEquals("-1001245756934", chat.getId());
        assertEquals("cameltemp", chat.getTitle());
        assertEquals("channel", chat.getType());

    }
    
    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("telegram:bots/mock-token")
                        .to("mock:telegram");
            }
        };
    }
}
