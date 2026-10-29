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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.camel.dsl.yaml.validator.ProjectFacts;

/**
 * What the YAML validator cannot read from a route file, read from the files beside it (CAMEL-25762): the operations of
 * the OpenAPI specifications the file uses, and the REST binding mode the project's properties set. And from a running
 * integration of the file: the bodies the endpoints of its routes gave (CAMEL-25750).
 */
final class ValidatorFacts {

    /** The configuration files Camel reads, with their profiles: application.properties, application-dev.yaml... */
    private static final Pattern CONFIGURATION = Pattern.compile("application(-[\\w.-]+)?\\.(properties|ya?ml)");

    /** camel.rest.bindingMode = json, camel.rest.binding-mode: json, or binding-mode: json nested in YAML. */
    private static final Pattern BINDING_MODE = Pattern.compile(
            "(?i)(?:^|[.\\s])binding-?mode\\s*[=:]\\s*[\"']?([a-z_]+)");

    /** The binding modes that write a Map or a List for the route. */
    private static final Set<String> BINDS = Set.of("auto", "json", "xml", "json_xml");

    private ValidatorFacts() {
    }

    /**
     * The facts for a file in a directory, or nothing when there is no directory: with what a running integration of
     * the file saw at the endpoints of its routes (CAMEL-25750).
     */
    static ProjectFacts read(String fileName, String content, Path directory) {
        if (content == null) {
            return ProjectFacts.NONE;
        }
        Map<String, ProjectFacts.KameletDataTypes> kamelets = kameletDataTypes(content, directory);
        if (directory == null) {
            return new ProjectFacts(Set.of(), Map.of(), null, Map.of(), Map.of(), kamelets);
        }
        return new ProjectFacts(
                OpenApiVerbs.bodylessEndpoints(content, directory), OpenApiVerbs.operations(content, directory),
                restBindingMode(directory), OpenApiVerbs.requestFields(content, directory),
                ObservedBodies.read(fileName, directory), kamelets, jsonArrays(content, directory));
    }

    /** resource:file:orders.json or resource:classpath:data/orders.json in a route file. */
    private static final Pattern JSON_RESOURCE = Pattern.compile("resource:((?:file|classpath):[^\\s\"'}]+\\.json)");

    /** The JSON files beyond this size are not read: the facts are for the files of a small project. */
    private static final long MAX_JSON_FILE = 1024 * 1024;

    /**
     * The files a resource: of the routes loads whose JSON is an array (file:orders.json): unmarshalled, such a file is
     * a List, and ${body.orderId} on it fails at runtime with "a List has no method or key orderId". A file: resource
     * is read from the directory, where camel run runs; a classpath: one from the directory or src/main/resources.
     */
    static Set<String> jsonArrays(String content, Path directory) {
        Set<String> answer = new HashSet<>();
        Matcher m = JSON_RESOURCE.matcher(content);
        while (m.find()) {
            String resource = m.group(1);
            String file = resource.substring(resource.indexOf(':') + 1);
            List<Path> candidates = resource.startsWith("file:")
                    ? List.of(directory.resolve(file))
                    : List.of(directory.resolve(file), directory.resolve("src/main/resources").resolve(file));
            for (Path path : candidates) {
                if (isJsonArray(path)) {
                    answer.add(resource);
                    break;
                }
            }
        }
        return answer;
    }

    private static boolean isJsonArray(Path path) {
        try {
            if (!Files.isRegularFile(path) || Files.size(path) > MAX_JSON_FILE) {
                return false;
            }
            String text = Files.readString(path).strip();
            return text.startsWith("[");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * The data types of the Kamelets of the catalog and of the project that declare them (CAMEL-25928): the data type
     * transformers of their default in and out data types, which say the body a Kamelet takes and gives. Only read when
     * the file uses a Kamelet.
     */
    static Map<String, ProjectFacts.KameletDataTypes> kameletDataTypes(String content, Path directory) {
        if (!content.contains("kamelet")) {
            return Map.of();
        }
        Map<String, ProjectFacts.KameletDataTypes> answer = new HashMap<>();
        List<Map<String, KameletDefinitions.Definition>> sources = new ArrayList<>();
        sources.add(KameletDefinitions.catalog());
        if (directory != null) {
            // the project's own Kamelets win over those of the catalog
            sources.add(KameletDefinitions.projectKamelets(directory));
        }
        for (Map<String, KameletDefinitions.Definition> definitions : sources) {
            for (KameletDefinitions.Definition def : definitions.values()) {
                String in = def.defaultFormat("in");
                String out = def.defaultFormat("out");
                if (in != null || out != null) {
                    answer.put(def.name(), new ProjectFacts.KameletDataTypes(in, out));
                }
            }
        }
        return answer;
    }

    /**
     * The REST binding mode the configuration beside the route (or in src/main/resources) sets: off when it sets none.
     * A binding that writes data in any of the files, a profile among them, wins: a route that only some profiles bind
     * is not reported.
     */
    static String restBindingMode(Path directory) {
        String answer = "off";
        for (Path dir : List.of(directory, directory.resolve("src/main/resources"))) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            List<Path> files;
            try (Stream<Path> list = Files.list(dir)) {
                files = list.filter(f -> CONFIGURATION.matcher(f.getFileName().toString()).matches()).toList();
            } catch (IOException e) {
                continue;
            }
            for (Path file : files) {
                String text;
                try {
                    text = Files.readString(file);
                } catch (IOException e) {
                    continue;
                }
                for (String line : text.split("\n")) {
                    String l = line.strip();
                    String lower = l.toLowerCase(Locale.ROOT);
                    if (l.startsWith("#") || (!lower.contains("rest") && !lower.startsWith("binding"))) {
                        continue;
                    }
                    Matcher m = BINDING_MODE.matcher(l);
                    if (m.find()) {
                        String mode = m.group(1).toLowerCase(Locale.ROOT);
                        if (BINDS.contains(mode)) {
                            return mode;
                        }
                        answer = mode;
                    }
                }
            }
        }
        return answer;
    }
}
