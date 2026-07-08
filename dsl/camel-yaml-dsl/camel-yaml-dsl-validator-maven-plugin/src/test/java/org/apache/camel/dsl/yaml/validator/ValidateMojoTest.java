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
package org.apache.camel.dsl.yaml.validator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.model.Resource;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidateMojoTest {

    @TempDir
    Path tempDir;

    @Test
    void findYamlRoutersScansCustomDirectories() throws IOException {
        Path customDir = tempDir.resolve("custom-routes");
        Files.createDirectories(customDir);
        Files.writeString(customDir.resolve("my-route.yaml"), "- route:\n    from:\n      uri: timer:yaml\n");

        MavenProject project = new MavenProject();
        project.setFile(new File(tempDir.toFile(), "pom.xml"));

        Set<File> yamlFiles = new LinkedHashSet<>();
        ValidateMojo.findYamlRouters(
                yamlFiles, false, ".yaml",
                List.of(customDir.toString()), project);

        assertEquals(1, yamlFiles.size());
        assertTrue(yamlFiles.iterator().next().getName().endsWith("my-route.yaml"));
    }

    @Test
    void findYamlRoutersScansRelativeCustomDirectories() throws IOException {
        Path customDir = tempDir.resolve("src/main/routes");
        Files.createDirectories(customDir);
        Files.writeString(customDir.resolve("route.yaml"), "- route:\n    from:\n      uri: timer:yaml\n");

        MavenProject project = new MavenProject();
        project.setFile(new File(tempDir.toFile(), "pom.xml"));

        Set<File> yamlFiles = new LinkedHashSet<>();
        ValidateMojo.findYamlRouters(
                yamlFiles, false, ".yaml",
                List.of("src/main/routes"), project);

        assertEquals(1, yamlFiles.size());
        assertTrue(yamlFiles.iterator().next().getName().endsWith("route.yaml"));
    }

    @Test
    void findYamlRoutersWithNullDirectories() {
        MavenProject project = new MavenProject();
        project.setFile(new File(tempDir.toFile(), "pom.xml"));

        Set<File> yamlFiles = new LinkedHashSet<>();
        ValidateMojo.findYamlRouters(
                yamlFiles, false, ".yaml",
                null, project);

        assertTrue(yamlFiles.isEmpty());
    }

    @Test
    void findYamlRoutersScansResourcesAndCustomDirectories() throws IOException {
        Path resourceDir = tempDir.resolve("src/main/resources");
        Files.createDirectories(resourceDir);
        Files.writeString(resourceDir.resolve("resource-route.yaml"), "- route:\n    from:\n      uri: timer:yaml\n");

        Path customDir = tempDir.resolve("custom");
        Files.createDirectories(customDir);
        Files.writeString(customDir.resolve("custom-route.yaml"), "- route:\n    from:\n      uri: timer:yaml\n");

        MavenProject project = new MavenProject();
        project.setFile(new File(tempDir.toFile(), "pom.xml"));

        Resource resource = new Resource();
        resource.setDirectory(resourceDir.toString());
        project.addResource(resource);

        Set<File> yamlFiles = new LinkedHashSet<>();
        ValidateMojo.findYamlRouters(
                yamlFiles, false, ".yaml",
                List.of(customDir.toString()), project);

        assertEquals(2, yamlFiles.size());
    }

    @Test
    void findYamlRoutersFiltersByCamelYamlExtension() throws IOException {
        Path customDir = tempDir.resolve("routes");
        Files.createDirectories(customDir);
        Files.writeString(customDir.resolve("my-route.camel.yaml"), "- route:\n    from:\n      uri: timer:yaml\n");
        Files.writeString(customDir.resolve("other.yaml"), "key: value\n");

        MavenProject project = new MavenProject();
        project.setFile(new File(tempDir.toFile(), "pom.xml"));

        Set<File> yamlFiles = new LinkedHashSet<>();
        ValidateMojo.findYamlRouters(
                yamlFiles, false, ".camel.yaml",
                List.of(customDir.toString()), project);

        assertEquals(1, yamlFiles.size());
        assertTrue(yamlFiles.iterator().next().getName().endsWith("my-route.camel.yaml"));
    }
}
