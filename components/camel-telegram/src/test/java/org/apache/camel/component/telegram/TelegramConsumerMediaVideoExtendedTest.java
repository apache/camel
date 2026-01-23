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
import org.apache.camel.component.telegram.model.IncomingMessage;
import org.apache.camel.component.telegram.model.IncomingPhotoSize;
import org.apache.camel.component.telegram.model.IncomingVideo;
import org.apache.camel.component.telegram.util.TelegramMockRoutes;
import org.apache.camel.component.telegram.util.TelegramTestSupport;
import org.apache.camel.component.telegram.util.TelegramTestUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests the reception of video messages with extended fields (fileUniqueId, thumbnail, cover, startTimestamp,
 * fileName).
 */
public class TelegramConsumerMediaVideoExtendedTest extends TelegramTestSupport {

    @EndpointInject("mock:telegram")
    private MockEndpoint endpoint;

    @Test
    public void testReceptionOfVideoWithExtendedFields() throws Exception {
        endpoint.expectedMinimumMessageCount(1);
        endpoint.assertIsSatisfied(5000);

        Exchange mediaExchange = endpoint.getExchanges().get(0);
        IncomingMessage msg = mediaExchange.getIn().getBody(IncomingMessage.class);

        IncomingVideo video = msg.getVideo();
        assertNotNull(video);

        // Basic fields
        assertEquals("BAADBAADAgADyzvwCC7_extended_video", video.getFileId());
        assertEquals(Integer.valueOf(1920), video.getWidth());
        assertEquals(Integer.valueOf(1080), video.getHeight());
        assertEquals(Integer.valueOf(300), video.getDurationSeconds());
        assertEquals(Long.valueOf(104857600), video.getFileSize());
        assertEquals("video/mp4", video.getMimeType());

        // Extended fields
        assertEquals("AgADyzvwCC7_extended_unique", video.getFileUniqueId());
        assertEquals("extended_video.mp4", video.getFileName());
        assertEquals(Integer.valueOf(15), video.getStartTimestamp());

        // Verify old thumb field (for backward compatibility)
        IncomingPhotoSize thumb = video.getThumb();
        assertNotNull(thumb);
        assertEquals("AAQEABMlaUQZAAT1thumb_old", thumb.getFileId());

        // Verify new thumbnail field
        IncomingPhotoSize thumbnail = video.getThumbnail();
        assertNotNull(thumbnail);
        assertEquals("AAQEABMlaUQZAAT1thumb_new", thumbnail.getFileId());
        assertEquals("thumb_new_unique", thumbnail.getFileUniqueId());
        assertEquals(Integer.valueOf(320), thumbnail.getWidth());
        assertEquals(Integer.valueOf(180), thumbnail.getHeight());

        // Verify cover field (album/carousel thumbnails)
        assertNotNull(video.getCover());
        assertEquals(2, video.getCover().size());

        IncomingPhotoSize smallCover = video.getCover().get(0);
        assertEquals("cover_small_id", smallCover.getFileId());
        assertEquals("cover_small_unique", smallCover.getFileUniqueId());
        assertEquals(Integer.valueOf(160), smallCover.getWidth());
        assertEquals(Integer.valueOf(90), smallCover.getHeight());

        IncomingPhotoSize largeCover = video.getCover().get(1);
        assertEquals("cover_large_id", largeCover.getFileId());
        assertEquals("cover_large_unique", largeCover.getFileUniqueId());
        assertEquals(Integer.valueOf(640), largeCover.getWidth());
        assertEquals(Integer.valueOf(360), largeCover.getHeight());
    }

    @Override
    protected RoutesBuilder[] createRouteBuilders() {
        return new RoutesBuilder[] {
                getMockRoutes(),
                new RouteBuilder() {
                    @Override
                    public void configure() {
                        from("telegram:bots?authorizationToken=mock-token")
                                .to("mock:telegram");
                    }
                } };
    }

    @Override
    protected TelegramMockRoutes createMockRoutes() {
        return new TelegramMockRoutes(port)
                .addEndpoint(
                        "getUpdates",
                        "GET",
                        String.class,
                        TelegramTestUtil.stringResource("messages/updates-media-video-extended.json"),
                        TelegramTestUtil.stringResource("messages/updates-empty.json"));
    }
}
