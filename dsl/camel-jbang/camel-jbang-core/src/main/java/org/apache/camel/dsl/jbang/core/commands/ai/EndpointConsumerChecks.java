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

import org.apache.camel.dsl.yaml.validator.EndpointConsumers;

/**
 * A {@code direct:} or {@code seda:} endpoint a YAML route sends to, and no route of the application consumes
 * (CAMEL-24955). The routes of an application are spread over the files of its directory, so the endpoints the other
 * route files consume - YAML, Java and XML - are read from them and handed to the check.
 */
public final class EndpointConsumerChecks {

    /**
     * A Java DSL route input: from( not called on something else, so not Instant.from( or List.from(; its endpoint is
     * read only when a string literal is the whole argument, not the start of from("direct:" + NAME).
     */
    private static final Pattern JAVA_FROM = Pattern.compile("(?<![.\\w])from\\s*\\(\\s*(\"([^\"]*)\"\\s*(?=[),]))?");
    /** Route inputs the scan cannot read: a route template's from( and fromF( with a format. */
    private static final Pattern JAVA_UNREADABLE = Pattern.compile("\\b(routeTemplate|fromF)\\s*\\(");
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
        return EndpointConsumers.check(content, consumed(directory, excludeFile));
    }

    /**
     * The {@code direct:} and {@code seda:} endpoints the route files under the directory consume, leaving out the file
     * being validated; null when they cannot be known: no directory, a directory that is only a part of a Maven or
     * Gradle project, more files than the scan looks at, or a route input the scan cannot read (a Java {@code from(}
     * with no literal, a placeholder, a route template), which could be any endpoint.
     */
    static Set<String> consumed(Path directory, String excludeFile) {
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
                    found = javaConsumed(Files.readString(p));
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

    static Set<String> javaConsumed(String src) {
        if (JAVA_UNREADABLE.matcher(src).find()) {
            return null;
        }
        Set<String> answer = new HashSet<>();
        Matcher m = JAVA_FROM.matcher(src);
        while (m.find()) {
            // from(someConstant) or from(direct("x")): the endpoint is not in the source as a literal
            if (m.group(2) == null || !add(m.group(2), answer)) {
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
