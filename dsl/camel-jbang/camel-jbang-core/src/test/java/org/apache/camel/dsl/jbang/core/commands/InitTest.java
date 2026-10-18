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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@UsesWorkingDirectory
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
        Path packageFolderInsideMavenProject
                = Files.createDirectories(workingDir.resolve("src/main/java/com/acme/demo"));

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

    @Test
    void initJavaInTheSourceFolderHasNoPackage() throws Exception {
        Path sourceFolder = Files.createDirectories(workingDir.resolve("src/main/java"));

        Init initCommand = new Init(new CamelJBangMain());
        CommandLine.populateCommand(initCommand, "MyRoute.java", "--dir=" + sourceFolder);

        int exit = initCommand.doCall();

        assertEquals(0, exit);
        List<String> lines = Files.readAllLines(sourceFolder.resolve("MyRoute.java"));
        assertEquals("import org.apache.camel.builder.RouteBuilder;", lines.get(0));
    }

    @Test
    void initJavaInAFolderThatOnlyStartsLikeTheSourceFolderHasNoPackage() throws Exception {
        Path folder = Files.createDirectories(workingDir.resolve("src/main/javafx/demo"));

        Init initCommand = new Init(new CamelJBangMain());
        CommandLine.populateCommand(initCommand, "MyRoute.java", "--dir=" + folder);

        int exit = initCommand.doCall();

        assertEquals(0, exit);
        List<String> lines = Files.readAllLines(folder.resolve("MyRoute.java"));
        assertEquals("import org.apache.camel.builder.RouteBuilder;", lines.get(0));
    }

    @Test
    void initXml() throws Exception {
        Init initCommand = new Init(new CamelJBangMain());
        CommandLine.populateCommand(initCommand, "my.camel.xml", "--dir=" + workingDir);

        int exit = initCommand.doCall();

        assertEquals(0, exit);
        String content = Files.readString(workingDir.resolve("my.camel.xml"));
        assertTrue(content.contains("<route"), content);
    }

    @Test
    void initKamelets() throws Exception {
        for (String name : List.of("my-source.kamelet.yaml", "my-sink.kamelet.yaml", "my-action.kamelet.yaml")) {
            Init initCommand = new Init(new CamelJBangMain());
            CommandLine.populateCommand(initCommand, name, "--dir=" + workingDir);

            int exit = initCommand.doCall();

            assertEquals(0, exit);
            String content = Files.readString(workingDir.resolve(name));
            assertTrue(content.contains("kind: Kamelet"), content);
            // named after the file
            assertTrue(content.contains("name: " + name.substring(0, name.indexOf('.'))), content);
        }
        assertTrue(Files.readString(workingDir.resolve("my-source.kamelet.yaml"))
                .contains("camel.apache.org/kamelet.type: \"source\""));
        assertTrue(Files.readString(workingDir.resolve("my-sink.kamelet.yaml"))
                .contains("camel.apache.org/kamelet.type: \"sink\""));
    }

    @Test
    void initPipe() throws Exception {
        Init initCommand = new Init(new CamelJBangMain());
        CommandLine.populateCommand(initCommand, "my.yaml", "--pipe", "--dir=" + workingDir);

        int exit = initCommand.doCall();

        assertEquals(0, exit);
        assertTrue(Files.readString(workingDir.resolve("my.yaml")).contains("kind: Pipe"));
    }

    @Test
    void initFailsWhenTheFileExists() throws Exception {
        Path existing = Files.writeString(workingDir.resolve("my.camel.yaml"), "- route: {}");
        StringPrinter printer = new StringPrinter();
        Init initCommand = new Init(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(initCommand, "my.camel.yaml", "--dir=" + workingDir);

        int exit = initCommand.doCall();

        assertEquals(1, exit);
        assertEquals("ERROR: File already exists: " + existing + " (use --force to overwrite it)", printer.getStderr());
        assertEquals("- route: {}", Files.readString(existing), "the file is kept as it was");
    }

    @Test
    void initForceOverwritesTheFile() throws Exception {
        Path existing = Files.writeString(workingDir.resolve("my.camel.yaml"), "- route: {}");
        // the same command without --force keeps the file, so it is --force that overwrites it
        Init withoutForce = new Init(new CamelJBangMain().withPrinter(new StringPrinter()));
        CommandLine.populateCommand(withoutForce, "my.camel.yaml", "--dir=" + workingDir);
        assertEquals(1, withoutForce.doCall());
        assertEquals("- route: {}", Files.readString(existing));

        Init initCommand = new Init(new CamelJBangMain());
        CommandLine.populateCommand(initCommand, "my.camel.yaml", "--dir=" + workingDir, "--force");

        int exit = initCommand.doCall();

        assertEquals(0, exit);
        assertTrue(Files.readString(existing).contains("from:"), "the file is the template now");
    }

    @Test
    void initPickerAsksBeforeOverwritingTheFile() throws Exception {
        Path existing = Files.writeString(workingDir.resolve("my.camel.yaml"), "- route: {}");
        StringPrinter printer = new StringPrinter();
        Init initCommand = new Init(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(initCommand, "--dir=" + workingDir);

        // the YAML route template, the file, and yes to overwriting it
        int exit = initCommand.interactivePicker(input("1", "1", "my.camel.yaml", "y"));

        assertEquals(0, exit);
        assertTrue(printer.getStdout().contains("File already exists: " + existing + ", overwrite it? [y/N]: "));
        assertTrue(Files.readString(existing).contains("from:"), "the file is the template now");
    }

    @Test
    void initPickerWithForceDoesNotAsk() throws Exception {
        Path existing = Files.writeString(workingDir.resolve("my.camel.yaml"), "- route: {}");
        StringPrinter printer = new StringPrinter();
        Init initCommand = new Init(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(initCommand, "--dir=" + workingDir, "--force");

        // no answer to give, as there is no question
        int exit = initCommand.interactivePicker(input("1", "1", "my.camel.yaml"));

        assertEquals(0, exit);
        assertFalse(printer.getStdout().contains("overwrite it?"), printer.getStdout());
        assertTrue(Files.readString(existing).contains("from:"), "the file is the template now");
    }

    @Test
    void initPickerKeepsTheFileWhenTheAnswerIsNo() throws Exception {
        Path existing = Files.writeString(workingDir.resolve("my.camel.yaml"), "- route: {}");
        StringPrinter printer = new StringPrinter();
        Init initCommand = new Init(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(initCommand, "--dir=" + workingDir);

        // no answer is no
        int exit = initCommand.interactivePicker(input("1", "1", "my.camel.yaml", ""));

        assertEquals(1, exit);
        assertEquals("ERROR: File already exists: " + existing + " (use --force to overwrite it)", printer.getStderr());
        assertEquals("- route: {}", Files.readString(existing), "the file is kept as it was");
    }

    @Test
    void initCleansTheDirectoryFirst() throws Exception {
        Path dir = Files.createDirectories(workingDir.resolve("project"));
        Path old = Files.writeString(dir.resolve("old.yaml"), "- route: {}");

        Init initCommand = new Init(new CamelJBangMain());
        CommandLine.populateCommand(initCommand, "my.camel.yaml", "--dir=" + dir, "--clean-dir");

        int exit = initCommand.doCall();

        assertEquals(0, exit);
        assertFalse(Files.exists(old), "the directory is cleaned first");
        assertTrue(Files.exists(dir.resolve("my.camel.yaml")));
    }

    @Test
    void initUnsupportedFileTypeIsAUsageError() throws Exception {
        StringPrinter printer = new StringPrinter();
        Init initCommand = new Init(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(initCommand, "notes.txt", "--dir=" + workingDir);

        int exit = initCommand.doCall();

        assertEquals(2, exit);
        assertTrue(printer.getStderr().contains("Unsupported file type: txt"), printer.getStderr());
        assertFalse(Files.exists(workingDir.resolve("notes.txt")));
    }

    @Test
    void initFromKameletNeedsAKameletFile() throws Exception {
        StringPrinter printer = new StringPrinter();
        Init initCommand = new Init(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(initCommand, "my.yaml", "--from-kamelet=timer-source", "--dir=" + workingDir);

        int exit = initCommand.doCall();

        assertEquals(2, exit);
        assertTrue(printer.getStderr().contains("file must have extension .kamelet.yaml"), printer.getStderr());
    }

    @Test
    void initListTemplates() throws Exception {
        StringPrinter printer = new StringPrinter();
        Init initCommand = new Init(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(initCommand, "--list");

        int exit = initCommand.doCall();

        assertEquals(0, exit);
        String output = printer.getOutput();
        assertTrue(output.contains("Available templates"), "Should contain header");
        assertTrue(output.contains("Routes:"), "Should contain Routes category");
        assertTrue(output.contains("Kamelets:"), "Should contain Kamelets category");
        assertTrue(output.contains("java"), "Should list java template");
        assertTrue(output.contains("yaml"), "Should list yaml template");
        assertTrue(output.contains("xml"), "Should list xml template");
        // Should not list internal templates
        assertFalse(output.contains("Dockerfile"), "Should not list Dockerfile templates");
        assertFalse(output.contains("pom"), "Should not list POM templates");
    }

    @Test
    void initWithoutFileAndWithoutList() throws Exception {
        StringPrinter printer = new StringPrinter();
        Init initCommand = new Init(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(initCommand);

        int exit = initCommand.doCall();

        assertEquals(2, exit);
        assertTrue(printer.getStderr().startsWith("ERROR: Missing required parameter: <file>"), printer.getStderr());
        assertEquals("", printer.getStdout());
    }

    private static InputStream input(String... lines) {
        return new ByteArrayInputStream((String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8));
    }
}
