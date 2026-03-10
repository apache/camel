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

import io.quarkiverse.mcp.server.TextResourceContents;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DependencyResourcesTest {

    private final DependencyResources resources;

    DependencyResourcesTest() {
        resources = new DependencyResources();
        resources.dependencyData = new DependencyData();
    }

    // ---- Core transitive artifacts ----

    @Test
    void coreTransitiveArtifactsReturnsValidJson() throws Exception {
        TextResourceContents contents = resources.coreTransitiveArtifacts();

        assertThat(contents.uri()).isEqualTo("camel://dependency/core-transitive-artifacts");
        assertThat(contents.mimeType()).isEqualTo("application/json");

        JsonObject result = (JsonObject) Jsoner.deserialize(contents.text());
        assertThat(result.getInteger("totalCount")).isGreaterThan(0);

        JsonArray artifacts = result.getCollection("artifacts");
        assertThat(artifacts).isNotEmpty();
    }

    @Test
    void coreTransitiveArtifactsContainsKnownEntries() throws Exception {
        TextResourceContents contents = resources.coreTransitiveArtifacts();
        JsonObject result = (JsonObject) Jsoner.deserialize(contents.text());
        JsonArray artifacts = result.getCollection("artifacts");

        assertThat(artifacts).contains("camel-core", "camel-direct", "camel-log",
                "camel-seda", "camel-timer", "camel-api", "camel-support");
    }

    @Test
    void coreTransitiveArtifactsAreSorted() throws Exception {
        TextResourceContents contents = resources.coreTransitiveArtifacts();
        JsonObject result = (JsonObject) Jsoner.deserialize(contents.text());
        JsonArray artifacts = result.getCollection("artifacts");

        for (int i = 1; i < artifacts.size(); i++) {
            String prev = (String) artifacts.get(i - 1);
            String curr = (String) artifacts.get(i);
            assertThat(prev.compareTo(curr))
                    .as("Expected sorted order: '%s' should come before '%s'", prev, curr)
                    .isLessThanOrEqualTo(0);
        }
    }

    @Test
    void coreTransitiveArtifactsCountMatchesData() throws Exception {
        TextResourceContents contents = resources.coreTransitiveArtifacts();
        JsonObject result = (JsonObject) Jsoner.deserialize(contents.text());

        DependencyData data = new DependencyData();
        assertThat(result.getInteger("totalCount")).isEqualTo(data.getCoreTransitiveArtifacts().size());
    }

    // ---- BOM template for known runtimes ----

    @Test
    void bomTemplateReturnsMainRuntime() throws Exception {
        TextResourceContents contents = resources.bomTemplate("main");

        assertThat(contents.uri()).isEqualTo("camel://dependency/bom-template/main");
        assertThat(contents.mimeType()).isEqualTo("application/json");

        JsonObject result = (JsonObject) Jsoner.deserialize(contents.text());
        assertThat(result.getString("runtime")).isEqualTo("main");
        assertThat(result.getBoolean("found")).isTrue();
        assertThat(result.getString("groupId")).isEqualTo("org.apache.camel");
        assertThat(result.getString("artifactId")).isEqualTo("camel-bom");
        assertThat(result.getString("snippet")).contains("<dependencyManagement>");
        assertThat(result.getString("snippet")).contains("camel-bom");
    }

    @Test
    void bomTemplateReturnsSpringBootRuntime() throws Exception {
        TextResourceContents contents = resources.bomTemplate("spring-boot");
        JsonObject result = (JsonObject) Jsoner.deserialize(contents.text());

        assertThat(result.getBoolean("found")).isTrue();
        assertThat(result.getString("groupId")).isEqualTo("org.apache.camel.springboot");
        assertThat(result.getString("artifactId")).isEqualTo("camel-spring-boot-bom");
        assertThat(result.getString("snippet")).contains("camel-spring-boot-bom");
    }

    @Test
    void bomTemplateReturnsQuarkusRuntime() throws Exception {
        TextResourceContents contents = resources.bomTemplate("quarkus");
        JsonObject result = (JsonObject) Jsoner.deserialize(contents.text());

        assertThat(result.getBoolean("found")).isTrue();
        assertThat(result.getString("groupId")).isEqualTo("org.apache.camel.quarkus");
        assertThat(result.getString("artifactId")).isEqualTo("camel-quarkus-bom");
        assertThat(result.getString("snippet")).contains("camel-quarkus-bom");
    }

    // ---- BOM template not found ----

    @Test
    void bomTemplateReturnsNotFoundForUnknownRuntime() throws Exception {
        TextResourceContents contents = resources.bomTemplate("unknown");

        assertThat(contents.uri()).isEqualTo("camel://dependency/bom-template/unknown");

        JsonObject result = (JsonObject) Jsoner.deserialize(contents.text());
        assertThat(result.getString("runtime")).isEqualTo("unknown");
        assertThat(result.getBoolean("found")).isFalse();
        assertThat(result.getString("message")).contains("Unknown runtime");

        JsonArray available = result.getCollection("availableRuntimes");
        assertThat(available).contains("main", "spring-boot", "quarkus");
    }
}
