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

import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.tooling.model.SecurityAdvisoryModel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link AdvisoryService}: loading the published advisories from the Camel catalog, the best-effort affected
 * version matching, and the query filtering. Fully offline - the advisory data ships with camel-catalog.
 */
class AdvisoryServiceTest {

    /** Builds a synthetic advisory for deterministic filter tests. */
    static SecurityAdvisoryModel advisory(String cve, String severity, String affected, String... components) {
        SecurityAdvisoryModel model = new SecurityAdvisoryModel();
        model.setCve(cve);
        model.setSeverity(severity);
        model.setAffected(affected);
        model.setSummary("Synthetic advisory " + cve);
        model.setFixed("n/a");
        model.setUrl("https://camel.apache.org/security/" + cve + ".html");
        model.getComponents().addAll(List.of(components));
        return model;
    }

    /** An {@link AdvisoryService} whose catalog carries no advisory data. */
    static AdvisoryService unavailableService() {
        return new AdvisoryService(new DefaultCamelCatalog() {
            @Override
            public List<SecurityAdvisoryModel> camelSecurityAdvisories() {
                return List.of();
            }
        });
    }

    // ---- loading from the catalog ----

    @Test
    void loadsPublishedAdvisoriesFromCatalog() {
        List<SecurityAdvisoryModel> advisories = new AdvisoryService().advisories();

        assertThat(advisories.size()).isGreaterThan(70);

        SecurityAdvisoryModel known = advisories.stream()
                .filter(a -> "CVE-2025-27636".equals(a.getCve()))
                .findFirst().orElse(null);
        assertThat(known).isNotNull();
        assertThat(known.getSeverity()).isEqualTo("MEDIUM");
        assertThat(known.getAffected()).contains("4.10.0 before 4.10.2");
        assertThat(known.getFixed()).contains("4.10.2");
        assertThat(known.getUrl()).isEqualTo("https://camel.apache.org/security/CVE-2025-27636.html");
        assertThat(known.getComponents()).contains("camel-bean", "camel-undertow", "camel-kafka");
    }

    @Test
    void emptyCatalogDataFailsExplicitly() {
        assertThatThrownBy(() -> unavailableService().advisories())
                .isInstanceOf(AdvisoryService.AdvisoriesUnavailableException.class)
                .hasMessageContaining("not available");
    }

    // ---- affected version matching ----

    @Test
    void affectsVersionWithExclusiveRanges() {
        String affected = "Apache Camel 4.10.0 before 4.10.2. Apache Camel 4.8.0 before 4.8.5. "
                          + "Apache Camel 3.10.0 before 3.22.4.";

        assertThat(AdvisoryService.affectsVersion(affected, "4.10.1")).isTrue();
        assertThat(AdvisoryService.affectsVersion(affected, "4.10.0")).isTrue();
        assertThat(AdvisoryService.affectsVersion(affected, "4.8.4")).isTrue();
        assertThat(AdvisoryService.affectsVersion(affected, "3.15.0")).isTrue();

        assertThat(AdvisoryService.affectsVersion(affected, "4.10.2")).isFalse();
        assertThat(AdvisoryService.affectsVersion(affected, "4.8.5")).isFalse();
        assertThat(AdvisoryService.affectsVersion(affected, "4.11.0")).isFalse();
        assertThat(AdvisoryService.affectsVersion(affected, "3.22.4")).isFalse();
    }

    @Test
    void affectsVersionWithInclusiveRangesAndBareVersion() {
        // the affected style used by the old advisories, e.g. CVE-2013-4330
        String affected = "2.9.0 up to 2.9.7, 2.10.0 up to 2.10.6, 2.11.0 up to 2.11.1, 2.12.0";

        assertThat(AdvisoryService.affectsVersion(affected, "2.9.0")).isTrue();
        assertThat(AdvisoryService.affectsVersion(affected, "2.9.7")).isTrue();
        assertThat(AdvisoryService.affectsVersion(affected, "2.12.0")).isTrue();

        assertThat(AdvisoryService.affectsVersion(affected, "2.9.8")).isFalse();
        assertThat(AdvisoryService.affectsVersion(affected, "2.12.1")).isFalse();
        assertThat(AdvisoryService.affectsVersion(affected, "2.8.0")).isFalse();
    }

    @Test
    void affectsVersionStripsQualifierSuffix() {
        String affected = "Apache Camel 4.10.0 before 4.10.2.";

        assertThat(AdvisoryService.affectsVersion(affected, "4.10.1-SNAPSHOT")).isTrue();
        assertThat(AdvisoryService.affectsVersion(affected, "4.10.2-SNAPSHOT")).isFalse();
    }

    @Test
    void affectsVersionIsNullWhenNotParseable() {
        assertThat(AdvisoryService.affectsVersion("All versions of Apache Camel", "4.10.1")).isNull();
        assertThat(AdvisoryService.affectsVersion(null, "4.10.1")).isNull();
        assertThat(AdvisoryService.affectsVersion("  ", "4.10.1")).isNull();
        assertThat(AdvisoryService.affectsVersion("Apache Camel 4.10.0 before 4.10.2.", null)).isNull();
        assertThat(AdvisoryService.affectsVersion("Apache Camel 4.10.0 before 4.10.2.", " ")).isNull();
    }

    // ---- query filtering ----

    @Test
    void queryFiltersByComponentSeverityAndVersion() {
        List<SecurityAdvisoryModel> advisories = List.of(
                advisory("CVE-2013-4330", "CRITICAL", "2.9.0 up to 2.9.7, 2.12.0"),
                advisory("CVE-2025-27636", "MEDIUM", "Apache Camel 4.10.0 before 4.10.2.", "camel-bean",
                        "camel-undertow"));

        // no filters: everything, newest first, no version verdict
        List<AdvisoryService.AdvisoryView> views = AdvisoryService.query(advisories, null, null, null);
        assertThat(views).hasSize(2);
        assertThat(views.get(0).cve()).isEqualTo("CVE-2025-27636");
        assertThat(views.get(0).affectsGivenVersion()).isNull();

        // component filter accepts both bare and camel- prefixed names
        assertThat(AdvisoryService.query(advisories, null, "bean", null)).hasSize(1);
        assertThat(AdvisoryService.query(advisories, null, "camel-bean", null)).hasSize(1);
        assertThat(AdvisoryService.query(advisories, null, "does-not-exist", null)).isEmpty();

        // severity filter is case-insensitive
        assertThat(AdvisoryService.query(advisories, null, null, "medium")).hasSize(1);
        assertThat(AdvisoryService.query(advisories, null, null, "CRITICAL")).hasSize(1);
        assertThat(AdvisoryService.query(advisories, null, null, "IMPORTANT")).isEmpty();

        // version filter drops advisories whose parsed ranges exclude the version
        List<AdvisoryService.AdvisoryView> affected = AdvisoryService.query(advisories, "4.10.1", null, null);
        assertThat(affected).hasSize(1);
        assertThat(affected.get(0).cve()).isEqualTo("CVE-2025-27636");
        assertThat(affected.get(0).affectsGivenVersion()).isTrue();

        assertThat(AdvisoryService.query(advisories, "4.10.2", null, null)).isEmpty();
        assertThat(AdvisoryService.query(advisories, "2.12.0", null, null))
                .singleElement()
                .satisfies(view -> assertThat(view.cve()).isEqualTo("CVE-2013-4330"));
    }

    @Test
    void queryKeepsAdvisoriesWithUnparseableRanges() {
        List<SecurityAdvisoryModel> advisories
                = List.of(advisory("CVE-2099-11111", "MEDIUM", "All versions", "camel-bar"));

        List<AdvisoryService.AdvisoryView> views = AdvisoryService.query(advisories, "4.10.1", null, null);

        assertThat(views).hasSize(1);
        assertThat(views.get(0).affectsGivenVersion()).isNull();
    }
}
