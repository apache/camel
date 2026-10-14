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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests that the migration guide fuzzy search works end-to-end: loads the real .adoc guide files from the classpath,
 * indexes them per topic, and returns relevant results for exact, fuzzy, version-scoped and nonsense queries.
 */
class MigrationDataSearchTest {

    private final MigrationData migrationData = new MigrationData();

    @Test
    void exactSearchFindsMatchingGuideSection() {
        // "camel-vm" was removed in Camel 4 — this is documented in the migration guides
        List<MigrationData.GuideSection> results = migrationData.searchGuides("camel-vm", 3);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).content().toLowerCase()).contains("camel-vm");
        assertThat(results.get(0).url()).startsWith("https://camel.apache.org/manual/");
    }

    @Test
    void fuzzySearchFindsResultDespiteTypo() {
        // "directvm" (missing hyphen) should still match "direct-vm" sections via fuzzy matching
        List<MigrationData.GuideSection> results = migrationData.searchGuides("directvm", 3);

        assertThat(results).isNotEmpty();
    }

    @Test
    void nonsenseQueryReturnsNoResults() {
        List<MigrationData.GuideSection> results = migrationData.searchGuides("xyznonexistent12345", 3);

        assertThat(results).isEmpty();
    }

    @Test
    void unrelatedOrStopWordsOnlyQueryReturnsNoResults() {
        // "smoothie" is within typo distance of "smoothing", but an entry matching a third of the query is unrelated
        assertThat(migrationData.searchGuides("banana smoothie recipe", 3)).isEmpty();
        assertThat(migrationData.searchGuides("camel migration upgrade guide", 3)).isEmpty();
    }

    @Test
    void entriesAreTopicsWithTheReleaseHeadingAsContext() {
        assertThat(migrationData.getGuideIndex())
                .anyMatch(s -> s.sectionTitle().equals("Upgrading Camel 4.20 to 4.21 / camel-cxf"))
                .allMatch(s -> s.content().lines().noneMatch(l -> l.startsWith("== ") || l.startsWith("=== ")));
    }

    @Test
    void cxfServletFindsCamelCxfEntry() {
        List<MigrationData.GuideSection> results = migrationData.searchGuides("cxf servlet", 3);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).sectionTitle()).endsWith("/ camel-cxf");
        assertThat(results.get(0).content()).contains("camel-servlet");
    }

    @Test
    void camelMailSubjectHeaderFindsCamelMailEntry() {
        List<MigrationData.GuideSection> results = migrationData.searchGuides("camel-mail subject header", 3);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).sectionTitle()).contains("/ camel-mail");
        assertThat(results.get(0).content()).contains("MAIL_SUBJECT");
    }

    @Test
    void versionRangeFavoursMajorMigrationGuides() {
        List<MigrationData.SearchHit> hits
                = migrationData.search("CXF SOAP web service migration Camel 2 to 4", "2.25", "4.22", 5);

        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).section().guide()).isEqualTo("Camel 3 Migration Guide (2.x to 3.0)");
        assertThat(hits.get(0).section().sectionTitle()).startsWith("Migrating Camel applications");
        assertThat(hits.get(0).section().content()).contains("camel-cxf");
    }

    @Test
    void versionRangeKeepsStrongUpgradeGuideEntries() {
        // the major guide bonus is a multiplier, not a partition: exact topic entries of upgrade guides stay on top
        List<MigrationData.SearchHit> hits = migrationData.search("cxf servlet", "2.25", "4.22", 3);

        assertThat(hits).hasSize(3);
        assertThat(hits).extracting(h -> h.section().sectionTitle()).anyMatch(t -> t.endsWith("/ camel-cxf"));
    }

    @Test
    void versionRangeOmitsGuidesOutsideTheRange() {
        List<MigrationData.SearchHit> hits = migrationData.search("cxf servlet", "4.10", "4.22", 20);

        assertThat(hits).isNotEmpty();
        assertThat(hits).extracting(h -> h.section().version())
                .allMatch(v -> v.startsWith("4.") && Integer.parseInt(v.substring(2)) > 10
                        && Integer.parseInt(v.substring(2)) <= 22);
    }

    @Test
    void invalidVersionIsRejected() {
        assertThatThrownBy(() -> migrationData.search("cxf", "four", null, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currentVersion");
        assertThatThrownBy(() -> migrationData.search("cxf", "4.22", "3.20", 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is after targetVersion");
    }

    @Test
    void snippetShowsTheLinesAroundTheMatch() {
        List<MigrationData.SearchHit> hits = migrationData.search("camel-mail subject header", null, null, 1);

        assertThat(hits).hasSize(1);
        String content = hits.get(0).section().content();
        String snippet = migrationData.snippet(hits.get(0), 10);

        assertThat(content.lines().count()).isGreaterThan(10);
        assertThat(content.lines().limit(10)).noneMatch(l -> l.contains("MAIL_SUBJECT"));
        assertThat(snippet).contains("MAIL_SUBJECT").contains("lines above");
    }

    @Test
    void searchRespectsLimit() {
        List<MigrationData.GuideSection> results = migrationData.searchGuides("cxf", 2);

        assertThat(results).hasSize(2);
    }
}
