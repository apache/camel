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

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.telegram.model.EditMessageLiveLocationMessage;
import org.apache.camel.component.telegram.model.MessageResult;
import org.apache.camel.component.telegram.model.SendLocationMessage;
import org.apache.camel.component.telegram.model.SendVenueMessage;
import org.apache.camel.component.telegram.model.StopMessageLiveLocationMessage;
import org.apache.camel.component.telegram.util.TelegramMockRoutes;
import org.apache.camel.component.telegram.util.TelegramMockRoutes.MockProcessor;
import org.apache.camel.component.telegram.util.TelegramTestSupport;
import org.apache.camel.component.telegram.util.TelegramTestUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests a producer that sends location information.
 */
public class TelegramProducerLocationTest extends TelegramTestSupport {

    private final double latitude = 59.9386292;
    private final double longitude = 30.3141308;

    @Test
    public void testSendLocation() {
        SendLocationMessage msg = new SendLocationMessage(latitude, longitude);
        template.requestBody("direct:telegram", msg, MessageResult.class);

        final MockProcessor<SendLocationMessage> mockProcessor = getMockRoutes().getMock("sendLocation");
        assertThat(mockProcessor.awaitRecordedMessages(1, 5000).get(0))
                .usingRecursiveComparison()
                .isEqualTo(msg);
    }

    @Test
    public void testSendVenue() {
        SendVenueMessage msg = new SendVenueMessage(latitude, longitude, "title", "address");
        template.requestBody("direct:telegram", msg, MessageResult.class);

        final MockProcessor<SendVenueMessage> mockProcessor = getMockRoutes().getMock("sendVenue");
        assertThat(mockProcessor.awaitRecordedMessages(1, 5000).get(0))
                .usingRecursiveComparison()
                .isEqualTo(msg);
    }

    @Test
    public void testEditMessageLiveLocation() {
        EditMessageLiveLocationMessage msg = new EditMessageLiveLocationMessage(latitude, longitude);
        template.requestBody("direct:telegram", msg, MessageResult.class);

        final MockProcessor<EditMessageLiveLocationMessage> mockProcessor = getMockRoutes().getMock("editMessageLiveLocation");
        assertThat(mockProcessor.awaitRecordedMessages(1, 5000).get(0))
                .usingRecursiveComparison()
                .isEqualTo(msg);
    }

    @Test
    public void testStopMessageLiveLocation() {
        StopMessageLiveLocationMessage msg = new StopMessageLiveLocationMessage();
        template.requestBody("direct:telegram", msg, MessageResult.class);

        final MockProcessor<StopMessageLiveLocationMessage> mockProcessor = getMockRoutes().getMock("stopMessageLiveLocation");
        assertThat(mockProcessor.awaitRecordedMessages(1, 5000).get(0))
                .usingRecursiveComparison()
                .isEqualTo(msg);
    }

    @Override
    protected RoutesBuilder[] createRouteBuilders() throws Exception {
        return new RoutesBuilder[] {
            getMockRoutes(),
            new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:telegram").to("telegram:bots?authorizationToken=mock-token&chatId=" + chatId);
                }
            }};
    }

    @Override
    protected TelegramMockRoutes createMockRoutes() {
        return new TelegramMockRoutes(port)
                .addEndpoint(
                        "sendLocation",
                        "POST",
                        SendLocationMessage.class,
                        TelegramTestUtil.stringResource("messages/send-location.json"))
                .addEndpoint(
                        "sendVenue",
                        "POST",
                        SendVenueMessage.class,
                        TelegramTestUtil.stringResource("messages/send-venue.json"))
                .addEndpoint(
                        "editMessageLiveLocation",
                        "POST",
                        EditMessageLiveLocationMessage.class,
                        TelegramTestUtil.stringResource("messages/edit-message-live-location.json"))
                .addEndpoint(
                        "stopMessageLiveLocation",
                        "POST",
                        StopMessageLiveLocationMessage.class,
                        TelegramTestUtil.stringResource("messages/stop-message-live-location.json"));
    }

}
