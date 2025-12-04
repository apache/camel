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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class InitTest {

    private Path workingDir;

    @BeforeEach
    public void beforeEach() throws IOException {
        Path base = Paths.get("target");
        workingDir = Files.createTempDirectory(base, "camel-init");
    }

    @AfterEach
    public void afterEach() throws IOException {
        PathUtils.deleteDirectory(workingDir);
    }

    @Test
    void initBasicYaml() throws Exception {
        Init initCommand = new Init(new CamelJBangMain());
        CommandLine.populateCommand(initCommand, "my.camel.yaml");

        int exit = initCommand.doCall();

        assertEquals(0, exit);
        Path f = Paths.get("my.camel.yaml");
        assertTrue(Files.exists(f), "Yaml file not created: " + f);
        Files.delete(f);
    }

    @Test
    void initYamlInDirectory() throws Exception {
        Init initCommand = new Init(new CamelJBangMain());
        CommandLine.populateCommand(initCommand, "my.camel.yaml", "--dir=" + workingDir);

        int exit = initCommand.doCall();

        assertEquals(0, exit);
        Path f = workingDir.resolve("my.camel.yaml");
        assertTrue(Files.exists(f), "Yaml file not created: " + f);
    }

    @Test
    void initYamlInDirectoryWithExistingFiles() throws Exception {
        Path existingFileInTargetDirectory = Files.createFile(workingDir.resolve("anotherfile.txt"));
        assertTrue(Files.exists(existingFileInTargetDirectory), "Cannot create file to setup context");

        Init initCommand = new Init(new CamelJBangMain());
        CommandLine.populateCommand(initCommand, "my.camel.yaml", "--dir=" + workingDir);

        int exit = initCommand.doCall();

        assertEquals(0, exit);
        Path f = workingDir.resolve("my.camel.yaml");
        assertTrue(Files.exists(f), "Yaml file not created: " + f);

        assertTrue(Files.exists(existingFileInTargetDirectory), "The file in the target folder has been deleted");
    }

    @Test
    void initJavaWithPackageName() throws Exception {
        Path packageFolderInsideMavenProject =
                Files.createDirectories(workingDir.resolve("src/main/java/com/acme/demo"));

        Init initCommand = new Init(new CamelJBangMain());
        CommandLine.populateCommand(initCommand, "MyRoute.java", "--dir=" + packageFolderInsideMavenProject);

        int exit = initCommand.doCall();

        assertEquals(0, exit);
        Path f = packageFolderInsideMavenProject.resolve("MyRoute.java");
        assertTrue(Files.exists(f), "Java file not created: " + f);
        List<String> lines = Files.readAllLines(f);
        assertEquals("package com.acme.demo;", lines.get(0));
        assertEquals("", lines.get(1));
        assertEquals("import org.apache.camel.builder.RouteBuilder;", lines.get(2));
        Files.delete(f);
    }

    @Test
    void initJavaWithoutPackageName() throws Exception {
        Init initCommand = new Init(new CamelJBangMain());
        CommandLine.populateCommand(initCommand, "MyRoute.java");

        int exit = initCommand.doCall();

        assertEquals(0, exit);
        Path f = Paths.get("MyRoute.java");
        assertTrue(Files.exists(f), "Java file not created: " + f);
        List<String> lines = Files.readAllLines(f);
        assertEquals("import org.apache.camel.builder.RouteBuilder;", lines.get(0));
        Files.delete(f);
    }
}
