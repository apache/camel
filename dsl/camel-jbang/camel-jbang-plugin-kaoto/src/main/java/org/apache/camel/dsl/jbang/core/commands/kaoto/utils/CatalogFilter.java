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
package org.apache.camel.dsl.jbang.core.commands.kaoto.utils;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * Build-time utility that filters the Kaoto camel-catalog to include only whitelisted runtime/version entries. Removes
 * non-whitelisted version directories and updates index.json accordingly.
 *
 * <p>
 * Usage: {@code java CatalogFilter <catalog-dir> <entry1,entry2,...>}
 * <p>
 * Example: {@code java CatalogFilter target/classes/kaoto/src/camel-catalog camel-main/4.23.0,citrus/latest}
 * <p>
 * The special version {@code latest} resolves to the highest version directory available in the catalog for that
 * runtime. Pinned versions that do not exist in the catalog cause a build failure.
 */
public final class CatalogFilter {

    private static final List<String> RUNTIMES = List.of("camel-main", "camel-quarkus", "camel-springboot", "citrus");

    private CatalogFilter() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: CatalogFilter <catalog-dir> <entry1,entry2,...>");
            System.exit(1);
        }

        Path catalogDir = Path.of(args[0]);
        Set<String> whitelist = resolveWhitelist(catalogDir, args[1]);

        System.out.println("Filtering Kaoto catalogs in: " + catalogDir);
        System.out.println("Included: " + whitelist);

        removeNonWhitelistedVersions(catalogDir, whitelist);
        filterIndexJson(catalogDir, whitelist);

        System.out.println("Catalog filtering complete.");
    }

    static Set<String> resolveWhitelist(Path catalogDir, String spec) throws IOException {
        Set<String> resolved = new LinkedHashSet<>();
        for (String entry : spec.split(",")) {
            entry = entry.trim();
            int slash = entry.indexOf('/');
            if (slash < 0) {
                throw new IllegalArgumentException("Invalid whitelist entry (expected runtime/version): " + entry);
            }

            String runtime = entry.substring(0, slash);
            String version = entry.substring(slash + 1);

            if ("latest".equals(version)) {
                version = findLatestVersion(catalogDir, runtime);
            } else {
                validateVersionExists(catalogDir, runtime, version);
            }

            resolved.add(runtime + "/" + version);
        }
        return resolved;
    }

    private static String findLatestVersion(Path catalogDir, String runtime) throws IOException {
        Path runtimeDir = catalogDir.resolve(runtime);
        if (!Files.isDirectory(runtimeDir)) {
            throw new IllegalStateException(
                    "Runtime directory not found in catalog: " + runtime
                                            + " (available: " + listDirectoryNames(catalogDir) + ")");
        }

        String latest = null;
        try (DirectoryStream<Path> versions = Files.newDirectoryStream(runtimeDir)) {
            for (Path versionDir : versions) {
                if (!Files.isDirectory(versionDir)) {
                    continue;
                }
                String name = versionDir.getFileName().toString();
                if (latest == null || compareVersions(name, latest) > 0) {
                    latest = name;
                }
            }
        }

        if (latest == null) {
            throw new IllegalStateException("No version directories found for runtime: " + runtime);
        }

        System.out.println("Resolved " + runtime + "/latest to " + runtime + "/" + latest);
        return latest;
    }

    private static void validateVersionExists(Path catalogDir, String runtime, String version) {
        Path versionDir = catalogDir.resolve(runtime).resolve(version);
        if (!Files.isDirectory(versionDir)) {
            String available;
            try {
                available = listDirectoryNames(catalogDir.resolve(runtime));
            } catch (IOException e) {
                available = "(unable to list)";
            }
            throw new IllegalStateException(
                    "Pinned version not found in catalog: " + runtime + "/" + version
                                            + " (available for " + runtime + ": " + available + ")");
        }
    }

    private static void removeNonWhitelistedVersions(Path catalogDir, Set<String> whitelist) throws IOException {
        for (String runtime : RUNTIMES) {
            Path runtimeDir = catalogDir.resolve(runtime);
            if (!Files.isDirectory(runtimeDir)) {
                continue;
            }
            try (DirectoryStream<Path> versions = Files.newDirectoryStream(runtimeDir)) {
                for (Path versionDir : versions) {
                    if (!Files.isDirectory(versionDir)) {
                        continue;
                    }
                    String entry = runtime + "/" + versionDir.getFileName();
                    if (!whitelist.contains(entry)) {
                        deleteRecursively(versionDir);
                    }
                }
            }
        }
    }

    private static void filterIndexJson(Path catalogDir, Set<String> whitelist) throws Exception {
        Path indexFile = catalogDir.resolve("index.json");
        if (!Files.isRegularFile(indexFile)) {
            return;
        }

        String content = Files.readString(indexFile);
        JsonObject index = (JsonObject) Jsoner.deserialize(content);
        JsonArray definitions = (JsonArray) index.get("definitions");

        JsonArray filtered = new JsonArray();
        for (Object obj : definitions) {
            JsonObject def = (JsonObject) obj;
            String fileName = def.getString("fileName");
            if (whitelist.stream().anyMatch(w -> fileName.startsWith(w + "/"))) {
                filtered.add(def);
            }
        }

        index.put("definitions", filtered);
        Files.writeString(indexFile, Jsoner.serialize(index));
    }

    static int compareVersions(String a, String b) {
        String[] partsA = a.split("\\.");
        String[] partsB = b.split("\\.");
        int len = Math.max(partsA.length, partsB.length);
        for (int i = 0; i < len; i++) {
            int numA = i < partsA.length ? parseSegment(partsA[i]) : 0;
            int numB = i < partsB.length ? parseSegment(partsB[i]) : 0;
            if (numA != numB) {
                return Integer.compare(numA, numB);
            }
        }
        return 0;
    }

    private static int parseSegment(String segment) {
        try {
            return Integer.parseInt(segment);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String listDirectoryNames(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return "(directory not found)";
        }
        StringBuilder sb = new StringBuilder();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    if (!sb.isEmpty()) {
                        sb.append(", ");
                    }
                    sb.append(entry.getFileName());
                }
            }
        }
        return sb.toString();
    }

    private static void deleteRecursively(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to delete: " + path, e);
                        }
                    });
        }
    }
}
