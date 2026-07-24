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
package org.apache.camel.dsl.jbang.launcher.selfupdate;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fetches and parses the website-installer manifest ({@code latest.properties}/{@code X.Y.Z.properties}). Mirrors
 * {@code install.sh}'s {@code parse_manifest} exactly, in Java. Used only for the pre-flight version compare that
 * neither {@code install.sh} nor {@code install.ps1} perform on their own -- the actual archive download, checksum
 * verification, and extraction happen inside whichever of those two scripts {@code SelfUpdateCommand} delegates to.
 */
public final class ManifestFetcher {

    private static final String DEFAULT_MANIFEST_BASE_URL = "https://camel.apache.org/camel-cli/releases";
    private static final String DEFAULT_MAVEN_BASE_URL = "https://repo1.maven.org/maven2/org/apache/camel/camel-launcher";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    public record Manifest(String version, String tarSha256, String zipSha256) {
    }

    private final String manifestBaseUrl;
    private final String mavenBaseUrl;
    private final HttpClient httpClient;

    public ManifestFetcher(String manifestBaseUrl, String mavenBaseUrl) {
        this(manifestBaseUrl, mavenBaseUrl, HttpClient.newBuilder().proxy(ProxySelector.getDefault()).build());
    }

    // Visible for testing: SelfUpdateIntegrationTest injects an HttpClient whose SSLContext trusts the
    // WebsiteInstallerFixture's self-signed certificate authority, since the platform default trust store
    // (used by the two-arg constructor above) has no way to know about a test-only CA. Production callers
    // only ever use the two-arg constructor.
    public ManifestFetcher(String manifestBaseUrl, String mavenBaseUrl, HttpClient httpClient) {
        this.manifestBaseUrl = manifestBaseUrl;
        this.mavenBaseUrl = mavenBaseUrl;
        this.httpClient = httpClient;
    }

    /**
     * Production wiring: {@code CAMEL_SELF_UPDATE_MANIFEST_BASE_URL}/{@code CAMEL_SELF_UPDATE_MAVEN_BASE_URL} override
     * the defaults, mirroring {@code install.sh}'s {@code CAMEL_INSTALL_*} test seams under a distinct name so
     * overriding one never accidentally affects the other. Production installs never set either.
     */
    public static ManifestFetcher fromEnvironment() {
        String manifestBaseUrl = System.getenv().getOrDefault("CAMEL_SELF_UPDATE_MANIFEST_BASE_URL",
                DEFAULT_MANIFEST_BASE_URL);
        String mavenBaseUrl = System.getenv().getOrDefault("CAMEL_SELF_UPDATE_MAVEN_BASE_URL",
                DEFAULT_MAVEN_BASE_URL);
        return new ManifestFetcher(manifestBaseUrl, mavenBaseUrl);
    }

    public String manifestBaseUrl() {
        return manifestBaseUrl;
    }

    public String mavenBaseUrl() {
        return mavenBaseUrl;
    }

    public Manifest fetchLatest() {
        return fetchManifest(manifestBaseUrl + "/latest.properties");
    }

    public Manifest fetch(String version) {
        return fetchManifest(manifestBaseUrl + "/" + version + ".properties");
    }

    private Manifest fetchManifest(String url) {
        return parse(get(url));
    }

    private byte[] get(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(CONNECT_TIMEOUT).build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new SelfUpdateException(
                        "failed to download manifest from " + url + " (HTTP "
                                              + response.statusCode() + ")");
            }
            return response.body();
        } catch (SelfUpdateException e) {
            throw e;
        } catch (IOException e) {
            throw new SelfUpdateException("failed to download manifest from " + url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SelfUpdateException("failed to download manifest from " + url, e);
        }
    }

    // Package-visible for direct unit testing without a network round-trip. Mirrors install.sh's parse_manifest:
    // exactly 4 non-blank key=value lines, no duplicate/unknown keys, format=1, X.Y.Z version, 64-char lowercase
    // hex hashes. CRLF-tolerant (a trailing '\r' is stripped per line) because install.ps1 may have produced or
    // re-served the same manifest. '#' comment lines (e.g. the ASF license header WebsiteManifestGenerator
    // prepends) are skipped and don't count toward the four-line total, matching install.sh/install.ps1 and
    // WebsiteManifestGenerator's own parseStrictManifest.
    static Manifest parse(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        String[] rawLines = text.split("\n", -1);
        int rawLineCount = rawLines.length > 0 && rawLines[rawLines.length - 1].isEmpty()
                ? rawLines.length - 1
                : rawLines.length;

        List<String> dataLines = new ArrayList<>();
        for (int i = 0; i < rawLineCount; i++) {
            String line = rawLines[i];
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            if (line.startsWith("#")) {
                continue;
            }
            dataLines.add(line);
        }
        if (dataLines.size() != 4) {
            throw new SelfUpdateException("manifest must contain exactly four lines");
        }

        Map<String, String> values = new HashMap<>();
        for (String line : dataLines) {
            int eq = line.indexOf('=');
            if (eq <= 0 || eq == line.length() - 1) {
                throw new SelfUpdateException("manifest contains a blank line");
            }
            String key = line.substring(0, eq);
            String value = line.substring(eq + 1);
            if (!Set.of("format", "version", "tar_sha256", "zip_sha256").contains(key)) {
                throw new SelfUpdateException("manifest has unknown key: " + key);
            }
            if (values.containsKey(key)) {
                throw new SelfUpdateException("manifest has duplicate key: " + key);
            }
            values.put(key, value);
        }

        for (String required : List.of("format", "version", "tar_sha256", "zip_sha256")) {
            if (!values.containsKey(required)) {
                throw new SelfUpdateException("manifest is missing a required key");
            }
        }

        if (!"1".equals(values.get("format"))) {
            throw new SelfUpdateException("unsupported manifest format: " + values.get("format"));
        }
        String version = values.get("version");
        if (!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) {
            throw new SelfUpdateException("manifest version is not a valid X.Y.Z value");
        }
        validateSha256(values.get("tar_sha256"), "manifest tar_sha256");
        validateSha256(values.get("zip_sha256"), "manifest zip_sha256");

        return new Manifest(version, values.get("tar_sha256"), values.get("zip_sha256"));
    }

    private static void validateSha256(String value, String label) {
        if (!value.matches("[0-9a-f]{64}")) {
            throw new SelfUpdateException(label + " is not a 64-character lowercase hex value");
        }
    }
}
