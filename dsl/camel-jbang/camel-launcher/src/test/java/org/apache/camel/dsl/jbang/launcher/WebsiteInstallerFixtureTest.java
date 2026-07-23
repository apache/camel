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
package org.apache.camel.dsl.jbang.launcher;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManagerFactory;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Self-test for {@link WebsiteInstallerFixture}: confirms the HTTPS server enforces real certificate validation,
 * unregistered paths 404, manifests are exactly four lines, safe archives have a single root, and every malicious
 * archive contains the intended bad entry.
 */
class WebsiteInstallerFixtureTest {

    @Test
    void requiresTrustedHttps(@TempDir Path temp) throws Exception {
        try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp)) {
            fixture.publish("/probe", "ok".getBytes(StandardCharsets.UTF_8));
            HttpsURLConnection untrusted = (HttpsURLConnection) new URL(fixture.baseUrl() + "/probe").openConnection();
            assertThrows(SSLHandshakeException.class, untrusted::getResponseCode);
        }
    }

    @Test
    void unregisteredPathIs404(@TempDir Path temp) throws Exception {
        try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp)) {
            HttpsURLConnection conn = trustedConnection(fixture, "/does-not-exist");
            assertEquals(404, conn.getResponseCode());
        }
    }

    @Test
    void manifestHasExactFourLines(@TempDir Path temp) throws Exception {
        try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp)) {
            Path tar = fixture.safeTar("1.2.3");
            Path zip = fixture.safeZip("1.2.3");
            fixture.publishManifest("/camel-cli/releases/1.2.3.properties", "1.2.3", tar, zip);

            HttpsURLConnection conn = trustedConnection(fixture, "/camel-cli/releases/1.2.3.properties");
            assertEquals(200, conn.getResponseCode());
            String body;
            try (InputStream in = conn.getInputStream()) {
                body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            List<String> lines = body.lines().filter(line -> !line.isBlank()).toList();
            assertEquals(4, lines.size(), body);
            assertEquals("format=1", lines.get(0));
            assertEquals("version=1.2.3", lines.get(1));
            assertTrue(lines.get(2).matches("tar_sha256=[0-9a-f]{64}"), lines.get(2));
            assertTrue(lines.get(3).matches("zip_sha256=[0-9a-f]{64}"), lines.get(3));
        }
    }

    @Test
    void safeArchivesContainOneRoot(@TempDir Path temp) throws Exception {
        try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp)) {
            Path tar = fixture.safeTar("1.2.3");
            assertEquals(Set.of("camel-launcher-1.2.3"), topLevelEntries(readTarEntries(tar)));

            Path zip = fixture.safeZip("1.2.3");
            assertEquals(Set.of("camel-launcher-1.2.3"), topLevelEntries(readZipEntries(zip)));
        }
    }

    @Test
    void maliciousArchivesContainIntendedEntry(@TempDir Path temp) throws Exception {
        try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp)) {
            Path absoluteTar = fixture.maliciousTar("/etc/passwd");
            assertTrue(readTarEntries(absoluteTar).contains("/etc/passwd"));

            Path traversalTar = fixture.maliciousTar("../escape");
            assertTrue(readTarEntries(traversalTar).contains("../escape"));

            Path multiRootTar = fixture.maliciousTar("evil-root/payload");
            assertTrue(readTarEntries(multiRootTar).contains("evil-root/payload"));

            Path symlinkTar = fixture.maliciousTarSymlink("camel-launcher-9.9.9/escape-link", "../../outside");
            assertTrue(readTarEntries(symlinkTar).contains("camel-launcher-9.9.9/escape-link"));

            Path absoluteZip = fixture.maliciousZip("/etc/passwd");
            assertTrue(readZipEntries(absoluteZip).contains("/etc/passwd"));

            Path traversalZip = fixture.maliciousZip("../escape");
            assertTrue(readZipEntries(traversalZip).contains("../escape"));

            Path multiRootZip = fixture.maliciousZip("evil-root/payload");
            assertTrue(readZipEntries(multiRootZip).contains("evil-root/payload"));

            Path symlinkZip = fixture.maliciousZipSymlink("camel-launcher-9.9.9/escape-link", "../../outside");
            assertTrue(readZipEntries(symlinkZip).contains("camel-launcher-9.9.9/escape-link"));
        }
    }

    private static HttpsURLConnection trustedConnection(WebsiteInstallerFixture fixture, String path) throws Exception {
        HttpsURLConnection conn = (HttpsURLConnection) new URL(fixture.baseUrl() + path).openConnection();
        conn.setSSLSocketFactory(trustingContext(fixture.caCertificate()).getSocketFactory());
        return conn;
    }

    private static SSLContext trustingContext(Path caCertPem) throws Exception {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        Certificate certificate;
        try (InputStream in = Files.newInputStream(caCertPem)) {
            certificate = certificateFactory.generateCertificate(in);
        }
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        trustStore.setCertificateEntry("camel-installer-test", certificate);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, tmf.getTrustManagers(), new SecureRandom());
        return context;
    }

    private static List<String> readTarEntries(Path archive) throws Exception {
        List<String> names = new ArrayList<>();
        try (InputStream fis = Files.newInputStream(archive);
             GzipCompressorInputStream gzis = new GzipCompressorInputStream(fis);
             TarArchiveInputStream tais = new TarArchiveInputStream(gzis)) {
            TarArchiveEntry entry;
            while ((entry = tais.getNextEntry()) != null) {
                String name = entry.getName();
                names.add(name.endsWith("/") ? name.substring(0, name.length() - 1) : name);
            }
        }
        return names;
    }

    private static List<String> readZipEntries(Path archive) throws Exception {
        List<String> names = new ArrayList<>();
        try (ZipFile zipFile = new ZipFile(archive.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                names.add(name.endsWith("/") ? name.substring(0, name.length() - 1) : name);
            }
        }
        return names;
    }

    private static Set<String> topLevelEntries(List<String> names) {
        Set<String> roots = new LinkedHashSet<>();
        for (String name : names) {
            int slash = name.indexOf('/');
            roots.add(slash == -1 ? name : name.substring(0, slash));
        }
        return roots;
    }
}
