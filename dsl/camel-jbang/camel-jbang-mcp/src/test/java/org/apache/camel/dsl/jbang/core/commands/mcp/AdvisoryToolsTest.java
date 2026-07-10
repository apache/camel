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

import io.quarkiverse.mcp.server.ToolCallException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the {@code camel_security_advisories} MCP tool, backed by the advisory data shipped with the Camel catalog.
 * Fully offline.
 */
class AdvisoryToolsTest {

    private AdvisoryTools tools() {
        AdvisoryTools tools = new AdvisoryTools();
        tools.advisoryService = new AdvisoryService();
        return tools;
    }

    @Test
    void listsAllPublishedAdvisoriesWithoutFilters() {
        AdvisoryTools.AdvisoriesResult result = tools().camel_security_advisories(null, null, null);

        assertThat(result.totalPublished()).isGreaterThan(70);
        assertThat(result.matched()).isEqualTo(result.totalPublished());
        assertThat(result.source()).isEqualTo("https://camel.apache.org/security/");
        // newest first: the very first published Camel advisory comes last
        assertThat(result.advisories().get(result.advisories().size() - 1).cve()).isEqualTo("CVE-2013-4330");
        assertThat(result.advisories().get(0).affectsGivenVersion()).isNull();
    }

    @Test
    void filtersByVersionComponentAndSeverity() {
        AdvisoryTools.AdvisoriesResult result = tools().camel_security_advisories("4.10.1", "bean", "MEDIUM");

        assertThat(result.camelVersion()).isEqualTo("4.10.1");
        assertThat(result.component()).isEqualTo("bean");
        assertThat(result.severity()).isEqualTo("MEDIUM");
        assertThat(result.matched()).isGreaterThanOrEqualTo(1);

        AdvisoryService.AdvisoryView view = result.advisories().stream()
                .filter(a -> "CVE-2025-27636".equals(a.cve()))
                .findFirst().orElse(null);
        assertThat(view).isNotNull();
        assertThat(view.affectsGivenVersion()).isTrue();
        assertThat(view.fixed()).contains("4.10.2");
        assertThat(view.mitigation()).isNotBlank();
    }

    @Test
    void blankArgumentsAreTreatedAsNoFilter() {
        AdvisoryTools.AdvisoriesResult result = tools().camel_security_advisories(" ", "", "  ");

        assertThat(result.matched()).isEqualTo(result.totalPublished());
        assertThat(result.camelVersion()).isNull();
        assertThat(result.component()).isNull();
        assertThat(result.severity()).isNull();
    }

    @Test
    void unavailableAdvisoryDataThrowsExplicitToolError() {
        AdvisoryTools tools = new AdvisoryTools();
        tools.advisoryService = AdvisoryServiceTest.unavailableService();

        assertThatThrownBy(() -> tools.camel_security_advisories(null, null, null))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("not available");
    }
}
