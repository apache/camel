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
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.apache.commons.codec.binary.Hex;
import org.apache.http.Header;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void testExtraDefaultRepositoriesFromSystemProperty() throws Exception {
        // This test verifies that MIMA correctly loads extra default repositories from system properties.

        // Use the local test server that requires authentication
        String testRepoUrl = "http://localhost:" + localServer.getLocalPort() + "/maven/repository";

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
            System.setProperty("camel.extra.repos", testRepoUrl);

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
