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

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.telegram.model.EditMessageLiveLocationMessage;
import org.apache.camel.component.telegram.model.MessageResult;
import org.apache.camel.component.telegram.model.SendLocationMessage;
import org.apache.camel.component.telegram.model.SendVenueMessage;
import org.apache.camel.component.telegram.model.StopMessageLiveLocationMessage;
import org.apache.camel.component.telegram.service.RestBotAPI;
import org.apache.camel.component.telegram.service.TelegramServiceRestBotAPIAdapter;
import org.apache.camel.component.telegram.util.TelegramTestSupport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Tests a producer that sends location information.
 */
@RunWith(MockitoJUnitRunner.class)
public class TelegramProducerLocationTest extends TelegramTestSupport {

    private final double latitude = 59.9386292;
    private final double longitude = 30.3141308;

    private TelegramService service;

    @Mock
    private RestBotAPI restBotAPI;

    @Before
    public void setUp() {
        service = new TelegramServiceRestBotAPIAdapter(restBotAPI);
        TelegramServiceProvider.get().setAlternativeService(service);
    }

    @Test
    public void testSendLocation() {
        MessageResult expected = new MessageResult();
        expected.setOk(true);
        when(restBotAPI.sendLocation(anyString(), any(SendLocationMessage.class))).thenReturn(expected);

        SendLocationMessage msg = new SendLocationMessage(latitude, longitude);
        MessageResult actual = (MessageResult) service.sendMessage("mock-token", msg);

        assertEquals(expected, actual);
    }

    @Test
    public void testSendVenue() {
        MessageResult expected = new MessageResult();
        expected.setOk(true);
        when(restBotAPI.sendVenue(anyString(), any(SendVenueMessage.class))).thenReturn(expected);

        SendVenueMessage msg = new SendVenueMessage(latitude, longitude, "title", "address");
        MessageResult actual = (MessageResult) service.sendMessage("mock-token", msg);

        assertEquals(expected, actual);
    }

    @Test
    public void testEditMessageLiveLocation() {
        MessageResult expected = new MessageResult();
        expected.setOk(true);
        when(restBotAPI.editMessageLiveLocation(anyString(), any(EditMessageLiveLocationMessage.class))).thenReturn(expected);

        EditMessageLiveLocationMessage msg = new EditMessageLiveLocationMessage(latitude, longitude);
        MessageResult actual = (MessageResult) service.sendMessage("mock-token", msg);

        assertEquals(expected, actual);
    }

    @Test
    public void testStopMessageLiveLocation() {
        MessageResult expected = new MessageResult();
        expected.setOk(true);
        when(restBotAPI.stopMessageLiveLocation(anyString(), any(StopMessageLiveLocationMessage.class))).thenReturn(expected);

        StopMessageLiveLocationMessage msg = new StopMessageLiveLocationMessage();
        MessageResult actual = (MessageResult) service.sendMessage("mock-token", msg);

        assertEquals(expected, actual);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:telegram").to("telegram:bots/mock-token?chatId=my-id");
            }
        };
    }
}
