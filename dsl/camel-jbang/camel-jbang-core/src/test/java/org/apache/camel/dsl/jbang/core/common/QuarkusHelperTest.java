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
package org.apache.camel.dsl.jbang.core.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.apache.camel.dsl.jbang.core.common.QuarkusHelper.CamelVersionInPlatformRelease;
import org.apache.camel.dsl.jbang.core.common.QuarkusHelper.MajorMinor;
import org.apache.camel.dsl.jbang.core.common.QuarkusHelper.QuarkusPlatformBom;
import org.apache.camel.tooling.maven.MavenArtifact;
import org.apache.camel.tooling.maven.MavenGav;
import org.apache.camel.util.json.DeserializationException;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class QuarkusHelperTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    @Test
    void findPlatformVersion() throws IOException, DeserializationException {

        // a local copy of https://registry.quarkus.io/client/platforms
        String src = Files.readString(Path.of("target/test-classes/QuarkusHelperTest/quarkus-registry-client-platforms.json"));
        JsonObject json = (JsonObject) Jsoner.deserialize(src);
        JsonArray platforms = json.getCollection("platforms");
        JsonObject platform = platforms.getMap(0);
        JsonArray streams = platform.getCollection("streams");

        // BOM 3.27.3.1 has Camel 4.14.5 and there is no other one with Camel 4.14.x so a BOM with newer Camel is returned
        assertPlatformVersion(streams, "4.14.0", "3.27.3.1");
        // perfect match
        assertPlatformVersion(streams, "4.14.5", "3.27.3.1");
        // BOM 3.27.3.1 has Camel 4.14.5 and there is no other one with Camel 4.14.x so a BOM with older Camel is returned
        assertPlatformVersion(streams, "4.14.10", "3.27.3.1");

        assertPlatformVersion(streams, "4.18.0", "3.33.1.1");

        assertPlatformVersion(streams, "4.20.0", "3.35.2");
        // There is no BOM with Camel 5.x so a BOM with latest Camel is returned
        assertPlatformVersion(streams, "5.0.0", "3.35.2");

        assertPlatformVersion(streams, "3.16.0", "2.8.0.Final");
        // There is no BOM with Camel 3.17 so a BOM with an older Camel 3.x is returned
        assertPlatformVersion(streams, "3.17.0", "2.8.0.Final");
    }

    private void assertPlatformVersion(JsonArray streams, String camelVersion, String expectedPlatformVersion) {
        Optional<String> resolved = QuarkusHelper.findPlatformBom(
                streams, new MajorMinor(camelVersion),
                QuarkusHelperTest::resolve,
                RuntimeType.QUARKUS_EXTENSION_REGISTRY_BASE_URL)
                .map(QuarkusPlatformBom::quarkusCamelBom)
                .map(MavenGav::getVersion);
        Assertions.assertThat(resolved).isPresent().contains(expectedPlatformVersion);
    }

    static MavenArtifact resolve(MavenGav resolverGatv) {
        MavenArtifact result = new MavenArtifact(
                resolverGatv,
                Path.of("target/test-classes/QuarkusHelperTest/quarkus-camel-bom-" + resolverGatv.getVersion() + ".pom.xml")
                        .toFile());
        return result;
    }

    @Test
    void versionDistance() {
        MajorMinor wantedCamelVersion = new MajorMinor("2.3.0");
        assertDistance("2.3.0", "2.3.0", 0);
        assertDistance("2.3.0", "2.4.0", 1);
        List<String> sorted = Stream.of("2.3.0",
                "2.4.0",
                "2.5.0",
                "2.2.0",
                "2.1.0",
                "3.0.0",
                "3.1.0",
                "4.0.0",
                "4.1.0",
                "1.2.0",
                "1.1.0",
                "0.2.0",
                "0.1.0")
                .map(MajorMinor::new)
                .map(camelVersion -> new CamelVersionInPlatformRelease(
                        camelVersion.toString(),
                        camelVersion,
                        wantedCamelVersion.distanceTo(camelVersion),
                        new ComparableVersion(camelVersion.toString()),
                        MavenGav.fromCoordinates("io.quarkus.platform", "quarkus-camel-bom", camelVersion.toString() + ".0",
                                "pom", null),
                        RuntimeType.QUARKUS_EXTENSION_REGISTRY_BASE_URL))
                .sorted()
                .map(CamelVersionInPlatformRelease::camelMajorMinor)
                .map(MajorMinor::toString)
                .toList();

        Assertions.assertThat(sorted).containsExactly(
                "2.3.0",
                "2.4.0",
                "2.5.0",
                "2.2.0",
                "2.1.0",
                "3.0.0",
                "3.1.0",
                "4.0.0",
                "4.1.0",
                "1.2.0",
                "1.1.0",
                "0.2.0",
                "0.1.0");
    }

    private void assertDistance(String a, String b, long expected) {
        Assertions.assertThat(new MajorMinor(a).distanceTo(new MajorMinor(b))).isEqualTo(expected);
    }

    @Test
    void cache() throws IOException {
        final Path registriesDir
                = Path.of("target/" + QuarkusHelperTest.class.getSimpleName() + "-" + UUID.randomUUID() + "/registries");
        Files.createDirectories(registriesDir);
        final String allPlatformsJson
                = Files.readString(Path.of("target/test-classes/registry.quarkus.io/client/platforms/all.json"));
        wireMock.stubFor(WireMock.get(WireMock.urlEqualTo("/client/platforms/all"))
                .willReturn(WireMock.ok()
                        .withHeader("Content-Type", "application/json")
                        .withBody(allPlatformsJson)));
        JsonArray arr = QuarkusHelper.fetchPlatformStreams(wireMock.baseUrl(), true, false, registriesDir);

        Assertions.assertThat(registriesDir.resolve("localhost/client/platforms/all.json")).hasContent(allPlatformsJson);

        wireMock.stubFor(WireMock.get(WireMock.urlEqualTo("/client/platforms/all"))
                .willReturn(WireMock.forbidden()));
        /* Another call should hit the cache instead of getting from the server */
        JsonArray arr2 = QuarkusHelper.fetchPlatformStreams(wireMock.baseUrl(), true, false, registriesDir);
        Assertions.assertThat(arr2).isEqualTo(arr);
    }

    private static final String CAMEL_4_14_5_PLATFORM = "3.27.3.1";

    private static Path newRegistriesDir() throws IOException {
        Path registriesDir
                = Path.of("target/" + QuarkusHelperTest.class.getSimpleName() + "-" + UUID.randomUUID() + "/registries");
        Files.createDirectories(registriesDir);
        return registriesDir;
    }

    private static Path mappingFile(Path registriesDir) {
        return registriesDir.resolve("localhost/client/platforms/platform-mapping.json");
    }

    private static Path registryCacheFile(Path registriesDir) {
        return registriesDir.resolve("localhost/client/platforms/all.json");
    }

    /** Serves the registry document matching the BOM fixtures of this test and forgets earlier requests. */
    private static void stubRegistry() throws IOException {
        wireMock.resetAll();
        wireMock.stubFor(WireMock.get(WireMock.urlEqualTo("/client/platforms/all"))
                .willReturn(WireMock.ok()
                        .withHeader("Content-Type", "application/json")
                        .withBody(Files.readString(
                                Path.of("target/test-classes/QuarkusHelperTest/quarkus-registry-client-platforms.json")))));
    }

    private static void verifyRegistryRequests(int count) {
        wireMock.verify(count, WireMock.getRequestedFor(WireMock.urlEqualTo("/client/platforms/all")));
    }

    private static QuarkusPlatformBom find(
            String camelVersion, boolean download, boolean fresh, Path registriesDir) {
        return find(camelVersion, QuarkusHelperTest::resolve, download, fresh, registriesDir);
    }

    private static QuarkusPlatformBom find(
            String camelVersion, Function<MavenGav, MavenArtifact> mavenResolver, boolean download, boolean fresh,
            Path registriesDir) {
        return QuarkusHelper.findQuarkusPlatformBom(
                camelVersion, mavenResolver, download, wireMock.baseUrl(), fresh, registriesDir);
    }

    private static void backdate(Path file) throws IOException {
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().minus(Duration.ofDays(2))));
    }

    @Test
    void releasedVersionIsCachedOnFirstResolution() throws Exception {
        stubRegistry();
        Path registriesDir = newRegistriesDir();

        QuarkusPlatformBom bom = find("4.14.5", true, false, registriesDir);

        Assertions.assertThat(bom).isEqualTo(
                new QuarkusPlatformBom("io.quarkus.platform", CAMEL_4_14_5_PLATFORM, "4.14.5", wireMock.baseUrl()));
        JsonObject mappings = (JsonObject) Jsoner.deserialize(Files.readString(mappingFile(registriesDir)));
        Map<String, Object> entry = mappings.getMap("4.14.5");
        Assertions.assertThat(entry)
                .containsEntry("groupId", "io.quarkus.platform")
                .containsEntry("version", CAMEL_4_14_5_PLATFORM)
                .hasSize(2);
    }

    @Test
    void cachedMappingSkipsRegistryAndMaven() throws Exception {
        stubRegistry();
        Path registriesDir = newRegistriesDir();
        QuarkusPlatformBom first = find("4.14.5", true, false, registriesDir);

        Files.delete(registryCacheFile(registriesDir));
        wireMock.resetRequests();
        QuarkusPlatformBom second = find("4.14.5", platform -> {
            throw new IllegalStateException("Maven should not be asked for " + platform);
        }, true, false, registriesDir);

        Assertions.assertThat(second).isEqualTo(first);
        verifyRegistryRequests(0);
    }

    @Test
    void cachedMappingIgnoresRegistryAge() throws Exception {
        stubRegistry();
        Path registriesDir = newRegistriesDir();
        QuarkusPlatformBom first = find("4.14.5", true, false, registriesDir);

        backdate(registryCacheFile(registriesDir));
        backdate(mappingFile(registriesDir));
        QuarkusPlatformBom second = find("4.14.5", true, false, registriesDir);

        Assertions.assertThat(second).isEqualTo(first);
        verifyRegistryRequests(1);
    }

    @Test
    void nonExactMatchIsNotCached() throws Exception {
        stubRegistry();
        Path registriesDir = newRegistriesDir();

        // BOM 3.27.3.1 has Camel 4.14.5, so it is only a fallback for 4.14.0
        QuarkusPlatformBom bom = find("4.14.0", true, false, registriesDir);
        Assertions.assertThat(bom.version()).isEqualTo(CAMEL_4_14_5_PLATFORM);
        Assertions.assertThat(bom.camelVersion()).isEqualTo("4.14.5");
        Assertions.assertThat(mappingFile(registriesDir)).doesNotExist();

        backdate(registryCacheFile(registriesDir));
        find("4.14.0", true, false, registriesDir);
        verifyRegistryRequests(2);
    }

    @Test
    void snapshotIsNeverCachedNorLookedUp() throws Exception {
        stubRegistry();
        Path registriesDir = newRegistriesDir();
        Path mappingFile = mappingFile(registriesDir);
        Files.createDirectories(mappingFile.getParent());
        String planted = "{\"4.14.5-SNAPSHOT\":{\"groupId\":\"planted\",\"version\":\"0\"}}";
        Files.writeString(mappingFile, planted);

        QuarkusPlatformBom bom = find("4.14.5-SNAPSHOT", true, false, registriesDir);

        Assertions.assertThat(bom.version()).isEqualTo(CAMEL_4_14_5_PLATFORM);
        Assertions.assertThat(mappingFile).hasContent(planted);
        verifyRegistryRequests(1);
    }

    @Test
    void freshDropsAndRebuildsMapping() throws Exception {
        stubRegistry();
        Path registriesDir = newRegistriesDir();
        Path mappingFile = mappingFile(registriesDir);
        Files.createDirectories(mappingFile.getParent());
        Files.writeString(mappingFile, "{\"4.14.5\":{\"groupId\":\"planted\",\"version\":\"0\"}}");

        // the planted entry is not trusted: the registry is asked and the entry rebuilt
        QuarkusPlatformBom bom = find("4.14.5", true, true, registriesDir);
        Assertions.assertThat(bom.groupId()).isEqualTo("io.quarkus.platform");
        Assertions.assertThat(bom.version()).isEqualTo(CAMEL_4_14_5_PLATFORM);
        Assertions.assertThat(mappingFile).content().contains(CAMEL_4_14_5_PLATFORM).doesNotContain("planted");
        verifyRegistryRequests(1);

        // a resolution that cannot be remembered leaves no mapping at all
        find("4.14.0", true, true, registriesDir);
        Assertions.assertThat(mappingFile).doesNotExist();
        verifyRegistryRequests(2);
    }

    @Test
    void freshWithoutDownloadStillContradicts() throws Exception {
        stubRegistry();
        Path registriesDir = newRegistriesDir();
        find("4.14.5", true, false, registriesDir);
        wireMock.resetRequests();

        Assertions.assertThatThrownBy(() -> find("4.14.5", false, true, registriesDir))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("contradict each other");
        Assertions.assertThat(mappingFile(registriesDir)).exists();
        verifyRegistryRequests(0);
    }

    @Test
    void downloadFalseUsesMappingWithoutRegistryCache() throws Exception {
        stubRegistry();
        Path registriesDir = newRegistriesDir();
        QuarkusPlatformBom first = find("4.14.5", true, false, registriesDir);

        Files.delete(registryCacheFile(registriesDir));
        wireMock.resetRequests();
        QuarkusPlatformBom second = find("4.14.5", false, false, registriesDir);

        Assertions.assertThat(second).isEqualTo(first);
        verifyRegistryRequests(0);
    }

    @Test
    void downloadFalseFallsBackToOldRegistryCache() throws Exception {
        stubRegistry();
        Path registriesDir = newRegistriesDir();
        QuarkusPlatformBom first = find("4.14.5", true, false, registriesDir);

        Files.delete(mappingFile(registriesDir));
        backdate(registryCacheFile(registriesDir));
        QuarkusPlatformBom second = find("4.14.5", false, false, registriesDir);

        Assertions.assertThat(second).isEqualTo(first);
        Assertions.assertThat(mappingFile(registriesDir)).exists();
        verifyRegistryRequests(1);
    }

    @Test
    void corruptMappingFallsBack() throws Exception {
        stubRegistry();
        Path registriesDir = newRegistriesDir();
        QuarkusPlatformBom expected = find("4.14.5", true, false, registriesDir);
        Path mappingFile = mappingFile(registriesDir);

        for (String corrupt : List.of(
                "",
                "not json {",
                "[1, 2]",
                "{\"4.14.5\":\"3.27.3.1\"}",
                "{\"4.14.5\":{\"version\":\"3.27.3.1\"}}",
                "{\"4.14.5\":{\"groupId\":\" \",\"version\":\"3.27.3.1\"}}",
                "{\"4.14.5\":{\"groupId\":\"io.quarkus.platform\",\"version\":7}}")) {
            Files.writeString(mappingFile, corrupt);

            Assertions.assertThat(find("4.14.5", true, false, registriesDir)).as(corrupt).isEqualTo(expected);
            Assertions.assertThat(find("4.14.5", false, false, registriesDir)).as(corrupt).isEqualTo(expected);
        }
        verifyRegistryRequests(1);
    }

    @Test
    void unreadableMappingFallsBack() throws Exception {
        stubRegistry();
        Path registriesDir = newRegistriesDir();
        // a directory can be neither read nor overwritten as the mapping file
        Files.createDirectories(mappingFile(registriesDir));

        QuarkusPlatformBom bom = find("4.14.5", true, false, registriesDir);

        Assertions.assertThat(bom.version()).isEqualTo(CAMEL_4_14_5_PLATFORM);
        Assertions.assertThat(mappingFile(registriesDir)).isDirectory();
    }

    @Test
    void fileRegistryIsNotCached() throws Exception {
        Path registriesDir = newRegistriesDir();
        Path registry = registriesDir.resolveSibling("file-registry");
        Files.createDirectories(registry.resolve("client/platforms"));
        Files.copy(Path.of("target/test-classes/QuarkusHelperTest/quarkus-registry-client-platforms.json"),
                registry.resolve("client/platforms/all.json"));
        String baseUri = registry.toUri().toString();
        baseUri = baseUri.substring(0, baseUri.length() - 1);

        QuarkusPlatformBom bom = QuarkusHelper.findQuarkusPlatformBom(
                "4.14.5", QuarkusHelperTest::resolve, true, baseUri, false, registriesDir);

        Assertions.assertThat(bom.version()).isEqualTo(CAMEL_4_14_5_PLATFORM);
        Assertions.assertThat(registriesDir).isEmptyDirectory();
    }
}
