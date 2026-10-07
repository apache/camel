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
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.tooling.model.ArtifactModel;
import org.apache.camel.tooling.model.ComponentModel;
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
        Set<String> componentOptions = kameletComponentOptions();
        for (Map.Entry<String, Integer> e : given.entrySet()) {
            if (def.property(e.getKey()) == null && !componentOptions.contains(e.getKey())) {
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

    private static volatile Set<String> componentOptions;

    /**
     * The options of the kamelet component's own endpoint (routeId, location, timeout, noErrorHandler...): they go on a
     * kamelet: endpoint beside the Kamelet's properties.
     */
    static Set<String> kameletComponentOptions() {
        Set<String> answer = componentOptions;
        if (answer == null) {
            Set<String> names = new HashSet<>();
            try {
                ComponentModel model = new DefaultCamelCatalog().componentModel("kamelet");
                if (model != null) {
                    model.getEndpointOptions().forEach(o -> names.add(o.getName()));
                }
            } catch (Exception e) {
                // the list below
            }
            if (names.isEmpty()) {
                names.addAll(Set.of("templateId", "routeId", "location", "uuid", "block", "timeout",
                        "failIfNoConsumers", "noErrorHandler", "bridgeErrorHandler", "lazyStartProducer",
                        "exchangePattern", "exceptionHandler"));
            }
            componentOptions = answer = names;
        }
        return answer;
    }

    /** The keys of spec of a Kamelet; anything else is not read. */
    private static final Set<String> SPEC_KEYS = Set.of("definition", "template", "dependencies", "types", "dataTypes",
            "sources", "flow");

    /**
     * The shape of a Kamelet file: what a model gets wrong writing one, and the runtime only reports as a Kamelet that
     * cannot be loaded or used. spec.template with from: is the route; the properties are under spec.definition, with
     * the required ones listed in spec.definition.required; an action or a sink starts from kamelet:source.
     */
    public static List<String> validateKameletFile(String content) {
        List<String> errors = new ArrayList<>();
        Node root;
        try {
            root = new Yaml(new SafeConstructor(new LoaderOptions())).compose(new StringReader(content));
        } catch (Exception e) {
            // not YAML: the schema check says so
            return errors;
        }
        if (!(root instanceof MappingNode doc) || !"Kamelet".equals(scalar(value(doc, "kind")))) {
            return errors;
        }
        Node specNode = value(doc, "spec");
        if (!(specNode instanceof MappingNode spec)) {
            errors.add(EndpointChecks.linePrefix(line(doc)) + "a Kamelet has a spec: with definition: (its properties)"
                       + " and template: (its route); " + AuthoringTools.KAMELET_GUIDE);
            return errors;
        }
        for (NodeTuple t : spec.getValue()) {
            String key = scalar(t.getKeyNode());
            if (key == null || SPEC_KEYS.contains(key)) {
                continue;
            }
            String hint = switch (key) {
                case "properties", "required" -> "it goes under spec.definition (definition: {required: [...],"
                                                 + " properties: {name: {type: string, ...}}})";
                case "from", "route", "steps", "do", "flow", "routeTemplate" -> "the route of a Kamelet is spec.template:"
                                                                                + " template: {from: {uri: kamelet:source, steps: [...]}}";
                default -> "the keys of spec are definition, template, dependencies and types";
            };
            errors.add(EndpointChecks.linePrefix(t.getKeyNode().getStartMark().getLine()) + "spec." + key
                       + " is not a key of a Kamelet: " + hint);
        }
        Node definition = value(spec, "definition");
        Set<String> declared = new java.util.LinkedHashSet<>();
        if (definition instanceof MappingNode def) {
            if (value(def, "properties") instanceof MappingNode props) {
                for (NodeTuple p : props.getValue()) {
                    String pname = scalar(p.getKeyNode());
                    declared.add(pname);
                    if (p.getValueNode() instanceof MappingNode pm && value(pm, "required") != null) {
                        errors.add(EndpointChecks.linePrefix(value(pm, "required").getStartMark().getLine())
                                   + "spec.definition.properties." + pname + ".required: a required property is listed"
                                   + " in spec.definition.required (required: [" + pname + "]), not marked on the"
                                   + " property");
                    }
                }
            }
            if (value(def, "required") instanceof SequenceNode req) {
                for (Node r : req.getValue()) {
                    String rname = scalar(r);
                    if (rname != null && !declared.contains(rname)) {
                        errors.add(EndpointChecks.linePrefix(r.getStartMark().getLine()) + "spec.definition.required"
                                   + " lists " + rname + ", which is not under spec.definition.properties");
                    }
                }
            }
        }
        if (value(spec, "dependencies") instanceof SequenceNode deps) {
            for (Node d : deps.getValue()) {
                String dep = scalar(d);
                String problem = dep != null && dep.startsWith("camel:") ? camelDependency(dep.substring(6)) : null;
                if (problem != null) {
                    errors.add(EndpointChecks.linePrefix(d.getStartMark().getLine()) + "spec.dependencies: " + dep + " "
                               + problem);
                }
            }
        }
        Node template = value(spec, "template");
        if (template == null) {
            errors.add(EndpointChecks.linePrefix(spec.getStartMark().getLine()) + "the Kamelet has no spec.template:"
                       + " its route, template: {from: {uri: kamelet:source, steps: [...]}} for an action or a sink, or"
                       + " from: the component for a source; " + AuthoringTools.KAMELET_GUIDE);
        } else if (template instanceof MappingNode tm) {
            Node from = value(tm, "from");
            if (from == null && value(tm, "route") instanceof MappingNode route) {
                from = value(route, "from");
            }
            String type = null;
            if (value(doc, "metadata") instanceof MappingNode meta && value(meta, "labels") instanceof MappingNode labels) {
                type = scalar(value(labels, "camel.apache.org/kamelet.type"));
            }
            if (value(tm, "steps") != null) {
                errors.add(EndpointChecks.linePrefix(tm.getStartMark().getLine()) + "spec.template.steps: the steps go"
                           + " under from:, template: {from: {uri: kamelet:source, steps: [...]}}");
            }
            if (from instanceof MappingNode fm0 && scalar(value(fm0, "uri")) != null
                    && scalar(value(fm0, "uri")).startsWith("kamelet:")
                    && !scalar(value(fm0, "uri")).startsWith("kamelet:source")) {
                // from: kamelet:<itself> instantiates the Kamelet from within itself, over and over
                errors.add(EndpointChecks.linePrefix(value(fm0, "uri").getStartMark().getLine()) + "the template starts"
                           + " from " + scalar(value(fm0, "uri")) + ": a Kamelet's template is entered from kamelet:source,"
                           + " the message the route sends to the Kamelet");
            } else if (from == null) {
                errors.add(EndpointChecks.linePrefix(tm.getStartMark().getLine()) + "spec.template has no from: the"
                           + " route starts with from: {uri: kamelet:source, steps: [...]} for an action or a sink");
            } else if (("action".equals(type) || "sink".equals(type)) && from instanceof MappingNode fm) {
                String uri = scalar(value(fm, "uri"));
                if (uri != null && !uri.startsWith("kamelet:")) {
                    errors.add(EndpointChecks.linePrefix(value(fm, "uri").getStartMark().getLine()) + "an " + type
                               + " Kamelet starts from: {uri: kamelet:source}, the message it is sent; " + uri
                               + " is the from: of a source");
                }
            }
        }
        return errors;
    }

    /** The scheme of an endpoint uri written as a value: timer:orders, kamelet:source, https://... */
    private static final Pattern URI_SCHEME = Pattern.compile("^([a-zA-Z][a-zA-Z0-9+.-]*):");

    /**
     * The camel: dependencies of a Kamelet file that name a component its template does not use: no endpoint of the
     * template has its scheme, and no language or data format of the template is in its artifact. Notes, not errors:
     * the Kamelet works, but an exported project gets the artifact for nothing. A model copies them from a sample whose
     * template starts from a timer, or from the route that uses the Kamelet (CAMEL-25403). Nothing is said when the
     * template could use a component in a way not visible here: a bean, a class reference or a placeholder as a scheme.
     */
    public static List<String> unusedDependencies(String content) {
        List<String> notes = new ArrayList<>();
        Node root;
        try {
            root = new Yaml(new SafeConstructor(new LoaderOptions())).compose(new StringReader(content));
        } catch (Exception e) {
            return notes;
        }
        if (!(root instanceof MappingNode doc) || !"Kamelet".equals(scalar(value(doc, "kind")))
                || !(value(doc, "spec") instanceof MappingNode spec)
                || !(value(spec, "dependencies") instanceof SequenceNode deps)
                || !(value(spec, "template") instanceof MappingNode template)) {
            return notes;
        }
        Set<String> used = new HashSet<>();
        if (!collectUsed(null, template, used)) {
            return notes;
        }
        CamelCatalog catalog = catalog();
        Set<String> usedArtifacts = new HashSet<>();
        for (String name : used) {
            String artifact = artifactOf(catalog, name);
            if (artifact == null) {
                // https: is a scheme of the http component, not a component of its own
                artifact = alternativeSchemes(catalog).get(name);
            }
            if (artifact != null) {
                usedArtifacts.add(artifact);
            }
        }
        for (Node d : deps.getValue()) {
            String dep = scalar(d);
            if (dep == null || !dep.startsWith("camel:")) {
                continue;
            }
            String name = dep.substring("camel:".length());
            ComponentModel model = catalog.componentModel(name);
            // only a component is checked: a language or a data format may be used in ways the template does not show
            if (model == null || catalog.languageModel(name) != null || catalog.dataFormatModel(name) != null
                    || usedArtifacts.contains(model.getArtifactId()) || delegatedTo(used, name)) {
                continue;
            }
            notes.add(EndpointChecks.linePrefix(line(d)) + "spec.dependencies: " + dep + " is not used by the template"
                      + " (it has no " + name + ": endpoint): list only the components, languages and data formats the"
                      + " template uses");
        }
        return notes;
    }

    /** Components that run on another one the template does not name: cron on quartz, rest-openapi on an http one. */
    private static final Map<String, Pattern> DELEGATES = Map.of(
            "cron", Pattern.compile("quartz|spring.*"),
            "rest", Pattern.compile(".*http.*|undertow|jetty"),
            "rest-openapi", Pattern.compile(".*http.*|undertow|jetty"));

    private static boolean delegatedTo(Set<String> used, String component) {
        for (Map.Entry<String, Pattern> e : DELEGATES.entrySet()) {
            if (used.contains(e.getKey()) && e.getValue().matcher(component).matches()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds the keys and the uri schemes of a template to the set; false when the template may use a component in a way
     * the keys and schemes do not show.
     */
    private static boolean collectUsed(String key, Node node, Set<String> used) {
        if (node instanceof MappingNode map) {
            for (NodeTuple t : map.getValue()) {
                String name = scalar(t.getKeyNode());
                if ("beans".equals(name) || "bean".equals(name) || "method".equals(name)) {
                    // a class of the Kamelet's own may need the artifact (the Kafka transform actions need camel:kafka)
                    return false;
                }
                if (name != null) {
                    used.add(name);
                }
                if (!collectUsed(name, t.getValueNode(), used)) {
                    return false;
                }
            }
        } else if (node instanceof SequenceNode seq) {
            for (Node n : seq.getValue()) {
                if (!collectUsed(key, n, used)) {
                    return false;
                }
            }
        } else if (node instanceof ScalarNode s) {
            String v = s.getValue().trim();
            if (v.contains("#class:") || v.contains("#type:")
                    || v.startsWith("{{") && ("uri".equals(key) || ENDPOINT_KEYS.contains(key))) {
                // a bean of a class, or an endpoint whose scheme is a property
                return false;
            }
            Matcher m = URI_SCHEME.matcher(v);
            if (m.find()) {
                used.add(m.group(1));
            } else if ("uri".equals(key) || ENDPOINT_KEYS.contains(key)) {
                // uri: aws2-sqs with the options under parameters:, as the canonical form writes it
                used.add(v.contains("?") ? v.substring(0, v.indexOf('?')) : v);
            }
        }
        return true;
    }

    private static volatile Map<String, String> alternativeSchemes;

    /** The alternative schemes of the components (https of http), each with the artifact of its component. */
    private static Map<String, String> alternativeSchemes(CamelCatalog catalog) {
        Map<String, String> answer = alternativeSchemes;
        if (answer == null) {
            answer = new HashMap<>();
            for (String n : catalog.findComponentNames()) {
                ComponentModel model = catalog.componentModel(n);
                if (model != null && model.getAlternativeSchemes() != null) {
                    for (String scheme : model.getAlternativeSchemes().split(",")) {
                        answer.putIfAbsent(scheme.trim(), model.getArtifactId());
                    }
                }
            }
            alternativeSchemes = answer;
        }
        return answer;
    }

    private static CamelCatalog catalog() {
        CamelCatalog catalog = dependencyCatalog;
        if (catalog == null) {
            catalog = new DefaultCamelCatalog();
            dependencyCatalog = catalog;
        }
        return catalog;
    }

    private static volatile CamelCatalog dependencyCatalog;

    /**
     * What is wrong with camel:name as a dependency, or null: it names the artifact without camel- (camel:jq for
     * camel-jq), which the runtime downloads. A language of camel core such as simple has no artifact of its own, so
     * camel:simple failed to download on every reload.
     */
    static String camelDependency(String name) {
        if (name.isBlank() || "core".equals(name) || "kamelet".equals(name)) {
            return null;
        }
        CamelCatalog catalog = dependencyCatalog;
        if (catalog == null) {
            catalog = new DefaultCamelCatalog();
            dependencyCatalog = catalog;
        }
        try {
            String artifact = artifactOf(catalog, name);
            if (artifact == null) {
                return isArtifact(catalog, "camel-" + name)
                        ? null
                        : "is not a Camel artifact: a dependency is camel:<artifact without camel->, such as camel:jq";
            }
            if (artifact.equals("camel-" + name)) {
                return null;
            }
            if (artifact.startsWith("camel-core") || artifact.equals("camel-base") || artifact.equals("camel-support")) {
                return "is part of camel core (" + artifact + "): leave it out, as the runtime has it; camel:" + name
                       + " is downloaded as an artifact camel-" + name + ", which does not exist";
            }
            return isArtifact(catalog, "camel-" + name)
                    ? null
                    : "is in " + artifact + ": write camel:" + artifact.substring("camel-".length());
        } catch (Exception e) {
            return null;
        }
    }

    /** The artifact of the component, language, data format or other of the given name, or null. */
    private static String artifactOf(CamelCatalog catalog, String name) {
        ArtifactModel<?> model = catalog.componentModel(name);
        if (model == null) {
            model = catalog.languageModel(name);
        }
        if (model == null) {
            model = catalog.dataFormatModel(name);
        }
        if (model == null) {
            model = catalog.otherModel(name);
        }
        return model != null ? model.getArtifactId() : null;
    }

    /** Whether some component, data format, language or other of the catalog is in the given artifact. */
    private static boolean isArtifact(CamelCatalog catalog, String artifactId) {
        for (String n : catalog.findComponentNames()) {
            if (artifactId.equals(artifactOf(catalog, n))) {
                return true;
            }
        }
        for (String n : catalog.findDataFormatNames()) {
            ArtifactModel<?> m = catalog.dataFormatModel(n);
            if (m != null && artifactId.equals(m.getArtifactId())) {
                return true;
            }
        }
        for (String n : catalog.findLanguageNames()) {
            ArtifactModel<?> m = catalog.languageModel(n);
            if (m != null && artifactId.equals(m.getArtifactId())) {
                return true;
            }
        }
        for (String n : catalog.findOtherNames()) {
            ArtifactModel<?> m = catalog.otherModel(n);
            if (m != null && artifactId.equals(m.getArtifactId())) {
                return true;
            }
        }
        return false;
    }

    private static Node value(MappingNode map, String key) {
        for (NodeTuple t : map.getValue()) {
            if (key.equals(scalar(t.getKeyNode()))) {
                return t.getValueNode();
            }
        }
        return null;
    }

    private static String scalar(Node node) {
        return node instanceof ScalarNode s ? s.getValue() : null;
    }

    private static int line(Node node) {
        return node.getStartMark().getLine();
    }

    private static final Pattern UNKNOWN_FUNCTION
            = Pattern.compile(
                    "Unknown function: (properties[.:]|property[.:]|header\\.|exchangeProperty\\.|variable\\.)?([\\w-]+)");

    /**
     * The messages of a Kamelet file with what a model gets wrong in its template: a property of the Kamelet written as
     * a Simple function (${properties.tag}, ${header.tag}) where the template has it as the placeholder {{tag}}.
     */
    public static List<String> withTemplateHints(String fileName, String content, List<String> msgs) {
        if (msgs.isEmpty() || fileName == null || !fileName.endsWith(".kamelet.yaml")) {
            return msgs;
        }
        KameletDefinitions.Definition def = KameletDefinitions.parse(content, fileName, fileName);
        List<String> answer = new ArrayList<>();
        for (String m : msgs) {
            Matcher matcher = UNKNOWN_FUNCTION.matcher(m);
            // properties.tag is a property whether the file declares it yet or not; a bare tag only when declared
            if (matcher.find() && (matcher.group(1) != null
                    || def != null && def.property(matcher.group(2)) != null)) {
                String p = matcher.group(2);
                m = m + " (in a Kamelet's template its property " + p + " is the placeholder {{" + p
                    + "}}, as in simple: \"${body} {{" + p + "}}\"; it is not a header or an exchange property)";
            }
            answer.add(m);
        }
        return answer;
    }

    /**
     * Whether application properties set the property of the Kamelet: camel.kamelet.name.prop (or name.routeId.prop),
     * or through the kamelet component, camel.component.kamelet.template-properties[name].prop (or route-properties of
     * a route of it).
     */
    static boolean inProperties(Map<String, String> properties, String kamelet, String property) {
        String prefix = "camel.kamelet." + kamelet + ".";
        for (String key : properties.keySet()) {
            if (key.startsWith(prefix) && (key.equals(prefix + property) || key.endsWith("." + property))) {
                return true;
            }
            String k = key.replace("templateProperties", "template-properties").replace("routeProperties",
                    "route-properties");
            if (k.startsWith("camel.component.kamelet.template-properties[" + kamelet + "].")
                    && k.endsWith("]." + property)) {
                return true;
            }
            if (k.startsWith("camel.component.kamelet.route-properties[") && k.endsWith("]." + property)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, String> projectProperties(Path directory) {
        Map<String, String> answer = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(p -> p.getFileName().toString().endsWith(".properties")).forEach(p -> {
                // as java.util.Properties reads them: key=value, key: value, key value
                Properties props = new Properties();
                try (Reader r = Files.newBufferedReader(p)) {
                    props.load(r);
                } catch (IOException e) {
                    // an unreadable file sets nothing
                }
                for (String key : props.stringPropertyNames()) {
                    if (key.startsWith("camel.kamelet.") || key.startsWith("camel.component.kamelet.")) {
                        answer.put(key, props.getProperty(key));
                    }
                }
            });
        } catch (IOException e) {
            // no directory listing: no properties
        }
        return answer;
    }
}
