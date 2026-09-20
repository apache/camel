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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import org.apache.camel.dsl.yaml.common.DataFormatKeyHints;
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
    private final String schemaJson;
    private CamelCatalog catalog;
    private Schema schema;
    private Map<String, OneOfGroup> oneOfGroups;

    private record OneOfGroup(Set<String> alternatives, boolean required) {
    }

    public YamlValidator() {
        this(false);
    }

    public YamlValidator(boolean canonical) {
        this(canonical, null, null);
    }

    /**
     * A validator for a schema document other than the one on the classpath: the schema of another Camel version, read
     * from the {@code camel-yaml-dsl} jar of that version.
     *
     * @param canonical  whether the document is the canonical schema
     * @param schemaJson the schema document as JSON; null for the schema on the classpath
     * @param catalog    the catalog of the same Camel version, for the checks the schema cannot express; null for the
     *                   catalog on the classpath
     */
    public YamlValidator(boolean canonical, String schemaJson, CamelCatalog catalog) {
        this.canonical = canonical;
        this.schemaJson = schemaJson;
        this.catalog = catalog;
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

    private static final Pattern LINE_COLUMN = Pattern.compile("line:? (\\d+), column:? (\\d+)");

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
        String[] lines = content.split("\n", -1);
        // a tab in the indentation points at column 1, which the scan below would read as a line of prose
        Error tab = tabIndentation(msg);
        if (tab != null) {
            return tab;
        }
        // the message names several positions (the collection being parsed, then the token that broke it); the
        // problem is at the last one
        int line = -1;
        String text = null;
        Matcher m = LINE_COLUMN.matcher(msg);
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
            Matcher any = LINE_COLUMN.matcher(msg);
            int last = -1;
            while (any.find()) {
                last = Integer.parseInt(any.group(1));
            }
            if (last >= 1 && last <= lines.length) {
                String t = lines[last - 1];
                Matcher q = Pattern.compile(":\\s*(\"(?:[^\"\\\\]|\\\\.)*\"|'[^']*')\\s*\\S").matcher(t);
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
            Error marked = indentationError(msg, lines);
            return marked != null ? marked : plain;
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

    private static final Pattern SNAKE_MARK = Pattern.compile("in 'reader', line (\\d+), column (\\d+):");
    private static final Pattern ESCAPE_CHAR = Pattern.compile("found unknown escape character (.)\\(");
    private static final Pattern KEY_LINE = Pattern.compile("^\\s*[^-\\s#][^:]*:(\\s|$)");

    /** A position the parser reported: the line and the column it points at, both 1-based. */
    private record Mark(int line, int column) {
    }

    /**
     * CAMEL-24837: the snakeyaml messages that only say where the parser gave up, said in YAML words. Returns null for
     * the messages this does not know, so the raw one is still reported.
     */
    static Error indentationError(String msg, String[] lines) {
        List<Mark> marks = marks(msg, lines);
        if (marks.isEmpty()) {
            return null;
        }
        Mark problem = marks.get(marks.size() - 1);
        // the stray item is named '-', or '<block sequence start>' when the list it broke uses bare dashes
        if (msg.contains("expected <block end>, but found '-'")
                || msg.contains("expected <block end>, but found '<block sequence start>'")) {
            return listItemColumn(problem, lines);
        }
        if (msg.contains("expected <block end>, but found '<block mapping start>'")) {
            return marks.size() > 1 ? mappingKeyColumn(marks.get(0), problem, lines) : null;
        }
        if (msg.contains("mapping values are not allowed here")) {
            return mappingValue(problem, lines);
        }
        if (msg.contains("found unknown escape character")) {
            return unknownEscape(msg, problem, lines);
        }
        return null;
    }

    /** {@code Do not use (TAB) for indentation}: the line is indented with a tab. */
    static Error tabIndentation(String msg) {
        if (!msg.contains("(TAB) for indentation")) {
            return null;
        }
        Matcher m = SNAKE_MARK.matcher(msg);
        int line = -1;
        while (m.find()) {
            line = Integer.parseInt(m.group(1));
        }
        return line < 1
                ? null
                : hint("line " + line + ": the indentation uses a tab; YAML indents with spaces only, replace the"
                       + " tab with spaces");
    }

    /**
     * A list item in a column of its own: the parser only says that it expected the end of what it was reading. Name
     * the list the item belongs to, which is the shallowest list still open below the item's column, or the deepest one
     * above it when the item is the over-indented one.
     */
    private static Error listItemColumn(Mark problem, String[] lines) {
        Mark deeper = null;
        Mark shallower = null;
        for (Mark open : openLists(problem, lines)) {
            if (open.column() > problem.column() && (deeper == null || open.column() < deeper.column())) {
                deeper = open;
            } else if (open.column() < problem.column() && (shallower == null || open.column() > shallower.column())) {
                shallower = open;
            }
        }
        Mark list = deeper != null ? deeper : shallower;
        String belongs = list == null
                ? "" : ", but the list that starts at line " + list.line() + " has its items in column " + list.column();
        return hint("line " + problem.line() + ": this list item starts in column " + problem.column() + belongs
                    + "; every item of a list must start in the same column");
    }

    /**
     * The lists still open above the problem, each as the line and column of its first item. A list at column c is open
     * while every line below it is indented to at least c.
     */
    private static List<Mark> openLists(Mark problem, String[] lines) {
        Map<Integer, Integer> firstItem = new LinkedHashMap<>();
        int deepest = Integer.MAX_VALUE;
        for (int i = problem.line() - 2; i >= 0; i--) {
            String stripped = lines[i].stripLeading();
            if (stripped.isEmpty() || stripped.startsWith("#")) {
                continue;
            }
            int indent = lines[i].length() - stripped.length();
            if (listItem(stripped) && indent <= deepest) {
                firstItem.put(indent + 1, i + 1);
            }
            deepest = Math.min(deepest, indent);
        }
        List<Mark> open = new ArrayList<>();
        firstItem.forEach((column, line) -> open.add(new Mark(line, column)));
        return open;
    }

    /** A list item: the indicator followed by its value, or alone on its line with the value below it. */
    private static boolean listItem(String stripped) {
        return stripped.startsWith("-")
                && (stripped.length() == 1 || Character.isWhitespace(stripped.charAt(1)));
    }

    /** A key in a column of its own, where the parser names the mapping it was reading. */
    private static Error mappingKeyColumn(Mark mapping, Mark problem, String[] lines) {
        return hint("line " + problem.line() + ": " + keyName(lines[problem.line() - 1]) + " starts in column "
                    + problem.column() + ", but the keys of the mapping that starts at line " + mapping.line()
                    + " are in column " + mapping.column() + "; every key of a mapping must start in the same column");
    }

    /**
     * "mapping values are not allowed here" has two causes: a key indented deeper than the keys around it, and a colon
     * inside a value that is not quoted. The parser points at the colon, so the key's own colon is the first.
     */
    private static Error mappingValue(Mark problem, String[] lines) {
        String text = lines[problem.line() - 1];
        int colon = problem.column() - 1;
        if (colon < 0 || colon >= text.length() || text.charAt(colon) != ':') {
            return null;
        }
        if (colon == text.indexOf(':')) {
            String stripped = text.stripLeading();
            int indent = text.length() - stripped.length();
            Mark mapping = enclosingMapping(problem.line(), indent, lines);
            return mapping == null
                    ? null
                    : hint("line " + problem.line() + ": " + keyName(text) + " starts in column " + (indent + 1)
                           + ", but the keys of the mapping that starts at line " + mapping.line() + " are in column "
                           + mapping.column() + "; every key of a mapping must start in the same column");
        }
        String value = text.substring(text.indexOf(':') + 1).trim();
        String quoted = value.contains("\"") ? "'" + value + "'" : "\"" + value + "\"";
        return hint("line " + problem.line() + ": the value of " + keyName(text) + " holds a colon (" + quoted
                    + "): a colon followed by a space starts a new key, so the value must be quoted");
    }

    /** The mapping an over-indented key was meant to join: the first key of the nearest shallower run of keys. */
    private static Mark enclosingMapping(int line, int indent, String[] lines) {
        for (int i = line - 2; i >= 0; i--) {
            String stripped = lines[i].stripLeading();
            if (stripped.isEmpty() || stripped.startsWith("#")) {
                continue;
            }
            int other = lines[i].length() - stripped.length();
            if (other >= indent || !KEY_LINE.matcher(lines[i]).find()) {
                continue;
            }
            int first = i;
            for (int j = i - 1; j >= 0; j--) {
                String above = lines[j].stripLeading();
                int aboveIndent = lines[j].length() - above.length();
                if (above.isEmpty() || above.startsWith("#") || aboveIndent > other) {
                    continue;
                }
                if (aboveIndent < other || !KEY_LINE.matcher(lines[j]).find()) {
                    break;
                }
                first = j;
            }
            return new Mark(first + 1, other + 1);
        }
        return null;
    }

    /** A backslash inside a double-quoted value: YAML reads it as an escape, so the value belongs in single quotes. */
    private static Error unknownEscape(String msg, Mark problem, String[] lines) {
        Matcher m = ESCAPE_CHAR.matcher(msg);
        if (!m.find()) {
            return null;
        }
        String escaped = m.group(1);
        String text = lines[problem.line() - 1];
        int open = text.indexOf('"');
        int close = text.lastIndexOf('"');
        String rewrite = "";
        if (open >= 0 && close > open) {
            String value = text.substring(open + 1, close);
            rewrite = value.contains("'") ? "" : ": '" + value + "'";
        }
        return hint("line " + problem.line() + ": \\" + escaped + " inside double quotes is an escape character and "
                    + escaped + " is not one; write the value in single quotes" + rewrite);
    }

    /** The positions the parser reported, in the order it reported them, keeping only those the file has. */
    private static List<Mark> marks(String msg, String[] lines) {
        List<Mark> marks = new ArrayList<>();
        Matcher m = SNAKE_MARK.matcher(msg);
        while (m.find()) {
            int line = Integer.parseInt(m.group(1));
            if (line >= 1 && line <= lines.length) {
                marks.add(new Mark(line, Integer.parseInt(m.group(2))));
            }
        }
        return marks;
    }

    /** The name of the key a line declares, or "the value" when the line has none. */
    private static String keyName(String line) {
        String stripped = line.strip();
        int colon = stripped.indexOf(':');
        return colon > 0 ? stripped.substring(0, colon) : "the value";
    }

    private static Error hint(String message) {
        return Error.builder()
                .messageKey("parser")
                .format(new MessageFormat("{0}"))
                .arguments(message)
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
            errors = SchemaHints.apply(SchemaHints.COMPACT, errors, this);
        }
        errors = SchemaHints.apply(SchemaHints.EXPRESSION, errors, this);
        errors = SchemaHints.apply(SchemaHints.PROPERTY, errors, this);
        errors = SchemaHints.apply(SchemaHints.LIST, errors, this);
        errors = SchemaHints.apply(SchemaHints.STEP, errors, this);
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
            // unmarshal: {jackson: {}}: the unknown key already got its hint; the list of every data format that
            // "found none" adds at the same location only buries it
            errors.removeIf(e -> "oneOf".equals(e.getKeyword())
                    && hinted.contains(String.valueOf(e.getInstanceLocation()))
                    && String.valueOf(e.getMessage()).endsWith("but found none"));
        }
        return errors;
    }

    /** The canonical body of a language: its expression property, or token for tokenize, as key: "...". */
    String languageForm(String language) {
        JsonNode ref = model.at("/items/definitions/org.apache.camel.model.language.ExpressionDefinition/properties/"
                                + language + "/$ref");
        JsonNode properties = ref.isTextual() ? model.at(ref.asText().substring(1) + "/properties") : null;
        String property = properties != null && properties.has("expression") ? "expression"
                : properties != null && properties.has("token") ? "token" : "expression";
        return property + ": \"...\"";
    }

    /**
     * The EIPs whose expression the runtime needs: the schema leaves it optional for every expression node, and a split
     * written with only delimiter: "," (or a filter, setBody, when... with only options) fails when the route is
     * created with "Unsupported definition: null".
     */
    static final Set<String> EXPRESSION_REQUIRED = Set.of(
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
    private Map<String, OneOfGroup> loadOneOfGroups() {
        Map<String, OneOfGroup> groups = new HashMap<>();
        CamelCatalog catalog = catalog();
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
     * This assumes the runtime defers the conversion for every scalar attribute the schema exposes. The only model
     * attribute that is still converted while deserializing (so a placeholder is never resolved for it) is
     * {@code BeanConstructorDefinition.index}, which is a map key and is not reachable from the schema - see
     * CAMEL-24696 before exposing it.
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

    private JsonNode model;
    private Set<String> topLevelEntries = Set.of();
    private Set<String> languageKeys = Set.of();
    private Set<String> stepNames = Set.of();
    private Set<String> resilienceProperties = Set.of();

    /** The keys of the file's entries (route, from, beans, rest, onException...), from the schema. */
    Set<String> topLevelEntries() {
        return topLevelEntries;
    }

    /** The language keys of an expression (simple, constant, groovy...), from the schema. */
    Set<String> languageKeys() {
        return languageKeys;
    }

    /** The names of the EIP steps, from the schema. */
    Set<String> stepNames() {
        return stepNames;
    }

    /** The properties of resilience4jConfiguration, from the schema. */
    Set<String> resilienceProperties() {
        return resilienceProperties;
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

    /**
     * The hint for a marshal/unmarshal key that is not a data format key: the key spelled as the schema has it
     * (jackson-xml: jacksonXml), the data format named as its artifact or catalog entry (jackson, json-jackson: json
     * with library Jackson), the catalog's suggestions for a word of a name (xml: jacksonXml, fhirXml, groovyXml), the
     * closest key for a typo (jsn: json), and failing all that, what the key is.
     */
    String dataFormatHint(String unknown, String eip, String schemaLocation) {
        Set<String> keys = knownProperties(schemaLocation);
        String hint = DataFormatKeyHints.hint(unknown, keys);
        if (hint != null) {
            return hint;
        }
        List<String> names = catalog().suggestDataFormatNames(unknown, 3);
        List<String> forms = names.stream().map(DataFormatKeyHints::form).distinct().toList();
        if (forms.size() == 1) {
            hint = DataFormatKeyHints.hint(names.get(0), keys);
            return hint != null ? hint : "did you mean '" + forms.get(0) + "'?";
        }
        if (forms.size() > 1) {
            return "did you mean " + String.join(", ", forms.subList(0, forms.size() - 1)) + " or "
                   + forms.get(forms.size() - 1) + "?";
        }
        String closest = closest(unknown, keys);
        if (closest != null) {
            return "did you mean '" + closest + "'?";
        }
        return "the key of " + eip + " is the data format: json, jacksonXml, csv, yaml, jaxb, avro, protobuf...;"
               + " camel catalog dataformat lists them";
    }

    private CamelCatalog catalog() {
        if (catalog == null) {
            catalog = new DefaultCamelCatalog();
        }
        return catalog;
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
        var model = schemaJson != null
                ? mapper.readTree(schemaJson) : mapper.readTree(YamlValidator.class.getResourceAsStream(location));
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
