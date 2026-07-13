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

import java.util.List;

import org.apache.camel.tooling.model.SecurityAdvisoryModel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the known-CVE advisory integration of the {@code camel_route_harden_context} tool: matched advisories are
 * embedded in the hardening context, unaffected versions report nothing, and advisory lookup failures degrade to a note
 * without breaking the hardening analysis.
 */
class HardenToolsAdvisoryTest {

    private static final String KAFKA_ROUTE = """
            - route:
                from:
                  uri: kafka:orders
                  steps:
                    - to: log:done
            """;

    private HardenTools hardenTools(AdvisoryService advisoryService) {
        HardenTools tools = new HardenTools();
        tools.catalogService = new CatalogService();
        tools.securityData = new SecurityData();
        tools.advisoryService = advisoryService;
        return tools;
    }

    private static AdvisoryService stubService(SecurityAdvisoryModel... advisories) {
        return new AdvisoryService() {
            @Override
            public List<SecurityAdvisoryModel> advisories() {
                return List.of(advisories);
            }
        };
    }

    @Test
    void matchedAdvisoriesAreEmbeddedInHardenContext() {
        // an advisory naming camel-kafka whose affected range cannot be parsed is kept for the LLM to judge
        AdvisoryService stub = stubService(
                AdvisoryServiceTest.advisory("CVE-2099-11111", "MEDIUM", "All versions", "camel-kafka"),
                AdvisoryServiceTest.advisory("CVE-2099-22222", "HIGH", "All versions", "camel-ftp"));

        HardenTools.HardenContextResult result = hardenTools(stub)
                .camel_route_harden_context(KAFKA_ROUTE, "yaml", null, null, null);

        assertThat(result.knownSecurityAdvisories()).hasSize(1);
        assertThat(result.knownSecurityAdvisories().get(0).cve()).isEqualTo("CVE-2099-11111");
        assertThat(result.knownSecurityAdvisories().get(0).affectsGivenVersion()).isNull();
        assertThat(result.advisoriesNote()).isNull();
    }

    @Test
    void unaffectedVersionReportsNoAdvisories() {
        // the advisory affected range excludes the current catalog version, so nothing is reported
        AdvisoryService stub = stubService(
                AdvisoryServiceTest.advisory("CVE-2025-27636", "MEDIUM",
                        "Apache Camel 4.10.0 before 4.10.2.", "camel-kafka"));

        HardenTools.HardenContextResult result = hardenTools(stub)
                .camel_route_harden_context(KAFKA_ROUTE, "yaml", null, null, null);

        assertThat(result.knownSecurityAdvisories()).isNull();
        assertThat(result.advisoriesNote()).isNull();
    }

    @Test
    void advisoryLookupFailureDegradesToNote() {
        HardenTools.HardenContextResult result = hardenTools(AdvisoryServiceTest.unavailableService())
                .camel_route_harden_context(KAFKA_ROUTE, "yaml", null, null, null);

        assertThat(result).isNotNull();
        assertThat(result.knownSecurityAdvisories()).isNull();
        assertThat(result.advisoriesNote()).contains("Known-CVE advisory check skipped");
        // the hardening analysis itself is unaffected by the advisory failure
        assertThat(result.securitySensitiveComponents()).extracting(HardenTools.SecurityComponent::name)
                .contains("kafka");
    }
}
