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
 * The route chain the cursor is in, in a Java route, read from the text above it so it works while the file does not
 * parse (CAMEL-25241): the calls of the statement up to the cursor, from(...).split(...).to(...)., and the name being
 * typed after the last dot. Only the calls of the chain itself count; what is inside their arguments is skipped.
 *
 * @param calls    the calls of the chain before the cursor, the first one starting it (from, onException, rest, or
 *                 body, header... in an argument)
 * @param prefix   the name typed after the last dot, which the completion replaces
 * @param argument whether the chain is in the argument of a call (.split(body().|)) rather than a statement
 */
record JavaChainContext(List<Call> calls, String prefix, boolean argument) {

    /**
     * A call of the chain.
     *
     * @param name      the method name
     * @param arguments the number of arguments given
     */
    record Call(String name, int arguments) {
    }

    /** The chain at the cursor, or null when the cursor is not after a dot of a route chain. */
    static JavaChainContext at(List<String> lines, int row, int col) {
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
        String text = code(sb.toString());
        if (text == null) {
            return null;
        }
        // the statement the cursor is in starts after the last ; { or } outside of any parentheses
        // where the statement starts, and where each argument open at the cursor starts: after its ( or , or, in a
        // lambda body inside the argument, after the last ; { or }
        int start = 0;
        List<Integer> arguments = new ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') {
                arguments.add(i + 1);
            } else if (c == ')') {
                if (!arguments.isEmpty()) {
                    arguments.remove(arguments.size() - 1);
                }
            } else if (c == ',' && !arguments.isEmpty()) {
                arguments.set(arguments.size() - 1, i + 1);
            } else if (c == ';' || c == '{' || c == '}') {
                if (arguments.isEmpty()) {
                    start = i + 1;
                } else {
                    arguments.set(arguments.size() - 1, i + 1);
                }
            }
        }
        boolean argument = !arguments.isEmpty();
        String statement = text.substring(argument ? arguments.get(arguments.size() - 1) : start);
        int end = statement.length();
        int p = end;
        while (p > 0 && Character.isJavaIdentifierPart(statement.charAt(p - 1))) {
            p--;
        }
        String prefix = statement.substring(p, end);
        int dot = p - 1;
        while (dot >= 0 && Character.isWhitespace(statement.charAt(dot))) {
            dot--;
        }
        if (dot < 0 || statement.charAt(dot) != '.') {
            return null;
        }
        List<Call> calls = calls(statement.substring(0, dot));
        if (calls == null || calls.isEmpty()) {
            return null;
        }
        return new JavaChainContext(calls, prefix, argument);
    }

    /** The calls of a chain: name(args).name(args)..., null when the text is no such chain. */
    private static List<Call> calls(String chain) {
        List<Call> calls = new ArrayList<>();
        int i = 0;
        int n = chain.length();
        boolean first = true;
        while (i < n) {
            while (i < n && Character.isWhitespace(chain.charAt(i))) {
                i++;
            }
            if (i == n) {
                break;
            }
            if (!first) {
                if (chain.charAt(i) != '.') {
                    return null;
                }
                i++;
                while (i < n && Character.isWhitespace(chain.charAt(i))) {
                    i++;
                }
            }
            // a type argument before the name: .<String> foo()
            if (i < n && chain.charAt(i) == '<') {
                int close = chain.indexOf('>', i);
                if (close < 0) {
                    return null;
                }
                i = close + 1;
                while (i < n && Character.isWhitespace(chain.charAt(i))) {
                    i++;
                }
            }
            int nameStart = i;
            while (i < n && Character.isJavaIdentifierPart(chain.charAt(i))) {
                i++;
            }
            String name = chain.substring(nameStart, i);
            if (name.isEmpty()) {
                return null;
            }
            while (i < n && Character.isWhitespace(chain.charAt(i))) {
                i++;
            }
            if (i >= n || chain.charAt(i) != '(') {
                // this.from(...) or a field: only the leading this is skipped
                if (first && "this".equals(name) && i < n && chain.charAt(i) == '.') {
                    i++;
                    continue;
                }
                return null;
            }
            int depth = 0;
            // the commas of a lambda or anonymous class body ({...}) and of type arguments (<String, String>) are
            // no argument separators
            int braces = 0;
            int angles = 0;
            int arguments = 0;
            boolean any = false;
            int j = i;
            for (; j < n; j++) {
                char c = chain.charAt(j);
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                    if (depth == 0) {
                        break;
                    }
                } else if (c == '{') {
                    braces++;
                } else if (c == '}') {
                    braces = Math.max(0, braces - 1);
                } else if (c == '<' && j > 0 && Character.isJavaIdentifierPart(chain.charAt(j - 1))
                        && j + 1 < n && (Character.isJavaIdentifierStart(chain.charAt(j + 1)) || chain.charAt(j + 1) == '?'
                                || chain.charAt(j + 1) == '>')) {
                    angles++;
                } else if (c == '>' && angles > 0) {
                    angles--;
                } else if (depth == 1 && braces == 0 && angles == 0 && c == ',') {
                    arguments++;
                }
                if (depth >= 1 && c != '(' && !Character.isWhitespace(c)) {
                    any = true;
                }
            }
            if (j >= n) {
                return null;
            }
            calls.add(new Call(name, any ? arguments + 1 : 0));
            i = j + 1;
            first = false;
        }
        return calls;
    }

    /**
     * The text with its comments, strings and character literals blanked (kept as spaces, or "" for strings), so their
     * brackets and dots do not count; null when the cursor is in one of them.
     */
    static String code(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (c == '/' && i + 1 < n && text.charAt(i + 1) == '/') {
                int nl = text.indexOf('\n', i);
                if (nl < 0) {
                    return null;
                }
                i = nl;
            } else if (c == '/' && i + 1 < n && text.charAt(i + 1) == '*') {
                int close = text.indexOf("*/", i + 2);
                if (close < 0) {
                    return null;
                }
                sb.append(' ');
                i = close + 2;
            } else if (text.startsWith("\"\"\"", i)) {
                int close = text.indexOf("\"\"\"", i + 3);
                if (close < 0) {
                    return null;
                }
                sb.append("\"\"");
                i = close + 3;
            } else if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < n && text.charAt(j) != c && text.charAt(j) != '\n') {
                    if (text.charAt(j) == '\\') {
                        j++;
                    }
                    j++;
                }
                if (j >= n || text.charAt(j) != c) {
                    return null;
                }
                sb.append(c).append(c);
                i = j + 1;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }
}
