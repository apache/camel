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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.util.regex.Pattern;

/**
 * Line-level helpers over a YAML source shared by the checks of {@link SourceValidator}: the enclosing EIP of a line,
 * the value of a key, quotes, indentation.
 */
final class YamlLines {

    private YamlLines() {
    }

    static final Pattern YAML_URI_PATTERN = Pattern.compile(
            "^\\s*-?\\s*(?:uri|from|to|toD|wireTap|enrich|pollEnrich|deadLetterChannel):\\s*\"?([a-zA-Z][a-zA-Z0-9+.-]*(?::[^\"\\s]*)?)");

    /** Whether a line reads as text rather than code: no braces, semicolons or annotations, and more than two words. */
    static boolean looksLikeProse(String line) {
        String t = line.trim();
        if (t.isEmpty() || t.startsWith("//") || t.startsWith("/*") || t.startsWith("*") || t.startsWith("@")) {
            return false;
        }
        for (char code : new char[] { '{', '}', ';', '(', ')', '=', '<', '>' }) {
            if (t.indexOf(code) >= 0) {
                return false;
            }
        }
        return t.split("\\s+").length > 2 || t.startsWith("#") || t.startsWith("```");
    }

    static String findParentEip(String[] lines, int lineIdx, int lineIndent) {
        int indent = lineIndent;
        for (int j = lineIdx - 1; j >= 0; j--) {
            String prev = lines[j];
            if (prev.isBlank()) {
                continue;
            }
            int prevIndent = countLeadingSpaces(prev);
            if (prevIndent < indent) {
                String eip = extractEipFromLine(prev.trim());
                if ("expression".equals(eip)) {
                    // when: - expression: simple: ...  : the wrapper is not the EIP, keep looking for when
                    indent = prevIndent;
                    continue;
                }
                return eip;
            }
        }
        return null;
    }

    static String extractEipFromLine(String trimmed) {
        if (trimmed.startsWith("- ")) {
            trimmed = trimmed.substring(2).trim();
        }
        int colon = trimmed.indexOf(':');
        if (colon > 0) {
            return trimmed.substring(0, colon).trim();
        }
        return null;
    }

    static String extractYamlValue(String trimmed, String key) {
        String prefix = key + ":";
        if (!trimmed.startsWith(prefix)) {
            return null;
        }
        return unquote(trimmed.substring(prefix.length()).trim());
    }

    static String unquote(String val) {
        if (val.length() >= 2 && val.startsWith("\"") && val.endsWith("\"")) {
            return val.substring(1, val.length() - 1);
        }
        if (val.length() >= 2 && val.startsWith("'") && val.endsWith("'")) {
            return val.substring(1, val.length() - 1);
        }
        return val;
    }

    static int countLeadingSpaces(String line) {
        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == ' ') {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

}
