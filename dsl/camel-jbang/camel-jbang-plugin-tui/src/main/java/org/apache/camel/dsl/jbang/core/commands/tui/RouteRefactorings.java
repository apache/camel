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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The refactorings of the source editor (Ctrl+R) for Java and XML routes (CAMEL-25256), as {@link SourceRefactorings}
 * has them for YAML: the endpoint URI of a line, the string literal (Java) or attribute value (XML) at the cursor, and
 * the element block of an XML step, with what replaces them.
 */
final class RouteRefactorings {

    /**
     * A quoted value on a line: where its text starts and ends (inside the quotes), and the text as the route means it
     * (escapes and entities undone).
     */
    record Value(int start, int end, String text) {
    }

    /** The DSL methods (Java) and elements (XML) whose argument or uri attribute is an endpoint URI. */
    private static final String ENDPOINT_CALLS = "from|to|toD|wireTap|enrich|pollEnrich|poll";
    private static final Pattern JAVA_URI = Pattern.compile(
            "\\b(?:" + ENDPOINT_CALLS + ")\\s*\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern XML_URI = Pattern.compile(
            "<(?:[\\w-]+:)?(?:" + ENDPOINT_CALLS + ")\\b[^>]*?\\suri\\s*=\\s*(\"([^\"]*)\"|'([^']*)')");
    private static final Pattern XML_ATTRIBUTE = Pattern.compile("[\\w:.-]+\\s*=\\s*(\"([^\"]*)\"|'([^']*)')");

    /** Elements that are no step to extract: the route, its input, and the parts of the steps that hold them. */
    private static final Set<String> NOT_EXTRACTABLE = Set.of(
            "routes", "route", "camel", "beans", "from", "when", "otherwise", "doCatch", "doFinally", "onFallback",
            "expression", "simple", "constant", "header", "exchangeProperty", "variable", "jsonpath", "xpath",
            "groovy", "language", "method", "tokenize", "jq", "ref", "datasonnet", "spel", "mvel", "ognl", "js",
            "python", "java", "wasm", "xquery", "xtokenize", "hl7terser", "jactl", "csimple", "joor");

    private RouteRefactorings() {
    }

    // ---- the endpoint URI of a line ----

    /** The endpoint URI on the line: the string of from, to, toD... in Java, the uri attribute of those in XML. */
    static Value uri(String dsl, String line) {
        if (line == null) {
            return null;
        }
        Matcher m = ("xml".equals(dsl) ? XML_URI : JAVA_URI).matcher(line);
        if (!m.find()) {
            return null;
        }
        if ("xml".equals(dsl)) {
            int group = m.group(2) != null ? 2 : 3;
            return new Value(m.start(group), m.end(group), xmlDecode(m.group(group)));
        }
        return new Value(m.start(1), m.end(1), javaUnescape(m.group(1)));
    }

    // ---- the value at the cursor ----

    /** The string literal (Java) or attribute value (XML) the cursor is on; null when it is on none. */
    static Value valueAt(String dsl, String line, int col) {
        if (line == null || col < 0) {
            return null;
        }
        if ("xml".equals(dsl)) {
            Matcher m = XML_ATTRIBUTE.matcher(line);
            while (m.find()) {
                int group = m.group(2) != null ? 2 : 3;
                // on the attribute name or in its value, the quotes included
                if (col >= m.start() && col <= m.end()) {
                    return new Value(m.start(group), m.end(group), xmlDecode(m.group(group)));
                }
            }
            return null;
        }
        int open = -1;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (open < 0 && c == '/' && i + 1 < line.length() && line.charAt(i + 1) == '/') {
                return null;
            }
            if (open >= 0 && c == '\\') {
                i++;
            } else if (c == '"') {
                if (open < 0) {
                    open = i;
                } else {
                    if (col >= open && col <= i + 1) {
                        return new Value(open + 1, i, javaUnescape(line.substring(open + 1, i)));
                    }
                    open = -1;
                }
            }
        }
        return null;
    }

    /** Whether a value can be extracted to a property: something, and no placeholder already. */
    static boolean isExtractable(Value v) {
        return v != null && !v.text().isBlank() && !(v.text().startsWith("{{") && v.text().endsWith("}}"));
    }

    /** The line with the value replaced, written as the DSL quotes it (escaped in Java, entities in XML). */
    static String replace(String dsl, String line, Value v, String text) {
        String written = "xml".equals(dsl) ? xmlEncode(text) : javaEscape(text);
        return line.substring(0, v.start()) + written + line.substring(v.end());
    }

    /**
     * The application.properties a property of the route file goes to: src/main/resources of a Maven or Gradle layout,
     * else the file's own folder, as camel run reads it.
     */
    static Path propertiesFile(Path routeFile) {
        Path dir = routeFile.toAbsolutePath().getParent();
        for (Path p = dir; p != null; p = p.getParent()) {
            if (p.getFileName() != null && "src".equals(p.getFileName().toString())) {
                return p.resolve("main/resources/application.properties");
            }
        }
        return dir.resolve("application.properties");
    }

    // ---- an XML step block ----

    /** The rows of an XML element block, its start tag on the first. */
    record Block(int start, int end, String element, int indent) {
    }

    private static final Pattern COMMENT = Pattern.compile("<!--.*?-->");
    private static final Pattern PROCESSING_INSTRUCTION = Pattern.compile("<\\?.*?\\?>");
    private static final Pattern TAG = Pattern.compile("<(/?)[\\w:-]+[^>]*?(/?)>");
    private static final Pattern START_TAG = Pattern.compile("^(\\s*)<(?:[\\w-]+:)?([\\w-]+)");

    /** The step block whose start tag is on the row; null when the row holds none, or no step to extract. */
    static Block xmlStep(List<String> lines, int row) {
        if (row < 0 || row >= lines.size()) {
            return null;
        }
        Matcher m = START_TAG.matcher(lines.get(row));
        if (!m.find() || NOT_EXTRACTABLE.contains(m.group(2))) {
            return null;
        }
        // must be inside a route
        XmlCompletionContext c = XmlCompletionContext.at(lines, row, m.start(2) - 1);
        if (c == null || !c.path().contains("route")) {
            return null;
        }
        int depth = 0;
        boolean inComment = false;
        for (int r = row; r < lines.size(); r++) {
            // a comment may span lines: its tags do not count
            StringBuilder code = new StringBuilder();
            String line = lines.get(r);
            int i = 0;
            while (i < line.length()) {
                if (inComment) {
                    int close = line.indexOf("-->", i);
                    if (close < 0) {
                        i = line.length();
                    } else {
                        inComment = false;
                        i = close + 3;
                    }
                } else {
                    int open = line.indexOf("<!--", i);
                    if (open < 0) {
                        code.append(line, i, line.length());
                        i = line.length();
                    } else {
                        code.append(line, i, open);
                        inComment = true;
                        i = open + 4;
                    }
                }
            }
            depth += tagBalance(code.toString());
            if (depth <= 0) {
                return new Block(row, r, m.group(2), m.group(1).length());
            }
        }
        return null;
    }

    /**
     * The opening minus the closing tags of a line: self-closing tags, comments and processing instructions count 0.
     */
    static int tagBalance(String line) {
        int balance = 0;
        String s = PROCESSING_INSTRUCTION.matcher(COMMENT.matcher(line).replaceAll("")).replaceAll("");
        Matcher m = TAG.matcher(s);
        while (m.find()) {
            if (!m.group(1).isEmpty()) {
                balance--;
            } else if (m.group(2).isEmpty()) {
                balance++;
            }
        }
        return balance;
    }

    /** A new XML routes file with the block as a route consuming from direct:name. */
    static String xmlRouteFile(String name, List<String> block, int indent) {
        StringBuilder sb = new StringBuilder();
        sb.append("<routes xmlns=\"http://camel.apache.org/schema/xml-io\">\n");
        sb.append(xmlRoute(name, block, indent));
        sb.append("</routes>\n");
        return sb.toString();
    }

    /** A route of the block consuming from direct:name, indented as a route of a routes file. */
    static String xmlRoute(String name, List<String> block, int indent) {
        StringBuilder sb = new StringBuilder();
        sb.append("    <route id=\"").append(xmlEncode(name)).append("\">\n");
        sb.append("        <from uri=\"direct:").append(xmlEncode(name)).append("\"/>\n");
        for (String line : block) {
            String body
                    = line.length() >= indent && line.substring(0, indent).isBlank() ? line.substring(indent) : line.strip();
            sb.append(body.isBlank() ? "" : "        " + body).append('\n');
        }
        sb.append("    </route>\n");
        return sb.toString();
    }

    /** An existing XML routes file with the route added before its end tag; null when it has no routes end tag. */
    static String addXmlRoute(String existing, String route) {
        int end = existing.lastIndexOf("</routes>");
        if (end < 0) {
            return null;
        }
        return existing.substring(0, end) + route + existing.substring(end);
    }

    /** The lines with the block replaced by a to of direct:name, at the block's indent. */
    static List<String> replaceWithTo(List<String> lines, Block block, String name) {
        List<String> answer = new ArrayList<>(lines.subList(0, block.start()));
        answer.add(" ".repeat(block.indent()) + "<to uri=\"direct:" + xmlEncode(name) + "\"/>");
        answer.addAll(lines.subList(block.end() + 1, lines.size()));
        return answer;
    }

    // ---- quoting ----

    static String xmlDecode(String s) {
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
                .replace("&amp;", "&");
    }

    static String xmlEncode(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** The value of the text of a Java string literal: its escapes (newline, tab, quote, unicode, octal...) undone. */
    static String javaUnescape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 >= s.length()) {
                sb.append(c);
                continue;
            }
            char n = s.charAt(++i);
            switch (n) {
                case 'n' -> sb.append('\n');
                case 't' -> sb.append('\t');
                case 'r' -> sb.append('\r');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 's' -> sb.append(' ');
                case 'u' -> {
                    int j = i;
                    while (j < s.length() && s.charAt(j) == 'u') {
                        j++;
                    }
                    if (j + 4 <= s.length()) {
                        sb.append((char) Integer.parseInt(s.substring(j, j + 4), 16));
                        i = j + 3;
                    } else {
                        sb.append('\\').append(n);
                    }
                }
                default -> {
                    if (n >= '0' && n <= '7') {
                        // octal: up to three digits, at most \377
                        int j = i;
                        int max = n <= '3' ? 3 : 2;
                        while (j < s.length() && j - i < max && s.charAt(j) >= '0' && s.charAt(j) <= '7') {
                            j++;
                        }
                        sb.append((char) Integer.parseInt(s.substring(i, j), 8));
                        i = j - 1;
                    } else {
                        // \" \' \\
                        sb.append(n);
                    }
                }
            }
        }
        return sb.toString();
    }

    /** A value as a .properties file writes it: backslashes and line breaks escaped, a leading space kept. */
    static String propertiesValue(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\f' -> sb.append("\\f");
                case ' ' -> sb.append(i == 0 ? "\\ " : " ");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    static String javaEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
