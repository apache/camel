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
import java.util.ArrayList;
import java.util.Comparator;
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
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import org.apache.camel.dsl.jbang.core.common.CitrusCatalogVersion;
import org.apache.camel.dsl.yaml.validator.YamlPointerLines;
import org.apache.camel.dsl.yaml.validator.YamlValidator;
import org.apache.camel.main.download.MavenDependencyDownloader;
import org.apache.camel.tooling.maven.MavenArtifact;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.nodes.SequenceNode;

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

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern INTEGER = Pattern.compile("[-+]?\\d+");
    /** The words SnakeYAML, which Citrus reads a test with, takes as a boolean. */
    private static final Set<String> BOOLEAN_WORDS = Set.of("true", "false", "yes", "no", "on", "off");

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
        } catch (IOException e) {
            return List.of();
        }
        if (tree == null || tree.isMissingNode()) {
            return List.of();
        }
        return new Check(s, content, tree).problems();
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
        model.at("/definitions/TestActions/properties").fieldNames().forEachRemaining(actions::add);
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
        if (r != null && r.isTextual() && ref.test(r.asText())) {
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
        if (value.isTextual()) {
            String text = value.asText().trim();
            if (expects(error, "integer")) {
                return INTEGER.matcher(text).matches();
            }
            if (expects(error, "number")) {
                try {
                    Double.parseDouble(text);
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
                if (type.equals(t.asText())) {
                    return true;
                }
            }
            return false;
        }
        return node != null && type.equals(node.asText());
    }

    /** The checks of one test: its tree for the schema, its YAML nodes for the lines and the keys of the actions. */
    private static final class Check {

        private final TestSchema schema;
        private final JsonNode tree;
        private final Node root;
        /** The action of each action item, by its pointer (/actions/0: http), for the path a message names. */
        private final Map<String, String> items = new LinkedHashMap<>();
        /** The pointers where an action is expected: the items of the action lists. */
        private final Set<String> slots = new HashSet<>();
        private final List<Problem> problems = new ArrayList<>();

        private record Problem(int line, String text) {
        }

        Check(TestSchema schema, String content, JsonNode tree) {
            this.schema = schema;
            this.tree = tree;
            this.root = YamlPointerLines.root(content);
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
                // log:, assert-log: or assertLog: in 8 of the 11 tests a local model wrote (test-citrus-orders)
                String cli = schema.model().at("/definitions/TestActions/properties/camel/properties/cli").isMissingNode()
                        ? "jbang" : "cli";
                return answer + ": echo: {message: \"...\"} writes a message to the test log, and camel: {" + cli
                       + ": {verify: {integration: \"<name>\", logMessage: \"...\"}}} checks that the integration"
                       + " logged a line";
            }
            return answer + "; the actions are " + String.join(", ", schema.actions());
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
                    return what + ": " + message + "; " + name(what) + " takes " + String.join(", ", keys);
                }
            }
            if ("enum".equals(e.getKeyword()) && e.getSchemaNode() != null && e.getInstanceNode() != null) {
                List<String> choices = new ArrayList<>();
                e.getSchemaNode().forEach(c -> choices.add(c.asText()));
                String value = e.getInstanceNode().asText();
                String closest = QuickFixes.closest(value, choices);
                return (what.isEmpty() ? "" : what + ": ") + value + " is not one of " + String.join(", ", choices)
                       + (closest != null ? " (did you mean " + closest + "?)" : "");
            }
            if ("oneOf".equals(e.getKeyword()) && e.getSchemaNode() != null && e.getInstanceNode() != null) {
                // the keys of which an object takes one (body: data, resource or script), given together
                List<String> keys = new ArrayList<>();
                e.getSchemaNode().forEach(branch -> {
                    if (branch.path("required").size() == 1) {
                        keys.add(branch.path("required").get(0).asText());
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
            String of = what.isEmpty() ? "a Citrus test"
                    : items.containsKey(at.substring(0, Math.max(0, at.lastIndexOf('/')))) ? "the " + what + " action"
                    : what;
            return and(named) + (keys.size() == 1 ? " is not a key of " : " are not keys of ") + of
                   + (takes.isEmpty() ? "" : ", which takes " + String.join(", ", takes));
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

        private void collectKeys(JsonNode node, Set<String> answer, int depth) {
            if (node == null || node.isMissingNode() || depth > 4) {
                return;
            }
            node.path("properties").fieldNames().forEachRemaining(answer::add);
            JsonNode ref = node.get("$ref");
            if (ref != null && ref.isTextual() && ref.asText().startsWith("#")) {
                collectKeys(schema.model().at(ref.asText().substring(1)), answer, depth + 1);
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
