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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.commands.catalog.KameletCatalogHelper;
import org.apache.camel.dsl.jbang.core.commands.catalog.KameletModel;
import org.apache.camel.dsl.jbang.core.commands.catalog.KameletOptionModel;
import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * The Kamelets an agent can use: those of the Kamelet catalog and the project's own {@code <name>.kamelet.yaml} files,
 * with their properties. The catalog tools and the validator read them, so that a Kamelet is looked up and checked as a
 * component is: a model knows the components from years of examples, but writes a Kamelet's properties from the
 * component's options (brokers on kafka-sink) or uses one that is gone (kafka-not-secured-source).
 */
public final class KameletDefinitions {

    /** A Kamelet and its properties. */
    public record Definition(String name, String type, String title, String description, String source,
            List<Property> properties) {

        public Property property(String name) {
            for (Property p : properties) {
                if (p.name().equals(name)) {
                    return p;
                }
            }
            return null;
        }
    }

    /** A property of a Kamelet. */
    public record Property(String name, boolean required, String type, String defaultValue, String description,
            List<String> enumValues) {
    }

    private static final Map<String, Map<String, Definition>> CATALOGS = new ConcurrentHashMap<>();
    private static volatile Map<String, Definition> testCatalog;

    private KameletDefinitions() {
    }

    /** The Kamelet of the project directory, else of the catalog, or null. */
    public static Definition find(String name, Path directory) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Definition def = directory != null ? projectKamelets(directory).get(name) : null;
        return def != null ? def : catalog().get(name);
    }

    /** The Kamelets of the catalog by name; empty when the catalog cannot be read (offline, say). */
    public static Map<String, Definition> catalog() {
        Map<String, Definition> fixed = testCatalog;
        if (fixed != null) {
            return fixed;
        }
        String version;
        try {
            version = VersionHelper.extractKameletsVersion();
        } catch (Exception e) {
            version = null;
        }
        if (version == null) {
            return Map.of();
        }
        return CATALOGS.computeIfAbsent(version, KameletDefinitions::loadCatalog);
    }

    /** The Kamelets the catalog has instead of the real catalog, for tests; null for the real one. */
    static void setTestCatalog(Map<String, Definition> kamelets) {
        testCatalog = kamelets;
    }

    private static Map<String, Definition> loadCatalog(String version) {
        Map<String, Definition> answer = new LinkedHashMap<>();
        try {
            for (Object o : KameletCatalogHelper.loadKamelets(version, null).values()) {
                KameletModel km = KameletCatalogHelper.createModel(o, true);
                List<Property> props = new ArrayList<>();
                if (km.properties != null) {
                    for (KameletOptionModel om : km.properties.values()) {
                        props.add(new Property(
                                om.name, om.required, om.type, om.defaultValue, om.description,
                                om.enumValues != null ? om.enumValues : List.of()));
                    }
                }
                answer.put(km.name, new Definition(
                        km.name, km.type, null, km.description,
                        "the Kamelet catalog " + version, props));
            }
        } catch (Exception e) {
            // no catalog: the tools say nothing about Kamelets rather than the wrong thing
        }
        return answer;
    }

    /** The project's own Kamelets: the *.kamelet.yaml files of the directory and its subdirectories. */
    public static Map<String, Definition> projectKamelets(Path directory) {
        Map<String, Definition> answer = new LinkedHashMap<>();
        if (directory == null || !Files.isDirectory(directory)) {
            return answer;
        }
        try (Stream<Path> files = Files.walk(directory, 4)) {
            files.filter(p -> p.getFileName().toString().endsWith(".kamelet.yaml"))
                    .filter(p -> !hidden(directory.relativize(p)))
                    .sorted()
                    .forEach(p -> {
                        Definition def = parse(p);
                        if (def != null) {
                            answer.putIfAbsent(def.name(), def);
                        }
                    });
        } catch (IOException e) {
            // an unreadable directory has no Kamelets
        }
        return answer;
    }

    private static boolean hidden(Path relative) {
        for (Path part : relative) {
            String s = part.toString();
            if (s.startsWith(".") || "target".equals(s) || "node_modules".equals(s)) {
                return true;
            }
        }
        return false;
    }

    /** A Kamelet file as a definition, or null when it is not one (yet): a half-written file has no Kamelet. */
    static Definition parse(Path file) {
        String fileName = file.getFileName().toString();
        String fallback = fileName.substring(0, fileName.length() - ".kamelet.yaml".length());
        try {
            return parse(Files.readString(file), fallback, fileName);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    static Definition parse(String content, String fallbackName, String fileName) {
        Object root;
        try {
            root = new Yaml(new SafeConstructor(new LoaderOptions())).load(content);
        } catch (Exception e) {
            return null;
        }
        if (!(root instanceof Map<?, ?> map) || !"Kamelet".equals(map.get("kind"))) {
            return null;
        }
        String name = fallbackName;
        String type = null;
        if (map.get("metadata") instanceof Map<?, ?> meta) {
            if (meta.get("name") instanceof String n && !n.isBlank()) {
                name = n;
            }
            if (meta.get("labels") instanceof Map<?, ?> labels
                    && labels.get("camel.apache.org/kamelet.type") instanceof String t) {
                type = t;
            }
        }
        String title = null;
        String description = null;
        List<Property> props = new ArrayList<>();
        if (map.get("spec") instanceof Map<?, ?> spec && spec.get("definition") instanceof Map<?, ?> def) {
            title = def.get("title") instanceof String t ? t : null;
            description = def.get("description") instanceof String d ? d : null;
            Set<String> required = new HashSet<>();
            if (def.get("required") instanceof List<?> req) {
                req.forEach(r -> required.add(String.valueOf(r)));
            }
            if (def.get("properties") instanceof Map<?, ?> properties) {
                for (Map.Entry<?, ?> e : properties.entrySet()) {
                    String pname = String.valueOf(e.getKey());
                    Map<String, Object> p = e.getValue() instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
                    Object dv = p.get("default");
                    List<String> enums = new ArrayList<>();
                    if (p.get("enum") instanceof List<?> en) {
                        en.forEach(v -> enums.add(String.valueOf(v)));
                    }
                    props.add(new Property(
                            pname, required.contains(pname),
                            p.get("type") != null ? String.valueOf(p.get("type")) : null,
                            dv != null ? String.valueOf(dv) : null,
                            p.get("description") != null ? String.valueOf(p.get("description")) : null, enums));
                }
            }
        }
        return new Definition(name, type, title, description, "the project file " + fileName, props);
    }

    /**
     * The Kamelets whose name is close to the given one: those sharing its words (kafka-not-secured-source has kafka
     * and source, so kafka-source), then those a few edits away. Best first.
     */
    public static List<String> suggest(String name, Collection<String> names, int limit) {
        if (name == null || name.isBlank()) {
            return List.of();
        }
        String lower = name.toLowerCase(Locale.ROOT);
        // a name may repeat a word (aws-s3-to-aws-sqs): Set.of would refuse it
        Set<String> words = new HashSet<>(Arrays.asList(lower.split("[-_.]")));
        record Scored(String name, int score) {
        }
        List<Scored> scored = new ArrayList<>();
        for (String candidate : names) {
            String c = candidate.toLowerCase(Locale.ROOT);
            String[] cw = c.split("-");
            int shared = 0;
            for (String w : cw) {
                if (words.contains(w)) {
                    shared++;
                }
            }
            int distance = distance(lower, c);
            // all words of the candidate in the name, or one edit apart for a short name
            if (shared == cw.length && shared >= 2) {
                scored.add(new Scored(candidate, 100 + shared * 10 - distance));
            } else if (distance <= Math.max(2, lower.length() / 4)) {
                scored.add(new Scored(candidate, 50 - distance));
            } else if (shared >= 2 && shared * 2 >= cw.length) {
                scored.add(new Scored(candidate, shared * 10 - distance));
            }
        }
        return scored.stream()
                .sorted(Comparator.comparingInt(Scored::score).reversed().thenComparing(Scored::name))
                .limit(limit).map(Scored::name).toList();
    }

    /** The property names close to the given one: same name in another case or form, or a few edits away. */
    public static List<String> suggestProperty(String name, Definition def) {
        String lower = name.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
        List<String> answer = new ArrayList<>();
        for (Property p : def.properties()) {
            String c = p.name().toLowerCase(Locale.ROOT);
            if (c.equals(lower) || distance(lower, c) <= Math.max(2, c.length() / 5) || c.contains(lower)
                    || (lower.length() >= 5 && lower.contains(c))) {
                answer.add(p.name());
            }
        }
        return answer;
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

    /** The properties in one line each: name (required), type, default; for a message that lists them. */
    public static String propertyList(Definition def) {
        StringBuilder sb = new StringBuilder();
        for (Property p : def.properties()) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(p.name());
            if (p.required() && p.defaultValue() == null) {
                sb.append(" (required)");
            } else if (p.defaultValue() != null) {
                sb.append(" (default ").append(p.defaultValue()).append(")");
            }
        }
        return sb.toString();
    }
}
