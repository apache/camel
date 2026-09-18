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

import java.util.List;

/**
 * Pure text transformations behind the source editor refactorings (extract route to file, replace URI, extract value to
 * property). Kept free of editor state so they can be unit tested on plain strings.
 */
final class SourceRefactorings {

    private SourceRefactorings() {
    }

    /**
     * Sanitizes a user-supplied string into a safe file base-name: replaces whitespace and illegal characters with
     * hyphens, collapses consecutive hyphens, and strips leading/trailing hyphens.
     */
    static String sanitizeFileName(String name) {
        if (name == null) {
            return "";
        }
        // Replace whitespace and any char that is not alphanumeric, hyphen, underscore, or dot with a hyphen
        String sanitized = name.trim().replaceAll("[^a-zA-Z0-9._-]", "-");
        // Collapse consecutive hyphens
        sanitized = sanitized.replaceAll("-{2,}", "-");
        // Strip leading/trailing hyphens
        sanitized = sanitized.replaceAll("^-+|-+$", "");
        return sanitized;
    }

    /**
     * Builds the YAML content for a new standalone route file containing the extracted step block.
     */
    static String buildExtractedRouteYaml(String name, List<String> blockLines, int stepIndent) {
        // Standard Camel YAML route indentation: step items at column 6.
        String stepPrefix = "      ";
        StringBuilder sb = new StringBuilder();
        sb.append("- route:\n");
        sb.append("    from:\n");
        sb.append("      uri: direct:").append(name).append("\n");
        sb.append("    steps:\n");
        for (String line : blockLines) {
            String stripped = line.length() >= stepIndent ? line.substring(stepIndent) : line.stripLeading();
            sb.append(stepPrefix).append(stripped).append("\n");
        }
        return sb.toString();
    }

    /**
     * Returns {@code true} if the line represents an EIP step list item that can be extracted to a new file. Excludes
     * {@code - route:} and {@code - from:} which are structural, not steps.
     */
    static boolean isExtractableStep(String line) {
        if (line == null) {
            return false;
        }
        String trimmed = line.trim();
        return trimmed.startsWith("- ") && !trimmed.equals("- ")
                && !trimmed.startsWith("- route:") && !trimmed.startsWith("- from:");
    }

    /**
     * Removes the {@code parameters:} sibling block immediately after a {@code uri:} line when the URI is replaced.
     * Only applies to block-form {@code uri:} lines; inline-form URIs carry no separate parameters block.
     */
    static void removeParametersBlock(List<String> lines, int uriRow, String uriLine) {
        if (uriLine == null || !uriLine.trim().startsWith("uri:")) {
            return;
        }
        int uriIndent = YamlBlockEditor.leadingSpaces(uriLine);
        int paramsStart = -1;
        int paramsEnd = -1;
        for (int i = uriRow + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            int indent = YamlBlockEditor.leadingSpaces(line);
            if (indent < uriIndent) {
                break;
            }
            if (indent == uriIndent) {
                if (line.trim().startsWith("parameters:")) {
                    paramsStart = i;
                    paramsEnd = i;
                    // Extend to all child lines (indented deeper than uriIndent)
                    for (int j = i + 1; j < lines.size(); j++) {
                        String next = lines.get(j);
                        if (next.isBlank()) {
                            continue;
                        }
                        if (YamlBlockEditor.leadingSpaces(next) <= uriIndent) {
                            break;
                        }
                        paramsEnd = j;
                    }
                }
                break; // another sibling key — stop regardless
            }
        }
        if (paramsStart >= 0) {
            lines.subList(paramsStart, paramsEnd + 1).clear();
        }
    }

    /**
     * Extracts the URI (without query parameters) from a YAML endpoint line, or {@code null} if the line is not a
     * recognized endpoint/URI line.
     */
    static String extractUriFromLine(String line) {
        if (line == null) {
            return null;
        }
        String trimmed = line.trim();
        for (String prefix : List.of(
                "- to:", "- from:", "from:", "- toD:", "- to-d:", "- wireTap:", "- wire-tap:",
                "- enrich:", "- pollEnrich:", "- poll-enrich:", "- poll:", "uri:")) {
            if (trimmed.startsWith(prefix)) {
                String val = trimmed.substring(prefix.length()).trim();
                val = unquoteYaml(val);
                if (val.isEmpty() || val.startsWith("{") || val.startsWith("#")) {
                    return null;
                }
                int q = val.indexOf('?');
                return q >= 0 ? val.substring(0, q) : val;
            }
        }
        return null;
    }

    /**
     * Replaces the URI on a YAML endpoint line, stripping any existing query parameters.
     */
    static String replaceUriOnLine(String line, String newUri) {
        if (line == null) {
            return line;
        }
        String trimmed = line.trim();
        int indent = YamlSourceContext.countLeadingSpaces(line);
        String indentStr = line.substring(0, indent);
        for (String prefix : List.of(
                "- to:", "- from:", "from:", "- toD:", "- to-d:", "- wireTap:", "- wire-tap:",
                "- enrich:", "- pollEnrich:", "- poll-enrich:", "- poll:", "uri:")) {
            if (trimmed.startsWith(prefix)) {
                return indentStr + prefix + " " + newUri;
            }
        }
        return line;
    }

    /**
     * Extracts the plain-string value from a {@code key: value} YAML line, or {@code null} if the line does not carry
     * an extractable literal (empty, structural, already a placeholder, or a URI endpoint line).
     */
    static String extractValueFromLine(String line) {
        if (line == null) {
            return null;
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("- ")) {
            return null;
        }
        int colon = trimmed.indexOf(':');
        if (colon <= 0) {
            return null;
        }
        String val = trimmed.substring(colon + 1).trim();
        if (val.isEmpty() || val.startsWith("[") || val.startsWith("*")) {
            return null;
        }
        val = unquoteYaml(val);
        // Skip YAML maps and existing property placeholders
        if (val.startsWith("{") || (val.startsWith("{{") && val.endsWith("}}"))) {
            return null;
        }
        return val;
    }

    /**
     * Replaces the value on a YAML {@code key: value} line with {@code {{propKey}}}, preserving indentation and key.
     */
    static String replaceValueWithPlaceholder(String line, String propKey) {
        if (line == null) {
            return line;
        }
        String trimmed = line.trim();
        int indent = YamlSourceContext.countLeadingSpaces(line);
        String indentStr = line.substring(0, indent);
        int colon = trimmed.indexOf(':');
        if (colon <= 0) {
            return line;
        }
        return indentStr + trimmed.substring(0, colon + 1) + " \"{{" + propKey + "}}\"";
    }

    static String unquoteYaml(String val) {
        if (val.length() >= 2 && val.startsWith("\"") && val.endsWith("\"")) {
            return val.substring(1, val.length() - 1);
        }
        if (val.length() >= 2 && val.startsWith("'") && val.endsWith("'")) {
            return val.substring(1, val.length() - 1);
        }
        return val;
    }
}
