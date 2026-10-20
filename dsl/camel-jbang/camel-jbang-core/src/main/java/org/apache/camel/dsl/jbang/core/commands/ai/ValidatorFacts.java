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
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.camel.dsl.yaml.validator.ProjectFacts;

/**
 * What the YAML validator cannot read from a route file, read from the files beside it (CAMEL-25762): the operations of
 * the OpenAPI specifications the file uses, and the REST binding mode the project's properties set.
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

    /** The facts for a file in a directory, or nothing when there is no directory. */
    static ProjectFacts read(String content, Path directory) {
        if (content == null || directory == null) {
            return ProjectFacts.NONE;
        }
        return new ProjectFacts(
                OpenApiVerbs.bodylessEndpoints(content, directory), OpenApiVerbs.operations(content, directory),
                restBindingMode(directory), OpenApiVerbs.requestFields(content, directory));
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
