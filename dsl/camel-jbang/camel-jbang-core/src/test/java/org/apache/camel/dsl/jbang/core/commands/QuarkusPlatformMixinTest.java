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
package org.apache.camel.dsl.jbang.core.commands;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.function.Function;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.QuarkusHelper;
import org.apache.camel.dsl.jbang.core.common.QuarkusHelper.QuarkusPlatformBom;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.camel.tooling.maven.MavenArtifact;
import org.apache.camel.tooling.maven.MavenGav;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.apache.camel.dsl.jbang.core.common.CamelJBangConstants.QUARKUS_EXTENSION_REGISTRY_BASE_URI;
import static org.assertj.core.api.Assertions.assertThat;

class QuarkusPlatformMixinTest {

    private static final String PLATFORMS_JSON = "target/test-classes/QuarkusHelperTest/quarkus-registry-client-platforms.json";

    /** Registry A has platform 3.27.3.1 for Camel 4.14.5 */
    @RegisterExtension
    static WireMockExtension registryA = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    /** Registry B, on the same host but another port, has a respin 3.27.4 for the same Camel version */
    @RegisterExtension
    static WireMockExtension registryB = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    @TempDir
    Path home;

    private String originalHome;

    private static final QuarkusPlatformMixinSpec DEFAULT_FALLBACK = new QuarkusPlatformMixinSpec() {
        @Override
        public String quarkusGroupId() {
            return "io.quarkus.platform";
        }

        @Override
        public String quarkusVersion() {
            return null;
        }

        @Override
        public String quarkusExtensionRegistryBaseUri() {
            return RuntimeType.QUARKUS_EXTENSION_REGISTRY_BASE_URL;
        }
    };

    @Test
    void platformUrlFromPropertiesIsHonoured() {
        Properties props = new Properties();
        props.setProperty(QuarkusHelper.QUARKUS_PLATFORM_URL_PROPERTY, "https://my.registry.example.com");

        QuarkusPlatformMixin result = QuarkusPlatformMixin.of(props, DEFAULT_FALLBACK);

        assertThat(result.quarkusExtensionRegistryBaseUri()).isEqualTo("https://my.registry.example.com");
    }

    @Test
    void platformUrlSuffixStrippedWhenReadFromProperties() {
        Properties props = new Properties();
        props.setProperty(QuarkusHelper.QUARKUS_PLATFORM_URL_PROPERTY,
                "https://my.registry.example.com/client/platforms");

        QuarkusPlatformMixin result = QuarkusPlatformMixin.of(props, DEFAULT_FALLBACK);

        assertThat(result.quarkusExtensionRegistryBaseUri()).isEqualTo("https://my.registry.example.com");
    }

    @Test
    void platformUrlTrailingSlashIsStripped() {
        Properties props = new Properties();
        props.setProperty(QuarkusHelper.QUARKUS_PLATFORM_URL_PROPERTY, "https://my.registry.example.com/");

        QuarkusPlatformMixin result = QuarkusPlatformMixin.of(props, DEFAULT_FALLBACK);

        assertThat(result.quarkusExtensionRegistryBaseUri()).isEqualTo("https://my.registry.example.com");
    }

    @Test
    void canonicalKeyTakesPriorityOverLegacyKey() {
        Properties props = new Properties();
        props.setProperty(QUARKUS_EXTENSION_REGISTRY_BASE_URI, "https://canonical.example.com");
        props.setProperty(QuarkusHelper.QUARKUS_PLATFORM_URL_PROPERTY, "https://legacy.example.com");

        QuarkusPlatformMixin result = QuarkusPlatformMixin.of(props, DEFAULT_FALLBACK);

        assertThat(result.quarkusExtensionRegistryBaseUri()).isEqualTo("https://canonical.example.com");
    }

    @Test
    void fallbackUsedWhenNeitherPropertyIsSet() {
        QuarkusPlatformMixin result = QuarkusPlatformMixin.of(new Properties(), DEFAULT_FALLBACK);

        assertThat(result.quarkusExtensionRegistryBaseUri())
                .isEqualTo(RuntimeType.QUARKUS_EXTENSION_REGISTRY_BASE_URL);
    }

    @Test
    void twoPhaseLoadingPreservesFirstValue() {
        Properties appProps = new Properties();
        appProps.setProperty(QuarkusHelper.QUARKUS_PLATFORM_URL_PROPERTY, "https://from-app-props.example.com");
        QuarkusPlatformMixin afterPhase1 = QuarkusPlatformMixin.of(appProps, DEFAULT_FALLBACK);

        QuarkusPlatformMixin afterPhase2 = QuarkusPlatformMixin.of(new Properties(), afterPhase1);

        assertThat(afterPhase2.quarkusExtensionRegistryBaseUri()).isEqualTo("https://from-app-props.example.com");
    }

    @Test
    void twoPhaseLoadingAllowsOverride() {
        Properties appProps = new Properties();
        appProps.setProperty(QuarkusHelper.QUARKUS_PLATFORM_URL_PROPERTY, "https://from-app-props.example.com");
        QuarkusPlatformMixin afterPhase1 = QuarkusPlatformMixin.of(appProps, DEFAULT_FALLBACK);

        Properties sysProps = new Properties();
        sysProps.setProperty(QuarkusHelper.QUARKUS_PLATFORM_URL_PROPERTY, "https://from-sys-prop.example.com");
        QuarkusPlatformMixin afterPhase2 = QuarkusPlatformMixin.of(sysProps, afterPhase1);

        assertThat(afterPhase2.quarkusExtensionRegistryBaseUri()).isEqualTo("https://from-sys-prop.example.com");
    }

    @BeforeEach
    void useTempHome() throws IOException {
        originalHome = CommandLineHelper.getHomeDir().toString();
        CommandLineHelper.useHomeDir(home.toString());
        String platforms = Files.readString(Path.of(PLATFORMS_JSON));
        stub(registryA, platforms);
        stub(registryB, platforms.replace("3.27.3.1", "3.27.4"));
    }

    @AfterEach
    void restoreHome() {
        CommandLineHelper.useHomeDir(originalHome);
    }

    private static void stub(WireMockExtension registry, String body) {
        registry.resetAll();
        registry.stubFor(WireMock.get(WireMock.urlEqualTo("/client/platforms/all"))
                .willReturn(WireMock.ok().withHeader("Content-Type", "application/json").withBody(body)));
    }

    /** The BOM fixtures of QuarkusHelperTest; the 3.27.4 respin pins the same Camel version as 3.27.3.1 */
    private static final Function<MavenGav, MavenArtifact> RESOLVER = gav -> {
        String version = "3.27.4".equals(gav.getVersion()) ? "3.27.3.1" : gav.getVersion();
        Path pom = Path.of("target/test-classes/QuarkusHelperTest/quarkus-camel-bom-" + version + ".pom.xml");
        if (!Files.isRegularFile(pom)) {
            throw new UncheckedIOException(new IOException("No fixture for " + gav));
        }
        return new MavenArtifact(gav, pom.toFile());
    };

    private static QuarkusPlatformBom resolveWithFlag(WireMockExtension registry) {
        QuarkusPlatformMixin mixin = new QuarkusPlatformMixin();
        CommandLine.populateCommand(mixin, "--quarkus-ext-registry=" + registry.baseUrl());
        return mixin.resolve("4.14.5", RESOLVER, true, false);
    }

    private static QuarkusPlatformBom resolveWithProperty(WireMockExtension registry) {
        Properties props = new Properties();
        props.setProperty(QUARKUS_EXTENSION_REGISTRY_BASE_URI, registry.baseUrl());
        return QuarkusPlatformMixin.of(props, DEFAULT_FALLBACK).resolve("4.14.5", RESOLVER, true, false);
    }

    private static void assertResolvedFrom(QuarkusPlatformBom bom, WireMockExtension registry, String platformVersion) {
        assertThat(bom.version()).isEqualTo(platformVersion);
        assertThat(bom.camelVersion()).isEqualTo("4.14.5");
        assertThat(bom.quarkusExtensionRegistryBaseUri()).isEqualTo(registry.baseUrl());
    }

    private static void assertAskedOnce(WireMockExtension registry) {
        registry.verify(1, WireMock.getRequestedFor(WireMock.urlEqualTo("/client/platforms/all")));
    }

    @Test
    void alternatingRegistryFlagDoesNotServeTheOtherRegistrysCache() {
        assertResolvedFrom(resolveWithFlag(registryA), registryA, "3.27.3.1");
        assertResolvedFrom(resolveWithFlag(registryB), registryB, "3.27.4");
        // back and forth again: each registry answers from its own cache
        assertResolvedFrom(resolveWithFlag(registryA), registryA, "3.27.3.1");
        assertResolvedFrom(resolveWithFlag(registryB), registryB, "3.27.4");

        assertAskedOnce(registryA);
        assertAskedOnce(registryB);
    }

    @Test
    void alternatingRegistryPropertyDoesNotServeTheOtherRegistrysCache() {
        assertResolvedFrom(resolveWithProperty(registryA), registryA, "3.27.3.1");
        assertResolvedFrom(resolveWithProperty(registryB), registryB, "3.27.4");
        // the flag and the property name the same registry, so they share its cache
        assertResolvedFrom(resolveWithFlag(registryA), registryA, "3.27.3.1");
        assertResolvedFrom(resolveWithProperty(registryB), registryB, "3.27.4");

        assertAskedOnce(registryA);
        assertAskedOnce(registryB);
    }

}
