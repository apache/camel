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
package org.apache.camel.dsl.yaml.validator;

import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.dialect.Dialect;
import com.networknt.schema.dialect.Dialects;
import com.networknt.schema.keyword.NonValidationKeyword;
import com.networknt.schema.path.NodePath;
import com.networknt.schema.path.PathType;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.tooling.model.EipModel;

/**
 * YAML DSL validator that tooling can use to validate Camel source files if they can be parsed and are valid according
 * to the Camel YAML DSL spec.
 */
public class YamlValidator {

    private static final String LOCATION = "/schema/camelYamlDsl.json";
    private static final String LOCATION_CANONICAL = "/schema/camelYamlDsl-canonical.json";

    /**
     * A handful of "pick exactly one" EIP option groups (see {@link EipModel.EipOptionModel#getOneOfs()}) flatten their
     * alternatives directly onto a specific host node instead of appearing under a wrapper key named after the option
     * itself (that's how "expression" works). The canonical schema cannot express this "exactly one of" cardinality (it
     * has no oneOf/anyOf constructs), so {@link #checkOneOfCardinality} re-checks it here, driven by the same catalog
     * metadata the classic schema is generated from.
     */
    private static final Map<String, Set<String>> FLATTENED_HOSTS = Map.of(
            "dataFormatType", Set.of("marshal", "unmarshal"),
            "errorHandlerType", Set.of("errorHandler"),
            "tokenizerImplementation", Set.of("tokenizer"),
            "resequencerConfig", Set.of("resequence"),
            "loadBalancerType", Set.of("loadBalance"));

    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    private final boolean canonical;
    private Schema schema;
    private Map<String, OneOfGroup> oneOfGroups;

    private record OneOfGroup(Set<String> alternatives, boolean required) {
    }

    public YamlValidator() {
        this(false);
    }

    public YamlValidator(boolean canonical) {
        this.canonical = canonical;
    }

    public boolean isCanonical() {
        return canonical;
    }

    public List<Error> validate(File file) throws Exception {
        // the same checks as for content, so the CLI and the tools report the same
        String content;
        try {
            content = java.nio.file.Files.readString(file.toPath());
        } catch (java.io.IOException e) {
            // a missing or unreadable file is one error, not an exception (the CLI prints the report)
            return List.of(parseError(e));
        }
        return validate(content);
    }

    public List<Error> validate(String content) throws Exception {
        if (schema == null) {
            init();
        }
        Error extra = extraDocument(content);
        if (extra != null) {
            return List.of(extra);
        }
        Error deps = jbangDirective(content);
        if (deps != null) {
            return List.of(deps);
        }
        if (content == null || content.isBlank() || content.lines().allMatch(l -> l.isBlank() || l.trim().startsWith("#"))) {
            return List.of(Error.builder().messageKey("empty").format(new MessageFormat("{0}"))
                    .arguments("the file has no YAML: a Camel YAML file is a list of entries, each starting with \"- \":"
                               + " - route:, - from:, - beans:, - rest:, - onException:")
                    .build());
        }
        try {
            var target = mapper.readTree(content);
            return validate(target);
        } catch (Exception e) {
            return List.of(parseError(e, content));
        }
    }

    private static final java.util.regex.Pattern LINE_COLUMN
            = java.util.regex.Pattern.compile("line:? (\\d+), column:? (\\d+)");

    /**
     * A YAML parse error whose line is a line of text at column 1 after the routes (an explanation appended to the
     * file, or a markdown fence) says so; the parser's "while scanning a simple key" does not.
     */
    static Error parseError(Exception e, String content) {
        Error plain = parseError(e);
        String msg = e.getMessage();
        if (msg == null || content == null) {
            return plain;
        }
        // the message names several positions (the collection being parsed, then the token that broke it); the
        // problem is at the last one
        String[] lines = content.split("\n", -1);
        int line = -1;
        String text = null;
        java.util.regex.Matcher m = LINE_COLUMN.matcher(msg);
        while (m.find()) {
            int l = Integer.parseInt(m.group(1));
            if (!"1".equals(m.group(2)) || l < 2 || l > lines.length) {
                continue;
            }
            String t = lines[l - 1].trim();
            if (t.isEmpty() || t.startsWith("-") || t.startsWith("#") || t.startsWith("%")) {
                continue;
            }
            line = l;
            text = t;
        }
        if (text == null) {
            // a value that continues after its closing quote: message: ">>> " + exchange.getIn().getBody()
            java.util.regex.Matcher any = LINE_COLUMN.matcher(msg);
            int last = -1;
            while (any.find()) {
                last = Integer.parseInt(any.group(1));
            }
            if (last >= 1 && last <= lines.length) {
                String t = lines[last - 1];
                java.util.regex.Matcher q
                        = java.util.regex.Pattern.compile(":\\s*(\"(?:[^\"\\\\]|\\\\.)*\"|'[^']*')\\s*\\S").matcher(t);
                if (q.find()) {
                    String key = t.trim().contains(":") ? t.trim().substring(0, t.trim().indexOf(':')) : "the value";
                    return Error.builder()
                            .messageKey("parser")
                            .format(new MessageFormat("{0}"))
                            .arguments("line " + last + ": the value of " + key + " continues after its closing quote"
                                       + " (\"...\" + ...): a YAML value is one string, there is no concatenation; a"
                                       + " log message is a simple expression, write it as one quoted text such as"
                                       + " \">>> ${body}\"")
                            .build();
                }
            }
            return plain;
        }
        String cleaned = msg.replace("\n", " ").replaceAll("\\s+", " ").trim();
        int cut = cleaned.indexOf("in 'reader'");
        String head = cut > 0 ? cleaned.substring(0, cut).trim() : cleaned;
        return Error.builder()
                .messageKey("parser")
                .format(new MessageFormat("{0}"))
                .arguments("line " + line + " is not YAML (\"" + (text.length() > 40 ? text.substring(0, 40) + "..." : text)
                           + "\"): a route file holds only the YAML, put explanations in a # comment or leave them out"
                           + " (" + head + ")")
                .build();
    }

    /**
     * {@code //DEPS org.apache.camel:camel-groovy} at the top of a YAML file: JBang's Java directive, which YAML reads
     * as a plain string so the whole file becomes one scalar ("string found, array expected"). Name it, and say what a
     * YAML file uses instead.
     */
    static Error jbangDirective(String content) {
        if (content == null) {
            return null;
        }
        String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String t = lines[i].trim();
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            if (t.startsWith("//")) {
                // a camel-jbang directive (//DEPS, //JAVA, //SOURCES) or any other // line: never YAML
                String directive = t.split("\\s+")[0];
                String msg = "line " + (i + 1) + ": " + directive + " is read as text by YAML, so the whole file"
                             + " becomes one string; write it as a YAML comment: # " + t
                             + " (camel-jbang reads its directives inside comments)";
                if ("//DEPS".equals(directive)) {
                    msg += ", or add the dependency with --dep or camel.jbang.dependencies in application.properties";
                }
                return Error.builder()
                        .messageKey("jbang")
                        .format(new MessageFormat("{0}"))
                        .arguments(msg)
                        .build();
            }
            break;
        }
        return null;
    }

    /**
     * A Camel YAML file is one YAML document. The YAML parser used here reads the first document and ignores what
     * follows a {@code ---} separator (often an explanation the author appended), while the runtime rejects the file
     * with "expected a single document in the stream"; so it is reported here, with the line of the separator.
     */
    static Error extraDocument(String content) {
        if (content == null) {
            return null;
        }
        String[] lines = content.split("\n", -1);
        boolean seenContent = false;
        for (int i = 0; i < lines.length; i++) {
            String t = lines[i].trim();
            // a marker starts at column 1; an indented --- or ... is text, such as a line of a block scalar
            String marker = lines[i].stripTrailing();
            if (marker.equals("---") || marker.equals("...")) {
                if (seenContent) {
                    return Error.builder()
                            .messageKey("document")
                            .format(new MessageFormat("{0}"))
                            .arguments("line " + (i + 1) + ": the file has more than one YAML document (a " + t
                                       + " separator): a Camel YAML file is one document, remove the " + t
                                       + " and everything after it")
                            .build();
                }
            } else if (!t.isEmpty() && !t.startsWith("#") && !t.startsWith("%")) {
                seenContent = true;
            }
        }
        return null;
    }

    private List<Error> validate(JsonNode target) {
        var errors = filterOneOfNoise(new ArrayList<>(schema.validate(target)));
        errors.removeIf(YamlValidator::isRuntimeAcceptedScalar);
        if (canonical) {
            errors = withCompactNotationHints(errors);
        }
        errors = withExpressionHints(errors);
        errors = withPropertyHints(errors);
        errors = withListHints(errors);
        errors = withStepHints(errors);
        // CAMEL-24707: the schema requires the expression, so a node without one fails its oneOf with "0 are valid"
        // plus one "required property <language> not found" per language; replace that with one line that says
        // what to write, at the node's own location
        List<Error> missing = new ArrayList<>();
        checkRequiredExpressions(target, new NodePath(PathType.JSON_POINTER), missing);
        for (Error m : missing) {
            String at = String.valueOf(m.getInstanceLocation());
            errors.removeIf(e -> String.valueOf(e.getInstanceLocation()).equals(at)
                    && ("oneOf".equals(e.getKeyword()) || "required".equals(e.getKeyword())));
        }
        errors.addAll(missing);
        // an unknown property that got a hint (bean: as a language, a header name as the key...) is the cause; the
        // oneOf and required errors the strict schema adds at the same location only repeat it thirty times
        java.util.Set<String> hinted = new java.util.HashSet<>();
        for (Error e : errors) {
            if ("additionalProperties".equals(e.getKeyword())) {
                hinted.add(String.valueOf(e.getInstanceLocation()));
            }
        }
        if (!hinted.isEmpty()) {
            errors.removeIf(e -> hinted.contains(String.valueOf(e.getInstanceLocation()))
                    && ("oneOf".equals(e.getKeyword()) || "required".equals(e.getKeyword())));
        }
        if (errors.isEmpty()) {
            checkSimpleSyntaxInScripts(target, new NodePath(PathType.JSON_POINTER), errors);
        }
        if (canonical) {
            checkOneOfCardinality(target, new NodePath(PathType.JSON_POINTER), errors);
        }
        return errors;
    }

    /** The property a step written as a string sets: the argument of the definition's String constructor. */
    private static final Map<String, String> STRING_STEP_PROPERTY = Map.ofEntries(
            Map.entry("bean", "ref"), Map.entry("convertBodyTo", "type"), Map.entry("log", "message"),
            Map.entry("poll", "uri"), Map.entry("removeHeader", "name"), Map.entry("removeHeaders", "pattern"),
            Map.entry("removeProperties", "pattern"), Map.entry("removeProperty", "name"),
            Map.entry("removeVariable", "name"), Map.entry("rollback", "message"),
            Map.entry("setExchangePattern", "pattern"), Map.entry("to", "uri"), Map.entry("toD", "uri"));

    private static final String NORMALIZE_HINT = "; camel validate normalize rewrites a file in the canonical format";

    /**
     * The canonical schema rejects the compact notation as a schema error that says nothing about it: "property
     * 'simple' is not defined" for a language key directly on the EIP, "string found, object expected" for a step or a
     * language written as a string. Each is replaced with a message that names the notation, the canonical form of that
     * line, and the normalize command.
     */
    List<Error> withCompactNotationHints(List<Error> errors) {
        List<Error> answer = new ArrayList<>(errors.size());
        for (Error error : errors) {
            String hint = compactNotationHint(error);
            if (hint == null) {
                answer.add(error);
                continue;
            }
            answer.add(Error.builder()
                    .keyword("compactNotation")
                    .instanceLocation(error.getInstanceLocation())
                    .messageKey("compactNotation")
                    .format(new MessageFormat("{0}"))
                    .arguments(hint + NORMALIZE_HINT)
                    .build());
        }
        return answer;
    }

    private String compactNotationHint(Error error) {
        String message = error.getMessage();
        if (message == null) {
            return null;
        }
        String location = String.valueOf(error.getInstanceLocation());
        String name = location.substring(location.lastIndexOf('/') + 1);
        if ("additionalProperties".equals(error.getKeyword())) {
            // setBody: {simple: ...} or when: [- simple: ...]: the language key sits on the EIP, not under expression:
            String unknown = between(message, "property '", "'");
            if (unknown == null || !languageKeys.contains(unknown)) {
                return null;
            }
            if (name.matches("\\d+")) {
                String parent = location.substring(0, location.lastIndexOf('/'));
                name = parent.substring(parent.lastIndexOf('/') + 1);
                return "a " + name + " item with " + unknown + ": ... is the deprecated compact notation: an expression"
                       + " is written under expression: (- expression: {" + unknown + ": {" + languageForm(unknown)
                       + "}})";
            }
            return name + ": {" + unknown + ": ...} is the deprecated compact notation: an expression is written under"
                   + " expression: (" + name + ": {expression: {" + unknown + ": {" + languageForm(unknown) + "}}})";
        }
        if ("type".equals(error.getKeyword()) && message.contains("string found, object expected")) {
            if (languageKeys.contains(name)) {
                // simple: "..." : the language is a map with its expression
                return name + ": \"...\" is the deprecated compact notation: write " + name + ": {" + languageForm(name)
                       + "}";
            }
            if (stepNames.contains(name) || topLevelEntries.contains(name)) {
                // log: "..." : the step is a map with its properties
                String property = STRING_STEP_PROPERTY.get(name);
                return name + ": \"...\" is the deprecated compact notation: write " + name
                       + (property != null ? ": {" + property + ": \"...\"}" : " as a map with its properties");
            }
        }
        return null;
    }

    /** The canonical body of a language: its expression property, or token for tokenize, as key: "...". */
    private String languageForm(String language) {
        JsonNode ref = model.at("/items/definitions/org.apache.camel.model.language.ExpressionDefinition/properties/"
                                + language + "/$ref");
        JsonNode properties = ref.isTextual() ? model.at(ref.asText().substring(1) + "/properties") : null;
        String property = properties != null && properties.has("expression") ? "expression"
                : properties != null && properties.has("token") ? "token" : "expression";
        return property + ": \"...\"";
    }

    /**
     * "must have at most 1 properties" at a step: a step holds one EIP, and the second key is either an option that
     * belongs under the EIP (indented one level more) or another step (its own - item).
     */
    static List<Error> withStepHints(List<Error> errors) {
        List<Error> answer = new ArrayList<>(errors.size());
        for (Error error : errors) {
            String location = String.valueOf(error.getInstanceLocation());
            if ("maxProperties".equals(error.getKeyword()) && location.matches("/\\d+")) {
                // - beans:\n  myBean: ... : the second key was meant to be inside the first; it is not indented enough
                answer.add(Error.builder()
                        .keyword("maxProperties")
                        .instanceLocation(error.getInstanceLocation())
                        .messageKey("maxProperties")
                        .format(new MessageFormat("{0}"))
                        .arguments(error.getMessage() + " (a top-level entry is one key: - route:, - beans:, - rest:...;"
                                   + " the lines that belong to it must be indented under it, a second key at the same"
                                   + " level as the entry is read as a separate property)")
                        .build());
                continue;
            }
            if ("maxProperties".equals(error.getKeyword()) && location.matches(".*/steps/\\d+")) {
                answer.add(Error.builder()
                        .keyword("maxProperties")
                        .instanceLocation(error.getInstanceLocation())
                        .messageKey("maxProperties")
                        .format(new MessageFormat("{0}"))
                        .arguments(error.getMessage() + " (a step is one EIP: an option of that EIP is indented under"
                                   + " its key, and the next EIP is its own - item)")
                        .build());
            } else {
                answer.add(error);
            }
        }
        return answer;
    }

    /**
     * The EIPs whose expression the runtime needs: the schema leaves it optional for every expression node, and a split
     * written with only delimiter: "," (or a filter, setBody, when... with only options) fails when the route is
     * created with "Unsupported definition: null".
     */
    private static final Set<String> LOG_COMPONENT_OPTIONS = Set.of(
            "showAll", "showBody", "showBodyType", "showHeaders", "showExchangePattern", "showProperties",
            "showAllProperties", "showVariables", "showExchangeId", "showException", "showCaughtException",
            "showStackTrace", "showStreams", "showFiles", "showFuture", "showRouteId", "showRouteGroup", "multiline",
            "maxChars", "skipBodyLineSeparator", "groupSize", "groupInterval", "groupDelay", "groupActiveOnly",
            "level", "plain", "sourceLocationLoggerName", "style");

    private static final Set<String> EXPRESSION_REQUIRED = Set.of(
            "split", "filter", "when", "setBody", "setHeader", "setProperty", "setVariable", "transform", "loop",
            "delay", "recipientList", "routingSlip", "dynamicRouter", "validate", "script", "throttle", "resequence",
            "idempotentConsumer");

    private static final Map<String, String> EXPRESSION_EXAMPLES = Map.of(
            "split", "split: {expression: {tokenize: {token: \",\"}}} or split: {expression: {simple: {expression:"
                     + " \"${body}\"}}} (delimiter only applies to the result of the expression)",
            "filter", "filter: {expression: {simple: {expression: \"${header.type} == 'urgent'\"}}}",
            "when", "when: {expression: {simple: {expression: \"${body} contains 'x'\"}}}",
            "setBody", "setBody: {expression: {simple: {expression: \"Hello ${body}\"}}} or setBody: {expression:"
                       + " {constant: {expression: \"Hello\"}}}",
            "setHeader", "setHeader: {name: id, expression: {simple: {expression: \"${exchangeId}\"}}}",
            "loop", "loop: {expression: {constant: {expression: \"3\"}}}",
            "recipientList", "recipientList: {expression: {simple: {expression: \"${header.to}\"}}}",
            "script", "script: {expression: {groovy: {expression: \"...\"}}}",
            "delay", "delay: {expression: {constant: {expression: \"1000\"}}}");

    private static final Set<String> SCRIPT_LANGUAGES = Set.of("groovy", "js", "python", "python3", "mvel", "ognl",
            "jq", "jsonpath", "xpath", "xquery", "spel", "jactl", "java", "joor", "quickjs", "wasm", "datasonnet");

    /**
     * ${body.value} < 1 written as a groovy expression: Simple syntax inside another language, which groovy reads as a
     * call to a method named $ and jsonpath as an invalid path. Says which language it is and how to write it there.
     */
    void checkSimpleSyntaxInScripts(JsonNode node, NodePath path, List<Error> errors) {
        if (node == null) {
            return;
        }
        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                checkSimpleSyntaxInScripts(node.get(i), path.append(i), errors);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        var fields = node.fieldNames();
        while (fields.hasNext()) {
            String name = fields.next();
            JsonNode value = node.get(name);
            String text = null;
            if (SCRIPT_LANGUAGES.contains(name)) {
                if (value.isTextual()) {
                    text = value.asText();
                } else if (value.isObject() && value.has("expression") && value.get("expression").isTextual()) {
                    text = value.get("expression").asText();
                }
            }
            String outsideQuotes
                    = text != null ? text.replaceAll("\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'", "\"\"") : null;
            if (outsideQuotes != null && outsideQuotes.contains("${") && !name.equals("js") && !name.equals("quickjs")) {
                // "${x}" inside quotes is a groovy GString (or a JS template) and is fine; ${...} outside quotes is simple
                String example;
                if (name.equals("groovy") || name.equals("mvel") || name.equals("ognl") || name.equals("jactl")) {
                    example = "body.value < 1, headers.foo, exchange.getIn().getBody()";
                } else if (name.equals("jsonpath") || name.equals("jq")) {
                    example = "$.value for a JSON body";
                } else if (name.equals("xpath") || name.equals("xquery")) {
                    example = "/order/value for an XML body";
                } else {
                    example = "the language's own syntax";
                }
                errors.add(Error.builder()
                        .keyword("type")
                        .instanceLocation(path.append(name))
                        .messageKey("type")
                        .format(new MessageFormat("{0}"))
                        .arguments(name + ": ${...} is simple syntax, not " + name + ": write the expression in " + name
                                   + " (" + example + "), or use simple: {expression: \"" + text.replace("\"", "'")
                                   + "\"}")
                        .build());
            }
            checkSimpleSyntaxInScripts(value, path.append(name), errors);
        }
    }

    /** Adds an error for every expression node in the tree that has neither expression: nor a language key. */
    void checkRequiredExpressions(JsonNode node, NodePath path, List<Error> errors) {
        if (node == null) {
            return;
        }
        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                checkRequiredExpressions(node.get(i), path.append(i), errors);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        var fields = node.fieldNames();
        while (fields.hasNext()) {
            String name = fields.next();
            JsonNode value = node.get(name);
            if ("when".equals(name) && value != null && value.isArray()) {
                // choice: {when: [...]}: each item is an expression node
                for (int i = 0; i < value.size(); i++) {
                    JsonNode item = value.get(i);
                    if (item != null && item.isObject() && !hasExpression(item)) {
                        errors.add(Error.builder()
                                .keyword("required")
                                .instanceLocation(path.append(name).append(i))
                                .messageKey("required")
                                .format(new MessageFormat("{0}"))
                                .arguments("when has no expression: write the language as a key, for example "
                                           + EXPRESSION_EXAMPLES.get("when"))
                                .build());
                    }
                }
            }
            if (EXPRESSION_REQUIRED.contains(name) && (value == null || value.isNull() || value.isObject())
                    && !hasExpression(value)) {
                String example = EXPRESSION_EXAMPLES.getOrDefault(name,
                        name + ": {expression: {simple: {expression: \"...\"}}} or " + name
                                                                        + ": {expression: {constant: {expression: \"...\"}}}");
                errors.add(Error.builder()
                        .keyword("required")
                        .instanceLocation(path.append(name))
                        .messageKey("required")
                        .format(new MessageFormat("{0}"))
                        .arguments(name + " has no expression: write the language as a key, for example " + example)
                        .build());
            }
            checkRequiredExpressions(value, path.append(name), errors);
        }
    }

    private boolean hasExpression(JsonNode value) {
        if (value == null || value.isNull() || !value.isObject()) {
            return true;
        }
        if (value.has("expression")) {
            return true;
        }
        for (String language : languageKeys) {
            if (value.has(language)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Filters noise from {@code oneOf} validation. When a {@code oneOf} has N branches and none match, the validator
     * reports errors from ALL branches — producing dozens of "required property 'X' not found" messages for branches
     * the user never intended. This method keeps only the errors from the branch that matched the user's YAML most
     * closely (deepest structural match) and drops the rest.
     */
    static List<Error> filterOneOfNoise(List<Error> errors) {
        if (errors.size() <= 1) {
            return errors;
        }

        List<Error> oneOfMetas = errors.stream()
                .filter(e -> "oneOf".equals(e.getKeyword()))
                .sorted(Comparator.comparingInt(
                        (Error e) -> e.getEvaluationPath().toString().length()).reversed())
                .toList();

        if (oneOfMetas.isEmpty()) {
            return errors;
        }

        Set<Error> toRemove = new LinkedHashSet<>();

        for (Error meta : oneOfMetas) {
            if (toRemove.contains(meta)) {
                continue;
            }

            String prefix = meta.getEvaluationPath().toString();

            Map<String, List<Error>> branches = new LinkedHashMap<>();
            for (Error e : errors) {
                if (toRemove.contains(e) || e == meta) {
                    continue;
                }
                String path = e.getEvaluationPath().toString();
                if (path.startsWith(prefix + "/")) {
                    String rest = path.substring(prefix.length() + 1);
                    String branchIndex = rest.contains("/") ? rest.substring(0, rest.indexOf('/')) : rest;
                    branches.computeIfAbsent(branchIndex, k -> new ArrayList<>()).add(e);
                }
            }

            if (branches.isEmpty()) {
                continue;
            }

            // find the best-matching branch using a three-tier priority:
            //   1. property-level errors (additionalProperties, enum, pattern, etc.) — "right branch, wrong value/property"
            //   2. type errors only — "wrong branch entirely" (less informative)
            //   3. structural errors only (required, oneOf, not) — wrong-branch noise
            // within the same tier, prefer the deepest instance location
            String bestBranch = null;
            int bestDepth = -1;
            int bestTier = 0;

            for (Map.Entry<String, List<Error>> entry : branches.entrySet()) {
                int tier = branchTier(entry.getValue());
                int maxDepth = entry.getValue().stream()
                        .mapToInt(e -> e.getInstanceLocation().toString().length())
                        .max().orElse(0);

                if (tier > bestTier) {
                    bestBranch = entry.getKey();
                    bestDepth = maxDepth;
                    bestTier = tier;
                } else if (tier == bestTier && maxDepth > bestDepth) {
                    bestBranch = entry.getKey();
                    bestDepth = maxDepth;
                }
            }

            for (Map.Entry<String, List<Error>> entry : branches.entrySet()) {
                if (!entry.getKey().equals(bestBranch)) {
                    toRemove.addAll(entry.getValue());
                }
            }

            if (bestTier > 0) {
                toRemove.add(meta);
            }
        }

        if (toRemove.isEmpty()) {
            return errors;
        }

        List<Error> result = new ArrayList<>(errors.size() - toRemove.size());
        for (Error e : errors) {
            if (!toRemove.contains(e)) {
                result.add(e);
            }
        }
        return result;
    }

    private static int branchTier(List<Error> branchErrors) {
        boolean hasPropertyLevel = false;
        boolean hasType = false;
        for (Error e : branchErrors) {
            String kw = e.getKeyword();
            if (isPropertyLevelError(kw)) {
                hasPropertyLevel = true;
            } else if ("type".equals(kw)) {
                hasType = true;
            }
        }
        if (hasPropertyLevel) {
            return 2;
        }
        if (hasType) {
            return 1;
        }
        return 0;
    }

    private static boolean isPropertyLevelError(String keyword) {
        return keyword != null
                && ("additionalProperties".equals(keyword)
                        || "enum".equals(keyword)
                        || "pattern".equals(keyword)
                        || "minimum".equals(keyword)
                        || "maximum".equals(keyword)
                        || "minLength".equals(keyword)
                        || "maxLength".equals(keyword)
                        || "format".equals(keyword)
                        || "const".equals(keyword)
                        || "minItems".equals(keyword)
                        || "maxItems".equals(keyword));
    }

    /**
     * Recursively walks the parsed YAML tree looking for the host nodes/wrapper keys of a "pick exactly one" option
     * group, and reports a synthetic error when zero (for a required group) or more than one alternative is present.
     */
    private void checkOneOfCardinality(JsonNode node, NodePath path, List<Error> errors) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> entry = it.next();
                String key = entry.getKey();
                JsonNode value = entry.getValue();
                NodePath childPath = path.append(key);
                if (value.isObject()) {
                    for (Map.Entry<String, Set<String>> hostEntry : FLATTENED_HOSTS.entrySet()) {
                        if (hostEntry.getValue().contains(key)) {
                            reportIfInvalidCardinality(value, oneOfGroups.get(hostEntry.getKey()), childPath, errors);
                        }
                    }
                    if (!FLATTENED_HOSTS.containsKey(key)) {
                        reportIfInvalidCardinality(value, oneOfGroups.get(key), childPath, errors);
                    }
                }
                checkOneOfCardinality(value, childPath, errors);
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                checkOneOfCardinality(node.get(i), path.append(i), errors);
            }
        }
    }

    private static void reportIfInvalidCardinality(JsonNode container, OneOfGroup group, NodePath path, List<Error> errors) {
        if (group == null) {
            return;
        }
        List<String> found = new ArrayList<>();
        Iterator<String> names = container.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (group.alternatives().contains(name)) {
                found.add(name);
            }
        }
        if (found.isEmpty() && group.required()) {
            errors.add(Error.builder()
                    .keyword("oneOf")
                    .instanceLocation(path)
                    .message("must have exactly one of " + group.alternatives() + " but found none")
                    .build());
        } else if (found.size() > 1) {
            errors.add(Error.builder()
                    .keyword("oneOf")
                    .instanceLocation(path)
                    .message("must have exactly one of " + group.alternatives() + " but found: " + found)
                    .build());
        }
    }

    /**
     * Builds the "pick exactly one" option groups from the Camel catalog's EIP model metadata - the same metadata the
     * classic (non-canonical) schema's oneOf groups are generated from. Only object-typed options are considered;
     * array-typed options (e.g. "outputs") use "oneOf" to mean "each element is one of these types", not "exactly one
     * of these sibling keys must be present".
     */
    private static Map<String, OneOfGroup> loadOneOfGroups() {
        Map<String, OneOfGroup> groups = new HashMap<>();
        CamelCatalog catalog = new DefaultCamelCatalog();
        for (String name : catalog.findModelNames()) {
            EipModel model = catalog.eipModel(name);
            if (model == null) {
                continue;
            }
            for (EipModel.EipOptionModel option : model.getOptions()) {
                List<String> oneOfs = option.getOneOfs();
                if (oneOfs != null && !oneOfs.isEmpty() && "object".equals(option.getType())) {
                    groups.putIfAbsent(option.getName(), new OneOfGroup(new LinkedHashSet<>(oneOfs), option.isRequired()));
                }
            }
        }
        return groups;
    }

    /**
     * Whether the schema rejected a scalar that the Camel runtime accepts, in which case the error is dropped.
     * <p>
     * Camel's model declares nearly every scalar attribute as a {@code String} field carrying the real type in
     * {@code @Metadata(javaType = ...)}, so that property placeholders can be used and the text is converted when the
     * route starts. The generated schema keeps the real type because tooling (Kaoto forms, TUI completion, catalog
     * docs) relies on it, which makes the schema stricter than the runtime in two ways:
     * <ul>
     * <li>a property placeholder at a typed attribute - the runtime resolves it before converting;</li>
     * <li>a number or boolean at a string-typed attribute (e.g. a {@code duration}) - the runtime converts any scalar
     * to text;</li>
     * <li>a quoted scalar that parses as the expected type (e.g. {@code parallelProcessing: "true"}) - the runtime
     * converts the text.</li>
     * </ul>
     * Everything else stays strict: unknown properties, structure (a map where a list is expected), enums, and strings
     * that do not parse as the expected type.
     * <p>
     * This assumes the runtime defers the conversion for every scalar attribute the schema exposes. The few model
     * attributes that are still converted while deserializing (so a placeholder is never resolved for them) are not
     * reachable from the schema today - see CAMEL-24696 before exposing one of them.
     */
    static boolean isRuntimeAcceptedScalar(Error error) {
        if (!"type".equals(error.getKeyword())) {
            return false;
        }
        JsonNode instance = error.getInstanceNode();
        if (instance == null) {
            return false;
        }
        if (instance.isTextual()) {
            String text = instance.asText();
            if (hasPropertyPlaceholder(text)) {
                return true;
            }
            if (isExpectedType(error, "boolean")) {
                return isBooleanText(text);
            }
            if (isExpectedType(error, "integer") || isExpectedType(error, "number")) {
                return isNumberText(text);
            }
            return false;
        }
        // the runtime converts any scalar to text, so a number or boolean is fine wherever a string is expected
        return (instance.isNumber() || instance.isBoolean()) && isExpectedType(error, "string");
    }

    /**
     * Replaces the schema's "boolean found, object expected" for a plain value at an option that takes an expression
     * (such as {@code handled: true} on onException, or {@code completionSizeExpression: 10} on aggregate) with a
     * message that shows the expression form, and drops the duplicates the schema composition produces for it.
     */
    static List<Error> withExpressionHints(List<Error> errors) {
        List<Error> answer = new ArrayList<>(errors.size());
        Set<String> seen = new LinkedHashSet<>();
        for (Error error : errors) {
            Error hinted = withExpressionHint(error);
            if (hinted == error || seen.add(hinted.getInstanceLocation() + " " + hinted.getMessage())) {
                answer.add(hinted);
            }
        }
        return answer;
    }

    static Error withExpressionHint(Error error) {
        if (!"type".equals(error.getKeyword())) {
            return error;
        }
        JsonNode instance = error.getInstanceNode();
        if (instance == null || !instance.isValueNode()) {
            return error;
        }
        String schemaLocation = String.valueOf(error.getSchemaLocation());
        String evaluationPath = String.valueOf(error.getEvaluationPath());
        if (!schemaLocation.contains(EXPRESSION_SUB_ELEMENT) && !evaluationPath.contains(EXPRESSION_SUB_ELEMENT)) {
            return error;
        }
        String location = String.valueOf(error.getInstanceLocation());
        String name = location.substring(location.lastIndexOf('/') + 1);
        String value = instance.asText();
        String message = String.format(
                "a plain value (%s) found, an expression expected: write %s: {constant: {expression: \"%s\"}} for a fixed value, or %s: {simple: {expression: \"...\"}} for a dynamic one",
                value, name, value, name);
        // the message is not a MessageFormat pattern (it contains braces), so pass it as the single argument
        return Error.builder()
                .keyword("type")
                .instanceLocation(error.getInstanceLocation())
                .messageKey("expression")
                .format(new MessageFormat("{0}"))
                .arguments(message)
                .build();
    }

    private static final String EXPRESSION_SUB_ELEMENT = "ExpressionSubElementDefinition";

    private JsonNode model;
    private Set<String> topLevelEntries = Set.of();
    private Set<String> languageKeys = Set.of();
    private Set<String> stepNames = Set.of();
    private Set<String> resilienceProperties = Set.of();

    /**
     * "object found, array expected" says what the schema wants, not how to write it: a list, each item starting with
     * "- ". At the root of the file it also names the entries (route, from, beans, rest, onException).
     */
    static List<Error> withListHints(List<Error> errors) {
        List<Error> answer = new ArrayList<>(errors.size());
        for (Error error : errors) {
            answer.add(withListHint(error));
        }
        return answer;
    }

    static Error withListHint(Error error) {
        if (!"type".equals(error.getKeyword()) || error.getMessage() == null) {
            return error;
        }
        String location = String.valueOf(error.getInstanceLocation());
        if (location.endsWith("/language") && error.getMessage().contains("object expected")) {
            // script: {language: groovy, text: ...}: the language is the key of the expression, not a property
            return Error.builder()
                    .keyword("type")
                    .instanceLocation(error.getInstanceLocation())
                    .messageKey("type")
                    .format(new MessageFormat("{0}"))
                    .arguments(error.getMessage() + " (an expression is written with the language as the key and its"
                               + " expression: property, e.g. groovy: {expression: \"...\"}, simple: {expression: \"...\"},"
                               + " constant: {expression: \"...\"}; the language: form is"
                               + " language: {language: groovy, expression: \"...\"})")
                    .build();
        }
        if (error.getMessage().contains("object found, string expected")) {
            // message: {simple: "..."}: a string property that is already an expression, or a plain option
            String prop = location.substring(location.lastIndexOf('/') + 1);
            return Error.builder()
                    .keyword("type")
                    .instanceLocation(error.getInstanceLocation())
                    .messageKey("type")
                    .format(new MessageFormat("{0}"))
                    .arguments(error.getMessage() + " (" + prop + " is a plain string"
                               + (location.endsWith("/log/message")
                                       ? " that is already a simple expression: write message: \"... ${body} ...\""
                                       : ": write " + prop + ": \"...\", not a language map")
                               + ")")
                    .build();
        }
        if (location.matches(
                "/\\d+/(onException|onCompletion|intercept|interceptFrom|interceptSendToEndpoint|errorHandler|route|rest|restConfiguration)")
                && error.getMessage().contains("array found, object expected")) {
            // - onException: [ ... ]: the entry is a map; several handlers are several - onException: items
            String entry = location.substring(location.lastIndexOf('/') + 1);
            return Error.builder()
                    .keyword("type")
                    .instanceLocation(error.getInstanceLocation())
                    .messageKey("type")
                    .format(new MessageFormat("{0}"))
                    .arguments(error.getMessage() + " (" + entry + " is a map, not a list: - " + entry + ": followed by its"
                               + " properties indented" + (entry.equals("onException")
                                       ? " (exception: [java.lang.Exception], handled: {constant: {expression: \"true\"}},"
                                         + " steps: [...])"
                                       : "")
                               + "; several of them are several - " + entry + ": items)")
                    .build();
        }
        if (location.matches(".*/(otherwise|doTry|doFinally|doCatch/\\d+)") && error.getMessage().contains("object expected")) {
            // otherwise: [- log: ...]: the block is a map whose steps: holds the list
            String eip = location.substring(location.lastIndexOf('/') + 1);
            if (eip.matches("\\d+")) {
                eip = "doCatch";
            }
            return Error.builder()
                    .keyword("type")
                    .instanceLocation(error.getInstanceLocation())
                    .messageKey("type")
                    .format(new MessageFormat("{0}"))
                    .arguments(error.getMessage() + " (" + eip + " holds its EIPs under steps: " + eip
                               + ": {steps: [- log: \"...\"]}" + (eip.equals("doCatch")
                                       ? ", each - doCatch: item with"
                                         + " exception: and steps:"
                                       : "")
                               + ")")
                    .build();
        }
        if (!error.getMessage().contains("array expected")) {
            return error;
        }
        String name = location.substring(location.lastIndexOf('/') + 1);
        String hint;
        if (location.isEmpty() || location.equals("/")) {
            hint = "a Camel YAML file is a list of entries, each starting with \"- \": - route:, - from:, - beans:, - rest:,"
                   + " - onException:";
        } else if (name.equals("beans")) {
            hint = "beans is a list: - name: myBean followed by type: \"#class:com.example.MyBean\" (indented under the -)";
        } else if (name.equals("steps") || name.equals("when") || name.equals("get") || name.equals("post")
                || name.equals("exception") || name.equals("doCatch")) {
            hint = name + " is a list: each item starts with \"- \"";
        } else {
            hint = "write it as a list: each item starts with \"- \"";
        }
        return Error.builder()
                .keyword("type")
                .instanceLocation(error.getInstanceLocation())
                .messageKey("type")
                .format(new MessageFormat("{0}"))
                .arguments(error.getMessage() + " (" + hint + ")")
                .build();
    }

    /**
     * Adds a hint to "property 'x' is not defined in the schema": the closest property name of that node (did you mean
     * 'logName'?), or, when the property is a top-level entry such as onException written inside a route, where it goes
     * instead.
     */
    List<Error> withPropertyHints(List<Error> errors) {
        List<Error> answer = new ArrayList<>(errors.size());
        for (Error error : errors) {
            answer.add(withPropertyHint(error));
        }
        return answer;
    }

    Error withPropertyHint(Error error) {
        if ("required".equals(error.getKeyword()) && error.getMessage() != null
                && error.getMessage().contains("required property 'steps' not found")
                && String.valueOf(error.getInstanceLocation()).matches(".*/route/from")) {
            return Error.builder()
                    .keyword("required")
                    .instanceLocation(error.getInstanceLocation())
                    .messageKey("required")
                    .format(new MessageFormat("{0}"))
                    .arguments(error.getMessage() + " (steps: is a property of from:, next to uri:; a steps: written at"
                               + " the route level must be indented under from:)")
                    .build();
        }
        if (!"additionalProperties".equals(error.getKeyword()) || error.getMessage() == null) {
            return error;
        }
        String message = error.getMessage();
        String unknown = between(message, "property '", "'");
        if (unknown == null) {
            return error;
        }
        String location = String.valueOf(error.getInstanceLocation());
        String hint = null;
        if (location.matches("/\\d+/beans/\\d+")
                && (unknown.equals("id") || unknown.equals("ref") || unknown.equals("class"))) {
            // - id: myBean / class: ... : the bean properties are name and type
            hint = "a bean is - name: myBean followed by type: \"#class:com.example.MyBean\" (name instead of " + unknown
                   + (unknown.equals("class") ? ", type instead of class" : "") + ")";
        } else if (location.matches("/\\d+/beans/\\d+")) {
            // - myBean: {type: ...} instead of - name: myBean / type: ...
            hint = "a bean item is written as - name: " + unknown + " followed by type: \"#class:com.example.MyBean\" "
                   + "(the name is a property, not the key)";
        } else if (topLevelEntries.contains(unknown) && location.chars().filter(c -> c == '/').count() >= 2) {
            hint = "'" + unknown + "' is a top-level entry: write it as a list item at the same level as the route, "
                   + "not inside it";
        } else if (location.matches(".*/(onException|doCatch/\\d+)") && unknown.matches("([a-z][\\w]*\\.)+[A-Z]\\w*")) {
            // onException: {java.lang.Exception: ...}: the class is a list item under exception:
            String eip = location.endsWith("/onException") ? "onException" : "doCatch";
            hint = "the exception class is a list item under exception: (" + eip + ": {exception: [" + unknown
                   + "], steps: [...]})";
        } else if (location.endsWith("/circuitBreaker") && unknown.equals("name")) {
            hint = "the circuit breaker's name is its id: circuitBreaker: {id: myBreaker, ...}";
        } else if (location.endsWith("/circuitBreaker") && !resilienceProperties.isEmpty()
                && (resilienceProperties.contains(unknown) || closest(unknown, resilienceProperties) != null
                        || unknown.toLowerCase(Locale.ROOT).contains("threshold")
                        || unknown.toLowerCase(Locale.ROOT).contains("timeout"))) {
            // circuitBreaker: {failureThreshold: 5}: the thresholds and timeouts are resilience4j configuration
            String best = resilienceProperties.contains(unknown) ? unknown : closest(unknown, resilienceProperties);
            hint = "the thresholds, timeouts and the like are written under resilience4jConfiguration: (circuitBreaker:"
                   + " {resilience4jConfiguration: {" + (best != null ? best : "failureRateThreshold") + ": ...}, steps:"
                   + " [...], onFallback: {steps: [...]}})";
        } else if (location.endsWith("/log") && (unknown.equals("level") || unknown.equals("logLevel"))) {
            hint = "did you mean 'loggingLevel'?";
        } else if (location.endsWith("/log") && LOG_COMPONENT_OPTIONS.contains(unknown)) {
            // log: {message: ..., showHeaders: true}: those are options of the log component endpoint
            hint = "'" + unknown + "' is an option of the log component, not of the log EIP: write a to: step with"
                   + " uri: \"log:com.example?" + unknown + "=...\" (the log EIP has message, loggingLevel, logName,"
                   + " marker)";
        } else if (unknown.equals("steps") && location.matches(".*/route")) {
            // - route: {from: {uri: ...}, steps: [...]}: steps belongs under from:
            hint = "steps: goes under from:, indented at the same level as uri: (route: {from: {uri: ..., steps: [...]}})";
        } else if (stepNames.contains(unknown) && !location.matches(".*/steps/\\d+")
                && location.matches(".*/(otherwise|when/\\d+|doTry|doCatch/\\d+|doFinally|split|filter|loop|aggregate"
                                    + "|circuitBreaker|onFallback|multicast|pipeline|saga|resequence|throttle|delay"
                                    + "|onException|onCompletion|intercept|interceptFrom|interceptSendToEndpoint|route|from)")) {
            // otherwise: {log: ...} or when: [- simple: ..., log: ...]: the EIPs go under steps:
            String eip = location.substring(location.lastIndexOf('/') + 1);
            if (eip.matches("\\d+")) {
                String parent = location.substring(0, location.lastIndexOf('/'));
                eip = parent.substring(parent.lastIndexOf('/') + 1);
            }
            hint = "'" + unknown + "' is a step: the steps of " + eip + " go under steps: (" + eip
                   + ": {steps: [- " + unknown + ": ...]})";
        } else if (unknown.equals("script") && !location.endsWith("/steps")
                && (EXPRESSION_REQUIRED.contains(location.substring(location.lastIndexOf('/') + 1))
                        || location.endsWith("/expression"))) {
            // setBody: {script: ...}: script is an EIP; the language is the key of an expression
            hint = "script is an EIP step, not a language: write the language as the key of the expression (expression:"
                   + " {groovy: {expression: \"...\"}}, expression: {simple: {expression: \"...\"}}), or run a script as"
                   + " its own step with - script: {expression: {groovy: {expression: \"...\"}}}";
        } else if (unknown.equals("bean") && !location.endsWith("/steps")) {
            // setBody: {bean: myBean} : the bean language is method:
            hint = "the bean language is written as method: (expression: {method: {ref: myBean, method: process}}), or"
                   + " call the bean as a step with - bean: {ref: myBean, method: process}";
        } else if (location.matches(".*/(setHeader|setProperty|setVariable|removeHeader|removeProperty|removeVariable)")
                && closest(unknown, knownProperties(String.valueOf(error.getSchemaLocation()))) == null) {
            // setHeader: {CamelNumberA: {simple: ...}} : the name is a property, not the key
            String eip = location.substring(location.lastIndexOf('/') + 1);
            hint = "the name is a property: " + eip + ": {name: " + unknown
                   + (eip.startsWith("set") ? ", expression: {simple: {expression: \"...\"}}}" : "}")
                   + " (" + unknown + " is not the key)";
        } else if (location.endsWith("/bean")
                && (unknown.equals("parameters") || unknown.equals("args") || unknown.equals("arguments"))) {
            hint = "arguments are written in the method call: bean: {ref: myBean, method: \"process(${body}, 'x')\"}";
        } else {
            String best = closest(unknown, knownProperties(String.valueOf(error.getSchemaLocation())));
            if (best != null) {
                hint = "did you mean '" + best + "'?";
            }
        }
        if (hint == null) {
            return error;
        }
        return Error.builder()
                .keyword("additionalProperties")
                .instanceLocation(error.getInstanceLocation())
                .messageKey("additionalProperties")
                .format(new MessageFormat("{0}"))
                .arguments(message + " (" + hint + ")")
                .build();
    }

    /** The property names the schema allows at the definition an additionalProperties error points to. */
    Set<String> knownProperties(String schemaLocation) {
        Set<String> answer = new LinkedHashSet<>();
        if (model == null) {
            return answer;
        }
        int hash = schemaLocation.indexOf('#');
        String pointer = hash >= 0 ? schemaLocation.substring(hash + 1) : schemaLocation;
        if (pointer.endsWith("/additionalProperties")) {
            pointer = pointer.substring(0, pointer.length() - "/additionalProperties".length());
        }
        collectProperties(model.at(pointer), answer, 0);
        return answer;
    }

    private void collectProperties(JsonNode node, Set<String> answer, int depth) {
        if (node == null || node.isMissingNode() || depth > 3) {
            return;
        }
        JsonNode props = node.get("properties");
        if (props != null) {
            props.fieldNames().forEachRemaining(answer::add);
        }
        JsonNode ref = node.get("$ref");
        if (ref != null && ref.isTextual() && ref.asText().startsWith("#")) {
            collectProperties(model.at(ref.asText().substring(1)), answer, depth + 1);
        }
        for (String composition : new String[] { "anyOf", "oneOf", "allOf" }) {
            JsonNode entries = node.get(composition);
            if (entries != null && entries.isArray()) {
                for (JsonNode entry : entries) {
                    if (!entry.has("not")) {
                        collectProperties(entry, answer, depth + 1);
                    }
                }
            }
        }
    }

    static String closest(String unknown, Set<String> known) {
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        int threshold = Math.max(2, unknown.length() / 3);
        String u = unknown.toLowerCase(Locale.ROOT);
        for (String k : known) {
            int d = distance(u, k.toLowerCase(Locale.ROOT));
            if (d <= threshold && (d < bestDistance || d == bestDistance && k.length() < best.length())) {
                best = k;
                bestDistance = d;
            }
        }
        if (best == null) {
            // propertyName, headerName, variableName: the schema property is the last word (name)
            for (String k : known) {
                String kl = k.toLowerCase(Locale.ROOT);
                if (kl.length() >= 3 && u.length() > kl.length() && u.endsWith(kl)
                        && (best == null || k.length() > best.length())) {
                    best = k;
                }
            }
        }
        return best;
    }

    static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }

    private static String between(String text, String start, String end) {
        int i = text.indexOf(start);
        if (i < 0) {
            return null;
        }
        int j = text.indexOf(end, i + start.length());
        return j < 0 ? null : text.substring(i + start.length(), j);
    }

    private static boolean isBooleanText(String text) {
        String s = text.trim();
        return "true".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s);
    }

    private static boolean isNumberText(String text) {
        try {
            Double.parseDouble(text.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean hasPropertyPlaceholder(String text) {
        int start = text.indexOf("{{");
        return start >= 0 && text.indexOf("}}", start + 2) > start;
    }

    private static boolean isExpectedType(Error error, String type) {
        JsonNode schemaNode = error.getSchemaNode();
        if (schemaNode == null) {
            return false;
        }
        if (schemaNode.isTextual()) {
            return type.equals(schemaNode.asText());
        }
        // "type" may also be declared as an array of accepted types
        if (schemaNode.isArray()) {
            for (JsonNode t : schemaNode) {
                if (type.equals(t.asText())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Error parseError(Exception e) {
        String msg = e.getClass().getName() + ": " + e.getMessage();
        return Error.builder()
                .messageKey("parser")
                .format(new MessageFormat("{0}"))
                .arguments(msg)
                .build();
    }

    public void init() throws Exception {
        String location = canonical ? LOCATION_CANONICAL : LOCATION;
        var model = mapper.readTree(YamlValidator.class.getResourceAsStream(location));
        this.model = model;
        this.topLevelEntries = new LinkedHashSet<>();
        model.at("/items/properties").fieldNames().forEachRemaining(topLevelEntries::add);
        this.stepNames = new LinkedHashSet<>();
        model.at("/items/definitions/org.apache.camel.model.ProcessorDefinition/properties").fieldNames()
                .forEachRemaining(stepNames::add);
        this.resilienceProperties = new LinkedHashSet<>();
        model.at("/items/definitions/org.apache.camel.model.Resilience4jConfigurationDefinition/properties").fieldNames()
                .forEachRemaining(resilienceProperties::add);
        this.languageKeys = new LinkedHashSet<>();
        model.at("/items/definitions/org.apache.camel.model.language.ExpressionDefinition/properties").fieldNames()
                .forEachRemaining(languageKeys::add);
        var version = getSpecificationVersion(model).orElse(SpecificationVersion.DRAFT_4);
        // no typeLoose: besides accepting quoted scalars it also accepts a single value where the schema expects a
        // list (steps: written as a map), which the runtime rejects. The scalar leniency the runtime has is done as
        // a filter on the reported errors instead, see isRuntimeAcceptedScalar.
        var config = SchemaRegistryConfig.builder().locale(Locale.ENGLISH).build();

        // Register "deprecated" as a known non-validation keyword to suppress warnings
        Dialect base = getBaseDialect(version);
        Dialect dialect = Dialect.builder(base)
                .keyword(new NonValidationKeyword("deprecated"))
                .build();

        var schemaRegistry = SchemaRegistry.withDefaultDialect(dialect,
                builder -> builder.schemaRegistryConfig(config));

        // Use a proper URI for the schema location to ensure $ref resolution works
        var schemaLocation = SchemaLocation.of(location);
        schema = schemaRegistry.getSchema(schemaLocation, model);

        if (canonical) {
            oneOfGroups = loadOneOfGroups();
        }
    }

    private static Dialect getBaseDialect(SpecificationVersion version) {
        return switch (version) {
            case DRAFT_4 -> Dialects.getDraft4();
            case DRAFT_6 -> Dialects.getDraft6();
            case DRAFT_7 -> Dialects.getDraft7();
            case DRAFT_2019_09 -> Dialects.getDraft201909();
            case DRAFT_2020_12 -> Dialects.getDraft202012();
        };
    }

    private static Optional<SpecificationVersion> getSpecificationVersion(JsonNode schemaNode) {
        var schemaField = schemaNode.get("$schema");
        if (schemaField != null && schemaField.isTextual()) {
            return SpecificationVersion.fromDialectId(schemaField.asText());
        }
        return Optional.empty();
    }

}
