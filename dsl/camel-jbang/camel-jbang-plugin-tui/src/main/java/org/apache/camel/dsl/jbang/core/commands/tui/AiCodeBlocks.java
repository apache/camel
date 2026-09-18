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
 * Finds the fenced code blocks in a markdown answer, so the code can be copied without the fences and the prose.
 */
final class AiCodeBlocks {

    /** A fenced code block: the language tag of the opening fence (may be empty) and the code without the fences. */
    record CodeBlock(String language, String code) {

        int lineCount() {
            return code.isEmpty() ? 0 : (int) code.lines().count();
        }

        /** The first non-blank line, trimmed and shortened, used to tell blocks of the same language apart. */
        String preview(int maxLength) {
            String first = code.lines().map(String::strip).filter(l -> !l.isEmpty()).findFirst().orElse("");
            if (first.length() > maxLength) {
                first = first.substring(0, Math.max(0, maxLength - 1)) + "…";
            }
            return first;
        }

        String label() {
            String lang = language.isEmpty() ? "code" : language;
            return lang + "  " + lineCount() + " line" + (lineCount() == 1 ? "" : "s");
        }
    }

    private AiCodeBlocks() {
    }

    static List<CodeBlock> parse(String markdown) {
        List<CodeBlock> answer = new ArrayList<>();
        if (markdown == null || markdown.isEmpty()) {
            return answer;
        }
        String fence = null;
        String language = null;
        StringBuilder code = null;
        for (String line : markdown.split("\r?\n", -1)) {
            String stripped = line.stripLeading();
            if (fence == null) {
                String opening = fenceOf(stripped);
                if (opening != null) {
                    fence = opening;
                    language = stripped.substring(opening.length()).strip();
                    // "yaml title=..." or "java {...}": only the first word is the language
                    int space = language.indexOf(' ');
                    if (space > 0) {
                        language = language.substring(0, space);
                    }
                    code = new StringBuilder();
                }
            } else if (stripped.startsWith(fence) && stripped.substring(fence.length()).isBlank()) {
                answer.add(new CodeBlock(language, code.toString()));
                fence = null;
                code = null;
            } else {
                if (!code.isEmpty()) {
                    code.append('\n');
                }
                code.append(line);
            }
        }
        if (code != null && !code.isEmpty()) {
            // unterminated fence (the answer was cut off): keep what is there, without the trailing line break
            String rest = code.toString();
            while (rest.endsWith("\n")) {
                rest = rest.substring(0, rest.length() - 1);
            }
            if (!rest.isEmpty()) {
                answer.add(new CodeBlock(language, rest));
            }
        }
        return answer;
    }

    private static String fenceOf(String line) {
        for (char c : new char[] { '`', '~' }) {
            int n = 0;
            while (n < line.length() && line.charAt(n) == c) {
                n++;
            }
            if (n >= 3) {
                return line.substring(0, n);
            }
        }
        return null;
    }
}
