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
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Stream;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManifestFetcherTest {

    private static final String HASH = "a".repeat(64);

    static Stream<Arguments> invalidManifests() {
        return Stream.of(
                Arguments.of("missing-key",
                        "format=1\nversion=1.0.0\ntar_sha256=" + HASH + "\n"),
                Arguments.of("duplicate-key",
                        "format=1\nformat=1\nversion=1.0.0\ntar_sha256=" + HASH + "\nzip_sha256=" + HASH + "\n"),
                Arguments.of("blank-line",
                        "format=1\n\nversion=1.0.0\ntar_sha256=" + HASH + "\nzip_sha256=" + HASH + "\n"),
                Arguments.of("unknown-key",
                        "format=1\nversion=1.0.0\ntar_sha256=" + HASH + "\nzip_sha256=" + HASH + "\nextra=1\n"),
                Arguments.of("bad-format",
                        "format=2\nversion=1.0.0\ntar_sha256=" + HASH + "\nzip_sha256=" + HASH + "\n"),
                Arguments.of("bad-version",
                        "format=1\nversion=1.0\ntar_sha256=" + HASH + "\nzip_sha256=" + HASH + "\n"),
                Arguments.of("bad-tar-hash",
                        "format=1\nversion=1.0.0\ntar_sha256=not-hex\nzip_sha256=" + HASH + "\n"),
                Arguments.of("bad-zip-hash",
                        "format=1\nversion=1.0.0\ntar_sha256=" + HASH + "\nzip_sha256=short"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidManifests")
    void rejectsInvalidManifest(String name, String content) {
        assertThatThrownBy(() -> ManifestFetcher.parse(content.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(SelfUpdateException.class);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidManifests")
    void rejectsCrlfVariantOfInvalidManifest(String name, String content) {
        String crlf = content.replace("\n", "\r\n");
        assertThatThrownBy(() -> ManifestFetcher.parse(crlf.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(SelfUpdateException.class);
    }

    @Test
    void parsesValidManifest() {
        String content = "format=1\nversion=4.22.0\ntar_sha256=" + HASH + "\nzip_sha256=" + HASH + "\n";

        ManifestFetcher.Manifest manifest = ManifestFetcher.parse(content.getBytes(StandardCharsets.UTF_8));

        assertThat(manifest.version()).isEqualTo("4.22.0");
        assertThat(manifest.tarSha256()).isEqualTo(HASH);
        assertThat(manifest.zipSha256()).isEqualTo(HASH);
    }

    @Test
    void parsesValidManifestWithCrlf() {
        String content = ("format=1\nversion=4.22.0\ntar_sha256=" + HASH + "\nzip_sha256=" + HASH + "\n")
                .replace("\n", "\r\n");

        ManifestFetcher.Manifest manifest = ManifestFetcher.parse(content.getBytes(StandardCharsets.UTF_8));

        assertThat(manifest.version()).isEqualTo("4.22.0");
    }

    @Test
    void parsesManifestWithAsfLicenseHeader() {
        // Mirrors the header WebsiteManifestGenerator.renderManifest() prepends to every published
        // manifest; the '#' lines (including a bare '##') must not count toward the four-line total.
        String header = "## ---------------------------------------------------------------------------\n"
                        + "## Licensed to the Apache Software Foundation (ASF) under one or more\n"
                        + "## contributor license agreements.  See the NOTICE file distributed with\n"
                        + "##\n"
                        + "## ---------------------------------------------------------------------------\n";
        String content = header + "format=1\nversion=4.22.0\ntar_sha256=" + HASH + "\nzip_sha256=" + HASH + "\n";

        ManifestFetcher.Manifest manifest = ManifestFetcher.parse(content.getBytes(StandardCharsets.UTF_8));

        assertThat(manifest.version()).isEqualTo("4.22.0");
        assertThat(manifest.tarSha256()).isEqualTo(HASH);
        assertThat(manifest.zipSha256()).isEqualTo(HASH);
    }

    @Test
    void fromEnvironmentUsesProductionDefaultsWhenUnset() {
        ManifestFetcher fetcher = ManifestFetcher.fromEnvironment();

        assertThat(fetcher.manifestBaseUrl())
                .isEqualTo("https://camel.apache.org/camel-cli/releases");
        assertThat(fetcher.mavenBaseUrl())
                .isEqualTo("https://repo1.maven.org/maven2/org/apache/camel/camel-launcher");
    }

    static String manifest(String version) {
        return "format=1\nversion=" + version + "\ntar_sha256=" + HASH + "\nzip_sha256=" + HASH + "\n";
    }

    /** Serves {@code files} (URL path to body) over plain loopback HTTP; any other path is a 404. */
    static HttpServer serve(Map<String, String> files) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            try {
                String body = files.get(exchange.getRequestURI().getPath());
                if (body == null) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            } finally {
                exchange.close();
            }
        });
        server.start();
        return server;
    }

    static ManifestFetcher fetcherFor(HttpServer server) {
        return new ManifestFetcher(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/releases",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/maven2");
    }

    @Test
    void fetchesLtsAndReleaseLineAliases() throws Exception {
        HttpServer server = serve(Map.of(
                "/releases/lts.properties", manifest("4.22.2"),
                "/releases/4.23.properties", manifest("4.23.1"),
                "/releases/latest.properties", manifest("4.24.0")));
        try {
            ManifestFetcher fetcher = fetcherFor(server);

            assertThat(fetcher.fetchAlias("lts").version()).isEqualTo("4.22.2");
            assertThat(fetcher.fetchAlias("LTS").version()).isEqualTo("4.22.2");
            assertThat(fetcher.fetchAlias("4.23").version()).isEqualTo("4.23.1");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void reportsMissingAliasClearly() throws Exception {
        HttpServer server = serve(Map.of("/releases/latest.properties", manifest("4.24.0")));
        try {
            ManifestFetcher fetcher = fetcherFor(server);

            assertThatThrownBy(() -> fetcher.fetchAlias("lts"))
                    .isInstanceOf(SelfUpdateException.class)
                    .hasMessageContaining("no manifest for LTS at http://127.0.0.1:")
                    .hasMessageEndingWith("/releases/lts.properties; the website may not publish aliases yet");
            assertThatThrownBy(() -> fetcher.fetchAlias("4.22"))
                    .isInstanceOf(SelfUpdateException.class)
                    .hasMessageContaining("no manifest for 4.22 at");
        } finally {
            server.stop(0);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "4", "4.22.2", "latest", "../lts", "4.22/../x" })
    void rejectsNonAliasNames(String name) {
        ManifestFetcher fetcher = new ManifestFetcher("http://127.0.0.1:1/releases", "http://127.0.0.1:1/maven2");

        assertThatThrownBy(() -> fetcher.fetchAlias(name)).isInstanceOf(IllegalArgumentException.class);
    }
}
