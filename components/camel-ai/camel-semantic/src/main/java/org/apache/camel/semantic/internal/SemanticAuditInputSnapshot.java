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
package org.apache.camel.semantic.internal;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.semantic.SemanticInput;

/** Bounded JSON values only: never converts arbitrary objects or consumes streams. */
public final class SemanticAuditInputSnapshot {
    private int remaining;
    private int nodes = 1000;

    private SemanticAuditInputSnapshot(int maxChars) {
        remaining = maxChars;
    }

    public static Object copy(Object input, int maxChars) {
        return new SemanticAuditInputSnapshot(maxChars).value(input, 0);
    }

    /** Classify without reading streams, decoding content, or exposing values in an audit summary. */
    public static String inputType(Object input) {
        if (SemanticInput.isMedia(input)) {
            boolean text = false;
            boolean image = false;
            List<?> parts = SemanticInput.parts(input);
            if (parts.size() > 1000) {
                return "unknown";
            }
            for (Object item : parts) {
                if (!(item instanceof Map<?, ?> part)) {
                    return "unsupported";
                }
                if ("text".equals(part.get("type"))) {
                    text = true;
                } else if ("image".equals(part.get("type"))) {
                    image = true;
                } else {
                    return "unsupported";
                }
            }
            return image ? text ? "text-and-images" : "image" : "text";
        }
        return input instanceof String ? "text"
                : input instanceof Map<?, ?> || input instanceof List<?> ? "structured" : "unsupported";
    }

    /** Retain bounded image descriptors when the configured capture budget cannot hold the image payload. */
    public static Object capture(Object input, int maxChars) {
        try {
            return copy(input, maxChars);
        } catch (IllegalArgumentException limit) {
            if (!SemanticInput.isMedia(input) || SemanticInput.parts(input).size() > 128) {
                throw limit;
            }
            List<Object> parts = new ArrayList<>();
            for (Object item : SemanticInput.parts(input)) {
                if (item instanceof Map<?, ?> part && "image".equals(part.get("type"))) {
                    Map<String, Object> descriptor = new LinkedHashMap<>();
                    descriptor.put("type", "image");
                    descriptor.put("dataOmitted", "snapshot_limit");
                    if (part.get("data") instanceof String data && data.startsWith("data:image/")) {
                        int end = data.indexOf(";base64,");
                        if (end > 5 && end < 120 && data.substring(5, end).matches("image/[a-z0-9.+-]+")) {
                            descriptor.put("mediaType", data.substring(5, end));
                        }
                    }
                    parts.add(descriptor);
                } else {
                    parts.add(item);
                }
            }
            return copy(Map.of(SemanticInput.PARTS, parts), maxChars);
        }
    }

    private Object value(Object value, int depth) {
        if (--nodes < 0 || depth > 8) {
            throw new IllegalArgumentException("Input snapshot limit");
        }
        if (value == null || value instanceof Boolean) {
            text(String.valueOf(value));
            return value;
        }
        if (value instanceof String text) {
            return text(text);
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long
                || value instanceof Float || value instanceof Double || value instanceof BigDecimal
                || value instanceof BigInteger) {
            if (value instanceof Double d && !Double.isFinite(d) || value instanceof Float f && !Float.isFinite(f)
                    || value instanceof BigInteger integer && integer.bitLength() > remaining * 4
                    || value instanceof BigDecimal decimal && decimal.precision() > remaining) {
                throw new IllegalArgumentException("Input snapshot limit");
            }
            text(value.toString());
            return value;
        }
        // Collection sizes reject obvious overflows; recursive calls enforce the shared node budget.
        if (value instanceof List<?> list && list.size() <= nodes) {
            List<Object> copy = new ArrayList<>();
            for (Object item : list) {
                copy.add(value(item, depth + 1));
            }
            return Collections.unmodifiableList(copy);
        }
        if (value instanceof Map<?, ?> map && map.size() <= nodes / 2) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (var entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("Unsupported input key");
                }
                copy.put((String) value(key, depth + 1), value(entry.getValue(), depth + 1));
            }
            return Collections.unmodifiableMap(copy);
        }
        throw new IllegalArgumentException("Unsupported input or snapshot limit");
    }

    private String text(String text) {
        if (text.length() > remaining * 2 || (remaining -= text.codePointCount(0, text.length())) < 0) {
            throw new IllegalArgumentException("Input snapshot limit");
        }
        return text;
    }
}
