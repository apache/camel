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
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Stack;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.catalog.VersionHelper;
import org.apache.camel.dsl.jbang.core.commands.ExportHelper;
import org.apache.camel.util.IOHelper;
import org.citrusframework.CitrusSettings;
import org.citrusframework.CitrusVersion;
import org.citrusframework.jbang.CitrusJBangMain;
import org.citrusframework.jbang.commands.CitrusCommand;
import org.citrusframework.util.ClassLoaderHelper;
import org.citrusframework.util.FileUtils;
import picocli.CommandLine;

import static java.nio.file.Files.writeString;

/**
 * Automatically uses test subfolder as a working directory for creating new tests. Automatically adds a
 * citrus-application.properties configuration if not present.
 */
@CommandLine.Command(name = "init", description = "Creates a new Citrus test")
public class TestInit extends CitrusCommand {

    @CommandLine.Parameters(description = "Name of test file (or a github link)", arity = "1",
                            paramLabel = "<file>", parameterConsumer = TestInit.FileConsumer.class)
    private Path filePath; // Defined only for file path completion; the field never used

    private String file;

    @CommandLine.Option(names = { "--directory" },
                        description = "Directory where the files will be created", defaultValue = TestPlugin.TEST_DIR)
    private String directory;

    public TestInit(CitrusJBangMain citrus) {
        super(citrus);
    }

    @Override
    public Integer call() throws Exception {
        String ext = FileUtils.getFileExtension(file);
        String name = FileUtils.getBaseName(file);
        String content;
        try (InputStream is = ClassLoaderHelper.getClassLoader().getResourceAsStream("templates/" + ext + ".tmpl")) {
            if (is == null) {
                printer().println("Error: Unsupported file type: " + ext);
                return 1;
            }
            content = FileUtils.readToString(is, StandardCharsets.UTF_8);
        }

        Path currentDir = Paths.get(".");
        Path workingDir;
        if (directory.equals(currentDir.getFileName().toString())) {
            // current directory is already the target subfolder
            workingDir = currentDir;
        } else if (currentDir.resolve(directory).toFile().exists()) {
            // navigate to existing target subfolder
            workingDir = currentDir.resolve(directory);
            System.setProperty(CitrusSettings.RESOURCES_WORKDIR_PROPERTY, workingDir.toString());
        } else if (currentDir.resolve(directory).toFile().mkdirs()) {
            // create target subfolder and navigate to it
            workingDir = currentDir.resolve(directory);
            System.setProperty(CitrusSettings.RESOURCES_WORKDIR_PROPERTY, workingDir.toString());
        } else {
            throw new RuntimeCamelException("Failed to create working directory in: " + currentDir);
        }

        File target = workingDir.resolve(file).toFile();
        content = content.replaceFirst("\\{\\{ \\.Name }}", name);

        writeString(target.toPath(), content);

        // Create Citrus application properties if not present
        if (!workingDir.resolve(CitrusSettings.getApplicationPropertiesFile()).toFile().exists()) {
            Path citrusApplicationProperties = workingDir.resolve(CitrusSettings.getApplicationPropertiesFile());
            try (InputStream is
                    = TestPlugin.class.getClassLoader()
                            .getResourceAsStream("templates/citrus-application-properties.tmpl")) {
                String context = IOHelper.loadText(is);

                context = context.replaceAll("\\{\\{ \\.CitrusVersion }}", CitrusVersion.version());
                context = context.replaceAll("\\{\\{ \\.CamelVersion }}", new VersionHelper().getVersion());

                ExportHelper.safeCopy(new ByteArrayInputStream(context.getBytes(StandardCharsets.UTF_8)),
                        citrusApplicationProperties);
            } catch (Exception e) {
                getMain().getOut().println("Failed to create %s for tests in: %s"
                        .formatted(CitrusSettings.getApplicationPropertiesFile(), citrusApplicationProperties));
            }
        }

        return 0;
    }

    static class FileConsumer extends ParameterConsumer<TestInit> {
        @Override
        protected void doConsumeParameters(Stack<String> args, TestInit cmd) {
            cmd.file = args.pop();
        }
    }
}
