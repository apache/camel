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

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Helper for escaping special characters so a value can be embedded safely in HTML, XML, JSON, JavaScript, SQL or a
 * URL, and for unescaping such a value again.
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
         * @return      the kind, or <code>null</code> if the name is not a supported kind
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
         * @return       the escaped value, or <code>null</code> if the value is <code>null</code>
         */
        public String escape(String value) {
            return EscapeHelper.escape(this, value);
        }

        /**
         * Unescapes the value according to this kind.
         *
         * @param  value the value to unescape
         * @return       the unescaped value, or <code>null</code> if the value is <code>null</code>
         */
        public String unescape(String value) {
            return EscapeHelper.unescape(this, value);
        }
    }

    private EscapeHelper() {
    }

    /**
     * Escapes the value according to the given kind.
     *
     * @param  kind  the escape kind
     * @param  value the value to escape
     * @return       the escaped value, or <code>null</code> if the value is <code>null</code>
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
     * Unescapes the value according to the given kind: the inverse of {@link #escape(Kind, String)}. A sequence that is
     * not a valid escape of the kind is kept as it is.
     *
     * @param  kind  the escape kind
     * @param  value the value to unescape
     * @return       the unescaped value, or <code>null</code> if the value is <code>null</code>
     */
    public static String unescape(Kind kind, String value) {
        if (value == null) {
            return null;
        }
        return switch (kind) {
            case HTML, XML -> unescapeEntities(value);
            case JSON -> unescapeJson(value);
            case JS -> unescapeJs(value);
            case SQL -> unescapeSql(value);
            case URL -> unescapeUrl(value);
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

    /**
     * Unescapes HTML or XML: the five predefined entities {@code &amp;}, {@code &lt;}, {@code &gt;}, {@code &quot;} and
     * {@code &apos;}, and the numeric character references such as {@code &#39;} and {@code &#x27;}. Other named
     * entities, such as the HTML {@code &nbsp;}, are kept as they are.
     */
    public static String unescapeEntities(String value) {
        if (value == null || value.indexOf('&') == -1) {
            return value;
        }
        StringBuilder sb = new StringBuilder(value.length());
        int i = 0;
        while (i < value.length()) {
            char ch = value.charAt(i);
            int end = ch == '&' ? value.indexOf(';', i + 1) : -1;
            // the longest entity is a numeric reference such as &#x10FFFF;
            String rep = end != -1 && end - i <= 10 ? entity(value.substring(i + 1, end)) : null;
            if (rep != null) {
                sb.append(rep);
                i = end + 1;
            } else {
                sb.append(ch);
                i++;
            }
        }
        return sb.toString();
    }

    /**
     * Unescapes a value that was escaped for use inside a JSON string literal: the backslash escapes {@code \"},
     * {@code \\}, {@code \/}, {@code \b}, {@code \f}, {@code \n}, {@code \r}, {@code \t} and {@code \u0000}.
     */
    public static String unescapeJson(String value) {
        return unescapeBackslash(value, false);
    }

    /**
     * Unescapes a value that was escaped for use inside a JavaScript string literal: the JSON escapes and {@code \'}.
     */
    public static String unescapeJs(String value) {
        return unescapeBackslash(value, true);
    }

    /**
     * Unescapes a value that was escaped for use inside a single-quoted SQL string literal: doubled single quotes
     * become one.
     */
    public static String unescapeSql(String value) {
        if (value == null || !value.contains("''")) {
            return value;
        }
        return value.replace("''", "'");
    }

    /**
     * Unescapes a percent-encoded value: {@code %XX} sequences are decoded as UTF-8, and a {@code +} becomes a space as
     * in a URL query. A {@code %} that is not followed by two hex digits is kept as it is.
     */
    public static String unescapeUrl(String value) {
        if (value == null || value.indexOf('%') == -1 && value.indexOf('+') == -1) {
            return value;
        }
        StringBuilder sb = new StringBuilder(value.length());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int i = 0;
        while (i < value.length()) {
            char ch = value.charAt(i);
            int hi = ch == '%' && i + 2 < value.length() ? hex(value.charAt(i + 1)) : -1;
            int lo = hi != -1 ? hex(value.charAt(i + 2)) : -1;
            if (lo != -1) {
                bytes.write(hi * 16 + lo);
                i += 3;
                continue;
            }
            if (bytes.size() > 0) {
                sb.append(bytes.toString(StandardCharsets.UTF_8));
                bytes.reset();
            }
            sb.append(ch == '+' ? ' ' : ch);
            i++;
        }
        if (bytes.size() > 0) {
            sb.append(bytes.toString(StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private static String entity(String name) {
        switch (name) {
            case "amp":
                return "&";
            case "lt":
                return "<";
            case "gt":
                return ">";
            case "quot":
                return "\"";
            case "apos":
                return "'";
            default:
                break;
        }
        if (name.length() < 2 || name.charAt(0) != '#') {
            return null;
        }
        boolean hex = name.charAt(1) == 'x' || name.charAt(1) == 'X';
        String digits = name.substring(hex ? 2 : 1);
        if (digits.isEmpty()) {
            return null;
        }
        int code = 0;
        for (int i = 0; i < digits.length(); i++) {
            int d = hex ? hex(digits.charAt(i)) : decimal(digits.charAt(i));
            if (d == -1) {
                return null;
            }
            code = code * (hex ? 16 : 10) + d;
            if (code > Character.MAX_CODE_POINT) {
                return null;
            }
        }
        return Character.toString(code);
    }

    private static String unescapeBackslash(String value, boolean js) {
        if (value == null || value.indexOf('\\') == -1) {
            return value;
        }
        StringBuilder sb = new StringBuilder(value.length());
        int i = 0;
        while (i < value.length()) {
            char ch = value.charAt(i);
            if (ch != '\\' || i + 1 == value.length()) {
                sb.append(ch);
                i++;
                continue;
            }
            char next = value.charAt(i + 1);
            String rep = switch (next) {
                case '"' -> "\"";
                case '\\' -> "\\";
                case '/' -> "/";
                case 'b' -> "\b";
                case 'f' -> "\f";
                case 'n' -> "\n";
                case 'r' -> "\r";
                case 't' -> "\t";
                case '\'' -> js ? "'" : null;
                case 'u' -> unicode(value, i + 2);
                default -> null;
            };
            if (rep == null) {
                sb.append(ch);
                i++;
            } else {
                sb.append(rep);
                i += next == 'u' ? 6 : 2;
            }
        }
        return sb.toString();
    }

    private static String unicode(String value, int start) {
        if (start + 4 > value.length()) {
            return null;
        }
        int code = 0;
        for (int i = start; i < start + 4; i++) {
            int d = hex(value.charAt(i));
            if (d == -1) {
                return null;
            }
            code = code * 16 + d;
        }
        return String.valueOf((char) code);
    }

    private static int decimal(char ch) {
        return ch >= '0' && ch <= '9' ? ch - '0' : -1;
    }

    private static int hex(char ch) {
        if (ch >= 'a' && ch <= 'f') {
            return ch - 'a' + 10;
        } else if (ch >= 'A' && ch <= 'F') {
            return ch - 'A' + 10;
        }
        return decimal(ch);
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
