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

/**
 * Tests that the migration guide fuzzy search works end-to-end: loads the real .adoc guide files from the classpath,
 * indexes them, and returns relevant results for exact, fuzzy, and nonsense queries.
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
    void searchRespectsLimit() {
        List<MigrationData.GuideSection> results = migrationData.searchGuides("camel", 2);

        assertThat(results).hasSizeLessThanOrEqualTo(2);
    }
}
