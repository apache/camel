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

package org.apache.camel.dsl.jbang.core.common;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

class SemanticMediaInputTest {
    @TempDir
    Path directory;

    @Test
    void imageFilesBecomeExplicitMediaAndDisplaysNeverContainBase64() throws Exception {
        var image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0xff0000);
        Path path = directory.resolve("sample.png");
        ImageIO.write(image, "png", path.toFile());
        Object input = SemanticMediaInput.readImage("image:" + path, 10_000_000);
        assertTrue(SemanticMediaInput.isMedia(input));
        var description = SemanticMediaInput.withoutImageData(input).toString();
        assertTrue(description.contains("image/png"));
        assertTrue(description.contains("available"));
        assertFalse(description.contains("base64"));
        var previews = SemanticImagePreview.prepare(input);
        assertEquals(1, previews.size());
        assertEquals(0xff0000, previews.get(0).pixels().get(0).get(0));
        assertTrue(previews.get(0).description().contains("4 × 4"));
        assertFalse(previews.get(0).textLines().toString().contains("base64"));
    }

    @Test
    void previewIsBoundedAndCorruptOrOmittedImagesAreExplicit() throws Exception {
        var image = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        var buffer = new ByteArrayOutputStream();
        ImageIO.write(image, "png", buffer);
        String data = "data:image/png;base64," + Base64.getEncoder().encodeToString(buffer.toByteArray());
        var input = Map.of("$semanticInput", List.of(Map.of("type", "image", "data", data),
                Map.of("type", "image", "data", "data:image/png;base64,broken"),
                Map.of("type", "image", "dataOmitted", "snapshot_limit", "mediaType", "image/png")));
        var previews = SemanticImagePreview.prepare(input);
        assertTrue(previews.get(0).pixels().get(0).size() <= 64);
        assertTrue(previews.get(0).pixels().size() <= 64);
        assertTrue(previews.get(1).pixels().isEmpty());
        assertTrue(previews.get(1).description().contains("unavailable"));
        assertTrue(previews.get(2).description().contains("not retained"));
        assertEquals("Text + images", SemanticMediaInput.typeLabel("text-and-images"));
    }

    @Test
    void transparentPixelsAreCompositedOnWhite() throws Exception {
        var image = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0x00ff0000);
        image.setRGB(1, 0, 0x80000000);
        var buffer = new ByteArrayOutputStream();
        ImageIO.write(image, "png", buffer);
        String data = "data:image/png;base64," + Base64.getEncoder().encodeToString(buffer.toByteArray());
        var input = Map.of("$semanticInput", List.of(Map.of("type", "image", "data", data)));
        var preview = SemanticImagePreview.prepare(input).get(0);
        assertEquals(List.of(0xffffff, 0x7f7f7f), preview.pixels().get(0));
    }

    @Test
    void expertLimitIncludesTheBoundaryAndRejectsLargerFiles() throws Exception {
        Path path = directory.resolve("sample.png");
        Files.write(path, new byte[] { 1, 2, 3 });
        assertTrue(SemanticMediaInput.isMedia(SemanticMediaInput.readImage("image:" + path, 3)));
        var failure = assertThrows(IllegalArgumentException.class, () -> SemanticMediaInput.readImage("image:" + path, 2));
        assertTrue(failure.getMessage().contains("expert maxImageBytes limit of 2 bytes"));
        assertThrows(IllegalArgumentException.class, () -> SemanticMediaInput.readImage("image:" + path, 0));
    }

    @Test
    void readRemainsBoundedWhenTheFileGrowsAfterTheSizeCheck() throws Exception {
        Path path = directory.resolve("growing.png");
        Files.write(path, new byte[] { 1, 2, 3, 4 });
        try (var files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.size(path)).thenReturn(3L);
            assertThrows(IllegalArgumentException.class, () -> SemanticMediaInput.readImage("image:" + path, 3));
        }
    }

    @Test
    void usesOnlyAdvertisedImageLimitsAndChecksEncodedImages() {
        assertEquals(3, SemanticMediaInput
                .imageMaxBytes(Map.of("contract", Map.of("inputTypes", List.of("image"), "maxImageBytes", 3))));
        for (Object limit : List.of(0, -1, 1.5, Long.MAX_VALUE, "3")) {
            assertThrows(IllegalArgumentException.class, () -> SemanticMediaInput.imageMaxBytes(
                    Map.of("contract", Map.of("inputTypes", List.of("image"), "maxImageBytes", limit))));
        }
        assertThrows(IllegalArgumentException.class, () -> SemanticMediaInput.imageMaxBytes(Map.of()));
        Object input = Map.of("$semanticInput", List.of(Map.of("type", "image", "data", "data:image/png;base64,AQID")));
        assertTrue(SemanticMediaInput.hasImages(input));
        SemanticMediaInput.validateImageSize(input, 3);
        assertThrows(IllegalArgumentException.class, () -> SemanticMediaInput.validateImageSize(input, 2));
    }

    @Test
    void ordinaryStateAndTextKeepTheirMeaning() {
        Object input = Map.of("type", "image", "data", "application state");
        assertEquals(input, SemanticMediaInput.withoutImageData(input));
        assertEquals("https://example.com/picture.png",
                SemanticMediaInput.readImage("https://example.com/picture.png", 10_000_000));
    }
}
