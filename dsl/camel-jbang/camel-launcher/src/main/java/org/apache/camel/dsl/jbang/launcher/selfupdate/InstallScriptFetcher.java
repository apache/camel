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
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

import org.apache.camel.util.FileUtil;

/**
 * Downloads and checksum-verifies the platform-appropriate installer script ({@code install.sh} on POSIX,
 * {@code install.ps1} on Windows) that {@code SelfUpdateCommand} delegates the actual update to. Verified against
 * {@code install.sha256}, a small companion file published alongside the scripts (see {@code WebsiteManifestGenerator})
 * - not part of the existing {@code latest.properties}/{@code X.Y.Z.properties} manifest format, which neither script's
 * own strict parser could tolerate being extended with new keys.
 */
public final class InstallScriptFetcher {

    private static final String DEFAULT_INSTALL_BASE_URL = "https://camel.apache.org";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final String installBaseUrl;
    private final HttpClient httpClient;

    public InstallScriptFetcher(String installBaseUrl) {
        this(installBaseUrl, HttpClient.newBuilder().proxy(ProxySelector.getDefault()).build());
    }

    // Visible for testing: SelfUpdateIntegrationTest injects an HttpClient whose SSLContext trusts the
    // WebsiteInstallerFixture's self-signed certificate authority, since the platform default trust store
    // (used by the one-arg constructor above) has no way to know about a test-only CA. Production callers
    // only ever use the one-arg constructor.
    public InstallScriptFetcher(String installBaseUrl, HttpClient httpClient) {
        this.installBaseUrl = installBaseUrl;
        this.httpClient = httpClient;
    }

    public static InstallScriptFetcher fromEnvironment() {
        String installBaseUrl = System.getenv().getOrDefault("CAMEL_SELF_UPDATE_INSTALL_BASE_URL",
                DEFAULT_INSTALL_BASE_URL);
        return new InstallScriptFetcher(installBaseUrl);
    }

    public String installBaseUrl() {
        return installBaseUrl;
    }

    public Path fetch(Path stagingDir) {
        String scriptName = FileUtil.isWindows() ? "install.ps1" : "install.sh";
        String checksumKey = FileUtil.isWindows() ? "install_ps1_sha256" : "install_sh_sha256";

        Map<String, String> checksums = parseChecksums(get(installBaseUrl + "/install.sha256"));
        String expected = checksums.get(checksumKey);
        if (expected == null) {
            throw new SelfUpdateException("install.sha256 is missing " + checksumKey);
        }

        byte[] scriptBytes = get(installBaseUrl + "/" + scriptName);
        String actual = sha256Hex(scriptBytes);
        if (!actual.equals(expected)) {
            throw new SelfUpdateException("checksum mismatch for downloaded " + scriptName);
        }

        try {
            Path scriptPath = stagingDir.resolve(scriptName);
            Files.write(scriptPath, scriptBytes);
            return scriptPath;
        } catch (IOException e) {
            throw new SelfUpdateException("failed to stage " + scriptName, e);
        }
    }

    private byte[] get(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new SelfUpdateException("failed to download " + url + " (HTTP " + response.statusCode() + ")");
            }
            return response.body();
        } catch (SelfUpdateException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            throw new SelfUpdateException("failed to download " + url, e);
        }
    }

    // Package-visible for direct unit testing without a network round-trip. Exactly 2 non-blank key=value lines,
    // no duplicate/unknown keys, both values 64-char lowercase hex - the same validation shape ManifestFetcher.parse
    // already applies to tar_sha256/zip_sha256.
    static Map<String, String> parseChecksums(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        String[] rawLines = text.split("\n", -1);
        int lineCount = rawLines.length > 0 && rawLines[rawLines.length - 1].isEmpty()
                ? rawLines.length - 1
                : rawLines.length;
        if (lineCount != 2) {
            throw new SelfUpdateException("install.sha256 must contain exactly two lines");
        }

        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < lineCount; i++) {
            String line = rawLines[i];
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            int eq = line.indexOf('=');
            if (eq <= 0 || eq == line.length() - 1) {
                throw new SelfUpdateException("install.sha256 contains a blank line");
            }
            String key = line.substring(0, eq);
            String value = line.substring(eq + 1);
            if (!Set.of("install_sh_sha256", "install_ps1_sha256").contains(key)) {
                throw new SelfUpdateException("install.sha256 has unknown key: " + key);
            }
            if (values.containsKey(key)) {
                throw new SelfUpdateException("install.sha256 has duplicate key: " + key);
            }
            if (!value.matches("[0-9a-f]{64}")) {
                throw new SelfUpdateException(key + " is not a 64-character lowercase hex value");
            }
            values.put(key, value);
        }
        return values;
    }

    private static String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JDK and must always be available.", e);
        }
    }
}
