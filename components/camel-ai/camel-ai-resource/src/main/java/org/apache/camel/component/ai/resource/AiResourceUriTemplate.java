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
package org.apache.camel.component.ai.resource;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A deliberately small RFC 6570 level-1 URI-template matcher for AI resources.
 * <p>
 * Each expression must occupy an entire path segment, for example {@code crm://customers/{id}}. Variables match one
 * non-empty path segment and are returned verbatim (including percent-encoding); they are never decoded, interpreted as
 * Camel expressions, or resolved as property placeholders. A value that, once percent-decoded, is a {@code .} or
 * {@code ..} dot segment or contains one between {@code /} or {@code \} separators does not match, including one
 * followed by path parameters such as {@code ..;x}. Query and fragment expressions are intentionally not supported.
 * <p>
 * A concrete URI is matched by walking its path segments against the template's: literal segments must be equal, and a
 * variable segment must consist of RFC 3986 {@code pchar} characters, with percent-encoded octets kept as they are (so
 * an encoded slash stays within one segment).
 *
 * @since 4.24
 */
public final class AiResourceUriTemplate {

    private static final Pattern SCHEME = Pattern.compile("[A-Za-z][A-Za-z0-9+.-]*");
    private static final Pattern VARIABLE_NAME = Pattern.compile("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)*");

    private final String template;
    private final String prefix;
    private final String pathPrefix;
    private final List<Segment> segments;

    public AiResourceUriTemplate(String template) {
        if (template == null || template.isBlank()) {
            throw new IllegalArgumentException("uriTemplate must not be blank");
        }
        this.template = template;

        int colon = template.indexOf(':');
        if (colon <= 0 || !SCHEME.matcher(template.substring(0, colon)).matches()) {
            throw new IllegalArgumentException("Invalid resource uriTemplate scheme: " + template);
        }
        if (template.indexOf('?') >= 0 || template.indexOf('#') >= 0) {
            throw new IllegalArgumentException(
                    "Resource uriTemplate supports path-segment variables only; query and fragment are not supported: "
                                               + template);
        }

        int pathStart = template.startsWith("//", colon + 1)
                ? template.indexOf('/', colon + 3)
                : template.indexOf('/', colon + 1);
        if (pathStart < 0) {
            throw new IllegalArgumentException("Resource uriTemplate must contain a path: " + template);
        }
        this.prefix = template.substring(0, pathStart);
        if (prefix.indexOf('{') >= 0 || prefix.indexOf('}') >= 0) {
            throw new IllegalArgumentException("Resource uriTemplate variables must be in path segments: " + template);
        }

        String path = template.substring(pathStart + 1);
        String[] rawSegments = path.split("/", -1);
        List<Segment> parsedSegments = new ArrayList<>(rawSegments.length);
        List<String> parsedVariables = new ArrayList<>();
        for (String rawSegment : rawSegments) {
            if (rawSegment.indexOf('{') < 0 && rawSegment.indexOf('}') < 0) {
                parsedSegments.add(Segment.literal(rawSegment));
                continue;
            }
            if (!rawSegment.startsWith("{") || !rawSegment.endsWith("}")
                    || rawSegment.indexOf('{', 1) >= 0 || rawSegment.indexOf('}') != rawSegment.length() - 1) {
                throw new IllegalArgumentException(
                        "Each resource uriTemplate variable must occupy a whole path segment: " + template);
            }
            String variable = rawSegment.substring(1, rawSegment.length() - 1);
            if (!VARIABLE_NAME.matcher(variable).matches()) {
                throw new IllegalArgumentException("Invalid resource uriTemplate variable name '" + variable + "'");
            }
            // Camel message header names are case-insensitive. Reject case variants
            // so two URI variables cannot silently overwrite the same header.
            if (parsedVariables.stream().anyMatch(existing -> existing.equalsIgnoreCase(variable))) {
                throw new IllegalArgumentException(
                        "Duplicate resource uriTemplate variable (case-insensitive) '"
                                                   + variable + "': " + template);
            }
            parsedVariables.add(variable);
            parsedSegments.add(Segment.variable(variable));
        }
        if (parsedVariables.isEmpty()) {
            throw new IllegalArgumentException(
                    "Resource uriTemplate must contain at least one path variable: " + template);
        }

        this.segments = List.copyOf(parsedSegments);
        this.pathPrefix = prefix + "/";
    }

    public String getTemplate() {
        return template;
    }

    /**
     * Matches a concrete resource URI and returns the raw value for each variable. An empty result means the URI does
     * not match this template.
     */
    public Optional<Map<String, String>> match(String resourceUri) {
        if (resourceUri == null || !resourceUri.startsWith(pathPrefix)) {
            return Optional.empty();
        }
        Map<String, String> values = new LinkedHashMap<>();
        int start = pathPrefix.length();
        for (int i = 0; i < segments.size(); i++) {
            int end = resourceUri.indexOf('/', start);
            boolean last = i == segments.size() - 1;
            // every segment but the last ends at a '/', and the last one runs to the end of the URI
            if (last != (end < 0)) {
                return Optional.empty();
            }
            Segment segment = segments.get(i);
            String part = resourceUri.substring(start, last ? resourceUri.length() : end);
            start = end + 1;
            if (!segment.isVariable()) {
                if (!segment.literal().equals(part)) {
                    return Optional.empty();
                }
                continue;
            }
            // Dot segments can be normalized by downstream URI/path consumers. RFC 3986 makes %2E equivalent to
            // ".", and a consumer that decodes the value also turns %2F (or %5C) into a path separator.
            if (!isSegmentValue(part) || containsDotSegment(part)) {
                return Optional.empty();
            }
            values.put(segment.variable(), part);
        }
        return Optional.of(Collections.unmodifiableMap(values));
    }

    /** Whether the value is one non-empty path segment of RFC 3986 {@code pchar}, percent-encoded octets included. */
    private static boolean isSegmentValue(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '%') {
                if (i + 2 >= value.length() || hexDigit(value.charAt(i + 1)) < 0 || hexDigit(value.charAt(i + 2)) < 0) {
                    return false;
                }
                i += 2;
            } else if (!isPchar(c)) {
                return false;
            }
        }
        return true;
    }

    /** Whether the character is an RFC 3986 unreserved or sub-delims character, {@code :} or {@code @}. */
    private static boolean isPchar(char c) {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || "-._~!$&'()*+,;=:@".indexOf(c) >= 0;
    }

    /**
     * Whether the value, once percent-decoded, is a dot segment or contains one between {@code /} or {@code \}
     * separators, for example {@code ..}, {@code %2e%2E} or {@code ..%2F..%2Fetc%2Fpasswd}. A segment counts up to its
     * first {@code ;}, because servers such as Tomcat drop the path parameters of a segment before resolving dot
     * segments: {@code ..;x} is a dot segment too.
     */
    private static boolean containsDotSegment(String value) {
        String decoded = percentDecode(value);
        int start = 0;
        // where the path parameters of the current segment begin, -1 when it has none
        int parameters = -1;
        for (int i = 0; i <= decoded.length(); i++) {
            char c = i < decoded.length() ? decoded.charAt(i) : '/';
            if (c == ';' && parameters < 0) {
                parameters = i;
            } else if (c == '/' || c == '\\') {
                int length = (parameters < 0 ? i : parameters) - start;
                if ((length == 1 || length == 2) && decoded.startsWith(".".repeat(length), start)) {
                    return true;
                }
                start = i + 1;
                parameters = -1;
            }
        }
        return false;
    }

    /**
     * Decodes percent-encoded octets as UTF-8. Unlike {@code URLDecoder} it keeps {@code +} as is and never throws: a
     * malformed escape is kept verbatim and invalid UTF-8 becomes the replacement character.
     */
    private static String percentDecode(String value) {
        if (value.indexOf('%') < 0) {
            return value;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            int high = c == '%' && i + 2 < value.length() ? hexDigit(value.charAt(i + 1)) : -1;
            int low = high >= 0 ? hexDigit(value.charAt(i + 2)) : -1;
            if (low >= 0) {
                bytes.write((high << 4) | low);
                i += 2;
            } else if (c < 0x80) {
                // a matched value is ASCII: isSegmentValue allows only percent-encoded octets beyond it
                bytes.write(c);
            } else {
                byte[] encoded = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
                bytes.write(encoded, 0, encoded.length);
            }
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private static int hexDigit(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        } else if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        } else if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        return -1;
    }

    /**
     * Returns true when there is at least one concrete URI that both templates can match.
     */
    public boolean overlaps(AiResourceUriTemplate other) {
        if (other == null || !prefix.equals(other.prefix) || segments.size() != other.segments.size()) {
            return false;
        }
        for (int i = 0; i < segments.size(); i++) {
            Segment left = segments.get(i);
            Segment right = other.segments.get(i);
            if (!left.isVariable() && !right.isVariable() && !left.literal().equals(right.literal())) {
                return false;
            }
            if (left.isVariable() && !right.isVariable() && right.literal().isEmpty()) {
                return false;
            }
            if (right.isVariable() && !left.isVariable() && left.literal().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private record Segment(String literal, String variable) {
        static Segment literal(String value) {
            return new Segment(value, null);
        }

        static Segment variable(String name) {
            return new Segment(null, name);
        }

        boolean isVariable() {
            return variable != null;
        }
    }
}
