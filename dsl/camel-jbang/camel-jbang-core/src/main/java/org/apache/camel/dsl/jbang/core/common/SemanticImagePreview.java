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
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

/** Bounded local thumbnail decoding. Never fetches a URL, opens a viewer, or writes image data to disk. */
public record SemanticImagePreview(String description, List<List<Integer>> pixels) {
    public SemanticImagePreview {
        pixels = pixels.stream().map(List::copyOf).toList();
    }

    public static List<SemanticImagePreview> prepare(Object input) {
        List<SemanticImagePreview> previews = new ArrayList<>();
        for (Object item : SemanticMediaInput.parts(input)) {
            if (item instanceof Map<?, ?> part && "image".equals(part.get("type"))) {
                previews.add(decode(part));
            }
        }
        return List.copyOf(previews);
    }

    private static SemanticImagePreview decode(Map<?, ?> part) {
        var metadata = SemanticMediaInput.imageDescription(part);
        String label = "Image (" + metadata.getOrDefault("mediaType", "format not recorded") + ")";
        if (!(part.get("data") instanceof String data)) {
            return unavailable(label, "payload was not retained");
        }
        int start = SemanticMediaInput.imageDataOffset(data);
        if (start < 0 || data.length() > 1_000_000) {
            return unavailable(label, "invalid image or preview byte limit");
        }
        try (var stream = new MemoryCacheImageInputStream(
                new ByteArrayInputStream(Base64.getDecoder().decode(data.substring(start))))) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                return unavailable(label, "no local decoder for this image");
            }
            var reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > 40_000_000) {
                    return unavailable(label, "preview pixel limit");
                }
                var parameters = reader.getDefaultReadParam();
                int sample = Math.max(1, (Math.max(width, height) + 63) / 64);
                parameters.setSourceSubsampling(sample, sample, 0, 0);
                BufferedImage image = reader.read(0, parameters);
                List<List<Integer>> rows = new ArrayList<>();
                for (int y = 0; y < image.getHeight(); y++) {
                    List<Integer> row = new ArrayList<>();
                    for (int x = 0; x < image.getWidth(); x++) {
                        row.add(onWhite(image.getRGB(x, y)));
                    }
                    rows.add(row);
                }
                return new SemanticImagePreview(label + " · " + width + " × " + height + " · terminal preview", rows);
            } finally {
                reader.dispose();
            }
        } catch (Exception failure) {
            return unavailable(label, "image could not be decoded");
        }
    }

    private static SemanticImagePreview unavailable(String label, String reason) {
        return new SemanticImagePreview(label + " · preview unavailable: " + reason, List.of());
    }

    private static int onWhite(int argb) {
        int alpha = argb >>> 24;
        int red = ((argb >> 16 & 255) * alpha + 255 * (255 - alpha)) / 255;
        int green = ((argb >> 8 & 255) * alpha + 255 * (255 - alpha)) / 255;
        int blue = ((argb & 255) * alpha + 255 * (255 - alpha)) / 255;
        return red << 16 | green << 8 | blue;
    }

    /** Plain terminal fallback; the TUI renders the same pixels with foreground/background colours. */
    public List<String> textLines() {
        List<String> lines = new ArrayList<>();
        lines.add(description);
        for (int y = 0; y < pixels.size(); y += 2) {
            StringBuilder line = new StringBuilder();
            for (int rgb : pixels.get(y)) {
                int luminance = ((rgb >> 16 & 255) * 299 + (rgb >> 8 & 255) * 587 + (rgb & 255) * 114) / 1000;
                line.append(" .:-=+*#%@".charAt(luminance * 9 / 255));
            }
            lines.add(line.toString());
        }
        return List.copyOf(lines);
    }
}
