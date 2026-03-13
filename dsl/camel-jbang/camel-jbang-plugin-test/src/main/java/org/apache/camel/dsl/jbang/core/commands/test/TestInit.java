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
package org.apache.camel.dsl.jbang.core.commands.test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.ExportHelper;
import org.apache.camel.util.IOHelper;
import org.citrusframework.CitrusVersion;
import picocli.CommandLine;

/**
 * Initializes a new Citrus test file from a template. Creates the test subfolder if it does not exist and writes a
 * template test source for the given file type. Also creates a jbang.properties file with default Citrus dependencies
 * when not already present.
 */
@CommandLine.Command(name = "init",
                     description = "Initialize a new Citrus test from a template")
public class TestInit extends CamelCommand {

    public static final String TEST_DIR = "test";

    @CommandLine.Parameters(index = "0", description = "Test file name (e.g. MyTest.yaml, MyTest.xml, MyTest.java)")
    String file;

    @CommandLine.Option(names = { "-d", "--directory" }, description = "Target directory", defaultValue = ".")
    String directory;

    public TestInit(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        // Determine file extension and base name
        String ext = getFileExtension(file);
        String baseName = getBaseName(file);

        if (ext.isEmpty()) {
            printer().println("Cannot determine file type for: " + file);
            return 1;
        }

        // Load template for the file type
        String template;
        try (InputStream is = TestInit.class.getClassLoader().getResourceAsStream("templates/citrus-" + ext + ".tmpl")) {
            if (is == null) {
                printer().println("Unsupported test file type: " + ext);
                return 1;
            }
            template = IOHelper.loadText(is);
        }

        // Replace template placeholders
        template = template.replaceAll("\\{\\{ \\.Name }}", baseName);

        // Determine the working directory
        Path workingDir = resolveTestDir();
        if (workingDir == null) {
            printer().println("Cannot create test working directory");
            return 1;
        }

        // Create target directory if specified
        Path targetDir;
        if (".".equals(directory)) {
            targetDir = workingDir;
        } else {
            targetDir = workingDir.resolve(directory);
            Files.createDirectories(targetDir);
        }

        // Create jbang properties with default dependencies if not present
        createJBangProperties(workingDir);

        // Write the test file
        Path testFile = targetDir.resolve(file);
        Files.writeString(testFile, template);

        printer().println("Created test file: " + testFile);
        return 0;
    }

    /**
     * Resolves and creates the test directory. Automatically uses the test subfolder as the working directory.
     */
    private Path resolveTestDir() {
        Path currentDir = Paths.get(".");
        Path workingDir;
        if (TEST_DIR.equals(currentDir.toAbsolutePath().normalize().getFileName().toString())) {
            // current directory is already the test subfolder
            workingDir = currentDir;
        } else if (currentDir.resolve(TEST_DIR).toFile().exists()) {
            // navigate to existing test subfolder
            workingDir = currentDir.resolve(TEST_DIR);
        } else if (currentDir.resolve(TEST_DIR).toFile().mkdirs()) {
            // create test subfolder and navigate to it
            workingDir = currentDir.resolve(TEST_DIR);
        } else {
            return null;
        }
        return workingDir;
    }

    /**
     * Creates jbang.properties with default Citrus dependencies if not already present.
     */
    private void createJBangProperties(Path workingDir) {
        if (!workingDir.resolve("jbang.properties").toFile().exists()) {
            Path jbangProperties = workingDir.resolve("jbang.properties");
            try (InputStream is
                    = TestInit.class.getClassLoader().getResourceAsStream("templates/jbang-properties.tmpl")) {
                String context = IOHelper.loadText(is);
                context = context.replaceAll("\\{\\{ \\.CitrusVersion }}", CitrusVersion.version());
                ExportHelper.safeCopy(new ByteArrayInputStream(context.getBytes(StandardCharsets.UTF_8)), jbangProperties);
            } catch (Exception e) {
                printer().println("Failed to create jbang.properties for tests in: " + jbangProperties);
            }
        }
    }

    private static String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1);
        }
        return "";
    }

    private static String getBaseName(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            return fileName.substring(0, dotIndex);
        }
        return fileName;
    }
}
