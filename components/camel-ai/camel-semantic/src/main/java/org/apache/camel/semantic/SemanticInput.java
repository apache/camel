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
package org.apache.camel.semantic;

import java.io.InputStream;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.StreamCache;
import org.apache.camel.semantic.SemanticExpert.InputType;

/**
 * Explicit, JSON-compatible input envelope for ordered text and image parts. The reserved {@value #PARTS} key
 * distinguishes media from ordinary application state. Values are data: no file or URL is resolved by this class.
 */
public final class SemanticInput {
    public static final String PARTS = "$semanticInput";

    private SemanticInput() {
    }

    public static Map<String, Object> ofParts(List<Map<String, Object>> parts) {
        Map<String, Object> input = Map.of(PARTS, parts.stream().map(Map::copyOf).toList());
        validate(input, Set.of(InputType.TEXT, InputType.IMAGE));
        return input;
    }

    public static Map<String, Object> text(String text) {
        return Map.of("type", "text", "text", text);
    }

    /** An image data URL, with an optional expert-specific detail setting supplied separately in the part. */
    public static Map<String, Object> image(String data) {
        return Map.of("type", "image", "data", data);
    }

    /** Binary image part with an explicit MIME type. No image interpretation or inference is performed. */
    public static Map<String, Object> image(byte[] data, String mediaType) {
        return image("data:" + mediaType + ";base64," + Base64.getEncoder().encodeToString(data));
    }

    /** Normalize a self-describing image string; ordinary strings remain text. */
    public static Object normalize(Object input) {
        return input instanceof String text && text.startsWith("data:image/")
                ? ofParts(List.of(image(text))) : input;
    }

    /**
     * Select an image body using Content-Type. Streams become replayable Camel stream caches before reading, so later
     * route steps can still consume the body. Headers do not affect explicitly selected headers or variables.
     */
    public static Object body(Exchange exchange) throws NoTypeConversionAvailableException {
        Object input = exchange.getMessage().getBody();
        String contentType = exchange.getMessage().getHeader(Exchange.CONTENT_TYPE, String.class);
        String mediaType = contentType == null ? "" : contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (mediaType.startsWith("audio/") || mediaType.startsWith("video/")) {
            throw new IllegalArgumentException("Audio and video bodies are not supported by the selected input contract");
        }
        if (!mediaType.startsWith("image/")) {
            return normalize(input);
        }
        if (!mediaType.matches("image/[a-z0-9.+-]{1,100}")) {
            throw new IllegalArgumentException("Invalid image Content-Type");
        }
        if (input instanceof InputStream && !(input instanceof StreamCache)) {
            input = exchange.getContext().getTypeConverter().mandatoryConvertTo(StreamCache.class, exchange, input);
            exchange.getMessage().setBody(input);
        }
        if (input instanceof StreamCache cache) {
            try {
                cache.reset();
                byte[] data = exchange.getContext().getTypeConverter().mandatoryConvertTo(byte[].class, exchange, cache);
                return ofParts(List.of(image(data, mediaType)));
            } finally {
                cache.reset();
            }
        }
        if (input instanceof byte[] data) {
            return ofParts(List.of(image(data, mediaType)));
        }
        if (input instanceof String text) {
            return text.startsWith("data:image/")
                    ? normalize(text)
                    : ofParts(List.of(image("data:" + mediaType + ";base64," + text)));
        }
        if (isMedia(input)) {
            return input;
        }
        throw new IllegalArgumentException("An image body requires bytes, an InputStream, or a base64 string");
    }

    public static boolean isMedia(Object input) {
        return input instanceof Map<?, ?> map && map.containsKey(PARTS);
    }

    /** Read an explicit envelope. Validate against the selected expert before using its parts. */
    public static List<?> parts(Object input) {
        if (!(input instanceof Map<?, ?> map) || map.size() != 1 || !(map.get(PARTS) instanceof List<?> parts)
                || parts.isEmpty()) {
            throw new IllegalArgumentException("Media input requires a nonempty $semanticInput part list");
        }
        return parts;
    }

    public static void validate(Object input, Set<InputType> accepted) {
        input = normalize(input);
        if (isMedia(input)) {
            for (Object item : parts(input)) {
                if (!(item instanceof Map<?, ?> part)) {
                    throw new IllegalArgumentException("Media input parts must be objects");
                }
                if ("text".equals(part.get("type")) && accepted.contains(InputType.TEXT)
                        && part.keySet().equals(Set.of("type", "text")) && part.get("text") instanceof String) {
                    continue;
                }
                if ("image".equals(part.get("type")) && accepted.contains(InputType.IMAGE)
                        && Set.of("type", "data", "detail").containsAll(part.keySet())
                        && part.get("data") instanceof String data && !data.isBlank()
                        && (!part.containsKey("detail") || part.get("detail") instanceof String)) {
                    continue;
                }
                throw new IllegalArgumentException("Unsupported media part or modality; expert accepts " + accepted);
            }
        } else if (input instanceof String && accepted.contains(InputType.TEXT)) {
            // After normalizing data:image/ URLs above, ordinary strings (including URLs and JSON) remain text.
        } else if ((input instanceof Map<?, ?> || input instanceof List<?>) && accepted.contains(InputType.STRUCTURED)) {
            // The expert validates its application-state schema; STRUCTURED does not imply a JSON-only contract.
        } else {
            throw new IllegalArgumentException("Unsupported selected state; accepts " + accepted + " input");
        }
    }

}
