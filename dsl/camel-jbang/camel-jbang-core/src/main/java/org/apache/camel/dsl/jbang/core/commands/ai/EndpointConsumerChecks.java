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
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.dsl.yaml.validator.EndpointConsumers;
import org.apache.camel.java.in.JavaParseResult;
import org.apache.camel.java.in.LwJavaParser;
import org.apache.camel.model.RouteDefinition;

/**
 * A {@code direct:} or {@code seda:} endpoint a YAML route sends to, and no route of the application consumes
 * (CAMEL-24955). The routes of an application are spread over the files of its directory, so the endpoints the other
 * route files consume - YAML, Java and XML - are read from them and handed to the check.
 */
public final class EndpointConsumerChecks {

    /** A Java DSL route input: from( not called on something else, so not Instant.from( or List.from(. */
    private static final Pattern JAVA_FROM = Pattern.compile("(?<![.\\w])from\\s*\\(");
    /** The build files of a project whose routes are spread over src/main/java and src/main/resources. */
    private static final List<String> BUILD_FILES = List.of("pom.xml", "build.gradle", "build.gradle.kts");
    private static final Pattern XML_FROM = Pattern.compile("<from\\s[^>]*?\\buri\\s*=\\s*[\"']([^\"']*)[\"']");

    private EndpointConsumerChecks() {
    }

    /**
     * @param  content     the YAML route file
     * @param  directory   the directory of the application's route files; null says nothing
     * @param  excludeFile the file being validated, whose routes come from the content
     * @return             the messages, one per endpoint no route consumes
     */
    public static List<String> validateYamlConsumers(String content, Path directory, String excludeFile) {
        return validateYamlConsumers(content, directory, excludeFile, null);
    }

    /**
     * @param catalog for the endpoint DSL of Java routes, may be null
     */
    public static List<String> validateYamlConsumers(
            String content, Path directory, String excludeFile, CamelCatalog catalog) {
        return EndpointConsumers.check(content, consumed(directory, excludeFile, catalog));
    }

    /**
     * The {@code direct:} and {@code seda:} endpoints the route files under the directory consume, leaving out the file
     * being validated; null when they cannot be known: no directory, a directory that is only a part of a Maven or
     * Gradle project, more files than the scan looks at, or a route input the scan cannot read (a Java {@code from(}
     * with no literal, a placeholder, a route template), which could be any endpoint.
     */
    static Set<String> consumed(Path directory, String excludeFile, CamelCatalog catalog) {
        if (directory == null || !Files.isDirectory(directory)) {
            return null;
        }
        Path root = directory.toAbsolutePath().normalize();
        if (isPartOfAProject(root)) {
            return null;
        }
        List<Path> files = routeFiles(root, excludeFile != null ? root.resolve(excludeFile).normalize() : null);
        if (files == null) {
            return null;
        }
        Set<String> answer = new HashSet<>();
        try {
            for (Path p : files) {
                String lower = p.getFileName().toString().toLowerCase(Locale.ROOT);
                Set<String> found;
                if (lower.endsWith(".yaml") || lower.endsWith(".yml")) {
                    found = EndpointConsumers.consumed(Files.readString(p));
                } else if (lower.endsWith(".java")) {
                    found = javaConsumed(Files.readString(p), catalog);
                } else {
                    found = xmlConsumed(Files.readString(p));
                }
                if (found == null) {
                    return null;
                }
                answer.addAll(found);
            }
        } catch (IOException e) {
            // an unreadable file: what it consumes is not known
            return null;
        }
        return answer;
    }

    /**
     * Whether the directory is inside a Maven or Gradle project without being its root, as the directory of
     * src/main/resources/camel is: the RouteBuilders under src/main/java are not under it.
     */
    private static boolean isPartOfAProject(Path root) {
        if (BUILD_FILES.stream().anyMatch(f -> Files.isRegularFile(root.resolve(f)))) {
            return false;
        }
        for (Path p = root.getParent(); p != null; p = p.getParent()) {
            Path dir = p;
            if (BUILD_FILES.stream().anyMatch(f -> Files.isRegularFile(dir.resolve(f)))) {
                return true;
            }
        }
        return false;
    }

    /**
     * The YAML, Java and XML files under the directory, the way the file tools look at a project: build and tooling
     * directories skipped. Null when the scan does not see them all: a directory deeper than it goes, more files than
     * it looks at, or one it cannot read.
     */
    private static List<Path> routeFiles(Path root, Path exclude) {
        List<Path> files = new ArrayList<>();
        boolean[] partial = { false };
        try {
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), AuthoringTools.MAX_DEPTH,
                    new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes attrs) {
                            return d.equals(root) || !isSkipped(d)
                                    ? FileVisitResult.CONTINUE : FileVisitResult.SKIP_SUBTREE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path f, BasicFileAttributes attrs) {
                            if (attrs.isDirectory()) {
                                // a directory at the depth limit: its files are not seen
                                partial[0] = !isSkipped(f);
                            } else if (attrs.isRegularFile() && !f.equals(exclude) && isRouteFile(f)) {
                                files.add(f);
                                partial[0] = files.size() >= AuthoringTools.SCAN_LIMIT;
                            }
                            return partial[0] ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFileFailed(Path f, IOException e) {
                            partial[0] = true;
                            return FileVisitResult.TERMINATE;
                        }
                    });
        } catch (IOException e) {
            return null;
        }
        return partial[0] ? null : files;
    }

    private static boolean isSkipped(Path dir) {
        String name = dir.getFileName().toString();
        return AuthoringTools.SKIPPED_DIRS.contains(name) || name.startsWith(".");
    }

    private static boolean isRouteFile(Path f) {
        String fn = f.getFileName().toString();
        String lower = fn.toLowerCase(Locale.ROOT);
        return !fn.startsWith(".")
                && (lower.endsWith(".yaml") || lower.endsWith(".yml") || lower.endsWith(".java") || lower.endsWith(".xml"));
    }

    /**
     * The endpoints the routes of a Java source consume, read into the model by the Java DSL parser (CAMEL-25199), so
     * constants, String.format and the endpoint DSL are resolved; null when they cannot be known: a route template,
     * whose input is a parameter, an input known only at runtime, or a from( the parser could not read.
     */
    static Set<String> javaConsumed(String src, CamelCatalog catalog) {
        JavaParseResult result = JavaRouteReader.parse(src, catalog, null);
        if (!result.routeTemplates().getRouteTemplates().isEmpty()) {
            return null;
        }
        List<RouteDefinition> routes = result.routes().getRoutes();
        if (routes.isEmpty() && JAVA_FROM.matcher(src).find()) {
            // a from( the parser did not read as a route: what it consumes is not known
            return null;
        }
        Set<String> answer = new HashSet<>();
        for (RouteDefinition r : routes) {
            String uri = r.getInput() != null ? r.getInput().getUri() : null;
            if (uri == null || uri.contains(LwJavaParser.UNRESOLVED_PREFIX) || !add(uri, answer)) {
                return null;
            }
        }
        return answer;
    }

    static Set<String> xmlConsumed(String src) {
        Set<String> answer = new HashSet<>();
        Matcher m = XML_FROM.matcher(src);
        while (m.find()) {
            if (!add(m.group(1), answer)) {
                return null;
            }
        }
        return answer;
    }

    /** Adds the endpoint when it is a direct: or seda: one; false when it is only known at runtime. */
    private static boolean add(String uri, Set<String> answer) {
        if (EndpointConsumers.isDynamic(uri)) {
            return false;
        }
        String endpoint = EndpointConsumers.endpoint(uri);
        if (endpoint != null) {
            answer.add(endpoint);
        }
        return true;
    }
}
