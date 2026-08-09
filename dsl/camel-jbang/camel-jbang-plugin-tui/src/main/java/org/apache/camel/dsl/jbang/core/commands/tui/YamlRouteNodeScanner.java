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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Scans Camel YAML route files and builds a flat tree of route headers and navigable processor/EIP nodes.
 */
class YamlRouteNodeScanner {

    private static final Set<String> STRUCTURAL_KEYS = Set.of(
            "steps", "uri", "parameters", "from", "expression", "routeConfiguration",
            "routeTemplate", "templatedRoute", "rest", "beans");

    private static final Set<String> BOILERPLATE_KEYS = Set.of("id", "note", "description", "disabled");

    private static final Set<String> ENDPOINT_EIPS = Set.of(
            "from", "to", "toD", "to-d", "wireTap", "wire-tap", "enrich",
            "pollEnrich", "poll-enrich", "poll", "interceptFrom", "intercept-from",
            "interceptSendToEndpoint", "intercept-send-to-endpoint");

    enum EntryKind {
        ROUTE,
        PROCESSOR
    }

    record NodeEntry(
            EntryKind kind,
            String routeId,
            String fromUri,
            String type,
            String label,
            String filePath,
            int lineIndex,
            int indent) {
    }

    static List<NodeEntry> scanFile(Path file) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return List.of();
        }
        return scanLines(lines, file.toString());
    }

    static List<NodeEntry> scanLines(List<String> lines, String filePath) {
        List<NodeEntry> result = new ArrayList<>();

        String currentRouteId = null;
        String currentFromUri = null;
        int pendingFromLine = -1;
        boolean routeHeaderEmitted = false;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }

            if (trimmed.startsWith("id:") && !trimmed.startsWith("id: \"\"")) {
                String val = extractYamlValue(trimmed, "id");
                if (val != null && !val.isEmpty()) {
                    currentRouteId = val;
                }
                continue;
            }

            if (trimmed.startsWith("- route:") || trimmed.equals("route:")) {
                currentRouteId = null;
                currentFromUri = null;
                pendingFromLine = -1;
                routeHeaderEmitted = false;
                continue;
            }

            if (trimmed.startsWith("from:") || trimmed.startsWith("- from:")) {
                currentFromUri = null;
                routeHeaderEmitted = false;
                String inlineUri = extractInlineUri(trimmed, "from");
                if (inlineUri != null) {
                    currentFromUri = stripQueryParams(inlineUri);
                    emitRouteHeader(result, currentRouteId, currentFromUri, filePath, i);
                    routeHeaderEmitted = true;
                    pendingFromLine = -1;
                } else {
                    pendingFromLine = i;
                }
                continue;
            }

            if (pendingFromLine >= 0 && trimmed.startsWith("uri:")) {
                String uri = extractYamlValue(trimmed, "uri");
                if (uri != null) {
                    currentFromUri = stripQueryParams(uri);
                    emitRouteHeader(result, currentRouteId, currentFromUri, filePath, pendingFromLine);
                    routeHeaderEmitted = true;
                }
                pendingFromLine = -1;
                continue;
            }

            if (pendingFromLine >= 0 && lineIndent(line) <= lineIndent(lines.get(pendingFromLine))) {
                pendingFromLine = -1;
            }

            if (!routeHeaderEmitted) {
                continue;
            }

            if (isNavigableNodeLine(line)) {
                String type = extractNodeType(line);
                if (type == null || BOILERPLATE_KEYS.contains(type)) {
                    continue;
                }
                if (STRUCTURAL_KEYS.contains(type) && !line.trim().substring(line.trim().startsWith("- ")
                        ? 2 : 0).trim().startsWith("uri:")) {
                    continue;
                }
                String routeId = resolveRouteId(currentRouteId, currentFromUri);
                String label = buildNodeLabel(line, lines, i);
                result.add(new NodeEntry(
                        EntryKind.PROCESSOR, routeId, null, type, label, filePath, i, 1));
            }
        }

        return result;
    }

    private static void emitRouteHeader(
            List<NodeEntry> result, String routeId, String fromUri, String filePath, int fromLine) {
        String resolvedId = resolveRouteId(routeId, fromUri);
        result.add(new NodeEntry(
                EntryKind.ROUTE, resolvedId, fromUri, "route", fromUri, filePath, fromLine, 0));
    }

    private static String resolveRouteId(String routeId, String fromUri) {
        if (routeId != null && !routeId.isEmpty()) {
            return routeId;
        }
        if (fromUri == null || fromUri.isEmpty()) {
            return "route";
        }
        int colon = fromUri.indexOf(':');
        String derived = colon >= 0 ? fromUri.substring(colon + 1) : fromUri;
        if (derived.startsWith("//")) {
            derived = derived.substring(2);
        }
        return derived.isEmpty() ? "route" : derived;
    }

    static boolean isNavigableNodeLine(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return false;
        }

        String content = trimmed.startsWith("- ") ? trimmed.substring(2).trim() : trimmed;
        if (content.startsWith("uri:")) {
            return true;
        }

        int colonIdx = content.indexOf(':');
        if (colonIdx <= 0) {
            return false;
        }

        String key = content.substring(0, colonIdx).trim();
        if (BOILERPLATE_KEYS.contains(key) || STRUCTURAL_KEYS.contains(key)) {
            return false;
        }

        String after = content.substring(colonIdx + 1).trim();
        if (ENDPOINT_EIPS.contains(key)) {
            if (after.isEmpty() || after.equals("{")) {
                return false;
            }
            return true;
        }

        if (trimmed.startsWith("- ")) {
            return !after.startsWith("#");
        }

        return false;
    }

    static String extractNodeType(String line) {
        String trimmed = line.trim();
        String content = trimmed.startsWith("- ") ? trimmed.substring(2).trim() : trimmed;
        if (content.startsWith("uri:")) {
            return "uri";
        }
        int colonIdx = content.indexOf(':');
        if (colonIdx > 0) {
            return content.substring(0, colonIdx).trim();
        }
        return null;
    }

    static String buildNodeLabel(String line, List<String> lines, int lineIndex) {
        String trimmed = line.trim();
        String content = trimmed.startsWith("- ") ? trimmed.substring(2).trim() : trimmed;

        if (content.startsWith("uri:")) {
            String uri = extractYamlValue(content, "uri");
            return uri != null ? uri : "";
        }

        int colonIdx = content.indexOf(':');
        if (colonIdx > 0) {
            String after = content.substring(colonIdx + 1).trim();
            if (!after.isEmpty() && !after.equals("{") && !after.startsWith("#")) {
                return unquote(after);
            }
        }

        int baseIndent = lineIndent(line);
        for (int j = lineIndex + 1; j < lines.size(); j++) {
            String next = lines.get(j);
            if (next.isBlank()) {
                continue;
            }
            int nextIndent = lineIndent(next);
            if (nextIndent <= baseIndent) {
                break;
            }
            String nt = next.trim();
            for (String prop : List.of("message:", "name:", "id:", "simple:", "constant:", "language:")) {
                if (nt.startsWith(prop)) {
                    String val = nt.substring(prop.length()).trim();
                    return unquote(val);
                }
            }
        }
        return "";
    }

    private static String extractYamlValue(String trimmed, String key) {
        String prefix = key + ":";
        if (!trimmed.startsWith(prefix)) {
            return null;
        }
        return unquote(trimmed.substring(prefix.length()).trim());
    }

    private static String extractInlineUri(String trimmed, String key) {
        String prefix = trimmed.startsWith("- ") ? "- " + key + ":" : key + ":";
        if (!trimmed.startsWith(prefix)) {
            return null;
        }
        String val = trimmed.substring(prefix.length()).trim();
        if (val.isEmpty() || val.equals("{") || val.startsWith("#")) {
            return null;
        }
        return unquote(val);
    }

    private static String unquote(String val) {
        if (val.length() >= 2 && val.startsWith("\"") && val.endsWith("\"")) {
            return val.substring(1, val.length() - 1);
        }
        if (val.length() >= 2 && val.startsWith("'") && val.endsWith("'")) {
            return val.substring(1, val.length() - 1);
        }
        return val;
    }

    private static String stripQueryParams(String uri) {
        if (uri == null) {
            return null;
        }
        int q = uri.indexOf('?');
        return q >= 0 ? uri.substring(0, q) : uri;
    }

    private static int lineIndent(String line) {
        int indent = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == ' ') {
                indent++;
            } else {
                break;
            }
        }
        return indent;
    }
}
