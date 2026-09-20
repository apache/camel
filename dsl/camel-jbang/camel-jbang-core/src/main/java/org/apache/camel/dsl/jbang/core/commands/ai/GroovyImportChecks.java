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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.camel.dsl.jbang.core.commands.ai.YamlLines.countLeadingSpaces;

/**
 * Checks on the imports of the Groovy expressions of a YAML route (CAMEL-24843). A Groovy script compiles against the
 * application's classpath: an import of a library the application does not declare fails at runtime with "unable to
 * resolve class", which names the class and nothing else. The check names the library when it is a known one and says
 * how to declare it for the runtime; with the Camel CLI a known library is downloaded when the script is compiled, so
 * only an unknown one is reported there.
 */
public final class GroovyImportChecks {

    private static final Pattern IMPORT
            = Pattern.compile("^\\s*import\\s+(static\\s+)?([a-zA-Z][.\\w]*(?:\\.\\*)?)\\s*;?\\s*$");
    /** Packages every application has: the JDK, Groovy and Camel itself. */
    private static final List<String> ALWAYS_PRESENT
            = List.of("java.", "javax.", "jakarta.", "groovy.", "org.codehaus.groovy.", "org.apache.camel.");

    private GroovyImportChecks() {
    }

    /**
     * @param  content      the YAML route file
     * @param  runtime      null or "jbang" for the Camel CLI (a known library is downloaded when the script is
     *                      compiled), "main", "spring-boot" or "quarkus" for a Maven project (the library must be
     *                      declared in the pom)
     * @param  projectTypes the classes the project declares itself (simple name to fully qualified name), from the Java
     *                      and Groovy files next to the route
     * @return              the messages, one per import to act on, empty when there is nothing to say
     */
    public static List<String> validateYamlGroovyImports(String content, String runtime, Map<String, String> projectTypes) {
        List<String> errors = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return errors;
        }
        boolean maven = runtime != null && !runtime.isBlank() && !"jbang".equalsIgnoreCase(runtime);
        Set<String> projectClasses = new LinkedHashSet<>(projectTypes != null ? projectTypes.values() : Set.of());
        String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            String key = trimmed.startsWith("- ") ? trimmed.substring(2) : trimmed;
            if (!key.startsWith("groovy:")) {
                continue;
            }
            // the script: a block scalar under groovy: |, the lines under groovy: / expression: |, or the inline value
            int indent = countLeadingSpaces(lines[i]);
            List<int[]> scriptLines = new ArrayList<>();
            String value = key.substring("groovy:".length()).trim();
            if (value.isEmpty() || value.startsWith("|") || value.startsWith(">")) {
                for (int j = i + 1; j < lines.length; j++) {
                    if (lines[j].isBlank()) {
                        continue;
                    }
                    if (countLeadingSpaces(lines[j]) <= indent) {
                        break;
                    }
                    scriptLines.add(new int[] { j });
                }
            } else {
                scriptLines.add(new int[] { i });
            }
            for (int[] sl : scriptLines) {
                String line = lines[sl[0]].trim();
                if (line.startsWith("expression:")) {
                    // the canonical form: groovy: on its own line, expression: | below it, the script under that;
                    // the expression: line is collected with the script lines and is not one of them
                    continue;
                }
                Matcher m = IMPORT.matcher(line);
                if (!m.find()) {
                    continue;
                }
                String cls = m.group(2);
                if (m.group(1) != null && cls.contains(".")) {
                    cls = cls.substring(0, cls.lastIndexOf('.'));
                }
                if (cls.endsWith(".*")) {
                    cls = cls.substring(0, cls.length() - 2);
                }
                final String fqcn = cls;
                if (ALWAYS_PRESENT.stream().anyMatch(fqcn::startsWith) || projectClasses.contains(fqcn)
                        || projectClasses.stream().anyMatch(c -> c.endsWith("." + fqcn))) {
                    continue;
                }
                String known = BeanRefChecks.knownDependency(fqcn);
                int lineNum = sl[0] + 1;
                if (known != null && known.startsWith("camel:")) {
                    continue;
                }
                if (known != null) {
                    if (maven) {
                        String[] gav = known.split(":");
                        errors.add("Line " + lineNum + ": the Groovy import " + fqcn + " is a class of " + known
                                   + ": the application must declare it as a dependency in the pom (<dependency>"
                                   + "<groupId>" + gav[0] + "</groupId><artifactId>" + gav[1] + "</artifactId>"
                                   + (gav.length > 2 && !gav[2].startsWith("${") ? "<version>" + gav[2] + "</version>" : "")
                                   + "</dependency>)");
                    }
                    // the Camel CLI downloads a known library when the script is compiled
                    continue;
                }
                errors.add("Line " + lineNum + ": the Groovy import " + fqcn + " is not a class of the JDK, Groovy, Camel"
                           + " or this project, and not a known library: declare the library that provides it as a"
                           + " dependency (with the Camel CLI camel.jbang.dependencies=groupId:artifactId:version in"
                           + " application.properties or a //DEPS line; in a Maven project a pom dependency), or put"
                           + " the class in a .groovy or .java file next to the route");
            }
        }
        return errors;
    }
}
