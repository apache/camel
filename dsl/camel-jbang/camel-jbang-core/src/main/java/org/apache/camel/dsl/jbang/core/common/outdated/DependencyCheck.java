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
package org.apache.camel.dsl.jbang.core.common.outdated;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.tooling.maven.MavenArtifact;
import org.apache.camel.tooling.maven.MavenDownloader;
import org.apache.camel.tooling.maven.MavenGav;

/**
 * The check of the dependencies of an integration (CAMEL-27351): how the version of each relates to the one the Camel
 * dependencies bring, which ones are loaded in another version or twice, and which newer versions there are. Shared by
 * {@code camel dependency outdated} and the Maven Dependencies screen of the TUI.
 */
public final class DependencyCheck {

    private DependencyCheck() {
    }

    /**
     * Sets the status of each row.
     *
     * @param rows         the dependencies, declared and transitive
     * @param camelVersion the Camel version of the integration, null when not known
     * @param camelBrings  {@code groupId:artifactId} to the version the Camel dependencies bring, empty when not
     *                     resolved
     * @param loaded       {@code groupId:artifactId} to the versions on the classpath of the running integration, null
     *                     when not known
     */
    public static void classify(
            List<DependencyRow> rows, String camelVersion, Map<String, String> camelBrings,
            Map<String, ? extends Collection<String>> loaded) {
        for (DependencyRow row : rows) {
            Collection<String> versions = loaded != null ? loaded.get(row.ga()) : null;
            row.loadedVersions().clear();
            if (versions != null) {
                row.loadedVersions().addAll(new TreeSet<>(versions));
            }
            if (row.camel()) {
                row.setCamelVersion(camelVersion);
            } else {
                row.setCamelVersion(camelBrings != null ? camelBrings.get(row.ga()) : null);
            }
            row.setStatus(statusOf(row, camelVersion));
        }
    }

    private static DependencyRow.Status statusOf(DependencyRow row, String camelVersion) {
        List<String> loaded = row.loadedVersions();
        if (loaded.size() > 1 || (row.version() != null && !loaded.isEmpty() && !loaded.contains(row.version()))) {
            return DependencyRow.Status.CONFLICT;
        }
        if (row.camel()) {
            if (DependencyRow.sameVersionAsCamel(row.groupId()) && camelVersion != null && row.version() != null
                    && !camelVersion.equals(row.version())) {
                return DependencyRow.Status.MIXED;
            }
            return DependencyRow.Status.CAMEL;
        }
        String brings = row.camelVersion();
        if (brings == null || row.version() == null) {
            return DependencyRow.Status.NONE;
        }
        if (brings.equals(row.version())) {
            return DependencyRow.Status.MANAGED;
        }
        // a transitive one in another version is not the project's choice: the classpath tells which one wins
        return row.transitive() ? DependencyRow.Status.NONE : DependencyRow.Status.OVERRIDE;
    }

    /** Sets the newer versions of each row that is not a Camel JAR, from the available versions per GA. */
    public static void applyAvailable(List<DependencyRow> rows, Map<String, List<String>> available) {
        for (DependencyRow row : rows) {
            if (row.camel() || row.version() == null) {
                continue;
            }
            List<String> versions = available.get(row.ga());
            if (versions != null) {
                row.setNewer(Versions.newerPatch(row.version(), versions), Versions.latest(row.version(), versions));
            }
        }
    }

    /**
     * The versions of the libraries the Camel dependencies bring: the Camel GAVs resolved with their transitive
     * dependencies, without the other dependencies of the project, so a version the project declares does not hide the
     * one of Camel.
     *
     * @return {@code groupId:artifactId} to version, of the JARs that are not Camel JARs
     */
    public static Map<String, String> camelBrings(MavenDownloader downloader, Collection<String> camelGavs)
            throws Exception {
        Map<String, String> result = new HashMap<>();
        if (camelGavs.isEmpty()) {
            return result;
        }
        boolean snapshot = camelGavs.stream().anyMatch(g -> g.contains("SNAPSHOT"));
        for (MavenArtifact ma : downloader.resolveArtifacts(new ArrayList<>(camelGavs), Set.of(), true, snapshot)) {
            MavenGav g = ma.getGav();
            if (g.getVersion() != null && !Versions.isCamel(g.getGroupId())) {
                result.putIfAbsent(g.getGroupId() + ":" + g.getArtifactId(), g.getVersion());
            }
        }
        return result;
    }

    /**
     * The available versions of each {@code groupId:artifactId}, from the remote repositories of the Maven settings
     * (Maven Central, mirrors, profiles), a few at a time.
     *
     * @param  error set to the message of a lookup that failed (the others are still returned)
     * @return       {@code groupId:artifactId} to its versions; one that failed is left out
     */
    public static Map<String, List<String>> availableVersions(
            MavenDownloader downloader, Collection<String> gas, AtomicReference<String> error) {
        Map<String, List<String>> result = new ConcurrentHashMap<>();
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, Math.min(8, gas.size())), r -> {
            Thread t = new Thread(r, "CamelDependencyVersions");
            t.setDaemon(true);
            return t;
        });
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (String ga : new TreeSet<>(gas)) {
                int colon = ga.indexOf(':');
                if (colon <= 0) {
                    continue;
                }
                futures.add(pool.submit(() -> {
                    try {
                        List<String> versions = new ArrayList<>();
                        for (MavenGav g : downloader.resolveAvailableVersions(
                                ga.substring(0, colon), ga.substring(colon + 1), null, false)) {
                            versions.add(g.getVersion());
                        }
                        result.put(ga, versions);
                    } catch (Exception e) {
                        if (error != null) {
                            error.compareAndSet(null, e.getMessage());
                        }
                    }
                }));
            }
            for (Future<?> f : futures) {
                try {
                    f.get(60, TimeUnit.SECONDS);
                } catch (Exception e) {
                    if (error != null) {
                        error.compareAndSet(null, "timeout looking up the available versions");
                    }
                }
            }
        } finally {
            pool.shutdownNow();
        }
        return result;
    }

    /**
     * The versions of each {@code groupId:artifactId} on a classpath of JARs in a Maven repository, such as
     * {@code .../repository/org/yaml/snakeyaml/1.33/snakeyaml-1.33.jar}; JARs outside a repository are skipped.
     */
    public static Map<String, Set<String>> loadedVersions(Collection<String> classpath) {
        Map<String, Set<String>> result = new HashMap<>();
        for (String path : classpath) {
            String[] gav = gavOfJar(path);
            if (gav != null) {
                result.computeIfAbsent(gav[0] + ":" + gav[1], k -> new TreeSet<>()).add(gav[2]);
            }
        }
        return result;
    }

    /** groupId, artifactId and version of a JAR in a Maven repository; null for another path. */
    static String[] gavOfJar(String path) {
        if (path == null) {
            return null;
        }
        String normalized = path.replace('\\', '/');
        int repoIdx = normalized.indexOf("/repository/");
        if (repoIdx < 0 || !normalized.endsWith(".jar")) {
            return null;
        }
        String[] parts = normalized.substring(repoIdx + "/repository/".length()).split("/");
        if (parts.length < 4) {
            return null;
        }
        String version = parts[parts.length - 2];
        String artifactId = parts[parts.length - 3];
        if (!parts[parts.length - 1].startsWith(artifactId + "-")) {
            return null;
        }
        StringBuilder groupId = new StringBuilder();
        for (int i = 0; i < parts.length - 3; i++) {
            if (i > 0) {
                groupId.append('.');
            }
            groupId.append(parts[i]);
        }
        return new String[] { groupId.toString(), artifactId, version };
    }
}
