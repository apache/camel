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
package org.apache.camel.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Helper for escaping special characters so a value can be embedded safely in HTML, XML, JSON, JavaScript, SQL or a
 * URL.
 */
public final class EscapeHelper {

    /**
     * The supported escape kinds.
     */
    public enum Kind {
        HTML,
        XML,
        JSON,
        JS,
        SQL,
        URL;

        /**
         * Resolves the escape kind from its name (case-insensitive).
         *
         * @param  name the name such as html, xml, json, js, sql, or url
         * @return      the kind, or <tt>null</tt> if the name is not a supported kind
         */
        public static Kind fromName(String name) {
            if (name == null) {
                return null;
            }
            String key = name.trim().toUpperCase(Locale.ROOT);
            if ("JAVASCRIPT".equals(key)) {
                return JS;
            }
            for (Kind kind : values()) {
                if (kind.name().equals(key)) {
                    return kind;
                }
            }
            return null;
        }

        /**
         * Escapes the value according to this kind.
         *
         * @param  value the value to escape
         * @return       the escaped value, or <tt>null</tt> if the value is <tt>null</tt>
         */
        public String escape(String value) {
            return EscapeHelper.escape(this, value);
        }
    }

    private EscapeHelper() {
    }

    /**
     * Escapes the value according to the given kind.
     *
     * @param  kind  the escape kind
     * @param  value the value to escape
     * @return       the escaped value, or <tt>null</tt> if the value is <tt>null</tt>
     */
    public static String escape(Kind kind, String value) {
        if (value == null) {
            return null;
        }
        return switch (kind) {
            case HTML -> html(value);
            case XML -> xml(value);
            case JSON -> json(value);
            case JS -> js(value);
            case SQL -> sql(value);
            case URL -> url(value);
        };
    }

    /**
     * Escapes the value for use in HTML text or attribute values: {@code &}, {@code <}, {@code >}, {@code "} and
     * {@code '} are replaced with their entities.
     */
    public static String html(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder sb = null;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            String rep = switch (ch) {
                case '&' -> "&amp;";
                case '<' -> "&lt;";
                case '>' -> "&gt;";
                case '"' -> "&quot;";
                case '\'' -> "&#39;";
                default -> null;
            };
            sb = append(sb, value, i, rep);
        }
        return sb != null ? sb.toString() : value;
    }

    /**
     * Escapes the value for use in XML text or attribute values: {@code &}, {@code <}, {@code >}, {@code "} and
     * {@code '} are replaced with the five predefined XML entities.
     */
    public static String xml(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder sb = null;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            String rep = switch (ch) {
                case '&' -> "&amp;";
                case '<' -> "&lt;";
                case '>' -> "&gt;";
                case '"' -> "&quot;";
                case '\'' -> "&apos;";
                default -> null;
            };
            sb = append(sb, value, i, rep);
        }
        return sb != null ? sb.toString() : value;
    }

    /**
     * Escapes the value for use inside a JSON string literal: quotes, backslashes and control characters are
     * backslash-escaped. The surrounding quotes are not added.
     */
    public static String json(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder sb = null;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            String rep = jsonEscape(ch);
            sb = append(sb, value, i, rep);
        }
        return sb != null ? sb.toString() : value;
    }

    /**
     * Escapes the value for use inside a JavaScript string literal: the JSON rules plus single quotes are
     * backslash-escaped, and {@code </} is broken up so the value cannot terminate an enclosing {@code <script>}
     * element. The surrounding quotes are not added.
     */
    public static String js(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder sb = null;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            String rep;
            if (ch == '\'') {
                rep = "\\'";
            } else if (ch == '/' && i > 0 && value.charAt(i - 1) == '<') {
                rep = "\\/";
            } else {
                rep = jsonEscape(ch);
            }
            sb = append(sb, value, i, rep);
        }
        return sb != null ? sb.toString() : value;
    }

    /**
     * Escapes the value for use inside a single-quoted SQL string literal by doubling single quotes. This is a
     * last-resort measure only; prefer parameterized queries wherever possible.
     */
    public static String sql(String value) {
        if (value == null) {
            return null;
        }
        if (value.indexOf('\'') == -1) {
            return value;
        }
        return value.replace("'", "''");
    }

    /**
     * Escapes the value for use as a URL query parameter or path segment using percent-encoding (RFC 3986). Unlike
     * {@link URLEncoder}, a space is encoded as {@code %20} rather than {@code +}.
     */
    public static String url(String value) {
        if (value == null) {
            return null;
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String jsonEscape(char ch) {
        return switch (ch) {
            case '"' -> "\\\"";
            case '\\' -> "\\\\";
            case '\b' -> "\\b";
            case '\f' -> "\\f";
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            case '\t' -> "\\t";
            default -> ch < 0x20 ? String.format("\\u%04x", (int) ch) : null;
        };
    }

    private static StringBuilder append(StringBuilder sb, String value, int i, String rep) {
        if (rep == null) {
            if (sb != null) {
                sb.append(value.charAt(i));
            }
            return sb;
        }
        if (sb == null) {
            sb = new StringBuilder(value.length() + 16);
            sb.append(value, 0, i);
        }
        sb.append(rep);
        return sb;
    }

}
