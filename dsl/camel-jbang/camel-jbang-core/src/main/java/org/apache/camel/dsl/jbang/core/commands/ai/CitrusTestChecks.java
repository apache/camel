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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import org.apache.camel.dsl.jbang.core.common.CitrusCatalogVersion;
import org.apache.camel.dsl.yaml.validator.YamlPointerLines;
import org.apache.camel.dsl.yaml.validator.YamlTrees;
import org.apache.camel.dsl.yaml.validator.YamlValidator;
import org.apache.camel.main.download.MavenDependencyDownloader;
import org.apache.camel.tooling.maven.MavenArtifact;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.nodes.SequenceNode;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.MissingNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

/**
 * Checks a Citrus test, such as test/orders.citrus.it.yaml, against the JSON schema of a test from the Citrus catalog
 * ({@code org.citrusframework:citrus-catalog-schema}) of the Citrus version camel test run uses. A local model wrote
 * Citrus YAML of its own (an http: action with uri:, method: and expectStatus:, log: and assert-log: actions), the YAML
 * check let it through, and camel test run then failed with "Unable to find property 'http' on class:
 * org.citrusframework.yaml.TestActions" (test-citrus-orders of the local-model benchmark).
 * <p/>
 * The schema is read from the classpath, where the test plugin brings the catalog, or else from the catalog jar, which
 * is downloaded once. Without the schema, offline say, a test is checked for its YAML syntax only.
 */
public final class CitrusTestChecks {

    private static final String SCHEMA_GROUP = "org.citrusframework";
    private static final String SCHEMA_ARTIFACT = "citrus-catalog-schema";
    private static final String SCHEMA_DIR = "org/citrusframework/schema/citrus/";
    private static final String SCHEMA_FILE = "citrus-testcase.json";
    private static final String TEST_ACTIONS = "#/definitions/TestActions";
    /** The items of the action lists of a container (sequential, parallel...), which the schema takes as any object. */
    private static final String NESTED_ACTION = "#/definitions/TestActionBuilder";

    private static final ObjectMapper YAML = YamlTrees.newMapper();
    private static final ObjectMapper JSON = JsonMapper.builderWithJackson2Defaults().build();
    /** The YAML 1.1 integers of SnakeYAML, which Citrus reads a test with: 1_000, 0x1F, 0b101, 0123. */
    private static final Pattern INTEGER = Pattern.compile("[-+]?(?:0b[01_]+|0x[0-9a-fA-F_]+|[0-9][0-9_]*)");
    /** The words SnakeYAML, which Citrus reads a test with, takes as a boolean. */
    private static final Set<String> BOOLEAN_WORDS = Set.of("true", "false", "yes", "no", "on", "off");

    // the keys local models wrote under http: for the parts of a request and its response, which an http action does
    // not take (every write of test-citrus-orders step 1 in r26 and r28 of the local-model benchmark)
    private static final Set<String> URL_KEYS = Set.of("uri", "url", "endpoint", "endpointUri", "address");
    private static final Set<String> METHOD_KEYS = Set.of("method", "httpMethod", "verb", "requestMethod");
    private static final Set<String> FILE_KEYS = Set.of("requestFile", "file", "bodyFile", "payloadFile",
            "requestBodyFile", "resource");
    private static final Set<String> BODY_KEYS = Set.of("body", "payload", "requestBody", "data", "content");
    private static final Set<String> STATUS_KEYS = Set.of("status", "statusCode", "expectStatus", "expectedStatus",
            "expectedStatusCode", "expectStatusCode", "responseCode", "responseStatus", "code");
    private static final Set<String> RESPONSE_BODY_KEYS = Set.of("expectedBody", "expectBody", "responseBody",
            "expectedResponse", "expectResponse", "expectedResponseBody");
    /** The keys a model wrote under a body for a part of the text it expects (test-citrus-orders step 2). */
    private static final Set<String> CONTAINS_KEYS = Set.of("contains", "containsString", "includes", "include",
            "matches", "match", "regex", "pattern", "like", "startsWith", "endsWith");
    /** A URL: the client part (scheme, host and port), and the path. */
    private static final Pattern URL = Pattern.compile("^([A-Za-z][\\w+.-]*://[^/?#]+)(.*)$");
    /**
     * A backslash before a character of a regular expression, such as \( in line\(s\), or before a second backslash:
     * the two backslashes are taken as a pair, so the character after them is not escaped.
     */
    private static final Pattern REGEX_ESCAPE = Pattern.compile("\\\\([\\\\()\\[\\]{}.*+?^$|])");

    private static volatile TestSchema schema;

    private CitrusTestChecks() {
    }

    /**
     * The test schema of a Citrus version, or why there is none.
     *
     * @param version the Citrus version
     * @param model   the schema document; null when there is no schema
     * @param test    the schema of a test
     * @param action  the schema of one action, for the actions of a container
     * @param actions the names of the actions (echo, http, camel, sequential...)
     * @param lists   the paths to the actions of a test (actions/[], finally/[]), "[]" for the items of a list
     * @param nested  the paths to the actions of each container (sequential: actions/[]; waitFor: action)
     * @param problem why there is no schema, or null
     */
    record TestSchema(String version, JsonNode model, Schema test, Schema action, Set<String> actions,
            List<List<String>> lists, Map<String, List<List<String>>> nested, String problem) {
    }

    /**
     * The problems of a Citrus test, each starting with "Line N: ": what the test schema says, and the actions that are
     * not Citrus test actions, which the schema lets through. Empty when the test is valid, when its YAML does not
     * parse (the YAML check says where), and when there is no schema.
     */
    public static List<String> validate(String content) {
        return validate(content, null, null);
    }

    /**
     * As {@link #validate(String)}, with where the test is: a file the lines to write name is given relative to the
     * test, as Citrus reads it.
     *
     * @param dir  the project directory, or null
     * @param file the test file, relative to the directory
     */
    public static List<String> validate(String content, Path dir, String file) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        TestSchema s = schema();
        if (s.model() == null) {
            return List.of();
        }
        JsonNode tree;
        try {
            tree = YAML.readTree(content);
        } catch (JacksonException e) {
            return List.of();
        }
        if (tree == null || tree.isMissingNode()) {
            return List.of();
        }
        Path project = dir != null ? dir.toAbsolutePath().normalize() : null;
        Path testDir = project != null && file != null ? project.resolve(file).normalize().getParent() : null;
        return new Check(s, content, tree, project, testDir).problems();
    }

    /** Whether a test is checked against the test schema; false when it is checked for its YAML syntax only. */
    public static boolean hasSchema() {
        return schema().model() != null;
    }

    /** The Citrus version whose test schema checks the tests. */
    public static String version() {
        return schema().version();
    }

    /** Why there is no test schema, such as a catalog that cannot be downloaded; null when there is one. */
    public static String schemaProblem() {
        return schema().problem();
    }

    /** The test schema of the Citrus version of the CLI, read once. */
    static TestSchema schema() {
        TestSchema s = schema;
        if (s == null) {
            synchronized (CitrusTestChecks.class) {
                s = schema;
                if (s == null) {
                    s = load(CitrusCatalogVersion.CITRUS_VERSION);
                    schema = s;
                }
            }
        }
        return s;
    }

    /** Uses the given schema document, or none for null, instead of the one of the catalog; for tests. */
    static void setTestSchema(String json) {
        try {
            schema = json != null
                    ? of(CitrusCatalogVersion.CITRUS_VERSION, json)
                    : new TestSchema(
                            CitrusCatalogVersion.CITRUS_VERSION, null, null, null, Set.of(), List.of(), Map.of(),
                            "no schema (test)");
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    /** Reads the schema of the catalog again when it is next needed; for tests. */
    static void reset() {
        schema = null;
    }

    /**
     * The schema of the version, or one with the problem: a test is never refused because the schema cannot be had, and
     * a download that failed is not tried again for each test.
     */
    private static TestSchema load(String version) {
        try {
            return of(version, read(version));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage().lines().findFirst().orElse("") : "";
            return new TestSchema(
                    version, null, null, null, Set.of(), List.of(), Map.of(),
                    msg.isBlank() ? e.getClass().getSimpleName() : msg);
        }
    }

    /**
     * The test schema from the classpath (camel-launcher has the test plugin), else from the catalog jar of the version
     * in the local Maven repository (where the test plugin put it when it was downloaded), or downloaded.
     */
    private static String read(String version) throws Exception {
        String entry = SCHEMA_DIR + version + "/" + SCHEMA_FILE;
        try (InputStream is = CitrusTestChecks.class.getClassLoader().getResourceAsStream(entry)) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        String gav = SCHEMA_GROUP + ":" + SCHEMA_ARTIFACT + ":" + version;
        MavenDependencyDownloader downloader = new MavenDependencyDownloader();
        downloader.setDownload(true);
        try {
            downloader.start();
            MavenArtifact ma = downloader.downloadArtifact(SCHEMA_GROUP, SCHEMA_ARTIFACT, version);
            if (ma == null || ma.getFile() == null) {
                throw new IOException("cannot download " + gav);
            }
            try (ZipFile jar = new ZipFile(ma.getFile())) {
                ZipEntry ze = jar.getEntry(entry);
                if (ze == null) {
                    throw new IOException(gav + " has no " + entry);
                }
                try (InputStream is = jar.getInputStream(ze)) {
                    return new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        } finally {
            downloader.stop();
            downloader.close();
        }
    }

    /** The schemas of a test and of one action, and what the checks of the actions need, from the schema document. */
    static TestSchema of(String version, String json) throws IOException {
        JsonNode model = JSON.readTree(json);
        Set<String> actions = new LinkedHashSet<>();
        model.at("/definitions/TestActions/properties").propertyNames().forEach(actions::add);
        if (actions.isEmpty()) {
            throw new IOException("the schema has no test actions");
        }
        var config = SchemaRegistryConfig.builder().locale(Locale.ENGLISH).build();
        var registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7,
                builder -> builder.schemaRegistryConfig(config));
        Schema test = registry.getSchema(SchemaLocation.of(SCHEMA_FILE), model);
        // one action: the definitions of the test schema, with an action as the root
        ObjectNode one = JSON.createObjectNode();
        one.set("$schema", model.get("$schema"));
        one.set("definitions", model.get("definitions"));
        one.putArray("allOf").addObject().put("$ref", TEST_ACTIONS);
        Schema action = registry.getSchema(SchemaLocation.of("citrus-testaction.json"), one);
        List<List<String>> lists = new ArrayList<>();
        refPaths(model, new ArrayList<>(), TEST_ACTIONS::equals, lists, 0);
        Map<String, List<List<String>>> nested = new LinkedHashMap<>();
        for (String name : actions) {
            List<List<String>> paths = new ArrayList<>();
            refPaths(model.at("/definitions/TestActions/properties/" + name), new ArrayList<>(),
                    ref -> ref.startsWith(NESTED_ACTION), paths, 0);
            if (!paths.isEmpty()) {
                nested.put(name, paths);
            }
        }
        return new TestSchema(version, model, test, action, actions, lists, nested, null);
    }

    /**
     * The paths of keys from a node of the schema to each reference the predicate accepts, "[]" for the items of a
     * list: actions/[] for the actions of sequential, assertFault/when for the action of the assertFault of soap.
     */
    private static void refPaths(
            JsonNode node, List<String> path, Predicate<String> ref, List<List<String>> answer, int depth) {
        if (node == null || !node.isObject() || depth > 10) {
            return;
        }
        JsonNode r = node.get("$ref");
        if (r != null && r.isString() && ref.test(r.asString())) {
            if (!answer.contains(path)) {
                answer.add(List.copyOf(path));
            }
            return;
        }
        node.path("properties").properties().forEach(e -> {
            path.add(e.getKey());
            refPaths(e.getValue(), path, ref, answer, depth + 1);
            path.remove(path.size() - 1);
        });
        if (node.has("items")) {
            path.add("[]");
            refPaths(node.get("items"), path, ref, answer, depth + 1);
            path.remove(path.size() - 1);
        }
        for (String composition : new String[] { "allOf", "anyOf", "oneOf" }) {
            for (JsonNode entry : node.path(composition)) {
                refPaths(entry, path, ref, answer, depth + 1);
            }
        }
    }

    /**
     * A scalar of another type than the schema says, which Citrus converts as it reads the test: SnakeYAML sets a
     * number or a boolean as text where the property is a string (status: 201), and parses a quoted number or boolean.
     */
    static boolean convertedScalar(Error error) {
        JsonNode value = error.getInstanceNode();
        if (!"type".equals(error.getKeyword()) || value == null) {
            return false;
        }
        if (value.isNumber() || value.isBoolean()) {
            return expects(error, "string");
        }
        if (value.isString()) {
            String text = value.asString("").trim();
            if (expects(error, "integer")) {
                return INTEGER.matcher(text).matches();
            }
            if (expects(error, "number")) {
                try {
                    Double.parseDouble(text.replace("_", ""));
                    return true;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
            return expects(error, "boolean") && BOOLEAN_WORDS.contains(text.toLowerCase(Locale.ROOT));
        }
        return false;
    }

    /** Whether the type error is about a value of the type: "type" may be one type or a list of them. */
    private static boolean expects(Error error, String type) {
        JsonNode node = error.getSchemaNode();
        if (node != null && node.isArray()) {
            for (JsonNode t : node) {
                if (type.equals(t.asString(""))) {
                    return true;
                }
            }
            return false;
        }
        return node != null && type.equals(node.asString(""));
    }

    /** The checks of one test: its tree for the schema, its YAML nodes for the lines and the keys of the actions. */
    private static final class Check {

        private final TestSchema schema;
        private final JsonNode tree;
        private final Node root;
        private final String[] lines;
        /** The project directory and the directory of the test, or null when they are not known. */
        private final Path project;
        private final Path testDir;
        /** The action of each action item, by its pointer (/actions/0: http), for the path a message names. */
        private final Map<String, String> items = new LinkedHashMap<>();
        /** The YAML node of each action item, by its pointer, for the lines of the item. */
        private final Map<String, Node> itemNodes = new HashMap<>();
        /** The pointers where an action is expected: the items of the action lists. */
        private final Set<String> slots = new HashSet<>();
        private final List<Problem> problems = new ArrayList<>();

        private record Problem(int line, String text) {
        }

        Check(TestSchema schema, String content, JsonNode tree, Path project, Path testDir) {
            this.schema = schema;
            this.tree = tree;
            this.root = YamlPointerLines.root(content);
            this.lines = content.split("\n", -1);
            this.project = project;
            this.testDir = testDir;
        }

        /**
         * A file as Citrus reads it, relative to the test: a file of the project named as the tools name it
         * (sample-order.json for the file beside the test directory) is ../sample-order.json from test/.
         */
        private String fromTest(String file) {
            if (testDir == null || file.isBlank() || Files.exists(testDir.resolve(file))) {
                return file;
            }
            Path inProject = project.resolve(file).normalize();
            return Files.exists(inProject) ? testDir.relativize(inProject).toString().replace('\\', '/') : file;
        }

        /**
         * The file a body written as text names, as Citrus loads it: a classpath: or file: resource as written, or a
         * file of the project, from the test; null for text, which is the body itself. Written under data:, the name of
         * the file was sent as the body (r29n-4 test-citrus-orders step 1 copied body: {data:
         * "classpath:test/sample-order.json"} from the lines of a refused write, and the service answered 500).
         */
        private String bodyFile(String text) {
            String name = text.strip();
            if (name.isEmpty() || name.chars().anyMatch(Character::isWhitespace)) {
                return null;
            }
            if (name.matches("(classpath|file):.+")) {
                return name;
            }
            try {
                return testDir != null
                        && (Files.isRegularFile(testDir.resolve(name)) || Files.isRegularFile(project.resolve(name)))
                                ? fromTest(name) : null;
            } catch (InvalidPathException e) {
                return null;
            }
        }

        /** The body loaded from a file: {resource: {file: ...}}. */
        private static ObjectNode resource(String file) {
            ObjectNode body = JSON.createObjectNode();
            body.set("resource", JSON.createObjectNode().put("file", file));
            return body;
        }

        List<String> problems() {
            // the actions first, also those of a container (sequential, parallel...), which the schema takes as any
            // object: that each item is one Citrus test action, and what the actions of a container have
            for (List<String> path : schema.lists()) {
                forEachAction(root, path, 0, "", (item, at) -> checkAction(item, at, false));
            }
            describe(schema.test().validate(tree), "");
            List<String> answer = new ArrayList<>();
            problems.stream()
                    .sorted(Comparator.comparingInt(Problem::line))
                    .map(p -> p.line() > 0 ? "Line " + p.line() + ": " + p.text() : p.text())
                    .distinct()
                    .forEach(answer::add);
            return answer;
        }

        /** Calls the visitor for each node the path of keys leads to from the node ("[]" for each item of a list). */
        private void forEachAction(Node node, List<String> path, int index, String pointer, BiConsumer<Node, String> visitor) {
            if (index == path.size()) {
                slots.add(pointer);
                visitor.accept(node, pointer);
                return;
            }
            String key = path.get(index);
            if ("[]".equals(key)) {
                if (node instanceof SequenceNode seq) {
                    for (int i = 0; i < seq.getValue().size(); i++) {
                        forEachAction(seq.getValue().get(i), path, index + 1, pointer + "/" + i, visitor);
                    }
                }
            } else if (node instanceof MappingNode map) {
                // the last of a key given twice, as the YAML is read
                Node value = null;
                for (NodeTuple t : map.getValue()) {
                    if (key.equals(scalar(t.getKeyNode()))) {
                        value = t.getValueNode();
                    }
                }
                if (value != null) {
                    forEachAction(value, path, index + 1, pointer + "/" + escape(key), visitor);
                }
            }
        }

        /**
         * An item of an action list names one Citrus test action, as - echo: {message: "..."}. The schema lets any
         * other key through, and two actions in one item (a - left out) are one action to Citrus, which runs the last.
         */
        private void checkAction(Node item, String at, boolean nested) {
            if (!(item instanceof MappingNode map)) {
                // the schema says what it is instead of a map
                return;
            }
            List<NodeTuple> keys = map.getValue();
            if (keys.isEmpty()) {
                add(line(item), "the list item has no action: write the action as its key, such as - echo: {message:"
                                + " \"...\"}");
                return;
            }
            boolean known = true;
            for (NodeTuple t : keys) {
                String name = scalar(t.getKeyNode());
                if (name == null || !schema.actions().contains(name)) {
                    known = false;
                    add(line(t.getKeyNode()), notAnAction(String.valueOf(name)));
                }
            }
            if (!known) {
                return;
            }
            if (keys.size() > 1) {
                List<String> names = keys.stream().map(t -> scalar(t.getKeyNode())).toList();
                String what = names.stream().distinct().count() == 1
                        ? names.get(0) + " is given " + (names.size() == 2 ? "twice" : names.size() + " times")
                        : and(names) + " are " + (names.size() == 2 ? "two" : names.size()) + " actions";
                add(line(keys.get(1).getKeyNode()), what + " in one list item, and Citrus runs only one of them: start"
                                                    + " each action with - on a line of its own");
                return;
            }
            String name = scalar(keys.get(0).getKeyNode());
            items.put(at, name);
            itemNodes.put(at, item);
            if ("camel".equals(name)) {
                checkLogMessage(at);
            }
            JsonNode action = tree.at(at);
            if (nested && !action.isMissingNode()) {
                describe(schema.action().validate(action), at);
            }
            for (List<String> path : schema.nested().getOrDefault(name, List.of())) {
                forEachAction(keys.get(0).getValueNode(), path, 0, at + "/" + escape(name),
                        (n, p) -> checkAction(n, p, true));
            }
        }

        /** What is said of a key that is not a Citrus test action. */
        private String notAnAction(String name) {
            String answer = name + " is not a Citrus test action";
            // assert-log, create-variables: the actions are written in camel case
            String camel = camelCase(name);
            String closest = schema.actions().contains(camel)
                    ? camel : QuickFixes.closest(camel, new ArrayList<>(schema.actions()));
            if (closest != null && !closest.equals(name)) {
                return answer + " (did you mean " + closest + "?)";
            }
            if (camelCaseWords(name).contains("log") && schema.actions().contains("echo")
                    && schema.actions().contains("camel")) {
                // log:, assert-log: or assertLog: in 8 of the 11 tests a local model wrote (test-citrus-orders); a
                // model that wrote assert-log: {pattern: ...} kept the escapes of the pattern in logMessage (r32)
                String cli = schema.model().at("/definitions/TestActions/properties/camel/properties/cli").isMissingNode()
                        ? "jbang" : "cli";
                return answer + ": echo: {message: \"...\"} writes a message to the test log, and camel: {" + cli
                       + ": {verify: {integration: \"<name>\", logMessage: \"...\"}}} checks that the integration"
                       + " logged a line that contains that text as written (plain text, not a pattern)";
            }
            return answer + "; the actions are " + String.join(", ", schema.actions());
        }

        /**
         * The logMessage of a camel cli (or jbang) verify action is plain text: Citrus checks that the integration
         * logged a line that contains it as written. In r32 of the local-model benchmark, three of seven runs of
         * test-citrus-orders step 1 wrote it as a regular expression, line\(s\) for line(s), and camel test run waited
         * 60 attempts for a line with the backslashes in it (the model had first written assert-log: {pattern: ...},
         * which is not a Citrus action, and kept the escapes). The line to write has the text without the backslash of
         * each escape. A backslash before another character, as in a Windows path or an escaped quote, is left alone.
         */
        private void checkLogMessage(String at) {
            JsonNode camel = schema.model().at("/definitions/TestActions/properties/camel");
            for (String cli : List.of("cli", "jbang")) {
                String pointer = at + "/camel/" + cli + "/verify/logMessage";
                JsonNode value = tree.at(pointer);
                if (!value.isString() || !hasPath(camel, cli, "verify", "logMessage")) {
                    continue;
                }
                List<String> escapes = new ArrayList<>();
                String text = REGEX_ESCAPE.matcher(value.asString()).replaceAll(m -> {
                    boolean pair = "\\".equals(m.group(1));
                    if (!pair) {
                        escapes.add(m.group());
                    }
                    return Matcher.quoteReplacement(pair ? m.group() : m.group(1));
                });
                if (escapes.isEmpty()) {
                    continue;
                }
                String answer = where(pointer) + " is plain text, not a regular expression: Citrus checks that the"
                                + " integration logged a line that contains it as written, so the backslash"
                                + (escapes.size() == 1 ? "" : "es") + " in " + and(escapes.stream().distinct().toList())
                                + " would have to be in the log too.";
                String written = keyLines(pointer, StringNode.valueOf(text));
                Node key = keyNode(pointer);
                add(key != null ? line(key) : YamlPointerLines.line(root, pointer, null),
                        answer + (written != null ? written : " Write logMessage: " + quoted(text)));
            }
        }

        /**
         * The schema errors in words: what the noise of the branches of a oneOf leaves, without the type errors of the
         * values Citrus converts, and the keys an object does not take in one message, with the keys it takes.
         *
         * @param raw    the errors of the validation
         * @param prefix the pointer of the node the validation was of, "" for the test
         */
        private void describe(List<Error> raw, String prefix) {
            List<Error> errors = new ArrayList<>(YamlValidator.filterOneOfNoise(new ArrayList<>(raw)));
            errors.removeIf(CitrusTestChecks::convertedScalar);
            // a oneOf that fails for an error in the branch of its key (sendRequest for its POST): that error says it
            List<Error> all = List.copyOf(errors);
            errors.removeIf(e -> isComposition(e)
                    && all.stream().anyMatch(o -> location(o).startsWith(location(e) + "/")));
            Map<String, List<Error>> unknown = new LinkedHashMap<>();
            for (Error e : errors) {
                if ("additionalProperties".equals(e.getKeyword()) && e.getProperty() != null) {
                    unknown.computeIfAbsent(prefix + location(e), k -> new ArrayList<>()).add(e);
                }
            }
            for (Error e : errors) {
                String at = prefix + location(e);
                if ("additionalProperties".equals(e.getKeyword()) && e.getProperty() != null) {
                    continue;
                }
                if ("required".equals(e.getKeyword()) && unknown.containsKey(at)) {
                    // a key misspelled: the unknown key says it, the missing one would only repeat it
                    continue;
                }
                add(YamlPointerLines.line(root, at, e.getMessage()), describe(e, at));
            }
            unknown.forEach((at, list) -> {
                List<String> keys = list.stream().map(Error::getProperty).distinct().toList();
                add(YamlPointerLines.line(root, at, "property '" + keys.get(0) + "'"), unknownKeys(at, keys, list.get(0)));
            });
        }

        private String describe(Error e, String at) {
            String what = where(at);
            String message = e.getMessage();
            if ("type".equals(e.getKeyword()) && slots.contains(at)) {
                return message + ": an action is a map with the action as its key, such as - echo: {message: \"...\"}";
            }
            if ("type".equals(e.getKeyword()) && expects(e, "object")) {
                Set<String> keys = keysAt(e);
                if (!keys.isEmpty() && what.isEmpty()) {
                    // a list, as a Camel route file is
                    return message + ": a Citrus test is a map with the keys " + String.join(", ", keys);
                }
                if (!keys.isEmpty()) {
                    String answer = what + ": " + message + "; " + name(what) + " takes " + String.join(", ", keys);
                    return isBody(keys) && tree.at(at).isValueNode() ? answer + bodyText(at, tree.at(at).asString()) : answer;
                }
            }
            if ("enum".equals(e.getKeyword()) && e.getSchemaNode() != null && e.getInstanceNode() != null) {
                List<String> choices = new ArrayList<>();
                e.getSchemaNode().forEach(c -> choices.add(c.asString("")));
                String value = e.getInstanceNode().asString("");
                String closest = QuickFixes.closest(value, choices);
                return (what.isEmpty() ? "" : what + ": ") + value + " is not one of " + String.join(", ", choices)
                       + (closest != null ? " (did you mean " + closest + "?)" : "");
            }
            if ("oneOf".equals(e.getKeyword()) && e.getSchemaNode() != null && e.getInstanceNode() != null) {
                // the keys of which an object takes one (body: data, resource or script), given together
                List<String> keys = new ArrayList<>();
                e.getSchemaNode().forEach(branch -> {
                    if (branch.path("required").size() == 1) {
                        keys.add(branch.path("required").get(0).asString(""));
                    }
                });
                List<String> given = keys.stream().filter(e.getInstanceNode()::has).toList();
                if (given.size() > 1) {
                    return (what.isEmpty() ? "" : what + ": ") + and(given) + " are given together, and "
                           + name(what) + " takes only one of " + String.join(", ", keys);
                }
            }
            return what.isEmpty() ? message : what + ": " + message;
        }

        /** The keys an object does not take, with the keys it takes, and the closest one of each when it is clear. */
        private String unknownKeys(String at, List<String> keys, Error error) {
            Set<String> takes = keysAt(error);
            List<String> named = new ArrayList<>();
            for (String key : keys) {
                String closest = QuickFixes.closest(key, new ArrayList<>(takes));
                named.add(closest != null ? key + " (did you mean " + closest + "?)" : key);
            }
            String what = where(at);
            // the keys of the action itself (http: {uri: ...}), or of a node in it (http.sendRequest)
            boolean action = items.containsKey(parent(at));
            String of = what.isEmpty() ? "a Citrus test" : action ? "the " + what + " action" : what;
            String answer = and(named) + (keys.size() == 1 ? " is not a key of " : " are not keys of ") + of
                            + (takes.isEmpty() ? "" : ", which takes " + String.join(", ", takes));
            if (action && "http".equals(what)) {
                return answer + requestAndResponse(at, keys);
            }
            if (isBody(takes)) {
                return answer + bodyKeys(at, at, keys);
            }
            if (takes.contains("body") && takes.contains("status")) {
                // a check of the body as a key of the response: bodyContains (r26 of the local-model benchmark)
                List<String> body = keys.stream().filter(k -> k.toLowerCase(Locale.ROOT).contains("body")).toList();
                return body.isEmpty() ? answer : answer + bodyKeys(at, at + "/body", body);
            }
            return answer;
        }

        /**
         * The request and its expected response written straight under http: (uri, method, requestFile, contentType and
         * expectStatus in every write of test-citrus-orders step 1 of the local-model benchmark), or as request: and
         * response: maps. An http action of a client sends the request or receives the response, so they are two
         * actions: the lines to write with the values as written, when they pass the schema; else the shape of the two.
         */
        private String requestAndResponse(String at, List<String> keys) {
            JsonNode http = schema.model().at("/definitions/TestActions/properties/http");
            if (!hasPath(http, "sendRequest") || !hasPath(http, "receiveResponse", "response")
                    || keys.stream().noneMatch(k -> isCallKey(k, http))) {
                return "";
            }
            String shape = ". An http action of a client either sends the request (sendRequest) or receives the response"
                           + " (receiveResponse)";
            HttpCall call = new HttpCall(http);
            tree.at(at).properties().forEach(e -> call.add(e.getKey(), e.getValue(), null));
            List<JsonNode> actions = call.actions();
            String written = actions != null ? itemLines(parent(at), actions) : null;
            String left = call.left.isEmpty()
                    ? ""
                    : " (" + and(call.left) + (call.left.size() == 1 ? " has" : " have") + " no place in them and "
                      + (call.left.size() == 1 ? "is" : "are") + " left out)";
            if (written != null && actions.size() == 2) {
                return shape + ", so the request and the response it expects are two http actions" + left + "." + written;
            }
            if (written != null) {
                return shape + ", so the request is an action of its own, and a second http action checks the response"
                       + " with receiveResponse: {response: {status: \"200\"}}" + left + "." + written;
            }
            return shape + ", so the request and the response it expects are two http actions, such as - http: {client:"
                   + " \"http://localhost:8080\", sendRequest: {POST: {path: \"/...\", body: {data: \"...\"}}}} and"
                   + " - http: {client: \"http://localhost:8080\", receiveResponse: {response: {status: \"200\"}}}";
        }

        /**
         * A body written with a key of its own, text, equal, contains or equalTo (five of seven runs of
         * test-citrus-orders step 2 had the whole case right but this key): the text goes under data:, as the body to
         * send or the body the test expects; a part of the expected text is checked with jsonPath.
         */
        private String bodyKeys(String at, String bodyAt, List<String> keys) {
            JsonNode node = tree.at(at);
            String key = keys.stream().filter(k -> node.path(k).isValueNode() && !node.path(k).isNull()).findFirst()
                    .orElse(null);
            if (key == null) {
                return "";
            }
            String text = node.get(key).asString("");
            boolean contains = CONTAINS_KEYS.contains(key) || key.toLowerCase(Locale.ROOT).contains("contain");
            if (contains && isReceived(bodyAt)) {
                String jsonPath = jsonPathCheck(bodyAt, Map.of("<field>", text));
                return "; data is the whole body the test expects, compared as a whole"
                       + (jsonPath != null
                               ? ". To check that one field holds this text, leave out " + (at.equals(bodyAt) ? "body" : key)
                                 + ": and validate the field next to response: " + jsonPath
                               : "");
            }
            if (!at.equals(bodyAt)) {
                String file = bodyFile(text);
                return "; the body the test expects goes under body: "
                       + (file != null ? "{resource: {file: " + quoted(file) + "}}" : "{data: " + quoted(text) + "}");
            }
            return bodyText(at, text);
        }

        /**
         * The lines of a body written as text, under data:, and what data is; for an expected JSON body, jsonPath too.
         * The name of a file goes under resource:, which loads the body from the file.
         */
        private String bodyText(String at, String text) {
            boolean expected = isReceived(at);
            String file = bodyFile(text);
            if (file != null) {
                String written = keyLines(at, resource(file));
                return "; " + quoted(text.strip()) + " names a file: it goes under resource: {file: ...}, and the body "
                       + (expected ? "the test expects" : "to send") + " is read from that file (under data:, the name"
                       + " itself would be the body)."
                       + (written != null ? written : " Write body: {resource: {file: " + quoted(file) + "}}");
            }
            Map<String, String> fields = expected ? jsonFields(text) : Map.of();
            String jsonPath = fields.isEmpty() ? null : jsonPathCheck(at, fields);
            String written = keyLines(at, JSON.createObjectNode().put("data", text));
            return "; the text goes under data:, which is the body "
                   + (expected ? "the test expects (Citrus compares a JSON body as JSON)" : "to send")
                   + (jsonPath != null
                           ? ". To check fields instead of the whole body, validate them next to response: " + jsonPath
                           : "")
                   + "." + (written != null ? written : " Write body: {data: " + quoted(text) + "}");
        }

        /**
         * The jsonPath check of the fields, as validate: [...] in one line, when the body is the one of the response of
         * an http action, whose receiveResponse takes it; null otherwise.
         */
        private String jsonPathCheck(String at, Map<String, String> fields) {
            JsonNode http = schema.model().at("/definitions/TestActions/properties/http");
            if (!at.endsWith("/receiveResponse/response/body")
                    || !hasPath(http, "receiveResponse", "validate", "jsonPath", "expression")
                    || !hasPath(http, "receiveResponse", "validate", "jsonPath", "value")) {
                return null;
            }
            StringJoiner checks = new StringJoiner(", ");
            fields.forEach((field, value) -> checks.add("{expression: " + quoted("$." + field) + ", value: " + quoted(value)
                                                        + "}"));
            return "validate: [{jsonPath: [" + checks + "]}]";
        }

        /**
         * " Write lines 14-19 as:" and the actions in place of the list item at the pointer, when they pass the schema;
         * null when the item is not in block style.
         */
        private String itemLines(String pointer, List<JsonNode> actions) {
            Node item = itemNodes.get(pointer);
            int first = item != null ? line(item) - 1 : -1;
            if (first < 0 || first >= lines.length) {
                return null;
            }
            int dash = indent(lines[first]);
            if (!lines[first].startsWith("- ", dash)) {
                return null;
            }
            List<String> written = new ArrayList<>();
            for (JsonNode a : actions) {
                List<String> one = new ArrayList<>();
                yaml(a, dash + 2, one);
                one.set(0, " ".repeat(dash) + "- " + one.get(0).substring(dash + 2));
                written.addAll(one);
            }
            if (!validActions(written)) {
                return null;
            }
            return writeLines(first + 1, blockEnd(first), written);
        }

        /** " Write lines 14-19 as:" or " Write line 43 as:", and the lines. */
        private static String writeLines(int first, int last, List<String> written) {
            return (first == last ? " Write line " + first : " Write lines " + first + "-" + last) + " as:\n"
                   + String.join("\n", written);
        }

        /** " Write lines 52-53 as:" and the key at the pointer with the value given; null when it is not found. */
        private String keyLines(String pointer, JsonNode value) {
            Node key = keyNode(pointer);
            int first = key != null ? line(key) - 1 : -1;
            if (first < 0 || first >= lines.length) {
                return null;
            }
            int column = indent(lines[first]);
            if (!lines[first].startsWith(scalar(key) + ":", column)) {
                return null;
            }
            List<String> written = new ArrayList<>();
            yaml(JSON.createObjectNode().set(scalar(key), value), column, written);
            return writeLines(first + 1, blockEnd(first), written);
        }

        /** Whether the lines are YAML of actions that the schema takes, each. */
        private boolean validActions(List<String> written) {
            try {
                JsonNode list = YAML.readTree(String.join("\n", written));
                if (list == null || !list.isArray() || list.isEmpty()) {
                    return false;
                }
                for (JsonNode a : list) {
                    List<Error> errors = new ArrayList<>(
                            YamlValidator.filterOneOfNoise(
                                    new ArrayList<>(schema.action().validate(a))));
                    errors.removeIf(CitrusTestChecks::convertedScalar);
                    if (!errors.isEmpty()) {
                        return false;
                    }
                }
                return true;
            } catch (RuntimeException e) {
                return false;
            }
        }

        /**
         * The 1-based last line of the block of a line: the lines below it indented more, without blank lines and
         * comments at its end.
         */
        private int blockEnd(int first) {
            int column = indent(lines[first]);
            int end = first + 1;
            int last = first + 1;
            while (end < lines.length) {
                String l = lines[end];
                if (!l.isBlank() && indent(l) <= column) {
                    break;
                }
                end++;
                if (!l.isBlank() && !l.strip().startsWith("#")) {
                    last = end;
                }
            }
            return last;
        }

        /** The YAML node of the key of the last segment of a pointer, or null. */
        private Node keyNode(String pointer) {
            Node node = root;
            Node key = null;
            for (String segment : pointer.split("/")) {
                if (segment.isEmpty()) {
                    continue;
                }
                String name = segment.replace("~1", "/").replace("~0", "~");
                key = null;
                if (node instanceof SequenceNode seq && INTEGER.matcher(name).matches()) {
                    int index = Integer.parseInt(name);
                    node = index < seq.getValue().size() ? seq.getValue().get(index) : null;
                } else if (node instanceof MappingNode map) {
                    Node value = null;
                    for (NodeTuple t : map.getValue()) {
                        if (name.equals(scalar(t.getKeyNode()))) {
                            key = t.getKeyNode();
                            value = t.getValueNode();
                        }
                    }
                    node = value;
                } else {
                    return null;
                }
                if (node == null) {
                    return null;
                }
            }
            return key;
        }

        /**
         * The keys the object takes that the schema location of an error is at (the location of the
         * additionalProperties or the type of the object): its properties and those of its branches.
         */
        private Set<String> keysAt(Error error) {
            String location = String.valueOf(error.getSchemaLocation());
            int hash = location.indexOf('#');
            String pointer = hash >= 0 ? location.substring(hash + 1) : "";
            pointer = pointer.substring(0, Math.max(0, pointer.lastIndexOf('/')));
            Set<String> answer = new LinkedHashSet<>();
            collectKeys(schema.model().at(pointer), answer, 0);
            return answer;
        }

        /** The keys a node of the schema takes: its properties and those of its references and branches. */
        private Set<String> keysOf(JsonNode node) {
            Set<String> answer = new LinkedHashSet<>();
            collectKeys(node, answer, 0);
            return answer;
        }

        /**
         * The schema of a property of a node of the schema, through its references, branches and the items of a list
         * (the methods of sendRequest are declared {} and have their schema in a branch); missing when there is none.
         */
        private JsonNode property(JsonNode node, String name) {
            return property(node, name, 0);
        }

        private JsonNode property(JsonNode node, String name, int depth) {
            if (node == null || node.isMissingNode() || depth > 6) {
                return MissingNode.getInstance();
            }
            JsonNode p = node.path("properties").path(name);
            if (!p.isMissingNode() && !p.isEmpty()) {
                return p;
            }
            List<JsonNode> more = new ArrayList<>();
            JsonNode ref = node.get("$ref");
            if (ref != null && ref.isString() && ref.asString().startsWith("#")) {
                more.add(schema.model().at(ref.asString("").substring(1)));
            }
            for (String composition : new String[] { "allOf", "anyOf", "oneOf" }) {
                node.path(composition).forEach(more::add);
            }
            if (node.has("items")) {
                more.add(node.get("items"));
            }
            for (JsonNode n : more) {
                JsonNode r = property(n, name, depth + 1);
                if (!r.isMissingNode()) {
                    return r;
                }
            }
            return p;
        }

        /** Whether the schema has the properties one under the other from the node: receiveResponse, response. */
        private boolean hasPath(JsonNode node, String... names) {
            JsonNode n = node;
            for (String name : names) {
                n = property(n, name);
                if (n.isMissingNode()) {
                    return false;
                }
            }
            return true;
        }

        /** Whether a key the http action does not take is a part of a request or its response. */
        private boolean isCallKey(String key, JsonNode http) {
            return URL_KEYS.contains(key) || METHOD_KEYS.contains(key) || FILE_KEYS.contains(key)
                    || BODY_KEYS.contains(key) || STATUS_KEYS.contains(key) || RESPONSE_BODY_KEYS.contains(key)
                    || "request".equals(key) || "response".equals(key) || new HttpCall(http).isRequestOrResponseKey(key);
        }

        /**
         * A call written as one http action, put in the two actions of a client: the request that sendRequest sends,
         * with its method, and the response that receiveResponse expects. The keys of the request and of the response
         * are the ones of the schema; the names models write for them are mapped.
         */
        private final class HttpCall {

            private final Set<String> methods = new LinkedHashSet<>();
            private final Set<String> requestKeys;
            private final Set<String> responseKeys;
            private final ObjectNode request = JSON.createObjectNode();
            private final ObjectNode response = JSON.createObjectNode();
            /** The keys as written that have no place in the two actions. */
            private final List<String> left = new ArrayList<>();
            private String client;
            private String method;
            private String description;

            HttpCall(JsonNode http) {
                JsonNode send = property(http, "sendRequest");
                // the methods are the keys of sendRequest in upper case: GET, POST...
                keysOf(send).stream().filter(k -> k.equals(k.toUpperCase(Locale.ROOT))).forEach(methods::add);
                requestKeys = methods.isEmpty() ? Set.of() : plainKeys(property(send, methods.iterator().next()));
                responseKeys = plainKeys(property(property(http, "receiveResponse"), "response"));
            }

            /**
             * The keys of a request or a response that are not of a group (advanced, schema...): a key of a model's own
             * with the name of one of those, such as type: Async, is not meant as it.
             */
            private Set<String> plainKeys(JsonNode node) {
                Set<String> answer = new LinkedHashSet<>();
                keysOf(node).stream().filter(k -> !property(node, k).has("$comment")).forEach(answer::add);
                return answer;
            }

            boolean isRequestOrResponseKey(String key) {
                return requestKeys.contains(key) || responseKeys.contains(key);
            }

            /**
             * Puts a key as written in the request or the response, or among the keys left out: side is request or
             * response for the keys of a request: or response: map, null for a key of the action.
             */
            void add(String key, JsonNode value, String side) {
                if (!place(key, value, side)) {
                    left.add(key);
                }
            }

            private boolean place(String key, JsonNode value, String side) {
                boolean forRequest = !"response".equals(side);
                if (side == null && "client".equals(key) && value.isString()) {
                    client = value.asString("").strip();
                } else if (side == null && "description".equals(key) && value.isString()) {
                    description = value.asString("");
                } else if (side == null && ("request".equals(key) || "response".equals(key)) && value.isObject()) {
                    value.properties().forEach(e -> add(e.getKey(), e.getValue(), key));
                } else if (side == null && "sendRequest".equals(key) && value.isObject() && value.size() == 1
                        && methods.contains(value.propertyNames().iterator().next())
                        && value.values().iterator().next().isObject()) {
                    method = value.propertyNames().iterator().next();
                    value.get(method).properties().forEach(e -> request.set(e.getKey(), e.getValue()));
                } else if (side == null && "receiveResponse".equals(key) && value.path("response").isObject()
                        && value.size() == 1) {
                    value.get("response").properties().forEach(e -> response.set(e.getKey(), e.getValue()));
                } else if (forRequest && URL_KEYS.contains(key) && value.isString()) {
                    Matcher m = URL.matcher(value.asString("").strip());
                    if (m.matches()) {
                        client = m.group(1);
                        if (!m.group(2).isEmpty()) {
                            request.put("path", m.group(2));
                        }
                    } else {
                        request.put("path", value.asString("").strip());
                    }
                } else if (forRequest && METHOD_KEYS.contains(key) && value.isString()) {
                    method = value.asString("").strip().toUpperCase(Locale.ROOT);
                } else if (forRequest && FILE_KEYS.contains(key) && value.isString()) {
                    request.set("body", resource(fromTest(value.asString("").strip())));
                } else if (!"request".equals(side) && STATUS_KEYS.contains(key) && value.isValueNode()) {
                    response.put("status", value.asString(""));
                } else if (side == null && RESPONSE_BODY_KEYS.contains(key) && value.isString()) {
                    response.set("body", body(value));
                } else if ("body".equals(key) || forRequest && BODY_KEYS.contains(key)) {
                    JsonNode body = body(value);
                    if (body == null) {
                        return false;
                    }
                    (forRequest ? request : response).set("body", body);
                } else if (value.isValueNode() && !value.isNull()
                        && (forRequest ? requestKeys : responseKeys).contains(key)) {
                    // contentType and path of the request, contentType of the response
                    (forRequest ? request : response).put(key, value.asString(""));
                } else {
                    return false;
                }
                return true;
            }

            /**
             * The body of a request or a response: the name of a file as a resource to load, other text as data, or a
             * map of one of data, resource or script.
             */
            private JsonNode body(JsonNode value) {
                String file = value.isString() ? bodyFile(value.asString()) : null;
                if (file != null) {
                    return resource(file);
                }
                if (value.isValueNode() && !value.isNull()) {
                    return JSON.createObjectNode().put("data", value.asString(""));
                }
                return value.isObject() && value.size() == 1
                        && (value.has("data") || value.has("resource") || value.has("script")) ? value : null;
            }

            /**
             * The action that sends the request, and the one that receives the response when a part of it is given;
             * null without a client, or with a method the schema does not have.
             */
            List<JsonNode> actions() {
                String m = method != null ? method : request.has("body") ? "POST" : "GET";
                if (client == null || client.isBlank() || !methods.contains(m)) {
                    return null;
                }
                List<JsonNode> answer = new ArrayList<>();
                ObjectNode send = JSON.createObjectNode();
                send.set(m, ordered(request, "path"));
                answer.add(http("sendRequest", send));
                if (description != null) {
                    ((ObjectNode) answer.get(0).get("http")).put("description", description);
                }
                if (!response.isEmpty()) {
                    ObjectNode receive = JSON.createObjectNode();
                    receive.set("response", ordered(response, "status"));
                    answer.add(http("receiveResponse", receive));
                }
                return answer;
            }

            private ObjectNode http(String key, ObjectNode value) {
                ObjectNode http = JSON.createObjectNode().put("client", client);
                http.set(key, value);
                ObjectNode action = JSON.createObjectNode();
                action.set("http", http);
                return action;
            }

            /** The keys with the given one first and the body last, as the tests of the examples write them. */
            private static ObjectNode ordered(ObjectNode node, String first) {
                ObjectNode answer = JSON.createObjectNode();
                if (node.has(first)) {
                    answer.set(first, node.get(first));
                }
                node.properties().forEach(e -> {
                    if (!first.equals(e.getKey()) && !"body".equals(e.getKey())) {
                        answer.set(e.getKey(), e.getValue());
                    }
                });
                if (node.has("body")) {
                    answer.set("body", node.get("body"));
                }
                return answer;
            }
        }

        private void collectKeys(JsonNode node, Set<String> answer, int depth) {
            if (node == null || node.isMissingNode() || depth > 4) {
                return;
            }
            node.path("properties").propertyNames().forEach(answer::add);
            JsonNode ref = node.get("$ref");
            if (ref != null && ref.isString() && ref.asString().startsWith("#")) {
                collectKeys(schema.model().at(ref.asString("").substring(1)), answer, depth + 1);
            }
            for (String composition : new String[] { "allOf", "anyOf", "oneOf" }) {
                for (JsonNode entry : node.path(composition)) {
                    if (!entry.has("not")) {
                        collectKeys(entry, answer, depth + 1);
                    }
                }
            }
        }

        /**
         * The keys from the action down to the node, such as http.sendRequest.POST.body, or from the top of the test
         * for a node that is in no action (variables); empty for the test itself.
         */
        private String where(String pointer) {
            String base = "";
            for (String item : items.keySet()) {
                if ((pointer.equals(item) || pointer.startsWith(item + "/")) && item.length() > base.length()) {
                    base = item;
                }
            }
            StringJoiner sj = new StringJoiner(".");
            for (String segment : pointer.substring(base.length()).split("/")) {
                if (!segment.isEmpty() && !INTEGER.matcher(segment).matches()) {
                    sj.add(segment.replace("~1", "/").replace("~0", "~"));
                }
            }
            return sj.toString();
        }

        private void add(int line, String text) {
            problems.add(new Problem(line, text));
        }
    }

    /** The last key of a path such as http.sendRequest.POST, or what the test itself is called. */
    private static String name(String what) {
        return what.isEmpty() ? "a Citrus test" : what.substring(what.lastIndexOf('.') + 1);
    }

    /** Whether the keys an object takes are the ones of a message body: data, resource and script. */
    private static boolean isBody(Set<String> keys) {
        return keys.contains("data") && keys.contains("resource");
    }

    /** Whether the node at the pointer is in a message the test receives, such as the response of an http request. */
    private static boolean isReceived(String pointer) {
        return List.of(pointer.split("/")).stream()
                .anyMatch(s -> "receiveResponse".equals(s) || "receiveRequest".equals(s) || "receive".equals(s));
    }

    /** The pointer of the node that holds the node at the pointer. */
    private static String parent(String pointer) {
        return pointer.substring(0, Math.max(0, pointer.lastIndexOf('/')));
    }

    /** The fields of a JSON object with a text, number or boolean value, by name; empty for any other text. */
    private static Map<String, String> jsonFields(String text) {
        Map<String, String> answer = new LinkedHashMap<>();
        try {
            JsonNode json = text != null && text.strip().startsWith("{") ? JSON.readTree(text) : null;
            if (json != null && json.isObject()) {
                json.properties().forEach(e -> {
                    if (e.getValue().isValueNode() && !e.getValue().isNull() && e.getKey().matches("[A-Za-z_]\\w*")) {
                        answer.put(e.getKey(), e.getValue().asString(""));
                    }
                });
            }
        } catch (JacksonException e) {
            // not JSON: no fields
        }
        return answer;
    }

    /**
     * What is said of a test that calls an integration (an http action, or a check of what an integration logged) but
     * does not start one: camel test run runs only the test. The action that starts it, with the route file of the
     * project when there is one, and the name the checks of the test give the integration (no test a local model wrote
     * for test-citrus-orders step 1 started the integration). Empty when the test starts one or calls none.
     *
     * @param content the test
     * @param dir     the project directory, or null
     * @param file    the test file, relative to the directory
     */
    static String startNote(String content, Path dir, String file) {
        TestSchema s = schema();
        JsonNode tree;
        try {
            tree = s.model() != null && content != null && !content.isBlank() ? YAML.readTree(content) : null;
        } catch (JacksonException e) {
            tree = null;
        }
        if (tree == null || !tree.isObject()) {
            return "";
        }
        String cli = s.model().at("/definitions/TestActions/properties/camel/properties/cli").isMissingNode()
                ? "jbang" : "cli";
        List<JsonNode> camel = tree.findValues("camel");
        if (camel.stream().anyMatch(c -> c.path("cli").has("run") || c.path("jbang").has("run"))) {
            return "";
        }
        String name = camel.stream().map(c -> c.path(cli).path("verify").path("integration"))
                .filter(JsonNode::isString).map(JsonNode::asString).findFirst().orElse(null);
        if (name == null && tree.findValues("http").isEmpty()) {
            return "";
        }
        // the route file of the project: the one *.camel.yaml in the directory, read from the directory of the test
        Path project = dir != null ? dir.toAbsolutePath().normalize() : null;
        Path testDir = project != null && file != null ? project.resolve(file).normalize().getParent() : null;
        List<Path> routes = new ArrayList<>();
        if (testDir != null && Files.isDirectory(project)) {
            try (Stream<Path> files = Files.list(project)) {
                files.filter(p -> p.getFileName().toString().matches(".+\\.camel\\.ya?ml")).sorted().forEach(routes::add);
            } catch (IOException e) {
                routes.clear();
            }
        }
        String start = "The test calls the integration but does not start it, and camel test run runs only the test:"
                       + " start it with the first action of the test (or have it running when the test runs)";
        if (routes.size() != 1) {
            String example = name != null ? name : "my-app";
            return start + ", such as - camel: {" + cli + ": {run: {integration: {name: " + example + ", file: ../"
                   + example + ".camel.yaml}}}} for the route file " + example + ".camel.yaml in the directory above the"
                   + " test (a file is relative to the test)";
        }
        String route = routes.get(0).getFileName().toString();
        ObjectNode integration = JSON.createObjectNode();
        integration.put("name", name != null ? name : route.substring(0, route.indexOf(".camel.")));
        integration.put("file", testDir.relativize(routes.get(0)).toString().replace('\\', '/'));
        if (Files.isRegularFile(project.resolve("application.properties"))) {
            integration.set("systemProperties", JSON.createObjectNode().put("file",
                    testDir.relativize(project.resolve("application.properties")).toString().replace('\\', '/')));
        }
        ObjectNode run = JSON.createObjectNode();
        run.set("integration", integration);
        ObjectNode action = JSON.createObjectNode();
        action.set("camel", JSON.createObjectNode().set(cli, JSON.createObjectNode().set("run", run)));
        List<Error> errors = new ArrayList<>(YamlValidator.filterOneOfNoise(new ArrayList<>(s.action().validate(action))));
        if (!errors.isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        yaml(action, 4, lines);
        lines.set(0, "  - " + lines.get(0).substring(4));
        return start + ":\n" + String.join("\n", lines);
    }

    /**
     * A node of the schema's shape as YAML lines in block style at the indentation given: a text value quoted, a text
     * of several lines as a block, an empty map as {}.
     */
    static void yaml(JsonNode node, int indent, List<String> out) {
        String pad = " ".repeat(indent);
        node.properties().forEach(e -> {
            JsonNode v = e.getValue();
            if (v.isObject() && !v.isEmpty()) {
                out.add(pad + e.getKey() + ":");
                yaml(v, indent + 2, out);
            } else if (v.isObject()) {
                out.add(pad + e.getKey() + ": {}");
            } else if (v.isArray()) {
                out.add(pad + e.getKey() + ":");
                for (JsonNode item : v) {
                    List<String> one = new ArrayList<>();
                    yaml(item, indent + 4, one);
                    if (!one.isEmpty()) {
                        one.set(0, pad + "  - " + one.get(0).substring(indent + 4));
                        out.addAll(one);
                    }
                }
            } else if (v.asString("").contains("\n")) {
                String text = v.asString("");
                boolean newline = text.endsWith("\n");
                out.add(pad + e.getKey() + ": " + (newline ? "|" : "|-"));
                for (String l : (newline ? text.substring(0, text.length() - 1) : text).split("\n", -1)) {
                    out.add(l.isEmpty() ? "" : pad + "  " + l);
                }
            } else {
                out.add(pad + e.getKey() + ": " + quoted(v.asString("")));
            }
        });
    }

    /**
     * A text as a YAML value in quotes: double quotes, or single quotes when it holds a double quote or a backslash.
     */
    static String quoted(String text) {
        return text.contains("\"") || text.contains("\\")
                ? "'" + text.replace("'", "''") + "'"
                : "\"" + text + "\"";
    }

    private static int indent(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ') {
            i++;
        }
        return i;
    }

    private static boolean isComposition(Error e) {
        return "oneOf".equals(e.getKeyword()) || "anyOf".equals(e.getKeyword());
    }

    /** The instance location of an error as a pointer, "" for the root. */
    private static String location(Error e) {
        String at = e.getInstanceLocation() != null ? e.getInstanceLocation().toString() : "";
        return "/".equals(at) ? "" : at;
    }

    private static String escape(String key) {
        return key.replace("~", "~0").replace("/", "~1");
    }

    private static String scalar(Node node) {
        return node instanceof ScalarNode s ? s.getValue() : null;
    }

    private static int line(Node node) {
        return node.getStartMark().map(mark -> mark.getLine() + 1).orElse(0);
    }

    /** assert-log and assert_log as assertLog. */
    private static String camelCase(String name) {
        StringBuilder sb = new StringBuilder();
        boolean upper = false;
        for (char ch : name.toCharArray()) {
            if (ch == '-' || ch == '_') {
                upper = sb.length() > 0;
            } else {
                sb.append(upper ? Character.toUpperCase(ch) : ch);
                upper = false;
            }
        }
        return sb.toString();
    }

    /** The words of a name in lower case: assertLog, assert-log and assert_log are assert and log. */
    private static List<String> camelCaseWords(String name) {
        return List.of(camelCase(name).split("(?<=[a-z0-9])(?=[A-Z])")).stream()
                .map(w -> w.toLowerCase(Locale.ROOT)).toList();
    }

    /** a, b and c. */
    private static String and(List<String> words) {
        if (words.size() <= 1) {
            return String.join("", words);
        }
        return String.join(", ", words.subList(0, words.size() - 1)) + " and " + words.get(words.size() - 1);
    }
}
