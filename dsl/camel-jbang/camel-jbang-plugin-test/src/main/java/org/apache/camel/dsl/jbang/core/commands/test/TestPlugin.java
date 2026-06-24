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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.catalog.VersionHelper;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.ExportHelper;
import org.apache.camel.dsl.jbang.core.common.CamelJBangPlugin;
import org.apache.camel.dsl.jbang.core.common.Plugin;
import org.apache.camel.dsl.jbang.core.common.PluginExporter;
import org.apache.camel.dsl.jbang.core.common.Printer;
import org.apache.camel.util.IOHelper;
import org.citrusframework.CitrusSettings;
import org.citrusframework.CitrusVersion;
import org.citrusframework.jbang.CitrusJBangMain;
import org.citrusframework.jbang.commands.Agent;
import org.citrusframework.jbang.commands.AgentRun;
import org.citrusframework.jbang.commands.AgentStart;
import org.citrusframework.jbang.commands.AgentStop;
import org.citrusframework.jbang.commands.Init;
import org.citrusframework.jbang.commands.Inspect;
import org.citrusframework.jbang.commands.ListTests;
import org.citrusframework.jbang.commands.Run;
import picocli.CommandLine;

@CamelJBangPlugin(name = "camel-jbang-plugin-test", firstVersion = "4.14.0")
public class TestPlugin implements Plugin {

    @Override
    public void customize(CommandLine commandLine, CamelJBangMain main) {
        CitrusJBangMain citrus = new CitrusJBangMain();
        citrus.withPrinter(new PipedPrinter(main.getOut()));

        var cmd = new CommandLine(new TestCommand(main))
                .addSubcommand("init", new CommandLine(new Init(citrus)))
                .addSubcommand("inspect", new CommandLine(new Inspect(citrus)))
                .addSubcommand("run", new CommandLine(new Run(citrus)))
                .addSubcommand("ps", new CommandLine(new ListTests(citrus)), "ls")
                .addSubcommand("agent", new CommandLine(new Agent(citrus))
                        .addSubcommand("start", new CommandLine(new AgentStart(citrus)))
                        .addSubcommand("run", new CommandLine(new AgentRun(citrus)))
                        .addSubcommand("stop", new CommandLine(new AgentStop(citrus))));

        commandLine.addSubcommand("test", cmd)
                .setExecutionStrategy(new CitrusExecutionStrategy(main));
    }

    @Override
    public Optional<PluginExporter> getExporter() {
        return Optional.of(new TestPluginExporter());
    }

    /**
     * Command execution strategy performs special command preparations and makes sure to set and run the proper Citrus
     * version for this Camel release.
     */
    private record CitrusExecutionStrategy(CamelJBangMain main) implements CommandLine.IExecutionStrategy {

        public static final String TEST_DIR = "test";

        @Override
        public int execute(CommandLine.ParseResult parseResult)
                throws CommandLine.ExecutionException, CommandLine.ParameterException {

            if (!parseResult.errors().isEmpty() || !parseResult.unmatched().isEmpty()) {
                // Something is wrong with the command - do not adjust anything
                return new CommandLine.RunLast().execute(parseResult);
            }

            if (isCitrusCommand(parseResult)) {
                String command = "";
                if (parseResult.originalArgs().size() > 2) {
                    command = parseResult.originalArgs().get(1);
                } else if (parseResult.originalArgs().size() == 2) {
                    command = parseResult.originalArgs().get(1);
                }

                System.setProperty("citrus.jbang.version", CitrusVersion.version());
                System.setProperty("citrus.camel.jbang.version", new VersionHelper().getVersion());

                // Prepare commands
                if ("init".equals(command)) {
                    prepareInitCommand();
                }

                if (!isCamelLauncherRuntime()) {
                    var tccLoader = Thread.currentThread().getContextClassLoader();
                    try {
                        // Update class loader so Citrus is able to resolve resources
                        ClassLoader cl = TestPlugin.class.getClassLoader();
                        Thread.currentThread().setContextClassLoader(cl);

                        return new CommandLine.RunLast().execute(parseResult);
                    } finally {
                        Thread.currentThread().setContextClassLoader(tccLoader);
                    }
                }
            }

            return new CommandLine.RunLast().execute(parseResult);
        }

        /**
         * Prepare init command. Automatically uses test subfolder as a working directory for creating new tests.
         * Automatically adds a citrus-application.properties configuration if not present.
         */
        private void prepareInitCommand() {
            Path currentDir = Paths.get(".");
            Path workingDir;
            // Automatically set test subfolder as a working directory
            if (TEST_DIR.equals(currentDir.getFileName().toString())) {
                // current directory is already the test subfolder
                workingDir = currentDir;
            } else if (currentDir.resolve(TEST_DIR).toFile().exists()) {
                // navigate to existing test subfolder
                workingDir = currentDir.resolve(TEST_DIR);
                System.setProperty(CitrusSettings.RESOURCES_WORKDIR_PROPERTY, workingDir.toString());
            } else if (currentDir.resolve(TEST_DIR).toFile().mkdirs()) {
                // create test subfolder and navigate to it
                workingDir = currentDir.resolve(TEST_DIR);
                System.setProperty(CitrusSettings.RESOURCES_WORKDIR_PROPERTY, workingDir.toString());
            } else {
                throw new RuntimeCamelException("Cannot create test working directory in: " + currentDir);
            }

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
                    main.getOut().println("Failed to create %s for tests in: %s"
                            .formatted(CitrusSettings.getApplicationPropertiesFile(), citrusApplicationProperties));
                }
            }
        }

        /**
         * Evaluate if the current runtime is using Camel Launcher. Camel launcher sets a System property marking the
         * runtime nature. If the System property is not present we can assume that the runtime is something different
         * e.g. Camel JBang.
         */
        private boolean isCamelLauncherRuntime() {
            return Boolean.parseBoolean(System.getProperty("camel.launcher", "false"));
        }

        /**
         * Evaluate command user object and check if this is a Citrus command implementation.
         */
        private boolean isCitrusCommand(CommandLine.ParseResult parseResult) {
            CommandLine.ParseResult subcommand = parseResult;
            while (subcommand.hasSubcommand()) {
                subcommand = subcommand.subcommand();
            }

            Object commandObject = subcommand.commandSpec().userObject();
            return commandObject.getClass().getName().startsWith("org.citrusframework");
        }
    }

    /**
     * Delegates Citrus printer API methods to Camel printer implementation.
     *
     * @param delegate the Camel printer.
     */
    private record PipedPrinter(Printer delegate) implements org.citrusframework.jbang.Printer {
        @Override
        public void println() {
            delegate.println();
        }

        @Override
        public void println(String line) {
            delegate.println(line);
        }

        @Override
        public void print(String output) {
            delegate.print(output);
        }

        @Override
        public void printf(String format, Object... args) {
            delegate.printf(format, args);
        }
    }
}
