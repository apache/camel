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

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.EipModel;
import org.apache.camel.tooling.model.LanguageModel;

/**
 * The Tab completions of simple expressions in the Source editor, for YAML, Java and XML routes alike (CAMEL-25219):
 * the functions of the simple language after ${, the header, exchange property and variable names after ${header., and
 * the operators after a function. All from the catalog's simple language model, so they follow the Camel version of the
 * project.
 */
final class SimpleCompletions {

    /** The operators after a function of a predicate (when, filter...): comparisons, and joining predicates. */
    private static final Set<String> PREDICATE_OPERATOR_KINDS = Set.of("binary", "logical");
    /** The operators after a function of an expression (setBody, log...): chaining, and a default value. */
    private static final Set<String> EXPRESSION_OPERATOR_KINDS = Set.of("chain", "other");
    private static final int MAX_EXAMPLES = 3;
    private static final Pattern IDENT = Pattern.compile("[A-Za-z0-9-]+");
    private static final Pattern SCHEME = Pattern.compile("(?<![\\w.+-])([a-z][a-z0-9-]*):[^\\s:]");

    // the predicate and EIP words of the last catalog: reading them goes through every model
    private static volatile Words words;

    private record Words(CamelCatalog catalog, Set<String> predicate, Set<String> eip) {
    }

    /** The commands of ${date:..}; an offset (now-24h, header.due+1h30m) may follow any of them. */
    private static final Map<String, String> DATE_COMMANDS = dateCommands();
    /** Patterns of java.text.SimpleDateFormat that dates are often formatted with. */
    private static final List<String> DATE_PATTERNS = List.of(
            "yyyy-MM-dd", "yyyyMMdd", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
            "yyyyMMddHHmmss", "HH:mm:ss", "HH:mm", "dd-MM-yyyy", "MM/dd/yyyy", "EEE, dd MMM yyyy HH:mm:ss Z");

    /**
     * What the project knows that simple functions take: the beans it declares (${bean:..}) and the keys of its
     * .properties files (${properties:..}).
     */
    record Project(
            Supplier<List<AutocompletePopup.CompletionItem>> beans,
            Supplier<List<AutocompletePopup.CompletionItem>> properties) {

        static final Project NONE = new Project(List::of, List::of);
    }

    private SimpleCompletions() {
    }

    private static Map<String, String> dateCommands() {
        Map<String, String> commands = new LinkedHashMap<>();
        commands.put("now", "The current date and time");
        commands.put("millis", "The current time in milliseconds");
        commands.put("exchangeCreated", "When the exchange was created");
        commands.put("header.", "The date (Long or Date) in the header with the given name");
        commands.put("variable.", "The date (Long or Date) in the variable with the given name");
        commands.put("exchangeProperty.", "The date (Long or Date) in the exchange property with the given name");
        commands.put("file", "The last modified time of the file (with a file consumer)");
        return commands;
    }

    /** The completions at the context, given the lines of the file being edited. */
    static List<AutocompletePopup.CompletionItem> provide(
            CamelCatalog catalog, SimpleCompletionContext context, List<String> lines) {
        return provide(catalog, context, lines, Project.NONE);
    }

    /** The completions at the context, given the lines of the file being edited and what the project declares. */
    static List<AutocompletePopup.CompletionItem> provide(
            CamelCatalog catalog, SimpleCompletionContext context, List<String> lines, Project project) {
        if (catalog == null || context == null) {
            return List.of();
        }
        LanguageModel simple = catalog.languageModel("simple");
        if (simple == null) {
            return List.of();
        }
        return switch (context.kind()) {
            case FUNCTION -> functions(simple);
            case OPERATOR -> {
                Words w = words(catalog);
                yield operators(simple, context.isPredicate(w.predicate(), w.eip()));
            }
            case HEADER -> names(catalog, lines, "Header", "header", true);
            case PROPERTY -> names(catalog, lines, "Property", "exchangeProperty", false);
            case VARIABLE -> names(catalog, lines, "Variable", "variable", false);
            case DATE_COMMAND -> dateCommandItems();
            case DATE_PATTERN -> DATE_PATTERNS.stream()
                    .map(p -> new AutocompletePopup.CompletionItem(
                            p, "Formats the date with this java.text.SimpleDateFormat pattern", "pattern", null, false,
                            null, null, false))
                    .toList();
            case TIME_ZONE -> timeZones();
            case BEAN -> project.beans().get();
            case PROPERTY_KEY -> project.properties().get();
        };
    }

    private static List<AutocompletePopup.CompletionItem> dateCommandItems() {
        List<AutocompletePopup.CompletionItem> items = new ArrayList<>();
        DATE_COMMANDS.forEach((command, doc) -> items.add(new AutocompletePopup.CompletionItem(
                command, doc + ". An offset may follow, such as -24h or +1h30m.", "command", null, false, null, null,
                false)));
        return items;
    }

    /** The time zone ids of the JVM, UTC first. */
    private static List<AutocompletePopup.CompletionItem> timeZones() {
        List<String> zones = new ArrayList<>(ZoneId.getAvailableZoneIds());
        zones.sort(String.CASE_INSENSITIVE_ORDER);
        zones.remove("UTC");
        zones.add(0, "UTC");
        List<AutocompletePopup.CompletionItem> items = new ArrayList<>();
        for (String zone : zones) {
            items.add(new AutocompletePopup.CompletionItem(zone, "Time zone", "zone", null, false, null, null, false));
        }
        return items;
    }

    static List<AutocompletePopup.CompletionItem> functions(LanguageModel simple) {
        List<AutocompletePopup.CompletionItem> items = new ArrayList<>();
        for (LanguageModel.LanguageFunctionModel f : simple.getFunctions()) {
            String insert = insertOf(f);
            items.add(new AutocompletePopup.CompletionItem(
                    displayOf(f.getName(), insert), describe(f), f.getJavaType(), null,
                    f.isDeprecated(), f.getDeprecationNote(), f.getGroup(), false, insert));
        }
        items.sort(Comparator.comparing(AutocompletePopup.CompletionItem::key, String.CASE_INSENSITIVE_ORDER));
        return items;
    }

    /**
     * What goes into the line for a function: the whole of it with its } when it takes nothing (body}), else the start
     * of it in the syntax its examples use, up to where the user goes on (header. date: substring( bean:). The names of
     * the catalog are not that syntax: date(command) is written ${date:now}.
     */
    static String insertOf(LanguageModel.LanguageFunctionModel f) {
        String key = f.getName();
        Matcher m = IDENT.matcher(key);
        if (!m.lookingAt()) {
            return key;
        }
        String ident = m.group();
        if (f.getParams().isEmpty() && key.indexOf('(') < 0) {
            return key + "}";
        }
        boolean optional = f.getParams().stream().noneMatch(LanguageModel.FunctionParamModel::isRequired);
        String stem = null;
        for (String example : f.getExamples()) {
            if (!example.startsWith("${" + ident)) {
                continue;
            }
            String rest = example.substring(2 + ident.length());
            if (optional && (rest.startsWith("}") || rest.startsWith("()}"))) {
                // used as is: ${uuid()}, ${messageHistory()}
                return ident + rest.substring(0, rest.indexOf('}') + 1);
            }
            if (stem == null && !rest.isEmpty() && "(:.[".indexOf(rest.charAt(0)) >= 0) {
                stem = ident + rest.charAt(0);
            }
        }
        if (stem != null) {
            return stem;
        }
        return key.length() > ident.length() ? key.substring(0, ident.length() + 1) : key + "}";
    }

    /** The name a function is listed under: the catalog name in the syntax it is inserted with (date:command). */
    static String displayOf(String key, String insert) {
        if (insert.endsWith("}")) {
            return key;
        }
        char delimiter = insert.charAt(insert.length() - 1);
        int at = insert.length() - 1;
        if (key.length() <= at || key.charAt(at) == delimiter) {
            return key;
        }
        String rest = key.substring(at + 1);
        if (key.charAt(at) == '(' && rest.endsWith(")")) {
            rest = rest.substring(0, rest.length() - 1);
        }
        return insert + rest;
    }

    private static String describe(LanguageModel.LanguageFunctionModel f) {
        StringBuilder sb = new StringBuilder(f.getDescription() != null ? f.getDescription() : "");
        if (!f.getParams().isEmpty()) {
            sb.append("\n\nParameters:");
            for (LanguageModel.FunctionParamModel p : f.getParams()) {
                sb.append("\n  ").append(p.getName());
                if (p.isRequired()) {
                    sb.append(" (required)");
                } else if (p.getDefaultValue() != null) {
                    sb.append(" (default ").append(p.getDefaultValue()).append(')');
                }
                if (p.getDescription() != null) {
                    sb.append(": ").append(p.getDescription());
                }
            }
        }
        appendExamples(sb, f.getExamples());
        return sb.toString();
    }

    private static void appendExamples(StringBuilder sb, List<String> examples) {
        if (!examples.isEmpty()) {
            sb.append("\n\nExamples:");
            examples.stream().limit(MAX_EXAMPLES).forEach(e -> sb.append("\n  ").append(e));
        }
    }

    static List<AutocompletePopup.CompletionItem> operators(LanguageModel simple, boolean predicate) {
        Set<String> kinds = predicate ? PREDICATE_OPERATOR_KINDS : EXPRESSION_OPERATOR_KINDS;
        List<AutocompletePopup.CompletionItem> items = new ArrayList<>();
        for (LanguageModel.LanguageOperatorModel op : simple.getOperators()) {
            // ! goes before a function, not after one
            if (!kinds.contains(op.getOperatorKind()) || "!".equals(op.getName())) {
                continue;
            }
            StringBuilder sb = new StringBuilder(op.getDescription() != null ? op.getDescription() : "");
            if (op.getOperatorSyntax() != null) {
                sb.append("\n\nSyntax: ").append(op.getOperatorSyntax());
            }
            appendExamples(sb, op.getExamples());
            items.add(new AutocompletePopup.CompletionItem(
                    op.getName(), sb.toString(), null, null, op.isDeprecated(), op.getDeprecationNote(),
                    op.getDisplayName(), false));
        }
        return items;
    }

    /**
     * The header (exchange property, variable) names of the file: the ones it sets or reads, then for headers the ones
     * of the components it uses, from the catalog.
     */
    static List<AutocompletePopup.CompletionItem> names(
            CamelCatalog catalog, List<String> lines, String setWord, String readWord, boolean componentHeaders) {
        String text = String.join("\n", lines);
        Map<String, AutocompletePopup.CompletionItem> items = new LinkedHashMap<>();
        // Java: setHeader("foo", ...; YAML and XML: setHeader with a name: foo or name="foo" below or after it
        for (String verb : List.of("set" + setWord, "remove" + setWord)) {
            addNames(items, text, Pattern.compile("\\b" + verb + "\\s*\\(\\s*\"([^\"]+)\""), "Set in this file");
            addNames(items, text,
                    Pattern.compile(
                            "\\b" + verb + "\\b[^(]{0,300}?\\bname\\s*[:=]\\s*['\"]?([\\w.\\-]+)"),
                    "Set in this file");
        }
        addNames(items, text, Pattern.compile("\\$\\{(?:in\\.)?" + readWord + "s?(?:\\.|\\[['\"]?)([\\w\\-]+)"),
                "Used in this file");
        addNames(items, text, Pattern.compile("\\b" + readWord + "\\s*\\(\\s*\"([^\"]+)\""), "Used in this file");
        if (componentHeaders) {
            Set<String> components = new HashSet<>(catalog.findComponentNames());
            Matcher m = SCHEME.matcher(text);
            Set<String> seen = new HashSet<>();
            while (m.find()) {
                String scheme = m.group(1);
                if (!components.contains(scheme) || !seen.add(scheme)) {
                    continue;
                }
                ComponentModel model = catalog.componentModel(scheme);
                if (model == null) {
                    continue;
                }
                for (ComponentModel.EndpointHeaderModel h : model.getEndpointHeaders()) {
                    AutocompletePopup.CompletionItem item = new AutocompletePopup.CompletionItem(
                            h.getName(), h.getDescription(), h.getJavaType(), h.getDefaultValue(),
                            h.isDeprecated(), h.getDeprecationNote(), scheme, false);
                    // one the file uses keeps its place first, with the documentation of the component
                    AutocompletePopup.CompletionItem known = items.get(h.getName());
                    if (known == null || known.group() == null) {
                        items.put(h.getName(), item);
                    }
                }
            }
        }
        return new ArrayList<>(items.values());
    }

    private static void addNames(
            Map<String, AutocompletePopup.CompletionItem> items, String text, Pattern pattern, String description) {
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            String name = m.group(1);
            items.putIfAbsent(name, new AutocompletePopup.CompletionItem(
                    name, description, null, null, false, null, null, false));
        }
    }

    private static Words words(CamelCatalog catalog) {
        Words w = words;
        if (w == null || w.catalog() != catalog) {
            w = new Words(catalog, predicateWords(catalog), eipWords(catalog));
            words = w;
        }
        return w;
    }

    /**
     * The words that make the expression after them a predicate: the EIPs whose expression is one (when, filter,
     * validate...) and the predicate options of the others (onWhen, handled, retryWhile, completionPredicate...).
     */
    static Set<String> predicateWords(CamelCatalog catalog) {
        Set<String> words = new HashSet<>();
        for (String name : catalog.findModelNames()) {
            EipModel model = catalog.eipModel(name);
            if (model == null) {
                continue;
            }
            for (BaseOptionModel option : model.getOptions()) {
                if (option.isAsPredicate()) {
                    words.add("expression".equals(option.getName()) ? name : option.getName());
                }
            }
        }
        return words;
    }

    /** The EIP names: the nearest one before an expression tells what it belongs to. */
    static Set<String> eipWords(CamelCatalog catalog) {
        Set<String> words = new HashSet<>();
        for (String name : catalog.findModelNames()) {
            EipModel model = catalog.eipModel(name);
            if (model != null && model.getLabel() != null && model.getLabel().contains("eip")) {
                words.add(name);
            }
        }
        return words;
    }
}
