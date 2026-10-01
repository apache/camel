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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.tooling.model.LanguageModel;

/**
 * The quick doc of the simple function the cursor is on (CAMEL-25219): what it is, an example and its parameters, from
 * the catalog; for ${header.x} the header too, and for an operator after a function the operator. Read from the line
 * alone, as the completion is, so it works on a line being typed. Drawn on every render, so the catalog's simple model
 * and the map from the written form of a function to it are kept per catalog.
 */
final class SimpleQuickDoc {

    static final String TITLE = "Simple";

    // the simple model of the last catalog and its functions by how they are written: body, header., date:, random(
    private static volatile Model model;

    private record Model(
            CamelCatalog catalog, LanguageModel simple, Map<String, List<LanguageModel.LanguageFunctionModel>> written) {
    }

    private SimpleQuickDoc() {
    }

    /**
     * The doc of what the cursor is on: col of row; col -1 (the view, no cursor) gives the functions of the line in one
     * entry.
     */
    static List<SourceViewer.DocEntry> at(CamelCatalog catalog, List<String> lines, int row, int col) {
        if (catalog == null || lines == null || row < 0 || row >= lines.size()) {
            return List.of();
        }
        String line = lines.get(row);
        if (line.indexOf("${") < 0) {
            return List.of();
        }
        Model m = model(catalog);
        if (m == null) {
            return List.of();
        }
        if (col < 0) {
            return summary(m, line);
        }
        SimpleCompletionContext.Function f = SimpleCompletionContext.functionAt(line, col);
        if (f == null) {
            return operatorAt(m, line, col);
        }
        LanguageModel.LanguageFunctionModel fn = resolve(m, f.text());
        if (fn == null) {
            return List.of();
        }
        List<SourceViewer.DocEntry> entries = new ArrayList<>();
        String insert = SimpleCompletions.insertOf(fn);
        String name = SimpleCompletions.displayOf(fn.getName(), insert);
        String head = name + (fn.getJavaType() != null ? " — " + simpleType(fn.getJavaType()) : "") + " — "
                      + firstSentence(fn.getDescription());
        entries.add(new SourceViewer.DocEntry(head, fn.isDeprecated(), TITLE));
        SourceViewer.DocEntry named = nameDoc(catalog, lines, insert, f.text());
        if (named != null) {
            entries.add(named);
        }
        if (!fn.getExamples().isEmpty()) {
            entries.add(new SourceViewer.DocEntry("Example: " + fn.getExamples().get(0), false, TITLE));
        }
        if (!fn.getParams().isEmpty()) {
            List<String> params = new ArrayList<>();
            for (LanguageModel.FunctionParamModel p : fn.getParams()) {
                params.add(p.getName() + (p.isRequired() ? " (required)" : "")
                           + (p.getDescription() != null ? ": " + firstSentence(p.getDescription()) : ""));
            }
            entries.add(new SourceViewer.DocEntry(String.join("; ", params), false, TITLE));
        }
        return entries;
    }

    /** The function a written one is: the longest written form it starts with, by its number of arguments. */
    static LanguageModel.LanguageFunctionModel resolve(CamelCatalog catalog, String text) {
        Model m = model(catalog);
        return m != null ? resolve(m, text) : null;
    }

    private static LanguageModel.LanguageFunctionModel resolve(Model m, String text) {
        String t = text.strip();
        List<LanguageModel.LanguageFunctionModel> candidates = m.written().get(t);
        if (candidates == null) {
            String best = null;
            for (String w : m.written().keySet()) {
                boolean stem = w.endsWith(".") || w.endsWith(":") || w.endsWith("(") || w.endsWith("[");
                if (stem && t.startsWith(w) && (best == null || w.length() > best.length())) {
                    best = w;
                }
            }
            if (best == null) {
                // a function used with OGNL after it: ${body.length}, ${exception.message.trim}
                int dot = t.indexOf('.');
                candidates = dot > 0 ? m.written().get(t.substring(0, dot)) : null;
            } else {
                candidates = m.written().get(best);
            }
        }
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() > 1) {
            int args = arguments(t);
            for (LanguageModel.LanguageFunctionModel c : candidates) {
                if (c.getParams().size() == args) {
                    return c;
                }
            }
        }
        return candidates.get(0);
    }

    /** The number of arguments given in the (...) of a written function, nested ones not counted. */
    private static int arguments(String text) {
        int open = text.indexOf('(');
        if (open < 0) {
            return 0;
        }
        int depth = 0;
        int args = 1;
        boolean quoted = false;
        for (int i = open + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'') {
                quoted = !quoted;
            } else if (quoted) {
                continue;
            } else if (c == '(' || c == '{') {
                depth++;
            } else if ((c == ')' || c == '}') && depth > 0) {
                depth--;
            } else if (c == ')') {
                break;
            } else if (c == ',' && depth == 0) {
                args++;
            }
        }
        return args;
    }

    /** The header, exchange property or variable after header. exchangeProperty. variable. */
    private static SourceViewer.DocEntry nameDoc(CamelCatalog catalog, List<String> lines, String insert, String text) {
        String stripped = text.strip();
        String name = stripped.substring(Math.min(insert.length(), stripped.length()));
        if (name.isEmpty()) {
            return null;
        }
        List<AutocompletePopup.CompletionItem> known = switch (insert) {
            case "header." -> SimpleCompletions.names(catalog, lines, "Header", "header", true);
            case "exchangeProperty." -> SimpleCompletions.names(catalog, lines, "Property", "exchangeProperty", false);
            case "variable." -> SimpleCompletions.names(catalog, lines, "Variable", "variable", false);
            default -> List.of();
        };
        for (AutocompletePopup.CompletionItem item : known) {
            if (item.key().equals(name)) {
                String where = item.group() != null ? item.group() + " — " : "";
                return new SourceViewer.DocEntry(name + " — " + where + firstSentence(item.description()), false, TITLE);
            }
        }
        return null;
    }

    /** The operator the cursor is on, after a function: ${body} contai|ns 'x'. */
    private static List<SourceViewer.DocEntry> operatorAt(Model m, String line, int col) {
        int from = col;
        while (from > 0 && !Character.isWhitespace(line.charAt(from - 1))) {
            from--;
        }
        int to = col;
        while (to < line.length() && !Character.isWhitespace(line.charAt(to))) {
            to++;
        }
        if (from >= to || !line.substring(0, from).stripTrailing().endsWith("}")) {
            return List.of();
        }
        String word = line.substring(from, to);
        for (LanguageModel.LanguageOperatorModel op : m.simple().getOperators()) {
            if (op.getName().equals(word)) {
                List<SourceViewer.DocEntry> entries = new ArrayList<>();
                entries.add(new SourceViewer.DocEntry(
                        op.getName() + " — " + op.getDisplayName() + " — " + firstSentence(op.getDescription()),
                        op.isDeprecated(), TITLE));
                if (op.getOperatorSyntax() != null) {
                    entries.add(new SourceViewer.DocEntry("Syntax: " + op.getOperatorSyntax(), false, TITLE));
                }
                if (!op.getExamples().isEmpty()) {
                    entries.add(new SourceViewer.DocEntry("Example: " + op.getExamples().get(0), false, TITLE));
                }
                return entries;
            }
        }
        return List.of();
    }

    /** The functions a line uses, in one entry: date:command, header.name. */
    private static List<SourceViewer.DocEntry> summary(Model m, String line) {
        Set<String> names = new LinkedHashSet<>();
        for (SimpleCompletionContext.Function f : SimpleCompletionContext.functions(line)) {
            LanguageModel.LanguageFunctionModel fn = resolve(m, f.text());
            if (fn != null) {
                names.add(SimpleCompletions.displayOf(fn.getName(), SimpleCompletions.insertOf(fn)));
            }
        }
        if (names.isEmpty()) {
            return List.of();
        }
        return List.of(new SourceViewer.DocEntry("Simple functions: " + String.join(", ", names), false, TITLE));
    }

    private static Model model(CamelCatalog catalog) {
        Model m = model;
        if (m == null || m.catalog() != catalog) {
            LanguageModel simple = catalog.languageModel("simple");
            if (simple == null) {
                return null;
            }
            Map<String, List<LanguageModel.LanguageFunctionModel>> written = new HashMap<>();
            for (LanguageModel.LanguageFunctionModel f : simple.getFunctions()) {
                String insert = SimpleCompletions.insertOf(f);
                String key = insert.endsWith("}") ? insert.substring(0, insert.length() - 1) : insert;
                written.computeIfAbsent(key, k -> new ArrayList<>()).add(f);
                if (key.endsWith("()")) {
                    // uuid() is also written uuid(short)
                    written.computeIfAbsent(key.substring(0, key.length() - 1), k -> new ArrayList<>()).add(f);
                }
            }
            m = new Model(catalog, simple, written);
            model = m;
        }
        return m;
    }

    private static String simpleType(String type) {
        int dot = type.lastIndexOf('.');
        return dot >= 0 ? type.substring(dot + 1) : type;
    }

    private static String firstSentence(String text) {
        if (text == null) {
            return "";
        }
        int dot = text.indexOf(". ");
        return dot > 0 ? text.substring(0, dot + 1) : text;
    }
}
