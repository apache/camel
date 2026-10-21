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
package org.apache.camel.tooling.maven;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Local repositories written by Maven 3.10 (Maven Resolver 2.0.23+) track the origin of artifacts with URL-qualified
 * keys ({@code file>central-<sha1(url)>=}) instead of the ID-only keys ({@code file>central=}) of older Maven versions.
 * Both forms must be accepted, for the same repository id.
 */
public class MavenDownloaderTrackingKeyTest {

    private static final String REPO_ID = "myrepo";
    // Maven hashes the URL it knows the repository by, which may differ from the URL Camel uses (e.g. Maven Central)
    private static final String OTHER_URL_KEY = REPO_ID + "-" + DigestUtils.sha1Hex("https://repo.example.com/maven2");

    @TempDir
    Path tempDir;

    @Test
    void testResolveArtifactTrackedWithLegacyKey() throws Exception {
        Path localRepo = tempDir.resolve("local");
        writeArtifact(localRepo, "dep", "", null, REPO_ID);

        // the remote repository is unreachable, so the artifact can only come from the local repository
        assertEquals(List.of("dep-1.0.jar"), resolve(localRepo, "http://localhost:1/unreachable", "dep", false));
    }

    @Test
    void testResolveArtifactTrackedWithUrlQualifiedKey() throws Exception {
        Path localRepo = tempDir.resolve("local");
        writeArtifact(localRepo, "dep", "", null, OTHER_URL_KEY);

        assertEquals(List.of("dep-1.0.jar"), resolve(localRepo, "http://localhost:1/unreachable", "dep", false));
    }

    @Test
    void testUrlQualifiedKeyOfAnotherRepositoryIsNotAccepted() throws Exception {
        Path localRepo = tempDir.resolve("local");
        // "myrepo-other" is a different repository whose key also starts with "myrepo-"
        writeArtifact(localRepo, "dep", "", null,
                REPO_ID + "-other-" + DigestUtils.sha1Hex("https://other.example.com"));

        // not trusted, so it has to be verified against the (unreachable) remote repository
        assertThrows(MavenResolutionException.class,
                () -> resolve(localRepo, "http://localhost:1/unreachable", "dep", false));
    }

    @Test
    void testResolveTransitivelyWithBomTrackedWithUrlQualifiedKey() throws Exception {
        Path localRepo = tempDir.resolve("local");
        Path remoteRepo = tempDir.resolve("remote");
        writePluginProject(localRepo, OTHER_URL_KEY);
        writePluginProject(remoteRepo, null);

        assertEquals(List.of("plugin-1.0.jar", "dep-1.0.jar"),
                resolve(localRepo, remoteRepo.toUri().toString(), "plugin", true));
    }

    @Test
    void testResolveTransitivelyWithBomMissingInLocalRepository() throws Exception {
        Path localRepo = tempDir.resolve("local");
        Path remoteRepo = tempDir.resolve("remote");
        writePluginProject(localRepo, REPO_ID);
        writePluginProject(remoteRepo, null);
        // the plugin POM cannot be built from the local repository alone, so the BOM has to be downloaded
        deleteDirectory(localRepo.resolve("org/test/bom"));

        assertEquals(List.of("plugin-1.0.jar", "dep-1.0.jar"),
                resolve(localRepo, remoteRepo.toUri().toString(), "plugin", true));
    }

    /**
     * A plugin whose parent imports a BOM that manages the version of its dependency.
     *
     * @param key the tracking key of the downloaded artifacts, {@code null} for a remote repository; the plugin and its
     *            parent are installed locally (mvn install)
     */
    private static void writePluginProject(Path repo, String key) throws IOException {
        boolean local = key != null;
        writePom(repo, "parent", "<packaging>pom</packaging>"
                                 + "<dependencyManagement><dependencies><dependency>"
                                 + "<groupId>org.test</groupId><artifactId>bom</artifactId><version>1.0</version>"
                                 + "<type>pom</type><scope>import</scope>"
                                 + "</dependency></dependencies></dependencyManagement>",
                local ? "" : null);
        writePom(repo, "bom", "<packaging>pom</packaging>"
                              + "<dependencyManagement><dependencies><dependency>"
                              + "<groupId>org.test</groupId><artifactId>dep</artifactId><version>1.0</version>"
                              + "</dependency></dependencies></dependencyManagement>",
                key);
        writeArtifact(repo, "plugin",
                "<parent><groupId>org.test</groupId><artifactId>parent</artifactId><version>1.0</version></parent>"
                                      + "<dependencies><dependency>"
                                      + "<groupId>org.test</groupId><artifactId>dep</artifactId>"
                                      + "</dependency></dependencies>",
                local ? "" : null, null);
        writeArtifact(repo, "dep", "", key, null);
    }

    private static void deleteDirectory(Path dir) throws IOException {
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }

    private List<String> resolve(Path localRepo, String remoteUrl, String artifactId, boolean transitively)
            throws Exception {
        try (MavenDownloaderImpl downloader = newDownloader(remoteUrl)) {
            return downloader.customize(localRepo.toString(), 1000, 1000)
                    .resolveArtifacts(List.of("org.test:" + artifactId + ":1.0"), null, transitively, false)
                    .stream().map(a -> a.getFile().getName()).collect(Collectors.toList());
        }
    }

    private static MavenDownloaderImpl newDownloader(String remoteUrl) {
        MavenDownloaderImpl downloader = new MavenDownloaderImpl();
        downloader.setMavenCentralEnabled(false);
        downloader.setMavenApacheSnapshotEnabled(false);
        downloader.setMavenSettingsLocation("false");
        downloader.setRepos(REPO_ID + "=" + remoteUrl);
        downloader.build();
        return downloader;
    }

    /**
     * @param pomKey the tracking key of the POM, {@code ""} if installed locally, {@code null} for no tracking file
     * @param jarKey the tracking key of the JAR, defaults to the POM's
     */
    private static void writeArtifact(Path repo, String artifactId, String pomBody, String pomKey, String jarKey)
            throws IOException {
        writePom(repo, artifactId, pomBody, pomKey);
        Path dir = versionDir(repo, artifactId);
        Files.writeString(dir.resolve(artifactId + "-1.0.jar"), artifactId);
        String key = jarKey != null ? jarKey : pomKey;
        if (key != null) {
            Files.writeString(dir.resolve("_remote.repositories"),
                    artifactId + "-1.0.jar>" + key + "=\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
    }

    private static void writePom(Path repo, String artifactId, String body, String key) throws IOException {
        Path dir = versionDir(repo, artifactId);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(artifactId + "-1.0.pom"),
                "<project><modelVersion>4.0.0</modelVersion>"
                                                                + "<groupId>org.test</groupId><artifactId>" + artifactId
                                                                + "</artifactId><version>1.0</version>" + body
                                                                + "</project>");
        if (key != null) {
            Files.writeString(dir.resolve("_remote.repositories"), artifactId + "-1.0.pom>" + key + "=\n");
        }
    }

    private static Path versionDir(Path repo, String artifactId) {
        return repo.resolve("org").resolve("test").resolve(artifactId).resolve("1.0");
    }
}
