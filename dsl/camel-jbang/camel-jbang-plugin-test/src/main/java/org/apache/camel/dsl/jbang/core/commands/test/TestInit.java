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

import org.aesh.command.CommandResult;
import org.aesh.command.impl.internal.OptionType;
import org.aesh.command.impl.internal.ProcessedCommand;
import org.aesh.command.impl.internal.ProcessedOptionBuilder;
import org.aesh.command.invocation.CommandInvocation;
import org.apache.camel.catalog.VersionHelper;
import org.apache.camel.dsl.jbang.core.common.CommandTree;
import org.apache.camel.dsl.jbang.run.common.ExportHelper;
import org.apache.camel.util.IOHelper;
import org.citrusframework.CitrusSettings;
import org.citrusframework.CitrusVersion;
import org.citrusframework.jbang.cli.CitrusJBangMain;
import org.citrusframework.jbang.cli.commands.Init;

/**
 * Automatically uses test subfolder as a working directory for creating new tests. Automatically adds a
 * citrus-application.properties configuration if not present.
 *
 * The --force option is added programmatically in {@link #setProcessedCommand} because Citrus ships compile-time
 * generated aesh metadata ({@code Init_AeshMetadata}) that takes precedence over annotation scanning. That metadata
 * only knows about {@code Init}'s own fields, so a subclass {@code @Option} would be silently ignored.
 */
public class TestInit extends Init implements CommandTree.ParsedCommandAware {

    boolean force;

    public TestInit(CitrusJBangMain citrus) {
        super(citrus);
    }

    @Override
    public void setProcessedCommand(ProcessedCommand<?, ?> processedCommand, CommandTree tree) {
        try {
            processedCommand.addOption(
                    ProcessedOptionBuilder.builder()
                            .name("force")
                            .description("Overwrite a test that exists already")
                            .hasValue(false)
                            .type(Boolean.class)
                            .optionType(OptionType.BOOLEAN)
                            .fieldSetter((cmd, val) -> ((TestInit) cmd).force = val != null && (Boolean) val)
                            .fieldGetter(cmd -> ((TestInit) cmd).force)
                            .fieldResetter(cmd -> ((TestInit) cmd).force = false)
                            .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to add --force option to test init command", e);
        }
    }

    @Override
    public CommandResult execute(CommandInvocation invocation)
            throws org.aesh.command.CommandException, InterruptedException {
        return TestPlugin.withClassLoader(inv -> {
            try {
                Path target = targetFile();
                if (target != null && Files.exists(target) && !force) {
                    printer().println("Error: Test already exists: %s (use --force to overwrite it)".formatted(target));
                    return CommandResult.FAILURE;
                }
                return super.execute(inv);
            } catch (Exception e) {
                printer().println(
                        "Error: Failed to initialize test: %s %s".formatted(e.getClass().getName(), e.getMessage()));
                return CommandResult.FAILURE;
            }
        }, invocation);
    }

    /**
     * The file the Citrus init writes: the file argument in the target directory, or null when it is not known.
     */
    Path targetFile() {
        try {
            String file = TestPlugin.readField(Init.class, "file", this);
            if (file == null) {
                return null;
            }
            String directory = TestPlugin.readField(Init.class, "directory", this);
            return Path.of(resolveTargetDirectory(directory)).resolve(file);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
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
