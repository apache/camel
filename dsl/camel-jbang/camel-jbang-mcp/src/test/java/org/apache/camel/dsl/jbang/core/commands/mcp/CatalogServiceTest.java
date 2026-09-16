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

        assertThatThrownBy(() -> service.loadCatalog(null, null, "invalid-format"))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("GAV format");
    }

    @Test
    void platformBomInvalidFormatTwoPartsThrows() {
        CatalogService service = createService(null);

        assertThatThrownBy(() -> service.loadCatalog(null, null, "group:artifact"))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("GAV format");
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
}
