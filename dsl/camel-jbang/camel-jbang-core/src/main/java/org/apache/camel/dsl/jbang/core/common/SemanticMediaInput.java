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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/** Media input shared by the CLI and TUI; independent of expert implementations. */
public final class SemanticMediaInput {
    private static final String PARTS = "$semanticInput";

    private SemanticMediaInput() {
    }

    public static boolean isMedia(Object input) {
        return input instanceof Map<?, ?> map && map.get(PARTS) instanceof List<?>;
    }

    public static List<?> parts(Object input) {
        return isMedia(input) ? (List<?>) ((Map<?, ?>) input).get(PARTS) : List.of();
    }

    public static boolean hasImages(Object input) {
        return input instanceof String text && text.startsWith("data:image/")
                || parts(input).stream().anyMatch(item -> item instanceof Map<?, ?> part && "image".equals(part.get("type")));
    }

    public static boolean hasImages(Object body, Object headers) {
        return hasImages(body) || body instanceof String && hasImageContentType(headers);
    }

    public static void validateImageSize(Object body, Object headers, int maxBytes) {
        if (body instanceof String text && !text.startsWith("data:image/") && hasImageContentType(headers)) {
            validateBase64Size(text, 0, maxBytes);
        } else {
            validateImageSize(body, maxBytes);
        }
    }

    private static boolean hasImageContentType(Object headers) {
        return headers instanceof Map<?, ?> values && values.entrySet().stream()
                .anyMatch(entry -> "Content-Type".equalsIgnoreCase(entry.getKey().toString())
                        && entry.getValue() instanceof String type
                        && type.trim().toLowerCase(Locale.ROOT).startsWith("image/"));
    }

    /** Check encoded images before sending a tooling request, without decoding their payloads. */
    public static void validateImageSize(Object input, int maxBytes) {
        if (input instanceof String data && data.startsWith("data:image/")) {
            int offset = imageDataOffset(data);
            if (offset >= 0) {
                validateBase64Size(data, offset, maxBytes);
            }
        } else {
            for (Object item : parts(input)) {
                if (item instanceof Map<?, ?> part && "image".equals(part.get("type"))) {
                    validateImageSize(part.get("data"), maxBytes);
                }
            }
        }
    }

    private static void validateBase64Size(String data, int offset, int maxBytes) {
        int padding = data.endsWith("==") ? 2 : data.endsWith("=") ? 1 : 0;
        if ((data.length() - offset) * 3L / 4 - padding > maxBytes) {
            throw new IllegalArgumentException("Image exceeds the expert maxImageBytes limit of " + maxBytes + " bytes");
        }
    }

    /** Read only an explicitly requested local image. This must run off the TUI render thread. */
    public static Object readImage(String value, int maxBytes) {
        if (!value.startsWith("image:")) {
            return value;
        }
        Path path = Path.of(value.substring(6));
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        String type = name.endsWith(".png") ? "image/png"
                : name.endsWith(".jpg") || name.endsWith(".jpeg") ? "image/jpeg"
                : name.endsWith(".gif") ? "image/gif" : name.endsWith(".webp") ? "image/webp" : null;
        if (type == null) {
            throw new IllegalArgumentException("image: requires a local PNG, JPEG, GIF or WebP file");
        }
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("The selected expert must declare a positive maxImageBytes limit");
        }
        String tooLarge = "Image file exceeds the expert maxImageBytes limit of " + maxBytes + " bytes";
        try {
            if (Files.size(path) > maxBytes) {
                throw new IllegalArgumentException(tooLarge);
            }
            byte[] bytes;
            try (InputStream input = Files.newInputStream(path)) {
                // Bound the read too: a file can grow after the size check.
                bytes = input.readNBytes(maxBytes);
                if (input.read() != -1) {
                    throw new IllegalArgumentException(tooLarge);
                }
            }
            String data = "data:" + type + ";base64," + Base64.getEncoder().encodeToString(bytes);
            return Map.of(PARTS, List.of(Map.of("type", "image", "data", data)));
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read the selected image file", e);
        }
    }

    /** Read the configured limit advertised by an image-capable expert operation. */
    public static int imageMaxBytes(Map<?, ?> operation) {
        if (operation != null && operation.get("contract") instanceof Map<?, ?> contract
                && contract.get("inputTypes") instanceof List<?> types && types.contains("image")
                && contract.get("maxImageBytes") instanceof Number limit
                && limit.longValue() > 0 && limit.longValue() <= Integer.MAX_VALUE
                && limit.doubleValue() == limit.longValue()) {
            return limit.intValue();
        }
        throw new IllegalArgumentException(
                "The selected expert must advertise image support and a positive maxImageBytes limit");
    }

    /** Preserve metadata in terminal/JSON displays without printing image payloads. */
    public static Object withoutImageData(Object value) {
        if (value instanceof Map<?, ?> map) {
            if (isMedia(map)) {
                JsonArray parts = new JsonArray();
                for (Object item : parts(map)) {
                    parts.add(item instanceof Map<?, ?> part && "image".equals(part.get("type"))
                            ? imageDescription(part) : withoutImageData(item));
                }
                JsonObject result = new JsonObject();
                result.put(PARTS, parts);
                return result;
            }
            JsonObject result = new JsonObject();
            map.forEach((key, item) -> result.put(key.toString(), withoutImageData(item)));
            return result;
        }
        if (value instanceof List<?> list) {
            JsonArray result = new JsonArray();
            list.forEach(item -> result.add(withoutImageData(item)));
            return result;
        }
        if (value instanceof String text && text.startsWith("data:image/")) {
            return imageDescription(Map.of("type", "image", "data", text));
        }
        return value;
    }

    public static JsonObject imageDescription(Map<?, ?> part) {
        JsonObject result = new JsonObject();
        result.put("type", "image");
        if (part.get("data") instanceof String data) {
            int start = imageDataOffset(data);
            if (start >= 0) {
                result.put("mediaType", data.substring(5, start - 8));
                result.put("encodedCharacters", data.length() - start);
            }
            result.put("capture", "available");
            result.put("dataOmitted", "display");
        } else {
            result.put("capture", "omitted");
            result.put("dataOmitted", part.getOrDefault("dataOmitted", null));
            if (part.get("mediaType") instanceof String type && type.matches("image/[a-z0-9.+-]{1,100}")) {
                result.put("mediaType", type);
            }
        }
        return result;
    }

    /** Offset of the base64 payload, or -1 when the inline image prefix is invalid. */
    static int imageDataOffset(String data) {
        int marker = data.indexOf(";base64,");
        return data.startsWith("data:image/") && marker > 5 && marker < 120
                && data.substring(5, marker).matches("image/[a-z0-9.+-]+") ? marker + 8 : -1;
    }

    public static String typeLabel(Object type) {
        return switch (type instanceof String text ? text : "") {
            case "image" -> "Image";
            case "text-and-images" -> "Text + images";
            case "text" -> "Text";
            case "structured" -> "Structured";
            case "unsupported" -> "Unsupported input";
            case "unknown" -> "Not determined";
            default -> "Not recorded";
        };
    }
}
