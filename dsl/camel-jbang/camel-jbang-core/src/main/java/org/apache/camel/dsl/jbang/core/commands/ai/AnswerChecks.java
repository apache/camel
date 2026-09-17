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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.catalog.LanguageValidationResult;
import org.apache.camel.tooling.model.LanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The checks of what an AI model says, as opposed to what it writes: a file goes through the validator before it is
 * written, an answer shown in a chat does not, and it is what the user copies. The simple expressions of an answer are
 * checked against the catalog, the way {@code camel_validate_source} checks a route: the {@code ${...}} placeholders of
 * the text and of any code block that is not YAML, and the {@code simple:} values and log messages of the YAML blocks.
 * <p>
 * A placeholder is only checked when it starts with a simple function or value (body, header, exchangeProperty, random,
 * date, ...), so a Maven {@code ${camel-version}} or a shell variable in the same answer is left alone. What the
 * catalog cannot judge is skipped as in {@link SimpleChecks}: a function of a language that is not on the classpath, a
 * property placeholder used as a logical operand.
 */
public final class AnswerChecks {

    /** A simple expression of an answer that the catalog rejects: what was written, and why. */
    public record Problem(String expression, String error) {

        /** {@code ${header.user ?: 'Guest'}: Unexpected token ?: at location 13}. */
        public String message() {
            return expression + ": " + error;
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(AnswerChecks.class);

    private static volatile CamelCatalog defaultCatalog;

    private AnswerChecks() {
    }

    /** The problems of the simple expressions of an answer, checked against the default catalog. */
    public static List<Problem> checkSimple(String markdown) {
        return checkSimple(markdown, defaultCatalog());
    }

    /** The problems of the simple expressions of an answer, checked against the given catalog. */
    public static List<Problem> checkSimple(String markdown, CamelCatalog catalog) {
        if (markdown == null || markdown.isBlank() || catalog == null) {
            return List.of();
        }
        Map<String, Problem> problems = new LinkedHashMap<>();
        StringBuilder text = new StringBuilder();
        StringBuilder block = null;
        boolean yaml = false;
        for (String line : markdown.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) {
                if (block == null) {
                    String language = trimmed.substring(3).trim().toLowerCase(Locale.ROOT);
                    yaml = language.equals("yaml") || language.equals("yml");
                    block = new StringBuilder();
                } else {
                    if (yaml) {
                        for (String error : SimpleChecks.validateYamlSimple(block.toString(), catalog)) {
                            problems.putIfAbsent(error, new Problem("the YAML block", error));
                        }
                    } else {
                        text.append(block).append('\n');
                    }
                    block = null;
                }
                continue;
            }
            if (block != null) {
                block.append(line).append('\n');
            } else {
                text.append(line).append('\n');
            }
        }
        if (block != null) {
            // an unterminated fence: the model ran out of tokens, judge what is there as text
            text.append(block);
        }
        Set<String> roots = roots(catalog);
        for (String placeholder : placeholders(text.toString())) {
            if (problems.containsKey(placeholder) || !isSimple(placeholder, roots)
                    || SimpleChecks.hasPlaceholderAsLogicalOperand(placeholder)) {
                continue;
            }
            try {
                LanguageValidationResult result = catalog.validateLanguageExpression(null, "simple", placeholder);
                if (!result.isSuccess()) {
                    String error = result.getShortError() != null ? result.getShortError() : result.getError();
                    if (error != null && !SimpleChecks.isMissingDependency(error)) {
                        problems.put(placeholder, new Problem(placeholder, error));
                    }
                }
            } catch (Exception e) {
                // best effort: what the catalog cannot judge is not reported, the cause is in the debug log
                LOG.debug("Cannot validate the simple expression {} of the answer", placeholder, e);
            }
        }
        return new ArrayList<>(problems.values());
    }

    /**
     * The {@code ${...}} placeholders of a text, braces balanced so {@code ${header.${header.key}}} is one placeholder,
     * in the order they appear.
     */
    static List<String> placeholders(String text) {
        List<String> answer = new ArrayList<>();
        int i = 0;
        while (i < text.length() - 1) {
            if (text.charAt(i) == '$' && text.charAt(i + 1) == '{') {
                int depth = 0;
                int end = -1;
                for (int j = i + 1; j < text.length(); j++) {
                    char ch = text.charAt(j);
                    if (ch == '{') {
                        depth++;
                    } else if (ch == '}') {
                        depth--;
                        if (depth == 0) {
                            end = j;
                            break;
                        }
                    } else if (ch == '\n' && depth == 1) {
                        // a placeholder does not span lines; an unclosed one is prose
                        break;
                    }
                }
                if (end < 0) {
                    i += 2;
                    continue;
                }
                answer.add(text.substring(i, end + 1));
                i = end + 1;
            } else {
                i++;
            }
        }
        return answer;
    }

    /**
     * Whether the placeholder starts with a simple function or value, one of the {@link #roots(CamelCatalog) roots}, so
     * the catalog is the judge of it.
     */
    static boolean isSimple(String placeholder, Set<String> roots) {
        String content = placeholder.substring(2, placeholder.length() - 1).strip();
        int end = 0;
        while (end < content.length()) {
            char ch = content.charAt(end);
            if (!Character.isLetterOrDigit(ch) && ch != '-' && ch != '_') {
                break;
            }
            end++;
        }
        if (end == 0) {
            return false;
        }
        return roots.contains(content.substring(0, end));
    }

    /**
     * The first word of every simple function name: header for header.name, date for date:command:pattern. Computed
     * once per answer from the catalog given, so a catalog of another Camel version answers for its own functions.
     */
    static Set<String> roots(CamelCatalog catalog) {
        // the language model behind it is cached by the catalog, so this is a walk over a list of names
        Set<String> answer = new HashSet<>();
        LanguageModel simple = catalog.languageModel("simple");
        if (simple != null && simple.getFunctions() != null) {
            for (LanguageModel.LanguageFunctionModel fn : simple.getFunctions()) {
                String name = fn.getName();
                if (name != null) {
                    int end = 0;
                    while (end < name.length()) {
                        char ch = name.charAt(end);
                        if (!Character.isLetterOrDigit(ch) && ch != '-' && ch != '_') {
                            break;
                        }
                        end++;
                    }
                    if (end > 0) {
                        answer.add(name.substring(0, end));
                    }
                }
            }
        }
        return answer;
    }

    private static CamelCatalog defaultCatalog() {
        CamelCatalog answer = defaultCatalog;
        if (answer == null) {
            synchronized (AnswerChecks.class) {
                answer = defaultCatalog;
                if (answer == null) {
                    answer = new DefaultCamelCatalog();
                    defaultCatalog = answer;
                }
            }
        }
        return answer;
    }
}
