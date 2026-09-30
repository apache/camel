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

import java.util.Set;

/**
 * The endpoint uri the cursor is in, read from the line alone so it works on a line being typed, which does not parse
 * (CAMEL-25208): in Java the string literal given to a DSL method, to("kafka:orders?bro| is in to with kafka:orders?bro
 * before the cursor; in XML the uri attribute of an element, &lt;to uri="kafka:orders?bro| is in to (its &amp;amp; read
 * as &amp;).
 *
 * @param call   the DSL method (Java) or element (XML) of the uri, such as to or from
 * @param before the text of the uri before the cursor
 */
record EndpointUriContext(String call, String before) {

    /** The DSL methods and elements whose argument or attribute is an endpoint uri. */
    static final Set<String> ENDPOINT_CALLS = Set.of("from", "to", "toD", "wireTap", "enrich", "pollEnrich", "poll");
    /** The ones of them that consume from their endpoint. */
    static final Set<String> CONSUMER_CALLS = Set.of("from", "pollEnrich", "poll");

    /** The context at the cursor in a Java line, or null when it is not in a string literal given to a method. */
    static EndpointUriContext inJava(String line, int col) {
        if (line == null || col < 0 || col > line.length()) {
            return null;
        }
        int open = -1;
        for (int i = 0; i < col; i++) {
            char c = line.charAt(i);
            if (open >= 0) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    open = -1;
                }
            } else if (c == '"') {
                open = i;
            } else if (c == '/' && i + 1 < line.length() && line.charAt(i + 1) == '/') {
                // a line comment: no string literal after it
                return null;
            }
        }
        if (open < 0) {
            return null;
        }
        String head = line.substring(0, open).stripTrailing();
        if (!head.endsWith("(")) {
            return null;
        }
        head = head.substring(0, head.length() - 1).stripTrailing();
        int start = head.length();
        while (start > 0 && Character.isJavaIdentifierPart(head.charAt(start - 1))) {
            start--;
        }
        String call = head.substring(start);
        if (call.isEmpty()) {
            return null;
        }
        return new EndpointUriContext(call, line.substring(open + 1, col));
    }

    /** The context at the cursor in an XML line, or null when it is not in the uri attribute of an element. */
    static EndpointUriContext inXml(String line, int col) {
        if (line == null || col < 0 || col > line.length()) {
            return null;
        }
        String head = line.substring(0, col);
        int attr = Math.max(head.lastIndexOf("uri=\""), head.lastIndexOf("uri='"));
        if (attr < 0 || attr > 0 && !Character.isWhitespace(head.charAt(attr - 1))) {
            return null;
        }
        char quote = head.charAt(attr + 4);
        String value = head.substring(attr + 5);
        if (value.indexOf(quote) >= 0) {
            // the attribute is closed before the cursor
            return null;
        }
        int lt = head.lastIndexOf('<', attr);
        if (lt < 0 || head.indexOf('>', lt) >= 0 && head.indexOf('>', lt) < attr) {
            return null;
        }
        int start = lt + 1;
        int end = start;
        while (end < head.length() && (Character.isLetterOrDigit(head.charAt(end)) || head.charAt(end) == ':'
                || head.charAt(end) == '-')) {
            end++;
        }
        String element = head.substring(start, end);
        // a namespace prefix: <camel:to uri="...
        element = element.substring(element.indexOf(':') + 1);
        if (element.isEmpty()) {
            return null;
        }
        return new EndpointUriContext(element, value.replace("&amp;", "&"));
    }

    /** Whether the literal is the endpoint uri of a DSL method. */
    boolean isEndpoint() {
        return ENDPOINT_CALLS.contains(call);
    }

    /** consumer or producer, as the completions of the endpoint options are filtered. */
    String role() {
        return CONSUMER_CALLS.contains(call) ? "consumer" : "producer";
    }
}
