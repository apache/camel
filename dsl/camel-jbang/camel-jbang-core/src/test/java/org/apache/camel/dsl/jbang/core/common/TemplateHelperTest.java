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
package org.apache.camel.dsl.jbang.core.common;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateHelperTest {

    // ===== TemplateHelper.processTemplate() tests (FreeMarker-processed templates) =====

    @Test
    void testCodeJavaTemplate() throws IOException {
        Map<String, Object> model = new HashMap<>();
        model.put("Name", "MyRoute");
        model.put("Code", "from(\"timer:tick\").log(\"Hello\");");

        String result = TemplateHelper.processTemplate("code-java.ftl", model);

        assertNoLicenseHeader(result);
        assertNoUnresolvedInterpolations(result);
        assertTrue(result.contains("class MyRoute"), "Should contain class name");
        assertTrue(result.contains("from(\"timer:tick\").log(\"Hello\");"), "Should contain code");
    }

    @Test
    void testMainClassTemplate() throws IOException {
        Map<String, Object> model = new HashMap<>();
        model.put("PackageName", "package com.example;");
        model.put("MainClassname", "MyApp");

        String result = TemplateHelper.processTemplate("main.ftl", model);

        assertNoLicenseHeader(result);
        assertNoUnresolvedInterpolations(result);
        assertTrue(result.contains("package com.example;"));
        assertTrue(result.contains("class MyApp"));
        assertTrue(result.contains("Main main = new Main(MyApp.class)"));
    }

    @Test
    void testMainClassTemplateWithoutPackage() throws IOException {
        Map<String, Object> model = new HashMap<>();
        model.put("MainClassname", "MyApp");

        String result = TemplateHelper.processTemplate("main.ftl", model);

        assertFalse(result.contains("package "), "Should not contain package declaration");
        assertTrue(result.contains("class MyApp"));
    }

    @Test
    void testSpringBootMainTemplate() throws IOException {
        Map<String, Object> model = new HashMap<>();
        model.put("PackageName", "com.example");
        model.put("MainClassname", "MySpringApp");

        String result = TemplateHelper.processTemplate("spring-boot-main.ftl", model);

        assertNoLicenseHeader(result);
        assertNoUnresolvedInterpolations(result);
        assertTrue(result.contains("package com.example;"));
        assertTrue(result.contains("@SpringBootApplication"));
        assertTrue(result.contains("class MySpringApp"));
        assertTrue(result.contains("SpringApplication.run(MySpringApp.class"));
    }

    @Test
    void testDockerfile21Template() throws IOException {
        Map<String, Object> model = new HashMap<>();
        model.put("ArtifactId", "my-app");
        model.put("Version", "1.0.0");
        model.put("AppJar", "my-app-1.0.0.jar");

        String result = TemplateHelper.processTemplate("Dockerfile21.ftl", model);

        assertNoLicenseHeader(result);
        assertNoUnresolvedInterpolations(result);
        assertTrue(result.contains("my-app-1.0.0.jar"), "Should contain app jar");
    }

    @Test
    void testDockerfile25Template() throws IOException {
        Map<String, Object> model = new HashMap<>();
        model.put("ArtifactId", "my-app");
        model.put("Version", "2.0.0");
        model.put("AppJar", "my-app-2.0.0.jar");

        String result = TemplateHelper.processTemplate("Dockerfile25.ftl", model);

        assertNoLicenseHeader(result);
        assertNoUnresolvedInterpolations(result);
        assertTrue(result.contains("my-app-2.0.0.jar"));
    }

    @Test
    void testReadmeTemplate() throws IOException {
        Map<String, Object> model = new HashMap<>();
        model.put("ArtifactId", "my-app");
        model.put("Version", "1.0.0");
        model.put("AppRuntimeJar", "my-app-1.0.0.jar");

        String result = TemplateHelper.processTemplate("readme.md.ftl", model);

        assertNoLicenseHeader(result);
        assertNoUnresolvedInterpolations(result);
        assertTrue(result.contains("my-app"));
    }

    @Test
    void testReadmeNativeTemplate() throws IOException {
        Map<String, Object> model = new HashMap<>();
        model.put("ArtifactId", "my-app");
        model.put("Version", "1.0.0");
        model.put("AppRuntimeJar", "my-app-1.0.0.jar");

        String result = TemplateHelper.processTemplate("readme.native.md.ftl", model);

        assertNoLicenseHeader(result);
        assertNoUnresolvedInterpolations(result);
        assertTrue(result.contains("my-app"));
    }

    @Test
    void testRestDslYamlTemplate() throws IOException {
        Map<String, Object> model = new HashMap<>();
        model.put("Spec", "openapi.json");

        String result = TemplateHelper.processTemplate("rest-dsl.yaml.ftl", model);

        assertNoLicenseHeader(result);
        assertNoUnresolvedInterpolations(result);
        assertTrue(result.contains("openapi.json"));
    }

    @Test
    void testRunCustomCamelVersionTemplate() throws IOException {
        Map<String, Object> model = new HashMap<>();
        model.put("JavaVersion", "21");
        model.put("MavenRepositories", "//REPOS https://repo.example.com/maven");
        model.put("CamelDependencies", "//DEPS org.apache.camel:camel-core:4.10.0\n");
        model.put("CamelJBangDependencies", "//DEPS org.apache.camel:camel-jbang-core:4.10.0\n");
        model.put("CamelKameletsDependencies", "//DEPS org.apache.camel.kamelets:camel-kamelets:4.10.0\n");

        String result = TemplateHelper.processTemplate("run-custom-camel-version.ftl", model);

        assertNoLicenseHeader(result);
        assertNoUnresolvedInterpolations(result);
        assertTrue(result.contains("//JAVA 21+"));
        assertTrue(result.contains("//REPOS https://repo.example.com/maven"));
        assertTrue(result.contains("camel-core:4.10.0"));
        assertTrue(result.contains("camel-jbang-core:4.10.0"));
        assertTrue(result.contains("camel-kamelets:4.10.0"));
        assertTrue(result.contains("class CustomCamelJBang"));
    }

    @Test
    void testMainPomTemplateWithDependencies() throws IOException {
        Map<String, Object> model = new HashMap<>();
        model.put("GroupId", "com.example");
        model.put("ArtifactId", "my-app");
        model.put("Version", "1.0.0");
        model.put("JavaVersion", "21");
        model.put("CamelVersion", "4.10.0");
        model.put("MainClassname", "com.example.MyApp");
        model.put("ProjectBuildOutputTimestamp", "2024-01-01T00:00:00Z");
        model.put("BuildProperties", "");
        model.put("Repositories", List.of());
        model.put("KubernetesProperties", List.of());
        model.put("hasJib", false);
        model.put("hasJkube", false);

        List<Map<String, Object>> deps = new ArrayList<>();
        Map<String, Object> dep = new HashMap<>();
        dep.put("groupId", "org.apache.camel");
        dep.put("artifactId", "camel-timer");
        dep.put("isLib", false);
        dep.put("isKameletsUtils", false);
        deps.add(dep);
        Map<String, Object> libDep = new HashMap<>();
        libDep.put("groupId", "com.example");
        libDep.put("artifactId", "my-lib");
        libDep.put("version", "1.0");
        libDep.put("isLib", true);
        libDep.put("isKameletsUtils", false);
        deps.add(libDep);
        model.put("Dependencies", deps);

        String result = TemplateHelper.processTemplate("main-pom.ftl", model);

        assertNoLicenseHeader(result);
        assertTrue(result.contains("<groupId>com.example</groupId>"));
        assertTrue(result.contains("<artifactId>my-app</artifactId>"));
        assertTrue(result.contains("<version>4.10.0</version>"), "Should contain Camel version in BOM");
        assertTrue(result.contains("<artifactId>camel-timer</artifactId>"));
        assertTrue(result.contains("<mainClass>com.example.MyApp</mainClass>"));
        // Maven ${...} expressions should pass through
        assertTrue(result.contains("${java.version}"));
        // lib dependency should use system scope with ${project.basedir}
        assertTrue(result.contains("${project.basedir}"), "Lib dep should have project.basedir system path");
        assertTrue(result.contains("<scope>system</scope>"));
        // No jib/jkube plugins
        assertFalse(result.contains("jib-maven-plugin"));
        assertFalse(result.contains("kubernetes-maven-plugin"));
    }

    @Test
    void testMainPomTemplateWithRepositories() throws IOException {
        Map<String, Object> model = new HashMap<>();
        model.put("GroupId", "com.example");
        model.put("ArtifactId", "my-app");
        model.put("Version", "1.0.0");
        model.put("JavaVersion", "21");
        model.put("CamelVersion", "4.10.0");
        model.put("MainClassname", "MyApp");
        model.put("ProjectBuildOutputTimestamp", "2024-01-01T00:00:00Z");
        model.put("BuildProperties", "");
        model.put("KubernetesProperties", List.of());
        model.put("hasJib", false);
        model.put("hasJkube", false);
        model.put("Dependencies", List.of());

        List<Map<String, Object>> repos = new ArrayList<>();
        repos.add(Map.of("id", "custom1", "url", "https://repo.example.com/snapshots", "isSnapshot", true));
        model.put("Repositories", repos);

        String result = TemplateHelper.processTemplate("main-pom.ftl", model);

        assertTrue(result.contains("<id>custom1</id>"));
        assertTrue(result.contains("<url>https://repo.example.com/snapshots</url>"));
        assertTrue(result.contains("<id>plugin-custom1</id>"), "Plugin repositories should use plugin- prefix");
        assertTrue(result.contains("<enabled>true</enabled>"), "Snapshot repos should enable snapshots");
    }

    @Test
    void testSpringBootPomTemplate() throws IOException {
        Map<String, Object> model = new HashMap<>();
        model.put("GroupId", "com.example");
        model.put("ArtifactId", "my-sb-app");
        model.put("Version", "1.0.0");
        model.put("SpringBootVersion", "3.4.0");
        model.put("JavaVersion", "21");
        model.put("CamelSpringBootVersion", "4.10.0");
        model.put("ProjectBuildOutputTimestamp", "2024-01-01T00:00:00Z");
        model.put("BuildProperties", "");
        model.put("Repositories", List.of());
        model.put("Dependencies", List.of());

        String result = TemplateHelper.processTemplate("spring-boot-pom.ftl", model);

        assertNoLicenseHeader(result);
        assertTrue(result.contains("<version>3.4.0</version>"), "Should contain Spring Boot version");
        assertTrue(result.contains("camel-spring-boot-bom"));
        assertTrue(result.contains("<version>4.10.0</version>"), "Should contain Camel SB version");
        assertTrue(result.contains("spring-boot-maven-plugin"));
    }

    @Test
    void testQuarkusPomTemplate() throws IOException {
        Map<String, Object> model = new HashMap<>();
        model.put("GroupId", "com.example");
        model.put("ArtifactId", "my-quarkus-app");
        model.put("Version", "1.0.0");
        model.put("QuarkusGroupId", "io.quarkus.platform");
        model.put("QuarkusArtifactId", "quarkus-bom");
        model.put("QuarkusVersion", "3.17.0");
        model.put("QuarkusPackageType", "uber-jar");
        model.put("JavaVersion", "21");
        model.put("ProjectBuildOutputTimestamp", "2024-01-01T00:00:00Z");
        model.put("BuildProperties", "");
        model.put("Repositories", List.of());
        model.put("Dependencies", List.of());

        String result = TemplateHelper.processTemplate("quarkus-pom.ftl", model);

        assertNoLicenseHeader(result);
        assertTrue(result.contains("io.quarkus.platform"));
        assertTrue(result.contains("quarkus-bom"));
        assertTrue(result.contains("3.17.0"));
        assertTrue(result.contains("<quarkus.package.jar.type>uber-jar</quarkus.package.jar.type>"),
                "Should contain QuarkusPackageType");
        // Maven ${...} should pass through
        assertTrue(result.contains("${quarkus.platform.version}"));
    }

    @Test
    void testMissingTemplateThrowsIOException() {
        Map<String, Object> model = new HashMap<>();
        assertThrows(IOException.class, () -> TemplateHelper.processTemplate("nonexistent.ftl", model));
    }

    // ===== Init template tests (loaded as raw text, NOT via FreeMarker) =====
    // These templates are loaded by Init.java as raw text with String.replace().
    // We test the same loading logic: load, strip header, replace placeholders.

    @Test
    void testInitJavaTemplate() throws IOException {
        String content = loadInitTemplate("java.ftl");

        assertNoLicenseHeader(content);
        // Verify placeholders are present before replacement
        assertTrue(content.contains("[=PackageDeclaration]") || content.contains("{{ .PackageDeclaration }}"),
                "Should have PackageDeclaration placeholder");
        assertTrue(content.contains("[=Name]") || content.contains("{{ .Name }}"),
                "Should have Name placeholder");

        // Simulate Init.java replacement
        content = content.replace("{{ .Name }}", "MyRoute");
        content = content.replace("[=Name]", "MyRoute");
        content = content.replace("{{ .PackageDeclaration }}", "package com.example;\n");
        content = content.replace("[=PackageDeclaration]", "package com.example;\n");

        assertTrue(content.contains("class MyRoute extends RouteBuilder"));
        assertTrue(content.contains("package com.example;"));
        // Camel ${...} expressions should be untouched
        assertTrue(content.contains("${routeId}"));
        assertTrue(content.contains("${body}"));
    }

    @Test
    void testInitYamlTemplate() throws IOException {
        String content = loadInitTemplate("yaml.ftl");

        assertNoLicenseHeader(content);
        // yaml template has no placeholders — it's static content
        assertTrue(content.contains("uri: timer:yaml"));
        // Camel ${...} expressions should be untouched
        assertTrue(content.contains("${routeId}"));
        assertTrue(content.contains("${body}"));
    }

    @Test
    void testInitXmlTemplate() throws IOException {
        String content = loadInitTemplate("xml.ftl");

        assertNoLicenseHeader(content);
        assertTrue(content.contains("<?xml version="));
        assertTrue(content.contains("<route>"));
        assertTrue(content.contains("timer:xml"));
        // Camel ${...} expressions should be untouched
        assertTrue(content.contains("${routeId}"));
        assertTrue(content.contains("${body}"));
    }

    @Test
    void testInitKameletSourceTemplate() throws IOException {
        String content = loadInitTemplate("kamelet-source.yaml.ftl");

        assertNoLicenseHeader(content);
        assertTrue(content.contains("[=Name]"), "Should have Name placeholder");

        content = content.replace("[=Name]", "my-source");
        assertTrue(content.contains("name: my-source"));
        assertTrue(content.contains("kamelet.type: \"source\""));
        // Kamelet {{...}} expressions should be untouched
        assertTrue(content.contains("\"{{period}}\""));
        assertTrue(content.contains("\"{{message}}\""));
    }

    @Test
    void testInitKameletSinkTemplate() throws IOException {
        String content = loadInitTemplate("kamelet-sink.yaml.ftl");

        assertNoLicenseHeader(content);
        content = content.replace("[=Name]", "my-sink");
        assertTrue(content.contains("name: my-sink"));
        assertTrue(content.contains("kamelet.type: \"sink\""));
    }

    @Test
    void testInitKameletActionTemplate() throws IOException {
        String content = loadInitTemplate("kamelet-action.yaml.ftl");

        assertNoLicenseHeader(content);
        content = content.replace("[=Name]", "my-action");
        assertTrue(content.contains("name: my-action"));
        assertTrue(content.contains("kamelet.type: \"action\""));
    }

    @Test
    void testInitPipeTemplate() throws IOException {
        String content = loadInitTemplate("init-pipe.yaml.ftl");

        assertNoLicenseHeader(content);
        content = content.replace("[=Name]", "my-pipe");
        assertTrue(content.contains("kind: Pipe"));
        assertTrue(content.contains("name: my-pipe"));
        assertTrue(content.contains("timer-source"));
        assertTrue(content.contains("log-sink"));
    }

    @Test
    void testInitIntegrationTemplate() throws IOException {
        String content = loadInitTemplate("integration.yaml.ftl");

        assertNoLicenseHeader(content);
        content = content.replace("[=Name]", "my-integration");
        assertTrue(content.contains("kind: Integration"));
        assertTrue(content.contains("name: my-integration"));
    }

    // ===== Helper methods =====

    /**
     * Loads a template the same way Init.java does: raw text + header stripping.
     */
    private String loadInitTemplate(String name) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("templates/" + name)) {
            String content = IOHelper.loadText(is);
            // Strip FreeMarker license header (same regex as Init.java)
            content = content.replaceFirst("(?s)\\A<#--.*?-->\\s*", "");
            return content;
        }
    }

    private void assertNoLicenseHeader(String content) {
        assertFalse(content.contains("<#--"), "License header should be stripped");
        assertFalse(content.startsWith("Licensed to"), "Should not start with license text");
    }

    private void assertNoUnresolvedInterpolations(String content) {
        assertFalse(content.contains("[="), "Should not contain unresolved [= interpolations");
        assertFalse(content.contains("[#"), "Should not contain unresolved [# directives");
    }
}
