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
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

/**
 * The kamelet: endpoints of a YAML route against the Kamelets they name ({@link KameletDefinitions}): a Kamelet that
 * does not exist, a property it does not have, a required property left out. The catalog validates the options of a
 * component's endpoint; the options of a kamelet: endpoint are the properties of a template it does not know, so these
 * went unchecked until the route failed to start ("mandatory parameters must be provided: message") or, for a
 * misspelled optional one, never failed and did nothing.
 */
public final class KameletChecks {

    /** The keys whose value is an endpoint uri, written as uri: under them or as their own value. */
    private static final Set<String> ENDPOINT_KEYS = Set.of("from", "to", "toD", "to-d", "wireTap", "wire-tap", "enrich",
            "pollEnrich", "poll-enrich");

    private KameletChecks() {
    }

    /** The errors of the kamelet: endpoints of a YAML route, each with its line; empty when it has none. */
    public static List<String> validateYaml(String content, Path directory) {
        return validateYaml(content, directory, true);
    }

    /**
     * As {@link #validateYaml(String, Path)}; unknownNames false for a write, where a Kamelet no catalog or project has
     * may be the project's own one not written yet: then only a name close to a known one is an error (the route and
     * its Kamelet could otherwise only be written in one order, as for direct: consumers in CAMEL-24955).
     */
    public static List<String> validateYaml(String content, Path directory, boolean unknownNames) {
        List<String> errors = new ArrayList<>();
        if (content == null || !content.contains("kamelet:")) {
            return errors;
        }
        List<Node> roots = new ArrayList<>();
        try {
            for (Node n : new Yaml(new SafeConstructor(new LoaderOptions())).composeAll(new StringReader(content))) {
                roots.add(n);
            }
        } catch (Exception e) {
            // not YAML: the schema check says so
            return errors;
        }
        Map<String, String> properties = directory != null ? projectProperties(directory) : Map.of();
        for (Node root : roots) {
            walk(root, null, errors, directory, properties, unknownNames);
        }
        return errors;
    }

    private static void walk(
            Node node, String parentKey, List<String> errors, Path directory, Map<String, String> properties,
            boolean unknownNames) {
        if (node instanceof SequenceNode seq) {
            for (Node n : seq.getValue()) {
                walk(n, parentKey, errors, directory, properties, unknownNames);
            }
        } else if (node instanceof MappingNode map) {
            ScalarNode uri = null;
            MappingNode parameters = null;
            for (NodeTuple t : map.getValue()) {
                String key = t.getKeyNode() instanceof ScalarNode k ? k.getValue() : null;
                if ("uri".equals(key) && t.getValueNode() instanceof ScalarNode s) {
                    uri = s;
                } else if ("parameters".equals(key) && t.getValueNode() instanceof MappingNode p) {
                    parameters = p;
                } else if (ENDPOINT_KEYS.contains(key) && t.getValueNode() instanceof ScalarNode s) {
                    // to: kamelet:log-sink, the short form
                    check(s, null, errors, directory, properties, unknownNames);
                }
            }
            if (uri != null && (parentKey == null || ENDPOINT_KEYS.contains(parentKey) || "from".equals(parentKey))) {
                check(uri, parameters, errors, directory, properties, unknownNames);
            }
            for (NodeTuple t : map.getValue()) {
                String key = t.getKeyNode() instanceof ScalarNode k ? k.getValue() : null;
                if (!"parameters".equals(key)) {
                    walk(t.getValueNode(), key, errors, directory, properties, unknownNames);
                }
            }
        }
    }

    private static void check(
            ScalarNode uriNode, MappingNode parameters, List<String> errors, Path directory,
            Map<String, String> properties, boolean unknownNames) {
        String uri = uriNode.getValue();
        if (uri == null || !uri.startsWith("kamelet:")) {
            return;
        }
        String rest = uri.substring("kamelet:".length());
        String query = null;
        int q = rest.indexOf('?');
        if (q >= 0) {
            query = rest.substring(q + 1);
            rest = rest.substring(0, q);
        }
        String name = rest.contains("/") ? rest.substring(0, rest.indexOf('/')) : rest;
        if (name.isEmpty() || "source".equals(name) || "sink".equals(name) || name.contains("{{")
                || name.contains("${")) {
            // kamelet:source and kamelet:sink are the ends of a Kamelet's own template
            return;
        }
        int line = uriNode.getStartMark().getLine();
        KameletDefinitions.Definition def = KameletDefinitions.find(name, directory);
        if (def == null) {
            Map<String, KameletDefinitions.Definition> catalog = KameletDefinitions.catalog();
            if (catalog.isEmpty()) {
                // the catalog cannot be read: nothing to check against
                return;
            }
            List<String> names = new ArrayList<>(catalog.keySet());
            if (directory != null) {
                names.addAll(KameletDefinitions.projectKamelets(directory).keySet());
            }
            List<String> suggestions = KameletDefinitions.suggest(name, names, 3);
            if (!unknownNames && suggestions.isEmpty()) {
                return;
            }
            StringBuilder sb = new StringBuilder(EndpointChecks.linePrefix(line)).append("kamelet:").append(name)
                    .append(": no Kamelet named ").append(name).append(" in the Kamelet catalog or the project");
            if (!suggestions.isEmpty()) {
                sb.append(". Did you mean: ").append(String.join(", ", suggestions));
            }
            sb.append("; camel_catalog_doc name=<kamelet> has the properties of one (a Kamelet of the project is")
                    .append(" a ").append(name).append(".kamelet.yaml file beside the route)");
            errors.add(sb.toString());
            return;
        }
        Map<String, Integer> given = new LinkedHashMap<>();
        if (query != null) {
            for (String part : query.split("&")) {
                String key = part.contains("=") ? part.substring(0, part.indexOf('=')) : part;
                if (!key.isBlank()) {
                    given.put(key, line);
                }
            }
        }
        if (parameters != null) {
            for (NodeTuple t : parameters.getValue()) {
                if (t.getKeyNode() instanceof ScalarNode k) {
                    given.put(k.getValue(), k.getStartMark().getLine());
                }
            }
        }
        String where = def.source().startsWith("the project") ? " (" + def.source() + ")" : "";
        for (Map.Entry<String, Integer> e : given.entrySet()) {
            if (def.property(e.getKey()) == null) {
                List<String> close = KameletDefinitions.suggestProperty(e.getKey(), def);
                StringBuilder sb = new StringBuilder(EndpointChecks.linePrefix(e.getValue())).append("kamelet:")
                        .append(name).append(": unknown property '").append(e.getKey()).append("'");
                if (!close.isEmpty()) {
                    sb.append(". Did you mean: ").append(String.join(", ", close));
                }
                sb.append(". The properties of ").append(name).append(where).append(": ")
                        .append(def.properties().isEmpty() ? "none" : KameletDefinitions.propertyList(def));
                if (!where.isEmpty()) {
                    // the project's own Kamelet: the property may be what the Kamelet lacks, not the route
                    sb.append("; a property of a Kamelet is declared in its file under spec.definition.properties")
                            .append(" (and listed under required: when it must be given)");
                }
                errors.add(sb.toString());
            }
        }
        Set<String> missing = new TreeSet<>();
        for (KameletDefinitions.Property p : def.properties()) {
            if (p.required() && p.defaultValue() == null && !given.containsKey(p.name())
                    && !inProperties(properties, name, p.name())) {
                missing.add(p.name());
            }
        }
        if (!missing.isEmpty()) {
            errors.add(EndpointChecks.linePrefix(line) + "kamelet:" + name + ": the required "
                       + (missing.size() == 1 ? "property " : "properties ") + String.join(", ", missing)
                       + (missing.size() == 1 ? " is" : " are")
                       + " missing (the runtime says 'mandatory parameters must be provided: "
                       + String.join(",", missing) + "'): add "
                       + (missing.size() == 1 ? "it" : "them") + " under parameters. The properties of " + name
                       + where + ": " + KameletDefinitions.propertyList(def));
        }
    }

    private static final Pattern UNKNOWN_FUNCTION
            = Pattern.compile(
                    "Unknown function: (?:properties\\.|property\\.|header\\.|exchangeProperty\\.|variable\\.)?([\\w-]+)");

    /**
     * The messages of a Kamelet file with what a model gets wrong in its template: a property of the Kamelet written as
     * a Simple function (${properties.tag}, ${header.tag}) where the template has it as the placeholder {{tag}}.
     */
    public static List<String> withTemplateHints(String fileName, String content, List<String> msgs) {
        if (msgs.isEmpty() || fileName == null || !fileName.endsWith(".kamelet.yaml")) {
            return msgs;
        }
        KameletDefinitions.Definition def = KameletDefinitions.parse(content, fileName, fileName);
        if (def == null || def.properties().isEmpty()) {
            return msgs;
        }
        List<String> answer = new ArrayList<>();
        for (String m : msgs) {
            Matcher matcher = UNKNOWN_FUNCTION.matcher(m);
            if (matcher.find() && def.property(matcher.group(1)) != null) {
                String p = matcher.group(1);
                m = m + " (in a Kamelet's template its property " + p + " is the placeholder {{" + p
                    + "}}, as in simple: \"${body} {{" + p + "}}\"; it is not a header or an exchange property)";
            }
            answer.add(m);
        }
        return answer;
    }

    /** Whether application properties set the property of the Kamelet: camel.kamelet.name.prop or name.routeId.prop. */
    static boolean inProperties(Map<String, String> properties, String kamelet, String property) {
        String prefix = "camel.kamelet." + kamelet + ".";
        for (String key : properties.keySet()) {
            if (key.startsWith(prefix) && (key.equals(prefix + property) || key.endsWith("." + property))) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, String> projectProperties(Path directory) {
        Map<String, String> answer = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(p -> p.getFileName().toString().endsWith(".properties")).forEach(p -> {
                try {
                    for (String l : Files.readAllLines(p)) {
                        String s = l.trim();
                        int eq = s.indexOf('=');
                        if (s.startsWith("camel.kamelet.") && eq > 0) {
                            answer.put(s.substring(0, eq).trim(), s.substring(eq + 1).trim());
                        }
                    }
                } catch (IOException e) {
                    // an unreadable file sets nothing
                }
            });
        } catch (IOException e) {
            // no directory listing: no properties
        }
        return answer;
    }
}
