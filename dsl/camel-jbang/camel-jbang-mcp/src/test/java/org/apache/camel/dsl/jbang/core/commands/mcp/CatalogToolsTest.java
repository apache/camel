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

class CatalogToolsTest {

    private static final String BUILTIN_VERSION = new DefaultCamelCatalog().getCatalogVersion();

    private CatalogTools createTools(String repos) {
        CatalogService catalogService = new CatalogService();
        catalogService.catalogRepos = Optional.ofNullable(repos);

        CatalogTools tools = new CatalogTools();
        tools.catalogService = catalogService;
        return tools;
    }

    @Test
    void defaultCatalogWithNoRepos() {
        CatalogTools tools = createTools(null);

        CatalogTools.ComponentListResult result = tools.camel_catalog_components(null, null, 5, null, null, null);

        assertThat(result).isNotNull();
        assertThat(result.camelVersion()).isNotNull();
    }

    @Test
    void defaultCatalogWithEmptyRepos() {
        CatalogTools tools = createTools("");

        CatalogTools.ComponentListResult result = tools.camel_catalog_components(null, null, 5, null, null, null);

        assertThat(result).isNotNull();
        assertThat(result.camelVersion()).isNotNull();
    }

    @Test
    void catalogWithExtraRepos() {
        CatalogTools tools = createTools("https://maven.repository.redhat.com/ga/");

        CatalogTools.ComponentListResult result = tools.camel_catalog_components(null, null, 5, null, null, null);

        assertThat(result).isNotNull();
        assertThat(result.camelVersion()).isNotNull();
    }

    @Test
    void catalogWithMultipleRepos() {
        CatalogTools tools = createTools(
                "https://maven.repository.redhat.com/ga/,https://repository.jboss.org/nexus/content/groups/public/");

        CatalogTools.ComponentListResult result = tools.camel_catalog_components(null, null, 5, null, null, null);

        assertThat(result).isNotNull();
        assertThat(result.camelVersion()).isNotNull();
    }

    @Test
    void componentDocWithRepos() {
        CatalogTools tools = createTools("https://maven.repository.redhat.com/ga/");

        CatalogTools.ComponentDetailResult result
                = tools.camel_catalog_component_doc("timer", null, null, null, null, null);

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("timer");
    }

    @Test
    void componentDocDefaultsToCommonScope() {
        CatalogTools tools = createTools(null);

        CatalogTools.ComponentDetailResult defaultResult
                = tools.camel_catalog_component_doc("kafka", null, null, null, null, null);
        CatalogTools.ComponentDetailResult allResult
                = tools.camel_catalog_component_doc("kafka", null, "all", null, null, null);

        assertThat(defaultResult.endpointOptions()).isNotEmpty();
        assertThat(allResult.endpointOptions()).isNotEmpty();
        assertThat(defaultResult.endpointOptions().size())
                .as("default 'common' scope must filter out advanced/deprecated options")
                .isLessThan(allResult.endpointOptions().size());

        // 'common' must not include any deprecated option (advanced options are not exposed in the
        // returned OptionInfo, but deprecated is hidden internally via the label/deprecated flags)
        assertThat(defaultResult.endpointOptions())
                .as("default 'common' scope must not contain options whose name starts with 'synchronous'")
                .noneMatch(o -> "synchronous".equals(o.name()));
    }

    @Test
    void componentDocRequiredScopeOnlyReturnsRequiredOptions() {
        CatalogTools tools = createTools(null);

        CatalogTools.ComponentDetailResult result
                = tools.camel_catalog_component_doc("kafka", null, "required", null, null, null);

        assertThat(result.endpointOptions()).allMatch(CatalogTools.OptionInfo::required);
    }

    @Test
    void componentDocOptionsFilterByName() {
        CatalogTools tools = createTools(null);

        CatalogTools.ComponentDetailResult result
                = tools.camel_catalog_component_doc("kafka", "topic", "all", null, null, null);

        assertThat(result.endpointOptions()).isNotEmpty();
        assertThat(result.endpointOptions())
                .allMatch(o -> o.name().toLowerCase().contains("topic"));
    }

    @Test
    void componentDocInvalidIncludeOptionsThrows() {
        CatalogTools tools = createTools(null);

        assertThatThrownBy(() -> tools.camel_catalog_component_doc("timer", null, "bogus", null, null, null))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("Invalid includeOptions");
    }

    @Test
    void componentMavenReturnsCoordinates() {
        CatalogTools tools = createTools(null);

        CatalogTools.ComponentMavenResult result
                = tools.camel_catalog_component_maven("timer", null, null, null);

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("timer");
        assertThat(result.groupId()).isEqualTo("org.apache.camel");
        assertThat(result.artifactId()).isEqualTo("camel-timer");
        assertThat(result.version()).isNotBlank();
    }

    @Test
    void componentMavenUnknownComponentThrows() {
        CatalogTools tools = createTools(null);

        assertThatThrownBy(() -> tools.camel_catalog_component_maven("does-not-exist", null, null, null))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("Component not found");
    }

    // platformBom validation tests

    @Test
    void platformBomInvalidFormatThrows() {
        CatalogTools tools = createTools(null);

        assertThatThrownBy(() -> tools.camel_catalog_components(null, null, 5, null, null, "invalid-format"))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("GAV format");
    }

    @Test
    void platformBomInvalidFormatTwoPartsThrows() {
        CatalogTools tools = createTools(null);

        assertThatThrownBy(() -> tools.camel_catalog_components(null, null, 5, null, null, "group:artifact"))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("GAV format");
    }

    @Test
    void platformBomNullDoesNotThrow() {
        CatalogTools tools = createTools(null);

        CatalogTools.ComponentListResult result = tools.camel_catalog_components(null, null, 5, null, null, null);

        assertThat(result).isNotNull();
        assertThat(result.camelVersion()).isNotNull();
    }

    @Test
    void platformBomEmptyStringDoesNotThrow() {
        CatalogTools tools = createTools(null);

        CatalogTools.ComponentListResult result = tools.camel_catalog_components(null, null, 5, null, null, "");

        assertThat(result).isNotNull();
        assertThat(result.camelVersion()).isNotNull();
    }

    @Test
    void platformBomBlankStringDoesNotThrow() {
        CatalogTools tools = createTools(null);

        CatalogTools.ComponentListResult result = tools.camel_catalog_components(null, null, 5, null, null, "   ");

        assertThat(result).isNotNull();
        assertThat(result.camelVersion()).isNotNull();
    }

    // Version reporting tests

    @Test
    void defaultCatalogReturnsBuiltinVersion() {
        CatalogTools tools = createTools(null);

        CatalogTools.ComponentListResult result = tools.camel_catalog_components(null, null, 5, null, null, null);

        assertThat(result.camelVersion()).isEqualTo(BUILTIN_VERSION);
    }

    @Test
    void emptyVersionAndPlatformBomReturnsBuiltinVersion() {
        CatalogTools tools = createTools(null);

        CatalogTools.ComponentListResult result = tools.camel_catalog_components(null, null, 5, null, "", "");

        assertThat(result.camelVersion()).isEqualTo(BUILTIN_VERSION);
    }

    @Test
    void specificCamelVersionReturnsRequestedVersion() {
        CatalogTools tools = createTools(null);

        CatalogTools.ComponentListResult result
                = tools.camel_catalog_components(null, null, 5, null, BUILTIN_VERSION, null);

        assertThat(result.camelVersion()).isEqualTo(BUILTIN_VERSION);
    }

    @Test
    void platformBomVersionReturnsRequestedVersion() {
        CatalogTools tools = createTools(null);

        String bom = "org.apache.camel:camel-catalog:" + BUILTIN_VERSION;
        CatalogTools.ComponentListResult result = tools.camel_catalog_components(null, null, 5, null, null, bom);

        assertThat(result.camelVersion()).isEqualTo(BUILTIN_VERSION);
    }

    @Test
    void platformBomVersionTakesPrecedenceOverCamelVersion() {
        CatalogTools tools = createTools(null);

        String bom = "org.apache.camel:camel-catalog:" + BUILTIN_VERSION;
        CatalogTools.ComponentListResult result
                = tools.camel_catalog_components(null, null, 5, null, "9.99.99", bom);

        assertThat(result.camelVersion()).isEqualTo(BUILTIN_VERSION);
    }

    // Download tests — require Maven Central access.
    // Disabled in CI environments to avoid flaky network-dependent failures.

    @Test
    @DisabledIfSystemProperty(named = "ci.env.name", matches = ".*",
                              disabledReason = "Runs only local — requires Maven Central access")
    void downloadedCatalogVersionDiffersFromBuiltin() {
        CatalogTools tools = createTools(null);

        String requestedVersion = "4.10.0";
        CatalogTools.ComponentListResult listResult
                = tools.camel_catalog_components("timer", null, 1, "main", requestedVersion, null);

        assertThat(listResult.camelVersion()).isEqualTo(requestedVersion);

        CatalogTools.ComponentMavenResult mavenResult
                = tools.camel_catalog_component_maven("timer", "main", requestedVersion, null);

        assertThat(mavenResult.version()).isEqualTo(requestedVersion);
        assertThat(mavenResult.version()).isNotEqualTo(BUILTIN_VERSION);
    }

    @Test
    @DisabledIfSystemProperty(named = "ci.env.name", matches = ".*",
                              disabledReason = "Runs only local — requires Maven Central access")
    void downloadedCatalogViaPlatformBomVersionDiffersFromBuiltin() {
        CatalogTools tools = createTools(null);

        String requestedVersion = "4.10.0";
        String bom = "org.apache.camel:camel-catalog:" + requestedVersion;
        CatalogTools.ComponentListResult listResult
                = tools.camel_catalog_components("timer", null, 1, "main", null, bom);

        assertThat(listResult.camelVersion()).isEqualTo(requestedVersion);

        CatalogTools.ComponentMavenResult mavenResult
                = tools.camel_catalog_component_maven("timer", "main", null, bom);

        assertThat(mavenResult.version()).isEqualTo(requestedVersion);
        assertThat(mavenResult.version()).isNotEqualTo(BUILTIN_VERSION);
    }
}
