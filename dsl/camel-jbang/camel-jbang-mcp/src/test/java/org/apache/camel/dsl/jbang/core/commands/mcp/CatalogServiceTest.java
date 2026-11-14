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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import java.util.Optional;

import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The catalog loader behind the MCP tools that take a Camel version or platform BOM: which catalog it picks, and what
 * it rejects. The former per-kind catalog tools were the callers of these checks; the shared authoring tools answer for
 * the version in use.
 */
class CatalogServiceTest {

    private static final String BUILTIN_VERSION = new DefaultCamelCatalog().getCatalogVersion();

    private CatalogService createService(String repos) {
        CatalogService catalogService = new CatalogService();
        catalogService.catalogRepos = Optional.ofNullable(repos);
        return catalogService;
    }

    private static String version(CatalogService service, String runtime, String camelVersion, String platformBom) {
        try {
            return service.loadCatalog(runtime, camelVersion, platformBom).getCatalogVersion();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void defaultCatalogWithNoRepos() {
        assertThat(version(createService(null), null, null, null)).isEqualTo(BUILTIN_VERSION);
    }

    @Test
    void defaultCatalogWithEmptyRepos() {
        assertThat(version(createService(""), null, null, null)).isEqualTo(BUILTIN_VERSION);
    }

    @Test
    void catalogWithExtraRepos() {
        assertThat(version(createService("https://maven.repository.redhat.com/ga/"), null, null, null))
                .isEqualTo(BUILTIN_VERSION);
    }

    @Test
    void catalogWithMultipleRepos() {
        CatalogService service = createService(
                "https://maven.repository.redhat.com/ga/,https://repository.jboss.org/nexus/content/groups/public/");
        assertThat(version(service, null, null, null)).isEqualTo(BUILTIN_VERSION);
    }

    // platformBom validation tests

    @Test
    void platformBomInvalidFormatThrows() {
        CatalogService service = createService(null);

        assertThatThrownBy(() -> service.loadCatalog("spring-boot", null, "invalid-format"))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("GAV format");
    }

    @Test
    void platformBomInvalidFormatTwoPartsThrows() {
        CatalogService service = createService(null);

        assertThatThrownBy(() -> service.loadCatalog("spring-boot", null, "group:artifact"))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("GAV format");
    }

    @Test
    void platformBomThatIsNotAGavIsIgnoredForMain() {
        CatalogService service = createService(null);

        assertThat(version(service, null, null, "invalid-format")).isEqualTo(BUILTIN_VERSION);
        assertThat(version(service, "main", null, "none")).isEqualTo(BUILTIN_VERSION);
        assertThat(version(service, "main", null, "group:artifact")).isEqualTo(BUILTIN_VERSION);
    }

    @Test
    void platformBomOfAnotherRuntimeIsIgnoredForMain() {
        CatalogService service = createService(null);

        assertThat(version(service, "main", null, "io.quarkus.platform:quarkus-camel-bom:3.31.3")).isEqualTo(BUILTIN_VERSION);
        assertThat(version(service, "main", BUILTIN_VERSION, "org.springframework.boot:spring-boot-dependencies:3.5.0"))
                .isEqualTo(BUILTIN_VERSION);
    }

    @Test
    void camelGav() {
        assertThat(CatalogService.isCamelGav("org.apache.camel:camel-bom:4.22.1")).isTrue();
        assertThat(CatalogService.isCamelGav(" org.apache.camel:camel-catalog:4.22.1 ")).isTrue();
        assertThat(CatalogService.isCamelGav("io.quarkus.platform:quarkus-camel-bom:3.31.3")).isFalse();
        assertThat(CatalogService.isCamelGav("org.apache.camel:camel-bom")).isFalse();
        assertThat(CatalogService.isCamelGav("none")).isFalse();
    }

    @Test
    void platformBomEmptyOrBlankIsTheDefaultCatalog() {
        CatalogService service = createService(null);

        assertThat(version(service, null, null, "")).isEqualTo(BUILTIN_VERSION);
        assertThat(version(service, null, null, "   ")).isEqualTo(BUILTIN_VERSION);
    }

    // Version reporting tests

    @Test
    void emptyVersionAndPlatformBomReturnsBuiltinVersion() {
        assertThat(version(createService(null), null, "", "")).isEqualTo(BUILTIN_VERSION);
    }

    @Test
    void specificCamelVersionReturnsRequestedVersion() {
        assertThat(version(createService(null), null, BUILTIN_VERSION, null)).isEqualTo(BUILTIN_VERSION);
    }

    @Test
    void platformBomVersionReturnsRequestedVersion() {
        String bom = "org.apache.camel:camel-catalog:" + BUILTIN_VERSION;
        assertThat(version(createService(null), null, null, bom)).isEqualTo(BUILTIN_VERSION);
    }

    @Test
    void platformBomVersionTakesPrecedenceOverCamelVersion() {
        String bom = "org.apache.camel:camel-catalog:" + BUILTIN_VERSION;
        assertThat(version(createService(null), null, "9.99.99", bom)).isEqualTo(BUILTIN_VERSION);
    }

    @Test
    void springBootWithoutVersionFallsBackToDefaultCatalogWhenOffline() {
        // When runtime=spring-boot with no explicit version or BOM, the catalog loader tries to download
        // the Spring Boot catalog provider from Maven Central. In environments without network access (CI),
        // this fails with a DownloadException. The fix is to fall back to the default catalog rather than
        // surfacing a confusing network error to the user.
        // This test uses a non-existent repo so the download always fails, simulating offline/CI behaviour.
        CatalogService service = createService("file:///nonexistent-repo-that-never-exists");

        assertThat(version(service, "spring-boot", null, null)).isEqualTo(BUILTIN_VERSION);
    }

    // Download tests — require Maven Central access.
    // Disabled in CI environments to avoid flaky network-dependent failures.

    @Test
    @DisabledIfSystemProperty(named = "ci.env.name", matches = ".*",
                              disabledReason = "Runs only local — requires Maven Central access")
    void downloadedCatalogVersionDiffersFromBuiltin() {
        String requestedVersion = "4.10.0";
        String version = version(createService(null), "main", requestedVersion, null);

        assertThat(version).isEqualTo(requestedVersion);
        assertThat(version).isNotEqualTo(BUILTIN_VERSION);
    }

    @Test
    @DisabledIfSystemProperty(named = "ci.env.name", matches = ".*",
                              disabledReason = "Runs only local — requires Maven Central access")
    void downloadedCatalogViaPlatformBomVersionDiffersFromBuiltin() {
        String requestedVersion = "4.10.0";
        String bom = "org.apache.camel:camel-catalog:" + requestedVersion;
        String version = version(createService(null), "main", null, bom);

        assertThat(version).isEqualTo(requestedVersion);
        assertThat(version).isNotEqualTo(BUILTIN_VERSION);
    }

    /**
     * CAMEL-25640: quarkus runtime with only camelVersion (no explicit platformBom) used to pass null to
     * loadQuarkusCatalog, which silently returned an unusable catalog. The fix resolves the BOM from the registry.
     * Disabled in CI: requires network access to the Quarkus extension registry.
     */
    @Test
    @DisabledIfSystemProperty(named = "ci.env.name", matches = ".*",
                              disabledReason = "Runs only local — requires Quarkus extension registry access")
    void quarkusRuntimeWithCamelVersionResolvesTheBomFromRegistry() throws Exception {
        // CAMEL-25640: passing camelVersion with quarkus runtime must resolve the platform BOM from the Quarkus
        // extension registry and pass it to doLoadCatalog; previously it passed null and produced an NPE or a
        // silently wrong catalog. Use a released version that is not the built-in one so sameAsDefault() is false
        // and the new code path is taken.
        CatalogService service = createService(null);
        CamelCatalog catalog = service.loadCatalog("quarkus", "4.10.0", null);
        assertThat(catalog.getRuntimeProvider().getProviderName()).isEqualTo("quarkus");
    }

    @Test
    @DisabledIfSystemProperty(named = "ci.env.name", matches = ".*",
                              disabledReason = "Runs only local — requires Maven Central access")
    void mixedRuntimeRequestsKeepTheirCoreSchemasAndProviderCoordinates() throws Exception {
        CatalogService service = createService(null);
        String targetSplit = service.loadCatalog("main", "4.8.0", null).modelJSonSchema("split");
        ConfigurationValidateTools tools = new ConfigurationValidateTools();
        tools.catalogService = service;
        String[][] requests = {
                { "spring-boot", "4.8.0", null, "camel-http-starter", "4.8.0" },
                { "quarkus", null, "io.quarkus.platform:quarkus-camel-bom:3.15.1", "camel-quarkus-http", "3.15.0" },
                { "main", "4.8.0", null, "camel-http", "4.8.0" },
                {
                        "spring-boot", "4.8.0", "org.apache.camel.springboot:camel-spring-boot-bom:4.8.0",
                        "camel-http-starter", "4.8.0" }
        };
        for (int pass = 0; pass < 2; pass++) {
            for (String[] request : requests) {
                String scenario = "runtime=" + request[0] + ", camelVersion=" + request[1]
                                  + ", platformBom=" + request[2] + ", pass=" + pass;
                CamelCatalog catalog = service.loadCatalog(request[0], request[1], request[2]);
                assertThat(catalog.getCatalogVersion()).as("%s: core version", scenario).isEqualTo("4.8.0");
                assertThat(catalog.modelJSonSchema("split")).as("%s: split schema", scenario).isEqualTo(targetSplit)
                        .doesNotContain("errorThreshold");
                assertThat(catalog.modelJSonSchema("switch")).as("%s: missing switch schema", scenario).isNull();
                assertThat(catalog.componentModel("http").getArtifactId()).as("%s: provider artifact", scenario)
                        .isEqualTo(request[3]);
                assertThat(catalog.componentModel("http").getVersion()).as("%s: provider version", scenario)
                        .isEqualTo(request[4]);
                var result = tools.camel_configuration_validate("camel.component.http.max-total-connections=20",
                        request[0], request[1], request[2]);
                assertThat(result.camelVersion()).as("%s: validation version", scenario).isEqualTo("4.8.0");
                assertThat(result.summary().valid()).as("%s: valid configurations", scenario).isEqualTo(1);
            }
            CamelCatalog current = service.loadCatalog("main", BUILTIN_VERSION, null);
            assertThat(current.getCatalogVersion()).isEqualTo(BUILTIN_VERSION);
            assertThat(current.modelJSonSchema("split")).contains("errorThreshold");
            assertThat(current.modelJSonSchema("switch")).isNotNull();
        }
    }
}
