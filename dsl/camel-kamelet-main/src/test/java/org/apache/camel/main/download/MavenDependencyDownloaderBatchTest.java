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
package org.apache.camel.main.download;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.tooling.maven.MavenGav;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dependencies downloaded together, from a local file repository.
 */
class MavenDependencyDownloaderBatchTest {

    @TempDir
    Path dir;

    private MavenDependencyDownloader downloader;
    private DependencyDownloaderClassLoader classLoader;
    private final List<String> downloaded = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        Path repo = Files.createDirectories(dir.resolve("repo"));
        // a -> common 1.0, b -> common 2.0
        writeJar(repo, "a", "1.0", "common", "1.0");
        writeJar(repo, "b", "1.0", "common", "2.0");
        writeJar(repo, "common", "1.0", null, null);
        writeJar(repo, "common", "2.0", null, null);
        Path settings = Files.writeString(dir.resolve("settings.xml"),
                "<settings><localRepository>" + dir.resolve("local") + "</localRepository></settings>");

        classLoader = new DependencyDownloaderClassLoader(getClass().getClassLoader());
        downloader = new MavenDependencyDownloader();
        downloader.setClassLoader(classLoader);
        downloader.setMavenCentralEnabled(false);
        downloader.setMavenApacheSnapshotEnabled(false);
        downloader.setMavenSettings(settings.toString());
        downloader.setRepositories(repo.toUri().toString());
        downloader.addDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadDependency(String groupId, String artifactId, String version) {
            }

            @Override
            public void onDownloadedDependency(String groupId, String artifactId, String version) {
                downloaded.add(groupId + ":" + artifactId + ":" + version);
            }

            @Override
            public void onAlreadyDownloadedDependency(String groupId, String artifactId, String version) {
            }
        });
        downloader.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        downloader.stop();
        classLoader.close();
    }

    @Test
    void dependenciesAreResolvedTogether() {
        downloader.downloadDependencies(List.of(gav("a"), gav("b")), true);

        assertEquals(List.of("org.example.batch:a:1.0", "org.example.batch:b:1.0"), downloaded);
        // one graph: one version of common (the nearest first: a's)
        assertEquals(List.of("a-1.0.jar", "b-1.0.jar", "common-1.0.jar"), jars());
    }

    @Test
    void dependencyThatCannotBeDownloadedIsLogged() {
        downloader.downloadDependencies(List.of(gav("a"), gav("missing"), gav("b")), false);

        // one at a time: the others are downloaded
        assertEquals(List.of("org.example.batch:a:1.0", "org.example.batch:b:1.0"), downloaded);
        assertTrue(jars().containsAll(List.of("a-1.0.jar", "b-1.0.jar")), jars().toString());
    }

    @Test
    void requiredDependencyThatCannotBeDownloadedFails() {
        DownloadException e = assertThrows(DownloadException.class,
                () -> downloader.downloadDependencies(List.of(gav("a"), gav("missing"), gav("b")), true));

        // the one that cannot be downloaded
        assertTrue(e.getMessage().startsWith("Cannot download dependency: org.example.batch:missing:1.0 due to: "),
                e.getMessage());
        assertEquals(List.of("org.example.batch:a:1.0"), downloaded);
    }

    private static MavenGav gav(String artifactId) {
        return MavenGav.fromCoordinates("org.example.batch", artifactId, "1.0", null, null);
    }

    private List<String> jars() {
        return classLoader.getDownloaded().stream().sorted().toList();
    }

    private static void writeJar(Path repo, String artifactId, String version, String dependency, String dependencyVersion)
            throws Exception {
        Path dir = Files.createDirectories(repo.resolve("org/example/batch/" + artifactId + "/" + version));
        String deps = dependency == null
                ? ""
                : """
                          <dependencies>
                            <dependency><groupId>org.example.batch</groupId><artifactId>%s</artifactId><version>%s</version></dependency>
                          </dependencies>
                        """
                        .formatted(dependency, dependencyVersion);
        Files.writeString(dir.resolve(artifactId + "-" + version + ".pom"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.example.batch</groupId>
                  <artifactId>%s</artifactId>
                  <version>%s</version>
                %s</project>
                """.formatted(artifactId, version, deps));
        Files.writeString(dir.resolve(artifactId + "-" + version + ".jar"), artifactId);
    }
}
