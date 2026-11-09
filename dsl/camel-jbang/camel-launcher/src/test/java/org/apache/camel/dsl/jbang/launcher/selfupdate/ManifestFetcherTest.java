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

    // As the Apache snapshot repository publishes them (trimmed).
    static final String ROOT_METADATA = """
            <?xml version="1.0" encoding="UTF-8"?>
            <metadata>
              <groupId>org.apache.camel</groupId>
              <artifactId>camel-launcher</artifactId>
              <versioning>
                <latest>4.22.3-SNAPSHOT</latest>
                <release></release>
                <versions>
                  <version>4.14.10-SNAPSHOT</version>
                  <version>4.24.0-SNAPSHOT</version>
                  <version>4.22.3-SNAPSHOT</version>
                  <version>4.9.0</version>
                </versions>
              </versioning>
            </metadata>
            """;

    static String versionMetadata(String snapshotVersion, String build) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata modelVersion="1.1.0">
                  <groupId>org.apache.camel</groupId>
                  <artifactId>camel-launcher</artifactId>
                  <version>%1$s</version>
                  <versioning>
                    <snapshot>
                      <timestamp>20261108.143653</timestamp>
                      <buildNumber>24</buildNumber>
                    </snapshot>
                    <snapshotVersions>
                      <snapshotVersion>
                        <extension>pom</extension>
                        <value>%2$s</value>
                      </snapshotVersion>
                      <snapshotVersion>
                        <classifier>bin</classifier>
                        <extension>zip</extension>
                        <value>%2$s</value>
                      </snapshotVersion>
                      <snapshotVersion>
                        <classifier>bin</classifier>
                        <extension>tar.gz</extension>
                        <value>%2$s</value>
                      </snapshotVersion>
                    </snapshotVersions>
                  </versioning>
                </metadata>
                """.formatted(snapshotVersion, build);
    }

    @Test
    void latestSnapshotVersionIsTheHighestNotTheLastDeployed() {
        // <latest> names whatever deployed last, which can be a maintenance branch
        assertThat(ManifestFetcher.parseLatestSnapshotVersion(ROOT_METADATA.getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("4.24.0-SNAPSHOT");
    }

    @Test
    void snapshotVersionsAreTheSnapshotEntriesInListedOrder() {
        assertThat(ManifestFetcher.parseSnapshotVersions(ROOT_METADATA.getBytes(StandardCharsets.UTF_8)))
                .containsExactly("4.14.10-SNAPSHOT", "4.24.0-SNAPSHOT", "4.22.3-SNAPSHOT");
    }

    @Test
    void latestSnapshotVersionRequiresOne() {
        String metadata = "<metadata><versioning><versions><version>4.9.0</version></versions></versioning></metadata>";

        assertThatThrownBy(() -> ManifestFetcher.parseLatestSnapshotVersion(metadata.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(SelfUpdateException.class)
                .hasMessageContaining("no X.Y.Z-SNAPSHOT version");
    }

    @Test
    void newestBuildIsTheValueOfTheLauncherArchive() {
        byte[] metadata = versionMetadata("4.24.0-SNAPSHOT", "4.24.0-20261108.143653-24").getBytes(StandardCharsets.UTF_8);

        assertThat(ManifestFetcher.parseNewestBuild(metadata, "4.24.0-SNAPSHOT")).isEqualTo("4.24.0-20261108.143653-24");
    }

    // Nexus 2 serves this Maven 2 metadata to the JDK HTTP client: no <snapshotVersions>
    @Test
    void newestBuildFromMaven2Metadata() {
        String metadata = """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata>
                  <groupId>org.apache.camel</groupId>
                  <artifactId>camel-launcher</artifactId>
                  <version>4.24.0-SNAPSHOT</version>
                  <versioning>
                    <snapshot>
                      <timestamp>20261108.143653</timestamp>
                      <buildNumber>24</buildNumber>
                    </snapshot>
                    <lastUpdated>20261108143653</lastUpdated>
                  </versioning>
                </metadata>
                """;

        assertThat(ManifestFetcher.parseNewestBuild(metadata.getBytes(StandardCharsets.UTF_8), "4.24.0-SNAPSHOT"))
                .isEqualTo("4.24.0-20261108.143653-24");
    }

    // a bin tar.gz entry without a <value> falls back to the <snapshot>, as when there is no entry
    @Test
    void newestBuildFromTheSnapshotWhenTheLauncherArchiveHasNoValue() {
        String metadata = """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata modelVersion="1.1.0">
                  <version>4.24.0-SNAPSHOT</version>
                  <versioning>
                    <snapshot>
                      <timestamp>20261108.143653</timestamp>
                      <buildNumber>24</buildNumber>
                    </snapshot>
                    <snapshotVersions>
                      <snapshotVersion>
                        <classifier>bin</classifier>
                        <extension>tar.gz</extension>
                      </snapshotVersion>
                    </snapshotVersions>
                  </versioning>
                </metadata>
                """;

        assertThat(ManifestFetcher.parseNewestBuild(metadata.getBytes(StandardCharsets.UTF_8), "4.24.0-SNAPSHOT"))
                .isEqualTo("4.24.0-20261108.143653-24");
    }

    @ParameterizedTest
    @ValueSource(strings = { "20261108.143653</timestamp><x>", "2026/../../x", "" })
    void rejectsAnInvalidMaven2Snapshot(String timestamp) {
        String metadata = "<metadata><versioning><snapshot><timestamp>" + timestamp
                          + "</timestamp><buildNumber>24</buildNumber></snapshot></versioning></metadata>";

        assertThatThrownBy(() -> ManifestFetcher.parseNewestBuild(metadata.getBytes(StandardCharsets.UTF_8),
                "4.24.0-SNAPSHOT"))
                .isInstanceOf(SelfUpdateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { "4.24.0-20261108.143653-24/../../x", "4.22.3-20261108.143653-24", "4.24.0-SNAPSHOT", "" })
    void rejectsAnInvalidOrForeignBuild(String build) {
        byte[] metadata = versionMetadata("4.24.0-SNAPSHOT", build).getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> ManifestFetcher.parseNewestBuild(metadata, "4.24.0-SNAPSHOT"))
                .isInstanceOf(SelfUpdateException.class)
                .hasMessageContaining("invalid nightly build of 4.24.0-SNAPSHOT");
    }

    @Test
    void rejectsMetadataWithADoctype() {
        String metadata = "<?xml version=\"1.0\"?><!DOCTYPE metadata [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>"
                          + "<metadata><versioning><versions><version>&x;</version></versions></versioning></metadata>";

        assertThatThrownBy(() -> ManifestFetcher.parseLatestSnapshotVersion(metadata.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(SelfUpdateException.class)
                .hasMessageContaining("the snapshot metadata is not valid");
    }

    @Test
    void fetchesTheNewestBuildOverHttp() throws Exception {
        HttpServer server = serve(Map.of(
                "/snapshots/maven-metadata.xml", ROOT_METADATA,
                "/snapshots/4.24.0-SNAPSHOT/maven-metadata.xml",
                versionMetadata("4.24.0-SNAPSHOT", "4.24.0-20261108.143653-24")));
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            ManifestFetcher fetcher = new ManifestFetcher(base + "/releases", base + "/maven2", base + "/snapshots");

            String snapshotVersion = fetcher.fetchLatestSnapshotVersion();

            assertThat(snapshotVersion).isEqualTo("4.24.0-SNAPSHOT");
            assertThat(fetcher.fetchNewestBuild(snapshotVersion)).isEqualTo("4.24.0-20261108.143653-24");
            assertThatThrownBy(() -> fetcher.fetchNewestBuild("4.22.3-SNAPSHOT"))
                    .isInstanceOf(SelfUpdateException.class)
                    .hasMessageContaining("no nightly builds of 4.22.3-SNAPSHOT at");
            assertThatThrownBy(() -> fetcher.fetchNewestBuild("../x"))
                    .isInstanceOf(IllegalArgumentException.class);
        } finally {
            server.stop(0);
        }
    }
}
