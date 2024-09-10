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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.camel.util.FileUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InitTest {

    private File workingDir;

    @BeforeEach
    public void beforeEach() throws IOException {
        workingDir = Files.createTempDirectory("camel-init").toFile();
    }

    @AfterEach
    public void afterEach() {
        FileUtil.removeDir(workingDir);
    }

    @Test
    void initBasicYaml() throws Exception {
        Init initCommand = new Init(new CamelJBangMain());
        CommandLine.populateCommand(initCommand, "my.camel.yaml");

        int exit = initCommand.doCall();

        assertEquals(0, exit);
        File f = new File("my.camel.yaml");
        assertTrue(f.exists(), "Yaml file not created: " + f);
        f.delete();
    }

    @Test
    void initYamlInDirectory() throws Exception {
        Init initCommand = new Init(new CamelJBangMain());
        CommandLine.populateCommand(initCommand, "my.camel.yaml", "--dir=" + workingDir);

        int exit = initCommand.doCall();

        assertEquals(0, exit);
        File f = new File(workingDir, "my.camel.yaml");
        assertTrue(f.exists(), "Yaml file not created: " + f);
    }

    @Test
    void initYamlInDirectoryWithExistingFiles() throws Exception {
        Path existingFileInTargetDirectory = Files.createFile(new File(workingDir, "anotherfile.txt").toPath());
        assertTrue(existingFileInTargetDirectory.toFile().exists(), "Cannot create file to setup context");

        Init initCommand = new Init(new CamelJBangMain());
        CommandLine.populateCommand(initCommand, "my.camel.yaml", "--dir=" + workingDir);

        int exit = initCommand.doCall();

        assertEquals(0, exit);
        File f = new File(workingDir, "my.camel.yaml");
        assertTrue(f.exists(), "Yaml file not created: " + f);

        assertTrue(existingFileInTargetDirectory.toFile().exists(), "The file in the target folder has been deleted");
    }

    @Test
    void initJavaWithPackageName() throws Exception {
        Path packageFolderInsideMavenProject
                = Files.createDirectories(new File(workingDir, "src/main/java/com/acme/demo").toPath());

        Init initCommand = new Init(new CamelJBangMain());
        CommandLine.populateCommand(initCommand, "MyRoute.java", "--dir=" + packageFolderInsideMavenProject);

        int exit = initCommand.doCall();

        assertEquals(0, exit);
        File f = new File(packageFolderInsideMavenProject.toFile(), "MyRoute.java");
        assertTrue(f.exists(), "Java file not created: " + f);
        List<String> lines = Files.readAllLines(f.toPath());
        assertEquals("package com.acme.demo;", lines.get(0));
        assertEquals("", lines.get(1));
        assertEquals("import org.apache.camel.builder.RouteBuilder;", lines.get(2));
        f.delete();
    }

    @Test
    void initJavaWithoutPackageName() throws Exception {
        Init initCommand = new Init(new CamelJBangMain());
        CommandLine.populateCommand(initCommand, "MyRoute.java");

        int exit = initCommand.doCall();

        assertEquals(0, exit);
        File f = new File("MyRoute.java");
        assertTrue(f.exists(), "Java file not created: " + f);
        List<String> lines = Files.readAllLines(f.toPath());
        assertEquals("import org.apache.camel.builder.RouteBuilder;", lines.get(0));
        f.delete();
    }
}
