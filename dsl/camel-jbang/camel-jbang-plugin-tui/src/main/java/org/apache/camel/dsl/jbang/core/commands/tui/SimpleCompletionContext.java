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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The simple expression the cursor is in, read from the line alone so it works on a line being typed, which does not
 * parse, and the same in YAML, Java and XML (CAMEL-25219): ${hea| is in a function, ${header.Camel| in a header name,
 * ${body} | after a function where an operator goes.
 *
 * @param kind    what goes at the cursor
 * @param prefix  the text before the cursor that the completion replaces
 * @param closing what follows a chosen name: } after header.x, ']} after header['x
 * @param owners  the words before the expression, nearest first (when, filter, setBody, log...), to tell whether the
 *                expression is a predicate; the language words (simple, expression) are left out
 */
record SimpleCompletionContext(Kind kind, String prefix, String closing, List<String> owners) {

    enum Kind {
        FUNCTION,
        HEADER,
        PROPERTY,
        VARIABLE,
        OPERATOR
    }

    /** How many lines above the cursor are looked at for the EIP the expression belongs to. */
    private static final int OWNER_LINES = 8;
    private static final Set<String> LANGUAGE_WORDS = Set.of("simple", "expression", "language", "camel");
    private static final Pattern NAME = Pattern.compile(
            "(header|headers|in\\.header|in\\.headers|exchangeProperty|variable|variables)(\\.|\\[['\"]?)([\\w.\\-]*)");
    private static final Pattern FUNCTION = Pattern.compile("[\\w.:\\-]*");
    private static final Pattern OPERATOR = Pattern.compile("\\s+([^\\s'\"$]*)");
    private static final Pattern WORD = Pattern.compile("[A-Za-z_][\\w-]*");

    /** The context at the cursor, or null when the cursor is not where a simple completion goes. */
    static SimpleCompletionContext at(List<String> lines, int row, int col) {
        if (lines == null || row < 0 || row >= lines.size()) {
            return null;
        }
        String line = lines.get(row);
        if (col < 0 || col > line.length()) {
            return null;
        }
        // the ${ still open at the cursor, nested ones included: ${abs(${header.pr|
        List<Integer> open = new ArrayList<>();
        int firstOpen = -1;
        int lastClose = -1;
        for (int i = 0; i < col; i++) {
            char c = line.charAt(i);
            if (c == '$' && i + 1 < col && line.charAt(i + 1) == '{') {
                if (firstOpen < 0) {
                    firstOpen = i;
                }
                open.add(i + 2);
                i++;
            } else if (c == '}' && !open.isEmpty()) {
                open.remove(open.size() - 1);
                lastClose = i;
            }
        }
        if (firstOpen < 0) {
            return null;
        }
        if (!open.isEmpty()) {
            String inner = line.substring(open.get(open.size() - 1), col);
            Matcher m = NAME.matcher(inner);
            if (m.matches()) {
                Kind kind = switch (m.group(1)) {
                    case "exchangeProperty" -> Kind.PROPERTY;
                    case "variable", "variables" -> Kind.VARIABLE;
                    default -> Kind.HEADER;
                };
                String bracket = m.group(2);
                String closing = ".".equals(bracket) ? "}" : bracket.substring(1) + "]}";
                return new SimpleCompletionContext(kind, m.group(3), closing, List.of());
            }
            if (FUNCTION.matcher(inner).matches()) {
                return new SimpleCompletionContext(Kind.FUNCTION, inner, "", List.of());
            }
            // in the arguments of a function, which take expressions of their own
            return null;
        }
        Matcher m = OPERATOR.matcher(line.substring(lastClose + 1, col));
        if (!m.matches()) {
            return null;
        }
        return new SimpleCompletionContext(Kind.OPERATOR, m.group(1), " ", owners(lines, row, firstOpen));
    }

    /**
     * A function of a simple expression on a line: the text between ${ and } (to the end of the line when it is not
     * closed yet), nested functions included.
     *
     * @param text  the text of the function, such as date:now-24h or header.priority
     * @param start where the text starts in the line, after the ${
     */
    record Function(String text, int start) {
    }

    /**
     * The function the cursor is on, the innermost one when they are nested (${abs(${header.price})}), the ${ and }
     * included; null when the cursor is not in one.
     */
    static Function functionAt(String line, int col) {
        Function best = null;
        for (Function f : functions(line)) {
            int end = f.start() + f.text().length();
            if (col >= f.start() - 2 && col <= end && (best == null || f.start() > best.start())) {
                best = f;
            }
        }
        return best;
    }

    /** The functions of a line, in the order they start. */
    static List<Function> functions(String line) {
        List<Function> found = new ArrayList<>();
        List<Integer> open = new ArrayList<>();
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '$' && i + 1 < line.length() && line.charAt(i + 1) == '{') {
                open.add(i + 2);
                slots.add(found.size());
                found.add(null);
                i++;
            } else if (c == '}' && !open.isEmpty()) {
                int start = open.remove(open.size() - 1);
                found.set(slots.remove(slots.size() - 1), new Function(line.substring(start, i), start));
            }
        }
        // the ones still open run to the end of the line, being typed
        for (int k = 0; k < open.size(); k++) {
            found.set(slots.get(k), new Function(line.substring(open.get(k)), open.get(k)));
        }
        return found;
    }

    /** The words before the expression that starts at col of row, nearest first. */
    private static List<String> owners(List<String> lines, int row, int col) {
        List<String> owners = new ArrayList<>();
        for (int r = row; r >= 0 && r > row - OWNER_LINES; r--) {
            String text = r == row ? lines.get(r).substring(0, col) : lines.get(r);
            Matcher m = WORD.matcher(text);
            List<String> words = new ArrayList<>();
            while (m.find()) {
                if (!LANGUAGE_WORDS.contains(m.group())) {
                    words.add(m.group());
                }
            }
            for (int i = words.size() - 1; i >= 0; i--) {
                owners.add(words.get(i));
            }
        }
        return owners;
    }

    /** Whether the expression is a predicate: the nearest EIP word before it takes one. */
    boolean isPredicate(Set<String> predicateWords, Set<String> eipWords) {
        for (String w : owners) {
            if (predicateWords.contains(w)) {
                return true;
            }
            if (eipWords.contains(w)) {
                return false;
            }
        }
        return false;
    }
}
