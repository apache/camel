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
import org.apache.camel.component.telegram.model.IncomingMaskPosition;
import org.apache.camel.component.telegram.model.IncomingMessage;
import org.apache.camel.component.telegram.model.IncomingPhotoSize;
import org.apache.camel.component.telegram.model.IncomingSticker;
import org.apache.camel.component.telegram.util.TelegramMockRoutes;
import org.apache.camel.component.telegram.util.TelegramTestSupport;
import org.apache.camel.component.telegram.util.TelegramTestUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TelegramConsumerMediaStickerTest extends TelegramTestSupport {

    @EndpointInject("mock:telegram")
    private MockEndpoint endpoint;

    @Test
    public void testReceptionOfAMessageWithASticker() throws Exception {
        endpoint.expectedMinimumMessageCount(1);
        endpoint.assertIsSatisfied(5000);

        Exchange mediaExchange = endpoint.getExchanges().get(0);
        IncomingMessage msg = mediaExchange.getIn().getBody(IncomingMessage.class);

        IncomingSticker sticker = msg.getSticker();

        assertNotNull(sticker);
        assertEquals(Integer.valueOf(512), sticker.getHeight());
        assertEquals(Integer.valueOf(512), sticker.getWidth());
        assertEquals("\ud83d\ude28", sticker.getEmoji());
        assertEquals("CAADBAADEQADmDVxAkmg3XnDZam0FgQ", sticker.getFileId());
        assertEquals("GreatMindsColor", sticker.getSetName());
        assertFalse(sticker.getAnimated());

        IncomingMaskPosition incomingMaskPosition = sticker.getMaskPosition();
        assertEquals("forehead", incomingMaskPosition.getPoint());
        assertEquals(2.0f, incomingMaskPosition.getScale());
        assertEquals(-1.0f, incomingMaskPosition.getxShift());
        assertEquals(1.0f, incomingMaskPosition.getyShift());

        IncomingPhotoSize thumb = sticker.getThumb();
        assertNotNull(thumb);

    }

    @Override
    protected RoutesBuilder[] createRouteBuilders() throws Exception {
        return new RoutesBuilder[] {
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
                TelegramTestUtil.stringResource("messages/updates-media-sticker.json"),
                TelegramTestUtil.stringResource("messages/updates-empty.json"));
    }
}
