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
package org.apache.camel.dsl.jbang.run.export;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExporterTest {

    @Test
    public void shouldBuildRepositoryListWithIdUrlFormat() {
        var repos = Exporter.buildRepositoryList(
                "atlassian=https://packages.atlassian.com/maven-external/,https://other.repo/releases");

        assertThat(repos).hasSize(2);
        assertThat(repos.get(0))
                .containsEntry("id", "atlassian")
                .containsEntry("url", "https://packages.atlassian.com/maven-external/");
        assertThat(repos.get(1))
                .containsEntry("id", "custom1")
                .containsEntry("url", "https://other.repo/releases");
    }

    @Test
    public void aDriveOfWindowsIsNoScheme() {
        assertThat(Exporter.getScheme("classpath:tls/keystore.jks")).isEqualTo("classpath");
        assertThat(Exporter.getScheme("file:C:\\certs\\keystore.jks")).isEqualTo("file");
        assertThat(Exporter.getScheme("github:apache:camel-kamelets-examples:jbang/hello-java")).isEqualTo("github");
        // the export kept \certs\keystore.jks of it, which is not the file
        assertThat(Exporter.getScheme("C:\\certs\\keystore.jks")).isNull();
        assertThat(Exporter.getScheme("c:/certs/keystore.jks")).isNull();
        assertThat(Exporter.getScheme("route.camel.yaml")).isNull();
    }

    @Test
    public void shouldKeepVersionForCamelTestInfraInBuildDependencyList() {
        ExportRequest request = new ExportRequest();
        request.camelVersion = "4.24.0";

        TestableExporter exporter = new TestableExporter(request);
        List<Map<String, Object>> deps = exporter.buildDependencyList(
                Set.of("mvn@test:org.apache.camel:camel-test-infra-kafka:4.23.0"));

        assertThat(deps).hasSize(1);
        Map<String, Object> dep = deps.get(0);
        assertThat(dep)
                .containsEntry("groupId", "org.apache.camel")
                .containsEntry("artifactId", "camel-test-infra-kafka")
                .containsEntry("version", "4.24.0");
    }

    @Test
    public void shouldStripVersionForRegularCamelDependencies() {
        ExportRequest request = new ExportRequest();
        request.camelVersion = "4.24.0";

        TestableExporter exporter = new TestableExporter(request);
        List<Map<String, Object>> deps = exporter.buildDependencyList(
                Set.of("mvn@test:org.apache.camel:camel-kafka"));

        assertThat(deps).hasSize(1);
        Map<String, Object> dep = deps.get(0);
        assertThat(dep)
                .containsEntry("groupId", "org.apache.camel")
                .containsEntry("artifactId", "camel-kafka")
                .containsEntry("version", null);
    }

    private static class TestableExporter extends Exporter {
        TestableExporter(ExportRequest request) {
            super(request, new ExportContext());
        }

        @Override
        public Integer export() {
            return 0;
        }
    }
}
