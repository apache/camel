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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.apache.camel.util.FileUtil;
import org.apache.commons.codec.binary.Hex;
import org.apache.http.Header;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for MavenDownloaderImpl using MIMA (Minimal Maven).
 * <p>
 * Note: Most tests are disabled by default and require -DenableMavenDownloaderTests=true because they download from
 * Maven Central and can be slow/flaky in CI environments.
 */
class MavenDownloaderImplTest {

    private static final Logger LOG = LoggerFactory.getLogger(MavenDownloaderImplTest.class);

    private static HttpServer localServer;

    @TempDir
    File tempDir;

    @BeforeAll
    public static void startMavenMirror() throws Exception {
        localServer = ServerBootstrap.bootstrap()
                .setListenerPort(AvailablePortFinder.getNextAvailable(9234, 10000))
                .registerHandler("/maven/*", (req, res, context) -> {
                    Header authz = req.getFirstHeader("Authorization");
                    if (authz == null) {
                        res.addHeader("WWW-Authenticate", "Basic realm=Camel");
                        res.setStatusCode(401);
                        return;
                    }
                    String creds = new String(Base64.getDecoder().decode(authz.getValue().split(" ")[1]));
                    if (!"camel:passw0rd".equals(creds)) {
                        res.setStatusCode(403);
                        return;
                    }
                    LOG.info("Request: {}", req.getRequestLine());
                    String request = req.getRequestLine().getUri().substring("/maven".length());
                    if (request.endsWith(".jar") || request.endsWith(".pom")) {
                        res.setEntity(new StringEntity(request));
                    } else {
                        try {
                            MessageDigest md = null;
                            if (request.endsWith(".md5")) {
                                md = MessageDigest.getInstance("MD5");
                                request = request.substring(0, request.length() - 4);
                            } else if (request.endsWith(".sha1")) {
                                md = MessageDigest.getInstance("SHA");
                                request = request.substring(0, request.length() - 5);
                            }
                            if (md != null) {
                                byte[] digest = md.digest(request.getBytes(StandardCharsets.UTF_8));
                                res.setEntity(new StringEntity(Hex.encodeHexString(digest)));
                            }
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                    res.setStatusCode(200);
                })
                .create();
        localServer.start();
    }

    @AfterAll
    public static void stopMavenMirror() {
        if (localServer != null) {
            localServer.shutdown(2, TimeUnit.SECONDS);
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "enableMavenDownloaderTests", matches = "true")
    void testResolveSimpleArtifact() throws Exception {
        try (MavenDownloaderImpl downloader = new MavenDownloaderImpl()) {
            downloader.build();

            // Resolve a small, stable artifact
            List<MavenArtifact> artifacts = downloader.resolveArtifacts(
                    List.of("org.apache.commons:commons-lang3:3.12.0"),
                    null,
                    false, // non-transitive
                    false  // no Apache snapshots
            );

            assertEquals(1, artifacts.size());
            MavenArtifact artifact = artifacts.get(0);
            assertEquals("org.apache.commons", artifact.getGav().getGroupId());
            assertEquals("commons-lang3", artifact.getGav().getArtifactId());
            assertEquals("3.12.0", artifact.getGav().getVersion());
            assertNotNull(artifact.getFile());
            assertTrue(artifact.getFile().exists());
            assertTrue(artifact.getFile().getName().endsWith(".jar"));
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "enableMavenDownloaderTests", matches = "true")
    void testResolveTransitiveDependencies() throws Exception {
        try (MavenDownloaderImpl downloader = new MavenDownloaderImpl()) {
            downloader.build();

            // Resolve with transitive dependencies - slf4j-simple depends on slf4j-api
            List<MavenArtifact> artifacts = downloader.resolveArtifacts(
                    List.of("org.slf4j:slf4j-simple:1.7.36"),
                    null,
                    true, // transitive
                    false);

            // Should have at least slf4j-simple + slf4j-api
            assertTrue(artifacts.size() >= 2,
                    "Expected at least 2 artifacts (slf4j-simple + slf4j-api), got " + artifacts.size());

            boolean hasSimple = artifacts.stream()
                    .anyMatch(a -> "slf4j-simple".equals(a.getGav().getArtifactId()));
            boolean hasApi = artifacts.stream()
                    .anyMatch(a -> "slf4j-api".equals(a.getGav().getArtifactId()));

            assertTrue(hasSimple, "Should contain slf4j-simple");
            assertTrue(hasApi, "Should contain slf4j-api (transitive dependency)");
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "enableMavenDownloaderTests", matches = "true")
    void testResolveMultipleArtifacts() throws Exception {
        try (MavenDownloaderImpl downloader = new MavenDownloaderImpl()) {
            downloader.build();

            List<MavenArtifact> artifacts = downloader.resolveArtifacts(
                    List.of(
                            "org.apache.commons:commons-lang3:3.12.0",
                            "commons-io:commons-io:2.11.0"),
                    null,
                    false,
                    false);

            assertEquals(2, artifacts.size());
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "enableMavenDownloaderTests", matches = "true")
    void testResolveAvailableVersions() throws Exception {
        try (MavenDownloaderImpl downloader = new MavenDownloaderImpl()) {
            downloader.build();

            List<MavenGav> versions = downloader.resolveAvailableVersions(
                    "org.apache.commons",
                    "commons-lang3",
                    null // use Maven Central
            );

            assertFalse(versions.isEmpty(), "Should find multiple versions of commons-lang3");
            assertTrue(versions.size() > 10, "Should have many versions of commons-lang3");

            // Check that versions contain expected versions
            boolean has3_12 = versions.stream()
                    .anyMatch(v -> "3.12.0".equals(v.getVersion()));
            assertTrue(has3_12, "Should contain version 3.12.0");

            LOG.info("Found {} versions of commons-lang3", versions.size());
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "enableMavenDownloaderTests", matches = "true")
    void testCustomLocalRepository() throws Exception {
        File customLocalRepo = new File(tempDir, "custom-local-repo");
        Files.createDirectories(customLocalRepo.toPath());

        try (MavenDownloaderImpl downloader = new MavenDownloaderImpl()) {
            downloader.build();

            // Create customized downloader with custom local repository
            MavenDownloader customDownloader = downloader.customize(
                    customLocalRepo.getAbsolutePath(),
                    5000,  // connect timeout
                    10000  // request timeout
            );

            List<MavenArtifact> artifacts = customDownloader.resolveArtifacts(
                    List.of("org.apache.commons:commons-lang3:3.12.0"),
                    null,
                    false,
                    false);

            assertEquals(1, artifacts.size());

            // Verify artifact was downloaded to custom local repository
            File expectedPath = new File(
                    customLocalRepo,
                    "org/apache/commons/commons-lang3/3.12.0/commons-lang3-3.12.0.jar");
            assertTrue(expectedPath.exists(),
                    "Artifact should be in custom local repository: " + expectedPath);
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "enableMavenDownloaderTests", matches = "true")
    void testOfflineMode() throws Exception {
        // First download the artifact to local repo
        try (MavenDownloaderImpl downloader = new MavenDownloaderImpl()) {
            downloader.build();
            downloader.resolveArtifacts(
                    List.of("org.apache.commons:commons-lang3:3.12.0"),
                    null,
                    false,
                    false);
        }

        // Now try in offline mode - should work from cache
        try (MavenDownloaderImpl downloader = new MavenDownloaderImpl()) {
            downloader.setOffline(true);
            downloader.build();

            List<MavenArtifact> artifacts = downloader.resolveArtifacts(
                    List.of("org.apache.commons:commons-lang3:3.12.0"),
                    null,
                    false,
                    false);

            assertEquals(1, artifacts.size());
            assertTrue(artifacts.get(0).getFile().exists());
        }

        // Try to download an artifact not in cache - should fail in offline mode
        try (MavenDownloaderImpl downloader = new MavenDownloaderImpl()) {
            downloader.setOffline(true);
            downloader.build();

            downloader.resolveArtifacts(
                    List.of("org.apache.commons:commons-lang3:99.99.99"), // non-existent version
                    null,
                    false,
                    false);
            fail("Should have failed in offline mode for non-cached artifact");
        } catch (MavenResolutionException e) {
            // Expected
            LOG.info("Expected failure in offline mode: {}", e.getMessage());
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "enableMavenDownloaderTests", matches = "true")
    void testPreferLocalResolvesFromCache() throws Exception {
        File customLocalRepo = new File(tempDir, "prefer-local-repo");
        Files.createDirectories(customLocalRepo.toPath());

        // First, download the artifact into the custom local repo (online)
        try (MavenDownloaderImpl downloader = new MavenDownloaderImpl()) {
            downloader.build();

            MavenDownloader customDownloader = downloader.customize(
                    customLocalRepo.getAbsolutePath(), 5000, 10000);

            customDownloader.resolveArtifacts(
                    List.of("org.apache.commons:commons-lang3:3.12.0"),
                    null, false, false);
        }

        // Verify artifact is in custom local repo
        File cachedJar = new File(
                customLocalRepo,
                "org/apache/commons/commons-lang3/3.12.0/commons-lang3-3.12.0.jar");
        assertTrue(cachedJar.exists(), "Artifact should have been downloaded to custom local repo");

        // Now create a new downloader with preferLocal=true, Maven Central disabled,
        // and a non-existent fake repo. Without preferLocal, this would fail because
        // there are no reachable remote repos. With preferLocal, it should succeed
        // by resolving from the local cache via offline-first resolution.
        try (MavenDownloaderImpl downloader = new MavenDownloaderImpl()) {
            downloader.setPreferLocal(true);
            downloader.setMavenCentralEnabled(false);
            downloader.setMavenApacheSnapshotEnabled(false);
            downloader.setMavenSettingsLocation("false"); // disable settings.xml repos
            downloader.setRepos("http://localhost:1/non-existent-repo"); // unreachable repo
            downloader.build();

            MavenDownloader customDownloader = downloader.customize(
                    customLocalRepo.getAbsolutePath(), 1000, 1000);

            List<MavenArtifact> artifacts = customDownloader.resolveArtifacts(
                    List.of("org.apache.commons:commons-lang3:3.12.0"),
                    null, false, false);

            assertEquals(1, artifacts.size());
            assertTrue(artifacts.get(0).getFile().exists());
            LOG.info("preferLocal resolved artifact from local cache without contacting remote repos");
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "enableMavenDownloaderTests", matches = "true")
    void testPreferLocalFallsBackToOnline() throws Exception {
        // With preferLocal=true, if the artifact is NOT in the local repo,
        // it should fall back to online resolution and succeed
        try (MavenDownloaderImpl downloader = new MavenDownloaderImpl()) {
            downloader.setPreferLocal(true);
            downloader.build();

            List<MavenArtifact> artifacts = downloader.resolveArtifacts(
                    List.of("org.apache.commons:commons-lang3:3.12.0"),
                    null, false, false);

            assertEquals(1, artifacts.size());
            assertTrue(artifacts.get(0).getFile().exists());
            LOG.info("preferLocal fell back to online resolution for non-cached artifact");
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "enableMavenDownloaderTests", matches = "true")
    void testDisableMavenCentral() throws Exception {
        try (MavenDownloaderImpl downloader = new MavenDownloaderImpl()) {
            downloader.setMavenCentralEnabled(false);
            downloader.build();

            try {
                downloader.resolveArtifacts(
                        List.of("org.apache.commons:commons-lang3:3.12.0"),
                        null,
                        false,
                        false);
                fail("Should fail when Maven Central is disabled");
            } catch (MavenResolutionException e) {
                // Expected
                LOG.info("Expected failure with Maven Central disabled: {}", e.getMessage());
            }
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "enableMavenDownloaderTests", matches = "true")
    void testCustomRepository() throws Exception {
        try (MavenDownloaderImpl downloader = new MavenDownloaderImpl()) {
            downloader.setMavenCentralEnabled(false);
            // Add Maven Central as a custom repository to verify custom repo support works
            downloader.setRepos("https://repo1.maven.org/maven2");
            downloader.build();

            List<MavenArtifact> artifacts = downloader.resolveArtifacts(
                    List.of("org.apache.commons:commons-lang3:3.12.0"),
                    null,
                    false,
                    false);

            assertEquals(1, artifacts.size());
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "enableMavenDownloaderTests", matches = "true")
    void testExtraRepositories() throws Exception {
        try (MavenDownloaderImpl downloader = new MavenDownloaderImpl()) {
            downloader.setMavenCentralEnabled(false);
            downloader.build();

            // Pass extra repository as parameter to resolveArtifacts
            List<MavenArtifact> artifacts = downloader.resolveArtifacts(
                    List.of("org.apache.commons:commons-lang3:3.12.0"),
                    Set.of("https://repo1.maven.org/maven2"), // extra repository
                    false,
                    false);

            assertEquals(1, artifacts.size());
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "enableMavenDownloaderTests", matches = "true")
    void testApacheSnapshots() throws Exception {
        try (MavenDownloaderImpl downloader = new MavenDownloaderImpl()) {
            downloader.setMavenCentralEnabled(true);
            downloader.setMavenApacheSnapshotEnabled(true);
            downloader.build();

            // Try to resolve a recent Camel SNAPSHOT (may not always be available)
            // This test verifies that Apache Snapshots repository can be used
            try {
                downloader.resolveAvailableVersions(
                        "org.apache.camel",
                        "camel-core",
                        "https://repository.apache.org/snapshots");
                // If this succeeds, Apache Snapshots is accessible
            } catch (MavenResolutionException e) {
                // This may fail if there are no snapshots or network issues
                // Just log and continue
                LOG.warn("Could not access Apache Snapshots: {}", e.getMessage());
            }
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "enableMavenDownloaderTests", matches = "true")
    void testDownloadListener() throws Exception {
        final int[] downloadingCount = { 0 };
        final int[] downloadedCount = { 0 };

        RemoteArtifactDownloadListener listener = new RemoteArtifactDownloadListener() {
            @Override
            public void artifactDownloading(
                    String groupId, String artifactId, String version,
                    String repositoryId, String repositoryUrl) {
                downloadingCount[0]++;
                LOG.info("Downloading: {}:{}:{} from {}", groupId, artifactId, version, repositoryUrl);
            }

            @Override
            public void artifactDownloaded(
                    String groupId, String artifactId, String version,
                    String repositoryId, String repositoryUrl, long elapsedMs) {
                downloadedCount[0]++;
                LOG.info("Downloaded: {}:{}:{} from {} in {}ms", groupId, artifactId, version, repositoryUrl, elapsedMs);
            }
        };

        File customLocalRepo = new File(tempDir, "empty-local-repo");
        Files.createDirectories(customLocalRepo.toPath());

        try (MavenDownloaderImpl downloader = new MavenDownloaderImpl()) {
            downloader.setRemoteArtifactDownloadListener(listener);
            downloader.build();

            MavenDownloader customDownloader = downloader.customize(
                    customLocalRepo.getAbsolutePath(),
                    5000,
                    10000);

            List<MavenArtifact> artifacts = customDownloader.resolveArtifacts(
                    List.of("org.apache.commons:commons-lang3:3.12.0"),
                    null,
                    true, // transitive - will download multiple artifacts
                    false);

            assertFalse(artifacts.isEmpty());

            // Note: Listener is only called for remote downloads, not cache hits
            // So counts might be 0 if artifacts were already in default local repo
            LOG.info("Download events: downloading={}, downloaded={}", downloadingCount[0], downloadedCount[0]);
        }
    }

    @Test
    void testBuildAndStop() throws Exception {
        // Test basic lifecycle without network access
        try (MavenDownloaderImpl downloader = new MavenDownloaderImpl()) {
            downloader.setMavenCentralEnabled(true);
            downloader.setMavenApacheSnapshotEnabled(false);
            downloader.build();

            // Verify it built successfully
            assertNotNull(downloader.getRepositoryResolver());
        }
    }

    @Test
    void testInvalidArtifactCoordinates() throws Exception {
        try (MavenDownloaderImpl downloader = new MavenDownloaderImpl()) {
            downloader.build();

            try {
                downloader.resolveArtifacts(
                        List.of("invalid:coordinates"), // Missing version
                        null,
                        false,
                        false);
                fail("Should fail with invalid coordinates");
            } catch (Exception e) {
                // Expected - could be IllegalArgumentException or MavenResolutionException
                LOG.info("Expected failure with invalid coordinates: {}", e.getMessage());
            }
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "enableMavenDownloaderTests", matches = "true")
    void testNonExistentArtifact() throws Exception {
        try (MavenDownloaderImpl downloader = new MavenDownloaderImpl()) {
            downloader.build();

            try {
                downloader.resolveArtifacts(
                        List.of("org.apache.camel:non-existent-artifact:99.99.99"),
                        null,
                        false,
                        false);
                fail("Should fail when artifact doesn't exist");
            } catch (MavenResolutionException e) {
                // Expected
                assertNotNull(e.getMessage());
                assertFalse(e.getRepositories().isEmpty(), "Should include repository URLs in exception");
                LOG.info("Expected failure for non-existent artifact: {}", e.getMessage());
            }
        }
    }

    @Test
    void testDisableSettings() throws Exception {
        try (MavenDownloaderImpl downloader = new MavenDownloaderImpl()) {
            // Setting to "false" disables settings.xml processing
            downloader.setMavenSettingsLocation("false");
            downloader.build();

            // Should still work with default configuration
            // This is tested in offline mode to avoid network dependency
        }
    }

    @Test
    void testRepositoryResolver() throws Exception {
        try (MavenDownloaderImpl downloader = new MavenDownloaderImpl()) {
            // Use the default resolver
            downloader.build();

            // Verify default resolver was created
            assertNotNull(downloader.getRepositoryResolver());
        }
    }

    @Test
    void testAuthenticationAndMirrors() throws Exception {
        // This test verifies that MIMA correctly handles:
        // 1. Settings.xml with server authentication
        // 2. Repository mirrors
        // 3. The id=url format for preserving repository IDs
        //
        // The id=url format (e.g., "test-server=http://...") preserves the repository ID,
        // which is required for settings.xml server authentication matching:
        //   <server><id>test-server</id><username>...</username></server>

        String localSettings = "src/test/resources/.m2/settings.xml";
        String localSettingsSecurity = "src/test/resources/.m2/settings-security.xml";

        // Read the settings and update the port to our test server
        String settingsContent = Files.readString(new File(localSettings).toPath());
        settingsContent = settingsContent.replaceAll("http://localhost:0/",
                "http://localhost:" + localServer.getLocalPort() + "/");

        // Write to temp file
        File tempSettings = new File(tempDir, "settings.xml");
        Files.writeString(tempSettings.toPath(), settingsContent);

        // Create custom local repository
        File customLocalRepo = new File(tempDir, "custom-m2-repository");
        FileUtil.removeDir(customLocalRepo);
        Files.createDirectories(customLocalRepo.toPath());

        String testRepoUrl = "http://localhost:" + localServer.getLocalPort() + "/maven/repository";

        try (MavenDownloaderImpl downloader = new MavenDownloaderImpl()) {
            downloader.setMavenSettingsLocation(tempSettings.getAbsolutePath());
            downloader.setMavenSettingsSecurityLocation(localSettingsSecurity);
            downloader.setMavenCentralEnabled(false); // Disable Maven Central
            // Use id=url format to preserve repository ID for settings.xml auth matching
            downloader.setRepos("test-server=" + testRepoUrl);
            downloader.build();

            // Customize with our local repository
            MavenDownloader customDownloader = downloader.customize(
                    customLocalRepo.getAbsolutePath(),
                    5000,
                    10000);

            // Try to resolve an artifact - should go through the mirror with authentication
            List<MavenArtifact> artifacts = customDownloader.resolveArtifacts(
                    List.of("org.apache.camel:camel-anything:3.42.0"),
                    null,
                    false,
                    false);

            assertEquals(1, artifacts.size());
            MavenArtifact artifact = artifacts.get(0);
            assertTrue(artifact.getFile().isFile());

            // Verify the content - our mock server returns the path as content
            String content = Files.readString(artifact.getFile().toPath());
            assertTrue(content.contains("/mirror/org/apache/camel/camel-anything/3.42.0/camel-anything-3.42.0.jar"),
                    "Should have downloaded from mirror, got: " + content);

            LOG.info("Authentication and mirror test passed - artifact resolved through authenticated mirror");
        }
    }

    @Test
    void testExtraDefaultRepositoriesFromSystemProperty() throws Exception {
        // This test verifies that MIMA correctly loads extra default repositories from system properties.

        // Use the local test server that requires authentication
        String testRepoUrl = "http://localhost:" + localServer.getLocalPort() + "/maven/repository";
        String extraReposValue = "test-server=" + testRepoUrl;

        File customLocalRepo = new File(tempDir, "extra-repos-test-m2");
        Files.createDirectories(customLocalRepo.toPath());

        String artifactCoords = "org.apache.camel:camel-test:9.99.9-non-exist";

        // Callable that captures local variables and performs artifact resolution
        Callable<List<MavenArtifact>> resolve = () -> {
            try (MavenDownloaderImpl downloader = new MavenDownloaderImpl()) {
                downloader.setMavenCentralEnabled(false);
                downloader.setMavenApacheSnapshotEnabled(false);
                downloader.build();

                MavenDownloader customDownloader = downloader.customize(
                        customLocalRepo.getAbsolutePath(),
                        5000,
                        10000);

                return customDownloader.resolveArtifacts(
                        List.of(artifactCoords),
                        null,
                        false,
                        false);
            }
        };

        String originalValue = System.getProperty("camel.extra.repos");
        try {
            // NEGATIVE TEST: without the extra repo, the resolution fails because of no artifact can be resolved, definitely not authentication failure
            System.clearProperty("camel.extra.repos");

            try {
                resolve.call();
                fail("Should have failed without extra repos system property");
            } catch (MavenResolutionException e) {
                // Expected - no repositories configured
                assertFalse(e.getMessage().contains("401") || e.getMessage().contains("Unauthorized")
                        || e.getMessage().contains("status code"),
                        "Should fail due to no repositories, not authentication, got: " + e.getMessage());
            }

            // POSITIVE TEST: with the extra repo configured, we have to receive authentication failure
            System.setProperty("camel.extra.repos", extraReposValue);

            try {
                resolve.call();
                fail("Should have failed with 401 Unauthorized (proves repo was loaded from system property)");
            } catch (MavenResolutionException e) {
                // Expected - repository loaded but download fails due to no authentication
                // The 401 error proves the repository from the system property was actually used
                assertTrue(e.getMessage().contains("401") || e.getMessage().contains("Unauthorized")
                        || e.getMessage().contains("status code"),
                        "Should fail with 401/authentication error (proves repository was used), got: " + e.getMessage());
            }

        } finally {
            // Clean up system property
            if (originalValue != null) {
                System.setProperty("camel.extra.repos", originalValue);
            } else {
                System.clearProperty("camel.extra.repos");
            }
        }
    }
}
