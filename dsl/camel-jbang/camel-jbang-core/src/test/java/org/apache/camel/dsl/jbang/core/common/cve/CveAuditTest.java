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
package org.apache.camel.dsl.jbang.core.common.cve;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-27340: an audit without OSV.dev checks the Apache Camel security advisories of the catalog.
 */
class CveAuditTest {

    @Test
    void offlineTheCamelAdvisoriesOfTheCatalog() {
        // CVE-2026-49098 affects camel-kafka from 4.15.0 before 4.18.3
        List<Dependency> deps = List.of(new Dependency("org.apache.camel", "camel-kafka", "4.18.1"),
                new Dependency("com.example", "lib", "1.0"));

        CveAudit.Result result = CveAudit.audit(deps, Map.of(), Set.of("org.apache.camel:camel-kafka"), Map.of(),
                Set.of("kafka"), new DefaultCamelCatalog(), null);

        assertThat(result.osvError()).isNull();
        assertThat(result.findings()).anySatisfy(f -> {
            assertThat(f.canonicalId()).isEqualTo("CVE-2026-49098");
            assertThat(f.fixFor()).containsEntry("org.apache.camel:camel-kafka:4.18.1", "4.18.3");
            assertThat(f.declared()).contains("org.apache.camel:camel-kafka:4.18.1");
            assertThat(f.usedByRoutes()).isTrue();
        });
        assertThat(result.artifacts()).singleElement().satisfies(a -> {
            assertThat(a.gav()).isEqualTo("org.apache.camel:camel-kafka:4.18.1");
            assertThat(a.camelRelease()).isTrue();
        });
    }

    @Test
    void offlineAFixedCamelVersionIsClean() {
        CveAudit.Result result = CveAudit.audit(List.of(new Dependency("org.apache.camel", "camel-kafka", "4.18.3")),
                Map.of(), Set.of(), Map.of(), null, new DefaultCamelCatalog(), null);
        assertThat(result.findings()).noneMatch(f -> f.canonicalId().equals("CVE-2026-49098"));
    }

    @Test
    void theCoordinatesOfAJarInAMavenRepository() {
        assertThat(Dependency.fromJarPath(
                "/home/me/.m2/repository/org/apache/kafka/kafka-clients/3.9.0/kafka-clients-3.9.0.jar"))
                .isEqualTo(new Dependency("org.apache.kafka", "kafka-clients", "3.9.0"));
        assertThat(Dependency.fromJarPath("C:\\Users\\me\\.m2\\repository\\org\\yaml\\snakeyaml\\1.33\\snakeyaml-1.33.jar"))
                .isEqualTo(new Dependency("org.yaml", "snakeyaml", "1.33"));
        assertThat(Dependency.fromJarPath("/opt/app/lib/app.jar")).isNull();
        assertThat(Dependency.fromJarPath("/home/me/.m2/repository/org/yaml/snakeyaml/1.33/other.jar")).isNull();
        assertThat(Dependency.fromJarPath(null)).isNull();
    }
}
