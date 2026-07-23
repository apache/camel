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
package org.apache.camel.yaml.io;

import java.util.Collection;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Serializes {@link Map} / {@link Collection} structures (typically from Camel's {@code JsonObject} /
 * {@code JsonArray}) to block-style YAML text. Produces a constrained YAML subset: no anchors, aliases, flow mappings,
 * tags, or multi-document streams.
 */
public final class YamlPrinter {

    private static final String INDENT = "  ";
    private static final Pattern NUMBER_PATTERN = Pattern.compile("-?(0|[1-9]\\d*)(\\.\\d+)?([eE][+-]?\\d+)?");

    private YamlPrinter() {
    }

    public static String print(Collection<?> roots) {
        StringBuilder sb = new StringBuilder();
        writeSequenceItems(sb, roots, 0, true);
        if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n') {
            sb.append('\n');
        }
        return sb.toString();
    }

    private static void writeSequenceItems(StringBuilder sb, Collection<?> items, int indent, boolean topLevel) {
        for (Object item : items) {
            writeIndent(sb, indent);
            sb.append("- ");
            if (item instanceof Map<?, ?> map) {
                if (map.isEmpty()) {
                    sb.append("{}\n");
                } else {
                    writeMappingEntries(sb, map, indent + 1, true);
                }
            } else if (item instanceof Collection<?> col) {
                sb.append('\n');
                writeSequenceItems(sb, col, indent + 1, false);
            } else {
                writeScalar(sb, item);
                sb.append('\n');
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void writeMappingEntries(StringBuilder sb, Map<?, ?> map, int indent, boolean firstInline) {
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();

            if (first && firstInline) {
                first = false;
            } else {
                writeIndent(sb, indent);
            }

            sb.append(key).append(':');

            if (value instanceof Map<?, ?> childMap) {
                if (childMap.isEmpty()) {
                    sb.append(" {}\n");
                } else {
                    sb.append('\n');
                    writeMappingEntries(sb, childMap, indent + 1, false);
                }
            } else if (value instanceof Collection<?> col) {
                sb.append('\n');
                writeSequenceItems(sb, col, indent + 1, false);
            } else {
                sb.append(' ');
                if (value instanceof String s && s.contains("\n")) {
                    writeBlockScalar(sb, s, indent + 1);
                } else {
                    writeScalar(sb, value);
                    sb.append('\n');
                }
            }
        }
    }

    private static void writeBlockScalar(StringBuilder sb, String value, int indent) {
        if (value.endsWith("\n")) {
            sb.append("|\n");
        } else {
            sb.append("|-\n");
        }
        for (String line : value.split("\n", -1)) {
            if (line.isEmpty()) {
                sb.append('\n');
            } else {
                writeIndent(sb, indent);
                sb.append(line).append('\n');
            }
        }
    }

    private static void writeScalar(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Number) {
            sb.append(value);
        } else {
            String s = String.valueOf(value);
            if (needsQuoting(s)) {
                sb.append('"');
                sb.append(s.replace("\\", "\\\\").replace("\"", "\\\""));
                sb.append('"');
            } else {
                sb.append(s);
            }
        }
    }

    static boolean needsQuoting(String s) {
        if (s.isEmpty()) {
            return true;
        }

        char first = s.charAt(0);
        if (first == ' ' || first == '\t' || first == '-' || first == '?' || first == '*'
                || first == '&' || first == '!' || first == '%' || first == '@' || first == '`'
                || first == '\'' || first == '"' || first == '{' || first == '[' || first == '>'
                || first == '|' || first == '#' || first == '$') {
            return true;
        }

        if (s.charAt(s.length() - 1) == ' ' || s.charAt(s.length() - 1) == '\t') {
            return true;
        }

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ':' && i + 1 < s.length() && s.charAt(i + 1) == ' ') {
                return true;
            }
            if (c == '#' && i > 0 && s.charAt(i - 1) == ' ') {
                return true;
            }
            if (c == '$' && i + 1 < s.length() && s.charAt(i + 1) == '{') {
                return true;
            }
            if (c == '{' && i + 1 < s.length() && s.charAt(i + 1) == '{') {
                return true;
            }
            if (c == '\n') {
                return true;
            }
        }

        if (isYamlKeyword(s)) {
            return true;
        }

        if (NUMBER_PATTERN.matcher(s).matches()) {
            return true;
        }

        return false;
    }

    private static boolean isYamlKeyword(String s) {
        return "true".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s)
                || "yes".equalsIgnoreCase(s) || "no".equalsIgnoreCase(s)
                || "on".equalsIgnoreCase(s) || "off".equalsIgnoreCase(s)
                || "null".equalsIgnoreCase(s) || "~".equals(s);
    }

    private static void writeIndent(StringBuilder sb, int level) {
        for (int i = 0; i < level; i++) {
            sb.append(INDENT);
        }
    }
}
