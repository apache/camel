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
package org.apache.camel.dsl.jbang.core.commands.kaoto.utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogFilterTest {

    @TempDir
    Path catalogDir;

    // --- resolveWhitelist ---

    @Test
    void resolveWhitelistWithPinnedVersion() throws Exception {
        createVersionDir("camel-main", "4.23.0");

        Set<String> result = CatalogFilter.resolveWhitelist(catalogDir, "camel-main/4.23.0");

        assertThat(result).containsExactly("camel-main/4.23.0");
    }

    @Test
    void resolveWhitelistWithMultiplePinnedVersions() throws Exception {
        createVersionDir("camel-main", "4.23.0");
        createVersionDir("citrus", "5.1.0");

        Set<String> result = CatalogFilter.resolveWhitelist(catalogDir, "camel-main/4.23.0,citrus/5.1.0");

        assertThat(result).containsExactly("camel-main/4.23.0", "citrus/5.1.0");
    }

    @Test
    void resolveWhitelistWithLatest() throws Exception {
        createVersionDir("citrus", "4.0.0");
        createVersionDir("citrus", "5.0.0");
        createVersionDir("citrus", "5.1.0");

        Set<String> result = CatalogFilter.resolveWhitelist(catalogDir, "citrus/latest");

        assertThat(result).containsExactly("citrus/5.1.0");
    }

    @Test
    void resolveWhitelistLatestPicksHighestSemver() throws Exception {
        createVersionDir("camel-main", "4.8.0");
        createVersionDir("camel-main", "4.22.1");
        createVersionDir("camel-main", "4.9.0");

        Set<String> result = CatalogFilter.resolveWhitelist(catalogDir, "camel-main/latest");

        assertThat(result).containsExactly("camel-main/4.22.1");
    }

    @Test
    void resolveWhitelistMixedPinnedAndLatest() throws Exception {
        createVersionDir("camel-main", "4.22.0");
        createVersionDir("camel-main", "4.23.0");
        createVersionDir("citrus", "5.0.0");
        createVersionDir("citrus", "5.1.0");

        Set<String> result = CatalogFilter.resolveWhitelist(catalogDir, "camel-main/4.22.0,citrus/latest");

        assertThat(result).containsExactly("camel-main/4.22.0", "citrus/5.1.0");
    }

    @Test
    void resolveWhitelistTrimsWhitespace() throws Exception {
        createVersionDir("camel-main", "4.23.0");
        createVersionDir("citrus", "5.1.0");

        Set<String> result = CatalogFilter.resolveWhitelist(catalogDir, " camel-main/4.23.0 , citrus/5.1.0 ");

        assertThat(result).containsExactly("camel-main/4.23.0", "citrus/5.1.0");
    }

    @Test
    void resolveWhitelistFailsOnMissingPinnedVersion() throws Exception {
        createVersionDir("camel-main", "4.22.0");
        createVersionDir("camel-main", "4.23.0");

        assertThatThrownBy(() -> CatalogFilter.resolveWhitelist(catalogDir, "camel-main/4.99.0"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Pinned version not found in catalog: camel-main/4.99.0")
                .hasMessageContaining("4.22.0")
                .hasMessageContaining("4.23.0");
    }

    @Test
    void resolveWhitelistFailsOnMissingRuntimeForLatest() throws Exception {
        assertThatThrownBy(() -> CatalogFilter.resolveWhitelist(catalogDir, "nonexistent/latest"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Runtime directory not found in catalog: nonexistent");
    }

    @Test
    void resolveWhitelistFailsOnMissingRuntimeForPinned() throws Exception {
        assertThatThrownBy(() -> CatalogFilter.resolveWhitelist(catalogDir, "nonexistent/1.0.0"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Pinned version not found in catalog: nonexistent/1.0.0");
    }

    @Test
    void resolveWhitelistFailsOnInvalidEntryFormat() {
        assertThatThrownBy(() -> CatalogFilter.resolveWhitelist(catalogDir, "no-slash"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid whitelist entry");
    }

    @Test
    void resolveWhitelistLatestIgnoresFiles() throws Exception {
        createVersionDir("citrus", "5.0.0");
        Files.writeString(catalogDir.resolve("citrus/README.txt"), "not a version");

        Set<String> result = CatalogFilter.resolveWhitelist(catalogDir, "citrus/latest");

        assertThat(result).containsExactly("citrus/5.0.0");
    }

    @Test
    void resolveWhitelistLatestWithEmptyRuntimeDir() throws Exception {
        Files.createDirectories(catalogDir.resolve("citrus"));

        assertThatThrownBy(() -> CatalogFilter.resolveWhitelist(catalogDir, "citrus/latest"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No version directories found for runtime: citrus");
    }

    // --- compareVersions ---

    @Test
    void compareVersionsEqual() {
        assertThat(CatalogFilter.compareVersions("4.23.0", "4.23.0")).isZero();
    }

    @Test
    void compareVersionsMajorDifference() {
        assertThat(CatalogFilter.compareVersions("5.0.0", "4.99.0")).isPositive();
        assertThat(CatalogFilter.compareVersions("3.0.0", "4.0.0")).isNegative();
    }

    @Test
    void compareVersionsMinorDifference() {
        assertThat(CatalogFilter.compareVersions("4.23.0", "4.22.0")).isPositive();
        assertThat(CatalogFilter.compareVersions("4.8.0", "4.22.0")).isNegative();
    }

    @Test
    void compareVersionsPatchDifference() {
        assertThat(CatalogFilter.compareVersions("4.23.1", "4.23.0")).isPositive();
    }

    @Test
    void compareVersionsDifferentSegmentCount() {
        assertThat(CatalogFilter.compareVersions("4.23", "4.23.0")).isZero();
        assertThat(CatalogFilter.compareVersions("4.23.1", "4.23")).isPositive();
    }

    @Test
    void compareVersionsNumericNotLexicographic() {
        assertThat(CatalogFilter.compareVersions("4.9.0", "4.22.0")).isNegative();
    }

    // --- main (end-to-end) ---

    @Test
    void mainRemovesNonWhitelistedVersionDirs() throws Exception {
        createVersionDir("camel-main", "4.22.0");
        createVersionDir("camel-main", "4.23.0");
        createVersionDir("camel-main", "4.20.0");
        createVersionDirWithFile("camel-main", "4.20.0", "components.json");
        writeIndexJson("camel-main/4.22.0/components.json",
                "camel-main/4.23.0/components.json",
                "camel-main/4.20.0/components.json");

        CatalogFilter.main(new String[] { catalogDir.toString(), "camel-main/4.23.0" });

        assertThat(catalogDir.resolve("camel-main/4.23.0")).isDirectory();
        assertThat(catalogDir.resolve("camel-main/4.22.0")).doesNotExist();
        assertThat(catalogDir.resolve("camel-main/4.20.0")).doesNotExist();
    }

    @Test
    void mainFiltersIndexJson() throws Exception {
        createVersionDir("camel-main", "4.22.0");
        createVersionDir("camel-main", "4.23.0");
        createVersionDir("citrus", "5.0.0");
        createVersionDir("citrus", "5.1.0");
        writeIndexJson("camel-main/4.22.0/components.json",
                "camel-main/4.23.0/components.json",
                "citrus/5.0.0/actions.json",
                "citrus/5.1.0/actions.json");

        CatalogFilter.main(new String[] { catalogDir.toString(), "camel-main/4.23.0,citrus/5.1.0" });

        String content = Files.readString(catalogDir.resolve("index.json"));
        JsonObject index = (JsonObject) Jsoner.deserialize(content);
        JsonArray definitions = (JsonArray) index.get("definitions");
        assertThat(definitions).hasSize(2);
        assertThat(definitions.stream().map(d -> ((JsonObject) d).getString("fileName")))
                .containsExactlyInAnyOrder("camel-main/4.23.0/components.json", "citrus/5.1.0/actions.json");
    }

    @Test
    void mainWithLatestResolvesAndFilters() throws Exception {
        createVersionDir("citrus", "4.0.0");
        createVersionDir("citrus", "5.0.0");
        createVersionDir("citrus", "5.1.0");
        writeIndexJson("citrus/4.0.0/actions.json",
                "citrus/5.0.0/actions.json",
                "citrus/5.1.0/actions.json");

        CatalogFilter.main(new String[] { catalogDir.toString(), "citrus/latest" });

        assertThat(catalogDir.resolve("citrus/5.1.0")).isDirectory();
        assertThat(catalogDir.resolve("citrus/5.0.0")).doesNotExist();
        assertThat(catalogDir.resolve("citrus/4.0.0")).doesNotExist();

        String content = Files.readString(catalogDir.resolve("index.json"));
        JsonObject index = (JsonObject) Jsoner.deserialize(content);
        JsonArray definitions = (JsonArray) index.get("definitions");
        assertThat(definitions).hasSize(1);
        assertThat(((JsonObject) definitions.get(0)).getString("fileName")).isEqualTo("citrus/5.1.0/actions.json");
    }

    @Test
    void mainSkipsMissingRuntimeDirs() throws Exception {
        createVersionDir("camel-main", "4.23.0");
        writeIndexJson("camel-main/4.23.0/components.json");

        CatalogFilter.main(new String[] { catalogDir.toString(), "camel-main/4.23.0" });

        assertThat(catalogDir.resolve("camel-main/4.23.0")).isDirectory();
    }

    @Test
    void mainHandsMissingIndexJson() throws Exception {
        createVersionDir("camel-main", "4.23.0");

        CatalogFilter.main(new String[] { catalogDir.toString(), "camel-main/4.23.0" });

        assertThat(catalogDir.resolve("camel-main/4.23.0")).isDirectory();
        assertThat(catalogDir.resolve("index.json")).doesNotExist();
    }

    @Test
    void mainDeletesVersionDirRecursively() throws Exception {
        createVersionDir("camel-main", "4.22.0");
        createVersionDir("camel-main", "4.23.0");
        Path nestedDir = catalogDir.resolve("camel-main/4.22.0/sub/deep");
        Files.createDirectories(nestedDir);
        Files.writeString(nestedDir.resolve("nested.json"), "{}");
        writeIndexJson();

        CatalogFilter.main(new String[] { catalogDir.toString(), "camel-main/4.23.0" });

        assertThat(catalogDir.resolve("camel-main/4.22.0")).doesNotExist();
        assertThat(catalogDir.resolve("camel-main/4.23.0")).isDirectory();
    }

    @Test
    void mainMultipleRuntimesWithLatest() throws Exception {
        createVersionDir("camel-main", "4.22.0");
        createVersionDir("camel-main", "4.23.0");
        createVersionDir("camel-quarkus", "3.38.0");
        createVersionDir("camel-quarkus", "3.40.0");
        createVersionDir("camel-springboot", "4.22.0");
        createVersionDir("camel-springboot", "4.23.0");
        createVersionDir("citrus", "5.0.0");
        createVersionDir("citrus", "5.1.0");
        writeIndexJson(
                "camel-main/4.22.0/c.json", "camel-main/4.23.0/c.json",
                "camel-quarkus/3.38.0/c.json", "camel-quarkus/3.40.0/c.json",
                "camel-springboot/4.22.0/c.json", "camel-springboot/4.23.0/c.json",
                "citrus/5.0.0/c.json", "citrus/5.1.0/c.json");

        CatalogFilter.main(new String[] {
                catalogDir.toString(),
                "camel-main/latest,camel-quarkus/latest,camel-springboot/latest,citrus/latest"
        });

        assertThat(catalogDir.resolve("camel-main/4.23.0")).isDirectory();
        assertThat(catalogDir.resolve("camel-main/4.22.0")).doesNotExist();
        assertThat(catalogDir.resolve("camel-quarkus/3.40.0")).isDirectory();
        assertThat(catalogDir.resolve("camel-quarkus/3.38.0")).doesNotExist();
        assertThat(catalogDir.resolve("camel-springboot/4.23.0")).isDirectory();
        assertThat(catalogDir.resolve("camel-springboot/4.22.0")).doesNotExist();
        assertThat(catalogDir.resolve("citrus/5.1.0")).isDirectory();
        assertThat(catalogDir.resolve("citrus/5.0.0")).doesNotExist();

        String content = Files.readString(catalogDir.resolve("index.json"));
        JsonObject index = (JsonObject) Jsoner.deserialize(content);
        JsonArray definitions = (JsonArray) index.get("definitions");
        assertThat(definitions).hasSize(4);
    }

    // --- helpers ---

    private void createVersionDir(String runtime, String version) throws Exception {
        Files.createDirectories(catalogDir.resolve(runtime).resolve(version));
    }

    private void createVersionDirWithFile(String runtime, String version, String fileName) throws Exception {
        Path dir = catalogDir.resolve(runtime).resolve(version);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(fileName), "{}");
    }

    private void writeIndexJson(String... fileNames) throws Exception {
        JsonArray definitions = new JsonArray();
        for (String fileName : fileNames) {
            JsonObject def = new JsonObject();
            def.put("fileName", fileName);
            def.put("name", fileName.replace("/", "-"));
            definitions.add(def);
        }
        JsonObject index = new JsonObject();
        index.put("definitions", definitions);
        Files.writeString(catalogDir.resolve("index.json"), Jsoner.serialize(index));
    }
}
