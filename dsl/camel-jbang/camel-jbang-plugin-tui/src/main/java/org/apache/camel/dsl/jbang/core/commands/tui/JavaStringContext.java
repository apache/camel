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
 * The string literal of a Java line the cursor is in, and the method it is an argument of: to("kafka:orders?bro| is in
 * to, with kafka:orders?bro before the cursor. Read from the line alone, so it works on a line being typed, which does
 * not parse (CAMEL-25208).
 *
 * @param call   the method the literal is the first text of the arguments of, such as to or from
 * @param before the text of the literal before the cursor
 */
record JavaStringContext(String call, String before) {

    /** The DSL methods whose argument is an endpoint uri. */
    static final Set<String> ENDPOINT_CALLS = Set.of("from", "to", "toD", "wireTap", "enrich", "pollEnrich", "poll");
    /** The ones of them that consume from their endpoint. */
    static final Set<String> CONSUMER_CALLS = Set.of("from", "pollEnrich", "poll");

    /** The context at the cursor, or null when the cursor is not in a string literal given to a method. */
    static JavaStringContext at(String line, int col) {
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
        return new JavaStringContext(call, line.substring(open + 1, col));
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
