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
package org.apache.camel.dsl.jbang.core.commands;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.dsl.jbang.core.common.outdated.DependencyCheck;
import org.apache.camel.dsl.jbang.core.common.outdated.DependencyRow;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-27351: camel dependency outdated, the parts that need no network or running integration.
 */
class DependencyOutdatedTest {

    private static List<DependencyRow> rows() {
        DependencyRow yaml = new DependencyRow("org.yaml", "snakeyaml", "1.33", false, null);
        DependencyRow text = new DependencyRow("org.apache.commons", "commons-text", "1.9", false, null);
        DependencyRow netty = new DependencyRow("io.netty", "netty-common", "4.2.20.Final", true, "camel-netty");
        List<DependencyRow> rows = List.of(yaml, text, netty);
        DependencyCheck.classify(rows, "4.18.1", Map.of("org.yaml:snakeyaml", "2.5"), null);
        return rows;
    }

    @Test
    void failOn() {
        List<DependencyRow> rows = rows();
        DependencyOutdated cmd = new DependencyOutdated();
        cmd.failOn = "status";
        assertThat(cmd.failed(rows)).isTrue();
        cmd.failOn = "none";
        assertThat(cmd.failed(rows)).isFalse();

        List<DependencyRow> text = List.of(rows.get(1));
        DependencyCheck.applyAvailable(text, Map.of("org.apache.commons:commons-text", List.of("1.15.0")));
        cmd.failOn = "any";
        assertThat(cmd.failed(text)).isTrue();
        // no newer release of the 1.9 line
        cmd.failOn = "patch";
        assertThat(cmd.failed(text)).isFalse();
        cmd.failOn = "status";
        assertThat(cmd.failed(text)).isFalse();
    }

    @Test
    void theTable() {
        List<DependencyRow> rows = rows();
        DependencyCheck.applyAvailable(rows, Map.of(
                "org.yaml:snakeyaml", List.of("2.5", "2.7"),
                "io.netty:netty-common", List.of("4.2.21.Final")));
        String table = DependencyOutdated.table(rows);
        assertThat(table).contains("ARTIFACT", "CAMEL", "STATUS", "NEWER", "LATEST", "VIA");
        assertThat(table).contains("org.yaml:snakeyaml").contains("override").contains("2.7 (major)");
        assertThat(table).contains("4.2.21.Final").contains("camel-netty");
        // no VIA column without a transitive one
        assertThat(DependencyOutdated.table(rows.subList(0, 2))).doesNotContain("VIA");
    }

    @Test
    void theJsonDocument() {
        DependencyOutdated cmd = new DependencyOutdated();
        cmd.failOn = "any";
        List<DependencyRow> rows = rows();
        JsonObject doc = cmd.jsonReport("project", null, rows, rows.subList(0, 1), true, null);
        String json = Jsoner.serialize(doc);
        assertThat(doc.getInteger("dependencies")).isEqualTo(3);
        assertThat(doc.getBoolean("failed")).isTrue();
        assertThat(json).contains("\"status\":\"override\"").contains("\"camelVersion\":\"2.5\"");
    }

    @Test
    void declaredVersionsOfARunningIntegration() {
        JsonObject status = (JsonObject) Jsoner.deserialize(
                "{\"properties\":{\"properties\":[{\"key\":\"camel.jbang.dependencies\","
                                                            + "\"value\":\"mvn:org.yaml:snakeyaml:1.33,org.apache.commons:commons-text:1.9\"}]}}",
                new JsonObject());
        assertThat(DependencyOutdated.declaredVersions(status)).containsExactly(
                Map.entry("org.yaml:snakeyaml", "1.33"), Map.entry("org.apache.commons:commons-text", "1.9"));
        assertThat(DependencyOutdated.declaredVersions(null)).isEmpty();
    }

    @Test
    void theVersionOfALoadedJar() {
        assertThat(DependencyOutdated.versionOf("org.yaml:snakeyaml", Map.of("org.yaml:snakeyaml", "1.33"),
                Set.of("1.33", "2.7"), "4.24.0")).isEqualTo("1.33");
        assertThat(DependencyOutdated.versionOf("org.apache.camel:camel-api", Map.of(),
                Set.of("4.18.0", "4.24.0"), "4.24.0")).isEqualTo("4.24.0");
        assertThat(DependencyOutdated.versionOf("org.slf4j:slf4j-api", Map.of(),
                Set.of("2.0.17", "2.0.9"), "4.24.0")).isEqualTo("2.0.17");
        assertThat(DependencyOutdated.camelVersionOf(rows())).isNull();
    }
}
