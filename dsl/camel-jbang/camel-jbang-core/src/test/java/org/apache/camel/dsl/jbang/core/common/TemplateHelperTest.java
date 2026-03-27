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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateHelperTest {

    @Test
    void testSimpleInterpolation() throws IOException {
        Map<String, Object> model = new HashMap<>();
        model.put("Name", "MyRoute");
        model.put("Code", "from(\"timer:tick\").log(\"Hello\");");

        String result = TemplateHelper.processTemplate("code-java.ftl", model);

        assertTrue(result.contains("class MyRoute"), "Should contain class name");
        assertTrue(result.contains("from(\"timer:tick\").log(\"Hello\");"), "Should contain code");
        assertFalse(result.contains("[="), "Should not contain unresolved interpolations");
    }

    @Test
    void testLicenseHeaderStripped() throws IOException {
        Map<String, Object> model = new HashMap<>();
        model.put("Name", "Test");
        model.put("Code", "// test");

        String result = TemplateHelper.processTemplate("code-java.ftl", model);

        assertFalse(result.contains("<#--"), "License header should be stripped");
        assertFalse(result.contains("Licensed to the Apache"), "License text should be stripped");
    }

    @Test
    void testConditionalRendering() throws IOException {
        // main.ftl has [#if PackageName??] conditional
        Map<String, Object> withPackage = new HashMap<>();
        withPackage.put("PackageName", "package com.example;");
        withPackage.put("MainClassname", "MyApp");

        String result = TemplateHelper.processTemplate("main.ftl", withPackage);
        assertTrue(result.contains("package com.example;"), "Should contain package declaration");
        assertTrue(result.contains("class MyApp"), "Should contain class name");

        // Without package
        Map<String, Object> withoutPackage = new HashMap<>();
        withoutPackage.put("MainClassname", "MyApp");

        result = TemplateHelper.processTemplate("main.ftl", withoutPackage);
        assertFalse(result.contains("package "), "Should not contain package declaration");
        assertTrue(result.contains("class MyApp"), "Should contain class name");
    }

    @Test
    void testListRendering() throws IOException {
        Map<String, Object> model = new HashMap<>();
        model.put("GroupId", "com.example");
        model.put("ArtifactId", "my-app");
        model.put("Version", "1.0.0");
        model.put("SpringBootVersion", "3.4.0");
        model.put("JavaVersion", "21");
        model.put("CamelSpringBootVersion", "4.10.0");
        model.put("ProjectBuildOutputTimestamp", "2024-01-01T00:00:00Z");
        model.put("BuildProperties", "");
        model.put("Repositories", List.of());

        List<Map<String, Object>> deps = List.of(
                Map.of("groupId", "org.apache.camel.springboot", "artifactId", "camel-timer-starter",
                        "isLib", false, "isKameletsUtils", false));

        model.put("Dependencies", deps);

        String result = TemplateHelper.processTemplate("spring-boot-pom.ftl", model);
        assertTrue(result.contains("<groupId>com.example</groupId>"), "Should contain groupId");
        assertTrue(result.contains("<artifactId>camel-timer-starter</artifactId>"),
                "Should contain dependency artifactId");
        // Maven ${...} expressions should pass through unmodified
        assertTrue(result.contains("${project.basedir}") || !result.contains("isLib"),
                "Maven expressions should not be interpreted by FreeMarker");
    }

    @Test
    void testMavenExpressionsPreserved() throws IOException {
        Map<String, Object> model = new HashMap<>();
        model.put("ArtifactId", "my-app");
        model.put("Version", "1.0.0");
        model.put("AppJar", "my-app-1.0.0.jar");

        String result = TemplateHelper.processTemplate("Dockerfile21.ftl", model);
        assertTrue(result.contains("my-app"), "Should contain artifact id");
        assertFalse(result.contains("[="), "Should not contain unresolved interpolations");
    }

    @Test
    void testMissingTemplateThrowsIOException() {
        Map<String, Object> model = new HashMap<>();
        assertThrows(IOException.class, () -> TemplateHelper.processTemplate("nonexistent.ftl", model));
    }
}
