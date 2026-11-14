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
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.catalog.VersionHelper;
import org.apache.camel.dsl.jbang.run.common.ExportHelper;
import org.apache.camel.util.IOHelper;
import org.citrusframework.CitrusSettings;
import org.citrusframework.CitrusVersion;
import org.citrusframework.jbang.cli.CitrusJBangMain;
import org.citrusframework.jbang.cli.commands.Init;
import picocli.CommandLine;

/**
 * Automatically uses test subfolder as a working directory for creating new tests. Automatically adds a
 * citrus-application.properties configuration if not present.
 */
@CommandLine.Command(name = "init", description = "Creates a new Citrus test")
public class TestInit extends Init {

    @CommandLine.Option(names = { "--force" }, description = "Overwrite a test that exists already", defaultValue = "false")
    boolean force;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    public TestInit(CitrusJBangMain citrus) {
        super(citrus);
    }

    @Override
    public Integer call() throws Exception {
        // the Citrus init writes the test over one that exists, so an edited test was lost (CAMEL-26933)
        Path target = targetFile();
        System.err.println("DEBUG26933 spec=" + (spec != null) + " target=" + target + " pos="
                           + (spec != null ? spec.positionalParameters() : null));
        if (target != null && Files.exists(target) && !force) {
            printer().println("Error: Test already exists: %s (use --force to overwrite it)".formatted(target));
            return 1;
        }
        return super.call();
    }

    /**
     * The file the Citrus init writes: the file argument in the target directory, or null when it is not known.
     */
    Path targetFile() {
        // the file argument goes through a parameter consumer of Citrus into its private field file
        String file;
        try {
            Field field = Init.class.getDeclaredField("file");
            field.setAccessible(true);
            file = (String) field.get(this);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // another Citrus version: no check, as before
            return null;
        }
        if (file == null) {
            return null;
        }
        CommandLine.Model.OptionSpec option = spec != null ? spec.findOption("--directory") : null;
        String directory = option != null ? option.getValue() : null;
        return Path.of(resolveTargetDirectory(directory)).resolve(file);
    }

    @Override
    protected String resolveTargetDirectory(String directory) {
        if (directory == null || ".".equals(directory)) {
            return TestPlugin.TEST_DIR;
        }

        return super.resolveTargetDirectory(directory);
    }

    @Override
    protected void initAdditionalFiles(Path workingDir) {
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
                printer().printErr("Failed to create '%s' in: %s"
                        .formatted(CitrusSettings.getApplicationPropertiesFile(), citrusApplicationProperties));
            }
        }
    }
}
