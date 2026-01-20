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
package org.apache.camel.component.telegram.model;

import org.apache.camel.component.telegram.util.TelegramTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests the JSON mapping of PaidMedia polymorphic types.
 */
public class PaidMediaMappingTest {

    @Test
    public void testPaidMediaPreviewMapping() {
        PaidMedia paidMedia = TelegramTestSupport.getJSONResource("messages/paid-media-preview.json", PaidMedia.class);

        assertNotNull(paidMedia);
        assertInstanceOf(PaidMediaPreview.class, paidMedia);
        assertEquals("preview", paidMedia.getType());

        PaidMediaPreview preview = paidMedia.asPreview();
        assertNotNull(preview);
        assertEquals(Integer.valueOf(1920), preview.getWidth());
        assertEquals(Integer.valueOf(1080), preview.getHeight());
        assertEquals(Integer.valueOf(120), preview.getDuration());

        // Verify convenience methods return null for wrong types
        assertNull(paidMedia.asPhoto());
        assertNull(paidMedia.asVideo());
    }

    @Test
    public void testPaidMediaPhotoMapping() {
        PaidMedia paidMedia = TelegramTestSupport.getJSONResource("messages/paid-media-photo.json", PaidMedia.class);

        assertNotNull(paidMedia);
        assertInstanceOf(PaidMediaPhoto.class, paidMedia);
        assertEquals("photo", paidMedia.getType());

        PaidMediaPhoto photo = paidMedia.asPhoto();
        assertNotNull(photo);
        assertNotNull(photo.getPhoto());
        assertEquals(2, photo.getPhoto().size());

        IncomingPhotoSize smallPhoto = photo.getPhoto().get(0);
        assertEquals("AgACAgIAAx0CXX1XXXXXAQI", smallPhoto.getFileId());
        assertEquals(Integer.valueOf(320), smallPhoto.getWidth());
        assertEquals(Integer.valueOf(240), smallPhoto.getHeight());
        assertEquals(Long.valueOf(15234), smallPhoto.getFileSize());

        IncomingPhotoSize largePhoto = photo.getPhoto().get(1);
        assertEquals("AgACAgIAAx0CXX1XXXXXAQJ", largePhoto.getFileId());
        assertEquals(Integer.valueOf(800), largePhoto.getWidth());
        assertEquals(Integer.valueOf(600), largePhoto.getHeight());

        // Verify convenience methods return null for wrong types
        assertNull(paidMedia.asPreview());
        assertNull(paidMedia.asVideo());
    }

    @Test
    public void testPaidMediaVideoMapping() {
        PaidMedia paidMedia = TelegramTestSupport.getJSONResource("messages/paid-media-video.json", PaidMedia.class);

        assertNotNull(paidMedia);
        assertInstanceOf(PaidMediaVideo.class, paidMedia);
        assertEquals("video", paidMedia.getType());

        PaidMediaVideo videoMedia = paidMedia.asVideo();
        assertNotNull(videoMedia);

        IncomingVideo video = videoMedia.getVideo();
        assertNotNull(video);
        assertEquals("BAADBAADAgADyzvwCC7_premium_video", video.getFileId());
        assertEquals("AgADyzvwCC7_unique", video.getFileUniqueId());
        assertEquals(Integer.valueOf(1920), video.getWidth());
        assertEquals(Integer.valueOf(1080), video.getHeight());
        assertEquals(Integer.valueOf(180), video.getDurationSeconds());
        assertEquals("premium_video.mp4", video.getFileName());
        assertEquals("video/mp4", video.getMimeType());
        assertEquals(Long.valueOf(52428800), video.getFileSize());

        // Check thumbnail
        IncomingPhotoSize thumbnail = video.getThumbnail();
        assertNotNull(thumbnail);
        assertEquals("AAQEABMlaUQZAAT1video_thumb", thumbnail.getFileId());
        assertEquals(Integer.valueOf(320), thumbnail.getWidth());
        assertEquals(Integer.valueOf(180), thumbnail.getHeight());

        // Verify convenience methods return null for wrong types
        assertNull(paidMedia.asPreview());
        assertNull(paidMedia.asPhoto());
    }
}
