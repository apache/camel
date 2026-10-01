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

import java.util.ArrayList;
import java.util.List;

/**
 * Where the cursor is in an XML route, read from the text above it so it works while the file does not parse
 * (CAMEL-25240): the elements open around it (routes, route, choice...), and whether an element name, an attribute name
 * or an attribute value goes at the cursor.
 *
 * @param kind      what goes at the cursor
 * @param path      the elements open around the cursor, the outermost first, without namespace prefixes
 * @param element   the element whose start tag the cursor is in (attribute kinds), else null
 * @param attribute the attribute whose value the cursor is in (value kind), else null
 * @param prefix    the text before the cursor that the completion replaces
 * @param given     the attributes the start tag has already (attribute name kind)
 * @param open      whether the < of a new element is there already (element kind)
 * @param ns        the namespace prefix typed with the new element (camel: of camel:to), else empty
 * @param siblings  the elements the parent has already above the cursor (element kind), such as the simple of a when
 */
record XmlCompletionContext(
        Kind kind, List<String> path, String element, String attribute, String prefix, List<String> given,
        boolean open, String ns, List<String> siblings) {

    enum Kind {
        ELEMENT,
        ATTRIBUTE,
        VALUE
    }

    /** The context at the cursor, or null when nothing completes there (text, comments, end tags). */
    static XmlCompletionContext at(List<String> lines, int row, int col) {
        if (lines == null || row < 0 || row >= lines.size()) {
            return null;
        }
        String line = lines.get(row);
        if (col < 0 || col > line.length()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < row; r++) {
            sb.append(lines.get(r)).append('\n');
        }
        sb.append(line, 0, col);
        String text = sb.toString();

        List<String> path = new ArrayList<>();
        // the children seen so far of each open element, and of the top level
        List<List<String>> kids = new ArrayList<>();
        kids.add(new ArrayList<>());
        int textStart = 0;
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (c != '<') {
                i++;
                continue;
            }
            if (text.startsWith("<!--", i)) {
                int end = text.indexOf("-->", i + 4);
                if (end < 0) {
                    return null;
                }
                i = end + 3;
                textStart = i;
                continue;
            }
            if (text.startsWith("<![CDATA[", i)) {
                int end = text.indexOf("]]>", i + 9);
                if (end < 0) {
                    return null;
                }
                i = end + 3;
                textStart = i;
                continue;
            }
            if (text.startsWith("<?", i) || text.startsWith("<!", i)) {
                int end = text.indexOf('>', i + 2);
                if (end < 0) {
                    return null;
                }
                i = end + 1;
                textStart = i;
                continue;
            }
            if (text.startsWith("</", i)) {
                int end = text.indexOf('>', i + 2);
                if (end < 0) {
                    // typing an end tag
                    return null;
                }
                String name = localName(text.substring(i + 2, end).strip());
                int at = path.lastIndexOf(name);
                if (at >= 0) {
                    while (path.size() > at) {
                        path.remove(path.size() - 1);
                        kids.remove(kids.size() - 1);
                    }
                }
                i = end + 1;
                textStart = i;
                continue;
            }
            // a start tag: its name, then its attributes up to > or />, a > in a quoted value not ending it
            int nameEnd = i + 1;
            while (nameEnd < n && isNameChar(text.charAt(nameEnd))) {
                nameEnd++;
            }
            String qname = text.substring(i + 1, nameEnd);
            if (nameEnd == n) {
                // typing the name of a new element
                int colon = qname.indexOf(':');
                return new XmlCompletionContext(
                        Kind.ELEMENT, path, null, null, qname.substring(colon + 1), List.of(),
                        true, qname.substring(0, colon + 1), kids.get(kids.size() - 1));
            }
            List<String> given = new ArrayList<>();
            int j = nameEnd;
            char quote = 0;
            int valueStart = -1;
            String attribute = null;
            int attrStart = -1;
            boolean closed = false;
            boolean selfClosing = false;
            while (j < n) {
                char d = text.charAt(j);
                if (quote != 0) {
                    if (d == quote) {
                        quote = 0;
                        valueStart = -1;
                    }
                } else if (d == '"' || d == '\'') {
                    quote = d;
                    valueStart = j + 1;
                } else if (d == '>') {
                    closed = true;
                    selfClosing = text.charAt(j - 1) == '/';
                    break;
                } else if (Character.isWhitespace(d)) {
                    attrStart = -1;
                } else if (d == '=') {
                    if (attrStart >= 0) {
                        attribute = text.substring(attrStart, j);
                        given.add(attribute);
                    }
                    attrStart = -1;
                } else if (attrStart < 0 && isNameChar(d)) {
                    attrStart = j;
                }
                j++;
            }
            String name = localName(qname);
            if (!closed) {
                // the cursor is in this start tag
                if (quote != 0) {
                    return new XmlCompletionContext(
                            Kind.VALUE, path, name, attribute, text.substring(valueStart), given, true, "", List.of());
                }
                String typed = attrStart >= 0 ? text.substring(attrStart) : "";
                if (attrStart < 0 && !Character.isWhitespace(text.charAt(n - 1))) {
                    // right after a value's closing quote: no attribute goes there without a space
                    return null;
                }
                return new XmlCompletionContext(Kind.ATTRIBUTE, path, name, null, typed, given, true, "", List.of());
            }
            kids.get(kids.size() - 1).add(name);
            if (!selfClosing) {
                path.add(name);
                kids.add(new ArrayList<>());
            }
            i = j + 1;
            textStart = i;
        }
        // in the text of an element: a new element goes on a line with nothing else before the cursor
        String tail = text.substring(textStart);
        int nl = tail.lastIndexOf('\n');
        if (!tail.substring(nl + 1).isBlank()) {
            return null;
        }
        return new XmlCompletionContext(
                Kind.ELEMENT, path, null, null, "", List.of(), false, "", kids.get(kids.size() - 1));
    }

    /** The parent element of what goes at the cursor (element kind), or the element of the start tag. */
    String parent() {
        return path.isEmpty() ? null : path.get(path.size() - 1);
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.' || c == ':';
    }

    private static String localName(String qname) {
        int colon = qname.indexOf(':');
        return colon >= 0 ? qname.substring(colon + 1) : qname;
    }
}
