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

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
}
