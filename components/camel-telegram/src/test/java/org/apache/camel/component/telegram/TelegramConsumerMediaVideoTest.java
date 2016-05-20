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

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.telegram.model.IncomingMessage;
import org.apache.camel.component.telegram.model.IncomingPhotoSize;
import org.apache.camel.component.telegram.model.IncomingVideo;
import org.apache.camel.component.telegram.model.UpdateResult;
import org.apache.camel.component.telegram.util.TelegramTestSupport;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

/**
 * Tests the reception of messages without text having media content.
 */
public class TelegramConsumerMediaVideoTest extends TelegramTestSupport {

    @EndpointInject(uri = "mock:telegram")
    private MockEndpoint endpoint;

    @Before
    public void mockAPIs() {
        TelegramService api = mockTelegramService();

        UpdateResult res = getJSONResource("messages/updates-media-video.json", UpdateResult.class);

        UpdateResult defaultRes = getJSONResource("messages/updates-empty.json", UpdateResult.class);

        when(api.getUpdates(any(), any(), any(), any()))
                .thenReturn(res)
                .thenAnswer((i) -> defaultRes);
    }

    @Test
    public void testReceptionOfAMessageWithAVideo() throws Exception {
        endpoint.expectedMinimumMessageCount(1);
        endpoint.assertIsSatisfied();

        Exchange mediaExchange = endpoint.getExchanges().get(0);
        IncomingMessage msg = mediaExchange.getIn().getBody(IncomingMessage.class);

        IncomingVideo video = msg.getVideo();

        assertNotNull(video);
        assertEquals(Integer.valueOf(2), video.getDurationSeconds());
        assertEquals(Integer.valueOf(360), video.getHeight());
        assertEquals(Integer.valueOf(640), video.getWidth());
        assertEquals(Long.valueOf(299284), video.getFileSize());
        assertEquals("BAADBAADAgADyzvwCC7_4AyvdAXXXX", video.getFileId());

        IncomingPhotoSize thumb = video.getThumb();
        assertNotNull(thumb);

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
