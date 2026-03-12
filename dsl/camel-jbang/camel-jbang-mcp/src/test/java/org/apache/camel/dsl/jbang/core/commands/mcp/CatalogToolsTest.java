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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogToolsTest {

    private CatalogTools createTools(String repos) {
        CatalogTools tools = new CatalogTools();
        tools.catalogRepos = Optional.ofNullable(repos);
        return tools;
    }

    @Test
    void defaultCatalogWithNoRepos() {
        CatalogTools tools = createTools(null);

        // Should use the default catalog (no version specified, no repos)
        CatalogTools.ComponentListResult result = tools.camel_catalog_components(null, null, 5, null, null);

        assertThat(result).isNotNull();
        assertThat(result.camelVersion()).isNotNull();
    }

    @Test
    void defaultCatalogWithEmptyRepos() {
        CatalogTools tools = createTools("");

        CatalogTools.ComponentListResult result = tools.camel_catalog_components(null, null, 5, null, null);

        assertThat(result).isNotNull();
        assertThat(result.camelVersion()).isNotNull();
    }

    @Test
    void catalogWithExtraRepos() {
        CatalogTools tools = createTools("https://maven.repository.redhat.com/ga/");

        // Should still work — the extra repo is added but default catalog doesn't need it
        CatalogTools.ComponentListResult result = tools.camel_catalog_components(null, null, 5, null, null);

        assertThat(result).isNotNull();
        assertThat(result.camelVersion()).isNotNull();
    }

    @Test
    void catalogWithMultipleRepos() {
        CatalogTools tools = createTools(
                "https://maven.repository.redhat.com/ga/,https://repository.jboss.org/nexus/content/groups/public/");

        CatalogTools.ComponentListResult result = tools.camel_catalog_components(null, null, 5, null, null);

        assertThat(result).isNotNull();
        assertThat(result.camelVersion()).isNotNull();
    }

    @Test
    void componentDocWithRepos() {
        CatalogTools tools = createTools("https://maven.repository.redhat.com/ga/");

        // timer is a core component, should always be available
        CatalogTools.ComponentDetailResult result = tools.camel_catalog_component_doc("timer", null, null);

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("timer");
    }
}
