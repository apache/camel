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
package org.apache.camel.maven.packaging;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.camel.tooling.model.DstWorldModel;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.JavaType;
import org.jboss.forge.roaster.model.JavaUnit;
import org.jboss.forge.roaster.model.source.AnnotationSource;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.JavaSource;
import org.jboss.forge.roaster.model.source.TypeHolderSource;

/**
 * Finds the fake worlds of the deterministic simulation tests of a component: the classes of its test sources annotated
 * with {@code org.apache.camel.test.dst.DstWorld} (CAMEL-26438).
 * <p>
 * The worlds are test classes, which are not compiled when the component JSON is generated (and not at all with
 * {@code -Dmaven.test.skip}), so the annotation is read from the source. Its values must be string literals,
 * concatenations of string literals or arrays of those.
 */
public final class DstWorldsHelper {

    static final String DST_WORLD = "org.apache.camel.test.dst.DstWorld";

    // the annotation used in a source, and the ways a source can refer to the one of camel-test-dst
    private static final Pattern ANNOTATED = Pattern.compile("@\\s*(org\\.apache\\.camel\\.test\\.dst\\.)?DstWorld\\b");
    private static final Pattern OURS = Pattern.compile(
            "org\\.apache\\.camel\\.test\\.dst\\.DstWorld\\b|import\\s+org\\.apache\\.camel\\.test\\.dst\\.\\*"
                                                        + "|package\\s+org\\.apache\\.camel\\.test\\.dst\\s*;");

    private DstWorldsHelper() {
    }

    /**
     * The worlds in the given test source roots, by the scheme of the component they name, each list sorted by the
     * simple name of the world class.
     *
     * @throws IllegalArgumentException if a file that uses the annotation cannot be parsed, an annotation is invalid or
     *                                  not on the top-level class of its file, or two worlds have the same simple name
     */
    public static Map<String, List<DstWorldModel>> findDstWorlds(List<Path> testSourceRoots) throws IOException {
        Map<String, List<DstWorldModel>> answer = new TreeMap<>();
        Map<String, String> names = new HashMap<>();
        for (Path root : testSourceRoots.stream().map(p -> p.toAbsolutePath().normalize()).distinct().toList()) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            List<Path> files;
            try (Stream<Path> walk = Files.walk(root)) {
                files = walk.filter(DstWorldsHelper::isJavaFile).sorted().toList();
            }
            for (Path file : files) {
                String text = read(file);
                // cheap checks first: most test classes are not worlds; the parsed types decide
                if (!text.contains("DstWorld") || !ANNOTATED.matcher(text).find() || !OURS.matcher(text).find()) {
                    continue;
                }
                AnnotationSource<JavaClassSource> annotation = annotation(text, file);
                if (annotation == null) {
                    continue;
                }
                String component = value(annotation, "component", file);
                DstWorldModel world = world(annotation, file);
                String other = names.put(world.getName(), world.getJavaType());
                if (other != null) {
                    throw new IllegalArgumentException(
                            "Two @DstWorld classes have the same simple name: " + other + " and " + world.getJavaType());
                }
                world.setTests(findTests(file, world.getName()));
                answer.computeIfAbsent(component, k -> new ArrayList<>()).add(world);
            }
        }
        answer.values().forEach(l -> l.sort(Comparator.comparing(DstWorldModel::getName)));
        return answer;
    }

    /** The world declared by the annotation on its class. */
    static DstWorldModel world(AnnotationSource<JavaClassSource> annotation, Path file) {
        JavaClassSource clazz = annotation.getOrigin();
        DstWorldModel world = new DstWorldModel();
        world.setName(clazz.getName());
        world.setJavaType(clazz.getQualifiedName());
        world.setFake(value(annotation, "fake", file));
        world.setCovers(values(annotation, "covers", file));
        world.setFaults(values(annotation, "faults", file));
        world.setInvariants(values(annotation, "invariants", file));
        world.setIssues(values(annotation, "issues", file));
        return world;
    }

    /**
     * The {@code @DstWorld} of the top-level class of a source, or null when no type of the source has it (the text
     * only mentions it, in a comment or a string). A world anywhere else (on a record, interface, enum, nested class or
     * second class of the file) would be left out of the catalog, so it fails.
     */
    private static AnnotationSource<JavaClassSource> annotation(String text, Path file) {
        JavaUnit unit;
        try {
            unit = Roaster.parseUnit(text);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Cannot parse " + file + " for @DstWorld: " + e.getMessage(), e);
        }
        List<JavaSource<?>> annotated = new ArrayList<>();
        for (JavaType<?> type : unit.getTopLevelTypes()) {
            if (type instanceof JavaSource<?> source) {
                collectAnnotated(source, annotated);
            }
        }
        if (annotated.isEmpty()) {
            return null;
        }
        JavaType<?> top = unit.getGoverningType();
        if (annotated.size() == 1 && annotated.get(0) instanceof JavaClassSource clazz
                && clazz.getQualifiedName().equals(top.getQualifiedName())) {
            for (AnnotationSource<JavaClassSource> a : clazz.getAnnotations()) {
                if (DST_WORLD.equals(a.getQualifiedName())) {
                    return a;
                }
            }
        }
        throw new IllegalArgumentException(
                "@DstWorld in " + file + " must be on the top-level class of the file (not on a record, interface, enum,"
                                           + " nested class or second class)");
    }

    private static void collectAnnotated(JavaSource<?> type, List<JavaSource<?>> annotated) {
        for (AnnotationSource<?> a : type.getAnnotations()) {
            if (DST_WORLD.equals(a.getQualifiedName())) {
                annotated.add(type);
                break;
            }
        }
        if (type instanceof TypeHolderSource<?> holder) {
            for (JavaSource<?> nested : holder.getNestedTypes()) {
                collectAnnotated(nested, annotated);
            }
        }
    }

    private static boolean isJavaFile(Path p) {
        return p.getFileName().toString().endsWith(".java") && Files.isRegularFile(p);
    }

    /** Read as the rest of the plugin reads sources: a test file in another encoding must not fail the build. */
    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    /** A required single value, which must not be blank. */
    private static String value(AnnotationSource<JavaClassSource> annotation, String member, Path file) {
        List<String> answer = values(annotation, member, file);
        if (answer.size() != 1 || answer.get(0).isBlank()) {
            throw new IllegalArgumentException("@DstWorld in " + file + " needs a non-empty " + member);
        }
        return answer.get(0);
    }

    private static List<String> values(AnnotationSource<JavaClassSource> annotation, String member, Path file) {
        String literal = annotation.getLiteralValue(member);
        if (literal == null) {
            return new ArrayList<>();
        }
        try {
            return parseStrings(literal);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "@DstWorld in " + file + ": " + member + " must be string literals (" + e.getMessage() + "): "
                                               + literal,
                    e);
        }
    }

    /**
     * The DST tests that run the world: the classes in the same package that have a {@code @DstTest} method and name
     * the world class.
     */
    private static List<String> findTests(Path world, String name) throws IOException {
        Pattern uses = Pattern.compile("\\b" + Pattern.quote(name) + "\\b");
        List<String> answer = new ArrayList<>();
        try (Stream<Path> list = Files.list(world.getParent())) {
            for (Path file : list.filter(DstWorldsHelper::isJavaFile).toList()) {
                String text = read(file);
                if (!file.equals(world) && text.contains("@DstTest") && uses.matcher(text).find()) {
                    String fileName = file.getFileName().toString();
                    answer.add(fileName.substring(0, fileName.length() - ".java".length()));
                }
            }
        }
        answer.sort(null);
        return answer;
    }

    /**
     * The strings of an annotation value as it is in the source: a string literal, string literals joined with
     * {@code +}, or an array of those in braces.
     *
     * @throws IllegalArgumentException for anything else (a constant, a text block, a method call)
     */
    static List<String> parseStrings(String source) {
        return new LiteralParser(source).parse();
    }

    private static final class LiteralParser {
        private final String text;
        private int pos;

        LiteralParser(String text) {
            this.text = text;
        }

        List<String> parse() {
            List<String> answer = new ArrayList<>();
            skipSpace();
            if (consume('{')) {
                skipSpace();
                while (!consume('}')) {
                    answer.add(concatenation());
                    skipSpace();
                    if (!consume(',') && !(pos < text.length() && text.charAt(pos) == '}')) {
                        throw error("',' or '}' expected");
                    }
                    skipSpace();
                }
            } else {
                answer.add(concatenation());
            }
            skipSpace();
            if (pos != text.length()) {
                throw error("unexpected text");
            }
            return answer;
        }

        private String concatenation() {
            StringBuilder sb = new StringBuilder();
            do {
                skipSpace();
                sb.append(literal());
                skipSpace();
            } while (consume('+'));
            return sb.toString();
        }

        private String literal() {
            if (!consume('"')) {
                throw error("a string literal expected");
            }
            if (text.startsWith("\"\"", pos)) {
                throw error("text blocks are not supported");
            }
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (pos >= text.length()) {
                    throw error("unterminated string literal");
                }
                char c = text.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                } else if (c == '\\') {
                    sb.append(escape());
                } else {
                    sb.append(c);
                }
            }
        }

        private char escape() {
            if (pos >= text.length()) {
                throw error("unterminated escape");
            }
            char c = text.charAt(pos++);
            return switch (c) {
                case 'b' -> '\b';
                case 't' -> '\t';
                case 'n' -> '\n';
                case 'f' -> '\f';
                case 'r' -> '\r';
                case 's' -> ' ';
                case '"', '\'', '\\' -> c;
                case 'u' -> unicode();
                default -> throw error("unsupported escape \\" + c);
            };
        }

        private char unicode() {
            while (pos < text.length() && text.charAt(pos) == 'u') {
                pos++;
            }
            if (pos + 4 > text.length()) {
                throw error("invalid unicode escape");
            }
            try {
                char c = (char) Integer.parseInt(text.substring(pos, pos + 4), 16);
                pos += 4;
                return c;
            } catch (NumberFormatException e) {
                throw error("invalid unicode escape");
            }
        }

        private boolean consume(char c) {
            if (pos < text.length() && text.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }

        private void skipSpace() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at position " + pos);
        }
    }
}
