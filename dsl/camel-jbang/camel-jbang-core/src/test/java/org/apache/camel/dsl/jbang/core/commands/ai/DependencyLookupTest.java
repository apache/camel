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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.util.Map;
import java.util.function.Function;

import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DependencyLookupTest {

    private final ToolContext ctx = new ToolContext();

    @Test
    void aKnownThirdPartyClassAnswersItsCoordinatesAndNeedsNoDeclarationForCamelRun() {
        JsonObject a = DependencyLookup.lookup(ctx, "org.postgresql.ds.PGSimpleDataSource", null, false);
        assertThat(a.getString("source")).isEqualTo("known-dependencies");
        assertThat(a.getBoolean("autoDownload")).isTrue();
        assertThat(a.getString("groupId")).isEqualTo("org.postgresql");
        assertThat(a.getString("artifactId")).isEqualTo("postgresql");
        assertThat(a.getString("version")).doesNotStartWith("${");
        JsonObject d = a.getMap("declare");
        assertThat(d.getString("jbang")).startsWith("camel.jbang.dependencies=org.postgresql:postgresql:");
        assertThat(d.getString("cli")).startsWith("--dep=org.postgresql:postgresql:");
        assertThat(d.getString("pomMain")).contains("<artifactId>postgresql</artifactId>").contains("<version>");
        assertThat(d.getString("pomSpringBoot")).isEqualTo(d.getString("pomMain"));
        assertThat(d.getString("pomQuarkus")).isEqualTo(d.getString("pomMain"));
    }

    @Test
    void aCamelComponentClassAnswersTheArtifactOfEachRuntime() {
        JsonObject a = DependencyLookup.lookup(ctx, "org.apache.camel.component.kafka.KafkaComponent", null, false);
        assertThat(a.getString("source")).isEqualTo("camel-component");
        assertThat(a.getString("artifactId")).isEqualTo("camel-kafka");
        assertThat(a.getString("version")).isNotNull().doesNotContain("null");
        JsonObject d = a.getMap("declare");
        assertThat(d.getString("cli")).isEqualTo("--dep=camel:kafka");
        assertThat(d.getString("pomMain")).contains("<groupId>org.apache.camel</groupId>")
                .contains("<artifactId>camel-kafka</artifactId>").contains("camel-bom");
        assertThat(d.getString("pomSpringBoot")).contains("org.apache.camel.springboot")
                .contains("camel-kafka-starter").contains("camel-spring-boot-bom");
        assertThat(d.getString("pomQuarkus")).contains("org.apache.camel.quarkus").contains("camel-quarkus-kafka")
                .contains("camel-quarkus-bom");

        JsonObject only = DependencyLookup.lookup(ctx, "org.apache.camel.component.kafka.KafkaComponent",
                "spring-boot", false);
        JsonObject onlyDeclare = only.getMap("declare");
        assertThat(onlyDeclare.keySet().stream().map(String::valueOf).toList())
                .containsExactlyInAnyOrder("jbang", "cli", "pomSpringBoot");
    }

    @Test
    void anUnknownClassWithoutMavenCentralSaysHowToDeclareIt() {
        JsonObject a = DependencyLookup.lookup(ctx, "com.example.pool.NoSuchDataSource", null, false);
        assertThat(a.getString("source")).isEqualTo("unknown");
        assertThat(a.getBoolean("autoDownload")).isFalse();
        assertThat(a.getString("note")).contains("mavenCentral=true").contains("camel.jbang.dependencies");
        assertThat(a.containsKey("declare")).isFalse();
    }

    @Test
    void mavenCentralIsSearchedOnlyWhenAskedAndTheBestArtifactAndNewestVersionArePicked() {
        // canned answers: the class search lists old versions first and a shaded copy; the second query gives the
        // newest version of the chosen artifact
        Function<String, String> fetcher = url -> {
            if (url.contains("fc%3A") && url.contains("g%3A")) {
                // the group-prefix narrowed searches: nothing under com.example.pool or com.example, as on Central
                return "{\"response\":{\"numFound\":0,\"docs\":[]}}";
            }
            if (url.contains("fc%3A")) {
                return """
                        {"response":{"numFound":3,"docs":[
                          {"g":"com.example","a":"example-pool","v":"1.3.3"},
                          {"g":"org.acme","a":"acme-shaded-all","v":"9.9"},
                          {"g":"com.example","a":"example-pool","v":"1.3.2"}]}}""";
            }
            // the artifact's versions newest first: two pre-releases ahead of the newest release
            return """
                    {"response":{"numFound":4,"docs":[
                      {"g":"com.example","a":"example-pool","v":"8.0.0-alpha.2"},
                      {"g":"com.example","a":"example-pool","v":"8.0.0-alpha.1"},
                      {"g":"com.example","a":"example-pool","v":"7.0.2"},
                      {"g":"com.example","a":"example-pool","v":"7.0.1"}]}}""";
        };
        JsonObject a = DependencyLookup.lookup(ctx, "com.example.pool.PoolDataSource", "main", true, fetcher);
        assertThat(a.getString("source")).isEqualTo("maven-central");
        assertThat(a.getBoolean("autoDownload")).isFalse();
        assertThat(a.getString("groupId")).isEqualTo("com.example");
        assertThat(a.getString("artifactId")).isEqualTo("example-pool");
        assertThat(a.getString("version")).isEqualTo("7.0.2");
        assertThat(a.getString("note")).contains("Check it");
        assertThat(((java.util.Collection<?>) a.get("candidates")).size()).isEqualTo(2);
        JsonObject declare = a.getMap("declare");
        assertThat(declare.getString("pomMain")).contains("<version>7.0.2</version>");

        assertThat(DependencyLookup.newestStable(java.util.List.of("5.0.0-alpha.16", "4.12.0", "4.11.0"),
                java.util.List.of())).isEqualTo("4.12.0");
        assertThat(DependencyLookup.newestStable(java.util.List.of(), java.util.List.of("1.0", "1.2", "1.1")))
                .isEqualTo("1.2");
        assertThat(DependencyLookup.newestStable(java.util.List.of("2.0-RC1"), java.util.List.of("2.0-RC1")))
                .isEqualTo("2.0-RC1");
        assertThat(DependencyLookup.compareVersions("4.12.0", "4.9.1")).isPositive();

        assertThatThrownBy(() -> DependencyLookup.lookup(ctx, "com.example.pool", null, true, fetcher))
                .isInstanceOf(ToolExecutionException.class).hasMessageContaining("class, not a package");
        assertThatThrownBy(() -> DependencyLookup.lookup(ctx, "x.Y", "wildfly", false))
                .isInstanceOf(ToolExecutionException.class).hasMessageContaining("runtime must be");
    }

    @Test
    void theToolIsInTheSharedRegistryAndReadOnly() {
        ToolDescriptor td = ToolRegistry.findTool("camel_dependency_for_class");
        assertThat(td).isNotNull();
        assertThat(td.isReadOnly()).isTrue();
        assertThat(td.isCore()).isFalse();
        Object result = ToolRegistry.execute("camel_dependency_for_class", ctx,
                Map.of("className", "#class:org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory"));
        assertThat(((JsonObject) result).getString("artifactId")).isEqualTo("artemis-jakarta-client-all");
    }
}
