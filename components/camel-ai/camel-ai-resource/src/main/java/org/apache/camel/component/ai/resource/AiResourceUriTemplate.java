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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A deliberately small RFC 6570 level-1 URI-template matcher for AI resources.
 * <p>
 * Each expression must occupy an entire path segment, for example {@code crm://customers/{id}}. Variables match one
 * non-empty path segment and are returned verbatim (including percent-encoding); they are never decoded, interpreted as
 * Camel expressions, or resolved as property placeholders. Query and fragment expressions are intentionally not
 * supported.
 *
 * @since 4.24
 */
public final class AiResourceUriTemplate {

    private static final Pattern SCHEME = Pattern.compile("[A-Za-z][A-Za-z0-9+.-]*");
    private static final Pattern VARIABLE_NAME = Pattern.compile("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)*");
    // RFC 3986 pchar: unreserved / pct-encoded / sub-delims / ":" / "@".
    // Keep percent-encoded octets intact so an encoded slash remains one segment.
    private static final String PCHAR_SEGMENT = "(?:[A-Za-z0-9._~!$&'()*+,;=:@-]|%[0-9A-Fa-f]{2})+";

    private final String template;
    private final String prefix;
    private final List<Segment> segments;
    private final List<String> variableNames;
    private final Pattern pattern;

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
        this.variableNames = List.copyOf(parsedVariables);

        StringBuilder regex = new StringBuilder("^").append(Pattern.quote(prefix));
        for (Segment segment : segments) {
            regex.append('/');
            if (segment.isVariable()) {
                regex.append('(').append(PCHAR_SEGMENT).append(')');
            } else {
                regex.append(Pattern.quote(segment.literal()));
            }
        }
        regex.append('$');
        this.pattern = Pattern.compile(regex.toString());
    }

    public String getTemplate() {
        return template;
    }

    /**
     * Matches a concrete resource URI and returns the raw value for each variable. An empty result means the URI does
     * not match this template.
     */
    public Optional<Map<String, String>> match(String resourceUri) {
        if (resourceUri == null) {
            return Optional.empty();
        }
        Matcher matcher = pattern.matcher(resourceUri);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < variableNames.size(); i++) {
            String value = matcher.group(i + 1);
            // Dot segments can be normalized by downstream URI/path consumers.
            if (".".equals(value) || "..".equals(value)) {
                return Optional.empty();
            }
            values.put(variableNames.get(i), value);
        }
        return Optional.of(Collections.unmodifiableMap(values));
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
