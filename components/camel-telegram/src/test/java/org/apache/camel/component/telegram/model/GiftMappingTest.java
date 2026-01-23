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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the JSON mapping of Gift and GiftBackground models.
 */
public class GiftMappingTest {

    @Test
    public void testGiftWithAllFieldsMapping() {
        Gift gift = TelegramTestSupport.getJSONResource("messages/gift.json", Gift.class);

        assertNotNull(gift);
        assertEquals("gift_12345", gift.getId());
        assertEquals(Integer.valueOf(100), gift.getStarCount());
        assertEquals(Integer.valueOf(500), gift.getUpgradeStarCount());
        assertTrue(gift.getIsPremium());
        assertTrue(gift.getHasColors());
        assertEquals(Integer.valueOf(1000), gift.getTotalCount());
        assertEquals(Integer.valueOf(750), gift.getRemainingCount());
        assertEquals(Integer.valueOf(50), gift.getPersonalTotalCount());
        assertEquals(Integer.valueOf(45), gift.getPersonalRemainingCount());
        assertEquals(Integer.valueOf(10), gift.getUniqueGiftVariantCount());

        // Verify sticker
        IncomingSticker sticker = gift.getSticker();
        assertNotNull(sticker);
        assertEquals("CAACAgIAAxkBAAIBZ2XXXXXX", sticker.getFileId());
        assertEquals("AgADXXXXXXXX", sticker.getFileUniqueId());
        assertEquals(Integer.valueOf(512), sticker.getWidth());
        assertEquals(Integer.valueOf(512), sticker.getHeight());
        assertEquals("regular", sticker.getType());

        // Verify background
        GiftBackground background = gift.getBackground();
        assertNotNull(background);
        assertEquals(Integer.valueOf(16711680), background.getCenterColor()); // Red (0xFF0000)
        assertEquals(Integer.valueOf(255), background.getEdgeColor()); // Blue (0x0000FF)
        assertEquals(Integer.valueOf(16777215), background.getTextColor()); // White (0xFFFFFF)

        // Verify publisher chat
        Chat publisherChat = gift.getPublisherChat();
        assertNotNull(publisherChat);
        assertEquals("-1001234567890", publisherChat.getId());
        assertEquals("Gift Shop", publisherChat.getTitle());
        assertEquals("channel", publisherChat.getType());
    }

    @Test
    public void testSimpleGiftMapping() {
        Gift gift = TelegramTestSupport.getJSONResource("messages/gift-simple.json", Gift.class);

        assertNotNull(gift);
        assertEquals("gift_simple_001", gift.getId());
        assertEquals(Integer.valueOf(50), gift.getStarCount());

        // Verify sticker exists
        assertNotNull(gift.getSticker());
        assertEquals("CAACAgIAAxkBAAISimpleGift", gift.getSticker().getFileId());

        // Optional fields should be null
        assertNull(gift.getUpgradeStarCount());
        assertNull(gift.getIsPremium());
        assertNull(gift.getHasColors());
        assertNull(gift.getTotalCount());
        assertNull(gift.getRemainingCount());
        assertNull(gift.getPersonalTotalCount());
        assertNull(gift.getPersonalRemainingCount());
        assertNull(gift.getBackground());
        assertNull(gift.getUniqueGiftVariantCount());
        assertNull(gift.getPublisherChat());
    }

    @Test
    public void testGiftBackgroundMapping() {
        Gift gift = TelegramTestSupport.getJSONResource("messages/gift.json", Gift.class);
        GiftBackground background = gift.getBackground();

        assertNotNull(background);

        // Test RGB color values
        // Red: 16711680 = 0xFF0000
        assertEquals(Integer.valueOf(16711680), background.getCenterColor());

        // Blue: 255 = 0x0000FF
        assertEquals(Integer.valueOf(255), background.getEdgeColor());

        // White: 16777215 = 0xFFFFFF
        assertEquals(Integer.valueOf(16777215), background.getTextColor());
    }
}
